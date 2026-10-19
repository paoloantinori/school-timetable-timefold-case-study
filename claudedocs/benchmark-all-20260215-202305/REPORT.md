# Benchmark Comparison — All Branches

Date: 20260215-202305 (updated 20260215 with 10-min top-3 benchmark)

## Summary (5-minute runs)

| Branch | CH (s) | Hard after CH | Final Hard | Final Medium | Final Soft | Lessons |
|--------|--------|---------------|------------|--------------|------------|----------|
| block-entity | 15 | -7171237 | -1761637 | -4864 | -28981277 | 7281 |
| block-entity (H8 fix, 5min) | ~200 | -7171238 | -4583747 | -2598 | -6193514 | 7281 |
| cartesian-block-entity (broken) | 15 | -7171238 | -7093228 | -1371 | -521998 | 7281 |
| cartesian-block-entity (config fix, 5min) | 15 | -7171238 | -175264 | -5096 | -60051150 | 7281 |
| cartesian-block-entity (config + H8 fix, 5min) | ~190 | -7171238 | -3099124 | -3303 | -16967309 | 7281 |
| cartesian-move-selector | 154 | 0 | -3984454 | -9045 | -40232729 | 10847 |
| cartesian-shadow-lab-blocks | 171 | 0 | -1986006 | -4235 | -36568483 | 8693 |
| master | 202 | 0 | -6477686 | -5446 | -19016609 | 10847 |
| room-phase-block-entity | 15 | -7171237 | -2353936 | -4079 | -20737506 | 7281 |
| room-phase-block-entity (H8 fix, 5min) | ~240 | -7171238 | -5018159 | -2155 | -4523493 | 7281 |
| room-phase-shadow-lab-blocks | 235 | 0 | -3533357 | -3230 | -14645553 | 8693 |
| room-phase | 154 | 0 | -5606531 | -6444 | -26257750 | 10847 |
| shadow-lab-blocks | 170 | 0 | -2546784 | -3845 | -26570770 | 8693 |
| student-compactness | 171 | 0 | -4973461 | -6441 | -30526813 | 10847 |

## 10-Minute Benchmark — Top 3 Branches

| Branch | CH (s) | Hard @ 5min | Hard @ 10min | Medium | Soft | Lessons |
|--------|--------|-------------|--------------|--------|------|---------|
| **cartesian-block-entity** | **~200** | **-2,439k** | **-75,058** | **-4,982** | **-58.7M** | **7,281** |
| cartesian-shadow-lab-blocks | ~300 | -5,498k | -1,079,460 | -4,319 | -51.9M | 8,693 |
| shadow-lab-blocks | ~260 | -4,598k | -1,371,408 | -3,600 | -46.0M | 8,693 |

### 10-Minute Hard Score Progression

| Time | cartesian-block-entity | cartesian-shadow-lab-blocks | shadow-lab-blocks |
|------|------|------|------|
| 60s | -- (CH) | 0 (CH) | 0 (CH) |
| 120s | -- (CH) | 0 (CH) | 0 (CH) |
| 180s | -- (CH) | 0 (CH) | 0 (CH) |
| 240s | -5,163,092 | 0 (CH) | 0 (CH) |
| 300s | -2,438,611 | -5,497,873 | -4,598,156 |
| 360s | -1,276,475 | -4,396,757 | -3,595,967 |
| 420s | -609,922 | -2,877,116 | -2,771,875 |
| 480s | -274,144 | -2,141,405 | -2,269,595 |
| 540s | -122,695 | -1,486,567 | -1,738,006 |
| 600s | **-75,058** | -1,079,460 | -1,371,408 |

## Analysis

### 10-minute results: cartesian-block-entity dominates

With adequate local search time, `cartesian-block-entity` is the clear winner — **14x better** than the runner-up. The improvement rate is also steeper: it drops from -2.4M to -75k in the last 5 minutes (97% reduction), while the shadow branches drop ~75-80% in the same window.

| Metric | cartesian-block-entity | cartesian-shadow-lab-blocks | shadow-lab-blocks |
|--------|----------------------|---------------------------|-------------------|
| Final hard (10min) | **-75,058** | -1,079,460 | -1,371,408 |
| Improvement rate (5-10min) | 97% reduction | 80% reduction | 70% reduction |
| LS time in 10min window | ~400s | ~300s | ~340s |
| Entity count | 7,281 Lessons + LabBlocks | 8,693 Lessons | 8,693 Lessons |

**Why cartesian-block-entity wins:**

1. **Fewer entities** (7,281 vs 8,693): The block-entity model consolidates 2-3 hour lab blocks into single LabBlock entities, reducing the search space by ~17%.
2. **Lesson cartesian product** (timeslot × room): Atomic moves that relocate a lesson to a new timeslot AND room simultaneously, avoiding intermediate dead states where timeslot changed but room is wrong.
3. **Multi-phase search**: LA+Tabu for broad exploration, Pure Tabu for refinement, Room-only for final polish — each phase complements the others.

**Why the 5-minute results were misleading:**

At 5 minutes, block-entity branches appeared worse because their CH takes ~200s (vs ~15s before the H8 fix, or ~170-260s for shadow/baseline). The 10-minute run gives all branches adequate local search time, revealing the true optimization capability.

### H8 fix impact on block-entity branches (5-minute runs)

The H8 fix (counting LabBlock hours toward minimum daily lessons) increased CH time from ~15s to ~200s because the constraint now correctly evaluates lab hours.

| Branch | Before H8 fix (5min) | After H8 fix (5min) | After H8 fix (10min) |
|--------|---------------------|---------------------|----------------------|
| block-entity | -1,761,637 | -4,583,747 | — |
| cartesian-block-entity | -225,954 (config fix) | -3,099,124 | **-75,058** |
| room-phase-block-entity | -2,353,936 | -5,018,159 | — |

## Hard Score Progression (original 5-minute runs, all branches)

| Time | block-entity | cartesian-block-entity (broken) | cartesian-block-entity (config fix) | cartesian-move-selector | cartesian-shadow-lab-blocks | master | room-phase-block-entity | room-phase-shadow-lab-blocks | room-phase | shadow-lab-blocks | student-compactness |
|------|------|------|------|------|------|------|------|------|------|------|------|
| 60s | -- | -- | -587674 | 0 | 0 | 0 | -- | 0 | 0 | 0 | 0 |
| 120s | -- | -- | -4339571 | 0 | 0 | 0 | -- | 0 | 0 | 0 | 0 |
| 180s | -5284201 | -7162344 | -1841830 | -9583035 | -5218031 | 0 | -6102098 | 0 | -9683466 | -5426246 | -10302867 |
| 240s | -3067662 | -7118396 | -667221 | -6554227 | -3310896 | -9186059 | -3481831 | -5364031 | -7344561 | -3762265 | -7691444 |
| 300s | -1978210 | -7100868 | -225954 | -4303530 | -2194214 | -6907336 | -2489650 | -3791833 | -5951109 | -2750041 | -5339547 |

## Fix Notes

### Fix 1: cartesian-block-entity config (commit 6412825)

The original config had a solver deadlock caused by:
1. LabBlock cartesian product selector generating expensive shadow-variable moves
2. `acceptedCountLimit=1000` with only tabu search (no late acceptance)
3. Single local search phase instead of multi-phase

Fix applied: removed LabBlock cartesian, added 3-phase local search (LA+Tabu → Pure Tabu → Room-only),
restored `lateAcceptanceSize=400` and `acceptedCountLimit=1` in phase 1. Kept Lesson cartesian as branch differentiator.

### Fix 2: H8 LabBlock hour counting (commit 1357311)

H8 (minimum student group daily lessons) was only counting Lesson entities. LabBlock hours
(~3566 out of 10847 total) were invisible to the constraint. Fix uses `concat()` to merge
Lesson counts (1 hour each) with LabBlock sums (blockSize hours each), then re-groups to get
total instructional hours. Propagated to all 3 block-entity branches.
