#!/usr/bin/env python3
"""TASK-558: M22 quota-aware min-days amendment measurement (spike, NOT adoption).

Replicates the M22 constraint's population on both sides and reports each group's
excess days under the two min-days forms:

  legacy   = ceil(placedHours / maxHoursPerDay)        [current]
  amended  = ceil(placedHours / avg declared aula+lab) [TASK-558 proposal]

Population (the constraint's own): every lesson counts, EXCEPT G14 (fillExempt)
and, on the run side, authoritative pins (promoted pins DO count). Stage hours
count too (the constraint has no pseudo filter). The golden side is the
"unpinned-equivalent" of the 25/08 analysis (the constraint is vacuous on the
100%-pinned golden import): all rows, Saturdays and Fasullo excluded by protocol.

Inputs are FILES (house style, no live DB):
  --golden    seed/lessons.csv                          (default)
  --quota     JSON from: db-query.sh "SELECT ... FROM dailyactivityquota ..."
              (regen: see docstring bottom)
  --maxhpd    JSON from: db-query.sh "SELECT name, maxhoursperday FROM studentgroup"
  --run-checkpoint POST_B_ROOM.json  (optional run side)
  --lesson-meta /tmp/lesson-meta.json (required with --run-checkpoint;
              db-query.sh "SELECT l.id, group, subject FROM lesson JOIN course ...")
  --ts-meta   /tmp/ts-meta.json        (timeslot id -> date, hour)

Usage:
  python3 scripts/analyze/m22-quota-aware.py --quota /tmp/quota-meta.json \
      --maxhpd /tmp/maxhpd.json
"""
import argparse, csv, datetime, json, math
from collections import defaultdict

def load_json(path):
    raw = open(path).read().strip()
    if raw.startswith("SET"):  # db-query.sh tuples output carries a leading SET line
        raw = raw.split("\n", 1)[1]
    return json.loads(raw)


def ceil_div(hours, quota):
    return math.ceil(hours / quota)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--golden", default="src/main/resources/seed/lessons.csv")
    ap.add_argument("--quota", required=True)
    ap.add_argument("--maxhpd", required=True)
    ap.add_argument("--run-checkpoint")
    ap.add_argument("--lesson-meta")
    ap.add_argument("--ts-meta")
    args = ap.parse_args()

    quota_rows = load_json(args.quota)
    maxhpd = {r["grp"]: r["mhp"] for r in load_json(args.maxhpd)}
    DEFAULT_QUOTA = 6

    # declared average ordinary quota per group (aula+lab > 0 rows only)
    avg_declared = {}
    per_group_rows = defaultdict(list)
    for r in quota_rows:
        per_group_rows[r["grp"]].append(r["a"] + r["l"])
    for g, vals in per_group_rows.items():
        pos = [v for v in vals if v > 0]
        if pos:
            avg_declared[g] = sum(pos) / len(pos)

    def report(side, hours_by_group_days, note):
        print(f"\n=== {side} ({note}) ===")
        print(f"{'gruppo':10s} {'ore':>5s} {'gg':>4s} {'maxHPD':>7s} {'avgDich':>8s}"
              f" {'minLeg':>7s} {'minEme':>7s} {'eccLeg':>7s} {'eccEme':>7s}")
        tot = [0, 0]
        for g in sorted(hours_by_group_days):
            if g == "G14":
                continue
            days = hours_by_group_days[g]
            hours = sum(days.values())
            used = len(days)
            quota = maxhpd.get(g) or DEFAULT_QUOTA
            avg = avg_declared.get(g)
            m_leg = ceil_div(hours, quota)
            m_eme = ceil_div(hours, avg) if avg else m_leg
            e_leg, e_eme = max(0, used - m_leg), max(0, used - m_eme)
            tot[0] += e_leg
            tot[1] += e_eme
            print(f"{g:10s} {hours:5d} {used:4d} {quota:7d} {avg if avg else -1:8.2f}"
                  f" {m_leg:7d} {m_eme:7d} {e_leg:7d} {e_eme:7d}")
        print(f"TOTALE ecc giorni: legacy={tot[0]}  emendato={tot[1]}"
              f"  (medium-equivalente x1000: {tot[0]*1000} -> {tot[1]*1000})")

    # golden side: unpinned-equivalent (Saturdays + Fasullo excluded, Stage INCLUDED)
    golden_days = defaultdict(lambda: defaultdict(int))
    golden_days_ordonly = defaultdict(lambda: defaultdict(int))
    for r in csv.DictReader(open(args.golden)):
        dt = datetime.date.fromisoformat(r["date"][:10])
        if dt.weekday() == 5 or r["teacher"] == "Fasullo":
            continue
        golden_days[r["group"]][r["date"][:10]] += 1
        if r["subject"] != "Stage":
            golden_days_ordonly[r["group"]][r["date"][:10]] += 1
    report("GOLDEN unpinned-equivalente", golden_days,
           "tutte le lezioni incl. Stage; sabati/Fasullo esclusi da protocollo")
    report("GOLDEN variante solo-ordinarie", golden_days_ordonly,
           "cross-check vs i numeri dell'analisi 25/08")

    if args.run_checkpoint:
        meta = {r["id"]: r for r in load_json(args.lesson_meta)}
        ts = {r["id"]: r for r in load_json(args.ts_meta)}
        run_days = defaultdict(lambda: defaultdict(int))
        for p in load_json(args.run_checkpoint)["placements"]:
            if p.get("timeslotId") is None:
                continue
            if p.get("pinned", False) and not p.get("promoted", False):
                continue  # authoritative pin: exempt (promoted pins DO count, TASK-419)
            m, t = meta.get(p["lessonId"]), ts.get(p["timeslotId"])
            if not m or not t:
                continue
            run_days[m["grp"]][t["d"]] += 1
        report("RUN (checkpoint)", run_days,
               "popolazione del vincolo: unpinned + promoted, G14 escluso")


if __name__ == "__main__":
    main()
