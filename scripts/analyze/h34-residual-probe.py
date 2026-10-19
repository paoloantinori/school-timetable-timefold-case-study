#!/usr/bin/env python3
"""TASK-560: probe di curabilita' delle celle H34 residue (v3, 26/08 14:2x).

STORIA DELLE TRE VERSIONI (lezione di metodo registrata nel registro):
- v1 lesse il DB live del profilo banco: le candidate erano gonfiate da uno stato
  mutato dai save dei resume (1344 lezioni roomless: gate stanza vacuo);
- v2 passo ai checkpoint congelati ma ESCLUDEVA le lezioni Stage dall'occupazione
  di stanze/docenti: le stub Stage OCCUPANO (11364 in R08 a 04/19 h8 bloccava
  tre candidate che il probe dava libere);
- v3 (questa) conta Stage/pinned nell'OCCUPANZA (H1 e stanze), li esclude solo
  come CANDIDATE/donatori; e dichiara il limite strutturale residuo: NON modella
  la legalita' delle SLIDE del donatore (il pass la richiede: la traccia 26/14
  mostra le due candidate superstiti di 04/19 morte esattamente li).

Input: checkpoint JSON (placements), meta lezioni/timeslot/stanze (db-query
export), quote, vacation, finestre calendario. L'occupanza e' ricostruita dal
checkpoint: MAI dal DB live di un profilo su cui girano resume.
"""
import argparse
import datetime
from collections import defaultdict

FLOOR = defaultdict(lambda: 4)
FLOOR['G09'] = 2
EXCL = {'G04', 'G08', 'G14'}


def load(path):
    raw = open(path).read().strip()
    if raw.startswith('SET'):
        raw = raw.split('\n', 1)[1]
    return __import__('json').loads(raw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--checkpoint', required=True)
    ap.add_argument('--lesson-meta', required=True)
    ap.add_argument('--ts-meta', required=True)
    ap.add_argument('--rooms', required=True)
    ap.add_argument('--quota', required=True)
    ap.add_argument('--vacations', required=True)
    ap.add_argument('--calwin', required=True)
    args = ap.parse_args()

    meta = {r['id']: r for r in load(args.lesson_meta)}
    ts = {r['id']: r for r in load(args.ts_meta)}
    rooms = {r['id']: r['name'] for r in load(args.rooms)}
    quota = {}
    for r in load(args.quota):
        quota[(r['grp'], r['d'])] = r['a'] + r['l']
    vacations = load(args.vacations)
    calwin = load(args.calwin)

    ck = __import__('json').load(open(args.checkpoint))
    lessons = []
    for p in ck['placements']:
        if p.get('timeslotId') is None:
            continue
        m, t = meta.get(p['lessonId']), ts.get(p['timeslotId'])
        if not m or not t:
            continue
        lessons.append({'id': p['lessonId'], 'grp': m['grp'], 'sub': m['sub'],
                        'tea': m['tea'], 'd': t['d'], 'h': int(t['h']),
                        'pin': p.get('pinned', False),
                        'room': rooms.get(p.get('roomId'))})

    # OCCUPANCY includes Stage and every placed lesson (they hold teachers/rooms);
    # the CANDIDATE filters below exclude Stage/pinned as donors.
    teacher_occ = set()
    room_occ = set()
    by_group = defaultdict(list)
    day_hours = defaultdict(int)
    for l in lessons:
        teacher_occ.add((l['tea'], l['d'], l['h']))
        if l['room']:
            room_occ.add((l['room'], l['d'], l['h']))
        if l['sub'] == 'Stage':
            continue
        by_group[l['grp']].append(l)
        day_hours[(l['grp'], l['d'])] += 1

    vac = defaultdict(set)
    for v in vacations:
        for h in range(int(v['h1']), int(v['h2'])):
            vac[(v['tea'], v['d'])].add(h)
    win = defaultdict(set)
    for w in calwin:
        for s in range(w['mn'], w['mx'] + 1):
            win[(w['grp'], w['dow'])].add(s)

    print(f"{'cella':24s} {'cand':>5s} {'1a-ora':>7s}  (limiti dichiarati: "
          "no slide-legalita', no VRP per-corso)")
    tot_first = 0
    cells = 0
    for (g, d), q in sorted(quota.items()):
        if q <= 0 or g in EXCL or day_hours.get((g, d), 0) != 0:
            continue
        if not any(t['d'] == d for t in ts.values()):
            continue
        cells += 1
        dow = datetime.date.fromisoformat(d).weekday()
        slots = sorted(win.get((g, dow), set()))
        if not slots:
            print(f"{g:12s} {d}  finestra vuota!")
            continue
        first_h = slots[0] + 8
        cands = firsts = 0
        for L in by_group.get(g, []):
            if L['pin'] or L['d'] == d:
                continue
            if day_hours[(g, L['d'])] - 1 < max(quota.get((g, L['d']), 0), FLOOR[g]):
                continue
            ok = first_ok = False
            for s in slots:
                h = s + 8
                if (L['tea'], d, h) in teacher_occ:
                    continue
                if h in vac.get((L['tea'], d), ()):
                    continue
                if L['room'] and (L['room'], d, h) in room_occ:
                    continue
                ok = True
                if h == first_h:
                    first_ok = True
            if ok:
                cands += 1
                if first_ok:
                    firsts += 1
        if firsts:
            tot_first += 1
        print(f"{g:12s} {d}  {cands:5d} {firsts:7d}")
    print(f"\ncelle vuote dichiarate: {cells}; con prima-ora legale: {tot_first}")
    print("NOTA: le candidate qui sono a rischio SLIDE (la traccia 26/08 14:14 le ha"
          " viste morire sul docente occupato uno slot sotto nel donatore).")


if __name__ == '__main__':
    main()
