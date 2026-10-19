---
id: task-011
title: Recupero Argomenti (RA) - catch-up lessons that merge into other classes
status: To Do
assignee: []
created_date: '2026-02-17 16:35'
updated_date: '2026-02-27 05:40'
labels: []
dependencies: []
priority: low
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

"Recupero Argomenti" (RA) are catch-up/recovery lessons for students who need additional support. These are administratively separate courses but in practice they **merge into another class**:

- RA classes are assigned to a predetermined "parent" class
- They use the **same teacher** as the parent class
- The **net effect** is that the parent class becomes longer
- Administratively they're a logical class on their own, but practically they converge into another class

## Implementation Considerations

1. **Data model**: How to represent the RA→parent relationship?
2. **Constraint handling**: RA lessons must be scheduled adjacent to or concurrent with parent lessons
3. **Teacher assignment**: RA automatically inherits the parent's teacher
4. **Room sharing**: RA likely shares the same room as the parent class

## Questions to Resolve

- Should RA be a separate `Course` entity with a `parentCourse` reference?
- Or should RA hours simply be added to the parent course's `totalHours`?
- How does this affect the solver's entity generation?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given clarification on what RA means, When the task is updated, Then the title reflects the actual feature or requirement
- [ ] #2 Given a clear description of the desired outcome, When acceptance criteria are defined, Then they follow Given/When/Then format and are specific and testable
<!-- AC:END -->
