#!/usr/bin/env python3
"""
Lesson Placement Analysis Script

Analyzes where lessons are actually placed in the current solution compared to:
- Total lessons generated from Course.totalHours
- Lessons placed before vs after idealEndDate
- Valid timeslots count from completion API

This helps identify:
- Lessons placed outside the "valid" range (after idealEndDate)
- Mismatches between generated and assigned lessons
- Date ranges of actual lesson placement

Usage:
    python3 scripts/analyze/lesson-placement-analysis.py

Requirements:
    - PostgreSQL database must be running
    - Uses psycopg2 (install with: pip install psycopg2-binary)
"""

import sys
from datetime import datetime
from typing import Dict, List, Tuple

try:
    import psycopg2
    from psycopg2.extras import RealDictCursor
except ImportError:
    print("Error: psycopg2 not installed. Install with: pip install psycopg2-binary")
    sys.exit(1)


def get_db_connection():
    """Connect to the planner PostgreSQL database."""
    try:
        conn = psycopg2.connect(
            host="localhost",
            port=5432,
            database="planner",
            user="planner",
            password="planner"
        )
        return conn
    except psycopg2.OperationalError as e:
        print(f"Error: Could not connect to database: {e}")
        print("\nMake sure the database is running:")
        print("  ./planner.sh start <profile>")
        sys.exit(1)


def analyze_group(conn, group_name: str, group_id: int) -> Dict:
    """Analyze lesson placement for a single student group."""
    cursor = conn.cursor(cursor_factory=RealDictCursor)

    # Get total hours from Course records (what should be scheduled)
    cursor.execute("""
        SELECT COALESCE(SUM(c.totalHours), 0) as total_hours
        FROM course c
        WHERE c.studentgroup_id = %s
    """, (group_id,))
    total_hours = cursor.fetchone()['total_hours']

    # Get ideal end date for this group
    cursor.execute("""
        SELECT s.idealenddate, s.startdate
        FROM studentgroup s
        WHERE s.id = %s
    """, (group_id,))
    group_info = cursor.fetchone()
    ideal_end_date = group_info['idealenddate']
    start_date = group_info['startdate']

    # Get assigned lessons
    cursor.execute("""
        SELECT COUNT(*) as total_assigned
        FROM lesson l
        JOIN timeslot t ON l.timeslot_id = t.id
        WHERE l.course_id IN (
            SELECT c.id FROM course c WHERE c.studentgroup_id = %s
        )
        AND l.room_id IS NOT NULL
    """, (group_id,))
    total_assigned = cursor.fetchone()['total_assigned']

    # Get lessons assigned before ideal end date
    cursor.execute("""
        SELECT COUNT(*) as before_ideal
        FROM lesson l
        JOIN timeslot t ON l.timeslot_id = t.id
        WHERE l.course_id IN (
            SELECT c.id FROM course c WHERE c.studentgroup_id = %s
        )
        AND l.room_id IS NOT NULL
        AND t.date < %s
    """, (group_id, ideal_end_date))
    before_ideal = cursor.fetchone()['before_ideal']

    # Get lessons assigned after ideal end date
    cursor.execute("""
        SELECT COUNT(*) as after_ideal,
               MIN(t.date) as earliest_overflow,
               MAX(t.date) as latest_overflow
        FROM lesson l
        JOIN timeslot t ON l.timeslot_id = t.id
        WHERE l.course_id IN (
            SELECT c.id FROM course c WHERE c.studentgroup_id = %s
        )
        AND l.room_id IS NOT NULL
        AND t.date > %s
    """, (group_id, ideal_end_date))
    overflow_info = cursor.fetchone()
    after_ideal = overflow_info['after_ideal']
    earliest_overflow = overflow_info['earliest_overflow']
    latest_overflow = overflow_info['latest_overflow']

    # Get overall date range of assigned lessons
    cursor.execute("""
        SELECT MIN(t.date) as earliest_lesson,
               MAX(t.date) as latest_lesson
        FROM lesson l
        JOIN timeslot t ON l.timeslot_id = t.id
        WHERE l.course_id IN (
            SELECT c.id FROM course c WHERE c.studentgroup_id = %s
        )
        AND l.room_id IS NOT NULL
    """, (group_id,))
    date_range = cursor.fetchone()

    # Get valid timeslots count (from completion API logic)
    cursor.execute("""
        WITH valid_timeslots AS (
            SELECT t.id, t.date, t.slotindex
            FROM timeslot t
            WHERE t.date >= %s
            AND t.date <= %s
            AND NOT EXISTS (
                SELECT 1 FROM schoolholiday h WHERE h.date = t.date
            )
        )
        SELECT COUNT(*) as valid_count
        FROM valid_timeslots
    """, (start_date, ideal_end_date))
    valid_timeslots = cursor.fetchone()['valid_count']

    cursor.close()

    return {
        'group_name': group_name,
        'total_hours': total_hours,
        'total_assigned': total_assigned,
        'before_ideal': before_ideal,
        'after_ideal': after_ideal,
        'earliest_overflow': earliest_overflow,
        'latest_overflow': latest_overflow,
        'earliest_lesson': date_range['earliest_lesson'],
        'latest_lesson': date_range['latest_lesson'],
        'valid_timeslots': valid_timeslots,
        'ideal_end_date': ideal_end_date,
        'start_date': start_date,
        'unassigned': total_hours - total_assigned
    }


def print_report(groups: List[Dict]):
    """Print formatted analysis report."""
    print("=" * 120)
    print("LESSON PLACEMENT ANALYSIS REPORT")
    print("=" * 120)
    print(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()

    # Summary header
    print(f"{'Group':<15} {'Total':<7} {'Assigned':<8} {'Unassigned':<10} {'Before':<7} {'After':<7} "
          f"{'Valid Slots':<11} {'Start':<12} {'Ideal End':<12} {'Actual Range':<20}")
    print("-" * 120)

    total_groups = len(groups)
    total_hours = sum(g['total_hours'] for g in groups)
    total_assigned = sum(g['total_assigned'] for g in groups)
    total_after_ideal = sum(g['after_ideal'] for g in groups)
    groups_with_overflow = 0

    for group in groups:
        # Format date range
        if group['earliest_lesson'] and group['latest_lesson']:
            date_range = f"{group['earliest_lesson']} to {group['latest_lesson']}"
        else:
            date_range = "No lessons assigned"

        # Overflow indicator
        overflow_marker = " ⚠️" if group['after_ideal'] > 0 else ""

        print(f"{group['group_name']:<15} "
              f"{group['total_hours']:<7} "
              f"{group['total_assigned']:<8} "
              f"{group['unassigned']:<10} "
              f"{group['before_ideal']:<7} "
              f"{group['after_ideal']:<7} "
              f"{group['valid_timeslots']:<11} "
              f"{group['start_date']!s:<12} "
              f"{group['ideal_end_date']!s:<12} "
              f"{date_range:<20}"
              f"{overflow_marker}")

        if group['after_ideal'] > 0:
            groups_with_overflow += 1

    print("-" * 120)
    print(f"{'TOTALS':<15} "
          f"{total_hours:<7} "
          f"{total_assigned:<8} "
          f"{total_hours - total_assigned:<10} "
          f"- "
          f"{total_after_ideal:<7} "
          f"- "
          f"{'GROUPS WITH OVERFLOW:':>15} {groups_with_overflow}/{total_groups}")
    print("=" * 120)

    # Detailed overflow analysis
    if groups_with_overflow > 0:
        print("\n⚠️  DETAILED OVERFLOW ANALYSIS")
        print("-" * 120)
        for group in groups:
            if group['after_ideal'] > 0:
                print(f"\n{group['group_name']}: {group['after_ideal']} lessons after ideal end date")
                print(f"  Ideal end date: {group['ideal_end_date']}")
                print(f"  Overflow range: {group['earliest_overflow']} to {group['latest_overflow']}")

                # Calculate overflow days
                if group['latest_overflow']:
                    overflow_days = (group['latest_overflow'] - group['ideal_end_date']).days
                    print(f"  Overflow extends {overflow_days} days beyond ideal end")
        print()

    # Unassigned lessons analysis
    total_unassigned = total_hours - total_assigned
    if total_unassigned > 0:
        print(f"\n⚠️  UNASSIGNED LESSONS: {total_unassigned} total hours not yet assigned")
        for group in groups:
            if group['unassigned'] > 0:
                print(f"  {group['group_name']}: {group['unassigned']} hours")
        print()


def main():
    """Main execution function."""
    print("Lesson Placement Analysis")
    print("=" * 40)
    print()

    conn = get_db_connection()
    print("Connected to database")

    # Get all student groups
    cursor = conn.cursor(cursor_factory=RealDictCursor)
    cursor.execute("SELECT id, name FROM studentgroup ORDER BY name")
    groups_data = cursor.fetchall()
    cursor.close()

    print(f"Found {len(groups_data)} student groups")
    print()

    # Analyze each group
    groups = []
    for group_data in groups_data:
        group_info = analyze_group(conn, group_data['name'], group_data['id'])
        groups.append(group_info)

    conn.close()

    # Print report
    print_report(groups)

    # Exit with error code if there are issues
    total_after_ideal = sum(g['after_ideal'] for g in groups)
    total_unassigned = sum(g['unassigned'] for g in groups)

    if total_after_ideal > 0 or total_unassigned > 0:
        print("\n⚠️  ISSUES DETECTED:")
        if total_after_ideal > 0:
            print(f"  - {total_after_ideal} lessons placed AFTER ideal end date")
        if total_unassigned > 0:
            print(f"  - {total_unassigned} lessons not yet assigned")
        sys.exit(1)
    else:
        print("\n✅ All lessons assigned within valid date ranges")
        sys.exit(0)


if __name__ == "__main__":
    main()
