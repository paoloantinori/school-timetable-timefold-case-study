#!/usr/bin/env python3
"""Extract and validate the Spalmone 2026/2027 xlsx (hand-made solution).

Usage: python3 scripts/analyze/spalmone2027-extract.py [path-to-xlsx]

Outputs CSVs under claudedocs/spalmone-2026-27/data/ and prints a
coherence report: teacher conflicts, assigned-vs-scheduled hour deltas,
closure-day violations, compactness stats.
"""
import sys, os, csv, warnings
from collections import defaultdict
from datetime import date

warnings.filterwarnings('ignore')
import openpyxl

if len(sys.argv) < 2:
    sys.exit('usage: spalmone2027-extract.py <path-to-xlsx>  (the ~/Downloads default was removed in TASK-444)')
XLSX = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'claudedocs', 'spalmone-2026-27', 'data')
os.makedirs(OUT, exist_ok=True)

wb = openpyxl.load_workbook(XLSX, data_only=True)

def w(name, header, rows):
    path = os.path.join(OUT, name)
    with open(path, 'w', newline='') as f:
        cw = csv.writer(f)
        cw.writerow(header)
        cw.writerows(rows)
    print(f"wrote {path} ({len(rows)} rows)")

# ---------- lessons from 'Orario in righe' ----------
lessons = []
ws = wb['Orario in righe']
for row in ws.iter_rows(min_row=2, values_only=True):
    d, giorno, orario, chiusura, classe, _, docente, materia = row[:8]
    if d is None or classe is None:
        continue
    lessons.append((d.date().isoformat(), giorno, str(orario).strip(), bool(chiusura),
                    str(classe).strip(), str(docente).strip() if docente else '',
                    str(materia).strip() if materia else ''))
w('lessons.csv', ['date','day','slot','closed','group','teacher','subject'], lessons)

# ---------- assignments from 'Incarichi in riga' ----------
incarichi = []
ws = wb['Incarichi in riga']
for row in ws.iter_rows(min_row=2, values_only=True):
    doc, mat, cls, ore, _ = row[:5]
    if doc is None:
        continue
    incarichi.append((str(doc).strip(), str(mat).strip(), str(cls).strip(), float(ore or 0)))
w('incarichi.csv', ['teacher','subject','group','hours'], incarichi)

# ---------- teachers ----------
# maxDailyHours (4th column, TASK-348), maxEarlyStartDaysPerWeek (5th, TASK-368)
# and requiredFreeDaysPerWeek (6th, TASK-368) are all hand-curated, NOT
# extractor-derived (e.g. T01's 8h maxDailyHours override, T11's
# maxEarlyStartDaysPerWeek=1, T05's requiredFreeDaysPerWeek=2) - each must be
# preserved by short-name lookup against the EXISTING teachers.csv on every
# re-run, never blanked out (code review D3, TASK-366/348; extended TASK-368).
# See seed-csv-format.md's teachers.csv row for the full rationale.
HAND_CURATED_COLUMNS = [3, 4, 5]  # maxDailyHours, maxEarlyStartDaysPerWeek, requiredFreeDaysPerWeek

def load_existing_hand_curated_columns(path):
    existing = {}
    if not os.path.exists(path):
        return existing
    with open(path, newline='') as f:
        reader = csv.reader(f)
        next(reader, None)  # header
        for row in reader:
            if len(row) > max(HAND_CURATED_COLUMNS):
                existing[row[0]] = tuple(row[i] for i in HAND_CURATED_COLUMNS)
    return existing

teachers_csv_path = os.path.join(OUT, 'teachers.csv')
existing_hand_curated = load_existing_hand_curated_columns(teachers_csv_path)
old_overrides = {short: vals for short, vals in existing_hand_curated.items() if any(vals)}

teachers = []
new_shorts = set()
ws = wb['Docenti']
for row in ws.iter_rows(min_row=2, values_only=True):
    if row[0] is None:
        continue
    short = str(row[0]).strip()
    new_shorts.add(short)
    # Blank default tuple's arity is derived from HAND_CURATED_COLUMNS so a
    # future 4th hand-curated column only needs to change that one list.
    hand_curated = existing_hand_curated.get(short, ('',) * len(HAND_CURATED_COLUMNS))
    teachers.append((short, str(row[1] or '').strip(), str(row[3] or '').strip(), *hand_curated))

# A teacher that had a non-blank hand-curated override in the existing file
# but is gaining an empty value here (i.e. missing from the fresh 'Docenti'
# sheet - e.g. renamed) would silently lose that override if this output is
# committed - flag it loudly instead of overwriting quietly.
for short, vals in old_overrides.items():
    if short not in new_shorts:
        print(f"WARNING: teacher '{short}' had hand-curated values {vals} (maxDailyHours, "
              f"maxEarlyStartDaysPerWeek, requiredFreeDaysPerWeek) in the existing teachers.csv but is "
              f"missing from the freshly extracted 'Docenti' sheet - these overrides would be LOST. "
              f"Re-add them by hand under the teacher's new/correct short name.")

w('teachers.csv', ['short','fullname','email','maxDailyHours','maxEarlyStartDaysPerWeek','requiredFreeDaysPerWeek'], teachers)

# ---------- groups + rooms from 'Elenchi' ----------
# Room section has explicit bounds (code-review fix, TASK-343): only collect
# rows between the 'Aule' header cell and the 'Docente' header cell (or a
# blank cell, whichever comes first). The previous one-way in_docente_section
# latch would collect any non-'Aule'/non-'Docente' cell before ever seeing
# 'Docente' - order-safe only by accident, since it never actually required
# 'Aule' to have appeared first.
groups, rooms = [], []
ws = wb['Elenchi']
in_aule_section = False
for row in ws.iter_rows(min_row=2, values_only=True):
    if row[0]:
        groups.append((str(row[0]).strip(), str(row[1] or '').strip(), int(row[2] or 0)))
    cell8 = str(row[8]).strip() if row[8] else ''
    if cell8 == 'Aule':
        in_aule_section = True
    elif cell8 == 'Docente' or cell8 == '':
        in_aule_section = False
    elif in_aule_section:
        rooms.append((cell8, int(row[9] or 0)))
if not rooms:
    print("WARNING: no rooms extracted from the 'Elenchi' sheet - check the 'Aule' section bounds", file=sys.stderr)
w('groups.csv', ['group','course_code','enrolled'], groups)
w('rooms.csv', ['room','capacity'], rooms)

# ---------- closures + year dates from 'Parametri' ----------
ws = wb['Parametri']
closures = []
year_start = year_end = None
for row in ws.iter_rows(min_row=1, values_only=True):
    if row[1] == 'Data inizio anno': year_start = row[0].date()
    if row[1] == 'Data fine anno': year_end = row[0].date()
    if row[3] is not None and hasattr(row[3], 'date'):
        closures.append(row[3].date().isoformat())
w('closures.csv', ['date'], [(c,) for c in sorted(closures)])
print(f"year: {year_start} .. {year_end}")

# ---------- class constraints from 'Vincoli Classi' ----------
vincoli = []
ws = wb['Vincoli Classi']
for row in ws.iter_rows(min_row=2, values_only=True):
    if row[0] is None:
        continue
    fmt = lambda v: v.date().isoformat() if hasattr(v, 'date') else (str(v).strip() if v else '')
    vincoli.append((str(row[0]).strip(), fmt(row[1]), fmt(row[2]), fmt(row[3]), fmt(row[4]), fmt(row[5])))
w('vincoli_classi.csv', ['group','stage_start','stage_end','lab_day','consecutive_hours','notes'], vincoli)

# ============ COHERENCE CHECKS ============
print("\n========== COHERENCE REPORT ==========")
real = [l for l in lessons if l[5] and l[5] not in ('NO',) and not l[3]]
print(f"total scheduled lesson-hours (non-closed, with teacher): {len(real)}")
print(f"distinct groups in orario: {sorted(set(l[4] for l in real))}")
print(f"distinct slots: {sorted(set(l[2] for l in real))}")
print(f"distinct teachers in orario: {len(set(l[5] for l in real))}")
print(f"date range in orario: {min(l[0] for l in real)} .. {max(l[0] for l in real)}")

# 1. teacher conflicts: same teacher, same date+slot, different group
byslot = defaultdict(list)
for l in real:
    byslot[(l[0], l[2], l[5])].append(l[4])
conflicts = {k: v for k, v in byslot.items() if len(set(v)) > 1}
print(f"\n[1] TEACHER CONFLICTS (same teacher, same slot, >1 group): {len(conflicts)}")
for k, v in sorted(conflicts.items())[:20]:
    print('   ', k, '->', v)

# 2. group conflicts
bygrp = defaultdict(list)
for l in real:
    bygrp[(l[0], l[2], l[4])].append(l[5])
gconf = {k: v for k, v in bygrp.items() if len(v) > 1}
print(f"\n[2] GROUP double-bookings: {len(gconf)}")
for k, v in sorted(gconf.items())[:10]:
    print('   ', k, '->', v)

# 3. scheduled vs assigned hours per (teacher, subject, group)
sched = defaultdict(float)
for l in real:
    sched[(l[5], l[6], l[4])] += 1
assigned = {(t, s, g): h for t, s, g, h in incarichi}
print(f"\n[3] ASSIGNED vs SCHEDULED (teacher,subject,group):")
mismatch = 0
allkeys = set(sched) | set(assigned)
for k in sorted(allkeys):
    a, s = assigned.get(k, 0), sched.get(k, 0)
    if abs(a - s) > 0.01:
        mismatch += 1
        print(f"    {k}: assigned={a} scheduled={s} delta={s-a:+.0f}")
print(f"  mismatched keys: {mismatch} / {len(allkeys)}")
print(f"  total assigned={sum(assigned.values()):.0f} total scheduled={sum(sched.values()):.0f}")

# 4. lessons on closure days
closet = set(closures)
onclosed = [l for l in real if l[0] in closet]
print(f"\n[4] lessons on closure days: {len(onclosed)}")
for l in onclosed[:10]: print('   ', l)

# 5. compactness: gaps within a group's day (using morning slot ordering)
SLOTS = ['08:00 09:00','09:00 10:00','10:00 11:00','11:00 12:00','12:00 13:00','13:00 14:00','14:30 15:30','15:30 16:30']
sidx = {s: i for i, s in enumerate(SLOTS)}
gday = defaultdict(list)
for l in real:
    gday[(l[4], l[0])].append(sidx.get(l[2], -1))
gaps = 0; sparse = 0; hist = defaultdict(int)
for k, idxs in gday.items():
    idxs = sorted(i for i in idxs if i >= 0)
    hist[len(idxs)] += 1
    morning = [i for i in idxs if i < 6]
    if morning and (max(morning) - min(morning) + 1) != len(morning):
        gaps += 1
    if 0 < len(idxs) < 4:
        sparse += 1
print(f"\n[5] group-days with a gap in morning block: {gaps} / {len(gday)}")
print(f"    sparse days (<4h): {sparse}")
print(f"    hours-per-day histogram: {dict(sorted(hist.items()))}")

# 6. afternoon slot usage
aft = [l for l in real if sidx.get(l[2], 0) >= 6]
print(f"\n[6] afternoon-slot lessons (14:30+): {len(aft)}")
byg = defaultdict(int)
for l in aft: byg[l[4]] += 1
print('    by group:', dict(byg))

# 7. teacher daily hours max
tday = defaultdict(int)
for l in real: tday[(l[5], l[0])] += 1
mx = defaultdict(int)
for (t, d), n in tday.items(): mx[t] = max(mx[t], n)
over6 = {t: m for t, m in mx.items() if m > 6}
print(f"\n[7] teachers exceeding 6h/day somewhere: {over6}")

# 8. stage blackout check
stages = {v[0]: (v[1], v[2]) for v in vincoli if v[1]}
print(f"\n[8] stage windows: {stages}")
for g, (s, e) in stages.items():
    inl = [l for l in real if l[4] == g and s <= l[0] <= e]
    print(f"    {g}: {len(inl)} lessons during stage window {s}..{e}")
    days = sorted(set(l[0] for l in inl))
    if days: print(f"       on {len(days)} distinct days, e.g. {days[:6]}")
EOF_MARKER_NOT_USED = None
