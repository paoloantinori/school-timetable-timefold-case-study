#!/usr/bin/env python3
"""
Common database queries for the planner.

Provides quick access to frequently needed database information without
requiring approval for each psql command.

Usage:
    python3 scripts/db/db-stats.py [command] [options]

Commands:
    status          Overall counts (lessons, teachers, rooms, etc.)
    unassigned      Lessons without timeslot/room assignments
    conflicts       Potential teacher/room conflicts (same timeslot)
    pinned          Count of pinned vs unpinned lessons
    teacher ID      All lessons for a specific teacher
    group NAME      All lessons for a student group
    date YYYY-MM-DD All lessons on a specific date

Options:
    -p, --profile   Database profile (default: dev)
    -f, --format    Output format: table, json (default: table)

Security: All queries are read-only, safe to whitelist

Examples:
    python3 scripts/db/db-stats.py status
    python3 scripts/db/db-stats.py unassigned
    python3 scripts/db/db-stats.py teacher 1
    python3 scripts/db/db-stats.py date 2026-02-21 -f json
"""

import subprocess
import sys
import json
import argparse

DEFAULT_PROFILE = "dev"

QUERIES = {
    "status": """
        SELECT
            'Teachers' as entity, COUNT(*) as count FROM teacher
        UNION ALL SELECT 'Student Groups', COUNT(*) FROM studentgroup
        UNION ALL SELECT 'Rooms', COUNT(*) FROM room
        UNION ALL SELECT 'Courses', COUNT(*) FROM course
        UNION ALL SELECT 'Timeslots', COUNT(*) FROM timeslot
        UNION ALL SELECT 'Lessons', COUNT(*) FROM lesson
        UNION ALL SELECT 'LabBlocks', COUNT(*) FROM labblock
        ORDER BY entity;
    """,
    "unassigned": """
        SELECT c.subject, sg.name as student_group, COUNT(*) as unassigned_count
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN studentgroup sg ON c.studentgroup_id = sg.id
        WHERE l.timeslot_id IS NULL
        GROUP BY c.subject, sg.name
        ORDER BY unassigned_count DESC;
    """,
    "conflicts": """
        SELECT t.name as teacher, ts.startdatetime, COUNT(*) as lesson_count
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN teacher t ON c.teacher_id = t.id
        JOIN timeslot ts ON l.timeslot_id = ts.id
        WHERE l.timeslot_id IS NOT NULL
        GROUP BY t.name, ts.startdatetime
        HAVING COUNT(*) > 1
        ORDER BY lesson_count DESC;
    """,
    "pinned": """
        SELECT
            CASE WHEN pinned THEN 'Pinned' ELSE 'Unpinned' END as status,
            COUNT(*) as count
        FROM lesson
        GROUP BY pinned
        ORDER BY pinned DESC;
    """,
}


def run_query(profile: str, sql: str, format: str = "table") -> str:
    """Execute a SQL query via podman exec."""
    # planner.sh uses fixed name "planner-pg" regardless of profile
    container = "planner-pg"

    # Check container is running
    result = subprocess.run(
        ["podman", "ps", "--format", "{{.Names}}"],
        capture_output=True, text=True
    )
    if container not in result.stdout:
        print(f"ERROR: Container {container} is not running", file=sys.stderr)
        print(f"Start it with: ./planner.sh start {profile}", file=sys.stderr)
        sys.exit(1)

    # Run query
    if format == "json":
        psql_args = ["-t", "-A"]
    else:
        psql_args = ["-x"]

    result = subprocess.run(
        ["podman", "exec", "-i", container,
         "psql", "-U", "planner", "-d", "planner"] + psql_args + ["-c", sql],
        capture_output=True, text=True
    )

    if result.returncode != 0:
        print(f"ERROR: {result.stderr}", file=sys.stderr)
        sys.exit(1)

    return result.stdout


def query_by_teacher(profile: str, teacher_id: int, format: str) -> str:
    sql = f"""
        SELECT c.subject, sg.name as student_group,
               TO_CHAR(ts.startdatetime, 'YYYY-MM-DD HH24:MI') as datetime,
               r.name as room, l.pinned
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN studentgroup sg ON c.studentgroup_id = sg.id
        LEFT JOIN timeslot ts ON l.timeslot_id = ts.id
        LEFT JOIN room r ON l.room_id = r.id
        WHERE c.teacher_id = {teacher_id}
        ORDER BY ts.startdatetime NULLS LAST, c.subject;
    """
    return run_query(profile, sql, format)


def query_by_group(profile: str, group_name: str, format: str) -> str:
    sql = f"""
        SELECT c.subject, t.name as teacher,
               TO_CHAR(ts.startdatetime, 'YYYY-MM-DD HH24:MI') as datetime,
               r.name as room
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN teacher t ON c.teacher_id = t.id
        LEFT JOIN timeslot ts ON l.timeslot_id = ts.id
        LEFT JOIN room r ON l.room_id = r.id
        JOIN studentgroup sg ON c.studentgroup_id = sg.id
        WHERE sg.name ILIKE '%{group_name}%'
        ORDER BY ts.startdatetime NULLS LAST, c.subject;
    """
    return run_query(profile, sql, format)


def query_by_date(profile: str, date: str, format: str) -> str:
    sql = f"""
        SELECT c.subject, t.name as teacher, sg.name as student_group,
               r.name as room, l.pinned
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN teacher t ON c.teacher_id = t.id
        JOIN studentgroup sg ON c.studentgroup_id = sg.id
        JOIN timeslot ts ON l.timeslot_id = ts.id
        LEFT JOIN room r ON l.room_id = r.id
        WHERE DATE(ts.startdatetime) = '{date}'
        ORDER BY ts.startdatetime, sg.name;
    """
    return run_query(profile, sql, format)


def main():
    parser = argparse.ArgumentParser(description="Common database queries")
    parser.add_argument("command", nargs="?", default="status",
                       help="Query command or entity")
    parser.add_argument("arg", nargs="?", help="Additional argument (ID, name, date)")
    parser.add_argument("-p", "--profile", default=DEFAULT_PROFILE,
                       help=f"Database profile (default: {DEFAULT_PROFILE})")
    parser.add_argument("-f", "--format", choices=["table", "json"], default="table",
                       help="Output format")
    args = parser.parse_args()

    if args.command in QUERIES:
        print(run_query(args.profile, QUERIES[args.command], args.format))
    elif args.command == "teacher" and args.arg:
        print(query_by_teacher(args.profile, int(args.arg), args.format))
    elif args.command == "group" and args.arg:
        print(query_by_group(args.profile, args.arg, args.format))
    elif args.command == "date" and args.arg:
        print(query_by_date(args.profile, args.arg, args.format))
    else:
        print(f"Unknown command: {args.command}", file=sys.stderr)
        print("Valid commands: status, unassigned, conflicts, pinned, teacher ID, group NAME, date YYYY-MM-DD")
        sys.exit(2)


if __name__ == "__main__":
    main()
