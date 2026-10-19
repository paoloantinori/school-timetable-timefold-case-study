#!/usr/bin/env python3
"""
Spalmone Bulk Database Importer

Imports parsed spalmone lessons efficiently by generating a single SQL file
and executing it in one database call.

Usage:
    python3 scripts/analyze/spalmone-db-importer.py <parsed_lessons.json>
"""

import json
import subprocess
import sys
import tempfile
from collections import defaultdict

from shared import OFF_CAMPUS_TEACHERS


# Explicit mapping from CSV teacher names to database teacher IDs
TEACHER_ID_MAP = {
    't28': 14,  # External Thursday-only teacher with VACATION constraints
}


def run_psql(query: str) -> list:
    """Run a psql query and return results."""
    cmd = [
        "podman", "exec", "-i", "planner-pg",
        "psql", "-U", "planner", "-d", "planner", "-t", "-A", "-F", "|",
        "-c", query
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        return []

    rows = []
    for line in result.stdout.strip().split('\n'):
        if line:
            rows.append(line.split('|'))
    return rows


def load_teachers() -> dict:
    """Load teachers and build name->ID mapping."""
    rows = run_psql("SELECT id, name FROM teacher ORDER BY id")
    teachers = {}

    for row in rows:
        if len(row) >= 2 and row[0] and row[1]:
            teacher_id = int(row[0])
            name = row[1]
            teachers[name.lower()] = teacher_id
            last_name = name.split()[0].lower() if name else ""
            if last_name:
                teachers[last_name] = teacher_id

    teachers.update(TEACHER_ID_MAP)
    return teachers


def load_student_groups() -> dict:
    """Load student groups."""
    rows = run_psql("SELECT id, name FROM studentgroup ORDER BY id")
    groups = {}
    for row in rows:
        if len(row) >= 2 and row[0] and row[1]:
            groups[row[1]] = int(row[0])
            groups[row[1].replace(" ", "")] = int(row[0])
    return groups


def load_timeslots() -> dict:
    """Load timeslots indexed by (date, slot_index)."""
    rows = run_psql("SELECT id, startdatetime FROM timeslot ORDER BY startdatetime")
    timeslots = {}
    for row in rows:
        if len(row) >= 2 and row[0] and row[1]:
            ts_id = int(row[0])
            start_dt = row[1]
            if ' ' in start_dt:
                date_part, time_part = start_dt.split(' ')
                hour = int(time_part.split(':')[0])
                slot_index = hour - 8
                timeslots[(date_part, slot_index)] = ts_id
    return timeslots


def load_rooms() -> dict:
    """Load rooms."""
    rows = run_psql("SELECT id, name FROM room ORDER BY id")
    rooms = {}
    for row in rows:
        if len(row) >= 2 and row[0] and row[1]:
            rooms[row[1]] = int(row[0])
    return rooms


def escape_sql(s: str) -> str:
    """Escape string for SQL."""
    return s.replace("'", "''")


def find_teacher_id(csv_name: str, teachers_map: dict) -> int | None:
    """Find teacher ID using multiple matching strategies."""
    name_lower = csv_name.lower().strip()

    if name_lower in teachers_map:
        return teachers_map[name_lower]

    for db_name, tid in teachers_map.items():
        if db_name.startswith(name_lower) or name_lower.startswith(db_name):
            return tid

    for db_name, tid in teachers_map.items():
        if name_lower in db_name or db_name in name_lower:
            return tid

    return None


def generate_bulk_sql(parsed_data: dict, teachers: dict, groups: dict,
                      timeslots: dict, rooms: dict) -> tuple[str, dict]:
    """Generate a single SQL file for bulk import. Returns (sql, stats)."""

    stats = {
        'total': len(parsed_data['lessons']),
        'imported': 0,
        'skipped_no_teacher': 0,
        'skipped_no_group': 0,
        'skipped_no_timeslot': 0,
        'T28_lessons': 0,
    }

    # Count hours per course
    lesson_counts = defaultdict(int)
    for lesson in parsed_data['lessons']:
        key = (lesson['teacher'], lesson['student_group'], lesson['subject'])
        lesson_counts[key] += 1

    # Build SQL
    sql_parts = []
    sql_parts.append("-- Spalmone bulk import")
    sql_parts.append("BEGIN;")

    # Clear existing data
    sql_parts.append("-- Clear existing data")
    sql_parts.append("DELETE FROM lesson;")
    sql_parts.append("DELETE FROM labblock;")
    sql_parts.append("DELETE FROM course_requiredroomtags;")
    sql_parts.append("DELETE FROM course;")

    # Collect unique courses
    courses = {}  # (teacher_id, group_id, subject) -> hours
    lesson_values = []

    for lesson in parsed_data['lessons']:
        csv_teacher = lesson['teacher']
        group_name = lesson['student_group']
        subject = lesson['subject']
        date_str = lesson['date']
        slot_index = lesson['slot_index']
        room_name = lesson.get('room')

        # Find teacher
        teacher_id = find_teacher_id(csv_teacher, teachers)
        if not teacher_id:
            stats['skipped_no_teacher'] += 1
            continue

        if teacher_id == 14:  # T28's ID
            stats['T28_lessons'] += 1

        # Find group
        group_id = groups.get(group_name) or groups.get(group_name.replace(" ", ""))
        if not group_id:
            stats['skipped_no_group'] += 1
            continue

        # Find timeslot
        ts_key = (date_str, slot_index)
        timeslot_id = timeslots.get(ts_key)
        if not timeslot_id:
            stats['skipped_no_timeslot'] += 1
            continue

        # Find room
        room_id = rooms.get(room_name) if room_name else None

        # Track course
        course_key = (teacher_id, group_id, subject)
        if course_key not in courses:
            key = (csv_teacher, group_name, subject)
            courses[course_key] = lesson_counts.get(key, 1)

        # Build lesson value
        room_sql = str(room_id) if room_id else "NULL"
        lesson_values.append(f"({course_key[0]}, {course_key[1]}, '{escape_sql(course_key[2])}', {timeslot_id}, {room_sql})")
        stats['imported'] += 1

    # Insert courses
    sql_parts.append("\n-- Insert courses")
    for (teacher_id, group_id, subject), hours in courses.items():
        sql_parts.append(
            f"INSERT INTO course (subject, teacher_id, studentgroup_id, totalhours, "
            f"islab, labblocksize, professionalizzanti) "
            f"VALUES ('{escape_sql(subject)}', {teacher_id}, {group_id}, {hours}, false, 0, false);"
        )

    # Insert lessons using join to get course IDs
    sql_parts.append("\n-- Insert lessons")
    for lv in lesson_values:
        teacher_id, group_id, subject, timeslot_id, room_id = lv.split(", ", 4)
        # Parse the values (they're in a tuple-like format)
        teacher_id = int(teacher_id.strip("("))
        # subject has quotes
        subject_clean = subject.strip("'")
        timeslot_id = int(timeslot_id)
        room_sql = room_id.rstrip(")")

        # promotedpin is always FALSE here (TASK-419): these are AUTHORITATIVE pins (the
        # school's own grid), never pins a staged solve made up for itself. The column has a
        # DB-side default, but it is listed explicitly so the intent is on the record.
        sql_parts.append(
            f"INSERT INTO lesson (course_id, timeslot_id, room_id, pinned, promotedpin) "
            f"SELECT id, {timeslot_id}, {room_sql}, true, false FROM course "
            f"WHERE teacher_id = {teacher_id} AND studentgroup_id = {group_id} "
            f"AND subject = '{escape_sql(subject_clean)}';"
        )

    sql_parts.append("COMMIT;")
    sql_parts.append("")
    sql_parts.append("-- Verification")
    sql_parts.append("SELECT count(*) AS pinned_lessons FROM lesson WHERE pinned = true;")
    sql_parts.append("SELECT count(*) AS T28_lessons FROM lesson l JOIN course c ON l.course_id = c.id WHERE c.teacher_id = 14;")

    return "\n".join(sql_parts), stats


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/analyze/spalmone-db-importer.py <parsed_lessons.json>",
              file=sys.stderr)
        sys.exit(1)

    with open(sys.argv[1], 'r', encoding='utf-8') as f:
        parsed_data = json.load(f)

    print(f"Loaded {parsed_data['total_lessons']} lessons from parser")

    # Defensive filtering: remove any off-campus entries that slipped through
    filtered_lessons = [l for l in parsed_data['lessons']
                        if l.get('teacher', '') not in OFF_CAMPUS_TEACHERS]
    filtered_count = len(parsed_data['lessons']) - len(filtered_lessons)
    if filtered_count > 0:
        print(f"  Filtered out {filtered_count} off-campus entries (defensive)")
        parsed_data['lessons'] = filtered_lessons
        parsed_data['total_lessons'] = len(filtered_lessons)

    print("\nLoading database entities...")
    teachers = load_teachers()
    print(f"  Teachers: {len(teachers)} mappings")
    print(f"  -> T28 maps to ID: {teachers.get('t28', 'NOT FOUND')}")

    groups = load_student_groups()
    print(f"  Student Groups: {len(groups)}")

    timeslots = load_timeslots()
    print(f"  Timeslots: {len(timeslots)}")

    rooms = load_rooms()
    print(f"  Rooms: {len(rooms)}")

    result = subprocess.run(
        ["podman", "exec", "planner-pg", "pg_isready", "-U", "planner"],
        capture_output=True
    )
    if result.returncode != 0:
        print("ERROR: Database container not ready", file=sys.stderr)
        sys.exit(1)

    print("\nGenerating bulk SQL...")
    sql, stats = generate_bulk_sql(parsed_data, teachers, groups, timeslots, rooms)

    # Write to temp file
    with tempfile.NamedTemporaryFile(mode='w', suffix='.sql', delete=False) as f:
        f.write(sql)
        sql_file = f.name

    print(f"  Generated {len(sql.split(chr(10)))} lines of SQL")
    print(f"  Written to: {sql_file}")

    print("\nExecuting bulk import...")
    result = subprocess.run(
        ["podman", "exec", "-i", "planner-pg",
         "psql", "-U", "planner", "-d", "planner", "-f", "-"],
        input=sql.encode(),
        capture_output=True
    )

    if result.returncode != 0:
        print(f"ERROR: {result.stderr.decode()}", file=sys.stderr)
        sys.exit(1)

    print(result.stdout.decode())

    print("\n" + "=" * 50)
    print("IMPORT STATISTICS")
    print("=" * 50)
    print(f"Total lessons in CSV:    {stats['total']}")
    print(f"Successfully imported:   {stats['imported']}")
    print(f"T28 lessons imported:   {stats['T28_lessons']}")
    print(f"Skipped (no teacher):    {stats['skipped_no_teacher']}")
    print(f"Skipped (no group):      {stats['skipped_no_group']}")
    print(f"Skipped (no timeslot):   {stats['skipped_no_timeslot']}")

    # Verify
    rows = run_psql("SELECT count(*) FROM lesson WHERE pinned = true")
    if rows and rows[0]:
        print(f"\nVerified pinned lessons: {rows[0][0]}")

    rows = run_psql("""
        SELECT count(*) FROM lesson l
        JOIN course c ON l.course_id = c.id
        WHERE c.teacher_id = 14
    """)
    if rows and rows[0]:
        print(f"T28's lessons in DB:        {rows[0][0]}")


if __name__ == '__main__':
    main()
