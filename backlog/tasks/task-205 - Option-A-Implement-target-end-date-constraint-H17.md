---
id: TASK-205
title: 'Option A: Implement target end date constraint (H17)'
status: Done
assignee: []
created_date: '2026-03-09 06:44'
updated_date: '2026-07-15 19:33'
labels:
  - constraint
  - compaction
  - hard-constraint
  - implementation
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Option A: Implement "Target End Date" Constraint (RECOMMENDED)

Calculate each student group's ideal end date and enforce it as a hard constraint.

### Implementation

1. **Target End Date Calculation Service ✅ COMPLETED**
   - **DONE**: `StudentGroupCompletionService.java` implemented (2026-03-09)
   - Algorithm: Walk calendar from Sept 10, counting school days (6h/day)
   - Excludes: weekends, holidays, stage dates
   - REST API: `GET /api/completion/{groupName}` returns:
     - `idealEndDate`: Calculated target date
     - `actualEndDate`: Last scheduled lesson/LabBlock
     - `daysDifference`: actual - ideal (positive = late)
   - **This service can be reused for H17 constraint calculation**

2. **Add H17: Student Group Target End Date Constraint**
   - Hard constraint: `1hard` per violation per lesson
   - Penalize lessons scheduled AFTER group's ideal end date
   - Exception: Lessons during stage period are allowed (professionalizzanti)
   - Use `StudentGroupCompletionService.calculateIdealEndDate()` logic

3. **Update Feasibility Badge**
   - Check H17 violations in addition to current medium score check
   - Message: "Compaction requirements not met" + show which groups exceed target
   - Use `/api/completion` for diagnostic display

4. **Add maxHoursPerDay to StudentGroups**
   - Set `maxHoursPerDay = 6` on all StudentGroup entities
   - Activates H11 constraint (currently inactive due to null values)

### Design Decisions

**RESOLVED via completion API:**
- **Q**: Should target end date include stage days? **A**: No, stage days are excluded from count
- **Q**: Should holidays be added or skipped? **A**: Skipped (not counted as school days)
- **Q**: What tolerance is acceptable? **A**: TBD (user decision)
- **Q**: How to handle groups with long stage periods? **A**: Stage days excluded automatically

### Expected Outcome

- Groups finish ON TIME (within target tolerance)
- No more front-loading (586 hours before stage for G09 → more even distribution)
- No more late finish (118 days late for G09 → within tolerance)
- Compaction badge passes when H17 = 0

### Verification

Use completion API to verify H17 effectiveness:
```bash
# Before H17: See how late groups finish
GET /api/completion/3%20MODA

# After H17: Should see daysDifference near 0
GET /api/completion/3%20MODA
```

### Risks

- May be over-constraining: Infeasible if stage/holidays make target impossible
- Need user decision on tolerance (±3 days? ±5 days?)
- G09 is 118 days late - H17 would be very expensive for solver

## References

- **Completion Service**: `src/main/java/com/school/planner/service/StudentGroupCompletionService.java`
- **REST API**: `src/main/java/com/school/planner/rest/StudentGroupCompletionResource.java`
- **Constraint Provider**: `SchoolScheduleConstraintProvider.java`
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SUPERSEDED (triaged 2026-07-15): H17 was implemented as vertical compaction (not target-end-date); target-end-date became M12. The task's architectural proposal was overtaken by the compaction redesign (TASK-227).
<!-- SECTION:FINAL_SUMMARY:END -->
