# CH-Only Benchmark: Custom DayClustered vs ALLOCATE_FROM_POOL (2026-03-24)

> 📌 **Session memory** (2026-03-24). Experimental results — configs/scores are point-in-time; re-run before citing.

## Experiment: Custom CH vs ALLOCATE_FROM_POOL

Tested using `solverConfig-ch-only.xml` (no LS phases, CH only, ~10s runs).

### Baseline: Custom CH + Queue Fallback

Config: Phase 1 (DayClusteredConstructionPhase) → Phase 2 (ALLOCATE_ENTITY_FROM_QUEUE fallback for 277 unplaced lessons)

Score: **-2002hard / -1,954,383medium / -189,930soft**

Key constraint breakdown:
- H7 (teacher daily max): -106 (3502 matches)
- H11 (student daily max): -136 (59 matches)
- H15 (same-day same-subject consecutive): -1380 (13 matches)
- H17 (vertically compact): -68 (53 matches)
- M22 (calendar compactness): -287 (8 matches)
- M20 (daily quota fill): -1,944,000 (754 matches — many sparse days)

### Pool Only: ALLOCATE_FROM_POOL (no custom phase)

Config: Phase 1 (Pool timeslot) → Phase 2 (Pool room)

Score: **-3627hard / -432,998medium / +15,614soft**

Key constraint breakdown:
- H7 (teacher daily max): -2114 (1916 matches) — **20x worse**
- H11 (student daily max): -1489 (355 matches) — **11x worse**
- H15 (same-day same-subject consecutive): 0 — better
- H17 (vertically compact): -24 (12 matches) — better
- M20 (daily quota fill): -43,000 (16 matches) — **45x better**
- M21 (max 1h/subject/day): -389,600 (1003 matches) — 45x worse

### Pool + Custom CH (custom CH first, pool fallback for timeslot, queue for room)

Score: **-2002hard / -1,954,383medium / -189,930soft** — identical to queue fallback

## Key findings

1. **Custom CH wins hard constraints by 81%** (-2002 vs -3627). Day-clustered planning prevents teacher/student daily overload.
2. **Pool CH wins medium/soft by 78%.** Global score awareness produces better distribution and fewer sparse days.
3. **Fallback CH type (queue vs pool) doesn't matter** — the 277 unplaced lessons are the bottleneck, not the fallback strategy.
4. **ALLOCATE_FROM_POOL has no day-level awareness** — it picks the globally best entity-value pair each step but doesn't plan "fill Monday for group X, then Tuesday."
5. **Timefold 1.31.0 ALLOCATE_FROM_POOL limitation**: only supports a single planning variable. Must use sequential CH phases for multi-variable problems (timeslot then room).

## ALLOCATE_FROM_POOL XML configuration lessons

Correct structure for single-variable pool:
```xml
<constructionHeuristic>
    <entitySorterManner>DECREASING_DIFFICULTY</entitySorterManner>
    <valueSorterManner>INCREASING_STRENGTH</valueSorterManner>
    <pooledEntityPlacer>
        <changeMoveSelector>
            <entitySelector>
                <entityClass>com.school.planner.domain.Lesson</entityClass>
                <cacheType>PHASE</cacheType>
            </entitySelector>
            <valueSelector variableName="timeslot"/>
        </changeMoveSelector>
    </pooledEntityPlacer>
</constructionHeuristic>
```

- Do NOT set `<constructionHeuristicType>ALLOCATE_FROM_POOL</constructionHeuristicType>` when using explicit `<pooledEntityPlacer>` — it causes `IllegalArgumentException`.
- Do NOT use `cartesianProductMoveSelector` inside `pooledEntityPlacer>` — the factory's `buildUnfoldedEntityPlacerConfig` flattens it and then rejects multiple move selectors.

## Takeaway

The custom CH's day-clustered approach is fundamentally better for school timetabling because it plans per-day capacity, preventing teacher/student overload. Pool CH lacks this structure. Keep `DayClusteredConstructionPhase` as Phase 1; the fallback CH type (queue vs pool) is not a meaningful lever — focus improvements on the custom CH itself or on LS phases.
