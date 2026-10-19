#!/usr/bin/env python3
"""Weekday-fill policy for sheet 25_WeekdayFill (golden mining r2, section D CORRECTED).

Live derivation from the golden (``seed/lessons.csv``) against the calendar sheets,
with the semantics the corrected mining measured (see
``claudedocs/spalmone-2026-27/golden-mining-r2-2026-08-16.md`` section D):

* expected: the school's own calendar sheets per (group, date) - class_days with the
  FC-group SKIP and the modality-S guard (stage days are not aula+lab capacity) plus
  the fc aula+lab split, exactly :func:`sources.load_calendario_class_days` semantics;
* observed: golden hours on EXPECTED days only, traps excluded (closed, Saturdays,
  Stage/Fasullo). Golden hours on days the calendar does not expect are the separate
  14/08 question (5 ore fuori calendario) and are deliberately NOT absorbed here;
* a cell (classe, weekday) is UNDER-FILL when fill < FILL_FLOOR with >= MIN_DAYS
  expected days of evidence.

Deterministic sorting (class anagraphic order, then weekday order).
"""
from __future__ import annotations

import csv
import datetime as dt
from collections import Counter, defaultdict
from pathlib import Path

import sources as S

# Thresholds (mandate 2026-08-16 sera + corrected mining section D).
FILL_FLOOR = 0.80   # fill < 80% of the group's own calendar expectation
MIN_DAYS = 10       # fewer expected days is noise, not practice

LESSONS_CSV = (Path(__file__).resolve().parents[2] / "src" / "main" / "resources"
               / "seed" / "lessons.csv")
PSEUDO = {"Stage", "Fasullo"}
WEEKDAY_IT = {0: "Lunedi", 1: "Martedi", 2: "Mercoledi", 3: "Giovedi", 4: "Venerdi"}


def _expected_by_date() -> dict[tuple[str, str], int]:
    """(group, date) -> expected aula+lab hours, sources semantics (S guard + FC skip)."""
    exp: dict[tuple[str, str], int] = {}
    for r in S.load_calendario_class_days():
        h = int(r["hours"] or 0)
        if h > 0 and r["modality"] != "S":
            exp[(r["group"], r["date"])] = h
    for r in S.load_calendario_FC():
        h = int(r["aulaHours"] or 0) + int(r["labHours"] or 0)
        if h > 0:
            exp[(r["group"], r["date"])] = h
    return exp


def underfill_cells() -> list[dict]:
    """The (classe, weekday) cells under the fill floor, with evidence columns.

    Each row: ``{classe, weekday, oreOsservate, oreAttese, fill, giorniAttesi}``.
    """
    exp = _expected_by_date()
    exp_wd: dict[str, dict[int, list[int]]] = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    for (g, d), h in exp.items():
        wd = dt.date.fromisoformat(d).weekday()
        exp_wd[g][wd][0] += h
        exp_wd[g][wd][1] += 1

    obs: dict[str, Counter] = defaultdict(Counter)
    with open(LESSONS_CSV, newline="") as f:
        for r in csv.DictReader(f):
            if r.get("closed", "False").lower() == "true":
                continue
            if r.get("day") == "Sabato":
                continue
            if r.get("teacher") in PSEUDO or r.get("subject") in PSEUDO:
                continue
            if (r["group"], r["date"]) not in exp:
                continue  # off-calendar golden hours: the separate 14/08 question
            obs[r["group"]][dt.date.fromisoformat(r["date"]).weekday()] += 1

    rows: list[dict] = []
    for g, per_wd in exp_wd.items():
        for wd, (expected_h, days) in per_wd.items():
            if days < MIN_DAYS or expected_h <= 0:
                continue
            observed = obs[g][wd]
            fill = observed / expected_h
            if fill < FILL_FLOOR:
                rows.append({
                    "classe": g, "weekday": WEEKDAY_IT[wd], "weekdayNum": wd,
                    "oreOsservate": observed, "oreAttese": expected_h,
                    "fill": fill, "giorniAttesi": days,
                })
    return rows


def sort_key(row: dict, classe_order: dict[str, int]) -> tuple:
    """Deterministic: class anagraphic order, then weekday order."""
    return (classe_order.get(row["classe"], 999), row["weekdayNum"])
