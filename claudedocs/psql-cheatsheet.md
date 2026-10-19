# PostgreSQL/psql Cheat Sheet

Quick reference for common database operations on the Planner database.

## Quick Start: Use Helper Scripts (Preferred)

The project includes whitelisted helper scripts for common queries. **Use these first** to avoid approval prompts:

```bash
# Entity counts (teachers, lessons, rooms, etc.)
python3 scripts/db-stats.py status

# Lessons without timeslot/room
python3 scripts/db-stats.py unassigned

# Potential double-bookings
python3 scripts/db-stats.py conflicts

# Pinned vs unpinned lesson counts
python3 scripts/db-stats.py pinned

# Lessons for a specific teacher
python3 scripts/db-stats.py teacher 1

# Lessons for a student group
python3 scripts/db-stats.py group MODA

# Lessons on a specific date
python3 scripts/db-stats.py date 2026-02-21

# JSON output (useful for scripts)
python3 scripts/db-stats.py status -f json

# Generic read-only query
./scripts/db-query.sh "SELECT count(*) FROM lesson WHERE pinned"
./scripts/db-query.sh -f json "SELECT * FROM teacher LIMIT 5"
```

For app health checks, see also:
```bash
./scripts/app-health.sh           # HTTP health check
./scripts/api/api.sh status           # Solver status via API
./scripts/api/api.sh score            # Current score
```

---

## Raw psql Commands (Advanced)

Use these when helper scripts don't cover your needs.

### Connection

The container name is fixed as `planner-pg` (managed by `planner.sh`).

```bash
# Connect to psql interactively
podman exec -it planner-pg psql -U planner -d planner

# Run a single query (non-interactive)
podman exec -it planner-pg psql -U planner -d planner -c "SELECT count(*) FROM lesson;"

# Run a SQL file
podman exec -i planner-pg psql -U planner -d planner < queries.sql
```

## Schema Inspection

```sql
-- List all tables
\dt

-- Describe a table (columns, types, nullable, defaults)
\d lesson
\d course
\d timeslot

-- Show all indexes
\di

-- List all foreign key relationships
SELECT
    tc.table_name AS source_table,
    kcu.column_name AS source_column,
    ccu.table_name AS target_table,
    ccu.column_name AS target_column
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
ORDER BY tc.table_name, kcu.column_name;

-- Show table sizes
SELECT
    relname AS table_name,
    pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
    n_live_tup AS row_count
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

## Common Queries

### Entity Counts

```sql
-- Quick status of all entities
SELECT 'teachers' AS entity, count(*) FROM teacher
UNION ALL SELECT 'student_groups', count(*) FROM studentgroup
UNION ALL SELECT 'rooms', count(*) FROM room
UNION ALL SELECT 'courses', count(*) FROM course
UNION ALL SELECT 'timeslots', count(*) FROM timeslot
UNION ALL SELECT 'lessons', count(*) FROM lesson
UNION ALL SELECT 'lab_blocks', count(*) FROM labblock
UNION ALL SELECT 'holidays', count(*) FROM schoolholiday
UNION ALL SELECT 'stage_patterns', count(*) FROM stagepattern
UNION ALL SELECT 'lab_rotations', count(*) FROM labrotation
UNION ALL SELECT 'teacher_availability', count(*) FROM teacheravailability;
```

### Lesson Status

```sql
-- Count lessons by pinned status
SELECT pinned, count(*) FROM lesson GROUP BY pinned ORDER BY pinned;

-- Find unassigned lessons (no timeslot or room)
SELECT
    l.id,
    c.subject,
    t.name AS teacher,
    sg.name AS student_group
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN teacher t ON c.teacher_id = t.id
JOIN studentgroup sg ON c.studentgroup_id = sg.id
WHERE l.timeslot_id IS NULL OR l.room_id IS NULL
ORDER BY sg.name, c.subject
LIMIT 50;

-- Lessons per student group
SELECT
    sg.name AS student_group,
    count(*) AS total_lessons,
    SUM(CASE WHEN l.timeslot_id IS NOT NULL THEN 1 ELSE 0 END) AS scheduled,
    SUM(CASE WHEN l.pinned THEN 1 ELSE 0 END) AS pinned
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN studentgroup sg ON c.studentgroup_id = sg.id
GROUP BY sg.name
ORDER BY sg.name;
```

### Conflict Detection

```sql
-- Teacher double-bookings (same timeslot, multiple lessons)
SELECT
    t.name AS teacher,
    ts.startdatetime,
    count(*) AS lesson_count
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN teacher t ON c.teacher_id = t.id
JOIN timeslot ts ON l.timeslot_id = ts.id
WHERE l.timeslot_id IS NOT NULL
GROUP BY t.name, ts.startdatetime
HAVING count(*) > 1
ORDER BY t.name, ts.startdatetime;

-- Room double-bookings
SELECT
    r.name AS room,
    ts.startdatetime,
    count(*) AS lesson_count
FROM lesson l
JOIN room r ON l.room_id = r.id
JOIN timeslot ts ON l.timeslot_id = ts.id
WHERE l.room_id IS NOT NULL
GROUP BY r.name, ts.startdatetime
HAVING count(*) > 1
ORDER BY r.name, ts.startdatetime;

-- Student group double-bookings
SELECT
    sg.name AS student_group,
    ts.startdatetime,
    count(*) AS lesson_count
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN studentgroup sg ON c.studentgroup_id = sg.id
JOIN timeslot ts ON l.timeslot_id = ts.id
WHERE l.timeslot_id IS NOT NULL
GROUP BY sg.name, ts.startdatetime
HAVING count(*) > 1
ORDER BY sg.name, ts.startdatetime;
```

### Schedule Analysis

```sql
-- Daily lesson distribution for a specific group
SELECT
    ts.startdatetime::date AS date,
    count(*) AS lessons,
    array_agg(DISTINCT c.subject ORDER BY c.subject) AS subjects
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN studentgroup sg ON c.studentgroup_id = sg.id
JOIN timeslot ts ON l.timeslot_id = ts.id
WHERE sg.name = 'G03'
  AND l.timeslot_id IS NOT NULL
GROUP BY ts.startdatetime::date
ORDER BY ts.startdatetime::date
LIMIT 20;

-- Teacher's weekly load
SELECT
    t.name AS teacher,
    EXTRACT(DOW FROM ts.startdatetime) AS day_of_week,
    count(*) AS lessons
FROM lesson l
JOIN course c ON l.course_id = c.id
JOIN teacher t ON c.teacher_id = t.id
JOIN timeslot ts ON l.timeslot_id = ts.id
WHERE t.name = 'T06'
  AND l.timeslot_id IS NOT NULL
GROUP BY t.name, EXTRACT(DOW FROM ts.startdatetime)
ORDER BY day_of_week;

-- Lab room usage
SELECT
    r.name AS room,
    count(*) AS lessons,
    SUM(CASE WHEN c.islab THEN 1 ELSE 0 END) AS lab_lessons
FROM lesson l
JOIN room r ON l.room_id = r.id
JOIN course c ON l.course_id = c.id
WHERE l.room_id IS NOT NULL
GROUP BY r.name
ORDER BY lessons DESC;
```

### Stage/Professionalizzanti

```sql
-- Stage patterns by group
SELECT
    sg.name AS student_group,
    sp.startdate,
    sp.enddate,
    sp.dayofweek_mask
FROM stagepattern sp
JOIN studentgroup sg ON sp.studentgroup_id = sg.id
ORDER BY sg.name;

-- Professionalizzanti courses
SELECT
    sg.name AS student_group,
    c.subject,
    c.totalhours,
    t.name AS teacher
FROM course c
JOIN studentgroup sg ON c.studentgroup_id = sg.id
JOIN teacher t ON c.teacher_id = t.id
WHERE c.professionalizzanti = true
ORDER BY sg.name, c.subject;
```

## Data Export

```bash
# Export table to CSV (inside container, then copy out)
podman exec -it planner-pg psql -U planner -d planner -c "\COPY lesson TO '/tmp/lesson.csv' CSV HEADER"
podman cp planner-pg:/tmp/lesson.csv ./lesson.csv

# Export query result to CSV
podman exec -it planner-pg psql -U planner -d planner -c "\COPY (
    SELECT l.id, c.subject, t.name as teacher, sg.name as student_group
    FROM lesson l
    JOIN course c ON l.course_id = c.id
    JOIN teacher t ON c.teacher_id = t.id
    JOIN studentgroup sg ON c.studentgroup_id = sg.id
    WHERE l.timeslot_id IS NULL
) TO '/tmp/unassigned.csv' CSV HEADER"
podman cp planner-pg:/tmp/unassigned.csv ./unassigned.csv

# Full database dump
podman exec planner-pg pg_dump -U planner planner > planner_backup.sql

# Restore from dump
cat planner_backup.sql | podman exec -i planner-pg psql -U planner -d planner
```

## psql Meta-Commands

```sql
\x                    -- Toggle expanded display (vertical output, useful for wide rows)
\timing               -- Show query execution time
\watch 5              -- Re-run last query every 5 seconds (for monitoring)
\q                    -- Quit psql
\?                    -- Help on meta-commands
\h SELECT             -- Help on SQL command
\dn                   -- List schemas
\du                   -- List users/roles
\l                    -- List databases
\conninfo             -- Show current connection info
\pset pager off       -- Disable pager (useful for scripts)
```

## Quick Diagnostics

```bash
# Check if database is ready
podman exec planner-pg pg_isready -U planner -d planner

# Show running queries
podman exec -it planner-pg psql -U planner -d planner -c "
SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';"

# Kill long-running query (use PID from above)
podman exec -it planner-pg psql -U planner -d planner -c "SELECT pg_cancel_backend(<PID>);"
```

## Table Reference

| Entity | Table Name | Key Columns |
|--------|------------|-------------|
| Lesson | `lesson` | `id`, `course_id`, `timeslot_id`, `room_id`, `pinned` |
| Course | `course` | `id`, `subject`, `teacher_id`, `studentgroup_id`, `totalhours`, `islab` |
| Teacher | `teacher` | `id`, `name`, `nickname` |
| StudentGroup | `studentgroup` | `id`, `name`, `size`, `maxhoursperday` |
| Room | `room` | `id`, `name`, `capacity` |
| Timeslot | `timeslot` | `id`, `startdatetime`, `enddatetime` |
| LabBlock | `labblock` | `id`, `course_id`, `starttimeslot_id`, `room_id`, `blocksize`, `pinned` |
| StagePattern | `stagepattern` | `id`, `studentgroup_id`, `startdate`, `enddate` |
| LabRotation | `labrotation` | `id`, `teacher_id`, `studentgroup_id`, `dayofweek`, `periodstart`, `periodend` |
| TeacherAvailability | `teacheravailability` | `id`, `teacher_id`, `startdatetime`, `enddatetime`, `type` |
| SchoolHoliday | `schoolholiday` | `id`, `date`, `description` |

### ElementCollection Tables

These are separate tables for collection fields:

| Parent Entity | Table Name | Columns |
|---------------|------------|---------|
| Room | `room_tags` | `room_id`, `tags` |
| Course | `course_requiredroomtags` | `course_id`, `requiredroomtags` |
| StagePattern | `stagepattern_dayofweek_mask` | `stagepattern_id`, `dayofweek_mask` |
