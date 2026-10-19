#!/usr/bin/env python3
"""golden-mining-r2.py - golden mining ROUND 2: four candidate patterns.

Direttiva maintainer 2026-08-15 (accelerare l'autonomia dal golden) + mandato Fedora
2026-08-16 pomeriggio: ogni pattern che il golden pratica e che riduce lo spazio
in modo utile e' patrimonio che sopravvive alla scomparsa del file. Round 1
(golden-mining.py, 2026-08-15) estrasse 5 pattern (2 confutati, fogli 22/23
proposti). Qui i 4 nuovi candidati:

  A. DISTRIBUZIONE INTRA-GIORNO: le materie hanno un ordine preferito dentro
     la giornata? Per (gruppo, materia): quota delle ore in slot 0-3 (mattina).
     Preferenza chiara = >60% in una meta'. Pattern dichiarabile se >70% delle
     coppie (gruppo, materia) ha preferenza chiara. Cross-tab teoria vs lab.
  B. RAGGRUPPAMENTI MENSILI: per (gruppo, materia), ore osservate per mese vs
     attesa = ore totali della materia x (ore attese del gruppo nel mese /
     ore attese totali). La base attesa usa i FOGLI CALENDARIO (input
     autoritativo: calendario_class_days.csv hours, calendario_fc.csv
     aula+lab), quindi i periodi di stage sono GIA' contabilizzati nell'attesa:
     un eccesso >2x e' oltre quota. Eccesso >2x con >=25% dell'anno in quel
     mese = candidato.
  C. COPPIE ADIACENTI: per (gruppo, coppia di materie), adiacenze osservate
     (slot consecutivi, stesso giorno) vs attesa casuale per giorno
     2*countA*countB/L (L = ore collocate quel giorno). >3x con >=10 casi = pattern.
  D. CARICO LUNEDI/VENERDI: per gruppo, ore medie osservate per weekday sui
     giorni attesi>0, vs attesa media dei fogli. Pratica dichiarabile se il
     venerdi' e' sistematicamente piu' leggero (>0.5h) DOPO la normalizzazione
     sull'attesa (i venerdi' corti sono INPUT: la finestra calendario e' piu'
     stretta; il residuo interessante e' il tasso di riempimento).

POPOLAZIONE (trappole golden escluse, protocollo CLAUDE.md sezione KPI):
righe closed, sabati (117 ore, di cui 112 Fasullo), docente/materia 'Fasullo',
pseudo Stage (fuori griglia per natura). Read-only: mai scrive, mai tocca il DB.
Exit 0 sempre.

Usage: ./scripts/analyze/golden-mining-r2.py [--golden path] [--top N]
"""
import argparse
import csv
import os
import re
import sys
from collections import Counter, defaultdict

DEFAULT_GOLDEN = os.path.join(os.path.dirname(__file__), "..", "..",
                              "src/main/resources/seed/lessons.csv")
SEED_DIR = os.path.join(os.path.dirname(__file__), "..", "..",
                        "src/main/resources/seed")
SLOT_RE = re.compile(r"^(\d{2}):(\d{2})")
WEEKDAYS = ["Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato"]
# theory/lab split copied from round 1 (golden-mining.py): same hints, one source
THEORY_HINT = ("lingua", "matematica", "storia", "scienze", "inglese", "diritto",
               "comunicazione", "educazione", "cittadinanza", "economia",
               "orientamento", "sicurezza", "sviluppo")
LAB_HINT = ("lavorazioni", "controllo numerico", "cad", "disegno", "taglio",
            "tecnologia meccanica", "metrologia", "confezione", "R16",
            "merceologia", "arredi", "tecnologia tessile", "moda ", "storia della moda",
            "personalizzazione", "competenze digitali", "informatica")
PSEUDO = {"Stage", "Fasullo"}


def load_rows(path):
    rows = []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r.get("closed", "False").lower() == "true":
                continue
            if r.get("day") == "Sabato":
                continue  # 117 golden Saturday hours: not placeable in our grid
            if r.get("teacher") in PSEUDO or r.get("subject") in PSEUDO:
                continue
            m = SLOT_RE.match(r["slot"] or "")
            if not m:
                continue
            rows.append({
                "date": r["date"],
                "weekday": r["day"],
                "slot": int(m.group(1)) - 8,
                "group": r["group"],
                "subject": r["subject"],
            })
    return rows


def load_expected_hours():
    """(group, date) -> expected aula+lab hours from the school's calendar sheets
    (authoritative input: class_days for 11 groups, fc split for the 3 FC)."""
    exp = {}
    with open(os.path.join(SEED_DIR, "calendario_class_days.csv"), newline="") as f:
        for r in csv.DictReader(f):
            # modality S rows are STAGE days: their hours are stage capacity, not
            # aula+lab (the fc sheet tracks stageHours separately). Counting them
            # here inflated the FC/FA expected baseline (2092h across 7 groups)
            # and depressed the concentration ratios (fixed 2026-08-16 sera).
            h = int(r["hours"] or 0)
            if h > 0 and r["modality"] != "S":
                exp[(r["group"], r["date"])] = h
    with open(os.path.join(SEED_DIR, "calendario_fc.csv"), newline="") as f:
        for r in csv.DictReader(f):
            h = int(r["aula_hours"] or 0) + int(r["lab_hours"] or 0)
            if h > 0:
                exp[(r["group"], r["date"])] = h
    return exp


def month_of(date_str):
    return date_str[:7]


def kind_of(subject):
    s = subject.lower()
    if any(h in s for h in THEORY_HINT):
        return "theory"
    if any(h in s for h in LAB_HINT):
        return "lab"
    return "other"


def section(title):
    print()
    print("=" * 78)
    print(title)
    print("=" * 78)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--golden", default=DEFAULT_GOLDEN)
    ap.add_argument("--top", type=int, default=12)
    args = ap.parse_args()
    rows = load_rows(args.golden)
    if not rows:
        sys.exit("no rows parsed from " + args.golden)
    expected = load_expected_hours()
    print(f"golden: {args.golden} ({len(rows)} lessons after trap filters)")
    print(f"calendar sheets: {len(expected)} (group, date) expected-days")

    # ── A. intra-day distribution ───────────────────────────────────────────
    section("A. INTRA-DAY: per (group, subject) morning share (slots 0-3)")
    hours_am = Counter()
    hours_tot = Counter()
    for r in rows:
        key = (r["group"], r["subject"])
        hours_tot[key] += 1
        if r["slot"] <= 3:
            hours_am[key] += 1
    eligible = {k: v for k, v in hours_tot.items() if v >= 20}
    clear = []
    for key, tot in sorted(eligible.items()):
        am = hours_am.get(key, 0) / tot
        if am > 0.6 or am < 0.4:
            clear.append((key, tot, am))
    n_clear = len(clear)
    n_all = len(eligible)
    print(f"(group, subject) pairs with >=20 placed hours: {n_all}")
    print(f"with a clear preference (>60% one half): {n_clear} "
          f"= {100*n_clear/max(n_all,1):.0f}%  [declarable bar: >70%]")
    bykind = defaultdict(lambda: [0, 0])
    for key, tot in eligible.items():
        k = kind_of(key[1])
        am = hours_am.get(key, 0) / tot
        bykind[k][0] += 1
        if am > 0.6 or am < 0.4:
            bykind[k][1] += 1
    for k in ("theory", "lab", "other"):
        tot, cl = bykind[k]
        print(f"  {k:7s}: {cl}/{tot} clear ({100*cl/max(tot,1):.0f}%)")
    print(f"\ntop {args.top} AM-leaning (theory expected here if the pattern is real):")
    am_sorted = sorted([c for c in clear if hours_am.get(c[0], 0) / c[1] > 0.6],
                       key=lambda c: -(hours_am.get(c[0], 0) / c[1]))
    for key, tot, am in am_sorted[:args.top]:
        print(f"  AM {100*am:3.0f}%  {key[0]:8s} {key[1][:34]:34s} (n={tot})")
    pm_sorted = sorted([c for c in clear if hours_am.get(c[0], 0) / c[1] < 0.4],
                       key=lambda c: (hours_am.get(c[0], 0) / c[1]))
    print(f"top {args.top} PM-leaning (lab expected here):")
    for key, tot, am in pm_sorted[:args.top]:
        print(f"  PM {100*(1-am):3.0f}%  {key[0]:8s} {key[1][:34]:34s} (n={tot})")

    # ── B. monthly concentration vs calendar-sheet expectation ──────────────
    section("B. MONTHLY: per (group, subject) observed vs calendar-expected share")
    obs_month = defaultdict(Counter)   # (group, subject) -> month -> hours
    tot_hours = Counter()
    for r in rows:
        key = (r["group"], r["subject"])
        obs_month[key][month_of(r["date"])] += 1
        tot_hours[key] += 1
    exp_month = defaultdict(Counter)   # group -> month -> expected hours
    for (g, d), h in expected.items():
        exp_month[g][month_of(d)] += h
    cands = []
    for key, tot in sorted(tot_hours.items()):
        if tot < 30:
            continue
        g, subj = key
        g_exp = exp_month.get(g)
        if not g_exp:
            continue
        year_exp = sum(g_exp.values())
        for m, obs in obs_month[key].items():
            exp_share = g_exp.get(m, 0) / year_exp
            if exp_share <= 0.02:
                continue
            expected_h = tot * exp_share
            if obs >= 2.0 * expected_h and obs >= 0.25 * tot:
                cands.append((obs / expected_h, key, m, obs, expected_h, tot))
    cands.sort(reverse=True)
    print(f"(group, subject) pairs >=30h: {sum(1 for v in tot_hours.values() if v >= 30)}")
    print(f"concentration candidates (>=2x expected AND >=25% of the year in that month): {len(cands)}")
    for ratio, key, m, obs, exp_h, tot in cands[:args.top]:
        print(f"  {ratio:4.1f}x  {key[0]:8s} {key[1][:30]:30s} {m}: "
              f"{obs}h obs vs {exp_h:.0f}h exp (year {tot}h)")

    # ── C. adjacent subject pairs vs random expectation ──────────────────────
    section("C. ADJACENCY: subject pairs in consecutive slots vs random expectation")
    by_day = defaultdict(list)  # (group, date) -> [(slot, subject)]
    for r in rows:
        by_day[(r["group"], r["date"])].append((r["slot"], r["subject"]))
    obs_adj = Counter()      # frozenset-ish key (A,B) sorted
    exp_adj = Counter()
    for (g, d), lessons in by_day.items():
        lessons.sort()
        subjects = [s for _, s in lessons]
        L = len(subjects)
        counts = Counter(subjects)
        for i in range(L - 1):
            a, b = subjects[i], subjects[i + 1]
            if a != b:
                obs_adj[tuple(sorted((a, b)))] += 1
        for a, ca in counts.items():
            for b, cb in counts.items():
                if a < b:
                    # under a random permutation of the day's L lessons:
                    # P(specific A next to specific B) ~ 2/L
                    exp_adj[(a, b)] += 2.0 * ca * cb / L
    print(f"distinct adjacent pairs observed: {len(obs_adj)}")
    pats = [(obs_adj[p] / e, p, obs_adj[p]) for p, e in exp_adj.items()
            if obs_adj.get(p, 0) >= 10 and e > 0]
    pats.sort(reverse=True)
    strong = [x for x in pats if x[0] > 3.0]
    print(f"pairs >3x expectation with >=10 cases: {len(strong)}  [pattern bar]")
    for ratio, p, obs in strong[:args.top]:
        print(f"  {ratio:4.1f}x  {p[0][:30]:30s} + {p[1][:30]:30s}  (n={obs})")

    # ── D. weekday load, observed vs expected ────────────────────────────────
    section("D. WEEKDAY LOAD: per group observed vs calendar-expected average")
    obs_wd = defaultdict(Counter)   # group -> weekday -> hours
    for r in rows:
        obs_wd[r["group"]][r["weekday"]] += 1
    exp_wd = defaultdict(Counter)   # group -> weekday -> expected hours
    for (g, d), h in expected.items():
        wd = weekday_of(d)
        if wd:
            exp_wd[g][wd] += h
    fri_lighter = 0
    n_groups = 0
    print("group      | Mon obs/exp | Fri obs/exp | Fri-Mon obs | fill Mon | fill Fri")
    for g in sorted(set(obs_wd) | set(exp_wd)):
        if g not in exp_wd or g not in obs_wd:
            continue
        n_groups += 1
        mon_o, mon_e = obs_wd[g]["Lunedì"], exp_wd[g]["Lunedì"]
        fri_o, fri_e = obs_wd[g]["Venerdì"], exp_wd[g]["Venerdì"]
        n_mon = sum(1 for (gg, d), h in expected.items() if gg == g and weekday_of(d) == "Lunedì")
        n_fri = sum(1 for (gg, d), h in expected.items() if gg == g and weekday_of(d) == "Venerdì")
        fill_mon = mon_o / mon_e if mon_e else 0
        fill_fri = fri_o / fri_e if fri_e else 0
        if fri_o / max(n_fri, 1) + 0.5 <= mon_o / max(n_mon, 1):
            fri_lighter += 1
        print(f"{g:10s} | {mon_o/max(n_mon,1):4.1f}/{mon_e/max(n_mon,1):4.1f} "
              f"| {fri_o/max(n_fri,1):4.1f}/{fri_e/max(n_fri,1):4.1f} "
              f"| {fri_o/max(n_fri,1)-mon_o/max(n_mon,1):+5.1f}      "
              f"| {100*fill_mon:4.0f}%     | {100*fill_fri:4.0f}%")
    print(f"\ngroups where Friday is >0.5h lighter than Monday (raw): {fri_lighter}/{n_groups}")


def weekday_of(date_str):
    """Italian weekday name for an ISO date, aligned with the golden's 'day' column."""
    import datetime
    it = ["Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"]
    try:
        d = datetime.date.fromisoformat(date_str)
    except ValueError:
        return None
    return it[d.weekday()]


if __name__ == "__main__":
    main()
