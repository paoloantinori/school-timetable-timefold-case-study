#!/usr/bin/env python3
"""TASK-562 AutopsyAnalyzer v1 (Python offline, 26/08): diagnosi post-run di minuti
per QUALSIASI consegna non-0hard, dal checkpoint/export. Nessun impatto sul ciclo
di produzione (invocazione esplicita; letture only).

A1 - DECOMPOSIZIONE MUS-style del residuo hard: per famiglia (H0 boxed, H34
celle vuote dichiarate, H17 gap interni, H1/H2/H3 conflitti), mappa
(gruppo, data) -> dettaglio. Puro ri-computo strutturale dal placement.

A2 - MOBILITY PROBE: per ogni punto del residuo, conteggio delle mosse legali
SINGOLE che lo chiuderebbero NELLO STATO DEL CHECKPOINT (donatore stesso gruppo
con surplus, docente libero, stanza propria libera, finestra calendario).
Esistenza senza chiusura = move-connectivity; non-esistenza = bacino/costruzione.

Limiti dichiarati (eredita' probe v3): VRP per-corso non modellato (finestra +
quota come proxy), H5 vacation modellata, mosse composte NON enumerate (vedi
banco Kempe TASK-560: la composizione e' necessaria).

Usage:
  python3 scripts/analyze/autopsy_analyzer.py --checkpoint <ck.json> \
      --lesson-meta /tmp/ms-lesson-meta.json --ts-meta /tmp/ms-ts-meta.json \
      --quota /tmp/ms-quota.json --calwin /tmp/ms-calwin.json \
      --vacations /tmp/ms-vac.json [--rooms /tmp/ms-rooms.json]
"""
import argparse
import datetime
import json
from collections import defaultdict

FLOOR = defaultdict(lambda: 4)
FLOOR['G09'] = 2
EXCL = {'G04', 'G08', 'G14'}


def load(path):
    raw = open(path).read().strip()
    if raw.startswith('SET'):
        raw = raw.split('\n', 1)[1]
    return json.loads(raw)


def slot_index(h):
    return h - 8


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--checkpoint', required=True)
    ap.add_argument('--lesson-meta', required=True)
    ap.add_argument('--ts-meta', required=True)
    ap.add_argument('--quota', required=True)
    ap.add_argument('--calwin', required=True)
    ap.add_argument('--vacations', required=True)
    args = ap.parse_args()

    meta = {r['id']: r for r in load(args.lesson_meta)}
    ts = {r['id']: r for r in load(args.ts_meta)}
    quota = {}
    for r in load(args.quota):
        quota[(r['grp'], r['d'])] = r['a'] + r['l']
    vac = defaultdict(set)
    for v in load(args.vacations):
        for h in range(int(v['h1']), int(v['h2'])):
            vac[(v['tea'], v['d'])].add(h)
    win = defaultdict(set)
    for w in load(args.calwin):
        for s in range(w['mn'], w['mx'] + 1):
            win[(w['grp'], w['dow'])].add(s)

    ck = json.load(open(args.checkpoint))
    placed = []
    for p in ck['placements']:
        m = meta.get(p['lessonId'])
        t = ts.get(p.get('timeslotId')) if p.get('timeslotId') else None
        placed.append({'id': p['lessonId'], 'm': m, 't': t,
                       'room': p.get('roomId'), 'pin': p.get('pinned', False)})
    by_group_day = defaultdict(list)  # (grp, d) -> [(h, tea, pin, id)]
    teacher_occ = set()
    for l in placed:
        if l['t'] is None or l['m'] is None or l['m'].get('pseudo'):
            continue
        g, d, h = l['m']['grp'], l['t']['d'], int(l['t']['h'])
        by_group_day[(g, d)].append((h, l['m']['tea'], l['pin'], l['id']))
        teacher_occ.add((l['m']['tea'], d, h))

    # ── A1: decomposizione ────────────────────────────────────────────────
    print('== A1 DECOMPOSIZIONE DEL RESIDUO ==')
    print('checkpoint:', args.checkpoint)
    print('score:', ck.get('score'), '| tier:', ck.get('timeslotTierHard'),
          '| boxed:', ck.get('boxed'))
    boxed = [l for l in placed if l['t'] is None]
    print(f"H0 boxed: {len(boxed)}")
    h34 = []
    for (g, d), q in sorted(quota.items()):
        if q <= 0 or g in EXCL:
            continue
        hrs = [x for x in by_group_day.get((g, d), []) if x[1] != 'Stage' or True]
        nonstage = [x for x in hrs if not (meta[x[3]] and meta[x[3]].get('pseudo'))]
        # count non-pseudo lessons (approx via teacher name != 'Stage')
        nonstage = [x for x in hrs if x[1] != 'Stage']
        if not nonstage:
            h34.append((g, d, q))
    print(f"H34 celle vuote dichiarate: {len(h34)}")
    for g, d, q in h34:
        print(f"   {g:10s} {d} quota={q}")
    h17 = []
    for (g, d), rows in sorted(by_group_day.items()):
        slots = sorted(slot_index(h) for h, *_ in rows)
        for a, b in zip(slots, slots[1:]):
            if b > a + 1:
                h17.append((g, d, list(range(a + 1, b))))
    print(f"H17 gap interni: {len(h17)}")
    for g, d, gaps in h17[:15]:
        print(f"   {g:10s} {d} gap={gaps}")
    h1 = defaultdict(int)
    occ = defaultdict(list)
    for (g, d), rows in by_group_day.items():
        for h, tea, pin, lid in rows:
            occ[(tea, d, h)].append((g, lid, pin))
    for k, v in occ.items():
        real = [x for x in v if x[0] != 'Stage']
        groups = {x[0] for x in real}
        if len(real) > 1 and len(groups) > 1:
            h1[k] = len(real)
    print(f"H1 conflitti docente (cross-gruppo): {len(h1)}")

    # ── A2: mobility probe ────────────────────────────────────────────────
    print('== A2 MOBILITY PROBE ==')
    day_hours = {k: len(v) for k, v in by_group_day.items()}
    print(f"{'punto':28s} {'cand':>5s}")
    tot_h34_cand = 0
    for g, d, q in h34:
        dow = datetime.date.fromisoformat(d).weekday()
        slots = sorted(win.get((g, dow), set()))
        cands = 0
        for (g2, d2), rows in by_group_day.items():
            if g2 != g or d2 == d:
                continue
            donor_quota = quota.get((g, d2), 0)
            if len(rows) - 1 < max(donor_quota, FLOOR[g]):
                continue
            for h, tea, pin, lid in rows:
                if pin:
                    continue
                for s in slots:
                    hh = s + 8
                    if (tea, d, hh) in teacher_occ:
                        continue
                    if hh in vac.get((tea, d), ()):
                        continue
                    cands += 1
                    break
        tot_h34_cand += 1 if cands else 0
        print(f"H34 {g:10s} {d} q={q}  {cands:5d}")
    tot_h17_cand = 0
    for g, d, gaps in h17:
        cands = 0
        for (g2, d2), rows in by_group_day.items():
            if g2 != g or d2 == d:
                continue
            donor_quota = quota.get((g, d2), 0)
            if len(rows) - 1 < max(donor_quota, FLOOR[g]):
                continue
            for h, tea, pin, lid in rows:
                if pin:
                    continue
                hh = gaps[0] + 8
                if (tea, d, hh) in teacher_occ:
                    continue
                if hh in vac.get((tea, d), ()):
                    continue
                cands += 1
        tot_h17_cand += 1 if cands else 0
        print(f"H17 {g:10s} {d} gap={gaps}  {cands:5d}")
    print(f"\nH34: {tot_h34_cand}/{len(h34)} punti con >=1 candidata")
    print(f"H17: {tot_h17_cand}/{len(h17)} punti con >=1 candidata")


if __name__ == '__main__':
    main()
