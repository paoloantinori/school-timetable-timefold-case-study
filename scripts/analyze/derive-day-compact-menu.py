#!/usr/bin/env python3
"""TASK-542 iter-20: derive the day-compact BLOCK MENU from the golden (golden-basin-analysis.md
section 4). For every (group, weekday) take the MODAL block list: the runs of consecutive hours
of the same subject, modal over the year's weeks, emitted as ordered (subject, hours) rows.

Output: seed/day-compact-menu.csv (group,weekday,subject,hours) - frozen input, same
technique as golden-copresence-depth.csv (TASK-452 #7).
"""
import csv
import datetime
from collections import defaultdict, Counter

GOLDEN = "src/main/resources/seed/lessons.csv"
OUT = "src/main/resources/seed/day-compact-menu.csv"

days = defaultdict(list)  # (group, date) -> [(slot, subject)]
for r in csv.DictReader(open(GOLDEN)):
    if not r.get("date"):
        continue
    try:
        d = datetime.date.fromisoformat(r["date"])
    except ValueError:
        continue
    if d.weekday() >= 5:
        continue
    days[(r["group"], d)].append((r["slot"], r["subject"]))

# per (group, weekday): block-list signature -> count
sig = defaultdict(Counter)
for (g, d), lessons in days.items():
    lessons.sort()
    blocks = []
    for slot, subject in lessons:
        if blocks and blocks[-1][0] == subject:
            blocks[-1][1] += 1
        else:
            blocks.append([subject, 1])
    signature = tuple((s, h) for s, h in blocks)
    sig[(g, d.weekday())][signature] += 1

rows = []
for (g, wd), counter in sorted(sig.items()):
    best, n = counter.most_common(1)[0]
    total = sum(counter.values())
    if total < 5 or n < 3:
        continue  # too rare to be a menu: decline rather than freeze noise
    for subject, hours in best:
        rows.append((g, ["Mon", "Tue", "Wed", "Thu", "Fri"][wd], subject, hours))

with open(OUT, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["group", "weekday", "subject", "hours"])
    w.writerows(rows)
print(f"wrote {len(rows)} block rows to {OUT}")
