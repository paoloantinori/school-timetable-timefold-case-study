---
id: task-057
title: add the psql commands somewhere
status: Done
assignee: []
created_date: '2026-02-20 20:30'
updated_date: '2026-02-21 13:41'
labels:
  - claude-planned
  - documentation
dependencies:
  - task-056
priority: medium
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Common PostgreSQL/psql commands are frequently needed but often forgotten. Tasks like connecting to the container, inspecting tables, checking constraints, or exporting data require specific syntax that's easy to get wrong. These commands should be documented in a discoverable location linked from CLAUDE.md.

## Context

The project uses Podman/Docker containers for PostgreSQL:
- Container name: `planner-postgres-<profile>` (managed by planner.sh)
- Database: `planner`
- User: `planner`
- Password: `planner`

Common operations include:
- Connecting to a running container's psql
- Listing tables/schemas
- Querying lesson/teacher/room state
- Checking foreign key constraints
- Exporting data for debugging
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [ ] Given I need to connect to the database, When I check the psql cheat sheet, Then I find the exact podman exec command with correct container naming
- [x] #2 [ ] Given I want to inspect table structure, When I reference the doc, Then I find \d and \dt commands with examples
- [x] #3 [ ] Given I need to debug data, When I look up the doc, Then I find useful SELECT queries for lessons, courses, teachers
- [x] #4 [ ] Given I open CLAUDE.md, When I search for psql or database, Then I find a link to the cheat sheet
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### 1. Create the psql cheat sheet
**File**: `claudedocs/psql-cheatsheet.md`

### 2. Document these command categories

#### Connection Commands
```bash
# Connect to psql for a specific profile
podman exec -it planner-postgres-dev psql -U planner -d planner

# Quick one-liner to run a query
podman exec -it planner-postgres-dev psql -U planner -d planner -c "SELECT count(*) FROM lesson;"
```

#### Schema Inspection
```sql
-- List all tables
\dt

-- Describe a table (columns, types, nullable)
\d lesson

-- List all foreign keys
SELECT
    tc.table_name, kcu.column_name, 
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name 
FROM information_schema.table_constraints AS tc 
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY';
```

#### Common Queries
```sql
-- Count lessons by pinned status
SELECT pinned, count(*) FROM lesson GROUP BY pinned;

-- Find unassigned lessons
SELECT l.id, c.subject, sg.name as student_group
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN studentgroup sg ON c.studentgroup_id = sg.id
WHERE l.timeslot_id IS NULL;

-- Check teacher conflicts (same timeslot, same teacher)
SELECT t.name, ts.startdatetime, count(*)
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN teacher t ON c.teacher_id = t.id
JOIN timeslot ts ON l.timeslot_id = ts.id
WHERE l.timeslot_id IS NOT NULL
GROUP BY t.name, ts.startdatetime
HAVING count(*) > 1;
```

#### Data Export
```bash
# Export to CSV
podman exec -it planner-postgres-dev psql -U planner -d planner -c "\COPY lesson TO '/tmp/lesson.csv' CSV HEADER"
podman cp planner-postgres-dev:/tmp/lesson.csv ./lesson.csv
```

#### Useful psql Meta-Commands
```sql
\x        -- Toggle expanded display (vertical output)
\timing   -- Show query execution time
\q        -- Quit
\?        -- Help on meta-commands
\h SELECT -- Help on SQL command
```

### 3. Add link to CLAUDE.md
```markdown
## Database Commands
See @claudedocs/psql-cheatsheet.md for common psql commands and queries.
```

### 4. Verify
- Test each command against a running container
- Ensure container naming matches planner.sh convention
- Confirm queries work with actual schema
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Documentation only |
| Risk | 1 | No code changes |
| Testing | 1 | Command verification |
| Dependencies | 1 | Follows task-056 |
| **Total** | **1** | 1-2 hours (after task-056) |

---

## Priority: **MEDIUM** (Practical Reference)

Practical reference document. Quick to implement after task-056.

## Dependencies
- task-056 (DB schema provides context)
<!-- SECTION:NOTES:END -->
