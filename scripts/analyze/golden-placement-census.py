#!/usr/bin/env python3
"""Golden-placement census for declared-structural defects (lever della direttiva endgame).

Per ogni cella-difetto (gruppo, data, slot|None) riporta cosa fa il GOLDEN quel giorno:
ore, span, buchi, e se lo slot specifico e' pieno. Serve a decidere se un difetto che un
round cieco dichiara 'inchiudibile' e' (i) praticato dal golden (-> pattern-placement,
frontiera costruzione) o (ii) accettato corto anche dal golden (-> tolleranza, domanda a
referente). Validato sui 7 difetti strutturali del round cieco B3 (15/09: golden 7/7 pieni).

Usage:
  python3 golden-placement-census.py <defetti.tsv>
  formato TSV: id<TAB>gruppo<TAB>data-ISO<TAB>slot-o-vuoto
Default (senza argomenti): i 7 difetti strutturali B3.
"""
import sys
from collections import defaultdict
from datetime import date

sys.path.insert(0, "/home/user/data/repo/personal/planner/scripts/analyze")
from shared import parse_lessons_csv

GOLDEN = "/home/user/data/repo/personal/planner/src/main/resources/seed/lessons.csv"

DEFAULT_DEFECTS = [
    ("D1", "G10", "2026-12-10", "11"),
    ("D3", "G11", "2026-12-10", "9"),
    ("D4", "G12", "2026-09-18", "11"),
    ("D5", "G12", "2026-10-02", "11"),
    ("D6", "G12", "2026-10-09", "11"),
    ("D8", "G12", "2026-11-10", ""),
    ("D9", "G12", "2027-04-16", ""),
]

WEEKDAYS = ["lun", "mar", "mer", "gio", "ven", "sab"]


def load_golden():
    by_group_date = defaultdict(list)
    for grp, dt, hour, subj, teacher in parse_lessons_csv(GOLDEN):
        d = dt if isinstance(dt, date) else date.fromisoformat(str(dt)[:10])
        by_group_date[(grp, d)].append((hour, subj, teacher))
    return by_group_date


def day_shape(hours):
    occ = sorted(h for h in hours if h is not None)
    if not occ:
        return "VUOTO"
    lo, hi = occ[0], occ[-1]
    holes = [h for h in range(lo, hi + 1) if h not in occ]
    return f"{len(occ)}h[{lo}-{hi}]" + (f" buchi@{holes}" if holes else " compatto")


def main(defects):
    by_group_date = load_golden()
    filled = 0
    for did, grp, dstr, slot in defects:
        d = date.fromisoformat(dstr)
        day = by_group_date.get((grp, d), [])
        hours = [h for (h, s, t) in day]
        at_slot = sum(1 for x in day if slot and x[0] == int(slot))
        wd_days = sorted({dd for (g, dd) in by_group_date if g == grp and dd.weekday() == d.weekday()})
        wd_full = sum(1 for dd in wd_days
                      if not [h for h in range(min((h for h in (x[0] for x in by_group_date[(grp, dd)]) if h is not None), default=0),
                                               max((h for h in (x[0] for x in by_group_date[(grp, dd)]) if h is not None), default=0) + 1)
                              if h not in {x[0] for x in by_group_date[(grp, dd)]}])
        marker = "PIENO" if (hours and (not slot or at_slot > 0)) else "NON-PIENO"
        filled += marker == "PIENO"
        print(f"{did} {grp} {dstr} ({WEEKDAYS[d.weekday()]}) slot={slot or '-'}: golden {day_shape(hours)}"
              f" | @{slot or '-'}: {at_slot} lez | weekday {WEEKDAYS[d.weekday()]}: {len(wd_days)}gg ({wd_full} compatti) | -> {marker}")
    print(f"\nTOTALE: golden pieno su {filled}/{len(defects)} celle difetto")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        rows = []
        with open(sys.argv[1]) as fh:
            for line in fh:
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 3 and not parts[0].startswith("#"):
                    rows.append((parts[0], parts[1], parts[2], parts[3] if len(parts) > 3 else ""))
        main(rows)
    else:
        main(DEFAULT_DEFECTS)
