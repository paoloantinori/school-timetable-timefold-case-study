#!/usr/bin/env python3
"""Weekly cadence KPI per (group, subject) - TASK-413 MISURA.

Golden reference: each subject holds an almost-constant weekly hour cadence
across its active span (e.g. G01 Lingua Italiana ~1-3h/week most of the
year, ramping to 4-6h near the end). Our solved runs instead show empty
weeks followed by end-of-year bursts (same subject/group: 8-9-13h in the
final weeks). This script quantifies the gap.

Input: EITHER
  - the golden CSV (claudedocs/spalmone-2026-27/data/lessons.csv format:
    date,day,slot,closed,group,teacher,subject header; closed=='True' rows
    skipped), OR
  - a pipe-format lessons dump (group|date|hour|subject|teacher|pinned,
    same format daily-fill-kpi.py/resemblance-kpi.py consume - a leading
    'SET' line and blank/malformed lines are ignored).
Format is auto-detected from the first non-blank line.

Excludes: rows whose subject or teacher is Stage/Esame (pseudo-teacher
accounting, not real lessons) or Fasullo (the golden CSV's placeholder
teacher/subject rows, 8 per group, which the Java import drops too).
Uses shared.KPI_EXCLUDED_PSEUDO, DELIBERATELY DIFFERENT from
shared.PSEUDO_TEACHERS: that set also lists 'RA' and 'Totale ore corso'
(incarichi.csv pseudo COURSE teachers), but RA micro-course hours are real
taught lessons on the run side (real teacher names in the dump) and must
stay countable on the golden side too, or the two sides would not be
comparable.

Per (group, subject), over the ISO-week hour series (weeks indexed against
the file's own global week domain - every ISO week that has at least one
valid lesson row anywhere in the file, so a fully-closed week, e.g.
Christmas, never counts as a "gap"):
  - active span: from the first to the last ISO week with hours > 0
  - mean / stddev (population), over ALL weeks in the span (gap-weeks count
    as 0, per spec - this is deliberate, it's what makes burst ratio blow up
    for bursty subjects)
  - max week hours, burst ratio = max / mean
  - gap-weeks: weeks with 0 hours INSIDE the span
  - first/last week (ISO, YYYY-Www)

Aggregate KPIs (overall and per group), computed only over (group, subject)
pairs with >= --min-hours total hours (default 10 - excludes noisy
micro-courses; their count is reported separately, never silently dropped):
  - % of pairs with burst ratio > --burst-threshold (default 2)
  - total gap-weeks, mean gap-weeks/pair, mean burst ratio

Usage:
  python3 scripts/analyze/cadence-kpi.py <lessons-file> [-j] [--top N]
                                          [--min-hours N] [--burst-threshold X]
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
    return load_lesson_rows(path, include_hour=False)


def iso_week(d):
    y, w, _ = d.isocalendar()
    return (y, w)


def week_label(yw):
    return f"{yw[0]}-W{yw[1]:02d}"


def build_details(rows):
    # global ISO-week domain: every week with >=1 valid lesson row anywhere
    # in the file (any group/subject) - a fully-closed week (all rows
    # closed=='True', filtered out upstream) never enters the domain, so it
    # can't masquerade as a subject-specific gap.
    all_weeks = sorted({iso_week(d) for _g, d, _s, _t in rows})
    week_index = {w: i for i, w in enumerate(all_weeks)}

    filtered = [(g, d, s, t) for g, d, s, t in rows
                if s not in PSEUDO_SUBJECTS and t not in PSEUDO_TEACHERS]

    hours = defaultdict(lambda: defaultdict(int))  # (group,subject) -> week -> hours
    for g, d, s, _t in filtered:
        hours[(g, s)][iso_week(d)] += 1

    details = []
    for (g, s), wk in sorted(hours.items()):
        total = sum(wk.values())
        active_idx = sorted(week_index[w] for w in wk if wk[w] > 0)
        if not active_idx:
            continue
        lo, hi = active_idx[0], active_idx[-1]
        series = [wk.get(all_weeks[i], 0) for i in range(lo, hi + 1)]
        mean = statistics.mean(series)
        stdev = statistics.pstdev(series) if len(series) > 1 else 0.0
        mx = max(series)
        burst = (mx / mean) if mean > 0 else 0.0
        gap_weeks = sum(1 for h in series if h == 0)
        details.append({
            'group': g, 'subject': s, 'total_hours': total,
            'first_week': week_label(all_weeks[lo]), 'last_week': week_label(all_weeks[hi]),
            'span_weeks': len(series), 'active_weeks': sum(1 for h in series if h > 0),
            'gap_weeks': gap_weeks, 'mean': round(mean, 2), 'stdev': round(stdev, 2),
            'max': mx, 'burst_ratio': round(burst, 2),
            'series': [[week_label(all_weeks[lo + i]), h] for i, h in enumerate(series)],
        })
    return details


def aggregate(items, burst_threshold):
    if not items:
        return {'pairs': 0, 'pct_burst_over_threshold': None,
                'total_gap_weeks': 0, 'mean_gap_weeks': None, 'mean_burst_ratio': None}
    n = len(items)
    over = sum(1 for d0 in items if d0['burst_ratio'] > burst_threshold)
    return {
        'pairs': n,
        'pct_burst_over_threshold': round(100 * over / n, 1),
        'total_gap_weeks': sum(d0['gap_weeks'] for d0 in items),
        'mean_gap_weeks': round(statistics.mean(d0['gap_weeks'] for d0 in items), 2),
        'mean_burst_ratio': round(statistics.mean(d0['burst_ratio'] for d0 in items), 2),
    }


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('lessons_file')
    ap.add_argument('-j', '--json', action='store_true',
                     help='JSON output (full detail incl. per-pair weekly series) instead of terminal text')
    ap.add_argument('--top', type=int, default=10, help='top-N worst burst-ratio pairs (default 10)')
    ap.add_argument('--min-hours', type=int, default=10,
                     help='min total hours for a (group,subject) pair to count toward aggregates (default 10)')
    ap.add_argument('--burst-threshold', type=float, default=2.0,
                     help='burst ratio threshold for the %% KPI (default 2.0)')
    args = ap.parse_args()

    rows = load_rows(args.lessons_file)
    details = build_details(rows)

    counted = [d0 for d0 in details if d0['total_hours'] >= args.min_hours]
    excluded_micro = len(details) - len(counted)

    overall = aggregate(counted, args.burst_threshold)
    by_group = {}
    for g in sorted({d0['group'] for d0 in counted}):
        by_group[g] = aggregate([d0 for d0 in counted if d0['group'] == g], args.burst_threshold)

    top_burst = sorted(counted, key=lambda d0: -d0['burst_ratio'])[:args.top]

    if args.json:
        out = {
            'input': args.lessons_file,
            'min_hours_threshold': args.min_hours,
            'burst_threshold': args.burst_threshold,
            'total_pairs': len(details),
            'excluded_micro_pairs': excluded_micro,
            'overall': overall,
            'by_group': by_group,
            'top_burst': top_burst,
            'pairs': details,
        }
        print(json.dumps(out, indent=2, ensure_ascii=False))
        return

    print(f'input: {args.lessons_file}')
    print(f'(group,subject) pairs totali: {len(details)}  '
          f'(esclusi <{args.min_hours}h dagli aggregati: {excluded_micro})')
    print()
    print(f'AGGREGATO COMPLESSIVO (n={overall["pairs"]}):')
    print(f'  burst>{args.burst_threshold}x: {overall["pct_burst_over_threshold"]}%')
    print(f'  gap-weeks totali: {overall["total_gap_weeks"]}  media/pair: {overall["mean_gap_weeks"]}')
    print(f'  burst ratio medio: {overall["mean_burst_ratio"]}')
    print()
    print('PER GRUPPO:')
    for g, agg in by_group.items():
        print(f'  {g:10s} n={agg["pairs"]:3d}  burst>{args.burst_threshold}x={agg["pct_burst_over_threshold"]:>5}%  '
              f'gap_tot={agg["total_gap_weeks"]:4d}  gap_media={agg["mean_gap_weeks"]}  '
              f'burst_medio={agg["mean_burst_ratio"]}')
    print()
    print(f'TOP {args.top} PEGGIORI PER BURST RATIO:')
    for d0 in top_burst:
        print(f"  {d0['group']:10s} {d0['subject']:35s} burst={d0['burst_ratio']:.2f}  "
              f"max={d0['max']}h mean={d0['mean']}h gap={d0['gap_weeks']}/{d0['span_weeks']}  "
              f"[{d0['first_week']}..{d0['last_week']}]  tot={d0['total_hours']}h")


if __name__ == '__main__':
    main()
