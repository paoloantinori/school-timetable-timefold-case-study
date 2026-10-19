---
id: TASK-099
title: Implement equals()/hashCode() on domain entities for robust Joiners.equal()
status: Done
assignee: []
created_date: '2026-02-23 19:14'
updated_date: '2026-03-10 05:16'
labels:
  - claude-planned
  - quality
  - solver
dependencies: []
priority: medium
ordinal: 32750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Domain entities (`Teacher`, `Room`, `StudentGroup`) are used in Timefold Solver's `Joiners.equal()` constraints but rely on Java's default identity-based `equals()`/`hashCode()`. This works currently because Timefold shares problem fact instances by reference during solving, but it's fragile and could break in edge cases.

## Risk Analysis

| Scenario | Why It Breaks |
|----------|---------------|
| Hibernate lazy loading | Different proxy instances for same entity |
| Entity reload from DB | New instance created, identity differs |
| Serialization/deserialization | New instances created |
| Multi-node solving | Different JVMs have different instances |

## Affected Entities

Used in `Joiners.equal()`:
- **Teacher** - `Joiners.equal(Lesson::getTeacher)`, `Joiners.equal(LabBlock::getTeacher)`
- **Room** - `Joiners.equal(l -> l.room)`, `Joiners.equal(b -> b.room)`
- **StudentGroup** - `Joiners.equal(Lesson::getStudentGroup)`, `Joiners.equal(LabBlock::getStudentGroup)`

Not affected (use `@PlanningId` or immutable types):
- Lesson, LabBlock - use `@PlanningId` for solver tracking
- Timeslot - joins use `getDate()` which returns `LocalDate` (has proper equals)

## Solution

Implement ID-based `equals()`/`hashCode()` on `Teacher`, `Room`, and `StudentGroup` using the `id` field. This is safe because:
- ID is set at persistence time and never changes during solving
- Timefold documentation confirms: "entity hashCode() must not depend on planning variables" - ID is not a planning variable

## Files Involved

- `domain/Teacher.java`
- `domain/Room.java`
- `domain/StudentGroup.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Teacher implements equals()/hashCode() based on id field
- [x] #2 Room implements equals()/hashCode() based on id field
- [x] #3 StudentGroup implements equals()/hashCode() based on id field
- [x] #4 All three handle null id (transient entities) correctly - identity fallback
- [x] #5 All three maintain hashCode() consistency - same id = same hashCode
- [x] #6 All existing tests pass (mvn test)
- [ ] #7 Solver produces same results before and after change (benchmark verification)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Pattern

Use this canonical pattern for all three entities (based on JPA best practices):

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Teacher other)) return false;
    // null id means transient entity - fall back to identity
    return id != null && id.equals(other.id);
}

@Override
public int hashCode() {
    // Constant hash for entities with null id (transient)
    // This satisfies JPA requirement that hashCode be consistent
    // before and after persistence
    return id == null ? getClass().hashCode() : id.hashCode();
}
```

### Why This Pattern

1. **null id handling**: Transient entities (not yet persisted) fall back to identity comparison. This prevents `new Teacher().equals(new Teacher())` from returning true.

2. **Constant hashCode**: Using `getClass().hashCode()` for null-id entities ensures:
   - Hash code doesn't change when entity gets persisted (JPA requirement)
   - Different entity classes have different hash codes even with null id

3. **instanceof pattern**: Using `instanceof Teacher other` (Java 16+) is cleaner than casting

## Verification Steps

1. Run `mvn test` - all existing tests must pass
2. Run a short benchmark (30s solve) before and after change
3. Compare final scores - should be identical or very close
4. Check constraint analysis counts match

## Optional Enhancement

Consider also adding `equals()`/`hashCode()` to:
- `Course` - used indirectly via lesson relationships
- `Timeslot` - currently joins use `getDate()` (LocalDate) which is safe, but full entity comparison would be more robust
<!-- SECTION:NOTES:END -->
