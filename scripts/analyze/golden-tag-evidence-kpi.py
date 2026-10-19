#!/usr/bin/env python3
"""PASSO A (mandato maintainer 2026-07-30): derive tagging from GOLDEN evidence, not names.

For every subject that today carries a roomTag, check in the golden whether it
appears in a (date, hour) cell where lessons of that tag exceed the number of
assignable rooms of the tag. A subject that SYSTEMATICALLY appears in those
over-capacity cells is one the school de facto does NOT keep in the lab (a
de-tag candidate by evidence); one that never appears there tells us nothing
either way. Complete table for ALL tags, not just acconciatura.

Sources:
  - golden: claudedocs/spalmone-2026-27/data/lessons.csv (date,day,slot,closed,
    group,teacher,subject). Cell = (date, hour-from-slot-start).
  - subject->tag: src/main/resources/seed/subjects.csv (subject,roomTags).
  - cap(tag): #assignable rooms (cap>0) of that tag, from the 30e export.json.gz
    rooms (authoritative, same source the M-TC constraint uses).

Reconciliation anchor: maintainer measured 44 golden acconciatura over-capacity
(30 G14 + 14 G12). Re-derive independently here.
"""
import csv, gzip, json, os, sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shared import LESSONS_CSV  # noqa: E402  (TASK-444: golden path via shared config)

ROOT = Path(__file__).resolve().parents[2]
GOLDEN = LESSONS_CSV
SUBJECTS = str(ROOT / 'src/main/resources/seed/subjects.csv')
EXPORT = str(ROOT / 'claudedocs/spalmone-2026-27/certification-2026-07-30e/export.json.gz')

# subject -> tag (single tag per subjects.csv row)
subj_tag = {}
with open(SUBJECTS, newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        tags = (r.get("roomTags") or "").strip()
        if tags:
            subj_tag[r["subject"].strip()] = tags.split(";")[0].strip()
print("TAGGED SUBJECTS (subjects.csv):")
by_tag = defaultdict(list)
for s, t in subj_tag.items():
    by_tag[t].append(s)
for t in sorted(by_tag):
    print(f"  {t:16s}: {', '.join(sorted(by_tag[t]))}")

# cap(tag) from export rooms (authoritative)
data = json.load(gzip.open(EXPORT, "rt", encoding="utf-8"))
cap = Counter()
for room in data["rooms"]:
    if not room.get("assignable"):
        continue
    for t in room.get("tags") or []:
        cap[t] += 1
print(f"\nCAP (assignable rooms per tag, from export): {dict(cap)}")

# golden placements: (date, hour) -> tag -> count ; and per-subject placement cells
cell_tag_count = defaultdict(Counter)   # (date,hour) -> tag -> n
subj_cells = defaultdict(set)           # subject -> set of (date,hour) it occupies
subj_total = Counter()                  # subject -> #placements
KPI_EXCLUDED = {"Stage", "Esame", "Fasullo"}
with open(GOLDEN, newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        if r.get("closed") == "True":
            continue
        s = r["subject"].strip()
        if s in KPI_EXCLUDED:
            continue
        if s not in subj_tag:
            continue  # untagged subject, not in scope
        date = r["date"]
        hour = int(r["slot"].split(":")[0])
        cell = (date, hour)
        tag = subj_tag[s]
        cell_tag_count[cell][tag] += 1
        subj_cells[s].add(cell)
        subj_total[s] += 1

# over-capacity cells per tag
overcap_cells = defaultdict(set)   # tag -> set of over-capacity cells
for cell, tc in cell_tag_count.items():
    for t, n in tc.items():
        if n > cap.get(t, 0):
            overcap_cells[t].add(cell)

print("\nGOLDEN over-capacity cells per tag (demand > rooms):")
for t in sorted(cap):
    cells = overcap_cells.get(t, set())
    # total over-capacity lessons (sum over those cells of demand)
    over_lessons = sum(cell_tag_count[c][t] for c in cells)
    print(f"  {t:16s}: {len(cells):3d} cells, {over_lessons:3d} lessons in them (cap={cap[t]})")
print(f"  ANCHOR: maintainer measured 44 acconciatura (30 G14 + 14 G12) -> re-derive above.")

# per-subject: total placements, #placements in over-capacity cells of its tag, rate
print("\nPER SUBJECT (tagged): total golden placements vs in-over-capacity-cells of its tag")
print(f"  {'subject':32s} {'tag':16s} {'tot':>5s} {'inOverCap':>9s} {'rate':>6s}")
rows = []
for s in sorted(subj_tag):
    t = subj_tag[s]
    tot = subj_total[s]
    oc = len(subj_cells[s] & overcap_cells.get(t, set()))
    rate = (oc / tot) if tot else 0.0
    rows.append((s, t, tot, oc, rate))
for s, t, tot, oc, rate in sorted(rows, key=lambda x: -x[4]):
    flag = "  <- high rate (evidence: school places it over-capacity)" if rate >= 0.10 and oc >= 5 else ""
    print(f"  {s:32s} {t:16s} {tot:>5d} {oc:>9d} {rate:>6.1%}{flag}")

# composition of acconciatura over-capacity cells (subject, group)
print("\nACCONCIATURA over-capacity cell composition (subject | group : lessons):")
acc_oc = overcap_cells.get("acconciatura", set())
comp = Counter()
with open(GOLDEN, newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        if r.get("closed") == "True":
            continue
        s = r["subject"].strip()
        if s not in subj_tag or subj_tag[s] != "acconciatura":
            continue
        date = r["date"]; hour = int(r["slot"].split(":")[0])
        if (date, hour) in acc_oc:
            comp[(s, r["group"].strip())] += 1
for (s, g), n in comp.most_common():
    print(f"   {n:3d}  {s:30s} | {g}")
print(f"  (anchor: G14 ~30, G12 ~14)")
