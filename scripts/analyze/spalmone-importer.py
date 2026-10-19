#!/usr/bin/env python3
"""
Spalmone REST Importer

Imports parsed spalmone lessons into the planner database via REST API.
Creates pinned lessons to represent the human planner's schedule.

Usage:
    python3 scripts/analyze/spalmone-importer.py <parsed_lessons.json>

Prerequisites:
    - Planner app running on localhost:8080
    - Fresh database profile recommended (spalmone-test)
"""

import json
import sys
import urllib.request
import urllib.error
from collections import defaultdict
from typing import Optional, Any


BASE_URL = "http://localhost:8080/api"


def api_get(endpoint: str, parse_json: bool = True) -> Any:
    """Make a GET request to the API."""
    url = f"{BASE_URL}{endpoint}"
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            data = response.read().decode('utf-8')
            if not parse_json:
                return data
            if not data:
                return {}
            return json.loads(data)
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code} for {url}: {e.reason}", file=sys.stderr)
        return None
    except urllib.error.URLError as e:
        print(f"URL Error for {url}: {e.reason}", file=sys.stderr)
        return None
    except json.JSONDecodeError as e:
        print(f"JSON Error for {url}: {e}", file=sys.stderr)
        return None


def api_post(endpoint: str, data: dict) -> Optional[dict]:
    """Make a POST request to the API."""
    url = f"{BASE_URL}{endpoint}"
    body = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(
        url,
        data=body,
        headers={'Content-Type': 'application/json'},
        method='POST'
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        error_body = e.read().decode('utf-8') if e.fp else ''
        print(f"HTTP Error {e.code} for POST {url}: {e.reason}", file=sys.stderr)
        print(f"  Response: {error_body[:200]}", file=sys.stderr)
        return None
    except urllib.error.URLError as e:
        print(f"URL Error for POST {url}: {e.reason}", file=sys.stderr)
        return None


def api_put(endpoint: str, data: dict) -> Optional[dict]:
    """Make a PUT request to the API."""
    url = f"{BASE_URL}{endpoint}"
    body = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(
        url,
        data=body,
        headers={'Content-Type': 'application/json'},
        method='PUT'
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        error_body = e.read().decode('utf-8') if e.fp else ''
        print(f"HTTP Error {e.code} for PUT {url}: {e.reason}", file=sys.stderr)
        print(f"  Response: {error_body[:200]}", file=sys.stderr)
        return None
    except urllib.error.URLError as e:
        print(f"URL Error for PUT {url}: {e.reason}", file=sys.stderr)
        return None


def load_existing_entities() -> dict:
    """Load all existing entities from the database."""
    print("Loading existing entities from database...")

    entities = {}

    # Teachers
    teachers = api_get("/teachers")
    if teachers:
        entities['teachers'] = {t['name']: t for t in teachers}
        print(f"  Teachers: {len(entities['teachers'])}")
    else:
        print("  ERROR: Could not load teachers", file=sys.stderr)
        sys.exit(1)

    # Student Groups
    groups = api_get("/student-groups")
    if groups:
        entities['student_groups'] = {g['name']: g for g in groups}
        print(f"  Student Groups: {len(entities['student_groups'])}")
    else:
        print("  ERROR: Could not load student groups", file=sys.stderr)
        sys.exit(1)

    # Rooms
    rooms = api_get("/rooms")
    if rooms:
        entities['rooms'] = {r['name']: r for r in rooms}
        print(f"  Rooms: {len(entities['rooms'])}")
    else:
        print("  WARNING: Could not load rooms", file=sys.stderr)
        entities['rooms'] = {}

    # Timeslots - load from schedule endpoint (single request)
    print("  Loading timeslots...")
    entities['timeslots'] = {}
    entities['timeslots_by_date_slot'] = {}

    # Get timeslots from schedule endpoint
    schedule = api_get("/schedule")
    if schedule and 'timeslots' in schedule:
        for ts in schedule['timeslots']:
            ts_id = ts['id']
            entities['timeslots'][ts_id] = ts

            # Parse date from the timeslot
            date_str = ts.get('date', '')
            start_dt = ts.get('startDateTime', '')
            if start_dt:
                # Format: "2025-09-10T08:00:00"
                time_part = start_dt.split('T')[1] if 'T' in start_dt else ''
                hour = int(time_part.split(':')[0]) if time_part else 0
                slot_index = hour - 8 if hour >= 8 else 0
                key = (date_str, slot_index)
                entities['timeslots_by_date_slot'][key] = ts

        print(f"  Timeslots: {len(entities['timeslots'])}")
    else:
        print("  ERROR: Could not load timeslots from schedule", file=sys.stderr)

    return entities


def find_or_create_course(
    entities: dict,
    teacher_name: str,
    group_name: str,
    subject: str,
    created_courses: dict
) -> Optional[dict]:
    """Find existing course or create a new one."""
    # Check if we already created this course
    course_key = (teacher_name, group_name, subject)
    if course_key in created_courses:
        return created_courses[course_key]

    # Look for existing course
    courses = api_get(f"/courses?limit=1000")
    if courses:
        for c in courses:
            t_name = entities['teachers'].get(teacher_name, {}).get('name', teacher_name)
            g_name = entities['student_groups'].get(group_name, {}).get('name', group_name)

            if (c.get('teacher', {}).get('name') == t_name and
                c.get('studentGroup', {}).get('name') == g_name and
                c.get('subject') == subject):
                created_courses[course_key] = c
                return c

    # Create new course
    teacher = entities['teachers'].get(teacher_name)
    group = entities['student_groups'].get(group_name)

    if not teacher:
        print(f"  WARNING: Teacher '{teacher_name}' not found in database", file=sys.stderr)
        return None
    if not group:
        print(f"  WARNING: Student group '{group_name}' not found in database", file=sys.stderr)
        return None

    course_data = {
        'subject': subject,
        'teacherId': teacher['id'],
        'studentGroupId': group['id'],
        'totalHours': 1,  # Will be updated later
        'lab': False,
        'labBlockSize': 0,
        'professionalizzanti': False,
    }

    new_course = api_post("/courses", course_data)
    if new_course:
        created_courses[course_key] = new_course
        print(f"  Created course: {subject} - {teacher_name} - {group_name}")
        return new_course

    return None


def import_lessons(parsed_data: dict, entities: dict) -> dict:
    """Import all lessons from parsed data."""
    stats = {
        'total': 0,
        'imported': 0,
        'skipped_no_teacher': 0,
        'skipped_no_group': 0,
        'skipped_no_timeslot': 0,
        'skipped_no_course': 0,
        'errors': 0,
    }

    created_courses = {}
    lesson_counts = defaultdict(int)  # Track hours per course

    # First pass: count hours per course
    for lesson in parsed_data['lessons']:
        teacher = lesson['teacher']
        group = lesson['student_group']
        subject = lesson['subject']
        key = (teacher, group, subject)
        lesson_counts[key] += 1

    print(f"\nImporting {len(parsed_data['lessons'])} lessons...")

    # Process in batches for efficiency
    batch_size = 100
    for i, lesson in enumerate(parsed_data['lessons']):
        stats['total'] += 1

        if (i + 1) % batch_size == 0:
            print(f"  Progress: {i + 1}/{len(parsed_data['lessons'])} ({stats['imported']} imported)")

        teacher_name = lesson['teacher']
        group_name = lesson['student_group']
        subject = lesson['subject']
        date_str = lesson['date']
        slot_index = lesson['slot_index']
        room_name = lesson.get('room')

        # Find timeslot
        ts_key = (date_str, slot_index)
        timeslot = entities['timeslots_by_date_slot'].get(ts_key)
        if not timeslot:
            stats['skipped_no_timeslot'] += 1
            continue

        # Find or create course
        course = find_or_create_course(entities, teacher_name, group_name, subject, created_courses)
        if not course:
            stats['skipped_no_course'] += 1
            continue

        # Find room (optional)
        room_id = None
        if room_name and room_name in entities['rooms']:
            room_id = entities['rooms'][room_name]['id']

        # Create lesson
        lesson_data = {
            'courseId': course['id'],
            'timeslotId': timeslot['id'],
            'roomId': room_id,
            'pinned': True,  # All spalmone lessons are pinned (human-planned)
        }

        result = api_post("/lessons", lesson_data)
        if result:
            stats['imported'] += 1
        else:
            stats['errors'] += 1

    # Update course totalHours based on actual lesson counts
    print("\nUpdating course hours...")
    for (teacher, group, subject), count in lesson_counts.items():
        course_key = (teacher, group, subject)
        if course_key in created_courses:
            course = created_courses[course_key]
            api_put(f"/courses/{course['id']}", {
                **course,
                'totalHours': count
            })

    return stats


def run_analysis() -> Optional[dict]:
    """Run constraint analysis on the imported schedule."""
    print("\nRunning constraint analysis...")
    analysis = api_get("/schedule/analysis")
    if analysis:
        print("  Analysis complete.")
        return analysis
    else:
        print("  ERROR: Could not get analysis", file=sys.stderr)
        return None


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/analyze/spalmone-importer.py <parsed_lessons.json>", file=sys.stderr)
        print("       python3 scripts/analyze/spalmone-importer.py -  # Read from stdin", file=sys.stderr)
        sys.exit(1)

    # Load parsed data
    if sys.argv[1] == '-':
        parsed_data = json.load(sys.stdin)
    else:
        with open(sys.argv[1], 'r', encoding='utf-8') as f:
            parsed_data = json.load(f)

    print(f"Loaded {parsed_data['total_lessons']} lessons from parser")

    # Check app health by trying to load teachers
    print("\nChecking app health...")
    teachers = api_get("/teachers?limit=1")
    if not teachers:
        print("ERROR: Planner app not running. Start with ./scripts/core/dev-start-and-wait.sh <profile>", file=sys.stderr)
        sys.exit(1)
    print("  App status: OK")

    # Load existing entities
    entities = load_existing_entities()

    # Import lessons
    stats = import_lessons(parsed_data, entities)

    # Print statistics
    print("\n" + "=" * 50)
    print("IMPORT STATISTICS")
    print("=" * 50)
    print(f"Total lessons in CSV:    {stats['total']}")
    print(f"Successfully imported:   {stats['imported']}")
    print(f"Skipped (no timeslot):   {stats['skipped_no_timeslot']}")
    print(f"Skipped (no course):     {stats['skipped_no_course']}")
    print(f"Errors:                  {stats['errors']}")

    # Run analysis
    analysis = run_analysis()

    if analysis:
        # Output analysis to file
        output_file = "/tmp/spalmone-analysis.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(analysis, f, indent=2, ensure_ascii=False)
        print(f"\nAnalysis saved to: {output_file}")

    # Output final stats
    output = {
        'import_stats': stats,
        'analysis': analysis,
    }

    print(json.dumps(output, indent=2))


if __name__ == '__main__':
    main()
