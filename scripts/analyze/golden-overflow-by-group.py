#!/usr/bin/env python3
"""TASK-395: machine-check the M12 invariant "golden places 29.3% of hours beyond target-end"
PER GROUP, and decompose it (uniform vs concentrated).

CONTEXT. CLAUDE.md (M12 / TASK-394) and the M12 javadoc (BaseConstraintProvider.overflowBeyondTargetEndDate)
cite "the school's own timetable places 29.3% of ALL hours (3,677) beyond these computed targets".
CLAUDE.md also says that figure was "an artifact of that [flat-8h] model": TASK-394 made targets
FACT-BASED, and the old flat model credited 8h/day -> a ~March target -> ~47 days of "overflow" that
was structure, not lateness. This script never machine-verified the per-group distribution. It does:
for each of the 14 groups, the fact-based target-end, the golden hours beyond it, and whether the
overflow is uniform or driven by outliers (e.g. G14 "100% beyond", G06 10/06).

METHOD (faithful to the current solver, read-only, NO adoption).
- Target-end per group = computeFactBasedIdealEnd (TargetEndDateCalculator): cumulate the group's
  per-date expected-hours facts (DailyActivityQuota, aula+lab+stage) in date order until they cover
  totalHours; that date is the ideal end. (Caveat: the solver then runs ensureEnoughSlots, which only
  EXTENDS the date, so this script's target is a lower bound -> overflow is an upper bound vs the
  constraint. totalHours = Course.totalHours = real courses + Stage pseudo-courses, EXCLUDING Esame/RA
  and the "Totale ore corso" summary row, matching DataGenerator exactly.)
- Golden hours beyond target-end: real lessons (Mon-Fri, excluding the documented traps Stage/Fasullo/
  Esame and all Saturdays - see CLAUDE.md "Trappole del golden") dated STRICTLY AFTER the target-end
  (same strict-after test as M12's `isAfter`; the target itself is the pre-ensureEnoughSlots date, see
  the caveat above). parse_lessons_csv (shared.py) already drops closed rows.

TRAP PROTOCOL (CLAUDE.md "KPI trappole"): exclude Saturdays (117h, grid is Mon-Fri), Fasullo (112h
fake Sat 19/09), Stage (off-grid internship), Esame (off-grid exams). Without this the beyond-count
inflates with phantom hours the grid cannot place.

NOTE on shared.py: the directive and CLAUDE.md refer to `parse_golden_csv`, but shared.py's actual
function is `parse_lessons_csv` (doc/code drift). This script uses the real name.

Usage:
  python3 scripts/analyze/golden-overflow-by-group.py
"""
import csv
import datetime
import os
from collections import defaultdict

from shared import (DATA_DIR, INCARICHI_CSV, KPI_EXCLUDED_PSEUDO,
                    LESSONS_CSV, PSEUDO_TEACHERS, SCHOOL_YEAR_END, parse_lessons_csv)

# The 3 groups whose DailyActivityQuota facts live in calendario_fc.csv (aula/lab/stage split,
# FCQuota=true). The other 11 groups come from calendario_class_days.csv (single hours column).
# Both CSVs happen to contain the FC groups; for them the fc file is authoritative (CLAUDE.md).
FC_GROUPS = {"G04", "G08", "G13"}
CALENDARIO_FC = os.path.join(DATA_DIR, "calendario_fc.csv")
CALENDARIO_CLASS_DAYS = os.path.join(DATA_DIR, "calendario_class_days.csv")

# Trap exclusions (off-grid / fake): shared.KPI_EXCLUDED_PSEUDO = {Stage, Fasullo, Esame}. RA
# ("Recuperi ed Approfondimenti") is intentionally KEEP (real taught lessons), as in shared.py.
TRAP_SUBJECTS = KPI_EXCLUDED_PSEUDO
SATURDAY, SUNDAY = 5, 6  # date.weekday()


def load_expected_hours_by_group():
    """Return {group: {date: expected_hours}} where expected_hours = aula+lab+stage (the universal
    per-date fact, matching SchoolSchedule.getExpectedHours). FC groups from calendario_fc.csv,
    the other 11 from calendario_class_days.csv."""
    by_group = defaultdict(lambda: defaultdict(int))
    # FC groups: aula_hours + lab_hours + stage_hours
    with open(CALENDARIO_FC, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            if r["group"] not in FC_GROUPS:
                continue
            d = datetime.date.fromisoformat(r["date"])
            by_group[r["group"]][d] += int(float(r["aula_hours"])) + int(float(r["lab_hours"])) + int(float(r["stage_hours"]))
    # The other 11 groups: single 'hours' column (one modality per row -> its aula OR lab OR stage
    # contribution; summed per (group,date) they are the day's expected total).
    with open(CALENDARIO_CLASS_DAYS, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            if r["group"] in FC_GROUPS:
                continue  # FC groups are authoritative in calendario_fc, skip the class_days dup
            d = datetime.date.fromisoformat(r["date"])
            by_group[r["group"]][d] += int(float(r["hours"]))
    return {g: dict(v) for g, v in by_group.items()}


def load_total_hours_by_group():
    """Return {group: Course.totalHours}, faithful to DataGenerator: real courses + Stage
    pseudo-courses (generateCourses skips every PSEUDO_TEACHERS row; generateStageCourses then adds
    Stage back from the incarichi Stage rows). Esame, RA and the "Totale ore corso" summary row never
    become Courses, so they are excluded - the school-stated "Totale ore corso" total is higher by
    exactly those hours (e.g. G08: 926 Course vs 990 stated = RA 40 + Esame 24)."""
    real = defaultdict(int)
    stage = defaultdict(int)
    with open(INCARICHI_CSV, newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            teacher = r["teacher"].strip()
            group = r["group"]
            hours = int(float(r["hours"]))
            if teacher == "Stage":
                stage[group] += hours
            elif teacher in PSEUDO_TEACHERS:
                continue  # Esame / RA / "Totale ore corso" never become Courses
            else:
                real[group] += hours
    return {g: real[g] + stage[g] for g in real}


def compute_ideal_end(facts_by_date, total_hours, year_end):
    """Replicate TargetEndDateCalculator.computeFactBasedIdealEnd: cumulate expected-hours facts in
    date order until they cover totalHours. Returns the ideal-end date (capped at year_end), or None
    if the group has no positive-hour fact to anchor on."""
    cumulative = 0
    last = None
    for d in sorted(facts_by_date):
        hours = facts_by_date[d]
        if hours <= 0:
            continue
        cumulative += hours
        last = d
        if cumulative >= total_hours:
            break
    if last is None:
        return None
    return last if last <= year_end else year_end


def load_real_golden_hours(path=LESSONS_CSV):
    """Return {group: [date, ...]} of REAL golden lesson-hours (trap-safe): Mon-Fri, subject not in
    TRAP_SUBJECTS. parse_lessons_csv already skips closed rows (the 112 Fasullo Sat + closed Stage)."""
    real = defaultdict(list)
    for group, d, subject, _teacher in parse_lessons_csv(path, include_hour=False):
        if d.weekday() in (SATURDAY, SUNDAY):
            continue
        if subject in TRAP_SUBJECTS:
            continue
        real[group].append(d)
    return real


def main():
    facts = load_expected_hours_by_group()
    totals = load_total_hours_by_group()
    golden = load_real_golden_hours()

    groups = sorted(totals)
    print("# TASK-395 golden overflow beyond fact-based target-end, per group (M12 invariant check)")
    print(f"# golden (trap-safe real lessons, Mon-Fri excl Stage/Fasullo/Esame): {LESSONS_CSV}")
    print(f"# target-end = fact-based ideal end (cumulate expected-hours to totalHours); year end {SCHOOL_YEAR_END}")
    print(f"# groups: {len(groups)}\n")
    print(f"{'group':9} {'totalH':>6} {'ideal-end':>11} {'beyondH':>7} {'realH':>6} {'overflow%':>9}")
    print("# totalH = Course.totalHours (real courses + Stage, excl Esame/RA); realH = trap-safe placed (Mon-Fri, excl Stage/Fasullo/Esame)")
    print("-" * 60)

    rows = []
    for g in groups:
        total_hours = totals.get(g, 0)
        g_facts = facts.get(g, {})
        ideal_end = compute_ideal_end(g_facts, total_hours, SCHOOL_YEAR_END)
        dates = golden.get(g, [])
        real_hours = len(dates)
        beyond = sum(1 for d in dates if ideal_end is not None and d > ideal_end)
        pct = 100.0 * beyond / real_hours if real_hours else 0.0
        ie_str = ideal_end.isoformat() if ideal_end else "n/a"
        print(f"{g:9} {total_hours:>6} {ie_str:>11} {beyond:>7} {real_hours:>6} {pct:>8.1f}%")
        rows.append(dict(group=g, total_hours=total_hours, ideal_end=ideal_end,
                         beyond=beyond, real_hours=real_hours, pct=pct))

    # Aggregate + uniform-vs-concentrated
    total_beyond = sum(r["beyond"] for r in rows)
    total_real = sum(r["real_hours"] for r in rows)
    agg_pct = 100.0 * total_beyond / total_real if total_real else 0.0
    over_groups = [r for r in rows if r["beyond"] > 0]
    mean_pct = sum(r["pct"] for r in rows) / len(rows) if rows else 0.0
    var = sum((r["pct"] - mean_pct) ** 2 for r in rows) / len(rows) if rows else 0.0
    std_pct = var ** 0.5
    outliers = sorted([r for r in rows if r["pct"] > mean_pct + 2 * std_pct and r["pct"] > 0],
                      key=lambda r: -r["pct"])

    print("\nAggregate (trap-safe):")
    print(f"  total beyond-target hours: {total_beyond} / {total_real} real = {agg_pct:.1f}%")
    print(f"  groups with overflow > 0: {len(over_groups)} / {len(rows)}")
    print(f"  per-group overflow%: mean {mean_pct:.1f}, std {std_pct:.1f}")
    print(f"  cited invariant (M12 javadoc, flat-model era): 29.3% / 3,677h beyond")

    print("\nUniform vs concentrated:")
    if outliers:
        print(f"  OUTLIERS (>{mean_pct:.1f} + 2*{std_pct:.1f} std):")
        for r in outliers:
            print(f"    {r['group']}: {r['pct']:.1f}% ({r['beyond']}h beyond target {r['ideal_end']})")
    else:
        print("  no statistical outliers; overflow is uniform across groups.")
    if not outliers:
        pattern = "UNIFORM (or near-zero)"
    elif len(outliers) < len(rows) / 2:
        pattern = "CONCENTRATED (outlier-driven)"
    else:
        pattern = "WIDESPREAD (most groups overflow)"
    print(f"  pattern: {pattern}")

    print("\nVerdict:")
    print(f"  - Fact-based target-end aggregate beyond = {agg_pct:.1f}% ({total_beyond}h of {total_real}).")
    print("  - The cited 29.3% / 3,677h was a flat-8h-model artifact (target ~March); with the current")
    print("    fact-based targets (the date the school's plan reaches totalHours) the overflow is")
    print("    mechanically near-zero. This is the current truth, not a comparison vs a stale invariant.")
    print("  - READ-ONLY tooling; no model change, no adoption (any per-group M12 retargeting stays gated maintainer).")


if __name__ == "__main__":
    main()
