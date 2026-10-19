#!/usr/bin/env python3
"""TASK-542 profile analysis: post-CONSTRUCTION profile features from a boundary checkpoint.

Reads a POST_CONSTRUCTION.json (or any checkpoint) and joins the placements against the DB
(quota rows, timeslot metadata, lesson->group) to compute the profile features the early-doom
predictor needs. Pure reading: no solver, no writes.

Usage: construction-profile.py <checkpoint.json>
Output: one JSON line with the feature vector.
"""
import json
import subprocess
import sys
from collections import defaultdict


def rows(sql):
    out = subprocess.run(['./scripts/db/db-query.sh', sql], capture_output=True, text=True).stdout
    for line in out.splitlines():
        p = [x.strip() for x in line.split('|')]
        if p and p[0] and p[0][0].isdigit():
            yield p


def main():
    ckpt = json.load(open(sys.argv[1]))
    ts = {p[0]: (p[1], p[2]) for p in rows(
        "SELECT id, startdatetime::date, startdatetime::time FROM timeslot")}
    les = {int(p[0]): p[1] for p in rows(
        "SELECT l.id, sg.name FROM lesson l JOIN course c ON c.id=l.course_id "
        "JOIN studentgroup sg ON sg.id=c.studentgroup_id")}
    quota = {}
    for p in rows("SELECT sg.name, q.date, q.aulahours+q.labhours FROM dailyactivityquota q "
                  "JOIN studentgroup sg ON sg.id=q.studentgroup_id WHERE q.aulahours+q.labhours>0"):
        quota[(p[0], p[1])] = int(p[2])

    # per (group, date): hours placed
    day_hours = defaultdict(int)
    slots_used = defaultdict(set)
    boxed = 0
    for pl in ckpt['placements']:
        if pl['timeslotId'] is None:
            boxed += 1
            continue
        d, h = ts.get(str(pl['timeslotId']), (None, None))
        g = les.get(pl['lessonId'], '?')
        day_hours[(g, d)] += 1
        # slot index: order of the hour within the day (8->0 ... 15:30->7)
        slots_used[(g, d)].add(h)

    holes = 0          # H17-shape: internal gaps in a group's day (slot-sequence gaps)
    deficit_cells = 0  # H32-shape: placed < quota on a quota day (partial only)
    surplus_hours = 0  # M33-shape: hours above quota
    for key, hours in day_hours.items():
        q = quota.get(key, 0)
        if hours > q:
            surplus_hours += hours - q
        elif 0 < hours < q:
            deficit_cells += 1
        # holes: sort the day's hours, count gaps between consecutive used hours
        hs = sorted(slots_used[key])
        for i in range(1, len(hs)):
            if hs[i] != hs[i - 1] and hs[i] is not None and hs[i - 1] is not None:
                # consecutive school hours are 1h apart until 13:00, then 14:30
                prev, cur = hs[i - 1], hs[i]
                gap = (int(cur[:2]) - int(prev[:2]))
                if gap > 1:
                    holes += gap - 1

    print(json.dumps({
        'checkpoint': sys.argv[1],
        'boundary': ckpt['cursor']['boundary'],
        'attempt': ckpt['cursor']['attempt'],
        'tier_hard': ckpt['timeslotTierHard'],
        'score': ckpt['score'],
        'boxed': ckpt['boxed'],
        'placed_hours': sum(day_hours.values()),
        'h17_holes_construction': holes,
        'h32_deficit_cells': deficit_cells,
        'surplus_hours_m33': surplus_hours,
    }))


if __name__ == '__main__':
    main()
