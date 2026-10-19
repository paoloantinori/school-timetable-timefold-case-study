#!/usr/bin/env python3
"""golden-weekday-envelope.py - TASK B-side 28/08: la BUSTA GOLDEN per (gruppo, weekday).

Le due tolleranze del mandato notturno 28/29-08, estratte meccanicamente dal golden
(l'oracolo sui dati, direttiva TASK-379/466):
  - "cap G12 venerdi'": il golden pratica 4h (modo) e mai piu' di 5h di venerdi'
    (12 date, sheet==golden per-data); il run che parcheggia 5-6h o svuota l'anno
    e' FUORI busta. La busta lo rende visibile per (gruppo, weekday).
  - "busta giorni-corti FC": il golden pratica G04 [4..5] modo 5, G08
    [2..5] modo 5 per-weekday (min 2 il giovedi', 3 il venerdi'), ore ordinarie Stage
    escluso, dove il foglio dichiara quasi solo 5-6h:
    i giorni corti dentro busta sono pratica, non difetto (domanda referente 2).

Output: claudodocs/spalmone-2026-27/data/golden-weekday-envelope.json con, per
(gruppo, weekday ISO 1..5): {"min": h, "mode": h, "max": h, "days": n, "sum": h}.
Protocollo del golden (CLAUDE.md "Trappole"): sabati esclusi, ore Fasullo escluse,
righe closed escluse, solo giorni con ore > 0.

--check: rigenera dal golden vivo e confronta col JSON committato; esce non-zero
a qualunque deriva (identity gate: la copia derivata non puo' marcire in silenzio,
stessa lezione del freeze-test golden-copresence). Il self-check del golden contro
la propria busta e' 0-fuori per costruzione (min/max DEFINISCONO la busta).

Usage:
  python3 scripts/analyze/golden-weekday-envelope.py                # genera + stampa
  python3 scripts/analyze/golden-weekday-envelope.py --check        # identity gate
"""
import argparse
import datetime
import json
import os
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(__file__))
from shared import iter_open_lessons_rows

REPO = os.path.join(os.path.dirname(__file__), "..", "..")
GOLDEN = os.path.join(REPO, "src/main/resources/seed/lessons.csv")
OUT = os.path.join(REPO, "claudodocs/spalmone-2026-27/data/golden-weekday-envelope.json")
WEEKDAY_IT = {1: "lunedi'", 2: "martedi'", 3: "mercoledi", 4: "giovedi'", 5: "venerdi'"}


def build_envelope(golden_path):
    """(group, weekday) -> {min, mode, max, days, sum} over days with hours > 0."""
    hours = defaultdict(dict)   # group -> date -> hours
    # iter_open_lessons_rows carries the closed-row exclusion (the shared golden
    # protocol); the Stage/Fasullo/weekend exclusions below are the same ones
    # solution-sanity-check.py's parse_golden applies (the sanity axes measure
    # aula+lab practice, Stage is off-grid by nature)
    for r in iter_open_lessons_rows(golden_path):
        if r.get("day") == "Sabato":
            continue
        if r.get("subject") == "Stage" or r.get("teacher") == "Fasullo":
            continue
        d = datetime.date.fromisoformat(r["date"])
        if d.weekday() >= 5:
            continue
        hours[r["group"]][d] = hours[r["group"]].get(d, 0) + 1
    envelope = {}
    for group, by_date in hours.items():
        per_wd = defaultdict(list)
        for d, h in by_date.items():
            if h > 0:
                per_wd[d.weekday() + 1].append(h)
        for wd, vals in per_wd.items():
            counts = Counter(vals)
            # deterministic tie-break (mirrored by the Java golden-pin test):
            # highest count first, then the SMALLEST hours value
            mode = min(counts, key=lambda v: (-counts[v], v))
            envelope[f"{group}|{wd}"] = {
                "min": min(vals),
                "mode": mode,
                "max": max(vals),
                "days": len(vals),
                "sum": sum(vals),
            }
    return envelope


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true",
                    help="identity gate: ricomputa dal golden e confronta col JSON committato")
    args = ap.parse_args()

    envelope = build_envelope(GOLDEN)
    if args.check:
        if not os.path.exists(OUT):
            sys.exit(f"ENVELOPE GATE FAIL: {OUT} non esiste: genera prima senza --check")
        with open(OUT) as f:
            committed = json.load(f)
        if committed != envelope:
            drift = {k for k in set(committed) | set(envelope)
                     if committed.get(k) != envelope.get(k)}
            sys.exit(f"ENVELOPE GATE FAIL: {len(drift)} celle divergono dal golden vivo "
                     f"(esempi {sorted(drift)[:5]}): rigenera e committa")
        print(f"ENVELOPE GATE OK: {len(committed)} celle (gruppo, weekday) identiche al golden vivo")
        return 0

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(envelope, f, indent=1, sort_keys=True)
        f.write("\n")
    print(f"busta golden per (gruppo, weekday): {len(envelope)} celle -> {OUT}")
    for key in ("G12|5", "G04|1", "G04|5", "G08|1", "G08|5"):
        if key in envelope:
            e = envelope[key]
            g, wd = key.split("|")
            print(f"  {g} {WEEKDAY_IT[int(wd)]}: min {e['min']} / modo {e['mode']} / max {e['max']}"
                  f"  ({e['days']} giorni, {e['sum']}h)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
