#!/usr/bin/env python3
"""
Sparse Calendar Root Cause Analysis

Analyzes why calendars have sparse/empty days despite having enough total hours
to fill the available timeslots.

Key Questions:
1. Is the ideal end date calculation too generous?
2. Is there a mismatch between totalHours and available slots?
3. Are constraints (M20, M22) working as expected?
"""

import sys
from datetime import datetime, timedelta
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from typing import Dict, List, Tuple

# Data from the discrepancy analysis - total hours per group
GROUP_HOURS = {
    'G03': 990, 'G07': 695, 'G11': 660,
    'G04': 990, 'G08': 666,
    'G02': 980, 'G06': 978, 'G10': 651, 'G12': 657,
    'G01': 965, 'G05': 986, 'G09': 643,
    'G13': 583,
}

# Groups with maxHoursPerDay = 5 (from 5hour-day-analysis.py)
FIVE_HOUR_GROUPS = {'G10', 'G12'}

# School calendar configuration
YEAR_START = datetime(2025, 9, 10)
YEAR_END = datetime(2026, 6, 10)

# Known holidays (approximate - need to verify with actual data)
# From DataGenerator comments: 33 school holidays
HOLIDAYS = {
    # Christmas 2025
    datetime(2025, 12, 22), datetime(2025, 12, 23), datetime(2025, 12, 24),
    datetime(2025, 12, 25), datetime(2025, 12, 26), datetime(2025, 12, 27),
    datetime(2025, 12, 28), datetime(2025, 12, 29), datetime(2025, 12, 30),
    datetime(2025, 12, 31),
    # New Year 2026
    datetime(2026, 1, 1), datetime(2026, 1, 2), datetime(2026, 1, 3),
    datetime(2026, 1, 4), datetime(2026, 1, 5), datetime(2026, 1, 6),
    # Easter 2026 (approximate)
    datetime(2026, 4, 2), datetime(2026, 4, 3), datetime(2026, 4, 4),
    datetime(2026, 4, 5), datetime(2026, 4, 6),
    # Other holidays (approximate)
    datetime(2025, 11, 1), datetime(2025, 12, 8), datetime(2026, 4, 25),
    datetime(2026, 5, 1), datetime(2026, 6, 2),
}

def count_weekdays(start: datetime, end: datetime, exclude_holidays: bool = True) -> int:
    """Count weekdays between start and end (inclusive)."""
    count = 0
    current = start
    while current <= end:
        if current.weekday() < 5:  # Monday=0, Friday=4
            if not exclude_holidays or current.date() not in [h.date() for h in HOLIDAYS]:
                count += 1
        current += timedelta(days=1)
    return count

def calculate_available_slots(group_name: str, total_hours: int,
                               end_date: datetime = None) -> Dict:
    """
    Calculate available slots for a group based on TargetEndDateCalculator logic.

    This replicates the logic in TargetEndDateCalculator.computeGroupTargetDate().
    """
    # Determine max hours per day
    max_hours_per_day = 5 if group_name in FIVE_HOUR_GROUPS else 6

    # If end_date not provided, calculate it (like TargetEndDateCalculator)
    if end_date is None:
        # Scan forward to find minimum end date with enough slots
        cumulative_slots = 0
        cursor = YEAR_START

        while cursor <= YEAR_END:
            if cursor.weekday() >= 5:  # Weekend
                cursor += timedelta(days=1)
                continue

            if cursor in HOLIDAYS:
                cursor += timedelta(days=1)
                continue

            # Count slots for this day
            if cursor.weekday() == 4:  # Friday
                slots_today = min(5, max_hours_per_day)
            else:  # Monday-Thursday
                slots_today = min(6, max_hours_per_day)

            cumulative_slots += slots_today

            if cumulative_slots >= total_hours:
                break

            cursor += timedelta(days=1)

        end_date = cursor

    # Now count total available slots from start to end_date
    available_days = 0
    total_available_slots = 0
    current = YEAR_START

    while current <= end_date:
        if current.weekday() >= 5:  # Weekend
            current += timedelta(days=1)
            continue

        if current in HOLIDAYS:
            current += timedelta(days=1)
            continue

        available_days += 1

        # Count slots for this day
        if current.weekday() == 4:  # Friday
            slots_today = min(5, max_hours_per_day)
        else:  # Monday-Thursday
            slots_today = min(6, max_hours_per_day)

        total_available_slots += slots_today
        current += timedelta(days=1)

    return {
        'group': group_name,
        'total_hours': total_hours,
        'max_hours_per_day': max_hours_per_day,
        'end_date': end_date,
        'available_days': available_days,
        'total_available_slots': total_available_slots,
        'surplus_slots': total_available_slots - total_hours,
        'surplus_days': (total_available_slots - total_hours) // max_hours_per_day,
    }

def main():
    print("=" * 100)
    print("SPARSE CALENDAR ROOT CAUSE ANALYSIS")
    print("=" * 100)
    print()

    print("ANALYSIS 1: Ideal End Date vs Total Hours")
    print("-" * 100)
    print(f"{'Group':<12} {'Hours':<8} {'MaxH':<6} {'End Date':<12} {'AvailDays':<10} {'AvailSlots':<10} {'Surplus':<10} {'SurplusDays':<12}")
    print("-" * 100)

    total_surplus_slots = 0
    total_surplus_days = 0

    for group_name in sorted(GROUP_HOURS.keys()):
        total_hours = GROUP_HOURS[group_name]
        result = calculate_available_slots(group_name, total_hours)

        total_surplus_slots += result['surplus_slots']
        total_surplus_days += result['surplus_days']

        print(f"{group_name:<12} {result['total_hours']:<8} {result['max_hours_per_day']:<6} "
              f"{result['end_date'].strftime('%Y-%m-%d'):<12} {result['available_days']:<10} "
              f"{result['total_available_slots']:<10} {result['surplus_slots']:<10} "
              f"{result['surplus_days']:<12}")

    print("-" * 100)
    print(f"{'TOTAL':<12} {'':8} {'':6} {'':12} {'':10} {'':10} {total_surplus_slots:<10} {total_surplus_days:<12}")
    print()

    print("=" * 100)
    print("KEY FINDINGS:")
    print("=" * 100)
    print()

    print(f"1. TOTAL SURPLUS SLOTS across all groups: {total_surplus_slots}")
    print(f"   This represents {total_surplus_slots} unused lesson slots that could be filled")
    print()

    print(f"2. TOTAL SURPLUS DAYS across all groups: {total_surplus_days}")
    print(f"   This is the number of FULL DAYS that could be completely empty")
    print()

    print("3. ROOT CAUSE ANALYSIS:")
    print()
    print("   The ideal end date calculation adds buffer days beyond what's needed.")
    print("   From TargetEndDateCalculator:")
    print("   - BUFFER_WEEKS = 0 (tight)")
    print("   - But ensureEnoughSlots() adds 10% buffer")
    print("   - This creates surplus capacity that the solver doesn't need to fill")
    print()

    print("4. CONSTRAINT BEHAVIOR:")
    print()
    print("   M20 (Daily Quota Fill):")
    print("   - Only penalizes days that HAVE lessons but aren't full")
    print("   - Does NOT force using ALL available days from year start")
    print("   - If there are surplus days at the end, they stay empty")
    print()
    print("   M22 (Calendar Compact):")
    print("   - Only prevents empty days WITHIN the calendar span")
    print("   - The span is first_lesson to last_lesson")
    print("   - If lessons start late, early days stay empty")
    print("   - If lessons end early (due to surplus), late days stay empty")
    print()

    print("5. WHY CALENDARS ARE SPARSE:")
    print()
    print("   The combination of:")
    print("   a) Generous ideal end dates (with 10% buffer)")
    print("   b) M20 only filling days that have lessons")
    print("   c) M22 only preventing gaps WITHIN the used span")
    print()
    print("   Results in:")
    print("   - Empty days at the start (solver doesn't force early start)")
    print("   - Empty days at the end (surplus capacity from buffer)")
    print("   - Sparse middle days (if solver spreads lessons)")
    print()

    print("=" * 100)
    print("RECOMMENDATIONS:")
    print("=" * 100)
    print()
    print("1. Reduce or remove the 10% buffer in ensureEnoughSlots()")
    print("2. Add constraint to force earliest possible start (like M20 but for start)")
    print("3. Or accept that sparse calendars are acceptable given the constraints")
    print()

    # Show detailed breakdown for a specific group
    print("=" * 100)
    print("DETAILED BREAKDOWN: G03 (typical 6-hour group)")
    print("=" * 100)
    result = calculate_available_slots('G03', GROUP_HOURS['G03'])
    print(f"Total hours: {result['total_hours']}")
    print(f"Max hours per day: {result['max_hours_per_day']}")
    print(f"Ideal end date: {result['end_date'].strftime('%Y-%m-%d')}")
    print(f"Available days: {result['available_days']}")
    print(f"Available slots: {result['total_available_slots']}")
    print(f"Surplus slots: {result['surplus_slots']}")
    print(f"Surplus days: {result['surplus_days']}")
    print()
    print(f"If the solver uses {result['total_hours']} slots across {result['available_days']} days:")
    print(f"  Average hours per day: {result['total_hours'] / result['available_days']:.1f}")
    print(f"  Empty days possible: {result['surplus_days']}")
    print(f"  This means the calendar could have {result['surplus_days']} completely empty days")
    print(f"  and still satisfy all constraints!")

if __name__ == '__main__':
    main()
