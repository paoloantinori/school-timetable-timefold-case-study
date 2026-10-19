---
id: task-056
title: publish the db schema
status: Done
assignee:
  - workflow-improvement
created_date: '2026-02-20 20:29'
updated_date: '2026-02-21 14:09'
labels:
  - claude-planned
  - documentation
dependencies: []
priority: medium
ordinal: 36000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

The PostgreSQL database schema is implicit in the JPA entity definitions but not documented anywhere. Developers (human or AI) need to understand the table structure, relationships, and constraints without reading through all domain entity source files. This documentation should be in a markdown file accessible from CLAUDE.md.

## Context

The domain model consists of 12 JPA entities in `src/main/java/com/school/planner/domain/`:
- **Core planning entities**: Lesson, LabBlock (both @PlanningEntity with timeslot/room variables)
- **Reference entities**: Teacher, StudentGroup, Room, Course, Timeslot
- **Pattern/configuration entities**: StagePattern, LabRotation, TeacherAvailability, SchoolHoliday
- **Enum**: AvailabilityType, PreAssignmentStatus

All entities use PanacheEntityBase with IDENTITY generation. Relationships are primarily @ManyToOne.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [ ] Given I read the schema documentation, When I need to understand the database structure, Then I see all 12 entities with their fields, types, and relationships
- [x] #2 [ ] Given I need to write a SQL query, When I reference the schema doc, Then I find table names matching entity names (lowercase with underscores) and column names
- [x] #3 [ ] Given I open CLAUDE.md, When I look for database reference, Then I find a link to the schema documentation in a dedicated section
- [x] #4 [ ] Given the schema doc exists, When I check it against domain entities, Then foreign key relationships are documented (e.g., lesson.course_id -> course.id)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### 1. Create the schema documentation file
**File**: `claudedocs/database-schema.md`

Include:
- Entity-to-table mapping (JPA convention: `Lesson` -> `lesson`)
- All columns with types (from Java field types)
- Primary keys (all use `Long id` with IDENTITY)
- Foreign key relationships (from @ManyToOne annotations)
- ElementCollection tables (tags in Room, requiredRoomTags in Course, dayOfWeekMask in StagePattern)

### 2. Document each entity

| Entity | Table | Key Fields | Relationships |
|--------|-------|------------|---------------|
| Lesson | lesson | id, pinned, timeslot_id, room_id | FK: course_id, timeslot_id, room_id |
| LabBlock | labblock | id, blockSize, pinned, startTimeslot_id, room_id | FK: course_id, startTimeslot_id, room_id |
| Course | course | id, subject, totalHours, isLab, labBlockSize, professionalizzanti | FK: teacher_id, studentGroup_id, doppioTeacher_id |
| Timeslot | timeslot | id, startDateTime, endDateTime | None |
| Room | room | id, name, capacity | ElementCollection: room_tags |
| Teacher | teacher | id, name, nickname | None |
| StudentGroup | studentgroup | id, name, size, maxHoursPerDay, startDate | None |
| StagePattern | stagepattern | id, startDate, endDate | FK: studentGroup_id; ElementCollection: dayOfWeek_mask |
| LabRotation | labrotation | id, dayOfWeek, slotStart, blockSize, periodStart, periodEnd, roomTag, subjectPrefix | FK: teacher_id, studentGroup_id |
| TeacherAvailability | teacheravailability | id, startDateTime, endDateTime, type | FK: teacher_id |
| SchoolHoliday | schoolholiday | id, date, description | None |

### 3. Add link to CLAUDE.md
Add to an appropriate section in CLAUDE.md:
```markdown
## Database Schema
See @claudedocs/database-schema.md for the complete PostgreSQL schema documentation.
```

### 4. Verify
- Ensure all 12 entities are documented
- Verify FK names follow JPA convention (fieldName_id)
- Cross-reference with actual entity classes
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Documentation only |
| Risk | 1 | No code changes |
| Testing | 1 | Review verification |
| Dependencies | 1 | None |
| **Total** | **1** | 1-2 hours |

---

## Priority: **MEDIUM** (Documentation Foundation)

Foundation for database documentation. Quick to implement. Enables task-057.
<!-- SECTION:NOTES:END -->
