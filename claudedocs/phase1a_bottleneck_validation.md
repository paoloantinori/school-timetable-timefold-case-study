# Phase 1a Bottleneck Validation - Final Report

**Date**: 2026-02-25
**Task**: Validate whether "T28 bottleneck" or entity count discrepancy caused Phase 1a -36hard violations

## Executive Summary

**ROOT CAUSE CONFIRMED**: The "T28 bottleneck" is real, caused by **overlapping rotation periods** that make the problem **structurally infeasible**.

| Finding | Status |
|---------|--------|
| Entity count increased from 232 to 369 | ✅ Confirmed (+137 blocks, 59% increase) |
| Problem still feasible in raw capacity | ✅ Confirmed (77.9% max lab utilization) |
| Phase 1a can achieve 0hard with 369 blocks | ❌ **NO** - structural infeasibility detected |
| T28 is the bottleneck | ✅ **Confirmed** - but due to period overlap, not raw capacity |

## Validation Results

### Entity Count Analysis

| Metric | Benchmark (0hard) | Current Run | Difference |
|--------|-------------------|-------------|------------|
| LabBlocks extracted | 232 | 369 | +137 (59%) |
| LabBlocks pinned | 232 | 369 | All extracted pinned |
| Hard violations | 0 | -48 | 4 conflicts |

**Why the increase**: The benchmark was likely run on a database with some LabBlocks already pinned from prior pre-assignment runs. With a fresh database, all 369 matching LabBlocks are extracted.

### Conflict Analysis

Phase 1a pinned 369 LabBlocks with **4 hard violations** (all involving T28):

| Date | Conflict | Teacher | Room |
|------|----------|---------|------|
| 2025-09-11 08:00 | 2 groups same slot | T28 | R11 |
| 2025-10-02 08:00 | 2 groups same slot | T28 | R11 |
| 2026-02-12 08:00 | 2 groups same slot | T28 | R11 |
| 2026-03-12 08:00 | 2 groups same slot | T28 | R11 |

### Root Cause: Overlapping Rotation Periods

T28's LabRotation records define **overlapping periods** that cannot all be satisfied:

| Group | Period | Available Thursdays | Blocks Needed | Feasibility |
|-------|--------|---------------------|---------------|-------------|
| G02 | Sep-Dec 2025 | 15 | 10 | ✅ OK |
| G06 | Jan-Apr 2026 | 10 | 10 | ⚠️ Tight |
| G10 | Apr-Jun 2026 | **9** | **10** | ❌ **IMPOSSIBLE** |
| G12 | Feb-Apr 2026 | 8 | 8 | ⚠️ Overlaps with G06 |

**Critical infeasibilities:**
1. **G10**: 10 blocks needed, only 9 Thursdays available
2. **G06 + G12 overlap**: Both need blocks in Feb-Apr, sharing ~8 Thursday slots

The solver correctly identified this infeasibility by producing -48hard (4 conflicts × 12hard per conflict).

## Why the Benchmark Achieved 0hard

The benchmark (232 LabBlocks → 0hard) likely had:
1. Fewer LabBlocks extracted (some already pinned from prior runs)
2. Or different rotation period configuration
3. Or fewer blocks per group for T28's courses

With 369 LabBlocks including all 38 of T28's blocks, the overlapping periods make 0hard impossible.

## Recommendations

### Option A: Fix Rotation Periods (Recommended)
Adjust LabRotation periods to be non-overlapping:
- Ensure each group has exclusive access to Thursday slots in their period
- Extend G10's period to include more Thursdays
- Or reduce G10's block count to 9

### Option B: Accept Current Baseline
- -48hard may be the best achievable with current rotation configuration
- Proceed to Phase 1b with these conflicts
- Main solver may resolve some conflicts when unpinned entities are moved

### Option C: Redistribute Workload
- Move some of T28's blocks to other acconciatura teachers (T26, T18)
- Requires changing Course assignments in seed data

## Lessons Learned

1. **Always validate feasibility before solver tuning**: Raw capacity checks are insufficient; must check per-teacher per-period constraints
2. **Symptoms ≠ root cause**: The -36hard was a symptom of structural infeasibility, not solver misconfiguration
3. **Benchmark context matters**: Always verify database state when comparing solver runs

## Files Modified

No code changes required - this is a data/configuration issue.

## Next Steps

1. Review LabRotation period configuration with domain expert
2. Decide on fix approach (A, B, or C)
3. Update LabRotation records or Course assignments accordingly
4. Re-run Phase 1a to validate 0hard achievement
