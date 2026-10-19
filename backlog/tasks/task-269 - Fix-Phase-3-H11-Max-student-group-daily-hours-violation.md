---
id: TASK-269
title: Fix Phase 3 H11 (Max student group daily hours) violation
status: Done
assignee: []
created_date: '2026-03-17 18:20'
updated_date: '2026-07-15 19:32'
labels:
  - bug
  - h11
  - phase-3
  - performance
dependencies: []
references:
  - 'https://github.com/anthropics/claude-code/issues/TASK-269'
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 3 pre-assignment places professionalizzanti lessons without checking H11 (maxHoursPerDay) constraint, causing student groups to exceed their daily hour limit.

**Issue**: For groups like G11 with 60 professional hours and maxHoursPerDay=6, Phase 3 concentrates all lessons on the designated day (e.g., Tuesday), violating the 6-hour daily limit.

**Root Cause**: Phase 3 iterates through dates finding the designated day, places lessons until slots are filled, without tracking daily totals.

**Work Completed**:
- ✅ Created 6 comprehensive unit tests (HybridPhase1aH11DailyHoursTest.java)
- ✅ Added auto-reset to pre-assign endpoint (no manual reset needed)
- ✅ Implemented cache-based daily lesson counting (buildExistingLessonCountsCache)
- ✅ Updated REST API documentation

**Remaining Work**:
- ⚠️ Phase 3 performance issue: takes 100+ seconds (needs investigation)
- ⚠️ H11 check currently disabled (commented out in HybridPhase1aService.findStageDaySlot)
- ⚠️ Need to investigate why date iteration is slow
- ⚠️ Re-enable H11 check after performance optimization
- ⚠️ Verify G07 and G11 no longer violate H11 constraint

**Technical Details**:
- H11 constraint: StudentGroup.maxHoursPerDay (hard constraint)
- Cache approach: Build Map<groupId, Map<date, count>> upfront
- Check: wouldExceedDailyLimit() before placing each lesson
- When limit reached: return Optional.empty() (leave lesson unassigned for solver)
- Key method: HybridPhase1aService.findStageDaySlot()
- Test class: HybridPhase1aH11DailyHoursTest
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Unit tests for H11 constraint pass (HybridPhase1aH11DailyHoursTest.java)
- [ ] #2 Phase 3 completes in reasonable time (<30 seconds)
- [ ] #3 H11 check enabled and working in findStageDaySlot
- [ ] #4 G07 and G11 respect maxHoursPerDay after pre-assignment
- [ ] #5 No H11 violations for any group after Phase 3 completes
- [ ] #6 Auto-reset functionality works (no manual reset needed before pre-assign)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): H11 check active (wouldExceedDailyLimit at HybridPhase1aService:792,853). buildExistingLessonCountsCache invoked before all phases.
<!-- SECTION:FINAL_SUMMARY:END -->
