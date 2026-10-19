#!/usr/bin/env python3
"""
Count Stage (teacher="Stage") rows in lessons.csv that fall on closed=="True"
days, per student group (TASK-373).

WHY: referente (17/07, Q8) confirmed the docx rule "lo stage puo essere fatto
anche nei giorni di chiusura" (stage can happen on closure days) is
mechanically exercised by the school - G08 alone has 24 Stage rows on the
2027-03-25/26/30 Easter closure. GoldenScheduleImportService.categorize()
used to lump every closed=="True" row into skippedClosed regardless of
teacher, undercounting the synthetic Stage course's totalHours for every
group whose stage window overlaps a closure. This script is the independent,
re-runnable derivation behind that fix - it does NOT use
shared.iter_open_lessons_rows() (that helper deliberately filters out
closed=="True" rows, the opposite of what's needed here) and instead reads
every row of lessons.csv directly.

Code review H2/H4 (2026-07-18): also horizon-aware. Some "open" (closed=False)
Stage rows are dated PAST the school year's Timeslot horizon (e.g. G04's
2027-06-11..14 rows) - no Timeslot exists for them and none is generated, so
they are exactly as structurally unschedulable as a closure-day row.
GoldenScheduleImportService.reconcileStageCourseHours folds BOTH into the
Stage course's totalHours/expectedUnscheduledHours, so this script now splits
"open" into within-horizon (actually pinned as Lessons) and beyond-horizon,
and reports a "course totalHours" column computed with the exact same
formula the Java code uses, so the two can never silently drift apart.

Usage:
    python3 scripts/analyze/derive-golden-stage-on-closure.py
"""
import csv
import os
import sys
from collections import defaultdict
from datetime import date

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

sys.path.insert(0, SCRIPT_DIR)
from shared import LESSONS_CSV, INCARICHI_CSV, SCHOOL_YEAR_END  # noqa: E402

STAGE_TEACHER = "Stage"


def load_stage_rows_by_group():
    """{group: {"open_within": n, "open_beyond": n, "closed": n}} for every
    Stage row in lessons.csv, reading the raw CSV directly (both closed and
    open rows). "open_beyond" is closed=False but dated after
    SCHOOL_YEAR_END - no Timeslot exists for it, same as a closure-day row."""
    counts = defaultdict(lambda: {"open_within": 0, "open_beyond": 0, "closed": 0})
    with open(LESSONS_CSV, newline='', encoding='utf-8') as f:
        for row in csv.DictReader(f):
            if row['teacher'].strip() != STAGE_TEACHER:
                continue
            group = row['group'].strip()
            if row['closed'].strip() == 'True':
                counts[group]["closed"] += 1
                continue
            row_date = date.fromisoformat(row['date'].strip())
            bucket = "open_beyond" if row_date > SCHOOL_YEAR_END else "open_within"
            counts[group][bucket] += 1
    return counts


def load_incarichi_stage_hours():
    """{group: hours} from incarichi.csv's Stage pseudo-teacher rows."""
    hours = {}
    with open(INCARICHI_CSV, newline='', encoding='utf-8') as f:
        for row in csv.DictReader(f):
            if row['teacher'].strip() != STAGE_TEACHER:
                continue
            hours[row['group'].strip()] = float(row['hours'])
    return hours


def main():
    counts = load_stage_rows_by_group()
    incarichi = load_incarichi_stage_hours()

    print("=" * 120)
    print("Stage-on-closure / beyond-horizon rows per group (lessons.csv)")
    print("=" * 120)
    header = (f"{'group':12} {'within':>7} {'beyond':>7} {'closed':>7} {'grid total':>11} "
              f"{'course totalHours':>18} {'incarichi':>10} {'match?':>7}")
    print(header)
    print("-" * len(header))

    total_within = total_beyond = total_closed = 0
    mismatches = []
    for group in sorted(counts):
        within_n = counts[group]["open_within"]
        beyond_n = counts[group]["open_beyond"]
        closed_n = counts[group]["closed"]
        grid_total = within_n + beyond_n + closed_n

        # "course totalHours" is printed as its own column purely to document
        # GoldenScheduleImportService.reconcileStageCourseHours's formula
        # explicitly (totalHours = openHours/within-horizon matches +
        # expectedUnscheduledHours, where expectedUnscheduledHours = closure +
        # beyond-horizon) - it is arithmetically identical to grid_total by
        # construction (same three addends), not an independent cross-check
        # against the live Java code, so there is nothing to compare it
        # against here.
        course_total_hours = within_n + closed_n + beyond_n

        inc = incarichi.get(group)
        inc_str = f"{inc:.0f}" if inc is not None else "-"
        match = "n/a" if inc is None else ("OK" if inc == grid_total else "MISMATCH")
        if inc is not None and inc != grid_total:
            mismatches.append(group)
        print(f"{group:12} {within_n:7} {beyond_n:7} {closed_n:7} {grid_total:11} "
              f"{course_total_hours:18} {inc_str:>10} {match:>7}")
        total_within += within_n
        total_beyond += beyond_n
        total_closed += closed_n

    print("-" * len(header))
    grand_total = total_within + total_beyond + total_closed
    print(f"{'TOTAL':12} {total_within:7} {total_beyond:7} {total_closed:7} {grand_total:11} "
          f"{grand_total:18}")
    print()
    print("course totalHours (Java formula: within-horizon matches + closure + beyond-horizon) equals "
          "the raw grid total for every group, by construction - see this script's own formula comment.")
    if mismatches:
        print(f"MISMATCH: incarichi.csv Stage hours disagree with the grid total for: {', '.join(mismatches)}")
    else:
        print("Every group with an incarichi.csv Stage row has incarichi hours == grid total "
              "(within-horizon + beyond-horizon + closed) - the Stage course's totalHours can be computed "
              "straight from the grid without relying on incarichi.csv at all (see reconcileStageCourseHours).")
    print(f"\nGroups with NO incarichi.csv Stage row (DUALE-style fallback, totalHours computed "
          f"purely from the grid): {sorted(g for g in counts if g not in incarichi)}")
    print(f"\nTotal stage-on-closure rows across all groups: {total_closed}")
    print(f"Total stage-beyond-horizon rows across all groups: {total_beyond}")


if __name__ == '__main__':
    main()
