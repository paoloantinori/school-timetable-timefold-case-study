# Research: Analysis of Gemini's Two Solver Strategies

**Date**: 2026-02-14
**Depth**: Exhaustive
**Scope**: Evaluate Strategy 1 (Phased) and Strategy 2 (Cartesian Product RoomSlot) against actual codebase and Timefold documentation

---

## Executive Summary

Gemini's diagnosis — "Search Space Explosion" causing the solver to get "stuck" — is **incorrect for this codebase**. The solver achieves 0 hard violations across 10,847 lessons. The problem is soft score quality (student compactness), not feasibility or search space navigation.

**Neither strategy warrants a spike.** Strategy 1 is already essentially implemented (it's a config tweak, not an architecture). Strategy 2 is actively harmful — it 18x's the value domain, has zero Timefold documentation support, and its core uniqueness claim is factually wrong.

| | Strategy 1: Phased | Strategy 2: RoomSlot |
|---|---|---|
| **Spike effort** | Trivial (XML config) | Very high (full rewrite) |
| **Risk** | None | High — unsupported pattern |
| **Expected impact on compactness** | None | Negative |
| **Recommendation** | Don't spike — just add 10 lines of XML if curious | Do not implement |

---

## The Flawed Diagnosis

Gemini claims: *"The solver is blindly trying to pick a time and a room simultaneously for thousands of items, and it's likely getting stuck in local optima or just thrashing."*

**Evidence against this:**

1. **0 hard violations** — the solver navigated the search space well enough to satisfy ALL hard constraints (teacher conflicts, room conflicts, student group conflicts, stage blackouts, holidays, max daily hours) across 10,847 lessons.

2. **The solver doesn't enumerate the search space.** Timefold uses neighborhood-based metaheuristics (Late Acceptance, Tabu Search), not brute-force enumeration. Move selectors generate individual variable changes — the solver never evaluates "all timeslot×room combinations."

3. **Variables are already assigned sequentially.** The construction heuristic uses `FIRST_FIT_DECREASING` with timeslot first, room second. Local search uses per-variable `changeMoveSelector` with `variableName` targeting.

4. **Room is already de-prioritized.** `allowsUnassigned = true` + MEDIUM penalty means the solver naturally focuses on timeslot correctness (hard constraints) and treats room assignment as secondary.

**The actual problem:** The score landscape has many local optima for soft constraints. Improving student compactness requires coordinated multi-lesson moves (moving several lessons for the same student group on the same day simultaneously). This is a **move selector** problem, not a **data model** problem.

---

## Strategy 1: Phased Approach — Detailed Analysis

### What Gemini Proposes
- Phase 1: Assign timeslots only, ignore rooms
- Phase 2: Assign rooms only, lock timeslots

### What You Already Have
- Construction heuristic: timeslot first, room second (sequential)
- Room is nullable (`allowsUnassigned = true`) — can be left unassigned
- M1 medium penalty incentivizes room assignment without requiring it
- Local search phases already target specific variables via `valueSelector variableName`

### Per-Variable Pinning Doesn't Exist

Timefold's `@PlanningPin` pins the **entire entity** (all variables). Per-variable pinning is an open feature request (GitHub issue #1321, January 2025, still in "needs triage").

The **workaround** is move selector restriction — if a local search phase only has `changeMoveSelector` for `room`, timeslots are effectively frozen because no moves can change them. This is purely a `solverConfig.xml` change.

### Shadow Variable Suggestion is Wrong

Gemini suggests making Room a "shadow variable" in Phase 1. In Timefold, shadow variables are **automatically computed** from other planning variables (e.g., arrival time in vehicle routing is computed from the route order). Room cannot be a shadow variable unless its value is deterministically derived from timeslot — which it isn't.

### Impact Assessment

Adding a room-only Phase 3 would:
- Marginally improve room assignment quality (reduce medium score)
- Have **zero effect** on student compactness (entirely timeslot-driven)
- Take ~10 lines of XML to implement — not worth a spike

```xml
<!-- This is all it would take -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
  </termination>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
  <acceptor>
    <entityTabuSize>7</entityTabuSize>
  </acceptor>
  <forager>
    <acceptedCountLimit>1000</acceptedCountLimit>
  </forager>
</localSearch>
```

### Verdict: Not worth a spike

It's a config tweak. If you want to try it, add the XML block above. No code changes needed.

---

## Strategy 2: Cartesian Product RoomSlot — Detailed Analysis

### What Gemini Proposes
- Create a `RoomSlot` class combining `Timeslot` + `Room`
- Pre-generate all valid combinations
- Replace both planning variables with a single `RoomSlot` variable

### Problem 1: Search Space INCREASES 18x

| Approach | Values per lesson |
|---|---|
| Current (separate variables) | 1,736 timeslots + 18 rooms = 1,754 |
| RoomSlot (combined) | 1,736 × 18 = **31,248** |

Per move evaluation, the solver must consider 18x more candidate values. This is the opposite of what Gemini claims.

### Problem 2: Zero Timefold Documentation

The exhaustive research found **no examples, no documentation, no community discussions** of combining timeslot+room into a single planning variable in Timefold (or OptaPlanner). The official school timetabling quickstart uses two separate `@PlanningVariable` fields — exactly what you have.

### Problem 3: Uniqueness Claim is Wrong

Gemini: *"If Lesson 1 has Mon-9:00-RoomA, Lesson 2 cannot have it."*

This is **factually incorrect**. In Timefold, planning variables are not unique — multiple entities CAN have the same value. The solver doesn't enforce value uniqueness. You would still need a conflict constraint (equivalent to H2 roomConflict). The only way to get uniqueness is `@PlanningListVariable` (which has a completely different semantic — list-based routing, not assignment).

### Problem 4: Breaks Nullable Rooms

Currently, lessons can have a timeslot but no room (allowed via `allowsUnassigned`). With RoomSlot, a lesson either has both or neither. To preserve nullable rooms, you'd need to generate "timeslot-only" RoomSlot values (adding ~1,736 more, total ~33K values). The M1/M2 constraint logic would need complete redesign.

### Problem 5: Rewrite Scope

Every piece of code that references `lesson.timeslot` or `lesson.room` must change:

| Component | Files affected | Changes |
|---|---|---|
| All 17 constraints | 1 file, ~300 lines | Every constraint method |
| Lesson entity | 1 file | Replace 2 vars with 1 |
| SchoolSchedule | 1 file | Value range provider |
| New RoomSlot class | 1 new file | New domain class |
| REST endpoints | ~10 files | JSON serialization |
| Frontend | ~5 files | Data model parsing |
| Tests | 1 file, ~700 lines | All 43 tests |
| DataGenerator | 1 file | RoomSlot generation |
| solverConfig.xml | 1 file | Move selectors |

**Estimated: 20+ files, 1000+ lines changed.**

### Problem 6: Pillar Moves Break

Pillar moves group entities sharing the same variable value. Currently, pillar moves on timeslot group all lessons at the same time — useful for coordinated scheduling. With RoomSlot, pillar moves would only group lessons at the same time AND room — a much smaller and less useful set.

### Verdict: Do NOT implement

Actively harmful. Increases value domain 18x, based on incorrect uniqueness assumption, requires full rewrite, breaks nullable rooms, has zero framework support.

---

## What Would Actually Help

The real bottleneck is the **move selector quality** for soft constraint optimization. Here are three evidence-based improvements, all configuration-level:

### 1. Cartesian Product MOVE SELECTOR (not variable)

Timefold supports `cartesianProductMoveSelector` with mimic selection — this changes timeslot+room atomically for the same entity. This helps when a good timeslot requires a different room.

```xml
<cartesianProductMoveSelector>
  <changeMoveSelector>
    <entitySelector id="sameEntity"/>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <entitySelector mimicSelectorRef="sameEntity"/>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</cartesianProductMoveSelector>
```

This is the **correct** version of what Gemini was trying to achieve with Strategy 2 — atomic time+room moves — without changing the data model.

### 2. Nearby Selection for Timeslot Moves

Instead of randomly selecting from all 1,736 timeslots, prefer timeslots on the same day or adjacent days. This dramatically improves convergence for compactness constraints because moves are more likely to create consecutive lesson blocks.

Requires implementing `NearbyDistanceMeter<Lesson, Timeslot>` that measures temporal distance.

### 3. Extend Solve Time

Current total: ~300 seconds (180 + 120 unimproved limits). For 10,847 entities, this is quite short. Many production school scheduling systems run for 10–30 minutes. Simply increasing `unimprovedSecondsSpentLimit` would give the solver more time to escape local optima.

---

## Recommendation

**Do not spike either strategy.** Instead:

1. **Try the room-only Phase 3** (10 lines of XML, zero risk) if you want to improve room assignment
2. **Add cartesianProductMoveSelector** to existing phases (documented Timefold pattern, medium effort)
3. **Increase solve time** to 10+ minutes for production runs (trivial config change)
4. **Consider nearby selection** for timeslot moves (requires one Java class + XML config)

These are all incremental, reversible, configuration-level changes — no data model risk, no constraint rewrites, no spike needed.

---

## Sources

- Timefold Solver documentation: Construction Heuristics, Local Search, Move Selectors
- Timefold GitHub: Issue #1321 (per-variable pinning feature request)
- Timefold School Timetabling Quickstart (official example with two separate planning variables)
- Timefold contributor Christopher Chianelli: workaround for per-variable pinning via ValueRangeProvider
- Codebase analysis: SchoolScheduleConstraintProvider.java, Lesson.java, solverConfig.xml, DataGenerator.java
