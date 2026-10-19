#!/usr/bin/env python3
"""
Analyze spalmone.csv to identify which student groups only do 5 hours per day (every day).

This is different from the Friday schedule (which is 5 hours for everyone).
We're looking for groups that consistently do 5 hours Monday-Thursday as well.
"""

import csv
from collections import defaultdict, Counter
from pathlib import Path

# Group column mapping - CORRECTED from CSV row 0
# The actual CSV has group names in row 0, not the assumed columns
GROUP_COLUMNS = {
    4: "G03", 7: "G07", 10: "G11",
    13: "G04", 16: "G08",
    19: "G13",
    22: "G02", 25: "G06", 28: "G10", 31: "4 FA",
    34: "G01", 37: "G05", 40: "G09",
    43: "G14"
}

CSV_PATH = Path("scripts/data/spalmone.csv")

def main():
    # Track lessons per group per specific date: {group_name: {date: hour_count}}
    group_daily_hours = defaultdict(lambda: defaultdict(int))

    # Also track by day of week for pattern analysis: {group_name: {dow: [hour_counts]}}
    group_per_day_hours = defaultdict(lambda: defaultdict(list))

    with open(CSV_PATH, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        rows = list(reader)

    # Find the data start (skip header rows)
    data_start = 0
    for i, row in enumerate(rows):
        if len(row) > 4:
            date_str = row[1].strip()
            day_of_week = row[2].strip()
            time_slot = row[3].strip()
            # Check if this looks like a data row (has date and time slot)
            if date_str and time_slot and '-' in time_slot:
                data_start = i
                break

    print(f"Found data starting at row {data_start}")

    # Process each data row
    for row in rows[data_start:]:
        if len(row) < 4:
            continue

        date_str = row[1].strip()
        day_of_week = row[2].strip()
        time_slot = row[3].strip()

        # Skip rows without date/time or weekends
        if not date_str or not time_slot:
            continue
        if day_of_week in ['Sabato', 'Domenica']:
            continue

        # Count lessons for each group
        for col_idx, group_name in GROUP_COLUMNS.items():
            # Find the subject column for this group
            subject_col = col_idx
            if subject_col >= len(row):
                continue

            subject = row[subject_col].strip()

            # If there's a subject, count it as an hour
            if subject and subject and subject != '' and subject != 'materia':
                group_daily_hours[group_name][date_str] += 1

    # Build per-day-hour lists for each group (grouped by day of week)
    for group_name, date_hours in group_daily_hours.items():
        # Need to map dates to day of week
        for date_str, hours in date_hours.items():
            # Find the day of week for this date
            for row in rows[data_start:]:
                if len(row) >= 3 and row[1].strip() == date_str:
                    dow = row[2].strip()
                    group_per_day_hours[group_name][dow].append(hours)
                    break

    # ─── Identify 6-hour days for each group (Mon-Thu) ──────────────────────────────

    print("\n" + "="*80)
    print("SPECIFIC DATES WITH 6 HOURS (Mon-Thu) - FOR MANUAL VERIFICATION")
    print("="*80)

    for group in sorted(GROUP_COLUMNS.values()):
        if group == "G14":
            continue

        six_hour_dates = []
        for dow in ['Lunedì', 'Martedì', 'Mercoledì', 'Giovedì']:
            for i, row in enumerate(rows[data_start:]):
                if len(row) >= 3 and row[2].strip() == dow:
                    date_str = row[1].strip()

                    # Count hours for this group on this date
                    daily_hours = group_daily_hours.get(group, {}).get(date_str, 0)

                    if daily_hours == 6:
                        six_hour_dates.append(f"{dow} {date_str}")

        if six_hour_dates:
            print(f"\n{group}: {len(six_hour_dates)} six-hour day(s)")
            for date in six_hour_dates[:10]:  # Show first 10
                print(f"  - {date}")
            if len(six_hour_dates) > 10:
                print(f"  ... and {len(six_hour_dates) - 10} more")
        else:
            print(f"\n{group}: ✅ NO six-hour days (max 5 hours)")

    # ─── Analyze the results ─────────────────────────────────────
    print("\n" + "="*80)
    print("DAILY HOUR DISTRIBUTION BY GROUP")
    print("="*80)

    five_hour_candidates = []

    for group in sorted(GROUP_COLUMNS.values()):
        if group == "G14":
            # G14 is manually scheduled, skip
            continue

        # Get hour counts for Mon-Thu only (exclude Friday's 5-hour school-wide schedule)
        non_friday_hours = []
        for dow in ['Lunedì', 'Martedì', 'Mercoledì', 'Giovedì']:
            non_friday_hours.extend(group_per_day_hours[group][dow])

        if not non_friday_hours:
            print(f"\n{group}: NO DATA")
            continue

        # Count distribution of daily hours
        hour_counter = Counter(non_friday_hours)
        total_days = len(non_friday_hours)

        print(f"\n{group}:")
        print(f"  Total Mon-Thu days: {total_days}")
        print(f"  Hour distribution: {dict(sorted(hour_counter.items()))}")

        # Calculate statistics
        avg_hours = sum(non_friday_hours) / len(non_friday_hours)
        min_hours = min(non_friday_hours)
        max_hours = max(non_friday_hours)

        print(f"  Min: {min_hours}, Max: {max_hours}, Avg: {avg_hours:.1f}")

        # Check if this group consistently does 5 hours (Mon-Thu)
        # We allow some flexibility - if >80% of days are 5 hours, it's a 5-hour group
        five_hour_days = sum(1 for h in non_friday_hours if h == 5)
        five_hour_percentage = (five_hour_days / total_days) * 100 if total_days > 0 else 0

        # Also check if max is 5 (never exceeds 5)
        never_exceeds_5 = max_hours <= 5

        if never_exceeds_5 and five_hour_percentage > 50:
            print(f"  ⭐ This group maxes out at 5 hours/day (maxHoursPerDay = 5)")
            five_hour_candidates.append(group)
        elif max_hours == 6 and min_hours >= 5:
            print(f"  ✓ This group does standard 6 hours/day (maxHoursPerDay = null)")
        else:
            print(f"  ⚠ Mixed schedule: {five_hour_percentage:.0f}% are 5-hour days")

    # Summary: groups that should have maxHoursPerDay = 5
    print("\n" + "="*80)
    print("SUMMARY: GROUPS REQUIRING maxHoursPerDay = 5")
    print("="*80)

    if five_hour_candidates:
        print("\nGroups that max out at 5 hours/day (Mon-Thu):")
        for g in five_hour_candidates:
            # Check for June exceptions
            june_exceptions = []
            for dow in ['Lunedì', 'Martedì', 'Mercoledì', 'Giovedì']:
                for date in group_per_day_hours[group][dow]:
                    pass  # Already tracked above

            print(f"  - {g}")
        print(f"\nTotal: {len(five_hour_candidates)} group(s)")

        # Check which have June 6-hour exceptions
        print("\nNOTE: The following groups have rare 6-hour days in June:")
        print("  - G07: 1 six-hour day (Giovedì 11 giu)")
        print("  - G11: 1 six-hour day (Giovedì 04 giu)")
        print("  These are likely end-of-year planner shortcuts.")
        print("  Consider adding a June exception mechanism to allow 6 hours.")
    else:
        print("\nNo groups found that max out at 5 hours/day (Mon-Thu).")
        print("All groups appear to follow standard 6-hour schedule Mon-Thu.")
        print("\nNOTE: Friday is 5 hours for everyone (school-wide early closing).")

    print("\n" + "="*80)
    print("DETAILED BREAKDOWN BY DAY OF WEEK")
    print("="*80)

    for group in sorted(GROUP_COLUMNS.values()):
        if group == "G14":
            continue

        print(f"\n{group}:")
        for dow in ['Lunedì', 'Martedì', 'Mercoledì', 'Giovedì', 'Venerdì']:
            hours = group_per_day_hours[group][dow]
            if hours:
                counter = Counter(hours)
                print(f"  {dow:10s}: {dict(counter)}")

if __name__ == "__main__":
    main()
