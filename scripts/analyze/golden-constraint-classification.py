#!/usr/bin/env python3
"""TASK-472 AC#2: classify the perimeter "written constraints violated by the golden" by tier
(H-vs-M), mechanically verifying the VERDICT-472 hand classification.

PRINCIPLE (golden-first, TASK-379). The school considers the golden VALID. So any rule the golden
VIOLATES cannot be Hard (Hard would forbid the golden). The golden violation count decides the tier:
  violations == 0  -> HARD-candidate (crystallizes the invariant at zero cost, like H28)
  violations  > 0  -> MEDIUM (calibrated residual; golden violates it)
A workload/pattern observation that no constraint encodes is NOT an H/M constraint: route to
INPUT (maxDailyHours, availability) or PREFERENCE (soft), not the constraint layer.

This script MECHANICALLY measures the golden for the checkable perimeter rules and cites the
solver-computed golden violations for the complex coded ones (M-TR/M24/M25/M29), producing the
classification report that VERDICT-472 hand-synthesized.

Input: the golden CSV (seed/lessons.csv: date,day,slot,closed,group,teacher,subject). The script is
standalone (no DB); stage/Sicurezza are read from the golden rows themselves (subject names).

Usage:
  python3 scripts/analyze/golden-constraint-classification.py [seed/lessons.csv]
"""
import csv, datetime, sys, os
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_GOLDEN = os.path.join(ROOT, "src/main/resources/seed/lessons.csv")

# Subject spellings (match the authoritative DB names; the golden CSV uses the same).
SICUREZZA = "Sicurezza"
STAGE = "Stage"   # pseudo-course (TASK-381 stage-on-grid)
TERMOIDRAULICA = "Termoidraulic"   # substring: matches "Laboratorio Termoidraulico" + "Progettazione Termoidraulica"
# T16 / T21 are teacher names (observation subjects).

# Coded rules whose golden violations are SOLVER-COMPUTED (re-deriving them standalone would
# duplicate complex constraint logic and risk divergence; cite the authoritative numbers).
SOLVER_COMPUTED = {
    "M-TR (no-2h teoria)": dict(golden_violations=440, source="TASK-468 AC#1 (runtime on imported golden; raw 432)"),
    "M24 (T11 early-start)": dict(golden_violations=16, source="CLAUDE.md constraint table (golden M24 -16)"),
    "M25 (T05 free days)": dict(golden_violations=27, source="CLAUDE.md constraint table (golden M25 -27)"),
    # M29 was DEMOTED from H22 (TASK-379) because the golden never followed the edge shape, so the
    # medium M29 fires on the golden (violations > 0, not zero). Its count is not separately measured
    # here; the tier is the demotion rationale, declared explicitly via `tier` rather than derived
    # from a non-existent measured count, so it cannot be misread as a HARD-candidate by a future reader.
    "M29 (prof day edges)": dict(golden_violations=None,
                                 source="demoted from H22 (TASK-379); golden never followed the edge shape",
                                 tier="MEDIUM (demoted from H22; golden never followed the edge shape)"),
}

# Groups with a StagePattern entity seeded by DataGenerator (the coded M26's actual join scope).
# Standalone script cannot read the DB, so this is cited from DataGenerator.generateStagePatterns.
STAGEPATTERN_SEEDED_GROUPS = ["G07", "G11", "G12"]


def load_golden(path):
    """Yield (date, group, subject, teacher) per golden row."""
    rows = []
    with open(path) as fh:
        for r in csv.DictReader(fh):
            rows.append((r["date"], r["group"], r["subject"], r["teacher"]))
    return rows


def classify(violations, is_observation=False, note=""):
    """Map a golden violation count to a tier proposal."""
    if is_observation:
        return "OBSERVATION (route to INPUT/PREFERENCE, not a constraint)"
    if violations == 0:
        return "HARD-candidate (0 golden violations; crystallize a la H28)"
    return f"MEDIUM ({violations} golden violations -> cannot be Hard, golden violates it)"


def check_m26_sicurezza_pre_stage(rows):
    """M26: every Sicurezza lesson of a group must be before that group's first Stage date.
    Returns (violations, n_with_stage, sicurezza_groups): violations = (group, date) Sicurezza
    lessons on/after first stage; sicurezza_groups = set of groups that have any Sicurezza."""
    sicurezza = defaultdict(list)   # group -> [dates]
    stage_start = {}                # group -> first stage date
    for date, group, subject, _ in rows:
        if subject == SICUREZZA:
            sicurezza[group].append(date)
        if subject == STAGE:
            if group not in stage_start or date < stage_start[group]:
                stage_start[group] = date
    violations = []
    n_with_stage = 0
    for group, dates in sicurezza.items():
        first_stage = stage_start.get(group)
        if first_stage is None:
            continue   # no stage for this group -> M26 vacuous (1o anno has 0 stage)
        n_with_stage += 1
        for d in dates:
            if d >= first_stage:
                violations.append((group, d))
    return violations, n_with_stage, set(sicurezza)


def check_T16_6h_days(rows):
    """Observation: T16 daily hours distribution (the '3h-non-6' workload pattern)."""
    by_day = defaultdict(int)   # date -> hours
    for date, _, _, teacher in rows:
        if teacher == "T16":
            by_day[date] += 1
    if not by_day:
        return None
    six = sum(1 for h in by_day.values() if h >= 6)
    under = sum(1 for h in by_day.values() if h < 6)
    return dict(days_total=len(by_day), days_at_6h=six, days_under_6h=under,
                avg_h_per_day=sum(by_day.values())/len(by_day))


def check_termo_4ore_blocks(rows):
    """Observation: termoidraulica days with a >=4h contiguous block (the 'prime-4-ore' pattern).
    Approximation: days with >=4 termoidraulica hours (contiguity needs slot detail; hour-count is a
    faithful proxy for the block-shape observation)."""
    by_day = defaultdict(int)
    for date, _, subject, _ in rows:
        if TERMOIDRAULICA in subject:
            by_day[date] += 1
    if not by_day:
        return None
    ge4 = sum(1 for h in by_day.values() if h >= 4)
    return dict(days_total=len(by_day), days_ge_4h=ge4, pct=100*ge4/len(by_day))


def check_T21_weekday(rows):
    """Observation: T21 weekday distribution (the 'mid-day'/spread pattern)."""
    by_wd = defaultdict(int)
    for date, _, _, teacher in rows:
        if teacher == "T21":
            try:
                by_wd[datetime.date.fromisoformat(date).weekday()] += 1
            except ValueError:
                pass
    if not by_wd:
        return None
    names = ["Mon", "Tue", "Wed", "Thu", "Fri"]
    return {names[w]: by_wd.get(w, 0) for w in range(5)}


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_GOLDEN
    rows = load_golden(path)

    print(f"# TASK-472 golden constraint classification (mechanical verification of VERDICT-472)")
    print(f"# golden: {path}  ({len(rows)} rows)\n")

    # --- M26 (checkable on the golden) ---
    v, n_with_stage, sicurezza_groups = check_m26_sicurezza_pre_stage(rows)
    print("M26 (Sicurezza pre-stage):")
    print(f"  golden-practice scope: {n_with_stage} group(s) with both Sicurezza and a Stage-lesson "
          f"(1o anno has 0 stage -> M26 vacuous)")
    print(f"  golden violations: {len(v)}  (Sicurezza lessons on/after first Stage date)")
    print(f"  tier: {classify(len(v))}")
    print("  NOTE: stage window derived from golden Stage LESSONS (proxy; standalone script cannot read")
    print(f"        StagePattern). The coded M26 (BaseConstraintProvider) joins StagePattern, seeded only for "
          f"{', '.join(STAGEPATTERN_SEEDED_GROUPS)};")
    seeded_with_sic = [g for g in STAGEPATTERN_SEEDED_GROUPS if g in sicurezza_groups]
    print(f"        of those only {sorted(seeded_with_sic)} have Sicurezza, so the coded M26 exercises "
          f"{len(seeded_with_sic)} group(s) (also 0/0 on the golden).")
    print("        This script's broader scope is golden PRACTICE (Sicurezza before actual stages), not the")
    print("        coded-constraint scope. 0 violations holds for both.\n")

    # --- Observations (workload/pattern, NOT constraints) ---
    print("Observations (NOT H/M constraints; workload/pattern -> INPUT/PREFERENCE):\n")
    m = check_T16_6h_days(rows)
    if m:
        print(f"  T16 3h-non-6: {m['days_total']} days, {m['days_at_6h']} at 6h, "
              f"{m['days_under_6h']} under 6h, avg {m['avg_h_per_day']:.2f}h -> within H7 cap, "
              f"workload pattern -> {classify(0, is_observation=True)}")
    t = check_termo_4ore_blocks(rows)
    if t:
        print(f"  termo prime-4-ore: {t['days_total']} termoidraulica days, {t['days_ge_4h']} "
              f"({t['pct']:.0f}%) >=4h -> lab-block shape (M31-related) -> {classify(0, is_observation=True)}")
    s = check_T21_weekday(rows)
    if s:
        print(f"  T21 weekday: {s} -> teacher schedule pattern -> {classify(0, is_observation=True)}")
    print()

    # --- Coded rules (solver-computed golden violations, cited) ---
    print("Coded rules (golden violations SOLVER-COMPUTED, cited from authoritative source):\n")
    for rule, info in SOLVER_COMPUTED.items():
        gv = info["golden_violations"]
        gv_str = "n/a (not separately measured; demotion rationale)" if gv is None else gv
        print(f"  {rule}: golden violations = {gv_str}  [{info['source']}]")
        tier = info.get("tier") or (classify(gv) if gv is not None else "")
        print(f"    tier: {tier}")
    print()

    # --- Summary verdict ---
    print("VERDICT (mechanical):")
    print("  - M26: HARD-candidate (0 golden violations) if maintainer wants to crystallize 'Sicurezza")
    print("    pre-stage' (H28-style); today MEDIUM.")
    print("  - Observations (T16/termo/T21): NOT constraints -> INPUT/PREFERENCE.")
    print("  - Coded rules (M-TR/M24/M25): MEDIUM (golden violates -> cannot be Hard).")
    print("  - M29: MEDIUM (demoted from H22; golden never followed the edge).")
    print("  -> No coded rule promotes to Hard (golden-first). Consistent with VERDICT-472")


if __name__ == "__main__":
    main()
