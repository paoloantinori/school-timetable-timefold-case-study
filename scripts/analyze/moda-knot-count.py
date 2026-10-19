#!/usr/bin/env python3
"""Count stacked (group, date, hour) cells — the G05 zero-slack knot R1 must crack.

Measure D of the TASK-392/393 validation (and the TASK-391 R&R sweep before it).
Reads lessons.txt as produced by the runbook's db-query:
    group | date | hour | subject | teacher | pinned
one row per PLACED lesson (unassigned lessons have no timeslot and aren't included,
so they can't stack). A 'stack' is a (date, hour) cell with >1 lesson for a group =
an H3 student-group conflict.

Primary output: G05 stacked-cell count (expected 0 under R1 ruin 5-20; the TASK-391
evidence is that the small ruin 2-5 control lets this knot appear 1-2x, R1 avoids it).
Secondary output: every group's stacked-cell count (the broader H3 signal — under a
clean solve this should be 0 across ALL groups).

Report-only (exit 0), to match the other KPI scripts. Prints a clear PASS/FAIL line
for the G05 knot so it doubles as a gate when read by eye or grep.

Usage: moda-knot-count.py <lessons.txt> [group-name]
       moda-knot-count.py local/val-run1/lessons.txt        # default target = "G05"
"""
import collections
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "lessons.txt"
target = sys.argv[2] if len(sys.argv) > 2 else "G05"

cells = collections.Counter()                       # (group, date, hour) -> count
detail = collections.defaultdict(list)              # (group, date, hour) -> [(subject, teacher)]
with open(path) as f:
    for line in f:
        line = line.strip()
        if not line or "|" not in line or line.startswith(("SET", "group")):
            continue
        parts = line.split("|")
        if len(parts) < 5:
            continue
        g, date, hour, subj, teacher = parts[0], parts[1], parts[2], parts[3], parts[4]
        key = (g, date, hour)
        cells[key] += 1
        detail[key].append((subj, teacher))


def report(name):
    stacks = {k: v for k, v in cells.items() if k[0] == name and v > 1}
    print(f"{name}: {len(stacks)} stacked (date,hour) cell(s)")
    for k in sorted(stacks):
        who = ", ".join(f"{s}/{t}" for s, t in detail[k])
        print(f"  {k[1]} h{k[2]}  x{cells[k]}: {who}")
    return len(stacks)


n = report(target)
print(f"G05 KNOT: {n}  ({'PASS' if n == 0 else 'FAIL — R1 did not crack it'})")

print("--- all groups with stacked cells (full H3 signal; expect 0 everywhere) ---")
by_group = collections.defaultdict(int)
for k, v in cells.items():
    if v > 1:
        by_group[k[0]] += 1
if by_group:
    for g in sorted(by_group, key=lambda x: -by_group[x]):
        print(f"  {g}: {by_group[g]}")
else:
    print("  (none)")
print(f"TOTAL stacked cells across all groups: {sum(by_group.values())}")
sys.exit(0)
