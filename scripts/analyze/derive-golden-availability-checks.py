#!/usr/bin/env python3
"""
Re-derive (mechanically, from lessons.csv) the golden-grid slot-usage counts
behind the TASK-374 availability_rules.csv corrections for T10, T12,
and T16, and check them against the rules currently seeded in
availability_rules.csv.

WHY: TASK-374's WHY section and availability-not-yet-modeled.md sections 4-6
cite specific counts (e.g. "T10 has 230 golden lessons, 20 on Thursday,
all at slot 4") as the justification for narrowing/adding VACATION rules.
Those counts were computed once, by hand, during that session. This script
makes them re-runnable and re-verifiable any time lessons.csv or
availability_rules.csv changes, instead of trusting a one-off, unversioned
calculation.

Scope: the three teachers TASK-374 changed (T10, T12, T16), plus
(TASK-368) the three teachers revised by the 2026-07-17 "vincoli-in-parole"
docx (T04, T05, T11) and their two new M24/M25 weekly choice-day
constraints (independent Python cross-check of the Java constraint streams
in SchoolScheduleConstraintProvider - see availability-not-yet-modeled.md
for the numbers this script reproduces), plus (TASK-369) the same docx's
three course-timing rules: T03's "Sicurezza before stage" (M26), T20's
subject start-period preference (M27), and the general "possibilmente >=1h/
week per subject" weekly-continuity reward (S12).

Usage:
    python3 scripts/analyze/derive-golden-availability-checks.py
"""
import csv
import os
import sys
from collections import defaultdict
from datetime import date

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

sys.path.insert(0, SCRIPT_DIR)
from shared import (  # noqa: E402
    LESSONS_CSV, AVAILABILITY_RULES_CSV, SLOT_START_TIMES, WEEKDAY_ORDER,
    ITALIAN_DAY_TO_ENUM, SUBJECT_CASE_NORMALIZATION, SCHOOL_YEAR_END,
    PSEUDO_TEACHERS, iter_open_lessons_rows,
)

# Mirrors com.school.planner.solver.TimeslotSequence.SCHOOL_YEAR_START - see
# SCHOOL_YEAR_END's own import above for why this is hand-pinned per-file
# rather than added to shared.py (only this script needs the start date so far).
SCHOOL_YEAR_START = date(2026, 9, 14)


def slot_index(slot_field: str) -> int:
    start = slot_field.split(" ")[0]
    return SLOT_START_TIMES.index(start)


def load_teacher_dated_lessons(teacher: str):
    """List of (date, slotIndex) for every open (non-closed) golden lesson
    taught by `teacher` - the dated counterpart of load_teacher_lessons()
    below, needed for ISO-week grouping (TASK-368's M24/M25 cross-checks)."""
    return [(date.fromisoformat(row['date'].strip()), slot_index(row['slot'].strip()))
            for row in iter_open_lessons_rows(LESSONS_CSV) if row['teacher'].strip() == teacher]


def group_dates_by_iso_week(dates):
    """{(isoyear, isoweek): {distinct dates in that week}} for a list of dates -
    shared by the M24 and M25 cross-checks below, which otherwise repeated
    the exact same isocalendar()-keyed grouping loop for T11 and T05."""
    by_week = defaultdict(set)
    for d in dates:
        iso = d.isocalendar()
        by_week[(iso[0], iso[1])].add(d)
    return by_week


def load_teacher_lessons(teacher: str):
    """List of (dayOfWeek, slotIndex) for every open (non-closed) golden
    lesson taught by `teacher`."""
    rows = []
    for row in iter_open_lessons_rows(LESSONS_CSV):
        if row['teacher'].strip() != teacher:
            continue
        day = ITALIAN_DAY_TO_ENUM[row['day'].strip()]
        rows.append((day, slot_index(row['slot'].strip())))
    return rows


def load_seeded_rule_ranges(teacher: str, day: str):
    """List of (minSlot, maxSlot) VACATION ranges currently seeded for
    (teacher, day) in availability_rules.csv - recurring rows only (blank
    effectiveFrom/To), matching what TASK-374 added/changed."""
    ranges = []
    with open(AVAILABILITY_RULES_CSV, newline='', encoding='utf-8') as f:
        reader = csv.reader(f)
        next(reader)  # header
        for row in reader:
            r_teacher, r_day, r_min, r_max, r_type, r_from, r_to, _note = row
            if r_teacher.strip() != teacher or r_day.strip() != day:
                continue
            if r_type.strip() != 'VACATION':
                continue
            if r_from.strip() or r_to.strip():
                continue  # dated row, not the recurring golden-derived rule
            ranges.append((int(r_min), int(r_max)))
    return ranges


def slot_counts(lessons, day, min_slot, max_slot):
    """(hits, per_slot) for every lesson on `day` within [min_slot, max_slot] -
    hits is the flat list of matching slot indices, per_slot is a
    {slot: count} dict covering every slot in the range (0 if unused)."""
    hits = [s for (d, s) in lessons if d == day and min_slot <= s <= max_slot]
    per_slot = {s: hits.count(s) for s in range(min_slot, max_slot + 1)}
    return hits, per_slot


def print_slot_breakdown(per_slot):
    print("    per-slot breakdown: " + ", ".join(f"slot{s}={c}" for s, c in per_slot.items()))


def report_teacher_day(teacher, lessons, day, min_slot, max_slot, total):
    hits, per_slot = slot_counts(lessons, day, min_slot, max_slot)
    day_total = sum(1 for (d, _s) in lessons if d == day)
    pct = (100.0 * len(hits) / total) if total else 0.0
    verdict = "PASS (0 violations)" if len(hits) == 0 else f"FAIL ({len(hits)} violations, {pct:.2f}%)"
    print(f"  {teacher} {day} slots {min_slot}-{max_slot}: "
          f"{len(hits)}/{total} golden lessons ({pct:.2f}% of all {teacher} lessons; "
          f"{day_total} lessons total on {day}) -> {verdict}")
    print_slot_breakdown(per_slot)
    return len(hits), pct


def main():
    print("=" * 100)
    print("T10 - Thursday slot distribution")
    print("=" * 100)
    t10 = load_teacher_lessons("T10")
    total_t10 = len(t10)
    print(f"  T10 total golden lessons: {total_t10}")
    thursday_hits, thursday_per_slot = slot_counts(t10, "THURSDAY", 0, len(SLOT_START_TIMES) - 1)
    print(f"  T10 Thursday golden lessons: {len(thursday_hits)}")
    print_slot_breakdown(thursday_per_slot)
    seeded = load_seeded_rule_ranges("T10", "THURSDAY")
    print(f"  Currently seeded T10/THURSDAY recurring VACATION ranges: {seeded}")
    for (min_slot, max_slot) in seeded:
        report_teacher_day("T10", t10, "THURSDAY", min_slot, max_slot, total_t10)

    print()
    print("=" * 100)
    print("T12 - Monday/Tuesday slots 0-1 usage")
    print("=" * 100)
    t12 = load_teacher_lessons("T12")
    total_t12 = len(t12)
    print(f"  T12 total golden lessons: {total_t12}")
    for day in ("MONDAY", "TUESDAY"):
        seeded = load_seeded_rule_ranges("T12", day)
        print(f"  Currently seeded T12/{day} recurring VACATION ranges: {seeded}")
        for (min_slot, max_slot) in seeded:
            report_teacher_day("T12", t12, day, min_slot, max_slot, total_t12)

    print()
    print("=" * 100)
    print("T16 - per-day slots 0-2 usage (all weekdays, not just the seeded one)")
    print("=" * 100)
    t16 = load_teacher_lessons("T16")
    total_t16 = len(t16)
    print(f"  T16 total golden lessons: {total_t16}")
    print("  (measured on all weekdays for comparison, not just the seeded Tuesday rule)")
    for day in WEEKDAY_ORDER:
        report_teacher_day("T16", t16, day, 0, 2, total_t16)
    seeded = load_seeded_rule_ranges("T16", "TUESDAY")
    print(f"  Currently seeded T16/TUESDAY recurring VACATION ranges: {seeded}")

    print()
    print("=" * 100)
    print("Summary vs. the >2% weakening threshold (TASK-374 WHAT #2)")
    print("=" * 100)
    print("  A seeded rule PASSes if it causes 0 golden violations. A rule that was")
    print("  deliberately NOT seeded (e.g. T16/Wednesday) is expected to show a")
    print("  nonzero, >2% count above - that is what justified dropping it, not a bug.")

    print()
    print("=" * 100)
    print("TASK-368: T04/T05 static-rule revisions vs. the golden")
    print("=" * 100)
    mon_thu = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY"}

    t04 = load_teacher_lessons("T04")
    total_bent = len(t04)
    print(f"  T04 total golden lessons: {total_bent}")
    slot5_hits = [x for x in t04 if x[0] in mon_thu and x[1] == 5]
    print(f"  T04 Mon-Thu slot5 (13:00-14:00, newly UNBLOCKED by TASK-368): "
          f"{len(slot5_hits)}/{total_bent} -> loosening only, cannot create a new violation")
    slot67_hits = [x for x in t04 if x[0] in mon_thu and x[1] in (6, 7)]
    pct = 100.0 * len(slot67_hits) / total_bent if total_bent else 0.0
    print(f"  T04 Mon-Thu slot6-7 (still blocked, unchanged): "
          f"{len(slot67_hits)}/{total_bent} ({pct:.2f}%) -> {'PASS' if len(slot67_hits) == 0 else 'FAIL'}")

    t05 = load_teacher_lessons("T05")
    total_t05 = len(t05)
    print(f"  T05 total golden lessons: {total_t05}")
    tue67_hits = [x for x in t05 if x[0] == "TUESDAY" and x[1] in (6, 7)]
    pct = 100.0 * len(tue67_hits) / total_t05 if total_t05 else 0.0
    print(f"  T05 Tuesday slot6-7 (14:30-16:30, newly BLOCKED by TASK-368, drops the old "
          f"Tue-afternoon-available asymmetry): {len(tue67_hits)}/{total_t05} ({pct:.2f}%) -> "
          f"{'PASS' if len(tue67_hits) == 0 else 'FAIL (weaken if >2%)'}")
    mwt67_hits = [x for x in t05 if x[0] in ("MONDAY", "WEDNESDAY", "THURSDAY") and x[1] in (6, 7)]
    print(f"  T05 Mon/Wed/Thu slot6-7 (already blocked, unchanged): {len(mwt67_hits)}/{total_t05}")

    print()
    print("=" * 100)
    print("TASK-368: M24 (T11, at most 1 early-start day/week) - Python cross-check")
    print("of the Java constraint stream in SchoolScheduleConstraintProvider")
    print("=" * 100)
    t11 = load_teacher_lessons("T11")
    total_t11 = len(t11)
    print(f"  T11 total golden lessons: {total_t11}")
    T11_dated = load_teacher_dated_lessons('T11')
    slot0_dates = [d for d, s in T11_dated if s == 0]
    print(f"  T11 slot-0 (08:00) golden lessons: {len(slot0_dates)} "
          f"(NOTE: this contradicts the TASK-368 task brief's claim of 'ZERO slot-0 days' - "
          f"see availability-not-yet-modeled.md for the correction)")
    by_week = group_dates_by_iso_week(slot0_dates)
    m24_matches = 0
    m24_penalty = 0
    for week, days in sorted(by_week.items()):
        if len(days) > 1:
            m24_matches += 1
            m24_penalty += len(days) - 1
    print(f"  M24 cross-check (cap=1/week): {m24_matches} weeks exceed the cap, "
          f"total shortfall {m24_penalty} (must match the Java GoldenSolutionRegressionTest "
          f"M24 medium score/match count exactly)")

    print()
    print("=" * 100)
    print("TASK-368: M25 (T05, required free days/week) - Python cross-check")
    print("of the Java constraint stream in SchoolScheduleConstraintProvider")
    print("=" * 100)
    T05_dated = [d for d, _s in load_teacher_dated_lessons('T05')]
    by_week_t05 = group_dates_by_iso_week(T05_dated)
    for required in (1, 2):
        m25_matches = 0
        m25_penalty = 0
        for week, days in sorted(by_week_t05.items()):
            free = 5 - len(days)
            if free < required:
                m25_matches += 1
                m25_penalty += required - free
        print(f"  M25 cross-check (requiredFreeDaysPerWeek={required}): {m25_matches} weeks fall short, "
              f"total shortfall {m25_penalty}"
              + (" <- Teacher.requiredFreeDaysPerWeek's actual seeded value" if required == 2 else
                 " (the docx's literal value - shown for comparison, see Teacher.requiredFreeDaysPerWeek"
                 " javadoc for why 2 is seeded instead)"))

    task_369_course_timing()


def normalize_subject(subject):
    """Mirrors com.school.planner.util.SubjectNormalization.normalize()."""
    return SUBJECT_CASE_NORMALIZATION.get(subject, subject)


def task_369_course_timing():
    """TASK-369 cross-check: M26 (T03, Sicurezza before stage), M27
    (T20, subject start-period preference), S12 (weekly subject presence).
    Independent Python re-derivation of the three Java constraint streams in
    BaseConstraintProvider/SchoolScheduleConstraintProvider - same role as
    the M24/M25 cross-check above, for GoldenSolutionRegressionTest's M26/
    M27/S12 pins."""
    print()
    print("=" * 100)
    print("TASK-369: M26 (T03, Sicurezza before stage) - Python cross-check")
    print("=" * 100)
    # Mirrors the 3 hardcoded StagePattern rows GoldenSolutionRegressionTest
    # seeds (DataGenerator.generateStagePatterns(), private) - only G07, 3
    # FB, G12 have a seeded StagePattern in the 2026/27 fixture.
    stage_starts = {"G07": date(2027, 3, 23), "G11": date(2027, 1, 13), "G12": date(2026, 11, 20)}
    m26_matches = 0
    for row in iter_open_lessons_rows(LESSONS_CSV):
        if normalize_subject(row['subject'].strip()) != 'Sicurezza':
            continue
        group = row['group'].strip()
        if group not in stage_starts:
            continue
        d = date.fromisoformat(row['date'].strip())
        if d >= stage_starts[group]:  # strictly-before passes; on-or-after violates (mirrors !isBefore in Java)
            m26_matches += 1
            print(f"  VIOLATION: {group} Sicurezza on {d} (stage starts {stage_starts[group]})")
    print(f"  M26 cross-check: {m26_matches} Sicurezza lessons dated on/after their group's stage start "
          f"(must match the Java GoldenSolutionRegressionTest M26 medium score/match count exactly)")

    print()
    print("=" * 100)
    print("TASK-369: M27 (T20, subject start-period preference) - Python cross-check")
    print("=" * 100)
    early_subjects = {'Merceologia', 'Arredi Tessili'}
    late_subjects = {'Tecnologia tessile', 'Storia della moda'}
    early_max_week = 3   # T20_EARLY_MAX_START_WEEK
    late_min_week = 4    # T20_LATE_MIN_START_WEEK
    first_lesson = {}  # (subject, group) -> min date
    for row in iter_open_lessons_rows(LESSONS_CSV):
        subject = normalize_subject(row['subject'].strip())
        if subject not in early_subjects and subject not in late_subjects:
            continue
        group = row['group'].strip()
        d = date.fromisoformat(row['date'].strip())
        key = (subject, group)
        if key not in first_lesson or d < first_lesson[key]:
            first_lesson[key] = d
    m27_matches = 0
    for (subject, group), d in sorted(first_lesson.items(), key=lambda item: (item[0][0], item[1])):
        week = (d - SCHOOL_YEAR_START).days // 7
        failed = week > early_max_week if subject in early_subjects else week < late_min_week
        m27_matches += failed
        verdict = ("FAIL (too late)" if subject in early_subjects else "FAIL (too early)") if failed else "PASS"
        print(f"  {group}/{subject}: first lesson {d} (week {week}) -> {verdict}")
    print(f"  M27 cross-check: {m27_matches} courses whose first lesson falls outside its subject's window "
          f"(T20_EARLY_MAX_START_WEEK={early_max_week}, T20_LATE_MIN_START_WEEK={late_min_week}; "
          f"must match the Java GoldenSolutionRegressionTest M27 medium score/match count exactly)")

    print()
    print("=" * 100)
    print("TASK-369: S12 (weekly subject presence) - Python cross-check")
    print("=" * 100)
    combos = set()
    horizon_excluded = 0
    for row in iter_open_lessons_rows(LESSONS_CSV):
        if row['teacher'].strip() in PSEUDO_TEACHERS:  # pseudo-teacher exclusion, mirrors Teacher.pseudo
            continue
        d = date.fromisoformat(row['date'].strip())
        if d < SCHOOL_YEAR_START or d > SCHOOL_YEAR_END:
            # No Timeslot exists outside the horizon - these rows never become a
            # pinned Lesson (report.unmatchedNoTimeslot), so the Java constraint
            # stream never sees them either.
            horizon_excluded += 1
            continue
        group = row['group'].strip()
        subject = normalize_subject(row['subject'].strip())
        iso = d.isocalendar()
        combos.add((group, subject, iso[0] * 100 + iso[1]))
    print(f"  {horizon_excluded} rows excluded as beyond the Timeslot horizon "
          f"({SCHOOL_YEAR_START}..{SCHOOL_YEAR_END})")
    print(f"  S12 cross-check: {len(combos)} distinct (group, subject, ISO week) combos with >=1 lesson "
          f"(must match the Java GoldenSolutionRegressionTest S12 soft score/match count exactly)")


if __name__ == '__main__':
    main()
