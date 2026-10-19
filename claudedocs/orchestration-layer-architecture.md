# Orchestration Layer Architecture

The 2026/27 campaign built a **deterministic orchestration layer** that operates at the boundaries between Timefold's solver phases. The framework's phases are first-class (construction heuristic, local search, custom phases); our layer lives in the **gaps between them**, where Timefold provides no API. It governs when to use a stochastic operator (local search, time-bounded) versus an exact one (bounded enumeration on the residual), based on the residual size, the tier of constraint violated, and whether a fix is demonstrably reachable.

See [Solver Multiphase Architecture](solver-phases.md) for the 5-phase pipeline this layer wraps.

## 1. Operator Census

### 1.1 Staged Pin Policy

**File:** `solver/StagedPinPolicy.java`

**Purpose:** Decides which unpinned lessons to promote to `pinned = true` after stage A (construction) and which promoted pins to release when the frozen template deadlocks the residual.

**Entry invariant:** The lesson population has no promoted pins held. The VRP caches are cold (no prior promotion has modified `pinnedLessonTimeslotsByGroup`).

**Exit guarantee:** `selectPromotions` returns the stable id-ordered list of lessons sitting on their course's M32 template cell. `selectDemotions` returns the least-constrained-first (ascending M32 deviation weight) promoted blockers of deadlocked boxed lessons, at most `MAX_DEMOTIONS_PER_BOXED_LESSON = 2` per boxed lesson.

**Rollback mechanism:** The promotion itself is idempotent (a lesson already `pinned` is filtered out). The demotion is not reversed by the policy; the released pins become ordinary unpinned lessons for the smarcatura and subsequent stages.

**Gate:** Promotion shares M32's `isOnTemplateCell` test (`StagedPinPolicy.java:71-82`). Demotion uses BoxRepair's full gate set (`passesPlacementGates` at `:140`, `passesSwapAwarePlacementGates` at `:200`) to identify truly deadlocked boxed lessons (`selectDemotions` at `StagedPinPolicy.java:124`).

### 1.2 Tier-Scoped Hard Gate

**File:** `service/ScheduleService.java`

**Purpose:** The staged cycle reads **timeslot-tier hard** (all hard constraint families except room-tier) separately from room-tier hard, so that the B-TIMESLOT gate and the repair `ensureImproveOrNoop` can measure progress on the placement-only score without being confused by room violations that B-ROOM has not yet resolved.

**Implementation:**

- `ROOM_TIER_HARD_FAMILIES = List.of("H2", "H-M2", "H29")` (`ScheduleService.java`). These families are EXCLUDED from the timeslot-tier hard sum.
- `timeslotTierHard(ConstraintMatchSnapshot)` sums every hard constraint match whose canonical ID is NOT in `ROOM_TIER_HARD_FAMILIES` (`ScheduleService.java`).
- `isRoomTierHardConstraint(String canonicalId)` tests family membership with dot-prefix matching (so `H2.1` is room-tier, while `H20` is timeslot-tier) (`ScheduleService.java`).

**Entry invariant:** A `ConstraintMatchSnapshot` from a solved `SchoolSchedule`.

**Exit guarantee:** A single `long` representing the placement-only hard score. This is the score the B-TIMESLOT gate and every repair pass's `ensureImproveOrNoop` compare against.

### 1.3 Deterministic Repair Passes

Each repair pass is pure-Java, deterministic, improve-or-noop, and shares BoxRepair's gate primitives. They run post-smarcatura (after all promoted pins are released), between the smarcatura and stage B-ROOM, where only room variables still move.

#### BoxRepair

**File:** `solver/BoxRepair.java`

**Purpose:** Places boxed (unassigned) lessons into VRP-legal, conflict-free, H17-safe cells. Direct pass (depth-0, cheapest M20-fill-gain day first) followed by eviction pass (depth-1: relocate a single unpinned blocker to free a cell).

**Entry invariant:** The grid is conflict-free. `buildOccupancyIndex`, `buildGroupDaySlots`, `buildTeacherDaily` are built on the placed population and kept live through every commit.

**Exit guarantee:** `Result.conflictsAdded` must be 0. Every committed placement passes the full gate chain: VRP, H1/H3 conflict-free, H14/H11/H7 caps, H17-safe arrival. Eviction chains are validated atomically before commit.

**Rollback mechanism:** The pass itself is improve-only (each placement removes one H0 hard, no new hard). The outer `ensureImproveOrNoop` in `ScheduleService.runHoleRepair` provides the safety net: if the pass ever worsens the timeslot-tier hard, the grid is reverted to the pre-pass snapshot.

#### HoleRepair

**File:** `solver/HoleRepair.java`

**Purpose:** Closes mid-day H17 breaks the staged cycle leaves behind, by importing a whole hour from another date. Three levels (cheapest damage first): free import (off-template, no M32 cost), on-template import (cheapest M32 weight), min-slide (the day's own first lesson moves up, may de-anchor M18).

**Entry invariant:** Post-smarcatura, no promoted pins held (`HoleRepair.java:147-150`). The `buildDayLessons` day view and `buildCandidatesByGroup` donor pool are built on the released grid.

**Exit guarantee:** `Result.conflictsAdded` must be 0. Every committed move passes conflict-free, placement gates, H17-safe departure, and VRP membership. The min-slide variant is allowed to de-anchor (M18 offset increases) because H17 hard outranks M18 medium.

**Rollback mechanism:** `ensureImproveOrNoop` in `ScheduleService.runHoleRepair` (`ScheduleService.java`).

#### AnchorRepair

**File:** `solver/AnchorRepair.java`

**Purpose:** Closes LEADING M18 late-start holes by importing an hour into the first calendar-open slot. Ratified unconditional (maintainer, 2026-07-28).

**Entry invariant:** Post-smarcatura, no promoted pins. Donor must pass `SurplusRebalance.keepsDayAnchored` (never de-anchor the donor day).

**Exit guarantee:** `Result.donorDaysNewlyLate` must be 0 (asserted in checkpoint harness). `conflictsAdded` must be 0.

**Rollback mechanism:** `ensureImproveOrNoop` in `ScheduleService.runAnchorRepair` (`ScheduleService.java`).

#### SurplusRebalance

**File:** `solver/SurplusRebalance.java`

**Purpose:** Moves one hour at a time from (group, date) days above the school's expected-hours plan to days below it. Every move is M20-net-positive by construction (donor has `m20MarginalRemovalCost == 0`, target has `m20MarginalFillGain > 0`).

**Entry invariant:** Post-smarcatura, no promoted pins. Donor must pass `keepsDayAnchored` and `keepsSubjectBlockWhole` (M18 and M15 departure guards).

**Exit guarantee:** `Result.conflictsAdded` must be 0. The M20 total strictly decreases per move, so the loop terminates. The `DayFill` before/after readout is measured on the score's own curve.

**Rollback mechanism:** `ensureImproveOrNoop` in `ScheduleService.runSurplusRebalance` (`ScheduleService.java`).

#### LabDayConsolidation

**File:** `solver/LabDayConsolidation.java`

**Purpose:** Cross-group teacher-day coordinator that closes lab-day H17 gaps whose root cause is cross-group teacher saturation at the gap slot. Ratified default ON (maintainer, 2026-08-01).

**Entry invariant:** Post-smarcatura, no promoted pins. Gated by `SchoolSchedule.isLabDayConsolidationEnabled` (opt-out with `-Dplanner.construction.lab-day-consolidation.enabled=false`).

**Exit guarantee:** Two-move transaction: donor relocation is reverted if no legal anchor import exists. `conflictsAdded` must be 0.

**Rollback mechanism:** `ensureImproveOrNoop` in `ScheduleService.runLabDayConsolidation` (`ScheduleService.java`). The two-move transaction is also self-reverting within the pass.

#### CompositionHoleCloser

**File:** `solver/CompositionHoleCloser.java`

**Purpose:** The classroom-day twin of LabDayConsolidation. Closes H17 gaps on non-lab days by a cross-group, same-teacher cascade: relocate the cross-group blocker occupying the gap slot (freeing its teacher there), then import a gap-group lesson of that SAME teacher into the freed slot. Runs post-LabDayConsolidation / pre-AnchorRepair in the staged cycle.

**Entry invariant:** Post-smarcatura, asserted: `SchoolSchedule.getPromotedLessonIdsStatic()` must be empty else `IllegalStateException` (`CompositionHoleCloser.java:150-154`). Gated by `SchoolSchedule.isCompositionHoleCloserEnabled()` (flag `planner.composition-hole-closer.enabled`, default OFF, opt-IN prototype; unlike its lab-day sibling it is NOT ratified default-on).

**Exit guarantee:** `Result.conflictsAdded` must be 0 (improve-or-noop by construction through the shared gate chain). A two-move transaction: if no legal same-teacher import exists, the blocker relocation is reverted, so the pass never moves a lesson without closing the gap it targeted.

**Rollback mechanism:** Internal two-move revert when the import has no donor (`CompositionHoleCloser.java:235-239`) plus the outer `ensureImproveOrNoop` in `ScheduleService.runCompositionHoleCloser` (`ScheduleService.java`).

**Gate:** Target-independent guards fire first: M18 anti-shell-game (`SurplusRebalance.keepsDayAnchored`, `CompositionHoleCloser.java:210`), H17 departure (`BoxRepair.isDepartureSafe`, line 214); then the relocation must be H1/H3 conflict-free and pass `BoxRepair.passesPlacementGates` (H14/H11/H7/H17-arrival) in the blocker's VRP value range. Budget-bounded EF-style: `getMaxCandidates()` (knob `planner.composition-hole-closer.max-candidates`, default `1000000`) plus `MAX_MOVES_PER_PASS = 200`.

**Verified-correct scaffold, inert on the known bad seed.** The `isDepartureSafe` gate refuses every mid-run blocker, and on the G11-Wednesday @10 saturated basin every blocker IS mid-run (day packed [0..5]), so the live pass commits 0 cascades / 0 conflicts / H17 unchanged (pinned by `compositionHoleCloserClosesBadSeed`). The offline discriminator proves why: `H17HoleCloseDiscriminatorTest#depth3CrossGroupCascade` shows SUSTAINABLE closes (edge-removable blocker) = 0/6 and SHELL-GAME closes (mid-run blocker, hole migrates) = 6/6. The pass is the correct building block for classroom-day gaps whose blocker IS edge-removable and refuses (does no harm) otherwise; the saturated-basin cure forked to multi-start (TASK-480, Section 4.1). Doc: `claudedocs/spalmone-2026-27/task481-results/FORK-shell-game-depth3-2026-08-02.md`.

### 1.4 Exact Sub-Solver: ExactFinisher

**File:** `solver/ExactFinisher.java`

**Purpose:** Deterministic residual-completion pass that enumerates placements for the small unassigned tail (boxed lessons) the local search misses. It converts the **boxed-tail (H0) closure** from probabilistic to deterministic on the `source=input` path. The total hard-0 remains seed-dependent through the orthogonal H17 residuals (Section 4.3, TASK-481). Ratified default ON (maintainer, 2026-08-02).

**Budget:** `getMaxCandidates()` (system property `planner.exact-finisher.max-candidates`, default `DEFAULT_MAX_CANDIDATES = 1000000`, floored at 1). Read LIVE per repair invocation.

**Depth:** `MAX_DEPTH = 1`. Depth-0 (free cell, no bump) is the workhorse. Depth-1 (one eviction) is the fallback. Depth-2 (two nested evictions) is designed but deferred: not needed on the known worst case.

**Gate chain (cheapest first, VRP last):** H1/H3 conflict, H14/H11/H7/H17-arrival placement gates, H17-departure for bumped lessons, VRP membership last.

**Entry invariant:** Post-smarcatura, no promoted pins held. At most `MAX_K = 30` boxed lessons (`ExactFinisher.java:106`). Gated by `SchoolSchedule.isExactFinisherEnabled` (opt-out).

**Exit guarantee:** `Result.conflictsAdded` must be 0 (the pass is 0-hard-additive by the gate chain). The `CandidateBudget` cap hit is logged at WARN.

**Rollback mechanism:** `ensureImproveOrNoop` in `ScheduleService.runExactFinisher` (`ScheduleService.java`).

### 1.5 CompactionPhase + StudentDayCompaction

**Files:** `solver/CompactionPhase.java`, `solver/StudentDayCompaction.java`

**Purpose:** Phase 3 of the 5-phase pipeline. `CompactionPhase` runs `BoxRepair` (pre-compaction), then `StudentDayCompaction.compact` (gap closing), then a second `BoxRepair` pass if the first left boxed lessons. `StudentDayCompaction` is also used by the construction tail.

**StudentDayCompaction behavior:** MAX-ONLY gap closer. It closes an internal gap by moving the day's LAST (max-slot) lesson down into it. Moving any non-max lesson merely shifts the gap elsewhere (same count), so it is never committed. This means it can only close a gap by compressing the run toward its trailing edge.

**Known limitation:** `StudentDayCompaction` cannot close a gap by **composing** two halves (moving an earlier lesson into a mid-day hole while another lesson fills the vacated earlier slot). The TASK-481 diagnosis identified 6 H17 residuals on the `source=input` path (G11, Wednesday, slot 10) where the construction produces a compact layout but the local search substitutes a non-compactable composition, and neither the max-only compactor nor a 1- or 2-move eviction can close the resulting hole. This is an open fork in the roadmap (Section 4).

**Entry invariant:** The working solution from Phase 2. The occupancy index, group-day slots, and teacher daily counts are built once and kept live.

**Exit guarantee:** `Result.conflictsAdded` must be 0. Gaps are either closed or left untouched. If the max lesson is pinned or cannot reach any gap legally, the day is left as-is.

### 1.6 Pre-Solve Coordinators

These run before the solver starts and publish static data that the constraints and the construction heuristic read during the solve.

#### Constrained Teacher Pre-Assignment (TASK-435)

**File:** `service/HybridPhase1aService.java:111-114`

**Purpose:** Reads `seed/lessons.csv` (the golden timetable) and pre-pins constrained-teacher lessons (teachers squeezed to 1 weekday by the golden pattern) before the solver runs. Ratified default ON (maintainer, 2026-07-30).

**Gate:** `isConstrainedTeacherEnabled()` (system property `planner.pre-assign.constrained-teacher.enabled`, default true, opt-out with `=false`). Called in `HybridPhase1aService.runConstrainedTeacherPhase()` (`line 540`) and in `refreshSolverStatics` (`ScheduleService.java`).

#### Weekly Template M32 Reference (TASK-413)

**File:** `solver/WeeklyTemplatePlanner.java` (build), `service/ScheduleService.java` (publish)

**Purpose:** `buildReference` constructs the per-course template cell set from the unpinned lesson population. `publishWeeklyTemplateStatics` publishes the template and per-course M32 deviation weights as `static volatile` fields on `SchoolSchedule`, read by M32 during the solve.

**Gate:** Called during `refreshSolverStatics` (before every stage of the staged cycle). The population must be the UNPINNED lessons (promotion happens after this).

#### Co-Presenza Depth Map (TASK-464)

**File:** `service/ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth`

**Purpose:** Reads the golden timetable's co-presenza depth per (tag, weekday, slot) cell. Published as `goldenCoPresenceDepthStatic` during `refreshSolverStatics` (`ScheduleService.java`). Consumed by H2's pairwise exemption and H-COPRESENCE cap.

#### Slot-0 Anchors (TASK-451)

**File:** `solver/WeeklyTemplateBuilder.java` (reserveFirstHourBlock)

**Purpose:** Reserves the 2h block (slot 0+1) for the group's anchor teacher (the golden's most common teacher at slot 0 per (group, weekday)). Gated by `isFirstHourSlotEnabled()` (default ON).

### 1.7 The Staged Cycle Driver

**File:** `service/ScheduleService.java` (method `runStagedSolve` and sub-methods)

**Purpose:** Orchestrates the full sequence: A-CONSTRUCTION, PROMOTION, B-TIMESLOT, DEMOTION (stage C), RELEASE, deterministic repair passes, B-ROOM.

**Phase subset extraction:**

- `constructionOnlyConfig()` (`ScheduleService.java`): extracts only the `DayClusteredConstructionPhase` from the solver XML. Found descriptively by class, never by position.
- `timeslotPhasesConfig()` (`ScheduleService.java`): every phase up to and including `CompactionPhase` (the last timeslot mover). `splitAtLastTimeslotPhase` (`ScheduleService.java`) finds the boundary and sanity-checks both halves structurally.
- `roomPhasesConfig()` (`ScheduleService.java`): every phase after `CompactionPhase` (room CH + room LS). These phases move only the `room` variable.

**Runner contract (`ensureImproveOrNoop`):** Every deterministic repair pass is wrapped by a snapshot/revert pair (`PlacementSnapshot.capture` before, `ensureImproveOrNoop` after). If the pass worsens the timeslot-tier hard or adds H1/H3 conflicts, the grid is reverted to the pre-pass snapshot and the cycle continues to B-ROOM on the original grid. This never aborts the cycle (TASK-473).

**Cycle execution order:**

1. A-CONSTRUCTION: `runStagedSolver(constructionOnlyConfig(), problem)` (`ScheduleService.java`).
2. PROMOTION: `promoteTemplatePins()` (`ScheduleService.java`).
3. B-TIMESLOT: `runTimeslotSolveStage()` (`ScheduleService.java`).
4. DEMOTION (stage C): `runDemotionStage()` (`ScheduleService.java`), releases promoted pins for deadlocked boxed lessons.
5. RELEASE: `releasePromotedPinsAndRepublish(true)` (`ScheduleService.java`), releases all remaining promoted pins.
6. HoleRepair: `runHoleRepair(roomProblem)` (`ScheduleService.java`).
7. LabDayConsolidation: `runLabDayConsolidation(roomProblem)` (`ScheduleService.java`).
8. CompositionHoleCloser: `runCompositionHoleCloser(roomProblem)` (`ScheduleService.java`), classroom-day H17 cascade (opt-in, default OFF).
9. AnchorRepair: `runAnchorRepair(roomProblem)` (`ScheduleService.java`).
10. SurplusRebalance: `runSurplusRebalance(roomProblem)` (`ScheduleService.java`).
11. ExactFinisher: `runExactFinisher(roomProblem)` (`ScheduleService.java`).
12. B-ROOM: `runRoomSolveStage(problem)` (`ScheduleService.java`).

## 2. Contracts: Stochastic vs Exact

### 2.1 The Decisional Framework

When does the layer use a stochastic operator (local search inside a Timefold phase, bounded by wall-clock time) versus an exact one (bounded enumeration on the residual)?

| Criterion | Stochastic (LS/metaheuristic) | Exact (enumeration) |
|----------|-------------------------------|---------------------|
| Residual size | Large, many feasible cells | Small (k <= 30 boxed lessons) |
| Feasibility density | High (many legal cells per lesson) | Low (~3-7 feasible cells per lesson among ~1000 VRP-legal) |
| Demonstrable fix | Not proven; many possible moves | Direct scan trivially finds a cell stochastic selection misses |
| Tier | Any | Placement-tier (timeslot hard); repair is room-agnostic (rooms nulled) |
| Completeness claim | None (time-bounded) | Bounded enumeration with early-abort budget |

### 2.2 The Key Insight

The staged B-TIMESLOT phase ALREADY runs tabu search (entity tabu size 7, accepted forager 1000, four move selectors including pillar change and pillar swap) as configured in `solverConfig.xml`. An acceptance-strategy change does NOT widen the move neighbourhood: the exact/repair operators exist precisely for residuals the stochastic phase's neighbourhood **cannot express**. The TASK-481 spike verified this on the G11 Wednesday slot-10 holes: 1-move fill = 0/6, 2-move evict+fill = 0/6. The cell is saturated with teachers for the aula subjects, and no single-entity or single-eviction move can close the hole. "Add a metaheuristic" is not a universal cure; the repair operators handle residuals whose fix requires coordinated multi-entity moves the move selector does not enumerate as atomic steps.

### 2.3 Operator Contracts Summary

| Operator | Entry invariant | Exit guarantee | Revert | Gate |
|---------|----------------|----------------|--------|------|
| StagedPinPolicy | No promoted pins; cold VRP | Stable id-ordered promotion/demotion lists | N/A (promotion is set; demotion is intentional) | M32 `isOnTemplateCell` + BoxRepair gates |
| Tier-scoped hard gate | ConstraintMatchSnapshot from solved schedule | Single `long` timeslot-tier hard | N/A (read-only) | `ROOM_TIER_HARD_FAMILIES` exclusion |
| BoxRepair | Conflict-free grid; live indexes | `conflictsAdded == 0` | `ensureImproveOrNoop` (snapshot revert) | VRP + H1/H3 + H14/H11/H7 + H17 |
| HoleRepair | Post-smarcatura; no promoted pins | `conflictsAdded == 0` | `ensureImproveOrNoop` | Same as BoxRepair + departure gate |
| AnchorRepair | Post-smarcatura; no promoted pins | `donorDaysNewlyLate == 0`; `conflictsAdded == 0` | `ensureImproveOrNoop` | Same as HoleRepair + M18 donor guard |
| SurplusRebalance | Post-smarcatura; no promoted pins | `conflictsAdded == 0`; M20 strictly decreases | `ensureImproveOrNoop` | Same + M18 + M15 departure guards |
| LabDayConsolidation | Post-smarcatura; flag-gated | `conflictsAdded == 0`; transactional revert | `ensureImproveOrNoop` + internal 2-move rollback | Same + anchor-teacher scarcity |
| CompositionHoleCloser | Post-smarcatura (asserted); flag default OFF (opt-in) | `conflictsAdded == 0`; 2-move transactional revert | `ensureImproveOrNoop` + internal 2-move rollback | M18 shell-game + H17 departure + placement gates; budget-bounded; inert on saturated basin |
| ExactFinisher | Post-smarcatura; <= MAX_K boxed | `conflictsAdded == 0`; 0-hard-additive | `ensureImproveOrNoop` | Full chain: H1/H3 + caps + H17 + VRP (cheapest first) |
| CompactionPhase | Phase 2 output | `conflictsAdded == 0` | N/A (framework phase) | Same as BoxRepair |
| StudentDayCompaction | Any conflict-free grid | `conflictsAdded == 0` | N/A (called inside CompactionPhase) | VRP + H1/H3; max-only (see limitation) |

## 3. Literature Positioning

The orchestration layer draws from three bodies of work: hybrid metaheuristics/matheuristics, fix-and-optimize, and multi-phase solver design.

### 3.1 Hybrid Metaheuristics / Matheuristics

Puchinger and Raidl (2005) classify hybrids as **collaborative** (algorithms exchange information but remain separate, run sequentially or in parallel) or **integrative** (one technique is a subordinate component embedded in another). Our layer is predominantly **collaborative sequential**: staged pinning promotes/demotes between separate phases, and the pre-solve coordinators run before the metaheuristic. The one **integrative** exception is ExactFinisher: an exact enumeration subroutine embedded as a repair operator in the staged cycle (Puchinger and Raidl, section 3.1).

Blum, Puchinger, Raidl and Roli (2011) unify hybrid approaches under decomposition, cooperative search, and incorporation of exact components. Our deterministic repair passes (BoxRepair, HoleRepair, AnchorRepair, SurplusRebalance) are "decomposition" in their sense: each operates on a restricted slice of the problem (a hole, a group's days, a surplus/deficit pair) instead of the whole solution.

Talbi (2002) classifies hybrids along low-level vs high-level and relay vs teamwork. Our layer is "high-level relay": phases pass the baton (relay) without sharing internal search state (high-level), unlike a low-level hybrid that would modify the local search move operator itself.

### 3.2 Fix-and-Optimize / LNS

Lindahl, Sorensen and Stidsen (2018) describe a fix-and-optimize matheuristic for university timetabling: fix most variables using domain knowledge, solve a small sub-problem exactly on the rest, iterate over partitions. The F/U (fixed/unfixed) partition is exactly "pinned/unpinned" applied to our whole pipeline, not just the final residual. Our staged pinning (PROMOTION of on-template cells, selective DEMOTION) is the same F/U idea applied at the phase-boundary scale.

Taillard and Voss (2002) define POPMUSIC: decompose the solution into parts, aggregate some into a sub-problem, re-optimize it, repeat. Our HoleRepair and SurplusRebalance are POPMUSIC instances where the part selection is guided by the measured deficit/surplus (not a round-robin over parts).

Pisinger and Ropke (2010) survey LNS: iterate destroy-repair on a large neighborhood; the repair step can be heuristic or exact. Our entire staged cycle (RELEASE + repair + re-solve) is an LNS at phase granularity: "destroy" = selective demotion of promoted pins, "repair" = the deterministic passes or the subsequent local search.

### 3.3 Multi-Phase Solver Design

Muller (ITC2007 hybrid solver, 2008) uses three sequential phases (construction to feasibility, hill climbing, Great Deluge/Simulated Annealing), with feasibility-first then optimization. Our 5-phase pipeline mirrors this: CH placement, LS compactness, deterministic repair, CH rooms, LS room refinement. Phase transitions are decided by observable conditions (score convergence per tier).

Fawcett et al. (ITC2007 track 2) describe a modular multiphase solver where phases are independent and testable. This is the closest precedent to our design: BoxRepair, HoleRepair, AnchorRepair, SurplusRebalance, LabDayConsolidation, CompositionHoleCloser, and ExactFinisher are all independent, testable modules with a shared gate interface (BoxRepair's primitives), rather than a monolithic heuristic.

### 3.4 Contracts and Phase Saving

Zilberstein and Russell (1996) define anytime algorithms as those that return a valid solution at every interruption point, with quality that never degrades (monotonicity). Our improve-or-noop contract with revert makes this operational: the score after a deterministic pass is never worse than before.

Demirovic, Chu and Stuckey (2018) show that solution-based phase saving in CP (remembering the best solution across restarts and orienting variable ordering toward reproducing it) bridges restarts without losing quality. Our staged pinning (M32 template deviation, weekly template promotion) carries forward the best construction's information across the phase boundary into the local search, analogous to phase saving across a restart.

### 3.5 What Is Distinctive

The layer adds three properties that are not central in the literature surveyed above.

**Tier-scoped gating.** The `timeslotTierHardScore` / `ROOM_TIER_HARD_FAMILIES` split lets the staged cycle run a B-TIMESLOT gate that measures only placement progress, blind to room violations that B-ROOM has not yet had a chance to resolve. This is not in any of the references above; they gate on total hard score.

**Verified improve-or-noop with revert.** Every deterministic repair pass is wrapped by a `PlacementSnapshot` capture and an `ensureImproveOrNoop` check that can revert the grid to its pre-pass state. This is a stronger guarantee than anytime monotonicity: it does not depend on wall-clock time but on an explicit pre/post comparison at each application of each operator.

**Phase-boundary operators.** Timefold's phases are first-class (configured in XML, selected by class). Our layer's operators run AT the boundaries between those phases, where Timefold provides no hook. The layer is not modifying move selectors or acceptance criteria inside a phase (a low-level hybrid); it is inserting deterministic whole-schedule passes between them (a high-level relay with measured contracts).

## 4. Roadmap: Missing Operators

The layer's contracts make certain operator slots visible. These are tracked as existing tasks.

### 4.1 True Multi-Start from Independent Constructions (TASK-480). ADOPTED (2026-08-02)

Adopted as an operational procedure (NOT a default flip), validated by A/B. The DRAFT-005 portfolio
(`ScheduleService.java`) runs N B-TIMESLOT solves from the SAME deterministic construction
(`DayClusteredConstructionPhase` / `WeeklyTemplateBuilder`: id-based hashCode, no `Random`;
`PhaseCommandContext` does not expose Timefold's seed to custom phases), so the ~7500 promoted pins
are byte-identical and candidates converge. The cure is to vary the construction BEFORE promotion,
not repeat the LS after it.

**Operator (stochastic, cross-JVM):** GRASP-RCL in `WeeklyTemplateBuilder` (knob
`planner.construction.grasp.enabled` + `.seed`, default OFF = byte-identical; seeded `Random` per
`build()` threaded through `PlacementState`, no static mutable state). Two tie-breaks randomized when
enabled: `startOffset` and `nextContiguousSlot` (uniform reservoir sampling over free slots). Contract:
entry is a staged solve, exit is a solved schedule, rollback is none (pick-best is post-hoc across N),
gate is P(>=1 0-hard) + wall-clock. Orchestrated out-of-process by `scripts/benchmark/multistart-ab.sh`
(fresh `java -jar` boot per seed, pick-best on hard then medium).

**Why out-of-process (not an in-JVM operator):** `SchoolSchedule` publishes 23 `private static
volatile` fields that constraints read during the solve. Two cycles in the same JVM would corrupt
these via shared state, and maintainer overruled an in-process sequential loop. Process-level isolation
(one JVM + one DB profile per boot) is the shallowest mechanism that is also safe.
`SolverManager` / `parallelSolverCount` are community but do not fit the staged cycle (interleaved DB
work). The parallel N-container speedup (~1x wall-clock) is the conditional industrialization, gated
on the A/B (passed).

**Independence precondition (research finding 4a):** the critical @10 cell is tie-break-diversifiable,
not constraint-forced (5/5 teacher-multisets distinct, 13/14 groups vary). Measured at zero-solve cost
(~12s, template-only). Without it, multi-start is dead because constructions would share the basin.

**A/B ground truth (n=3 GRASP + baseline, source=input):** 2/3 GRASP seeds reached 0-hard; one hit the
G11-Wednesday bad basin (-11hard, H17 -10/10match). p_hat(good)=0.667, Wilson 95% CI [0.21, 0.94];
P(>=1 0-hard | N=3) = 0.96 [0.50, 1.00]. Complementary with ExactFinisher (section 1.4): EF closes the
boxed tail (H0) deterministically and is orthogonal to H17 (the H17 cell is H1-blocked); multi-start
selects a basin that avoids H17. They compose: N constructions, each with EF, pick-best. Honest
limitation: n=3 does not size N tightly; the bad-seed frequency is a deferred background measurement
(~10-20 seeds). Plan B structural is Kempe-chain (TASK-482). Full verdict and procedure:
`claudedocs/spalmone-2026-27/task480-results/VERDICT-fase4-multistart-ab-2026-08-02.md`.

**References:** Gomes and Selman (heavy-tailed runtime distributions, restart/portfolio as the canonical cure); Marti, Resende and Ribeiro (multi-start methods); Feo and Resende (GRASP).

### 4.2 ExactFinisher Depth-2 (Deferred)

The `MAX_DEPTH = 1` bound covers depth-0 (direct placement) and depth-1 (one eviction). Depth-2 (two nested evictions) is designed but deferred: the known worst case (TASK-478 discriminator) is fully closed at depth 0/1, so depth-2 is gated on a future A/B proving depth-0/1 insufficient. The implementation challenge is a combined bump+relocation day-map gate that does not yet exist in `BoxRepair.findEviction`.

### 4.3 Composition-Aware Hole Closer (TASK-481)

Closed (TASK-481 Done, 2026-08-02). The 6 H17 residuals on the `source=input` path (G11, Wednesday, slot 10) arise because the construction produces a compact layout, the local search substitutes a non-compactable composition, and neither `StudentDayCompaction` (max-only) nor a 1- or 2-move eviction can close the resulting hole. The gap requires a coordinated 3-move composition that the move selector does not enumerate atomically. Fork resolution (maintainer 2026-08-02): the local closer (directions 1 and 3 below) is excluded because the depth-3 cascade is a shell-game on the saturated @10 basin (the hole migrates to an adjacent slot or another group rather than closing); the chosen cure is multi-start (direction 2, TASK-480, see section 4.1).

Three candidate directions (per `PROPOSTA-cura-h17-2026-08-02.md`):
- **Deterministic depth 2-3 closer:** Extend `StudentDayCompaction` beyond max-only to compose two halves of a day.
- **Multi-start (CHOSEN):** the selected H17 cure. Validated by TASK-480 A/B (2/3 GRASP seeds reach 0-hard, P(>=1 0-hard | N=3) = 0.96). See section 4.1.
- **Coordinated move selector:** Add a problem-specific multi-entity move to the local search that can express the required 3-move composition.

The layer's contracts make this slot visible: `StudentDayCompaction` is the operator that OUGHT to close mid-day gaps but provably cannot close composition-required ones.

### 4.4 Kempe-Chain Move Selector (TASK-482). Plan B structural, deferred.

The structural alternative to multi-start for the saturated-basin H17. Where multi-start pays the Nx wall-clock to re-roll the construction and pick a good basin (Section 4.1), a Kempe chain reorganizes the composition of a slot wholesale by swapping colour-chains between two slots, reaching states the single-entity and pillar move selectors cannot express at any N. The motivation is research finding 3 (Lewis and Thompson): raising the connectivity of the solution space with domain-specific neighbourhood operators systematically improves reachable quality in course timetabling, and the canonical operator for the domain is the Kempe chain.

**Why deferred.** It touches the production local-search path (a custom `MoveSelector` / `MoveIteratorFactory` in the B-TIMESLOT LS), so it carries regression risk and is gated behind an A/B with guards on hard / full-day / ricorrenza. With p_hat(good) = 0.667 from TASK-480, multi-start already meets the reliability target at N = 3, so Kempe-chain opens only if the deferred bad-seed-frequency measurement shows p low or the required N unacceptable. One run, no Nx: the cost argument flips in Kempe-chain's favour only when multi-start's N grows.

**Operator slot the layer exposes.** The move selector's neighbourhood is the thing that cannot express the 3-move composition TASK-481 identified (Section 4.3). A Kempe chain is the missing atomic move that CAN. Prospective contract: entry is the B-TIMESLOT LS, exit is a schedule with the chain applied or rejected (Timefold acceptance), rollback is the move's undo (framework), gate is the standard accept/reject plus the H17-connectivity the chain targets.

### 4.5 Rotation Enumerator (TASK-467)

A scheletro enumeratore for acconciatura rotation candidates, shared with the CSP pre-assignment pipeline. The layer's contracts suggest this as a slot for a pre-solve coordinator that could reduce the combinatorial space before the solver starts.

## 5. Relationship to the Solver Phases

The orchestration layer wraps the 5-phase pipeline described in [Solver Multiphase Architecture](solver-phases.md). Phases 1-3 (timeslot placement, compactness LS, repair + compaction) are the framework's phases. The layer adds:

- The **staged cycle** that splits the pipeline into A-CONSTRUCTION (phase 1 only), PROMOTION (new), B-TIMESLOT (phases 1-3, with promoted pins held), DEMOTION + RELEASE (new), deterministic repair passes (new, between phases 3 and 4), and B-ROOM (phases 4-5).
- The **pre-solve coordinators** that publish static data before phase 1 runs.
- The **tier-scoped gating** that measures timeslot-tier hard independently of room-tier hard.
- The **improve-or-noop contracts with revert** that protect every deterministic pass.
- The **exact sub-solver** (ExactFinisher) that closes the boxed residual deterministically.
