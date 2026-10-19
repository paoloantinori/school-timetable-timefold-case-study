#!/usr/bin/env python3
"""Weekly fingerprint sharpness KPI per group (TASK-411 MISURA).

Golden reference: the golden-vs-run-viz.py fingerprint view shows each group's
weekly (weekday, slot) grid as almost entirely near-0% or near-100% cells (a
near-fixed template, occasional lab-periodicity exceptions e.g. G04 Mon/Tue
18%). Our solved runs instead show mid-range occupancies (52-89%) with no
clean structure. This script turns that visual impression into a single
number per group: "nitidezza" (sharpness) = the share of the group's used
(weekday, slot) cells whose occupancy is DECIDED (>=0.9 "almost always" or
<=0.1 "almost never"), rather than genuinely mixed.

Input: EITHER
  - the golden CSV (claudedocs/spalmone-2026-27/data/lessons.csv format:
    date,day,slot,closed,group,teacher,subject header; closed=='True' rows
    skipped), OR
  - a pipe-format lessons dump (group|date|hour|subject|teacher|pinned,
    same format daily-fill-kpi.py/resemblance-kpi.py/cadence-kpi.py consume -
    a leading 'SET' line and blank/malformed lines are ignored).
Format is auto-detected from the first non-blank line (same convention as
cadence-kpi.py).

Excludes: rows whose subject or teacher is Stage/Esame (pseudo-teacher
accounting) or Fasullo (the golden CSV's placeholder rows) - uses
shared.KPI_EXCLUDED_PSEUDO, the same set cadence-kpi.py's
PSEUDO_SUBJECTS/PSEUDO_TEACHERS use (deliberately different from
shared.PSEUDO_TEACHERS - see that constant's docstring).

Occupancy per (group, weekday 0-4, slot 0-7): the fraction of the group's
ACTIVE weeks FOR THAT WEEKDAY (ISO weeks in which the group has >=1 valid
lesson on that specific weekday, any slot) in which the exact cell holds
>=1 lesson. Deliberately normalized per-weekday, NOT on the ~39 theoretical
school weeks (or even on the group's overall active weeks regardless of
weekday) - a group's stage/closure weeks, and weekday-specific exceptions
within an otherwise-active week (e.g. a stage day reserved on one weekday
while the rest of that week is taught normally), would otherwise dilute
every cell's occupancy toward mid-range without saying anything about the
template's sharpness. Empirically this is the denominator that reproduces
the TASK-408/411 visual finding ("colonne al 97-100%" for G01's Tue-Thu
slots) - a coarser per-group (not per-weekday) active-week count was tried
first and measured golden BELOW every run (fails the brief's own sanity
check), because it dilutes a weekday's occupancy with weeks where that
particular weekday was off but the rest of the group's week was not; the
per-weekday version does not have this flaw. Slot = hour-8 (hours 8..15 ->
slots 0..7), same mapping resemblance-kpi.py/golden-vs-run-viz.py already
use.

Calendar-openness convention (declared per the task brief, since neither
input format carries GroupCalendarRule/DailyActivityQuota data): a cell that
is NEVER occupied in any active week has unknown calendar status - it could
be a genuinely closed slot (outside the group's calendar) or a slot the
schedule simply never uses. Rather than guess, such cells are EXCLUDED from
the denominator entirely: the "calendar proxy" for a group is the set of
cells occupied at least once during the year (in any active week), and
sharpness is computed only over that set. A cell used only once out of many
active weeks (occupancy near 0) DOES count as decided ("essentially never") -
it is excluded only when it is used literally zero times. This convention is
applied identically to golden and run inputs (same function, no branching),
so the golden-vs-run comparison is apples-to-apples.

Per-group sharpness = 100 * |decided used cells| / |used cells|.
Overall sharpness reported two ways: POOLED (decided/used summed across all
groups) and laptopRO (unweighted mean of per-group sharpness %) - report both
since a pooled number can be dominated by groups with many used cells.

Usage:
  python3 scripts/analyze/fingerprint-sharpness-kpi.py <lessons-file> [-j]
      [--hi X] [--lo X] [--top N]
"""
import argparse
import json
import os
import statistics
import sys
from collections import defaultdict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from shared import KPI_EXCLUDED_PSEUDO, load_lesson_rows  # noqa: E402

PSEUDO_SUBJECTS = KPI_EXCLUDED_PSEUDO
PSEUDO_TEACHERS = KPI_EXCLUDED_PSEUDO


def load_rows(path):
    return load_lesson_rows(path)


def iso_week(d):
    y, w, _ = d.isocalendar()
    return (y, w)


def build_details(rows, hi, lo):
    filtered = [(g, d, h, s, t) for g, d, h, s, t in rows
                if s not in PSEUDO_SUBJECTS and t not in PSEUDO_TEACHERS]

    active_weeks = defaultdict(set)          # group -> {iso_week} (any weekday, reported only)
    active_wd_weeks = defaultdict(set)       # (group, weekday) -> {iso_week} (the denominator)
    cell_weeks = defaultdict(set)            # (group, weekday, slot) -> {iso_week}
    for g, d, h, _s, _t in filtered:
        wk = iso_week(d)
        wd = d.weekday()
        active_weeks[g].add(wk)
        active_wd_weeks[(g, wd)].add(wk)
        slot = h - 8
        if 0 <= slot < 8:
            cell_weeks[(g, wd, slot)].add(wk)

    groups = sorted(active_weeks)
    by_group = {}
    all_cells = []  # flat list of per-cell dicts, for JSON detail / --top
    for g in groups:
        cells = []
        for wd in range(5):
            n_active = len(active_wd_weeks.get((g, wd), ()))
            for sl in range(8):
                weeks_used = cell_weeks.get((g, wd, sl), set())
                if not weeks_used:
                    continue  # never used -> calendar status unknown, excluded (see docstring)
                occ = len(weeks_used) / n_active if n_active else 0.0
                decided = occ >= hi or occ <= lo
                cell = {'group': g, 'weekday': wd, 'slot': sl,
                        'occupancy': round(occ, 3), 'weeks_used': len(weeks_used),
                        'active_weekday_weeks': n_active, 'decided': decided}
                cells.append(cell)
                all_cells.append(cell)
        used = len(cells)
        decided_n = sum(1 for c in cells if c['decided'])
        sharpness = round(100 * decided_n / used, 1) if used else None
        by_group[g] = {'active_weeks': len(active_weeks[g]), 'used_cells': used,
                        'decided_cells': decided_n, 'sharpness_pct': sharpness,
                        'cells': cells}
    return by_group, all_cells


def overall_stats(by_group):
    used = sum(v['used_cells'] for v in by_group.values())
    decided = sum(v['decided_cells'] for v in by_group.values())
    pooled = round(100 * decided / used, 1) if used else None
    per_group_pct = [v['sharpness_pct'] for v in by_group.values() if v['sharpness_pct'] is not None]
    macro = round(statistics.mean(per_group_pct), 1) if per_group_pct else None
    return {'groups': len(by_group), 'used_cells': used, 'decided_cells': decided,
            'pooled_sharpness_pct': pooled, 'macro_sharpness_pct': macro}


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('lessons_file')
    ap.add_argument('-j', '--json', action='store_true',
                     help='JSON output (full detail incl. per-cell occupancy) instead of terminal text')
    ap.add_argument('--hi', type=float, default=0.9, help='decided-high threshold (default 0.9)')
    ap.add_argument('--lo', type=float, default=0.1, help='decided-low threshold (default 0.1)')
    ap.add_argument('--top', type=int, default=10,
                     help='top-N LEAST decided used cells to list (closest to 0.5, default 10)')
    args = ap.parse_args()

    rows = load_rows(args.lessons_file)
    by_group, all_cells = build_details(rows, args.hi, args.lo)
    overall = overall_stats(by_group)

    undecided_worst = sorted(all_cells, key=lambda c: abs(c['occupancy'] - 0.5))[:args.top]

    if args.json:
        out = {
            'input': args.lessons_file,
            'hi_threshold': args.hi,
            'lo_threshold': args.lo,
            'overall': overall,
            'by_group': by_group,
            'least_decided_cells': undecided_worst,
        }
        print(json.dumps(out, indent=2, ensure_ascii=False))
        return

    WD_NAME = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven']
    print(f'input: {args.lessons_file}')
    print(f'soglie: decisa se occupancy >= {args.hi} o <= {args.lo}')
    print()
    print(f'COMPLESSIVO (gruppi={overall["groups"]}, celle usate={overall["used_cells"]}):')
    print(f'  nitidezza pooled: {overall["pooled_sharpness_pct"]}%')
    print(f'  nitidezza macro (media gruppi): {overall["macro_sharpness_pct"]}%')
    print()
    print('PER GRUPPO:')
    for g, v in by_group.items():
        print(f'  {g:10s} settimane_attive={v["active_weeks"]:3d}  celle_usate={v["used_cells"]:3d}  '
              f'decise={v["decided_cells"]:3d}  nitidezza={v["sharpness_pct"]}%')
    print()
    print(f'TOP {args.top} CELLE MENO DECISE (occupancy piu vicina a 0.5):')
    for c in undecided_worst:
        print(f"  {c['group']:10s} {WD_NAME[c['weekday']]} slot{c['slot']}  "
              f"occupancy={c['occupancy']:.2f}  ({c['weeks_used']}/{c['active_weekday_weeks']} settimane)")


if __name__ == '__main__':
    main()
