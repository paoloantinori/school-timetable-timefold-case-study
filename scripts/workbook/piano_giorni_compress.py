#!/usr/bin/env python3
"""Compress the per-date day-plan into weekly patterns per period (TASK-445 Tappa 4).

The seed carries the school's day-plan as two flat per-date tables:
``calendario_class_days.csv`` (2.413 rows, one modality A/L/S per date, 11 groups)
and ``calendario_fc.csv`` (909 rows, the aula/lab/stage split, 3 FC groups). The
proposal (section 3.13-3.15) reorganizes them into:

- ``13_PianoGiorniPattern`` - one row per (class, period, weekday, aula, lab, stage):
  "in this period, on this weekday, the day-plan is this". The period is a date
  range [validoDa, validoA]; the plan applies to every matching weekday in it.
- ``14_PianoGiorniEccezioni`` - the dated breaks of the pattern, each with a
  mandatory motivo. A (0, 0, 0) row with motivo is the explicit "non in sessione"
  declaration that today replaces the loader's ambiguous "no row = no session".
- ``15_PianoGiorniGenerato`` - the system-generated per-date expansion, one row per
  in-grid school day, with origine (pattern/eccezione) and the rule that produced it.

Correctness model (the gate :mod:`test_piano_giorni_roundtrip`): expanding 13 + 14
over the in-grid school days must reproduce the seeded ``DailyActivityQuota`` table
byte-identically on (class, date, aula, lab, stage). Pattern extraction has the same
blind spot the availability gate has (an equivalence of the expansion cannot see a
pattern that lies but happens to be patched by an exception), so the gate asserts
honesty PER PATTERN and fails loud on residuals, not just on the expansion equality.

Honesty contract (the part maintainer made his own for this tappa):
  - A pattern period is built from a maximal run of the SAME plan on a weekday,
    merging across closure gaps and no-session gaps. The period ends ONLY where the
    plan (aula, lab, stage) actually changes. Segmentation is never widened to make
    the counts fit: a real, sustained plan change is its own period, never absorbed
    as a string of exceptions.
  - A single-date deviation inside an otherwise uniform period is an exception, not
    a period (a "pattern" implies recurrence: a period covers >= 2 dates). Two or
    more adjacent deviations are a real period, not folded into exceptions.
  - Every in-grid school day is explained: by a pattern, or by an exception. A quota
    date the expansion cannot explain is a residual and fails the gate loud.
  - Off-grid hours (Saturdays and closure dates, all stage: no Timeslot exists) are
    NOT part of 13/14/15 because Saturday cannot be a pattern weekday
    (giorniSettimana = LUN-VEN). They are declared separately as oreFuoriGriglia,
    never silently dropped.

See ``claudedocs/proposta-workbook-input-2026-07-28.md`` sections 3.13-3.15, V16/V17.
"""

from __future__ import annotations

import datetime
from collections import defaultdict
from functools import lru_cache

import sources as S

ORIGINE_SISTEMA = S.ORIGINE_SISTEMA

# Weekday codes reused from sources.CALENDAR_WEEKDAYS (the same vocabulary
# 11_ClassiCalendario and 01_Anno use), exactly as the sibling availability_compress
# reuses it as WEEKDAYS_IT = S.CALENDAR_WEEKDAYS. date.weekday() 0..4 -> Italian code.
WEEKDAY_CODES = S.CALENDAR_WEEKDAYS

# First-day ramp (04_GiorniSpeciali): 2026-09-14 every open-Monday group is in
# slots 1-4. It shows up here as a single-date plan deviation (e.g. (4,0,0) instead
# of the regular (6,0,0)); its motivo is inferred from this date. By design the seed
# has a single special day; if a second ever appears, make this a {date: motivo}
# table sourced from 04_GiorniSpeciali instead of a date-equality check.
FIRST_DAY = datetime.date.fromisoformat(S.GIORNI_SPECIALI_DATE)

# Stage windows (16_Stage), to infer "Stage" as the motivo for no-session days that
# fall inside a group's stage: the group is at the company, not in school. Built
# eagerly at import from S.STAGE_PATTERNS, the same way availability_compress derives
# MATTINO/POMERIGGIO from S.CAMPANELLA_SLOTS.
_STAGE_WINDOWS = [
    (classe, datetime.date.fromisoformat(inizio), datetime.date.fromisoformat(fine),
     set(giorni.split(";")))
    for classe, inizio, fine, giorni, _rientro, _tot in S.STAGE_PATTERNS
]


def _in_stage(group: str, d: datetime.date) -> bool:
    for classe, lo, hi, giorni in _STAGE_WINDOWS:
        if classe == group and lo <= d <= hi and WEEKDAY_CODES[d.weekday()] in giorni:
            return True
    return False


# --- the ground-truth table, split in-grid vs off-grid ----------------------

def _quota_by_group() -> tuple[dict, list]:
    """Return (in_grid, off_grid).

    ``in_grid``: {group: {date: (aula, lab, stage)}} for quota rows on in-grid
    school days (Mon-Fri, non-closure) - the universe 13/14/15 describe.
    ``off_grid``: list of {group, date, aula, lab, stage} for quota rows on
    Saturdays or closures (no Timeslot) - declared as oreFuoriGriglia, not in 13/14.
    """
    school_days = set(S.school_year_dates())
    in_grid: dict = defaultdict(dict)
    off_grid: list = []
    for r in S.daily_activity_quota():
        rec = (r["group"], r["date"], r["aula"], r["lab"], r["stage"])
        if r["date"] in school_days:
            in_grid[r["group"]][r["date"]] = (r["aula"], r["lab"], r["stage"])
        else:
            off_grid.append({"group": r["group"], "date": r["date"],
                             "aula": r["aula"], "lab": r["lab"], "stage": r["stage"]})
    return in_grid, off_grid


# --- per-group pattern extraction -------------------------------------------

def _segment_runs(entries: list) -> list:
    """Sorted (date, plan) entries on one weekday -> list of runs {plan, dates},
    maximal runs of constant plan. Gaps between same-plan dates (closure or
    no-session days) are tolerated: only a date with a DIFFERENT plan breaks a run.
    """
    runs = []
    for d, plan in entries:
        if runs and runs[-1]["plan"] == plan:
            runs[-1]["dates"].append(d)
        else:
            runs.append({"plan": plan, "dates": [d]})
    return runs


def _fold_sandwiched_singles(runs: list, deviations: list) -> None:
    """Fold a length-1 run sandwiched between two runs of the SAME plan into a
    deviation exception, merging the neighbours into one period. A genuine one-off
    inside a uniform period becomes an exception; two or more adjacent deviations
    are left as separate periods (never absorbed, never diluted). Mutates runs and
    appends (date, plan) to deviations."""
    changed = True
    while changed:
        changed = False
        for i in range(1, len(runs) - 1):
            if (len(runs[i]["dates"]) == 1
                    and runs[i - 1]["plan"] == runs[i + 1]["plan"]):
                mid_date = runs[i]["dates"][0]
                deviations.append((mid_date, runs[i]["plan"]))
                runs[i - 1]["dates"].extend(runs[i + 1]["dates"])
                del runs[i:i + 2]
                changed = True
                break


def _motivo_deviation(d: datetime.date) -> str:
    if d == FIRST_DAY:
        return S.GIORNI_SPECIALI_MOTIVO
    return "Variazione di piano"


def _motivo_no_session(group: str, d: datetime.date) -> str:
    if _in_stage(group, d):
        return "Stage (gruppo fuori sede)"
    return "Non in sessione"


def _extract_group(group: str, quota_g: dict, school_days: list):
    """One group's in-grid quota -> (patterns, deviation_exc, nosession_dates).

    patterns: list of (validoDa, validoA, weekday_idx, (aula, lab, stage)), each a
    run of >= 2 same-plan dates on a weekday. deviation_exc: (date, plan) for the
    single-date breaks. nosession_dates: in-grid school days with no quota row."""
    patterns = []
    deviations = []
    for wd in range(5):
        entries = sorted((d, p) for d, p in quota_g.items() if d.weekday() == wd)
        if not entries:
            continue
        runs = _segment_runs(entries)
        _fold_sandwiched_singles(runs, deviations)
        for run in runs:
            if len(run["dates"]) >= 2:
                patterns.append((run["dates"][0], run["dates"][-1], wd, run["plan"]))
            else:
                deviations.append((run["dates"][0], run["plan"]))
    nosession = [d for d in school_days if d not in quota_g]
    return patterns, deviations, nosession


# --- compress ---------------------------------------------------------------

@lru_cache(maxsize=1)
def compress() -> tuple[list, list, list, dict]:
    """In-grid quota -> (patterns_13, exceptions_14, off_grid, metrics).

    patterns_13 / exceptions_14 are workbook-row dicts (origins = sistema).
    off_grid is the declared oreFuoriGriglia set (Saturdays + closures, all stage).
    metrics is the per-group describability report the gate prints and 99 declares.
    Cached: pure over the seed; the negative test deepcopies before mutating."""
    in_grid, off_grid = _quota_by_group()
    school_days = S.school_year_dates()
    patterns_13: list = []
    exceptions_14: list = []
    metrics = {"groups": {}}

    for group in sorted(in_grid, key=lambda g: (g.split()[-1], g)):
        quota_g = in_grid[group]
        pats, devs, nosess = _extract_group(group, quota_g, school_days)
        for da, a, wd, (aula, lab, stage) in sorted(pats):
            patterns_13.append({
                "classe": group, "validoDa": da.isoformat(), "validoA": a.isoformat(),
                "giorno": WEEKDAY_CODES[wd], "oreAula": aula, "oreLab": lab,
                "oreStage": stage, "origine": ORIGINE_SISTEMA,
                "note": f"pattern {group} {da.isoformat()}..{a.isoformat()} {WEEKDAY_CODES[wd]}",
            })
        for d, (aula, lab, stage) in sorted(devs):
            exceptions_14.append({
                "classe": group, "data": d.isoformat(), "oreAula": aula, "oreLab": lab,
                "oreStage": stage, "motivo": _motivo_deviation(d),
                "origine": ORIGINE_SISTEMA, "note": "rottura del pattern",
            })
        for d in nosess:
            exceptions_14.append({
                "classe": group, "data": d.isoformat(), "oreAula": 0, "oreLab": 0,
                "oreStage": 0, "motivo": _motivo_no_session(group, d),
                "origine": ORIGINE_SISTEMA, "note": "non in sessione (H26 esplicito)",
            })
        metrics["groups"][group] = {
            "patterns": len(pats), "deviation_exc": len(devs),
            "nosession_exc": len(nosess), "quota_dates": len(quota_g),
        }
    # Stable row order: 14 sorted by (classe, data) for human review.
    exceptions_14.sort(key=lambda r: (r["classe"], r["data"]))
    return patterns_13, exceptions_14, off_grid, metrics


# Describability policy: which groups are "exception-heavy" and must be DECLARED
# (in 99 + the task) rather than hidden inside a generous pattern. The two clauses:
#   - deviation_exc > 5: more than ~5 plan-breaks on a single weekday-family means
#     the weekly structure is genuinely irregular, not a clean pattern + rare breaks.
#   - nosession_exc + deviation_exc > 20: ~20+ unexplained/absence days is heavy for
#     a 33-week year (a clean group is ~0-3).
# No-session exceptions alone (e.g. G12's 21 stage absences) are legit and do not
# by themselves make a group "heavy" unless deviations also pile up.
HEAVY_DEVIATION = 5
HEAVY_TOTAL = 20


def heavy_groups(metrics: dict) -> list:
    """Groups whose pattern+exception description is heavy enough to declare."""
    out = []
    for g, m in metrics["groups"].items():
        if m["deviation_exc"] > HEAVY_DEVIATION or m["nosession_exc"] + m["deviation_exc"] > HEAVY_TOTAL:
            out.append(g)
    return out


# --- expand (the inverse) ---------------------------------------------------

def expand(patterns: list, exceptions: list) -> dict:
    """13 + 14 -> {(group, date): (aula, lab, stage, origine, regola)} over the
    in-grid school days. Exception wins over pattern (a dated break overrides the
    weekly rule for that date, including the 0/0/0 "non in sessione" override)."""
    school_days = S.school_year_dates()
    by_wd = defaultdict(list)   # weekday -> [(group, da, a, plan, note), ...]
    for p in patterns:
        wd = WEEKDAY_CODES.index(p["giorno"])
        by_wd[wd].append((p["classe"],
                          datetime.date.fromisoformat(p["validoDa"]),
                          datetime.date.fromisoformat(p["validoA"]),
                          (p["oreAula"], p["oreLab"], p["oreStage"]),
                          p["note"]))
    exc_by_key = {}
    for e in exceptions:
        exc_by_key[(e["classe"], datetime.date.fromisoformat(e["data"]))] = e

    out = {}
    for d in school_days:
        for group, da, a, plan, note in by_wd.get(d.weekday(), []):
            if (group, d) in exc_by_key:
                continue  # exception overrides; emitted below
            if da <= d <= a:
                out[(group, d)] = (plan[0], plan[1], plan[2], "pattern", note)
    for (group, d), e in exc_by_key.items():
        if d in school_days:
            out[(group, d)] = (e["oreAula"], e["oreLab"], e["oreStage"], "eccezione",
                               f"{group} {e['data']} ({e['motivo']})")
    return out
