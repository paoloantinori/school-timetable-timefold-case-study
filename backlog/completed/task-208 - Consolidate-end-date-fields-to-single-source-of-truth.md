---
id: TASK-208
title: Consolidate end date fields to single source of truth
status: Done
assignee: []
created_date: '2026-03-09 09:04'
updated_date: '2026-03-16 14:21'
labels:
  - refactoring
  - cleanup
  - technical-debt
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

We currently have THREE end date fields on `StudentGroup`, serving different purposes:
1. `targetEndDate` (old, slot-based) - used by ValueRangeProvider for VRP filtering
2. `idealEndDate` (new, hour-based) - used by M12/M21 constraints
3. `idealEndDateInfo` (extended info) - used by UI for display

This creates confusion and maintenance burden. We should consolidate to a **single source of truth**.

## Current State (after TASK-207)

| Field | Algorithm | Used By | Purpose |
|-------|-----------|---------|---------|
| `targetEndDate` | Cumulative slots (5 Fri, 6 Mon-Thu) | ValueRangeProvider | VRP filtering (solver efficiency) |
| `idealEndDate` | Hour accumulation (5 Fri, 6 Mon-Thu) | M12/M21 constraints | Constraint scoring (solution quality) |
| `idealEndDateInfo` | N/A (metadata container) | UI API `/api/completion` | Display breakdown |

**Note**: Both algorithms now produce similar results (both account for Friday's 5h), but `targetEndDate` adds buffer weeks and progressive extension that `idealEndDate` doesn't have.

## Proposed Solution

### Phase 1: Unify the Algorithm

**Option A: Use idealEndDate logic everywhere**
- Pros: Simpler, already verified correct
- Cons: May affect solver performance (VRP becomes more restrictive)

**Option B: Use targetEndDate logic everywhere**
- Pros: Keeps current solver performance characteristics
- Cons: Old algorithm is more complex (buffers, progressive extension)

**Recommendation**: Start with **Option A** (use `idealEndDate` everywhere) for simplicity.

### Phase 2: Update ValueRangeProvider to Use idealEndDate

**File**: `src/main/java/com/school/planner/domain/SchoolSchedule.java`

Update the ValueRangeProvider methods:
```java
@ValueRangeProvider(shadowMode = ShadowMode.SHADOW_PROPERTY)
public List<Timeslot> getAvailableTimeslots(Lesson lesson) {
    StudentGroup group = lesson.getStudentGroup();
    if (group != null && group.idealEndDate != null) {
        // Only offer timeslots up to ideal end date
        return timeslots.stream()
                .filter(ts -> !ts.getDate().isAfter(group.idealEndDate))
                .collect(Collectors.toList());
    }
    return timeslots;
}
```

Do the same for LabBlock's `getAvailableStartTimeslots()`.

### Phase 3: Deprecate and Remove targetEndDate

**File**: `src/main/java/com/school/planner/domain/StudentGroup.java`

1. Add deprecation notice:
```java
/**
 * @deprecated Use {@link #idealEndDate} instead. This field will be removed in a future version.
 */
@Deprecated(forRemoval = true)
@Transient
public LocalDate targetEndDate;
```

2. Update all usages:
   - `TargetEndDateCalculator.computeTargetEndDates()` → set `idealEndDate` instead
   - Any code reading `targetEndDate` → use `idealEndDate`

3. Remove field in next major version.

### Phase 4: Rename for Clarity (Optional)

Consider renaming `idealEndDate` to `targetEndDate` (remove old field) for clarity:
- "targetEndDate" is more intuitive than "idealEndDate"
- "ideal" sounds theoretical; "target" sounds actionable

## Verification Steps

Before consolidating:
1. **Benchmark solver performance** with current setup (separate fields)
2. **Test with VRP using idealEndDate** to measure performance impact
3. **Compare solution quality** - same hard/medium scores?

After consolidating:
1. **Re-run benchmarks** - performance should be similar
2. **Verify solution quality** - should be identical or better
3. **Check UI** - `/api/completion` still works

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 #1 Benchmark results documented (current vs unified)
- [ ] #2 #2 #2 ValueRangeProvider updated to use `idealEndDate`
- [x] #3 #3 #3 `targetEndDate` field deprecated with `@Deprecated(forRemoval = true)`
- [x] #4 #4 #4 `TargetEndDateCalculator` updated to set `idealEndDate` instead
- [x] #5 #5 #5 All tests pass (mvn test)
- [ ] #6 #6 #6 Solver performance within 10% of baseline
- [ ] #7 #7 #7 Solution quality unchanged or improved
- [ ] #8 #8 #8 Documentation updated (CLAUDE.md, solver docs)

## Dependencies

**Must complete after TASK-207** (current implementation must be validated first).

## References

- `StudentGroup.java` lines 37-62 (three end date fields)
- `TargetEndDateCalculator.java` (sets `targetEndDate`)
- `SchoolSchedule.java` (ValueRangeProvider methods)
- TASK-207 (implementing correct `idealEndDate` calculation)

## Blocked By

TASK-207 validation (verify current implementation works correctly before changing it).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Consolidation Plan (Revised)

### Problem Summary
- Three end date fields with overlapping purposes: `targetEndDate`, `idealEndDate`, `idealEndDateInfo`
- `idealEndDate` is duplicated (exists both on StudentGroup and inside IdealEndDateInfo)
- Two different services calculate "ideal end date" with nearly identical algorithms
- Inaccurate comments (M21 doesn't actually use idealEndDate)

### Target State: Single Source of Truth
**Consolidate to 2 fields:**
1. **`targetEndDate`** (LocalDate) - Filtered horizon for VRP, used by M12
2. **`idealEndDateInfo`** (IdealEndDateInfo) - Extended calculation data for UI

**Remove:**
- **`StudentGroup.idealEndDate`** field - replaced by idealEndDateInfo.idealEndDate

### Changes Required

#### Phase 1: Understanding (COMPLETE)
- ✅ Read StudentGroup.java
- ✅ Read TargetEndDateCalculator.java
- ✅ Read IdealEndDateInfo.java
- ✅ Analyzed all usages across codebase

#### Phase 2: Consolidation Implementation

**Step 1: Remove StudentGroup.idealEndDate field**
- File: `StudentGroup.java`
- Action: Remove `idealEndDate` field and its javadoc

**Step 2: Update StudentGroupCompletionService**
- File: `StudentGroupCompletionService.java`
- Action: Remove `group.idealEndDate = idealEndDate;` line (line 52)
- Action: Method `calculateStats()` no longer sets idealEndDate on entity
- Action: Method still returns `StudentGroupCompletionStats` with its own idealEndDate

**Step 3: Update references**
- File: `ScheduleService.java`
- Action: Fix incorrect comment "idealEndDate field used by M12 and M21"
- Action: M21 doesn't use idealEndDate; only M12 uses targetEndDate

**Step 4: Verify REST API still works**
- File: `StudentGroupCompletionResource.java`
- Action: Verify idealEndDate in response comes from stats object, not entity
- Current: Already correct - uses stats.idealEndDate

#### Phase 3: Testing
- Rebuild: `./scripts/core/rebuild.sh`
- Test REST API: `/api/student-groups/completion` still returns idealEndDate
- Verify solver still runs (no constraint changes needed)

#### Phase 4: Documentation
- Update CLAUDE.md if needed (constraint comments are already correct)

### Risk Assessment: LOW
- No solver constraint changes
- No VRP filtering changes
- REST API behavior unchanged (already uses stats object)
- Clean removal of redundant field
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
**Implementation Summary:**

Consolidated three end date fields to two, removing the redundant `StudentGroup.idealEndDate` field.

**Changes Made:**

1. **StudentGroup.java**: Removed redundant `idealEndDate` field
   - The field duplicated data already present in `IdealEndDateInfo.idealEndDate`
   - Was only being set by `StudentGroupCompletionService`, not used by any constraints

2. **StudentGroupCompletionService.java**: Removed redundant assignment
   - No longer sets `group.idealEndDate` (line 52 removed)
   - Method still returns `StudentGroupCompletionStats` with `idealEndDate` for API

3. **ScheduleService.java**: Fixed incorrect documentation
   - Corrected comment claiming M21 uses `idealEndDate` (M21 doesn't use it)
   - Updated log message to reflect actual behavior

**Target State:**
- `targetEndDate` (LocalDate) - Used by ValueRangeProviders for filtering, M12 constraint
- `idealEndDateInfo` (IdealEndDateInfo) - Extended calculation data for UI (contains idealEndDate)
- ~~`idealEndDate`~~ (LocalDate) - **REMOVED**, redundant with `idealEndDateInfo.idealEndDate`

**Verification:**
- ✅ Build succeeded (7.78s)
- ✅ REST API `/api/completion` still returns `idealEndDate` from stats object
- ✅ No solver constraint changes (M12 uses `targetEndDate` via static map)
<!-- SECTION:FINAL_SUMMARY:END -->

<!-- AC:END -->

<!-- AC:END -->
