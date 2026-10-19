# Pre-Assignment Algorithms

The algorithmic patterns of the CURRENT pre-assignment pipeline (CSP-based,
`HybridPhase1aService`). Verified against the code 2026-08-04 (file:line refs
below); this rewrites the pre-TASK-228/267 description that used to live here
(`BacktrackingPreAssigner` / `LabBlockPreAssigner` / the `LabBlock` entity, all
removed). For business context see `CLAUDE.md` -> "Pre-Assignment Workflow" and
`claudedocs/human-planner-heuristics.md` (the human planner's 5-step heuristic);
for the file list see `PROJECT_INDEX.md` -> Service layer.

## What pre-assignment is for

Human schedulers place the "hard" lessons (constrained teachers, lab rotations,
professionalizzanti) deterministically before optimizing. The pipeline reproduces
that: it **pins** lesson placements that are known-good (0hard), so the solver's
search space shrinks to the unpinned residue. A pinned `Lesson` has
`pinned = true` (`@PlanningPin isPinned()`), so the solver never moves it;
unpinned lessons (`timeslot = null`) are the solver's job.

## Entry point and orchestration

- REST: `POST /api/schedule/pre-assign` -> `PreAssignmentResource.runPreAssignment()`
  (`rest/PreAssignmentResource.java:50`). It resets status, starts the pipeline
  **asynchronously** (`hybridPhase1aService.runAsync()`), and returns HTTP 202 at
  once. Poll `GET /api/schedule/pre-assign/status` for completion.
- Orchestrator: `HybridPhase1aService.run()` (`service/HybridPhase1aService.java:488`).
  Phases run **sequentially, each gated on success**; any failure builds a
  `FailureAnalysis` and stops the pipeline. Phase 0 and 0b are invoked via CDI
  self-injection (`self.get().<phase>()`, each `@ActivateRequestContext @Transactional`);
  Phases 1-3 are plain private calls sharing the request context established by
  `runWithEpoch()`.

## The 5-phase pipeline

`CLAUDE.md` summarizes the lab sub-sequence as "acconciatura -> labs ->
professionalizzanti"; the full pipeline runs two earlier phases first:

| Phase | Method (HybridPhase1aService) | What it does | Default |
|-------|------------------------------|--------------|---------|
| 0 | `runConstrainedTeacherPhase()` (`:814`) | **TASK-435** constrained-teacher pin. Pins teachers whose golden placement is squeezed to <= `max-weekdays` (default 1) distinct weekdays. `source=golden` (reads `seed/lessons.csv`) by default; `source=input` (incarichi-derived, TASK-452) opt-in. | **ON** |
| 0b | `runMonoRoomTagPhase()` (`:1000`) | **TASK-449** mono-room-tag pin. | OFF (checkpoint prototype) |
| 1 | `runPhase1()` (`:1363`) | Acconciatura lab rotations. CSP (`GreedyRotationAssigner`); supports **PARTIAL** success (overflow blocks -> solver). | ON |
| 2 | `runPhase2ForRoomTag()` (`:1441`) | Other labs (moda, termoidraulica, meccanica, informatica), sequential loop over `PHASE2_ROOM_TAGS`. Same CSP as Phase 1 but **always counts as success even if nothing pinned** (`:1473`). | ON |
| 3 | `runPhase3()` (`:1503`) | Professionalizzanti on stage days. **Deterministic** day-by-day rotation (TASK-279), round-robin, max 1 lesson/course/day, `isValidSlot()`. NOT the CSP. | ON |

Phases 1-2 are CSP; Phase 3 is deterministic. TASK-435 (Phase 0) deliberately runs
before the lab phases with an already-initialized context (see the comment at
`HybridPhase1aService:528-539`).

## The CSP engine (Phases 1-2): `GreedyRotationAssigner` + `DayAssignmentCsp`

Lab rotations (acconciatura + the Phase-2 tags) are placed by a two-layer CSP:

- **`GreedyRotationAssigner.assignTeachers()`** (`service/GreedyRotationAssigner.java:228`)
  is the backtracking engine over `(teacher, group)` pairs. It assigns day + slot
  + room and supports **PARTIAL success**: if at least one block of a pair places,
  the pair is considered assigned and the overflow goes to the solver
  (`:777-808`). Placement modes (`:126-130`): `SINGLE_DAY` (all blocks one day,
  from `LabRotation` or CSP pick), `MULTI_DAY_DETERMINISTIC` (blocks have distinct
  `LabRotation` days), `MULTI_DAY_CSP` (no `LabRotation`, spread greedily).
  Backtracking: `backtrackAssign()` (`:779`); slot finding: `findValidSlotForBlock()`
  (`:1166`) checks H1/H3/H2 (via `PreAssignmentContext`), H5, H11, ideal end date,
  stage days.
- **`DayAssignmentCsp.enumerateDayAssignments()`** (`service/DayAssignmentCsp.java:128`)
  is the meta-CSP. Level 1 enumerates feasible `(teacher -> day)` mappings per
  roomTag under simplified day-level constraints; level 2 runs deterministic
  slot-finding for the best day assignment. `enumeratePairsRecursive()` (`:252`)
  early-aborts at `MAX_CANDIDATES = 10000` (`:57`, check at `:270`). Constraints
  checked during enumeration: same-teacher-same-day (`:295`), student-group
  conflict (`:307`), room capacity (`:331`). Candidate days are pre-filtered for
  H5 VACATION days, `LabRotation` days, block-size fit, and saturated days
  (`computeValidDaysForPair`, `:364`).

## Validator gating: `PreAssignmentValidator`

A candidate is pinned only if it introduces no hard violation. The validator
(`service/PreAssignmentValidator.java`) exposes:

- `canPin()` (`:451`) - full check: H1, H2, H3, H5, H11, H-M2, M15, H16.
- `canPinInMemory()` (`:514`) - same, but O(1) via `PreAssignmentContext` lookups
  (the path the CSP uses).
- `canPinHardConstraints()` (`:600`) - **TASK-435 hard-only veto**: H1, H2, H3,
  H5, H11, H-M2 (skips M15/H16). Used by the constrained-teacher phase.

Every pin path calls one of these; `false` means the lesson is left unpinned for
the solver rather than force-placed into a conflict.

## Batch preload: `PreAssignmentContext` + `PreAssignmentEntityExtractor`

To avoid N+1 queries across thousands of candidate checks, the context is built
once:

- `PreAssignmentContext.initialize()` (`service/PreAssignmentContext.java:84`)
  preloads all pinned `Lesson`s, teacher unavailability (VACATION/SICK) by teacher
  id, rooms indexed by tag, timeslots indexed by `(date, slotIndex)`, and
  same-subject lessons for M15/H16. Conflict lookups become O(1) map reads.
- `PreAssignmentEntityExtractor.extract()` (`service/PreAssignmentEntityExtractor.java:69`)
  extracts the entities to pre-assign: lab blocks (`isLabHour` lessons, `:107`)
  and professionalizzanti lessons for groups with `StagePattern`s (`:131`).

## What gets pinned vs solver-handled

`HybridPhase1aService.persistPinnedLesson()` (`:1263`) sets `timeslot`, `room`,
`pinned = true`, merges the entity, and calls `context.trackLesson()` so the
in-memory conflict maps stay current. `LabRotation` rows guide which lab sessions
to pre-assign (day-of-week preference, period boundaries); they are wrapped into
`LabSession`s by `loadLabSessionsByRoomTag()` (`:1986`), the CSP assigns them, and
successful assignments become pinned `Lesson`s. After the pipeline, the solver
runs only on `pinned = false` / `timeslot = null` entities.

## Live `-D` flags (read at runtime via `System.getProperty`)

Pre-assignment:
- `planner.pre-assign.constrained-teacher.enabled` - TASK-435, default **ON**
  (opt-out). Reader: `HybridPhase1aService.isConstrainedTeacherEnabled()` (`:111`).
- `planner.pre-assign.constrained-teacher.max-weekdays` - default 1.
  `constrainedTeacherMaxWeekdays()` (`:126`).
- `planner.pre-assign.constrained-teacher.source` - `golden` (default) | `input`
  (TASK-452).
- `planner.pre-assign.mono-room-tag.enabled` - TASK-449, default **OFF**.

Related (read in `SchoolSchedule`, not the pre-assign service): the M-LF
lab-fallback (`planner.room.lab-fallback.enabled`, TASK-456) and the M-TC
tag-capacity prototype (`planner.tag-capacity.enabled`, TASK-455). Note: a `-D`
flag reaches the running app only on the `java -jar` command line, not via
`MAVEN_OPTS` (see `CLAUDE.md`).

## Output: `HybridPhase1aReport`

`service/HybridPhase1aReport.java` captures: `success` (0hard achieved), `phase`
("COMPLETE" or the failed phase name), `totalDuration`, `totalCandidatesTried`,
`failureAnalysis`, and a `List<PhaseResult>` (each: `phaseName`, `success`,
`candidatesEvaluated`, `duration`, `entitiesPinned`, `roomTag`). The
`winningCandidate` field is legacy and always `null` in production (see below).

## Dead / legacy code (do NOT rely on, do NOT revive)

- **`RotationCandidate`** (`service/RotationCandidate.java`) is **test-only**:
  `new RotationCandidate(...)` appears only under `src/test/java`
  (`RotationTestRunnerTest`, `HybridPhase1aDataClassesTest`), never in production.
  It is legacy scaffolding; the `HybridPhase1aReport.winningCandidate` field
  referencing it is always `null` in the live pipeline. Cleanup candidate.
- **`RotationTestRunner`** (`service/RotationTestRunner.java`): its enumeration
  logic (`testCandidate`, `:93`) is NOT called by the current pipeline (which uses
  `GreedyRotationAssigner` CSP). Only its inner POJO types survive as shared
  records/classes used across the pre-assignment code (`TestConfig`, `BlockKey`,
  `SlotAssignment`), plus `clearCapacityCache()` (called from
  `HybridPhase1aService`). (TASK-467: "RotationTestRunner/RotationCandidate MORTI
  test-only, non resuscitare".)

## Related files

- `rest/PreAssignmentResource.java` - REST trigger (async).
- `service/HybridPhase1aService.java` - orchestrator + phases 0/0b/1/2/3.
- `service/GreedyRotationAssigner.java` - CSP backtracking engine (phases 1-2).
- `service/DayAssignmentCsp.java` - meta-CSP (day assignment -> slot finding).
- `service/PreAssignmentValidator.java` - 0hard gating (`canPin*`).
- `service/PreAssignmentContext.java` - O(1) conflict maps (batch preload).
- `service/PreAssignmentEntityExtractor.java` - lab/prof entity extraction.
- `service/ConstrainedTeacherAllocator.java` - TASK-435 pin-plan builder.
- `service/HybridPhase1aReport.java` - pipeline report.

## References

- `CLAUDE.md` -> "Pre-Assignment Workflow" (business context, authoritative) and
  the TASK-435 / TASK-449 / TASK-452 ratification notes.
- `PROJECT_INDEX.md` -> Service layer (file list).
- `claudedocs/human-planner-heuristics.md` (the human planner's 5-step heuristic,
  current).
- `CLAUDE.md` -> "RATIFICA TASK-435" (constrained-teacher DEFAULT ON) and
  "TASK-452" (golden-runtime debt census, including the `source=golden` read).
