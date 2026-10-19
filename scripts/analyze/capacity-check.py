#!/usr/bin/env python3
"""
Capacity analysis for the school planner.

Parses DataGenerator.java to extract all courses, room tags, and room definitions,
then checks whether the total hours per lab tag fit within available room slots.

Usage:
    python3 scripts/analyze/capacity-check.py
    python3 scripts/analyze/capacity-check.py path/to/DataGenerator.java

Reports:
  - Hours per lab tag vs available slots (utilization %)
  - Teacher bottlenecks per tag (busiest teacher)
  - Student group loading per tag
  - Regular classroom utilization
  - Overall teacher loading sorted by hours
"""

import re
import sys
import os

DEFAULT_GENERATOR = "src/main/java/com/school/planner/bootstrap/DataGenerator.java"


def find_generator(path=None):
    """Find DataGenerator.java from explicit path or auto-detect."""
    if path and os.path.isfile(path):
        return path
    # Try relative to script location
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(script_dir)
    candidate = os.path.join(repo_root, DEFAULT_GENERATOR)
    if os.path.isfile(candidate):
        return candidate
    # Try CWD
    candidate = os.path.join(os.getcwd(), DEFAULT_GENERATOR)
    if os.path.isfile(candidate):
        return candidate
    print(f"ERROR: Cannot find DataGenerator.java. Pass the path as argument.")
    sys.exit(1)


def parse_room_tags(content):
    """Extract the getRoomTags mapping from Java source.

    Supports both formats:
      - switch/case: case "Subject" -> List.of("tag");
      - startsWith:  if (subject.startsWith("Prefix")) return List.of("tag");
    """
    tag_map = {}
    match = re.search(r'private List<String> getRoomTags\(String subject\)\s*\{(.*?)\n    \}', content, re.DOTALL)
    if not match:
        print("WARNING: Could not parse getRoomTags method")
        return tag_map

    body = match.group(1)

    # Try startsWith format: if (subject.startsWith("X") || ...) return List.of("tag");
    # Use DOTALL to handle multi-line conditions
    for if_match in re.finditer(
        r'if\s*\((.*?)\)\s*return\s+List\.of\("([^"]*)"\)',
        body, re.DOTALL
    ):
        condition = if_match.group(1)
        tag = if_match.group(2)
        for prefix in re.findall(r'subject\.startsWith\("([^"]*)"\)', condition):
            tag_map[prefix] = tag

    # Fall back to switch/case format if no startsWith matches found
    if not tag_map:
        for case_match in re.finditer(
            r'case\s+((?:"[^"]*"(?:,\s*\n\s*"[^"]*")*\s*(?:,\s*\n\s*"[^"]*")*))\s*->\s*List\.of\("([^"]*)"\)',
            body
        ):
            subjects_raw = case_match.group(1)
            tag = case_match.group(2)
            subjects = re.findall(r'"([^"]*)"', subjects_raw)
            for subj in subjects:
                tag_map[subj] = tag

    return tag_map


def parse_rooms(content):
    """Extract room definitions: name, capacity, tags."""
    rooms = []
    for m in re.finditer(r'new Room\("([^"]*)",\s*(\d+),\s*List\.of\((.*?)\)\)', content):
        name = m.group(1)
        capacity = int(m.group(2))
        tags_raw = m.group(3)
        tags = re.findall(r'"([^"]*)"', tags_raw)
        rooms.append({"name": name, "capacity": capacity, "tags": tags})
    return rooms


def parse_courses(content):
    """Extract all addCourse calls: teacher, subject, group, hours."""
    pattern = r'addCourse\(teachers,\s*groups,\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*(\d+)\)'
    courses = []
    for m in re.finditer(pattern, content):
        courses.append({
            "teacher": m.group(1),
            "subject": m.group(2),
            "group": m.group(3),
            "hours": int(m.group(4)),
        })
    return courses


def parse_timeslot_count(content):
    """Try to determine number of timeslots from the data, or use the default.

    Default 1176 = 6 slots/day x 196 school days; overridable via the
    PLANNER_CAPACITY_TIMESLOTS env var (TASK-444) so it is not a magic number
    when the school year rolls over."""
    return int(os.environ.get("PLANNER_CAPACITY_TIMESLOTS", "1176"))


def run_analysis(generator_path):
    with open(generator_path) as f:
        content = f.read()

    tag_map = parse_room_tags(content)
    rooms = parse_rooms(content)
    courses = parse_courses(content)
    num_timeslots = parse_timeslot_count(content)

    # Build room capacity per tag
    rooms_by_tag = {}
    regular_rooms = []
    for r in rooms:
        if r["tags"]:
            tag = r["tags"][0]
            if tag not in rooms_by_tag:
                rooms_by_tag[tag] = {"rooms": [], "slots": 0}
            rooms_by_tag[tag]["rooms"].append(r["name"])
            rooms_by_tag[tag]["slots"] += num_timeslots
        else:
            regular_rooms.append(r["name"])

    regular_slots = len(regular_rooms) * num_timeslots

    # Count hours per tag
    tag_hours = {}
    tag_details = {}
    total_hours = 0

    for c in courses:
        total_hours += c["hours"]
        # Match by exact key or startsWith prefix
        tag = tag_map.get(c["subject"])
        if not tag:
            for prefix, t in tag_map.items():
                if c["subject"].startswith(prefix):
                    tag = t
                    break
        if tag:
            tag_hours[tag] = tag_hours.get(tag, 0) + c["hours"]
            if tag not in tag_details:
                tag_details[tag] = []
            tag_details[tag].append(c)

    regular_hours = total_hours - sum(tag_hours.values())

    # Print report
    print(f"CAPACITY ANALYSIS")
    print(f"{'=' * 70}")
    print(f"Source: {generator_path}")
    print(f"Timeslots per room: {num_timeslots}")
    print(f"Total lesson-hours: {total_hours}")
    print()

    print(f"REGULAR CLASSROOMS")
    print(f"-" * 70)
    print(f"  Rooms: {len(regular_rooms)} ({', '.join(regular_rooms[:5])}{'...' if len(regular_rooms) > 5 else ''})")
    print(f"  Hours: {regular_hours} / {regular_slots} slots")
    print(f"  Utilization: {regular_hours / regular_slots * 100:.1f}%")
    print()

    has_issues = False

    print(f"LAB ROOMS BY TAG")
    print(f"-" * 70)
    for tag in sorted(tag_hours.keys()):
        h = tag_hours[tag]
        info = rooms_by_tag.get(tag, {"rooms": ["UNKNOWN"], "slots": 0})
        slots = info["slots"]
        room_names = ", ".join(info["rooms"])
        pct = h / slots * 100 if slots > 0 else 999
        status = "OK" if h <= slots else "OVER CAPACITY"

        if h > slots:
            has_issues = True

        print(f"\n  Tag '{tag}': {room_names}")
        print(f"  Hours: {h} / {slots} slots  ({pct:.1f}%)  [{status}]")

        # By student group
        by_group = {}
        by_teacher = {}
        by_subject = {}
        for c in tag_details[tag]:
            by_group[c["group"]] = by_group.get(c["group"], 0) + c["hours"]
            by_teacher[c["teacher"]] = by_teacher.get(c["teacher"], 0) + c["hours"]
            by_subject[c["subject"]] = by_subject.get(c["subject"], 0) + c["hours"]

        print(f"  By subject:")
        for subj in sorted(by_subject.keys()):
            print(f"    {subj}: {by_subject[subj]}h")

        print(f"  By student group:")
        for grp in sorted(by_group.keys()):
            print(f"    {grp}: {by_group[grp]}h")

        print(f"  By teacher:")
        for t in sorted(by_teacher.keys(), key=lambda x: by_teacher[x], reverse=True):
            print(f"    {t}: {by_teacher[t]}h")

        max_teacher = max(by_teacher.values())
        max_teacher_name = [t for t, hh in by_teacher.items() if hh == max_teacher][0]
        print(f"  Busiest teacher: {max_teacher_name} ({max_teacher}h = {max_teacher / num_timeslots * 100:.0f}% of year)")

    # Teacher total hours
    print(f"\n\nTEACHER TOTAL HOURS")
    print(f"{'=' * 70}")
    teacher_total = {}
    for c in courses:
        teacher_total[c["teacher"]] = teacher_total.get(c["teacher"], 0) + c["hours"]

    for t in sorted(teacher_total.keys(), key=lambda x: teacher_total[x], reverse=True):
        h = teacher_total[t]
        pct = h / num_timeslots * 100
        bar = "#" * int(pct / 2)
        print(f"  {t:35s} {h:5d}h  ({pct:4.0f}%)  {bar}")

    # Summary
    print(f"\n\nSUMMARY")
    print(f"{'=' * 70}")
    if has_issues:
        print("  FEASIBILITY ISSUE: One or more lab tags exceed available room capacity.")
        print("  The solver CANNOT find a valid solution with current data.")
    else:
        print("  All lab tags fit within available room capacity.")
        print("  The problem is feasible in raw capacity terms.")
        print("  (Teacher/group scheduling conflicts may still make it hard to solve.)")

    return not has_issues


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else None
    generator = find_generator(path)
    feasible = run_analysis(generator)
    sys.exit(0 if feasible else 1)
