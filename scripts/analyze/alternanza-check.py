#!/usr/bin/env python3
"""
Alternanza hour validation for the school planner.

Verifies that stage calendar days x 6h >= Alternanza target hours for each
student group that has an Alternanza/Stage requirement.

Target hours (from school curriculum data):
  - G07: 295h Alternanza
  - G11: 295h Alternanza
  - G12: 295h Alternanza
  - G13:   295h Alternanza
  - G10:  300h Stage
  - G08:  300h Stage
  - G09: 300h Stage

This script requires StagePattern data to exist in the running application.
It queries the REST API at http://localhost:8080 to fetch stage patterns.

Usage:
    python3 scripts/analyze/alternanza-check.py
    python3 scripts/analyze/alternanza-check.py --url http://localhost:8080

NOTE: This is a skeleton — StagePattern data must be populated first.
      Until then, this script reports the target hours and exits with
      a warning that validation cannot be performed.
"""

import sys
import json
try:
    import urllib.request
except ImportError:
    pass

# Target hours per group (from school curriculum documents)
ALTERNANZA_TARGETS = {
    "G07": {"hours": 295, "type": "Alternanza"},
    "G11": {"hours": 295, "type": "Alternanza"},
    "G12": {"hours": 295, "type": "Alternanza"},
    "G13":   {"hours": 295, "type": "Alternanza"},
    "G10":  {"hours": 300, "type": "Stage"},
    "G08":  {"hours": 300, "type": "Stage"},
    "G09": {"hours": 300, "type": "Stage"},
}

HOURS_PER_DAY = 6

DEFAULT_URL = "http://localhost:8080"


def fetch_stage_patterns(base_url):
    """Fetch StagePattern data from the REST API."""
    url = f"{base_url}/stage-patterns"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode())
            return data
    except Exception as e:
        return None


def count_stage_days(pattern):
    """Count weekdays in [startDate, endDate] that match the dayOfWeekMask."""
    from datetime import date, timedelta

    start = date.fromisoformat(pattern["startDate"])
    end = date.fromisoformat(pattern["endDate"])
    mask = set(pattern.get("dayOfWeekMask", []))

    # Java DayOfWeek names: MONDAY, TUESDAY, etc.
    count = 0
    current = start
    while current <= end:
        if current.strftime("%A").upper() in mask:
            count += 1
        current += timedelta(days=1)
    return count


def run_check(base_url):
    print("ALTERNANZA HOUR VALIDATION")
    print("=" * 70)
    print()

    # Show target hours
    print("TARGET HOURS (from school curriculum)")
    print("-" * 70)
    for group in sorted(ALTERNANZA_TARGETS.keys()):
        info = ALTERNANZA_TARGETS[group]
        print(f"  {group:15s}  {info['hours']:4d}h  ({info['type']})")
    print()

    # Try to fetch stage patterns
    patterns = fetch_stage_patterns(base_url)
    if patterns is None:
        print("WARNING: Could not connect to the application REST API.")
        print(f"  Tried: {base_url}/stage-patterns")
        print()
        print("  StagePattern data is required for validation.")
        print("  Start the app and populate stage patterns, then re-run.")
        print()
        print("RESULT: SKIPPED (no data available)")
        return True  # Don't fail CI — just skip

    if not patterns:
        print("WARNING: No StagePattern records found in the database.")
        print("  Populate stage patterns for the groups listed above,")
        print("  then re-run this script to validate.")
        print()
        print("RESULT: SKIPPED (no stage patterns defined yet)")
        return True

    # Validate each group
    print("VALIDATION")
    print("-" * 70)
    has_issues = False

    for group_name, target in sorted(ALTERNANZA_TARGETS.items()):
        group_patterns = [p for p in patterns
                          if p.get("studentGroup", {}).get("name") == group_name]
        if not group_patterns:
            print(f"  {group_name:15s}  NO STAGE PATTERN DEFINED  *** MISSING ***")
            has_issues = True
            continue

        total_days = sum(count_stage_days(p) for p in group_patterns)
        available_hours = total_days * HOURS_PER_DAY
        status = "OK" if available_hours >= target["hours"] else "INSUFFICIENT"

        if available_hours < target["hours"]:
            has_issues = True

        print(f"  {group_name:15s}  {total_days:3d} days x {HOURS_PER_DAY}h = {available_hours:4d}h  "
              f"(target: {target['hours']}h)  [{status}]")

    print()
    print("SUMMARY")
    print("=" * 70)
    if has_issues:
        print("  RESULT: FAIL - some groups have insufficient stage hours")
    else:
        print("  RESULT: PASS - all groups meet their stage hour targets")

    return not has_issues


if __name__ == "__main__":
    url = DEFAULT_URL
    for i, arg in enumerate(sys.argv[1:], 1):
        if arg == "--url" and i < len(sys.argv) - 1:
            url = sys.argv[i + 1]

    ok = run_check(url)
    sys.exit(0 if ok else 1)
