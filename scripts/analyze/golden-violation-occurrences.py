#!/usr/bin/env python3
"""TASK-472 AC#3 - Standing occurrence report of golden rule violations.

For each codified rule the golden itself violates (M-TR no-2h-teoria, M24
T11 early-start, M25 T05 free-days), enumerate EVERY occurrence and attach
an attempted mechanical explanation, or an explicit 'inspiegata' when no
mechanical cause is found. Feeds the referente-facing tension doc (AC#4).

Explanation heuristics (proxies, declared):
- M-TR: the declared block policy per (classe, materia) from TASK-471 sheet 22
  (theory_block_policy, same golden, same is_theory mirror). BLOCCO -> the 2h
  day is deliberate practice; MISTA -> ambivalent (open referente question);
  STRETTA -> two mechanical causes: (a) compaction, the 2h day IS the whole
  week's hours of that pair (teacher-trip economy pattern); (b) load, week
  hours > 5 (the Mon-Fri ceiling) make a 2h day arithmetically FORCED; else
  'inspiegata' (2h against the pair's own dominant practice, no forcing).
- M24: weeks where T11 starts early (slot 08:00) on 2+ days. Load proxy:
  week hours > 25 (~4+ full days) -> 'carico alto'; else 'inspiegata'.
- M25: weeks where T05 teaches on 4+ days (<2 free). Load proxy: week hours
  >= 24 (4 x 6h needed) -> 'carico alto'; else 'inspiegata'.

Usage: python3 scripts/analyze/golden-violation-occurrences.py [golden.csv]
"""
from __future__ import annotations

import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "workbook"))
import shared  # noqa: E402
from theory_adjacent_residual_dump import PLACEHOLDER  # noqa: E402
from theory_block_policy import BLOCCO, MISTA, STRETTA, theory_block_policy  # noqa: E402

GOLDEN = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "seed" / "lessons.csv"

T11_MAX_EARLY = 1   # M24: maxEarlyStartDaysPerWeek
T05_MIN_FREE = 2      # M25: requiredFreeDaysPerWeek
EARLY_HOUR = 8         # slot 0 starts at 08:00


def iso_year_week(d: date) -> tuple[int, int]:
    iso = d.isocalendar()
    return iso[0], iso[1]


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    golden = Path(args[0]) if args else GOLDEN
    lessons = shared.parse_lessons_csv(golden, include_hour=True)

    # ---- M-TR occurrences: (group, subject, date) with 2+ theory hours ----
    policy = {(r["classe"], r["materia"]): r["bloccoTeoria"] for r in theory_block_policy()}
    per_day: dict[tuple[str, str], dict] = defaultdict(lambda: defaultdict(int))
    for group, d, _h, subject, teacher in lessons:
        if d.weekday() == 5 or teacher in PLACEHOLDER or subject in PLACEHOLDER:
            continue
        key = (group, subject)
        if key not in policy:
            continue  # not a theory pair under the is_theory mirror
        per_day[key][d] += 1

    mtr = defaultdict(int)  # explanation -> count
    mtr_ins  = []           # inspiegate occurrences (the report-worthy ones)
    for (group, subject), days in sorted(per_day.items()):
        pol = policy[(group, subject)]
        week_hours: dict[tuple[int, int], int] = defaultdict(int)
        for d, c in days.items():
            week_hours[iso_year_week(d)] += c
        for d, c in sorted(days.items()):
            if c < 2:
                continue
            yw = iso_year_week(d)
            if pol == BLOCCO:
                mtr["deliberata (policy 2h, foglio 22)"] += 1
            elif pol == MISTA:
                mtr["mista (pratica ambivalente, domanda referente)"] += 1
            elif c == week_hours[yw]:
                mtr["compattazione settimanale in un giorno (probabile economia docente)"] += 1
            elif week_hours[yw] > 5:
                mtr["forzata dal carico (ore settimanali > 5 giorni)"] += 1
            else:
                mtr["inspiegata (policy 1h, nessuna forzante)"] += 1
                mtr_ins.append((group, subject, d.isoformat(), c, week_hours[yw]))

    # ---- M24: T11 weeks with 2+ early-start days ----
    T11_days: dict[tuple[int, int], set] = defaultdict(set)
    T11_hours: dict[tuple[int, int], int] = defaultdict(int)
    T11_groups: dict[tuple[int, int], set] = defaultdict(set)
    for group, d, h, subject, teacher in lessons:
        if teacher != "T11":
            continue
        yw = iso_year_week(d)
        T11_hours[yw] += 1
        T11_groups[yw].add(group)
        if h == EARLY_HOUR:
            T11_days[yw].add(d)
    m24 = defaultdict(int)
    for yw, days in sorted(T11_days.items()):
        if len(days) <= T11_MAX_EARLY:
            continue
        expl = ("carico alto (week %dh)" % T11_hours[yw]) if T11_hours[yw] > 25 else "inspiegata"
        m24[expl] += 1

    # ---- M25: T05 weeks with <2 free days ----
    T05_days: dict[tuple[int, int], set] = defaultdict(set)
    T05_hours: dict[tuple[int, int], int] = defaultdict(int)
    for group, d, h, subject, teacher in lessons:
        if teacher != "T05":
            continue
        yw = iso_year_week(d)
        T05_days[yw].add(d)
        T05_hours[yw] += 1
    m25 = defaultdict(int)
    for yw, days in sorted(T05_days.items()):
        teaching = len(days)
        free = 5 - teaching
        if free >= T05_MIN_FREE:
            continue
        expl = ("carico alto (week %dh su %dg)" % (T05_hours[yw], teaching)) if T05_hours[yw] >= 24 else "inspiegata"
        m25[expl] += 1

    # ---- Standing report ----
    total_mtr = sum(mtr.values())
    print("# TASK-472 AC#3 - golden violation occurrences with attempted explanation")
    print("# golden: %s" % golden)
    print()
    print("M-TR (no-2h teoria): %d giorni-2+ occorrenze" % total_mtr)
    for expl, n in sorted(mtr.items(), key=lambda kv: -kv[1]):
        print("  %4d  %s" % (n, expl))
    print("  -- inspiegate (%d):" % len(mtr_ins))
    shown = mtr_ins if "--full" in sys.argv else mtr_ins[:15]
    for group, subject, d, c, wh in shown:
        print("     %s | %s | %s | %dh nel giorno, %dh in settimana" % (group, subject, d, c, wh))
    if len(mtr_ins) > len(shown):
        print("     ... e altre %d (usa --full)" % (len(mtr_ins) - len(shown)))
    print()
    print("M24 (T11 early-start >%d/sett): %d settimane violate" % (T11_MAX_EARLY, sum(m24.values())))
    for expl, n in sorted(m24.items(), key=lambda kv: -kv[1]):
        print("  %4d  %s" % (n, expl))
    print()
    print("M25 (T05 free days <%d/sett): %d settimane violate" % (T05_MIN_FREE, sum(m25.values())))
    for expl, n in sorted(m25.items(), key=lambda kv: -kv[1]):
        print("  %4d  %s" % (n, expl))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
