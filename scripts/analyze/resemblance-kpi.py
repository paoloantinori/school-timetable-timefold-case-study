#!/usr/bin/env python3
"""Resemblance KPIs vs the golden standard (TASK-381/385/386).

Computes, from a lessons dump (same format as daily-fill-kpi.py's input):
  - weekly recurrence % (total / unpinned / pinned): a lesson's
    (group, weekday-cell, slot) matches the same cell 7 days earlier on
    (teacher, subject) - golden strict reference: 41.5%
  - same-subject block histogram (adjacent slots, same group/subject/day)
    and the >=2h total - golden reference: 1,299 blocks
  - per-group UNPINNED recurrence breakdown (TASK-413) - same rec() metric
    as the UNPINNED line above, sliced by group, to localize which groups
    drive the aggregate number

Usage: python3 scripts/analyze/resemblance-kpi.py <lessons-dump.txt>
Dump query: see daily-fill-kpi.py's docstring (identical input).
Companion: daily-fill-kpi.py (full-day rate vs the school's expected hours).
"""
import datetime
import sys
from collections import Counter, defaultdict

SLOT = {8: 0, 9: 1, 10: 2, 11: 3, 12: 4, 13: 5, 14: 6, 15: 7}
MIN = 8  # per-(group,subject) cells with fewer lessons than this are noise, skipped


def main(dump_path: str, by_subject: bool = False) -> None:
    rows = []
    for line in open(dump_path):
        line = line.strip()
        if '|' not in line:
            continue
        p = [x.strip() for x in line.split('|')]
        if len(p) != 6:
            continue
        rows.append((p[0], datetime.date.fromisoformat(p[1]), int(float(p[2])),
                     p[3], p[4], p[5].lower() in ('t', 'true')))

    cells = {}
    for g, d, h, s, t, _pn in rows:
        cells.setdefault((g, d, h), []).append((s, t))

    def matches_prev(g, d, h, s, t):
        """The single recurrence definition: same (group, cell, slot) 7 days
        earlier holds the same (subject, teacher). Both the aggregate lines and
        the per-group breakdown call this, so they cannot drift."""
        prev = cells.get((g, d - datetime.timedelta(days=7), h), [])
        return any(ss == s and tt == t for ss, tt in prev)

    def rec(pred):
        n = de = 0
        for g, d, h, s, t, pn in rows:
            if not pred(pn):
                continue
            de += 1
            if matches_prev(g, d, h, s, t):
                n += 1
        return n, de

    for lab, pred in [('TOTALE', lambda _p: True), ('UNPINNED', lambda p: not p),
                      ('PINNED', lambda p: p)]:
        n, d = rec(pred)
        pct = 100 * n / d if d else 0
        print(f'ricorrenza {lab}: {n}/{d} = {pct:.1f}%')
    print('(golden stretta: 41.5%)')

    byday = defaultdict(list)
    for g, d, h, s, t, _pn in rows:
        byday[(g, d)].append((SLOT.get(h, -1), s))
    blocks = Counter()
    for items in byday.values():
        items.sort()
        run = 1
        for i in range(1, len(items) + 1):
            if i < len(items) and items[i][0] == items[i - 1][0] + 1 \
                    and items[i][1] == items[i - 1][1]:
                run += 1
            else:
                blocks[min(run, 8)] += 1
                run = 1
    multi = sum(v for k, v in blocks.items() if k >= 2)
    print('blocchi:', ', '.join(f'{v}x{k}h' for k, v in sorted(blocks.items())),
          f'| >=2h: {multi} (golden 1,299)')

    print()
    print('ricorrenza UNPINNED per gruppo:')
    by_group = defaultdict(lambda: [0, 0])
    for g, d, h, s, t, pn in rows:
        if pn:
            continue
        by_group[g][1] += 1
        if matches_prev(g, d, h, s, t):
            by_group[g][0] += 1
    for g in sorted(by_group):
        n, d = by_group[g]
        pct = 100 * n / d if d else 0
        print(f'  {g:10s} {n}/{d} = {pct:.1f}%')

    if by_subject:
        # Per-(group, subject) UNPINNED recurrence (maintainer 2026-07-29): localizes WHICH subject of
        # WHICH group drives the aggregate, so a construction->final diff pinpoints the erosion
        # hotspot. Same matches_prev() definition as every line above, so it cannot drift.
        # Subjects with N>=MIN only (low-N cells are noise). Run on construction + final dumps and
        # diff to find where the LS scatters the weekly cadence most.
        print()
        print(f'ricorrenza UNPINNED per (gruppo,materia) [N>={MIN}, materia non-pin-only]:')
        by_gs = defaultdict(lambda: [0, 0])
        for g, d, h, s, t, pn in rows:
            if pn:
                continue
            by_gs[(g, s)][1] += 1
            if matches_prev(g, d, h, s, t):
                by_gs[(g, s)][0] += 1
        for g in sorted({k[0] for k in by_gs}):
            for s in sorted({k[1] for k in by_gs if k[0] == g}):
                n, d = by_gs[(g, s)]
                if d < MIN:
                    continue
                pct = 100 * n / d if d else 0
                print(f'  {g:10s} | {s[:34]:34s} {n}/{d} = {pct:.0f}%')


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if a != '--by-subject']
    by_subject = '--by-subject' in sys.argv
    if len(args) != 1:
        print(__doc__)
        print('  --by-subject  also print per-(group,subject) unpinned recurrence (N>=8)')
        sys.exit(1)
    main(args[0], by_subject=by_subject)
