#!/usr/bin/env python3
"""golden-mining.py - measure candidate human patterns in the golden timetable.

Direttiva maintainer 2026-08-15 (accelerare l'autonomia): the golden is a real school
solution; patterns it practices that REDUCE the solution space usefully are input we
have not declared yet. This script MEASURES the candidates that are NOT already mined
(already mined: weekly cadence 21_CadenzaAttesa, theory blocks 22_TeoriaBlocco, slot-0
anchors TASK-451, co-presenza depth TASK-464, first-hour block TASK-451, teacher
fingerprint sharpness, hole topology).

Candidates measured here (each section prints a compact table + a space-reduction
estimate; see golden-mining-2026-08-15.md for the interpretation):
  1. block-length distribution per (group, subject): 1h vs 2h vs longer runs
  2. weekday-set pattern per group: which weekdays each group is full on
  3. intra-day subject order: theory in the morning vs lab in the afternoon
  4. fixed-day vs rotating subjects: is a (group, subject) always the same weekday(s)?
  5. shared-chair teachers: teachers teaching the same groups on the same weekdays

Usage: ./scripts/analyze/golden-mining.py [--golden path] [--top N]
  --golden  the school's golden lessons CSV (default seed/lessons.csv: date,day,slot,
            closed,group,teacher,subject; slot is an HH:MM range)
Read-only: never writes, never touches the DB. Exit 0 always.
"""
import argparse
import csv
import os
import re
import sys
from collections import Counter, defaultdict

DEFAULT_GOLDEN = os.path.join(os.path.dirname(__file__), "..", "..",
                              "src/main/resources/seed/lessons.csv")
# slot "08:00 09:00" -> ordinal 0..7 by start hour (8am = first slot of the day)
SLOT_RE = re.compile(r"^(\d{2}):(\d{2})")
THEORY_HINT = ("lingua", "matematica", "storia", "scienze", "inglese", "diritto",
               "comunicazione", "educazione", "cittadinanza", "economia",
               "orientamento", "sicurezza", "sviluppo")
LAB_HINT = ("lavorazioni", "controllo numerico", "cad", "disegno", "taglio",
            "tecnologia meccanica", "metrologia", "confezione", "R16",
            "merceologia", "arredi", "tecnologia tessile", "moda ", "storia della moda",
            "personalizzazione", "competenze digitali", "informatica")


def load_rows(path):
    rows = []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r.get("closed", "False").lower() == "true":
                continue
            m = SLOT_RE.match(r["slot"] or "")
            if not m:
                continue
            hour = int(m.group(1))
            rows.append({
                "date": r["date"],
                "weekday": r["day"],
                "slot": hour - 8,
                "group": r["group"],
                "teacher": r["teacher"],
                "subject": r["subject"],
            })
    return rows


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
    print(f"golden: {args.golden} ({len(rows)} lessons)")

    # ── 1. block-length distribution per (group, subject) ─────────────────────
    section("1. block length per (group, subject): contiguous same-subject runs")
    runs = Counter()
    by_gd = defaultdict(list)
    for r in rows:
        by_gd[(r["group"], r["date"])].append(r)
    for (g, d), lst in by_gd.items():
        lst.sort(key=lambda r: r["slot"])
        cur_sub, cur_len = None, 0
        for r in lst + [None]:
            sub = r["subject"] if r else None
            if sub == cur_sub:
                cur_len += 1
                continue
            if cur_sub is not None:
                runs[cur_len] += 1
            cur_sub, cur_len = sub, 1
    tot = sum(runs.values())
    print("run length | runs | share")
    for ln in sorted(runs):
        print(f"  {ln}h      | {runs[ln]:5d} | {100*runs[ln]/tot:5.1f}%")
    print(f"total runs {tot}; 1h share {100*runs[1]/tot:.1f}%, >=2h share "
          f"{100*sum(v for k,v in runs.items() if k>=2)/tot:.1f}%")
    per_pair = defaultdict(Counter)
    for (g, d), lst in by_gd.items():
        lst.sort(key=lambda r: r["slot"])
        cur_sub, cur_len = None, 0
        for r in lst + [None]:
            sub = r["subject"] if r else None
            if sub == cur_sub:
                cur_len += 1
                continue
            if cur_sub is not None:
                per_pair[(g, cur_sub)][cur_len] += 1
            cur_sub, cur_len = sub, 1
    print(f"\ntop {args.top} (group, subject) by 2h+ preference (pairs with >=20 runs):")
    ranked = []
    for pair, c in per_pair.items():
        n = sum(c.values())
        if n >= 20:
            two_plus = sum(v for k, v in c.items() if k >= 2)
            ranked.append((two_plus / n, pair, dict(sorted(c.items())), n))
    for frac, pair, dist, n in sorted(ranked, reverse=True)[:args.top]:
        print(f"  {pair[0]:8s} {pair[1][:28]:28s} 2h+ {100*frac:4.0f}%  dist {dist}  (n={n})")

    # ── 2. weekday-set pattern per group ──────────────────────────────────────
    section("2. weekday fullness per group (share of expected days fully filled)")
    wd = ["Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato"]
    present = defaultdict(set)
    for r in rows:
        present[r["group"]].add(r["weekday"])
    per_gw = defaultdict(list)
    for r in rows:
        per_gw[(r["group"], r["weekday"])].append(r)
    print("group      | " + " | ".join(f"{w[:3]:>6s}" for w in wd))
    groups = sorted(present)
    for g in groups:
        cells = []
        for w in wd:
            days = sorted({r['date'] for (gg, ww), _ in per_gw.items() if gg == g and ww == w
                           for r in per_gw[(gg, ww)]})
            n_days = len(days)
            n_les = len(per_gw.get((g, w), []))
            cells.append(f"{n_les//max(n_days,1):>2d}h/{n_days:2d}d")
        print(f"{g:10s} | " + " | ".join(f"{c:>6s}" for c in cells))

    # ── 3. intra-day order: theory morning vs lab afternoon ───────────────────
    section("3. intra-day subject order: theory vs lab by slot half")
    kind_counter = Counter()
    for r in rows:
        s = r["subject"].lower()
        kind = ("theory" if any(h in s for h in THEORY_HINT)
                else "lab" if any(h in s for h in LAB_HINT) else "other")
        half = "AM" if r["slot"] < 4 else "PM"
        kind_counter[(kind, half)] += 1
    print("kind   | AM (slots 0-3) | PM (slots 4-7)")
    for kind in ("theory", "lab", "other"):
        am = kind_counter[(kind, "AM")]
        pm = kind_counter[(kind, "PM")]
        print(f"{kind:6s} | {am:14d} | {pm:5d}  ({100*am/max(am+pm,1):.0f}% AM)")

    # ── 4. fixed-day vs rotating subjects ─────────────────────────────────────
    section("4. fixed weekday vs rotating per (group, subject)")
    pair_wd = defaultdict(Counter)
    for r in rows:
        pair_wd[(r["group"], r["subject"])][r["weekday"]] += 1
    fixed = rotating = 0
    fixed_examples = []
    for pair, c in pair_wd.items():
        n = sum(c.values())
        if n < 10:
            continue
        dominant_share = c.most_common(1)[0][1] / n
        n_wd = len(c)
        if n_wd == 1 or dominant_share >= 0.9:
            fixed += 1
            fixed_examples.append((dominant_share, n_wd, pair, n))
        else:
            rotating += 1
    print(f"pairs >=10 lessons: fixed(1 weekday or >=90% dominant) {fixed}, "
          f"rotating {rotating}")
    for share, n_wd, pair, n in sorted(fixed_examples, reverse=True)[:args.top]:
        print(f"  {pair[0]:8s} {pair[1][:30]:30s} dominant {100*share:3.0f}% on "
              f"{n_wd} weekday(s), n={n}")

    # ── 5. shared-chair teachers on same weekdays ─────────────────────────────
    section("5. shared teachers: same teacher, same group-set, same weekday")
    t_gd = defaultdict(set)     # teacher -> {(group, weekday)}
    t_hours = Counter()
    for r in rows:
        t_gd[r["teacher"]].add((r["group"], r["weekday"]))
        t_hours[r["teacher"]] += 1
    # teachers whose groups repeat on a weekday across MULTIPLE groups = the shared-chair
    tw = defaultdict(lambda: defaultdict(set))
    for t, gws in t_gd.items():
        for (g, w) in gws:
            tw[t][w].add(g)
    print("teacher        | weekday with >=2 groups (potential co-teaching day)")
    n_multi = 0
    for t in sorted(tw, key=lambda t: -t_hours[t]):
        hits = {w: gs for w, gs in tw[t].items() if len(gs) >= 2}
        if hits:
            n_multi += 1
            if n_multi <= args.top:
                pretty = ", ".join(f"{w[:3]}:{len(gs)}g" for w, gs in sorted(hits.items()))
                print(f"  {t[:14]:14s}| {pretty}   (total {t_hours[t]}h)")
    print(f"teachers with >=1 multi-group weekday: {n_multi} / {len(tw)}")


if __name__ == "__main__":
    main()
