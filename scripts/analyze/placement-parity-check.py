#!/usr/bin/env python3
"""placement-parity-check.py - TASK-517: nessun PIANO piu' rigido della pratica del golden.

Chiusura della famiglia B della meta-indagine 17/08: la direttiva golden-first
(TASK-379) controlla le REGOLE, non i PIANI. Tre occorrenze storiche di piani
golden-derived applicati con piu' rigidita' della pratica: TASK-432 (giovedi'
monografici per-gruppo forzati al 100%), TASK-430 (ancore slot-0 imposte al 100%
dove il golden le pratica al 99,86%), chain round 2-6 (pin lab a cap H7 dove il
golden tiene 4h su 6 nei giorni con RA).

Tre assi, tutti per confronto PIANO (lezioni PINNATE del run/plan) vs GOLDEN:
  A. densita' per-weekday della coppia (docente, gruppo): il piano e' flagged
     quando e' ovunque (tutti i weekday comuni) piu' denso del golden di >= 1h
     medio/giorno, con >= 3 giorni per lato.
  B. ancora slot-0 per (gruppo, weekday): flagged quando il piano ancora al 100%
     dei giorni dove il golden sta sotto il 99,5% (>= 10 giorni golden).
  C. concentrazione (gruppo, materia) su un solo weekday: flagged quando il
     golden spalma su >= 2 weekday (ognuno >= 15% delle ore) e il piano ne usa
     uno solo per > 95% delle ore.

Calibrazione (AC#4): il golden contro se stesso NON deve produrre flag; i piani
ratificati (import calendario) non passano da qui perche' non aggiungono pin
oltre il golden. Validazione storica (AC#3): --self-test perturba il golden nelle
tre firme storiche (saturazione a cap dei giorni RA, ancora slot-0 al 100%,
concentrazione monografica) e verifica che gli assi scattino; l'asse A e'
inoltre validato sul piano REALE della chain (run4, pin lab a cap).

Read-only, exit 0 (il check e' un REPORT; l'adozione decide maintainer).

Usage:
  ./scripts/analyze/placement-parity-check.py --plan <export.json.gz> [--golden path]
  ./scripts/analyze/placement-parity-check.py --reserve-plan <journal.txt> [--golden path]
  ./scripts/analyze/placement-parity-check.py --self-test

--reserve-plan consumes the DeclaredDayPreBooker v2.1 journal directly
(target/reserve-plan.txt, pipe format group|date|slotIndex|subject|teacher,
each line one RESERVED placement treated as a pinned plan lesson): the
TASK-564 refinement loop's own input, no export conversion in between.
"""
import argparse
import gzip
import json
import os
import sys
from collections import defaultdict

DEFAULT_GOLDEN = os.path.join(os.path.dirname(__file__), "..", "..",
                              "src/main/resources/seed/lessons.csv")
PSEUDO = {"Stage", "Fasullo"}
WEEKDAYS = [0, 1, 2, 3, 4]  # Mon..Fri ISO weekday


def load_golden(path):
    """(teacher, group, date) -> hours, plus first-slot map for (group, date)."""
    import csv
    import datetime
    pair_hours = defaultdict(int)          # (teacher, group, date) -> h
    subj_hours = defaultdict(int)          # (group, subject, date) -> h
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r.get("closed", "False").lower() == "true":
                continue
            if r.get("day") == "Sabato":
                continue
            if r.get("teacher") in PSEUDO or r.get("subject") in PSEUDO:
                continue
            d = datetime.date.fromisoformat(r["date"])
            if d.weekday() >= 5:
                continue
            pair_hours[(r["teacher"], r["group"], d)] += 1
            subj_hours[(r["group"], r["subject"], d)] += 1
    return pair_hours, subj_hours


def load_plan_export(path):
    """Pinned lessons of a run export: same shapes as the golden loader."""
    import datetime
    with gzip.open(path, "rt") as f:
        d = json.load(f)
    pair_hours = defaultdict(int)
    subj_hours = defaultdict(int)
    group_first_slot = defaultdict(list)   # (group, date) -> [slot]
    for l in d.get("lessons", []):
        ts = l.get("timeslot")
        if not ts or not l.get("pinned"):
            continue                       # the PLAN is the imposed part
        course = l.get("course") or {}
        t = (l.get("teacher") or course.get("teacher") or {}).get("name")
        g = (l.get("studentGroup") or course.get("studentGroup") or {}).get("name")
        subj = l.get("subject") or course.get("subject")
        if not t or not g or t in PSEUDO or subj in PSEUDO:
            continue
        d = datetime.date.fromisoformat(ts["date"][:10])
        slot = int(ts["startDateTime"][11:13]) - 8
        pair_hours[(t, g, d)] += 1
        subj_hours[(g, subj, d)] += 1
        group_first_slot[(g, d)].append(slot)
    return pair_hours, subj_hours, group_first_slot


def load_reserve_journal(path):
    """DeclaredDayPreBooker v2.1 reserve journal (group|date|slotIndex|subject|
    teacher, one line = one reserved placement): every line IS the imposed plan."""
    import datetime
    pair_hours = defaultdict(int)
    subj_hours = defaultdict(int)
    group_first_slot = defaultdict(list)
    with open(path) as f:
        for line in f:
            parts = line.strip().split("|")
            if len(parts) < 5:
                continue
            g, datestr, slot, subj, t = parts[:5]
            if t in PSEUDO or subj in PSEUDO:
                continue
            d = datetime.date.fromisoformat(datestr)
            s = int(slot)
            pair_hours[(t, g, d)] += 1
            subj_hours[(g, subj, d)] += 1
            group_first_slot[(g, d)].append(s)
    return pair_hours, subj_hours, group_first_slot


def golden_first_slots(path):
    import csv
    import datetime
    per_day = defaultdict(list)
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r.get("closed", "False").lower() == "true":
                continue
            if r.get("day") == "Sabato" or r.get("teacher") in PSEUDO:
                continue
            d = datetime.date.fromisoformat(r["date"])
            if d.weekday() >= 5:
                continue
            per_day[(r["group"], d)].append(int(r["slot"][:2]) - 8)
    return per_day


def axis_a(pair_hours_plan, pair_hours_golden):
    """(teacher, group): plan denser than golden on EVERY common weekday."""
    def by_pair_weekday(hours):
        agg = defaultdict(lambda: defaultdict(list))
        for (t, g, d), h in hours.items():
            agg[(t, g)][d.weekday()].append(h)
        return agg
    plan = by_pair_weekday(pair_hours_plan)
    gold = by_pair_weekday(pair_hours_golden)
    flags = []
    for pair in plan:
        if pair not in gold:
            continue
        common = [wd for wd in plan[pair] if wd in gold[pair]
                  and len(plan[pair][wd]) >= 3 and len(gold[pair][wd]) >= 3]
        if len(common) < 1:
            continue
        deltas = [sum(plan[pair][wd]) / len(plan[pair][wd])
                  - sum(gold[pair][wd]) / len(gold[pair][wd]) for wd in common]
        if all(delta >= 1.0 for delta in deltas):
            flags.append(("A", "pair %s/%s denser on %s (avg %s h/day over golden)" % (
                pair[0], pair[1], common,
                ["%+.1f" % d for d in deltas])))
    return flags


def axis_b(plan_first, golden_first):
    """(group, weekday): plan anchors slot 0 at 100% where golden is below 99.5%."""
    def rate(per_day):
        agg = defaultdict(list)
        for (g, d), slots in per_day.items():
            agg[(g, d.weekday())].append(min(slots) == 0)
        return agg
    plan = rate(plan_first)
    gold = rate(golden_first)
    flags = []
    for key, vals in plan.items():
        if len(vals) < 10:
            continue
        gvals = gold.get(key, [])
        if len(gvals) < 10:
            continue
        p_rate = sum(vals) / len(vals)
        g_rate = sum(gvals) / len(gvals)
        if p_rate >= 0.999 and g_rate < 0.995:
            flags.append(("B", "group %s weekday %d slot0 anchored %.0f%%"
                          " vs golden %.1f%%" % (key[0], key[1] + 1,
                                                 100 * p_rate, 100 * g_rate)))
    return flags


def axis_c(subj_plan, subj_golden):
    """(group, subject): plan concentrates on one weekday a subject the golden spreads."""
    def spread(hours):
        agg = defaultdict(lambda: defaultdict(int))
        for (g, s, d), h in hours.items():
            agg[(g, s)][d.weekday()] += h
        return agg
    plan = spread(subj_plan)
    gold = spread(subj_golden)
    flags = []
    for key, by_wd in gold.items():
        total = sum(by_wd.values())
        if total < 20:
            continue
        major = [wd for wd, h in by_wd.items() if h >= 0.15 * total]
        if len(major) < 2:
            continue                      # golden already mono-weekday
        pby = plan.get(key)
        if not pby:
            continue
        ptotal = sum(pby.values())
        if ptotal < 20:
            continue
        pmax = max(pby.values()) / ptotal
        if pmax > 0.95:
            flags.append(("C", "group %s subject '%s' plan %.0f%% on one weekday"
                          " vs golden spread %s" % (key[0], key[1], 100 * pmax,
                                                    {wd + 1: h for wd, h in by_wd.items()})))
    return flags


def run_check(golden_path, plan_pair=None, plan_subj=None, plan_first=None,
              golden_pair=None, golden_subj=None, golden_first=None):
    flags = []
    if plan_pair is not None:
        flags += axis_a(plan_pair, golden_pair)
    if plan_first is not None:
        flags += axis_b(plan_first, golden_first)
    if plan_subj is not None:
        flags += axis_c(plan_subj, golden_subj)
    return flags


def self_test(golden_path):
    """AC#3: perturb the golden into the three historical signatures and
    verify each axis fires; the untouched golden must fire NOTHING (AC#4)."""
    gpair, gsubj = load_golden(golden_path)
    gfirst = golden_first_slots(golden_path)
    base = run_check(golden_path, gpair, gsubj, gfirst, gpair, gsubj, gfirst)
    assert not base, "AC#4 violated: golden self-check flagged %s" % base[:3]

    import datetime

    # Signature chain r2-6 (axis A): saturate T15/G04 lab days to 6h.
    sat = defaultdict(int, gpair)
    days = sorted({d for (t, g, d), h in gpair.items() if t == "T15" and g == "G04"})
    for d in days:
        sat[("T15", "G04", d)] = 6
    a = axis_a(sat, gpair)
    assert any("T15/G04" in msg for _, msg in a), "axis A did not fire: %s" % a

    # Signature TASK-430 (axis B): anchor slot 0 on 100% of G01 Mondays.
    mono = defaultdict(list)
    for (g, d), slots in gfirst.items():
        mono[(g, d)] = list(slots)
    mondays = sorted(d for (g, d) in mono if g == "G01" and d.weekday() == 0)
    for d in mondays:
        mono[("G01", d)][0] = 0
    b = axis_b(mono, gfirst)
    assert any("G01" in msg for _, msg in b), "axis B did not fire: %s" % b

    # Signature TASK-432 (axis C): concentrate a spread subject on one weekday.
    conc = defaultdict(int, gsubj)
    target = None
    agg = defaultdict(lambda: defaultdict(int))
    for (g, s, d), h in gsubj.items():
        agg[(g, s)][d.weekday()] += h
    for key, by_wd in agg.items():
        total = sum(by_wd.values())
        if total >= 40 and len([h for h in by_wd.values() if h >= 0.15 * total]) >= 2:
            target = (key, max(by_wd, key=by_wd.get))
            break
    assert target, "no spread subject found in the golden for the C signature"
    (g, s), wd = target
    for (gg, ss, d), h in list(gsubj.items()):
        if (gg, ss) == (g, s) and d.weekday() != wd:
            del conc[(gg, ss, d)]
            conc[(gg, ss, d + datetime.timedelta(days=(wd - d.weekday()) % 7))] += h
    c = axis_c(conc, gsubj)
    assert any(g in msg and s in msg for _, msg in c), "axis C did not fire: %s" % c

    print("SELF-TEST OK: golden self-check clean (0 flags); signatures fired:")
    for axis, msg in a[:1] + b[:1] + c[:1]:
        print("  [%s] %s" % (axis, msg))
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--golden", default=DEFAULT_GOLDEN)
    ap.add_argument("--plan", help="run export.json.gz (pinned lessons = the plan)")
    ap.add_argument("--reserve-plan",
                    help="DeclaredDayPreBooker v2.1 journal (reserved placements = the plan)")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        sys.exit(self_test(args.golden))

    gpair, gsubj = load_golden(args.golden)
    gfirst = golden_first_slots(args.golden)
    if not args.plan and not args.reserve_plan:
        # golden self-check is the calibration reference
        flags = run_check(args.golden, gpair, gsubj, gfirst, gpair, gsubj, gfirst)
        print("golden self-check (calibration): %d flag(s)" % len(flags))
        for axis, msg in flags:
            print("  [%s] %s" % (axis, msg))
        sys.exit(0)

    if args.reserve_plan:
        ppair, psubj, pfirst = load_reserve_journal(args.reserve_plan)
        plan_name = args.reserve_plan
    else:
        ppair, psubj, pfirst = load_plan_export(args.plan)
        plan_name = args.plan
    flags = run_check(args.golden, ppair, psubj, pfirst, gpair, gsubj, gfirst)
    print("placement-parity check: %d flag(s) for plan %s" % (len(flags), plan_name))
    for axis, msg in flags:
        print("  [%s] %s" % (axis, msg))
    sys.exit(0)


if __name__ == "__main__":
    main()
