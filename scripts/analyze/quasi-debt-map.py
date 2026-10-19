#!/usr/bin/env python3
"""Mappa del debito quasi (H32/H34) di una consegna, per l'arbitraggio di maintainer.

TASK-538 M3: il ciclo quasi-tier consegna [0/-X]hard e il debito X e' il prezzo
visibile (H32 celle sotto quota + H34 giorni dichiarati vuoti). Questo script
trasforma quel numero aggregato nella mappa per (gruppo, data): quante ore
mancano, dove, e quanto e' profondo il buco, con il golden come riferimento
della pratica della scuola sulla stessa mappa.

Scope H32 (da CLAUDE.md): escluse G04 e G08 (target pool-cap forma-b,
non la quota del foglio) e G14; G09 tolleranza 1h. Stage escluso dal
conteggio ore (pseudo-docente).

Usage:
  quasi-debt-map.py --run <lessons.txt> [--golden] [--html OUT.html]
"""
import argparse
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from shared import data_dir_for_year, parse_lessons_csv, parse_lessons_pipe  # noqa: E402

STAGE_TEACHER = "Stage"
# Scope H32/H34 per la direttiva corrente (CLAUDE.md, H32/H34 rows).
EXCLUDED_GROUPS = {"G04", "G08", "G14"}
TOLERANCE = {"G09": 1}


def load_declared_quota(data_dir: Path):
    """(group, date) -> declared aula+lab hours, dal seed (fonte DailyActivityQuota)."""
    quota = {}
    class_days = data_dir / "calendario_class_days.csv"
    with open(class_days, encoding="utf-8-sig") as fh:
        header = fh.readline().strip().split(",")
        idx = {name: header.index(name) for name in ("group", "date", "hours", "modality")}
        for line in fh:
            parts = line.strip().split(",")
            if len(parts) < 4:
                continue
            group, date, hours, modality = (parts[idx["group"]], parts[idx["date"]],
                                            parts[idx["hours"]], parts[idx["modality"]])
            if modality == "S":  # stage: fuori dal metro aula+lab
                continue
            key = (group, date)
            quota[key] = quota.get(key, 0) + int(hours)
    fc = data_dir / "calendario_fc.csv"
    with open(fc, encoding="utf-8-sig") as fh:
        header = fh.readline().strip().split(",")
        idx = {name: header.index(name) for name in ("group", "date", "aula_hours", "lab_hours")}
        for line in fh:
            parts = line.strip().split(",")
            if len(parts) < 4:
                continue
            key = (parts[idx["group"]], parts[idx["date"]])
            quota[key] = quota.get(key, 0) + int(parts[idx["aula_hours"]] or 0) \
                + int(parts[idx["lab_hours"]] or 0)
    return quota


def placed_non_stage_hours(rows):
    """(group, date) -> ore collocate non-Stage. Accetta tuple con o senza hour
    (i parser di shared.py ritornano (group, date, [hour,] subject, teacher))."""
    placed = defaultdict(int)
    for row in rows:
        group, d, teacher = row[0], row[1], row[-1]
        if teacher == STAGE_TEACHER:
            continue
        placed[(group, str(d))] += 1
    return placed


def debt_map(rows, quota):
    """Ritorna (celle_h32, celle_h34, dettaglio per gruppo)."""
    placed = placed_non_stage_hours(rows)
    per_group = defaultdict(lambda: {"h32_cells": 0, "h32_hours": 0, "h34_days": [],
                                     "detail": []})
    for (group, date), declared in sorted(quota.items()):
        if group in EXCLUDED_GROUPS:
            continue
        got = placed.get((group, date), 0)
        tol = TOLERANCE.get(group, 0)
        deficit = declared - got - tol
        if deficit <= 0:
            continue
        entry = per_group[group]
        entry["detail"].append((date, declared, got, deficit))
        if got == 0:
            entry["h34_days"].append(date)
        else:
            entry["h32_cells"] += 1
            entry["h32_hours"] += deficit
    return per_group


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--run", required=True, help="lessons.txt (formato pipe) della consegna")
    ap.add_argument("--golden", action="store_true",
                    help="aggiungi la stessa mappa calcolata sul golden come riferimento")
    ap.add_argument("--year", default="2026-27")
    ap.add_argument("--compare", help="secondo lessons.txt: emette anche il confronto "
                    "cella-per-cella dei debiti oltre-golden fra i due run (n=2)")
    args = ap.parse_args()

    data_dir = Path(data_dir_for_year(args.year))
    quota = load_declared_quota(data_dir)

    rows = list(parse_lessons_pipe(args.run, include_hour=True))

    maps = [("RUN", debt_map(rows, quota))]
    if args.golden:
        golden_csv = data_dir / "lessons.csv"
        golden_rows = list(parse_lessons_csv(golden_csv, include_hour=True))
        maps.append(("GOLDEN", debt_map(golden_rows, quota)))

    if args.compare:
        # Confronto n=2 cella-per-cella dei debiti oltre-golden: le celle presenti in
        # ENTRAMBI i run sono le firme strutturali (non rumore di ricerca); quelle in
        # uno solo sono variabilita' del bacino. Con --golden le celle condivise col
        # golden sono escluse da entrambi i lati (non sono debito nostro).
        def beyond_golden_cells(per_group, golden_cells):
            cells = {}
            for group, g in per_group.items():
                for date, declared, got, deficit in g["detail"]:
                    if (group, date) in golden_cells:
                        continue
                    cells[(group, date)] = deficit
            return cells

        golden_cells = set()
        if args.golden:
            for group, g in maps[[m[0] for m in maps].index("GOLDEN")][1].items():
                golden_cells.update((group, date) for date, _d, _g, _v in g["detail"])
        run1_cells = beyond_golden_cells(maps[0][1], golden_cells)
        run2_cells = beyond_golden_cells(debt_map(list(parse_lessons_pipe(args.compare, True)), quota),
                                         golden_cells)
        both = sorted(set(run1_cells) & set(run2_cells))
        only1 = sorted(set(run1_cells) - set(run2_cells))
        only2 = sorted(set(run2_cells) - set(run1_cells))
        print(f"\n=== CONFRONTO n=2 (debito oltre-golden) ===")
        print(f"  REPLICATE in entrambi i run: {len(both)} celle (firme strutturali)")
        for (g, d) in both:
            print(f"    {g} {d}: run1 -{run1_cells[(g, d)]}h, run2 -{run2_cells[(g, d)]}h")
        print(f"  SOLO run1: {len(only1)} | SOLO run2: {len(only2)} (variabilita' di bacino)")
        for label, cells_map, only in (("run1", run1_cells, only1), ("run2", run2_cells, only2)):
            for (g, d) in only:
                print(f"    solo {label}: {g} {d} -{cells_map[(g, d)]}h")
    # Il DELTA run-vs-golden e' il numero che serve all'arbitraggio: entrambe le
    # mappe usano la stessa base (il foglio), quindi la differenza e' immune dalle
    # divergenze foglio-vs-app (G13: il golden stesso pratica 4-6h contro le 10-11
    # dichiarate; metà giugno: giorni dichiarati che nemmeno il golden riempie).
    print("\n=== DELTA run-vs-golden (il debito NUOVO oltre la pratica della scuola) ===")
    run_detail, gold_detail = maps[0][1], maps[1][1]
    groups = sorted(set(run_detail) | set(gold_detail))
    tot_c = tot_h = tot_e = 0
    for group in groups:
        run_d = {d: (decl, got, deficit) for d, decl, got, deficit in run_detail[group]["detail"]}
        gold_d = {d: (decl, got, deficit) for d, decl, got, deficit in gold_detail[group]["detail"]}
        new_cells, new_empty, lines = [], [], []
        for d in sorted(set(run_d) | set(gold_d)):
            r = run_d.get(d, (None, None, 0))
            g = gold_d.get(d, (None, None, 0))
            delta_h = r[2] - g[2]
            if delta_h > 0:
                run_empty = r[1] == 0
                gold_empty = g[1] == 0
                if run_empty and not gold_empty:
                    new_empty.append(d)
                    lines.append(f"    {d}: VUOTO nel run, golden collocava {g[1]}h")
                else:
                    new_cells.append(d)
                    lines.append(f"    {d}: -{delta_h}h oltre il golden (run {r[1]}h, golden {g[1]}h, dichiarate {r[0]})")
        if lines:
            print(f"  {group}: {len(new_cells)} celle peggiori, {len(new_empty)} giorni vuoti nuovi")
            print("\n".join(lines))
            tot_c += len(new_cells)
            tot_h += sum(1 for _ in new_cells)
            tot_e += len(new_empty)
    print(f"  TOTALE debito nuovo: {tot_c} celle sotto quota oltre-golden, "
          f"{tot_e} giorni vuoti oltre-golden")

    for label, per_group in maps:
        tot_cells = sum(g["h32_cells"] for g in per_group.values())
        tot_hours = sum(g["h32_hours"] for g in per_group.values())
        tot_h34 = sum(len(g["h34_days"]) for g in per_group.values())
        print(f"\n=== {label}: H32 {tot_cells} celle / {tot_hours} ore sotto quota; "
              f"H34 {tot_h34} giorni dichiarati vuoti ===")
        for group in sorted(per_group):
            g = per_group[group]
            if not g["detail"]:
                continue
            print(f"  {group}: H32 {g['h32_cells']} celle ({g['h32_hours']}h), "
                  f"H34 {len(g['h34_days'])} gg vuoti")
            for date, declared, got, deficit in g["detail"]:
                marker = "VUOTO" if got == 0 else f"-{deficit}h"
                print(f"    {date}: dichiarate {declared:2d}, collocate {got:2d} -> {marker}")


if __name__ == "__main__":
    main()
