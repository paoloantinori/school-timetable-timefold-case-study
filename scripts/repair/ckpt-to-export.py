#!/usr/bin/env python3
"""TASK-542 iter-24-ter: turn a night POST_CONSTRUCTION checkpoint (lessonId->timeslotId)
into an export-shaped grid the pure repairer can consume: take the night winner export,
swap each lesson's timeslot for the checkpoint's (None when boxed), room=None."""
import gzip
import json
import sys

export, ckpt, out = sys.argv[1], sys.argv[2], sys.argv[3]
d = json.load(gzip.open(export))
grid = json.load(open(ckpt))['placements']
by_id = {p['lessonId']: p for p in grid}
ts_by_id = {t['id']: t for t in d['timeslots']}
n = 0
for l in d['lessons']:
    p = by_id.get(l['id'])
    if p is None or p.get('timeslotId') is None:
        l['timeslot'] = None
        l['room'] = None
        n += 1
    else:
        l['timeslot'] = ts_by_id[p['timeslotId']]
        l['room'] = None
json.dump(d, gzip.open(out, 'wt'))
print(f'written {out} (unplaced {n})')
