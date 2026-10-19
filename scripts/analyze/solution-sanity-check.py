#!/usr/bin/env python3
"""TASK-535: sanity gate obbligatorio per ogni validazione di un solve.

La prima tabella letta in ogni validazione: per ogni gruppo misura le tre cose
che l'occhio di maintainer trova in 10 minuti e che la batteria KPI storica non
guardava (post-ispezione 20/08):

  1. min ore/giorno RUN vs min ore/giorno GOLDEN (pavimento per-gruppo derivato
     dal conteggio del golden stesso) + giorni sotto quel minimo;
  2. buchi interni 1h H17-like per (gruppo, giorno): slot vuoti TRA il primo e
     l'ultimo slot occupato del giorno;
  3. lezioni collocate senza stanza (pin inclusi: i 72 pin aula roomless dei
     MODA erano invisibili perche' nessun KPI li contava).

Un rosso su una qualsiasi riga = run NON CONFORME: exit code 1. Nessuna
eccezione per "bacino sfortunato" (0hard e' binario).

Input: l'export JSON del run (gz o plain; capture-run.sh lo produce come
export.json.gz). Il golden e' src/main/resources/seed/lessons.csv con le
esclusioni di protocollo (sabati, ore Stage, ore Fasullo - vedi CLAUDE.md
"Trappole del golden").

Nota buchi: il conteggio usa lo SLOT INDEX (0-7), non l'ora wall-clock: la
pausa pranzo (slot 4 = 12:00, slot 5 = 14:00) non e' un buco. Mappa
ora->slot: h<13 -> h-8, altrimenti h-9.

Usage:
  python3 scripts/analyze/solution-sanity-check.py <export.json[.gz]> [--golden PATH]
"""

import argparse
import csv
import datetime
import gzip
import json
import os
import sys
from collections import defaultdict

REPO_GOLDEN = "src/main/resources/seed/lessons.csv"
REPO_ENVELOPE = os.path.join("claudodocs/spalmone-2026-27/data",
                             "golden-weekday-envelope.json")


def slot_index(hour):
    """Wall-clock start hour -> slot index (0-7); lunch 12->14 is NOT a gap."""
    return hour - 8 if hour < 13 else hour - 9


def lookup_stripped(table, name):
    """Fetch by name, falling back to the stripped spelling: the export's group
    names carry a leading space for digit groups (" G03") while the golden
    CSV and the envelope keys don't. One helper, both call sites - a second
    divergent spelling of this fix is how the dead g-min axis went unnoticed."""
    v = table.get(name)
    if v is not None:
        return v
    return table.get(name.strip())


def load_export(path):
    op = gzip.open if path.endswith(".gz") else open
    with op(path, "rt") as f:
        return json.load(f)


def parse_run(export):
    """group -> date -> list of (slotIndex, has_room, pinned); Stage/pseudo out."""
    run = defaultdict(lambda: defaultdict(list))
    for l in export.get("lessons", []):
        ts = l.get("timeslot")
        if not ts:
            continue
        c = l.get("course") or {}
        t = (c.get("teacher") or {})
        if c.get("subject") == "Stage" or t.get("pseudo") or t.get("name") == "Stage":
            continue
        room = l.get("room")
        # group names normalized HERE (single point): the export carries a leading
        # space for digit groups (" G03"), the golden CSV and the envelope keys
        # don't - before this, the g-min axis was dead since TASK-535 (28/08 fix)
        run[c["studentGroup"]["name"].strip()][ts["date"][:10]].append(
            (slot_index(int(ts["startDateTime"][11:13])), room is not None, bool(l.get("pinned"))))
    return run


def parse_golden(path):
    """group -> date -> non-Stage/non-Fasullo hour count (Saturdays excluded)."""
    g = defaultdict(lambda: defaultdict(int))
    for r in csv.DictReader(open(path)):
        dt = datetime.date.fromisoformat(r["date"][:10])
        if dt.weekday() == 5:  # the golden's 117 Saturday hours are protocol-excluded
            continue
        if r["subject"] == "Stage" or r["teacher"] == "Fasullo":
            continue
        g[r["group"]][r["date"][:10]] += 1
    return g


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("export", help="run export JSON (.json or .json.gz)")
    ap.add_argument("--golden", default=REPO_GOLDEN, help="golden lessons.csv")
    args = ap.parse_args()

    run = parse_run(load_export(args.export))
    golden = parse_golden(args.golden)

    print(f"{'gruppo':8s} {'g-min/max':>9s} {'run-min':>7s} {'gg<sottoMin':>11s}"
          f" {'buchi1h':>8s} {'no-stanza':>9s}")
    reds = []
    for grp in sorted(run):
        days = {dt: len(v) for dt, v in run[grp].items()}
        # leading-space export names: see lookup_stripped (found 28/08: the g-min
        # axis had been dead since TASK-535 before this normalization)
        gold_days = lookup_stripped(golden, grp)
        gvals = [v for v in gold_days.values() if v > 0]
        gmin = min(gvals) if gvals else None
        gmax = max(gvals) if gvals else None
        below = sorted((dt, n) for dt, n in days.items() if gmin and n < gmin)
        holes = []
        for dt, v in run[grp].items():
            slots = sorted(x[0] for x in v)
            gaps = [s for s in range(slots[0], slots[-1] + 1) if s not in slots]
            if gaps:
                holes.append((dt, gaps))
        noroom = sum(1 for v in run[grp].values() for x in v if not x[1])
        runmin = min(days.values()) if days else "-"
        print(f"{grp:8s} {str(gmin) + '-' + str(gmax):>9s} {str(runmin):>7s}"
              f" {len(below):>11d} {len(holes):>8d} {noroom:>9d}")
        if below:
            print(f"         ROSSO gg sotto min golden {gmin}h: {below[:6]}"
                  f"{' ...' if len(below) > 6 else ''}")
        if holes:
            print(f"         ROSSO buchi interni (gg {len(holes)}): {holes[:4]}"
                  f"{' ...' if len(holes) > 4 else ''}")
        if noroom:
            pinned_nr = sum(1 for v in run[grp].values() for x in v if not x[1] and x[2])
            print(f"         ROSSO lezioni senza stanza: {noroom} (di cui pinnate {pinned_nr})")
        if below or holes or noroom:
            reds.append(grp)

    # ── BUSTA GOLDEN per (gruppo, weekday) - TASK B-side 28/08: ADDITIVA, non fa
    # verdetto (la promozione a rosso viaggia col prossimo batch, dopo il suo
    # ciclo). Le due tolleranze del mandato: "cap G12 venerdi'" (il golden
    # pratica [4..5], mai 6+) e "busta giorni-corti FC" (il golden pratica
    # [4..5]/[3..5] ordinari dove il foglio dichiara 5-6: un giorno corto DENTRO
    # busta e' pratica, non difetto). Un giorno FUORI busta e' la firma visibile
    # del parcheggio surplus (sopra max) o del sotto-riempimento (sotto min). ──
    out_rows = []
    if os.path.exists(REPO_ENVELOPE):
        with open(REPO_ENVELOPE) as f:
            envelope = json.load(f)
        for key, e in sorted(envelope.items()):
            grp, wd = key.split("|")
            wd = int(wd)
            run_days = lookup_stripped(run, grp)
            for dt, v in run_days.items():
                n = len(v)
                if datetime.date.fromisoformat(dt).weekday() + 1 != wd:
                    continue
                if n < e["min"] or n > e["max"]:
                    out_rows.append((grp, dt, n, e["min"], e["mode"], e["max"]))
        print(f"\nBUSTA GOLDEN per weekday ({len(out_rows)} giorni fuori busta, "
              f"NON in verdetto):")
        for grp, dt, n, lo, mode, hi in out_rows[:15]:
            print(f"  {grp:8s} {dt} run={n}h vs busta [{lo}..{hi}] (modo golden {mode})")
        if len(out_rows) > 15:
            print(f"  ... e altri {len(out_rows) - 15}")

    if reds:
        print(f"\nSANITY: NON CONFORME - rosso su {len(reds)} gruppo/i: {', '.join(reds)}")
        sys.exit(1)
    print("\nSANITY: CONFORME - nessun rosso (minimi golden, buchi, stanze)")


if __name__ == "__main__":
    main()
