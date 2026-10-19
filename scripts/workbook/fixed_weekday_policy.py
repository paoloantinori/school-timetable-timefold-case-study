#!/usr/bin/env python3
"""Fixed-weekday policy per (classe, materia) for sheet 23_GiornoFisso (golden mining 2026-08-15).

Derived live from the golden (``seed/lessons.csv``): a pair is FIXED-WEEKDAY when its
lessons (>= MIN_LESSONS) sit on one weekday >= DOMINANCE threshold of the time. The
sheet is the deposition of the school's PRACTICE as declared input (origine=sistema,
referente confirms or corrects), mirroring what :mod:`theory_block_policy` does for sheet 22.

DEDUCIBILITY FILTER (the mining doc's split, made mechanical here): pairs whose fixation
is already IMPLIED by the cadence reference (sheet 21) are EXCLUDED, because sheet 21
already declares them: a pair with mean hours/active-week <= MEAN_EPSILON (about 1h
every active week) and active weeks covering >= ACTIVE_COVERAGE of its span has no room
to move weekday without breaking the cadence, so the weekday is implied, not an extra
practice. Everything else in the fixed set is GENUINELY NEW input the cadence cannot
express. The reference lives in ``data/golden-cadence-reference.json`` (pairs keyed
"classe\\tmateria").

Sorting is deterministic (class anagraphic order, then materia) so the sheet and the
identity gate never see presentation drift.
"""
from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import sources as S

# Fixed-weekday detection thresholds (golden mining 2026-08-15).
MIN_LESSONS = 10       # a pair with fewer is noise, not practice
DOMINANCE = 0.90       # >=90% of the pair's hours on one weekday = fixed
# Deducibility-from-cadence thresholds (same criteria as the mining doc's split).
MEAN_EPSILON = 1.05    # mean hours per ACTIVE week ~ a single weekly slot
ACTIVE_COVERAGE = 0.80  # active weeks covering >=80% of the span = always-on cadence

LESSONS_CSV = (Path(__file__).resolve().parents[2] / "src" / "main" / "resources"
               / "seed" / "lessons.csv")
CADENCE_JSON = (Path(__file__).resolve().parents[2] / "claudedocs" / "spalmone-2026-27"
                / "data" / "golden-cadence-reference.json")

WEEKDAY_IT = {"Mon": "Lunedi", "Tue": "Martedi", "Wed": "Mercoledi",
              "Thu": "Giovedi", "Fri": "Venerdi", "Sat": "Sabato"}


def fixed_weekday_pairs() -> list[dict]:
    """All (classe, materia) pairs fixed on one weekday, with evidence columns.

    Each row: ``{classe, materia, giorno, ore, giorniOsservati, dominanza}``. Only the
    GENUINELY NEW pairs are returned (the cadence-implied ones are sheet 21's business).
    """
    per_pair: dict[tuple[str, str], Counter] = defaultdict(Counter)
    with open(LESSONS_CSV, newline="") as f:
        import csv
        for r in csv.DictReader(f):
            if r.get("closed", "False").lower() == "true":
                continue
            if not r.get("slot"):
                continue
            key = (r["group"], r["subject"])
            per_pair[key][r["day"]] += 1

    cadence = _cadence_reference()
    rows = []
    for (classe, materia), days in sorted(per_pair.items()):
        total = sum(days.values())
        if total < MIN_LESSONS:
            continue
        weekday, count = days.most_common(1)[0]
        dominance = count / total
        if dominance < DOMINANCE:
            continue
        if _implied_by_cadence(cadence, classe, materia):
            continue
        rows.append({
            "classe": classe,
            "materia": materia,
            "giorno": weekday,
            "ore": total,
            "giorniOsservati": len(days),
            "dominanza": dominance,
        })
    return rows


def _cadence_reference() -> dict:
    with open(CADENCE_JSON) as f:
        data = json.load(f)
    return {f"{p['group']}\t{p['subject']}": p for p in data.get("pairs", [])}


def _implied_by_cadence(cadence: dict, classe: str, materia: str) -> bool:
    p = cadence.get(f"{classe}\t{materia}")
    if p is None:
        return False
    return p["mean"] <= MEAN_EPSILON and p["active_weeks"] >= ACTIVE_COVERAGE * p["span_weeks"]


def sort_key(row: dict, classe_order: dict) -> tuple:
    """Deterministic presentation order: class anagraphic, then materia."""
    return (classe_order.get(row["classe"], 999), row["materia"])
