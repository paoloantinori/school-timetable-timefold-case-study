#!/usr/bin/env python3
"""Day-start anchoring KPI (TASK-405, maintainer 2026-07-23).

Anchored rate = share of (group, date) days whose FIRST lesson sits at the
group's first calendar-open slot. All 2026/27 calendars open at slot 0
(hour 8) on every open weekday, except the first-Monday ramp (2026-09-14,
opens at slot 1 / hour 9) - handled below. Reference values: golden 99.3%
raw (~100% vs the first OPEN slot); pre-405 solver runs measured 81.0%.

Usage:
  python3 scripts/analyze/day-start-kpi.py <lessons-dump.txt>

where <lessons-dump.txt> is the same group|date|hour|subject|teacher|pinned
dump daily-fill-kpi.py documents (db-query.sh SELECT in its header), or a
laptop-artifact lessons.txt.

Java mirror (kept in lockstep): the widened M18 constraint
(SchoolScheduleConstraintProvider.dayShouldStartFromFirstOpenSlot) scores the
same definition per (group, date), offset slots against
SchoolSchedule.getGroupCalendarWindow's minOpenSlot.
"""
import argparse
import os
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shared import RAMP_DATE, add_kpi_args  # noqa: E402

BASE_HOUR = 8


def main(path, ramp_date=RAMP_DATE.isoformat()):
    first = {}
    for line in open(path):
        t = line.strip()
        if not t or t == 'SET' or '|' not in t:
            continue
        f = [x.strip() for x in t.split('|')]
        if len(f) < 3:
            continue
        group, date, hour = f[0], f[1], int(float(f[2]))
        k = (group, date)
        if k not in first or hour < first[k]:
            first[k] = hour

    tot = len(first)
    anchored = 0
    dist = defaultdict(int)
    late_by_group = defaultdict(int)
    tot_by_group = defaultdict(int)
    for (group, date), hour in first.items():
        expected = BASE_HOUR + (1 if date == ramp_date else 0)
        tot_by_group[group] += 1
        dist[hour] += 1
        if hour <= expected:
            anchored += 1
        else:
            late_by_group[group] += 1

    print(f"giorni-gruppo: {tot}")
    print(f"DAY-START ANCHORED RATE: {anchored}/{tot} = {100*anchored/tot:.1f}%"
          f"  (golden ~99.3-100%, pre-405 81.0%)")
    print(f"istogramma prima ora: {dict(sorted(dist.items()))}")
    print("\nper gruppo (tardivi/tot):")
    for g in sorted(tot_by_group):
        print(f"  {g:8} {late_by_group[g]:3}/{tot_by_group[g]}")


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('lessons', help='lessons dump (group|date|hour|subject|teacher|pinned)')
    add_kpi_args(ap)  # --ramp (+ --year/--year-start/--year-end/--data-dir/--lessons-path/--closures-path)
    args = ap.parse_args()
    main(args.lessons, args.ramp)
