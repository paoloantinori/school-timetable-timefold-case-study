#!/usr/bin/env python3
"""Lesson-identity recurrence-erosion tracker (WORK 2, TASK-452-adjacent).

WHY THIS EXISTS. SPIKE-7 measured recurrence erosion by comparing the resemblance-%
of the UNPINNED population at construction-end vs at final
(claudedocs/spalmone-2026-27/task447-results/spikes/SPIKES-RAW.md section SPIKE 7).
That comparison is confounded by COMPOSITION: the pre-assignment/promotion pipeline
pins ~3090 lessons between the two cuts, so the "unpinned" set is not the same
lessons at construction and at final. Part of the -1.9pp global delta is the set
changing, not the solver moving lessons.

This tool removes the confound by tracking the SAME lesson instance (by its DB id)
across two phase dumps from ONE solve, so a cell-change is provably the solver
moving that lesson, not the population shifting. Lesson ids do NOT survive across
separate solves (the DB reassigns them), so the two inputs must come from one
flagged solve - which is what PhaseTransitionMarker produces at every phase
boundary when -Dplanner.phase-transition-dump.enabled=true.

INPUT FORMAT (the -id.txt dumps PhaseTransitionMarker.writeTransitionDumpWithIds writes):
    id|group|date|hour|subject|teacher|pinned|promotedPin
(hour is the wall-clock start hour 8-15; slotIndex = hour-8, so 14:30->slot6,
15:30->slot7, matching TimeslotSequence.SLOT_START_TIMES. weekday is derived from
the date. A leading SET line and blanks are ignored, same as the 6-field dumps.)

CELL = (weekday, slotIndex) - the weekly cell whose recurrence the golden cadence
defines. A lesson "kept its cell" between two cuts iff (weekday, slotIndex) is
identical. Changing the DATE at the same cell is NOT erosion (cadence preserved),
exactly the distinction M32/S13 draw.

MOBILE SET = lessons that are UNPINNED at BOTH cuts (the solver is free to move
them throughout). Cell-moves on this set are pure LS erosion. Lessons that get
pinned between the cuts leave the mobile set (composition shift) - reported
separately, never counted as erosion.

Usage:
  # two-cut comparison (construction-end vs final from one flagged solve):
  python3 scripts/analyze/lesson-identity-erosion.py --from A-id.txt --to B-id.txt

  # per-phase attribution over a whole target/phase-transitions dir:
  python3 scripts/analyze/lesson-identity-erosion.py --dir target/phase-transitions

Both modes report: composition shift (mobile-set size at each cut, how many left
it), overall cell-retention on the mobile set, and a per-(group, subject)
breakdown of which cohorts lose their weekly cell - the "where do the 3rd-year
points go" verdict, now composition-controlled. The --dir mode additionally
attributes each cell-change to the phase where it happened.
"""
import argparse
import os
import sys
from collections import defaultdict
from datetime import date

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from shared import KPI_EXCLUDED_PSEUDO  # noqa: E402

BASE_HOUR = 8
WEEKDAY = ['MON', 'TUE', 'WED', 'THU', 'FRI']


def parse(path):
    """id-tagged dump -> {lesson_id: (group, date_iso, slot, subject, teacher, pinned, promotedPin)}.
    Drops pseudo (Stage/Esame/Fasullo) rows: they are accounting, not real lessons,
    same exclusion every KPI in this family applies."""
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line == 'SET' or '|' not in line:
                continue
            p = [x.strip() for x in line.split('|')]
            if len(p) != 8:
                continue
            lid, g, d, hh, s, t, pn, ppn = p
            if s in KPI_EXCLUDED_PSEUDO or t in KPI_EXCLUDED_PSEUDO:
                continue
            slot = int(float(hh)) - BASE_HOUR
            if not (0 <= slot < 8):
                continue
            out[int(lid)] = (g, d, slot, s, t, pn.lower() == 'true', ppn.lower() == 'true')
    return out


def cell(rec):
    """(weekday, slotIndex) - date-independent weekly cell."""
    g, d, slot, s, t, _pn, _ppn = rec
    return (WEEKDAY[date.fromisoformat(d).weekday()], slot)


def report_cohort(label, a, b):
    """Compare two {id: rec} maps. Print composition shift + cell retention
    (overall and per-(group,subject)) on the lessons mobile (unpinned) at BOTH cuts."""
    ids = set(a) & set(b)            # present and placed at both cuts
    unp_a = {i for i in ids if not a[i][5]}
    unp_b = {i for i in ids if not b[i][5]}
    mobile = unp_a & unp_b            # unpinned at both = the pure-erosion set
    left = unp_a - unp_b              # got pinned between cuts (composition shift, unpin -> pin)
    gained = unp_b - unp_a            # got demoted/unpinned between cuts (reverse flow, pin -> unpin)
    # Split the composition shift: import/stage/constrained pins (authoritative, promotedPin=false)
    # vs template-promotion pins (promotedPin=true at TO). promotedPin is the field the dump carries
    # end-to-end; using it here is the documented purpose (it was dead payload before).
    left_auth = {i for i in left if not b[i][6]}
    left_promo = {i for i in left if b[i][6]}

    moved, kept = 0, 0
    by_gs = defaultdict(lambda: [0, 0])   # (group, subject) -> [kept, moved]
    for i in mobile:
        same = cell(a[i]) == cell(b[i])
        if same:
            kept += 1
            by_gs[(a[i][0], a[i][3])][0] += 1
        else:
            moved += 1
            by_gs[(a[i][0], a[i][3])][1] += 1

    print(f'\n=== {label} ===')
    print(f'lessons placed at both cuts: {len(ids)}')
    print(f'unpinned at FROM: {len(unp_a)} | unpinned at TO: {len(unp_b)} '
          f'| MOBILE (unpinned at both): {len(mobile)}')
    print(f'left the mobile set (got pinned between cuts): {len(left)}  '
          f'<- composition shift, NOT erosion '
          f'(authoritative {len(left_auth)} + template-promoted {len(left_promo)})')
    print(f'entered the mobile set (got demoted/unpinned between cuts): {len(gained)}  '
          f'<- reverse composition flow')
    if mobile:
        print(f'cell-retention on MOBILE set: {kept}/{len(mobile)} = '
              f'{100*kept/len(mobile):.1f}% kept, {moved} moved')
    else:
        print('cell-retention on MOBILE set: (empty mobile set)')

    rows = [(gs[0], gs[1], v[0], v[1]) for gs, v in by_gs.items() if (v[0] + v[1]) >= 5]
    rows.sort(key=lambda r: -(r[2] + r[3]) * (1 if r[3] else 0))  # cohorts with moves first
    if rows:
        print(f'per-(group, subject) on mobile set (cohorts >=5 lessons, moves first):')
        print(f'  {"group":10s} {"subject":32s} {"kept":>5s} {"moved":>6s} {"ret%":>6s}')
        for g, s, k, m in rows[:25]:
            tot = k + m
            print(f'  {g:10s} {s:32s} {k:5d} {m:6d} {100*k/tot:6.1f}')


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--from', dest='frm')
    ap.add_argument('--to', dest='to')
    ap.add_argument('--dir', dest='directory',
                    help='dir of phase-*-id.txt dumps; attribute cell-changes per phase')
    args = ap.parse_args()

    if args.directory:
        files = sorted(
            (f for f in os.listdir(args.directory) if f.endswith('-id.txt') and f.startswith('phase-')),
            key=_phase_key)
        if len(files) < 2:
            sys.exit(f'need >=2 phase-*-id.txt dumps in {args.directory}, found {len(files)}')
        dumps = [(f, parse(os.path.join(args.directory, f))) for f in files]
        print(f'phase dumps ({len(dumps)}): ' + ', '.join(f for f, _ in dumps))
        # always-mobile set across ALL phases
        ids_all = set(dumps[0][1])
        for _, d in dumps[1:]:
            ids_all &= set(d)
        mobile_all = {i for i in ids_all if all(not d[i][5] for _, d in dumps)}
        print(f'\nlessons placed & unpinned at EVERY phase (always-mobile): {len(mobile_all)}')
        prev = None
        for name, d in dumps:
            if prev is None:
                prev = (name, d)
                continue
            changed = sum(1 for i in mobile_all if cell(prev[1][i]) != cell(d[i]))
            print(f'  {prev[0]:36s} -> {name:36s}  cell-changes on always-mobile: {changed}')
            prev = (name, d)
        report_cohort(f'{dumps[0][0]} (first) -> {dumps[-1][0]} (last)', dumps[0][1], dumps[-1][1])
        return

    if not (args.frm and args.to):
        sys.exit('provide --from/--to (two dumps) or --dir (phase dump dir)')
    report_cohort(f'{os.path.basename(args.frm)} -> {os.path.basename(args.to)}',
                  parse(args.frm), parse(args.to))


def _phase_key(name):
    """Sort phase-N-... by N (the leading integer after 'phase-')."""
    try:
        return int(name.split('-')[1])
    except (IndexError, ValueError):
        return 0


if __name__ == '__main__':
    main()
