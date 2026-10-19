#!/usr/bin/env python3
"""Monthly-concentration policy for sheet 24_ConcentrazioneMensile (golden mining r2).

Derived live from the golden (``seed/lessons.csv``) against the calendar sheets'
expectation baseline, with EXACTLY the rule measured in
``claudedocs/spalmone-2026-27/golden-mining-r2-2026-08-16.md`` section B:

* population: (classe, materia) pairs with >= MIN_HOURS placed hours (traps excluded:
  closed rows, Saturdays, Stage/Fasullo pseudo);
* expectation: the pair's annual hours x the group's expected-hours share of that month
  (class_days ``hours`` + fc ``aula+lab``): stage periods are ALREADY accounted for by
  the baseline, so an excess is beyond quota;
* STRONG set (the declarable one, far above multinomial noise): a month holding
  >= RATIO_FLOOR x its expectation AND >= SHARE_FLOOR of the pair's annual hours.

The 2x threshold of the mandate is deliberately NOT used for the sheet: the mining doc
measured that at ~10%/month expected shares, ">= 2x" fires on noise (41/171 pairs);
the >= 4x strong set is the 8-pair population referente is asked about. Sorting is
deterministic (ratio desc, then class anagraphic order, then materia, then month) so
the sheet and the identity gate never see presentation drift.
"""
from __future__ import annotations

import csv
import datetime as dt
from collections import Counter, defaultdict
from pathlib import Path

import sources as S

# Thresholds (golden-mining-r2-2026-08-16.md, section B).
MIN_HOURS = 30      # pairs below this are noise, not practice
RATIO_FLOOR = 4.0   # observed >= 4x the calendar-expected hours in that month
SHARE_FLOOR = 0.25  # and that month holds >= 25% of the pair's annual hours

LESSONS_CSV = (Path(__file__).resolve().parents[2] / "src" / "main" / "resources"
               / "seed" / "lessons.csv")
PSEUDO = {"Stage", "Fasullo"}
MONTH_IT = {1: "gennaio", 2: "febbraio", 3: "marzo", 4: "aprile", 5: "maggio", 6: "giugno",
            7: "luglio", 8: "agosto", 9: "settembre", 10: "ottobre", 11: "novembre",
            12: "dicembre"}


def _expected_hours_by_group_month() -> dict[str, Counter]:
    """group -> month -> expected aula+lab hours (the school's own calendar sheets)."""
    exp: dict[str, Counter] = defaultdict(Counter)
    for r in S.load_calendario_class_days():
        # modality S rows are stage days, not aula+lab capacity (see the r2 mining
        # script's loader for the same guard and its rationale).
        h = int(r["hours"] or 0)
        if h > 0 and r["modality"] != "S":
            exp[r["group"]][int(r["date"][5:7])] += h
    for r in S.load_calendario_FC():
        h = int(r["aulaHours"] or 0) + int(r["labHours"] or 0)
        if h > 0:
            exp[r["group"]][int(r["date"][5:7])] += h
    return exp


def concentration_pairs() -> list[dict]:
    """The STRONG monthly-concentration rows, with evidence columns.

    Each row: ``{classe, materia, mese, oreNelMese, oreAttese, rapporto, oreAnno,
    quotaAnno}``.
    """
    obs: dict[tuple[str, str], Counter] = defaultdict(Counter)
    tot: Counter = Counter()
    with open(LESSONS_CSV, newline="") as f:
        for r in csv.DictReader(f):
            if r.get("closed", "False").lower() == "true":
                continue
            if r.get("day") == "Sabato":
                continue  # 117 golden Saturday hours: not placeable in our grid
            if r.get("teacher") in PSEUDO or r.get("subject") in PSEUDO:
                continue
            if not r.get("slot"):
                continue
            key = (r["group"], r["subject"])
            obs[key][int(r["date"][5:7])] += 1
            tot[key] += 1

    exp = _expected_hours_by_group_month()
    rows: list[dict] = []
    for key, hours in tot.items():
        if hours < MIN_HOURS:
            continue
        group_exp = exp.get(key[0])
        if not group_exp:
            continue
        year_exp = sum(group_exp.values())
        for month, observed in obs[key].items():
            share_expected = group_exp.get(month, 0) / year_exp
            if share_expected <= 0.02:
                continue
            expected_h = hours * share_expected
            if observed >= RATIO_FLOOR * expected_h and observed >= SHARE_FLOOR * hours:
                rows.append({
                    "classe": key[0], "materia": key[1],
                    "mese": MONTH_IT[month], "meseNum": month,
                    "oreNelMese": observed, "oreAttese": expected_h,
                    "rapporto": observed / expected_h,
                    "oreAnno": hours, "quotaAnno": observed / hours,
                })
    return rows


def sort_key(row: dict, classe_order: dict[str, int]) -> tuple:
    """Deterministic: ratio desc, then class anagraphic order, materia, month."""
    return (-row["rapporto"], classe_order.get(row["classe"], 999),
            row["materia"], row["meseNum"])
