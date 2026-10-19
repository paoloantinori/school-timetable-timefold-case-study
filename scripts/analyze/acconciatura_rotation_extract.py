#!/usr/bin/env python3
"""
TASK-468/task8 - Estrazione read-only delle rotazioni acconciatura dal golden.

Per ogni cella (docente, weekday, slot) la sequenza di gruppi che la occupano
nell'anno, con i confini di periodo (prima/ultima data, ore). Evidenzia:
- regolarita' per periodo (lo stesso gruppo tiene la cella per un run di settimane)
- eredita' dello slot (finito un periodo, la cella passa a un altro gruppo)
- co-presenza (piu' gruppi sulla stessa cella nello stesso periodo = ospiti)

Conforme alle trappole-golden (esclude sabati, Fasullo/Fasutto, Stage).
Separazione struttura-vs-arbitrio nel doc, non qui (qui e' l'istanza grezza).
"""
import csv
from datetime import datetime, timedelta
from collections import defaultdict

GOLDEN = "src/main/resources/seed/lessons.csv"
ACCONCIATURA = {"Barberia", "Lavaggio", "Servizio stilistico", "Servizio tecnico e stilistico"}
PLACEHOLDER = {"Fasullo", "Fasutto", ""}
WD_ORDER = ["Mon", "Tue", "Wed", "Thu", "Fri"]
WD_IT = {"Mon": "Lun", "Tue": "Mar", "Wed": "Mer", "Thu": "Gio", "Fri": "Ven"}


def parse_start(slot):
    h, m = slot.split()[0].split(":")
    return int(h) * 60 + int(m)


def main():
    # cell (teacher, weekday, slot_start) -> list of (date, group)
    cell = defaultdict(list)
    teacher_subject = {}
    for r in csv.DictReader(open(GOLDEN)):
        s, t, g, d = (r[k].strip() for k in ("subject", "teacher", "group", "date"))
        if s not in ACCONCIATURA or t in PLACEHOLDER:
            continue
        wd = datetime.strptime(d, "%Y-%m-%d").strftime("%a")
        if wd == "Sat":
            continue
        teacher_subject[t] = s
        cell[(t, wd, parse_start(r["slot"].strip()))].append((d, g))

    # ordina docenti per ore totali desc
    totals = defaultdict(int)
    for (t, wd, ss), occ in cell.items():
        totals[t] += len(occ)

    print("=" * 78)
    print("ESTRAZIONE ROTAZIONI ACCONCIATURA DAL GOLDEN 2026/27 (read-only)")
    print("=" * 78)
    print("Docenti: " + ", ".join(f"{t} ({teacher_subject[t]}) {totals[t]}h"
                                  for t in sorted(totals, key=lambda x: -totals[x])))
    print()

    for t in sorted(totals, key=lambda x: -totals[x]):
        print(f"### {t} - {teacher_subject[t]} ({totals[t]}h) ###")
        # celle di questo docente, ordinate per weekday poi slot
        cells = {k: v for k, v in cell.items() if k[0] == t}
        for wd in WD_ORDER:
            wd_cells = sorted([k for k in cells if k[1] == wd], key=lambda k: k[2])
            if not wd_cells:
                continue
            print(f"  [{WD_IT[wd]}]")
            for (_, _, ss), occ in [(k, cells[k]) for k in wd_cells]:
                occ.sort()
                # collassa in run per gruppo (periodi contigui: stessa data o settimana+1)
                runs = []  # (group, first, last, count)
                cur_g, cur_first, cur_last, cur_n = None, None, None, 0
                for d, g in occ:
                    if g != cur_g:
                        if cur_g is not None:
                            runs.append((cur_g, cur_first, cur_last, cur_n))
                        cur_g, cur_first, cur_last, cur_n = g, d, d, 1
                    else:
                        cur_last = d
                        cur_n += 1
                if cur_g is not None:
                    runs.append((cur_g, cur_first, cur_last, cur_n))
                # same-period co-presenza: piu' gruppi nello stesso giorno su questa cella
                by_day = defaultdict(set)
                for d, g in occ:
                    by_day[d].add(g)
                copres_days = sum(1 for gs in by_day.values() if len(gs) > 1)
                slot_hh = f"{ss // 60:02d}:{ss % 60:02d}"
                line = f"    slot {slot_hh} "
                # se co-presenza, mostra i gruppi con overlap
                if copres_days:
                    line += f"({copres_days} giorni co-presenza) "
                # descrivi run principali (>1 occ); scagliona
                main_runs = [r for r in runs if r[3] >= 2]
                tail = [r for r in runs if r[3] < 2]
                parts = []
                for g, f, l, n in main_runs:
                    fshort = f[5:]  # MM-DD
                    lshort = l[5:]
                    rng = fshort if fshort == lshort else f"{fshort}>{lshort}"
                    parts.append(f"{g}({rng}, {n}h)")
                txt = " ".join(parts)
                if tail:
                    tg = Counter_like(tail)
                    txt += " +" + tg if parts else tg
                print(line + txt)
        print()


def Counter_like(tail):
    from collections import Counter
    c = Counter(r[0] for r in tail)
    return ",".join(f"{g}:{n}" for g, n in c.most_common()) + "(singole)"


if __name__ == "__main__":
    main()
