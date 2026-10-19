#!/usr/bin/env python3
"""TASK-542 iter-26: multi-dimensional construction-profile features.

Extracts, for each available construction grid (night attempts' POST_CONSTRUCTION
checkpoints joined to the night winner export's metadata, the golden, and any
capture lessons.txt), a feature vector:

  holes      H17-style internal 1h holes per (group, date), construction grid
  conc       teacher-weekday concentration: fraction of real teachers (>=10 placed
             hours) with >=50% of their hours on ONE weekday (the golden's norm)
  entropy    normalized Shannon entropy of the teacher-weekday hour matrix
  boxed      unplaced lessons at construction end
  daystart   fraction of (group, date) days starting at slot 0
  quotaDef   mean per-day deficit vs declared aula+lab quota (H32-style), in-grid

Usage:
  construction-profile-features.py --winner <export.json.gz> --checkpoints <dir> [...]
  construction-profile-features.py --lessons <lessons.txt> --label NAME [...]
"""
import argparse
import collections
import datetime
import gzip
import json
import math
import os
import sys

WD = ['MON', 'TUE', 'WED', 'THU', 'FRI']


def load_export(path):
    d = json.load(gzip.open(path) if path.endswith('.gz') else open(path))
    # no slotIndex in the export: rank each date's distinct start times (0..7 chronological)
    times_by_date = collections.defaultdict(set)
    for t in d['timeslots']:
        times_by_date[t['date']].add(t['startDateTime'][11:16])
    rank = {dt: {tm: i for i, tm in enumerate(sorted(ts))}
            for dt, ts in times_by_date.items()}
    ts = {}
    for t in d['timeslots']:
        ts[t['id']] = (t['date'], rank[t['date']][t['startDateTime'][11:16]])
    lessons = {}
    for l in d['lessons']:
        c = l.get('course') or {}
        t = (c.get('teacher') or {})
        g = (c.get('studentGroup') or {}).get('name')
        lessons[l['id']] = {
            'teacher': t.get('name'), 'pseudo': bool(t.get('pseudo')),
            'group': g, 'subject': c.get('subject'),
        }
    placements = {}
    for l in d['lessons']:
        if l.get('timeslot') is not None:
            placements[l['id']] = l['timeslot']['id'] if isinstance(l['timeslot'], dict) else l['timeslot']
    return lessons, ts, placements


def load_checkpoints(lessons, ts, ckdir):
    """lessonId->timeslotId grids from POST_CONSTRUCTION.json files (id-space of `lessons`)."""
    out = {}
    for name in sorted(os.listdir(ckdir)):
        if not name.startswith('attempt'):
            continue
        p = os.path.join(ckdir, name, 'POST_CONSTRUCTION.json')
        if not os.path.exists(p):
            continue
        d = json.load(open(p))
        out[name] = {pl['lessonId']: pl['timeslotId'] for pl in d.get('placements', [])}
    return out


def load_lessons_txt(path):
    """group|date|hour|subject|teacher|pinned -> the same shape as an export grid."""
    lessons, ts, placements = {}, {}, {}
    tsid = 0
    # per-date rank of the hour value (hour is the start hour 8..15; ranking keeps the
    # ordinal semantics even if the slot schema ever gains a lunch gap)
    hours_by_date = collections.defaultdict(set)
    rows = []
    for line in open(path):
        parts = line.strip().split('|')
        if len(parts) < 6 or parts[0] == 'group':
            continue
        g, date, hour, subject, teacher, pinned = parts[:6]
        try:
            h = int(hour)
        except ValueError:
            continue
        rows.append((g, date, h, subject, teacher))
        hours_by_date[date].add(h)
    rank = {dt: {h: i for i, h in enumerate(sorted(hs))} for dt, hs in hours_by_date.items()}
    for g, date, h, subject, teacher in rows:
        tsid += 1
        ts[tsid] = (date, rank[date][h])
        lessons[tsid] = {'teacher': teacher, 'pseudo': teacher == 'Stage',
                         'group': g, 'subject': subject}
        placements[tsid] = tsid
    return lessons, ts, placements


def features(lessons, ts, placements):
    # holes + daystart per (group, date)
    day_slots = collections.defaultdict(list)
    # teacher-weekday hours
    tw = collections.defaultdict(collections.Counter)
    tot = collections.Counter()
    for lid, tsid in placements.items():
        meta = lessons.get(lid)
        tsmeta = ts.get(tsid)
        if meta is None or tsmeta is None:
            continue
        date, slot = tsmeta
        wd = datetime.date.fromisoformat(date).weekday()
        day_slots[(meta['group'], date)].append(slot)
        t = meta['teacher']
        if t and not meta['pseudo']:
            tw[t][wd] += 1
            tot[t] += 1
    holes = 0
    days = 0
    daystart = 0
    for k, slots in day_slots.items():
        s = sorted(slots)
        holes += sum(1 for a, b in zip(s, s[1:]) if b - a > 1)
        days += 1
        if s and s[0] == 0:
            daystart += 1
    conc_teachers = 0
    real = 0
    ent = 0.0
    for t, n in tot.items():
        if n < 10:
            continue
        real += 1
        top = max(tw[t].values())
        if top / n >= 0.5:
            conc_teachers += 1
        p = [c / n for c in tw[t].values() if c]
        ent += -sum(x * math.log(x) for x in p) / math.log(5)
    conc = conc_teachers / real if real else 0.0
    entropy = ent / real if real else 0.0
    return {
        'holes': holes,
        'conc': round(conc, 3),
        'entropy': round(entropy, 3),
        'daystart': round(daystart / days, 3) if days else 0.0,
        'placed': len(placements),
        'nTeachers': real,
        'nDays': days,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--winner')       # export json(.gz) with metadata (id-space for checkpoints)
    ap.add_argument('--checkpoints')  # dir with attemptN/POST_CONSTRUCTION.json
    ap.add_argument('--lessons', action='append', default=[])  # lessons.txt[:label]
    ap.add_argument('--golden')       # seed lessons.csv
    ap.add_argument('--out', default='construction-profile-features.json')
    a = ap.parse_args()
    result = {}
    if a.winner:
        lessons, ts, placements = load_export(a.winner)
        result['night-winner-FINAL'] = features(lessons, ts, placements)
        if a.checkpoints:
            for name, grid in load_checkpoints(lessons, ts, a.checkpoints).items():
                result['night-' + name + '-CONSTRUCTION'] = features(lessons, ts, grid)
    for spec in a.lessons:
        path, _, label = spec.partition(':')
        label = label or os.path.basename(path)
        lessons, ts, placements = load_lessons_txt(path)
        result[label] = features(lessons, ts, placements)
    if a.golden:
        # reuse lessons.txt loader on the golden csv via shared.py convention is heavy;
        # the golden csv has its own header - transform inline
        lessons, ts, placements = {}, {}, {}
        i = 0
        ghours, grows = collections.defaultdict(set), []
        for line in open(a.golden):
            parts = line.strip().split(',')
            if not parts or parts[0] in ('date', '') or line.startswith('#'):
                continue
            if len(parts) < 7:
                continue
            date, _day, slot, _closed, g, teach, sub = parts[:7]
            if teach in ('', 'Fasullo'):
                continue
            token = slot.split()[0][:5]  # "09:00 10:00" -> "09:00"
            try:
                hour = int(token[:2])
            except ValueError:
                continue
            ghours[date].add(hour)
            grows.append((date, hour, g, teach, sub))
        rank = {dt: {h: i for i, h in enumerate(sorted(hs))} for dt, hs in ghours.items()}
        for date, hour, g, teach, sub in grows:
            i += 1
            ts[i] = (date, rank[date][hour])
            lessons[i] = {'teacher': teach, 'pseudo': teach == 'Stage', 'group': g, 'subject': sub}
            placements[i] = i
        result['GOLDEN'] = features(lessons, ts, placements)
    json.dump(result, open(a.out, 'w'), indent=1)
    for k in sorted(result):
        f = result[k]
        print(f"{k:45} holes={f['holes']:>4} conc={f['conc']:.2f} ent={f['entropy']:.2f}"
              f" daystart={f['daystart']:.2f} placed={f['placed']}")


if __name__ == '__main__':
    sys.exit(main())
