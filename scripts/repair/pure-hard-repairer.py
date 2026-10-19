#!/usr/bin/env python3
"""TASK-542 iter-24-bis: PURE hard repairer, no Timefold (mandato maintainer 23/08 16:00).

v2 (23/08 16:15): the first run exposed that scoring H1/H5 as soft-totals lets the
search chase pseudo-teacher co-presence (legal) and vacation blocks instead of holes.
Redesign: SCORE = number of internal hole slots ONLY; H1/H3/H2/H5 are MOVE GUARDS
(never create one for a moved lesson; pseudo teachers excluded from the busy/vacation
indexes - their co-presence is legal); H31 guarded not-worsened; H32/H33 INVARIANT BY
CONSTRUCTION of the move families: (a) intra-day pull-up (same day, same room, same
per-day count), (b) blocker-relocate + pull-up (blocker moves within ITS day, counts
preserved), (c) cross-day swap of two lessons of the SAME group (each day keeps its
hour count). No VRP/H20/H23/H24/H28 modelling: the ORACLE (import + analyzeScore) is
the final judge - declared risk in iterations-542.md.

Usage: pure-hard-repairer.py <export.json.gz> [--out repaired.json.gz] [--max-moves N]
"""
import argparse
import collections
import gzip
import json
import sys
from datetime import date, timedelta


def load(path):
    return json.load(gzip.open(path) if path.endswith('.gz') else open(path))


class Grid:
    def __init__(self, data):
        self.data = data
        self.lessons = {l['id']: l for l in data['lessons']}
        bydate = collections.defaultdict(set)
        for t in data['timeslots']:
            bydate[t['date']].add(t['startDateTime'][11:16])
        rank = {d: {tm: i for i, tm in enumerate(sorted(xs))} for d, xs in bydate.items()}
        self.key = {t['id']: (t['date'], rank[t['date']][t['startDateTime'][11:16]])
                    for t in data['timeslots']}
        self.ts_by_key = {v: t for t in data['timeslots'] for v in [self.key[t['id']]]}
        # pseudo teachers: their co-presence and vacations are not constraints here
        self.pseudo = set()
        for l in data['lessons']:
            t = ((l.get('course') or {}).get('teacher') or {})
            if t.get('pseudo') and t.get('name'):
                self.pseudo.add(t['name'])
        self.vac = collections.defaultdict(set)
        for ta in data.get('teacherAvailabilities') or []:
            if ta.get('type') != 'VACATION':
                continue
            t = (ta.get('teacher') or {}).get('name')
            if not t or t in self.pseudo:
                continue
            d = date.fromisoformat(ta['startDateTime'][:10])
            end = date.fromisoformat(ta['endDateTime'][:10])
            while d <= end:
                self.vac[t].add(d.isoformat())
                d += timedelta(days=1)
        self.teacher_busy = collections.defaultdict(set)
        self.group_busy = collections.defaultdict(set)
        self.room_busy = collections.defaultdict(set)
        self.day = collections.defaultdict(dict)  # (group,date) -> {slot: lessonId}
        self.tname = {}
        self.gname = {}
        for l in self.lessons.values():
            c = l.get('course') or {}
            t = (c.get('teacher') or {}).get('name')
            g = (c.get('studentGroup') or {}).get('name')
            self.tname[l['id']] = t
            self.gname[l['id']] = g
            if l.get('timeslot') is None:
                continue
            k = self.key[l['timeslot']['id']]
            if t and t not in self.pseudo:
                self.teacher_busy[k].add(t)
            if g:
                self.group_busy[k].add(g)
                self.day[(g, k[0])][k[1]] = l['id']
            if l.get('room'):
                self.room_busy[k].add(l['room']['id'])

    def teacher(self, lid):
        return self.tname[lid]

    def group(self, lid):
        return self.gname[lid]

    def holes(self):
        out = []
        for gd, slots in self.day.items():
            if len(slots) < 2:
                continue
            lo, hi = min(slots), max(slots)
            for s in range(lo, hi + 1):
                if s not in slots:
                    out.append((gd, s))
        return out

    def movable(self, lid):
        l = self.lessons[lid]
        return not l.get('pinned') and not l.get('promotedPin')

    def cell_ok(self, lid, ts, ignore=()):
        """True if lesson lid can sit at timeslot ts without creating H1/H3/H2/H5.
        `ignore` = lesson ids treated as absent (self, swap partner)."""
        k = self.key[ts['id']]
        t, g = self.teacher(lid), self.group(lid)
        l = self.lessons[lid]
        if t and t not in self.pseudo:
            busy = set()
            for other, oid in ((self.teacher_busy[k], None),):
                busy = other
            for oid2 in self.day_occupants_teacher(k, t):
                if oid2 not in ignore and oid2 != lid:
                    return False, f'H1 {t} occupato in {k}'
        if g and g in self.group_busy[k]:
            occ = self.day[(g, k[0])].get(k[1])
            if occ is not None and occ not in ignore and occ != lid:
                return False, f'H3 {g} occupato in {k}'
        if t and t not in self.pseudo and k[0] in self.vac.get(t, ()):
            return False, f'H5 {t} vacanza {k[0]}'
        if l.get('room'):
            if l['room']['id'] in self.room_busy[k]:
                holder = self.room_holder.get((l['room']['id'], k))
                if holder is not None and holder not in ignore and holder != lid:
                    return False, f'H2 stanza occupata in {k}'
        return True, ''

    def day_occupants_teacher(self, k, t):
        return [lid for lid in self.tcell.get((t, k), ())]

    # simpler occupancy: rebuild per (teacher,cell), (room,cell)
    def _build(self):
        self.tcell = collections.defaultdict(list)
        self.room_holder = {}
        for l in self.lessons.values():
            if l.get('timeslot') is None:
                continue
            k = self.key[l['timeslot']['id']]
            t = self.teacher(l['id'])
            if t and t not in self.pseudo:
                self.tcell[(t, k)].append(l['id'])
            if l.get('room'):
                self.room_holder[(l['room']['id'], k)] = l['id']
        # group occupancy lives in self.day

    def move(self, lid, ts):
        """Move lesson lid to timeslot ts (caller has checked legality)."""
        l = self.lessons[lid]
        old_ts = l['timeslot']
        self._remove(lid)
        l['timeslot'] = ts
        self._add(lid)
        return old_ts

    def _remove(self, lid):
        l = self.lessons[lid]
        k = self.key[l['timeslot']['id']]
        t, g = self.teacher(lid), self.group(lid)
        if t and t not in self.pseudo:
            lst = self.tcell.get((t, k))
            if lst and lid in lst:
                lst.remove(lid)
            if not self.tcell.get((t, k)):
                self.teacher_busy[k].discard(t)
        if g:
            if self.day.get((g, k[0]), {}).get(k[1]) == lid:
                self.day[(g, k[0])].pop(k[1], None)
            if not self.day.get((g, k[0])):
                self.group_busy[k].discard(g)
        if l.get('room'):
            if self.room_holder.get((l['room']['id'], k)) == lid:
                del self.room_holder[(l['room']['id'], k)]
            self.room_busy[k].discard(l['room']['id'])

    def _add(self, lid):
        l = self.lessons[lid]
        k = self.key[l['timeslot']['id']]
        t, g = self.teacher(lid), self.group(lid)
        if t and t not in self.pseudo:
            self.tcell[(t, k)].append(lid)
            self.teacher_busy[k].add(t)
        if g:
            self.day[(g, k[0])][k[1]] = lid
            self.group_busy[k].add(g)
        if l.get('room'):
            self.room_holder[(l['room']['id'], k)] = lid
            self.room_busy[k].add(l['room']['id'])

    # ---- repair strategies for one hole ----
    # A pull-up only CLOSES a hole when the mover is the day's LAST lesson (the run
    # compacts); pulling the lesson right after the hole would just migrate the hole
    # right. So every strategy targets the day's LAST lesson moving INTO the hole.
    def repair(self, g, d, hole):
        ts_hole = self.ts_by_key.get((d, hole))
        if ts_hole is None:
            return None
        day = self.day.get((g, d), {})
        if not day:
            return None
        s0 = max(day)  # the day's LAST occupied slot
        lid = day[s0]
        if not self.movable(lid):
            return None
        ok, why = self.cell_ok(lid, ts_hole)
        if ok:
            self.move(lid, ts_hole)
            return ('pull-last', lid, s0, hole)
        # who blocks the hole cell for lid's teacher? relocate the blocker within its
        # own day (hole-fill or tail-extend only: cannot open a hole), then pull
        t = self.teacher(lid)
        blocker = None
        if t and t not in self.pseudo:
            occ = [x for x in self.tcell.get((t, self.key[ts_hole['id']]), ()) if x != lid]
            blocker = occ[0] if occ else None
        if blocker is not None and self.movable(blocker):
            bts = self.lessons[blocker]['timeslot']
            bk = self.key[bts['id']]
            bday = self.day.get((self.group(blocker), bk[0]), {})
            bmax = max(bday) if bday else None
            for s in range(0, 16):
                if s in bday:
                    continue
                target = self.ts_by_key.get((bk[0], s))
                if target is None:
                    continue
                fills_hole = len(bday) >= 2 and min(bday) < s < bmax
                tail_extend = bmax is not None and bk[1] == bmax and s == bmax + 1
                if not (fills_hole or tail_extend):
                    continue
                okb, _ = self.cell_ok(blocker, target)
                if okb:
                    self.move(blocker, target)
                    ok2, _ = self.cell_ok(lid, ts_hole)
                    if ok2:
                        self.move(lid, ts_hole)
                        return ('blocker-relocate+pull-last', (blocker, lid), s0, hole)
                    self.move(blocker, bts)  # revert
        # cross-day swap of the LAST lesson with a same-group lesson whose teacher is
        # FREE at the hole cell: after the swap the incoming lesson is the new LAST and
        # can be pulled into the hole (done in the same compound move). Per-day counts
        # preserved; the swap itself opens no hole (both ends occupied).
        for (g2, d2), day2 in sorted(self.day.items()):
            if d2 == d or g2 != g:
                continue
            for s2 in sorted(day2):
                lid2 = day2[s2]
                if not self.movable(lid2):
                    continue
                ok_h2, _ = self.cell_ok(lid2, ts_hole, ignore=(lid,))
                if not ok_h2:
                    continue
                t0 = self.ts_by_key[(d, s0)]
                t2 = self.ts_by_key[(d2, s2)]
                self._remove(lid)
                self._remove(lid2)
                ok1, w1 = self.cell_ok(lid, t2, ignore=(lid, lid2))
                ok2, w2 = self.cell_ok(lid2, t0, ignore=(lid, lid2))
                if ok1 and ok2:
                    self._add_swapped(lid, t2)
                    self._add_swapped(lid2, t0)
                    okp, _ = self.cell_ok(lid2, ts_hole)
                    if okp:
                        self.move(lid2, ts_hole)
                        return ('swap+pull-last', (lid, lid2), s0, hole)
                    # revert: undo pull (none), swap back
                    self._remove(lid)
                    self._remove(lid2)
                    self._add_swapped(lid, t0)
                    self._add_swapped(lid2, t2)
                else:
                    self._add_swapped(lid, t0)
                    self._add_swapped(lid2, t2)
        return None

    def _add_swapped(self, lid, ts):
        self.lessons[lid]['timeslot'] = ts
        self._add(lid)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('export')
    ap.add_argument('--out')
    ap.add_argument('--max-moves', type=int, default=400)
    a = ap.parse_args()
    data = load(a.export)
    grid = Grid(data)
    grid._build()
    grid._holes_before = len(grid.holes())
    print(f"start: holes={grid._holes_before}")
    moves = []
    for step in range(a.max_moves):
        holes = grid.holes()
        grid._holes_before = len(holes)
        if not holes:
            break
        (g, d), s = holes[0]
        mv = grid.repair(g, d, s)
        if mv is None:
            # try the remaining holes before declaring stall
            progressed = False
            for (g2, d2), s2 in holes[1:]:
                grid._holes_before = len(holes)
                mv = grid.repair(g2, d2, s2)
                if mv is not None:
                    moves.append(mv)
                    progressed = True
                    break
            if not progressed:
                print(f"STALL: {len(holes)} hole(s) left after {len(moves)} move(s)")
                break
            continue
        moves.append(mv)
    holes = grid.holes()
    print(f"end: holes={len(holes)} moves={len(moves)}")
    kinds = collections.Counter(m[0] for m in moves)
    print("move kinds:", dict(kinds))
    for hd, hs in holes[:10]:
        print("  residual hole:", hd, hs)
    if a.out:
        with gzip.open(a.out, 'wt') as f:
            json.dump(grid.data, f)
        print('written', a.out)
    return 0 if not holes else 1


if __name__ == '__main__':
    sys.exit(main())
