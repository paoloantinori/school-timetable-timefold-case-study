#!/usr/bin/env python3
"""
Quantify the weekly-template structure of the golden 2026/27 timetable, per
(group, segment), for the TASK-351 template-first pre-assignment research.

WHY: analysis.md section 4.4 reports that 67% of all golden lesson-hours occupy
a (group, weekday, slot, teacher, subject) cell that repeats >=10 weeks across
the WHOLE year. That whole-year figure is diluted by the stage/ramp-up/run-out
periods where the weekly grid legitimately changes. The human planner clearly
builds a weekly template and then patches those periods. This script measures
recurrence WITHIN homogeneous segments (before/during/after stage, plus the
first-2-weeks and last-2-weeks edges) where a single template is expected to
hold - the number that decides whether a template-first solve is worth building.

Two families of numbers per group:
  1. RECURRENCE: within each (group, segment), the % of lesson-hours whose
     (weekday, slot, teacher, subject) cell recurs in >=80% of that segment's
     teaching weeks. High => a single weekly template covers the segment.
  2. TEMPLATE CHANGES: across the group's whole year, the number of DISTINCT
     weekly templates = 1 + (consecutive week pairs where >30% of cells differ,
     Jaccard distance over the week's cell set). Low => few materially different
     grids to solve.

Segments are derived from the golden data itself: stage windows come from the
Stage pseudo-teacher rows in lessons.csv (authoritative per analysis.md, and
complete for all 8 stage groups - vincoli_classi.csv only dates 3 of them).

Usage:
    python3 scripts/analyze/derive-golden-template-recurrence.py
"""
import datetime as dt
import os
import sys
from collections import defaultdict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from shared import (  # noqa: E402
    ITALIAN_DAY_TO_ENUM, OFF_CAMPUS_TEACHERS, PSEUDO_TEACHERS,
    iter_open_lessons_rows,
)

STAGE_TEACHER = "Stage"

# Pseudo/off-campus "teachers": stage, exams, company visits and summary rows.
# They are scheduled in the golden grid but the template-first pipeline PINS
# them from the calendar import - they are not the hours the solver places.
# Recurrence is therefore measured over REAL lessons only. Composed (lower-cased,
# stripped) from shared.py's two source-of-truth sets rather than re-listed here.
NON_SOLVED_TEACHERS = {

# NOTE (review 2026-07-18): rows whose SUBJECT is 'RA' carry REAL teacher names in
# lessons.csv (0 rows have teacher=='RA' — verified), so the teacher-based filter below
# does NOT drop G08's RA hours; they are counted as normal schedule structure.
# The golden grid holds one cell per (group, slot): no doppio rows exist (school answer
# Q1), so the dict-overwrite in the modal-template build cannot lose co-teachers on this
# dataset; a count-and-warn guard would be needed before reusing this script on data
# that CAN contain doppio.
# distinct_templates() compares weeks adjacent IN THE DATA (stage/closure gaps collapse
# into one change-point). This UNDERCOUNTS template churn — a bias in FAVOR of the
# template-first option, which still lost: the NO-GO conclusion is conservative.
    t.strip().lower() for t in (PSEUDO_TEACHERS | OFF_CAMPUS_TEACHERS)
}

# A lesson-hour's "template cell": if the same tuple lands in the same weekday
# and slot week after week, it is one recurring template cell. Teacher is part
# of the identity because rotation courses swap the teacher while keeping the
# subject/slot fixed - those are materially different templates.
CELL_FIELDS = ("weekday", "slot", "teacher", "subject")

RECUR_THRESHOLD = 0.80   # cell must appear in >=80% of a segment's weeks
CHANGE_THRESHOLD = 0.30  # >30% of cells differ from prev week => template change


def iso_week(date_str):
    """(iso_year, iso_week) key for a YYYY-MM-DD string - the unit we treat as
    one 'week' of the timetable."""
    y, m, d = (int(x) for x in date_str.split("-"))
    iso = dt.date(y, m, d).isocalendar()
    return (iso[0], iso[1])


def load_rows():
    """Real, non-closed lesson rows as dicts with a normalized weekday and an
    iso_week key. Pseudo/off-campus teachers (Stage, Esame, RA, azienda) are
    dropped from the recurrence measure - they are pinned from the calendar
    import, not placed by the solver. Stage dates are captured separately (see
    load_stage_windows) purely to draw the segment boundaries."""
    rows = []
    for r in iter_open_lessons_rows():
        teacher = r["teacher"].strip()
        if teacher.lower() in NON_SOLVED_TEACHERS:
            continue
        weekday = ITALIAN_DAY_TO_ENUM.get(r["day"].strip())
        if weekday is None:
            continue
        rows.append({
            "group": r["group"].strip(),
            "date": r["date"].strip(),
            "week": iso_week(r["date"].strip()),
            "weekday": weekday,
            "slot": r["slot"].strip(),
            "teacher": teacher,
            "subject": r["subject"].strip(),
        })
    return rows


def load_stage_windows():
    """{group: (min_date, max_date)} over Stage rows (dropped from load_rows by
    the NON_SOLVED_TEACHERS filter). Defines the segment boundaries."""
    dates = defaultdict(list)
    for r in iter_open_lessons_rows():
        if r["teacher"].strip() == STAGE_TEACHER:
            dates[r["group"].strip()].append(r["date"].strip())
    return {g: (min(ds), max(ds)) for g, ds in dates.items()}


def segment_of_week(week, ordered_weeks, stage_weeks):
    """Label a week for a group. Edge segments win over stage so the ramp-up
    and run-out weeks are analyzed on their own; the rest split around stage."""
    first2 = set(ordered_weeks[:2])
    last2 = set(ordered_weeks[-2:])
    if week in first2:
        return "first-2-weeks"
    if week in last2:
        return "last-2-weeks"
    if stage_weeks is not None:
        w_start, w_end = stage_weeks
        if week < w_start:
            return "before-stage"
        if week > w_end:
            return "after-stage"
        return "during-stage"
    return "middle"


def cell(row):
    return tuple(row[f] for f in CELL_FIELDS)


def open_weeks_by_weekday(seg_rows):
    """{weekday: set(weeks where the group has >=1 real lesson on that weekday)}.
    This is the closure-robust denominator for recurrence: a Monday cell is only
    scored against the weeks whose Monday actually hosted teaching (a bridge-day
    or stage-week Monday with no real lesson is not held against it)."""
    ow = defaultdict(set)
    for r in seg_rows:
        ow[r["weekday"]].add(r["week"])
    return ow


def recurrence_for_segment(seg_rows):
    """(pct_recurring_hours, n_hours, n_weeks) for one (group, segment).
    A lesson-hour recurs if its cell appears in >=80% of the weeks in which that
    cell's WEEKDAY was open for teaching (not >=80% of all segment weeks)."""
    n_weeks = len({r["week"] for r in seg_rows})
    ow = open_weeks_by_weekday(seg_rows)
    cell_weeks = defaultdict(set)
    for r in seg_rows:
        cell_weeks[cell(r)].add(r["week"])
    recurring = 0
    for r in seg_rows:
        denom = len(ow[r["weekday"]])
        if denom and len(cell_weeks[cell(r)]) / denom >= RECUR_THRESHOLD:
            recurring += 1
    pct = 100.0 * recurring / len(seg_rows) if seg_rows else 0.0
    return pct, len(seg_rows), n_weeks


def modal_coverage_for_segment(seg_rows):
    """pct_covered_hours for one (group, segment) - the template-first
    CEILING. Build the modal weekly grid: at each (weekday, slot) position, the
    most common occupant across the segment's weeks, where an unfilled position
    votes EMPTY (a position that is usually empty templates to EMPTY, so its rare
    lessons count as exceptions). Coverage = the fraction of real lesson-hours
    that already match this stamped grid; the rest is what date-level repair must
    fix. Unlike the strict >=80% recurrence, a cell stable in 25/34 weeks still
    wins its position and is counted as covered - so this is the fair ceiling."""
    ow = open_weeks_by_weekday(seg_rows)
    # position -> {week -> (teacher, subject)}   (<=1 occupant per week: grid is
    # structurally one lesson per group-slot-date)
    filled = defaultdict(dict)
    for r in seg_rows:
        filled[(r["weekday"], r["slot"])][r["week"]] = (r["teacher"], r["subject"])
    template = {}
    for pos, week_occ in filled.items():
        weekday = pos[0]
        votes = defaultdict(int)
        for occ in week_occ.values():
            votes[occ] += 1
        empty_votes = len(ow[weekday]) - len(week_occ)
        if empty_votes > 0:
            votes[None] += empty_votes  # None = EMPTY
        template[pos] = max(votes.items(), key=lambda kv: kv[1])[0]
    covered = 0
    for r in seg_rows:
        if template[(r["weekday"], r["slot"])] == (r["teacher"], r["subject"]):
            covered += 1
    return 100.0 * covered / len(seg_rows) if seg_rows else 0.0


def distinct_templates(group_rows, ordered_weeks):
    """Number of materially different weekly grids across the year: 1 change per
    consecutive week pair whose grids differ by >30% (Jaccard distance). The
    comparison is restricted to the weekdays OPEN in both weeks, so a closure
    that removes a whole day is not mistaken for a template redesign."""
    # week -> {weekday -> set(cells)}
    week_day_cells = defaultdict(lambda: defaultdict(set))
    for r in group_rows:
        week_day_cells[r["week"]][r["weekday"]].add(cell(r))
    changes = 0
    for prev, cur in zip(ordered_weeks, ordered_weeks[1:]):
        shared_days = set(week_day_cells[prev]) & set(week_day_cells[cur])
        if not shared_days:
            continue
        a = {c for d in shared_days for c in week_day_cells[prev][d]}
        b = {c for d in shared_days for c in week_day_cells[cur][d]}
        union = a | b
        if not union:
            continue
        if len(a ^ b) / len(union) > CHANGE_THRESHOLD:
            changes += 1
    return changes + 1  # N change-points partition the year into N+1 templates


SEGMENT_ORDER = ["first-2-weeks", "before-stage", "during-stage",
                 "after-stage", "middle", "last-2-weeks"]


def main():
    rows = load_rows()
    stage_windows = load_stage_windows()
    by_group = defaultdict(list)
    for r in rows:
        by_group[r["group"]].append(r)

    print("=" * 92)
    print("Golden weekly-template recurrence by (group, segment)  -  TASK-351")
    print("=" * 92)
    print("recurring % = hours whose (weekday,slot,teacher,subject) cell appears "
          "in >=80% of that weekday's open weeks (strict)")
    print("modal %     = hours matching the stamped modal weekly grid = the "
          "template-first CEILING (fair; long segments not penalized)")
    print()
    header = (f"{'group':7} {'segment':14} {'weeks':>5} {'hours':>6} "
              f"{'recurring %':>11} {'modal %':>9}")
    print(header)
    print("-" * len(header))

    # Weighted totals for an overall figure comparable to analysis.md's 67%.
    seg_hours_total = 0
    seg_recurring_total = 0.0
    seg_modal_total = 0.0
    per_group_templates = {}

    for group in sorted(by_group):
        grows = by_group[group]
        ordered_weeks = sorted({r["week"] for r in grows})
        sw = stage_windows.get(group)
        stage_weeks = (iso_week(sw[0]), iso_week(sw[1])) if sw else None

        # bucket this group's rows by segment
        seg_rows = defaultdict(list)
        for r in grows:
            seg = segment_of_week(r["week"], ordered_weeks, stage_weeks)
            seg_rows[seg].append(r)

        per_group_templates[group] = distinct_templates(grows, ordered_weeks)

        for seg in SEGMENT_ORDER:
            if seg not in seg_rows:
                continue
            pct, n_hours, n_weeks = recurrence_for_segment(seg_rows[seg])
            modal_pct = modal_coverage_for_segment(seg_rows[seg])
            flag = " (<2 wk: trivial)" if n_weeks < 2 else ""
            print(f"{group:7} {seg:14} {n_weeks:5} {n_hours:6} "
                  f"{pct:10.1f}% {modal_pct:8.1f}%{flag}")
            # Exclude <2-week segments from the weighted mean: a 1-week segment
            # recurs at 100% by definition and would inflate the total.
            if n_weeks >= 2:
                seg_hours_total += n_hours
                seg_recurring_total += pct / 100.0 * n_hours
                seg_modal_total += modal_pct / 100.0 * n_hours
        print("-" * len(header))

    overall = (100.0 * seg_recurring_total / seg_hours_total
               if seg_hours_total else 0.0)
    overall_modal = (100.0 * seg_modal_total / seg_hours_total
                     if seg_hours_total else 0.0)
    print()
    print(f"WEIGHTED within-segment, segments >=2 weeks, over {seg_hours_total} "
          f"real lesson-hours:")
    print(f"  strict recurrence (>=80% of weeks): {overall:.1f}%")
    print(f"  modal-grid coverage (template ceiling): {overall_modal:.1f}%")
    print("(analysis.md 4.4 reports 67% measured as 'cell repeats in >=10 weeks "
          "somewhere in the year' - a looser, absolute-count definition than "
          "either column here, and it includes the trivially-recurring stage grid)")
    print()
    print("Distinct weekly templates per group across the year")
    print("(1 + count of consecutive-week pairs differing by >30% of cells):")
    hdr2 = f"  {'group':7} {'#templates':>10}"
    print(hdr2)
    print("  " + "-" * (len(hdr2) - 2))
    for group in sorted(per_group_templates):
        print(f"  {group:7} {per_group_templates[group]:10}")
    vals = list(per_group_templates.values())
    print(f"  {'mean':7} {sum(vals) / len(vals):10.1f}")


if __name__ == "__main__":
    main()
