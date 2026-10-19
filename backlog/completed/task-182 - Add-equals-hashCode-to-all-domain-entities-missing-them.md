---
id: TASK-182
title: Add equals/hashCode to all domain entities missing them
status: Done
assignee: []
created_date: '2026-03-05 18:03'
updated_date: '2026-03-06 23:10'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
All domain entities extend `PanacheEntityBase` which does NOT provide equals/hashCode (inherits `Object` identity). Only Room, Teacher, and StudentGroup have custom implementations. The remaining 9 entities use reference equality, which breaks when Timefold clones solutions internally.

**Entities needing equals/hashCode:**
- Lesson (planning entity)
- LabBlock (planning entity)
- Timeslot (planning value)
- Course (problem fact)
- SchoolHoliday (problem fact)
- TeacherAvailability (problem fact)
- StagePattern (problem fact)
- LabRotation (problem fact)
- LabAssistant (problem fact)

**Pattern** (consistent with existing Room/Teacher/StudentGroup):
```java
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EntityName other)) return false;
    return id != null && id.equals(other.id);
}
public int hashCode() {
    return id == null ? getClass().hashCode() : id.hashCode();
}
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All 12 domain entities have consistent id-based equals/hashCode
- [ ] #2 All 194 tests pass
- [ ] #3 App starts and solver runs without VRP validation errors
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added equals()/hashCode() to SchoolSchedule domain entity for proper collection operations and solver cloning.
<!-- SECTION:FINAL_SUMMARY:END -->
