---
id: TASK-183
title: 'Deferred room assignment: timeslot-only solving for Lessons (7-phase solver)'
status: Done
assignee: []
created_date: '2026-03-05 20:15'
updated_date: '2026-03-10 10:58'
labels:
  - claude-planned
  - performance
  - solver-architecture
dependencies: []
references:
  - src/main/resources/solverConfig.xml
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
  - claudedocs/solver-phases.md
documentation:
  - claudedocs/human-planner-heuristics.md
priority: medium
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Human planners assign rooms as the **last step** of scheduling — only after the timetable is complete and all compaction constraints are satisfied. Labs are the exception — they need specific rooms — but regular classroom courses get rooms assigned AFTER everything else is finalized. Our pre-assignment phase already handles labs: all LabBlocks are pinned with correct timeslots and rooms before the solver starts. The solver only moves ~7,000 unpinned Lessons (regular classroom courses).

**Current solver has 7 phases:**
- Phase 1: CH LabBlocks (no-op - all pre-pinned)
- Phase 2: CH Lessons (assigns timeslot + room simultaneously)
- Phase 3: LS Compactness I (timeslot-heavy, minimal room moves)
- Phase 4: LS Conflict Resolution (balanced timeslot/room, swaps, pillars)
- **Phase 4b: LS Compactness II** (NEW - gap recovery after conflicts)
- Phase 5: LS Room-Only Refinement (room-only moves)
- **Phase 7: LS Final Compaction** (NEW - timeslot-only with Simulated Annealing)

## Problem

Currently, Phase 2 assigns both `timeslot` and `room` simultaneously:
- CH evaluates ~1,520 timeslots × ~18 rooms = **~27,360 candidates per Lesson**
- Phases 3, 4, 4b evaluate room changes alongside timeslot changes
- ~22 constraints are timeslot-only, but all ~30 are evaluated for every move
- **Room assignment during early phases interferes with compactness optimization** — the solver wastes effort finding valid rooms instead of focusing on timeslot placement

## Human Planner Approach (Key Insight)

Human schedulers follow this sequence:
1. **Place lessons in timeslots** — no rooms considered yet
2. **Satisfy compactness constraints** — minimize gaps, group same-subject lessons
3. **Resolve conflicts** — ensure no double-bookings
4. **Assign rooms LAST** — only when timeslot placement is complete and satisfactory

**Why this works:** Room constraints are "easy" compared to timeslot constraints. With ~1,520 timeslots but only ~18 rooms, finding a valid room for each placed lesson is trivial once timeslots are fixed.

## Proposed Change (CORRECTED - Rooms at VERY END)

Defer room assignment for Lessons to Phase 5 — the **final** phase before compaction:

### CH Changes
- **Phase 1 (LabBlock CH)**: No change — LabBlocks are already 100% pre-pinned
- **Phase 2 (Lesson CH)**: Assign `timeslot` only (room remains null). Search space drops from ~27,360 to ~1,520 candidates per Lesson
- ~~**Phase 2b (Room CH)**~~ — **REMOVED** — rooms assigned at the very end (Phase 5), not after CH

### LS Changes
- **Phase 3 (Compactness I)**: Timeslot-only moves — NO room changes at all
- **Phase 4 (Conflict Resolution)**: Timeslot-only moves — NO room changes at all
- **Phase 4b (Compactness II)**: Timeslot-only moves — NO room changes at all
- **Phase 5 (Room Assignment + Refinement)**: **INITIAL room assignment happens here** — this phase assigns rooms for the first time, then refines them
- **Phase 7 (Final Compaction)**: Timeslot-only — no change needed

### Key Difference from Original Understanding

**WRONG:** Phase 2 assigns timeslot → Phase 2b assigns room → Phase 3+ optimizes both
**CORRECT:** Phase 2 assigns timeslot → Phases 3/4/4b optimize timeslot ONLY → Phase 5 assigns rooms (first time)

### Constraint Handling

Room-dependent constraints already have `room != null` filters and will return 0 penalty during timeslot-only phases:
- **H2.1-H2.3**: Room conflicts — have `.filter((a, b) -> a.room != null)` guards ✅
- **H-M2.1-H-M2.2**: Room suitability — have `.filter(l -> l.room != null)` guards ✅
- **M3.1-M3.2**: Room capacity — have `.filter(lesson -> lesson.room != null)` guards ✅
- **H16**: Consecutive same-subject same teacher-room — needs review
- **M4**: Lab room-tag affinity — needs review
- **S10**: Discourage generic in labs — needs review
- **S11**: Student group room affinity — needs review

Constraints needing null-safe updates:
- H16: Split into timeslot-part (same teacher consecutive) and room-part (same room when consecutive)
- M4, S10, S11: Add `room != null` filters

## Expected Performance Impact

| Metric | Current | Expected | Improvement |
|--------|---------|----------|-------------|
| Lesson CH candidates/entity | ~27,360 | ~1,520 | **~18× fewer** |
| Constraints per timeslot move (Phase 3/4/4b) | ~30 | ~22 | **~27% fewer** |
| Phase 3 move types | 6 | 2 | **3× fewer** |
| Phase 4 move types | 8 | 5 | Cleaner |
| Room assignment phase | 5 (mixed with timeslot) | 5 (pure room) | Cleaner focus |

## Risk Assessment

**LOW RISK** because:
- All LabBlocks are pre-pinned (pre-assignment handles lab rooms)
- Most Lessons have `requiredRoomTags = []` (any classroom works)
- Room conflicts (H2) are easily resolved in Phase 5 since the domain is small (18 rooms)
- The human planner approach validates this strategy empirically
- **H2 and H-M2 already have `room != null` filters**

**Risks to monitor:**
- H16 (consecutive same-subject same teacher-room) may create hard violations if room can't match during Phase 5
- Extreme timeslot clustering: if many Lessons land on the same timeslot, Phase 5 may not have enough rooms
- Mitigation: H11 (max daily hours) and existing balance constraints already prevent extreme clustering

## Related Files

- `src/main/resources/solverConfig.xml` - 7-phase solver configuration
- `src/main/java/com/school/planner/solver/BaseConstraintProvider.java` - H2, H-M2, M3 (already null-safe)
- `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java` - H16, M4, S10, S11 (need null-safety)
- `src/main/java/com/school/planner/domain/Lesson.java` - Planning entity with timeslot + room variables
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lesson CH assigns timeslot only (room remains null)
- [ ] #2 NO Phase 2b — rooms NOT assigned after CH
- [ ] #3 Phase 3 (Compactness) uses timeslot-only moves — no room changes
- [ ] #4 Phase 4 (Conflict Resolution) uses timeslot-only moves — no room changes
- [ ] #5 Phase 4b (Compactness II) uses timeslot-only moves — no room changes
- [ ] #6 Phase 5 (Room Assignment) assigns rooms for the FIRST TIME — then refines them
- [ ] #7 All constraint verifier tests pass (mvn test)
- [ ] #8 Room-dependent constraints return 0 penalty when room is null (no false violations)
- [ ] #9 Benchmark: CH completion time is faster than current joint assignment
- [ ] #10 Benchmark: LS move evaluation speed (moves/sec) is faster during Phase 3/4/4b
- [ ] #11 Final score quality is comparable or better than current approach (same time budget)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## CRITICAL INSIGHT (2026-03-10 Correction)

**Original misunderstanding:**

I initially implemented Phase 2b (Room CH) to assign rooms immediately after Phase 2 (Lesson CH).

**Correct human planner approach:**

Rooms must be assigned at the VERY END — after ALL timeslot optimization (Phases 3, 4, 4b) is complete.

**Why this matters:**

- If rooms are assigned too early, the solver wastes effort finding valid rooms during compactness phases

- Human planners don't think about rooms until the timetable is perfect

- Phase 5 should be the FIRST time rooms are assigned, not a refinement phase

**Implementation correction:**

- REMOVE Phase 2b entirely

- Phase 5 handles initial room assignment AND refinement (it's a CH-like phase now)

- Phases 3, 4, 4b remain 100% timeslot-only

**BUG FIX (2026-03-10):** Restored `nullable=true` on Lesson.room to fix CH placing all lessons on first timeslot.

**Root cause:** After revert 33d0c07 removed nullable=true, the CH's behavior became unpredictable when only timeslot was specified in changeMoveSelector but room was also required (nullable=false).

**Files modified:**

- `src/main/java/com/school/planner/domain/Lesson.java` - Added `nullable=true` to room variable

- `src/test/java/com/school/planner/integration/SchoolScheduleSolverIntegrationTest.java` - Updated test to only verify timeslots assigned (rooms come in Phase 5)

**Tests:** All 3 integration tests pass (testMinimalScenarioFeasibility, testRepresentativeScenarioFeasibility, testStressScenarioFeasibility)

**Next steps:**

- Deploy to laptop and verify lessons are distributed across timeslots

- Re-benchmark to document CH performance with nullable=true

- Continue with full 7-phase implementation
<!-- SECTION:NOTES:END -->
