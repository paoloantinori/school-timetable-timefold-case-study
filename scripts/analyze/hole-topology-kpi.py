#!/usr/bin/env python3
"""Hole-topology KPI (TASK-419-adjacent, maintainer 2026-07-26 request).

Existing KPIs (daily-fill-kpi.py, day-start-kpi.py) measure the QUANTITY of
missing hours per (group, date). This script classifies their TOPOLOGY -
WHERE inside the day the missing hours sit - because the three shapes are
not equally acceptable:

  1. BUCO-INTERNO (an empty slot with real lessons both before and after it
     the same day) - MAI accettabile. The golden 2026/27 timetable has
     exactly ONE such day in the whole year (G10, 2026-12-17, hole at
     slot 5 / 13:00-14:00 - the documented H17 pinned-exemption case,
     08:00-13:00 + 14:30-16:30, see CLAUDE.md "H17 afternoon-contiguity"
     and SchoolScheduleConstraintProvider#verticallyCompact). A second
     historically-cited H17 exception (G08, 2027-04-01, stage+RA 12:00-14:00)
     is NOT counted here as an internal hole once Stage/Esame lessons are
     excluded from the "occupied" side (see PSEUDO_SUBJECTS below) - what
     looks like a double hole against ALL lessons resolves to a normal
     early-end once the pseudo-teacher accounting rows are dropped from the
     picture, exactly like every other KPI in this family drops them.
  2. inizio-tardivo (the day's first real lesson is later than the group's
     normal opening slot, day-start-kpi.py's own slot-0-except-ramp-Monday
     convention) - quasi mai (golden: 2 casi).
  3. fine-anticipata (the day's last real lesson is earlier than where the
     day's own expected-hours total would put it) - the ONE normal form of
     missing hours (golden: 131 casi) - a day that runs out of subjects to
     teach simply stops, it doesn't leave a hole in the middle or delay its
     start.
  4. entrambi - both 2 and 3 on the same day (rare), or a fully-empty
     expected day (no real lesson at all that day - see NOTE below).
  5. OK-pieno - the day's real lessons form one contiguous run starting at
     the expected slot and reaching (or exceeding) the expected end.

Expected hours per (group, date) - SAME combined source golden-vs-run-viz.py's
load_expected() already uses (calendario_fc.csv wins for its 3 groups -
G04/G08/G13 -, calendario_class_days.csv for the other 11): NOT the raw
aula+lab+stage sum, though. Since Stage/Esame lessons are excluded from the
"occupied" side (below), counting stage_hours in the expected total would
turn every stage day into a spurious huge "fine-anticipata" (or "entrambi")
deficit. Mirrors the TASK-385 aula+lab-only precedent already established
for the exact same reason (M20's dailyQuota()/the CH stamp's
expectedHoursCapFor - see CLAUDE.md "Universal expected-hours facts"):
  - FC-sheet groups: aula_hours + lab_hours (stage_hours dropped).
  - class_days-sheet groups: the day's hours UNLESS modality=='S' (a pure
    stage row), in which case expected = 0 (day excluded from the analysis
    entirely, same as a day with no sheet fact at all).
A day with expected == 0 is not evaluated (nothing was expected, so nothing
can be "missing"). LO/HI horizon bounds match daily-fill-kpi.py's.

Exclusions - uses shared.KPI_EXCLUDED_PSEUDO, IDENTICAL set to
cadence-kpi.py's PSEUDO_SUBJECTS/PSEUDO_TEACHERS: rows whose subject or
teacher is Stage/Esame (pseudo-teacher accounting, not real lessons) or
Fasullo (golden CSV placeholder rows) are dropped before building the
occupied-slot set; golden CSV rows with closed=='True' are skipped at parse
time. Format (golden CSV vs pipe dump) is auto-detected exactly like
cadence-kpi.py (shared.load_lesson_rows).

Canonical slots are 0-7, derived as hour-8 (hours 8..15), matching
TimeslotSequence's schema and golden-vs-run-viz.py's own slot math - slot 5
(13:00-14:00) and slot 6 (14:30-15:30) are adjacent for gap purposes only in
the sense that BOTH being occupied leaves no hole between them; if slot 5
itself is unoccupied while slot 4 and slot 6 both hold real lessons, that IS
one hole slot (exactly the G10 2026-12-17 case).

Day-start convention (expected first slot) is day-start-kpi.py's own: slot 0
(hour 8) every day except the 2026-09-14 ramp Monday (slot 1, hour 9) - a
simplification already validated against the golden there (~99.3% anchored).
Expected end slot = expected first slot + expected_hours - 1 (i.e., the
day's real-teaching hours are assumed to form one compact run starting at
the normal opening slot - the same assumption M18/H17/the weekly-template
CH stamp already make about a "normal" day shape).

KNOWN LIMITATION (documented, not silently swallowed): on a mixed aula+stage
day (FC groups only) where the stage hours are NOT the day's trailing block
(e.g. stage first, aula/lab after), the excluded Stage slots can make the
real-teaching occupied range start later than slot 0 even though nothing was
actually "late" - this would misclassify as inizio-tardivo/entrambi. Stage
is scheduled at the afternoon/day-edge in this school's practice (M28/H21
afternoon-reservation precedent), so this is expected to be rare-to-absent;
not corrected for here since it would require re-deriving actual stage
placement per date, out of scope for this KPI. Confirmed to fire exactly
once on the golden (G08, 2027-04-01, the "stage+RA" H17 exception).

GOLDEN MEASUREMENT (2026-07-26, verified against claudedocs/spalmone-2026-27/
data/lessons.csv): BUCO-INTERNO 1/1 slot, matching the specification exactly
(also confirmed on 3 other reference runs: smoke2 9/14, val408-run1 1/1,
val408-run2 1/1 - see golden-cadence-reference.md's "Hole topology" section
for the full reconciliation, including the 172 "sheet-vs-golden mismatch"
days - mostly G14's documented 9x hours-inflation artifact and a handful
of FC stage/sheet disagreements - that are excluded from the 5-way split
rather than forced into a category that doesn't describe them). Edge counts
(fine-anticipata 125, inizio-tardivo 3) land close to, but not bit-identical
with, the pre-registered golden values (131, 2) - traced to specific,
individually-verified causes, not left as an unexplained mismatch.

Classification priority per active (group, date) day: BUCO-INTERNO is
checked FIRST (independent of expected hours - a hole is a hole regardless
of how many hours were expected), then the edge checks. A day with zero
real occupied slots (all expected hours are "missing") is classified
'entrambi' (there's no real occupied range to test edges against, so it's
maximally both late-starting and early-ending) - reported separately in the
totals as such, not silently folded into fine-anticipata.

Usage:
  python3 scripts/analyze/hole-topology-kpi.py <lessons-file> [-j]

<lessons-file> is EITHER the golden CSV (claudedocs/spalmone-2026-27/data/
lessons.csv) or a pipe-format lessons dump (group|date|hour|subject|teacher|
pinned, the same dump daily-fill-kpi.py/cadence-kpi.py consume).
"""
import argparse
import csv
import json
import os
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / 'src/main/resources/seed'

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from shared import KPI_EXCLUDED_PSEUDO, hole_slots, load_lesson_rows  # noqa: E402

PSEUDO_SUBJECTS = KPI_EXCLUDED_PSEUDO
PSEUDO_TEACHERS = KPI_EXCLUDED_PSEUDO

FC_GROUPS = {'G04', 'G08', 'G13'}
from shared import SCHOOL_YEAR_START, SCHOOL_YEAR_END, add_kpi_args  # noqa: E402  (TASK-444 parametric)
LO, HI = SCHOOL_YEAR_START.isoformat(), SCHOOL_YEAR_END.isoformat()
RAMP_DATE = SCHOOL_YEAR_START.isoformat()  # first-day ramp; overridden from --ramp in main()
SLOTS_PER_DAY = 8
BASE_HOUR = 8


# ─────────────────────────── input parsing ───────────────────────────

def load_rows(path):
    """Golden CSV or group|date|hour|subject|teacher|pinned pipe dump,
    auto-detected (shared.load_lesson_rows). strip_fields=True mirrors this
    script's original parse_csv, which .strip()-ed subject/teacher on the
    golden-CSV path (cadence-kpi.py/fingerprint-sharpness-kpi.py's own CSV
    parsing does not - a genuine, preserved difference, see
    shared.parse_lessons_csv's docstring)."""
    return load_lesson_rows(path, strip_fields=True)


# ─────────────────────────── expected hours ───────────────────────────

def load_expected_hours():
    """(group, date-iso) -> real-teaching expected hours (aula+lab only,
    stage_hours excluded - see module docstring). FC sheet wins for its 3
    groups, calendario_class_days.csv for the rest (same combination
    golden-vs-run-viz.py's load_expected() uses)."""
    exp = {}
    with open(SEED / 'calendario_fc.csv', newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            d = r['date']
            if not (LO <= d <= HI):
                continue
            exp[(r['group'], d)] = int(r['aula_hours']) + int(r['lab_hours'])
    with open(SEED / 'calendario_class_days.csv', newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            if r['group'] in FC_GROUPS:
                continue
            d = r['date']
            if not (LO <= d <= HI):
                continue
            h = 0 if r['modality'].strip() == 'S' else int(float(r['hours']))
            exp[(r['group'], d)] = h
    return exp


# ─────────────────────────── occupied slots ───────────────────────────

def build_occupied(rows):
    """(group, date-iso) -> sorted set of canonical slot indices (0-7)
    occupied by REAL (non-pseudo) lessons. Stage/Esame/Fasullo rows are
    excluded (identical filter to cadence-kpi.py's PSEUDO_SUBJECTS/
    PSEUDO_TEACHERS) - Stage accounting hours are not real-teaching
    topology, mirrored by load_expected_hours() excluding stage_hours too."""
    days = defaultdict(set)
    for g, d, h, s, t in rows:
        if s in PSEUDO_SUBJECTS or t in PSEUDO_TEACHERS:
            continue
        slot = h - BASE_HOUR
        if 0 <= slot < SLOTS_PER_DAY:
            days[(g, d.isoformat())].add(slot)
    return days


# ─────────────────────────── classification ───────────────────────────

def classify_day(expected_hours, occupied_slots, date_iso):
    """Returns (label, holes) for one (group,date) day. holes is the sorted
    list of canonical slot indices strictly between the first and last
    occupied slot that are NOT occupied (empty unless label=='BUCO-INTERNO'),
    computed by shared.hole_slots (also used by golden-vs-run-viz.py's
    season-view hole marker)."""
    if not occupied_slots:
        return 'entrambi', []
    lo, hi = min(occupied_slots), max(occupied_slots)
    holes = hole_slots(occupied_slots)
    if holes:
        return 'BUCO-INTERNO', holes
    expected_start = 1 if date_iso == RAMP_DATE else 0
    expected_end = expected_start + expected_hours - 1
    late = lo > expected_start
    early = hi < expected_end
    if late and early:
        return 'entrambi', []
    if late:
        return 'inizio-tardivo', []
    if early:
        return 'fine-anticipata', []
    return 'OK-pieno', []


def classify_all(expected, occupied):
    details = []
    for (g, d), e in expected.items():
        if e <= 0:
            continue
        occ = occupied.get((g, d), set())
        label, holes = classify_day(e, occ, d)
        details.append({
            'group': g, 'date': d, 'expected_hours': e,
            'occupied_slots': sorted(occ),
            'occupied_hours': [BASE_HOUR + s for s in sorted(occ)],
            'label': label,
            'hole_slots': holes,
            'hole_hours': [BASE_HOUR + s for s in holes],
        })
    return details


LABELS = ['OK-pieno', 'fine-anticipata', 'inizio-tardivo', 'entrambi', 'BUCO-INTERNO']


def totals_by_label(details):
    c = {lab: 0 for lab in LABELS}
    for d0 in details:
        c[d0['label']] += 1
    return c


def by_group(details):
    groups = sorted({d0['group'] for d0 in details})
    out = {}
    for g in groups:
        rows = [d0 for d0 in details if d0['group'] == g]
        out[g] = totals_by_label(rows)
    return out


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('lessons_file')
    ap.add_argument('-j', '--json', action='store_true',
                     help='JSON output (full per-day detail) instead of terminal text')
    add_kpi_args(ap)  # --ramp (+ --year/--year-start/--year-end/--data-dir/--lessons-path/--closures-path)
    args = ap.parse_args()
    global LO, HI, RAMP_DATE
    LO, HI = args.year_start, args.year_end
    RAMP_DATE = args.ramp

    rows = load_rows(args.lessons_file)
    expected = load_expected_hours()
    occupied = build_occupied(rows)
    details = classify_all(expected, occupied)

    totals = totals_by_label(details)
    groups = by_group(details)
    holes_days = sorted(
        (d0 for d0 in details if d0['label'] == 'BUCO-INTERNO'),
        key=lambda d0: (d0['group'], d0['date']))
    total_hole_slots = sum(len(d0['hole_slots']) for d0 in holes_days)

    if args.json:
        out = {
            'input': args.lessons_file,
            'total_active_days': len(details),
            'totals': totals,
            'buco_interno_days': len(holes_days),
            'buco_interno_slots': total_hole_slots,
            'by_group': groups,
            'buco_interno_detail': holes_days,
            'days': details,
        }
        print(json.dumps(out, indent=2, ensure_ascii=False))
        return

    print(f'input: {args.lessons_file}')
    print(f'giorni attesi (expected_hours>0): {len(details)}')
    print()
    print('TOTALI PER CLASSE:')
    for lab in LABELS:
        print(f'  {lab:16s} {totals[lab]}')
    print(f'\nBUCO-INTERNO: {len(holes_days)} giorni / {total_hole_slots} slot bucati totali')
    print()
    print('PER GRUPPO:')
    for g, c in groups.items():
        line = '  '.join(f'{lab}={c[lab]}' for lab in LABELS)
        print(f'  {g:10s} {line}')
    print()
    print('ELENCO COMPLETO GIORNI CON BUCO-INTERNO:')
    for d0 in holes_days:
        print(f"  {d0['group']:10s} {d0['date']}  occupied_hours={d0['occupied_hours']}  "
              f"hole_hours={d0['hole_hours']}")


if __name__ == '__main__':
    main()
