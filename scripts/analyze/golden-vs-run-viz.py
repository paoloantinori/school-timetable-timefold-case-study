#!/usr/bin/env python3
"""Golden-vs-run visual comparator (TASK-407/408, maintainer 2026-07-24).

Generates a self-contained HTML page for HUMAN side-by-side inspection of the
golden timetable vs a solver run, per group:

  1. SEASON view — two aligned year strips per group (G above, N below), one
     cell per school day, colored by fill status vs the school's own expected
     hours (full / under-by-N / over / empty-but-expected / no-expectation).
     Stage days are hatched; the fact-based ideal end is a marker line; the
     past-ideal region is tinted. Clicking any cell pins a detail panel with
     the exact hours and subject lists on BOTH sides for that (group, date).
  2. FINGERPRINT view — per group, two weekday x slot grids (G | N) shading how
     often each cell is occupied across the year (% printed in the cell) —
     the recurring-pattern lens. Clicking a cell lists the top subjects that
     occupy it on both sides.
  3. TEACHERS view — the same weekday x slot fingerprint grids, but per
     TEACHER instead of per group (the Q22/TASK-412 fixed-days lens).
  4. SUBJECT x WEEK view — per group, one row per subject, one column per ISO
     week, hours in each cell, G and N side by side — the weekly-cadence lens
     (TASK-413's buchi+burst finding came from this view).

On top of those, the season view carries the APPLICABILITY TRIPLE (maintainer's
criterion: each of these three makes the schedule non-deliverable, so each is
a first-class visual marker plus a counter in the scoreboard header):

  1. BUCO-INTERNO, an empty slot with real lessons on both sides the same day
     (red ring + X glyph).
  2. LATE-START, a day whose first real lesson is later than the group's normal
     opening slot for that weekday, where "normal" is the MODE of the GOLDEN's
     own first slot per (group, weekday). Two golden-legitimate exceptions are
     drawn DISTINCTLY (dashed amber bar) and never counted as violations: the
     ceremonial first school day (2026-09-14, the whole school starts one slot
     late, 13/13 groups in the golden) and any (group, date) where the GOLDEN
     ITSELF starts late. A solid amber bar is a real violation.
  3. EMPTY-IN-SPAN, a school date with expected hours > 0, inside the group's
     used span (its first..last placed date on that side), holding ZERO placed
     lessons (white hatch over the dark red empty-day fill). Golden-legitimate
     exception, drawn as a thin white ring instead and not counted as a
     violation: a date the GOLDEN leaves empty too (e.g. 2027-01-22, the
     TASK-408 closure-typo Friday, 100% closed in the golden). Groups whose
     per-date sheet is not authoritative are skipped entirely, see
     SHEET_INFLATION_RATIO below.

Both golden-legitimacy rules make the golden's own violation counters 0 by
construction; that is the intended self-check, and the legitimate cases stay
visible on the strips (and counted separately) instead of disappearing.

Usage:
  python3 scripts/analyze/golden-vs-run-viz.py <run-lessons.txt> [-o out.html]

<run-lessons.txt> is the usual group|date|hour|subject|teacher|pinned dump
(laptop artifact lessons.txt or the db-query SELECT documented in
daily-fill-kpi.py). Expected hours come from calendario_fc.csv for
G04 / G08 / G13 and calendario_class_days.csv for the other groups
(same sourcing as the live DailyActivityQuota facts). The ideal end is
recomputed exactly like TargetEndDateCalculator's fact-based branch
(cumulate expected to the incarichi demand).
"""
import argparse
import csv
import json
import os
import sys

from collections import Counter, defaultdict
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / 'src/main/resources/seed'
GOLDEN = ROOT / 'claudedocs/spalmone-2026-27/data/lessons.csv'

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from shared import (hole_slots, real_slots, load_closures,  # noqa: E402
                    add_kpi_args, resolve_config)

YEAR_START, YEAR_END = date(2026, 9, 14), date(2027, 6, 10)
FC_GROUPS = {'G04', 'G08', 'G13'}

# A group whose calendar sheet claims at least this many times its incarichi
# demand has a per-date sheet that cannot be taken literally, so EMPTY-IN-SPAN
# is not evaluated for it (an "expected" day that carries no real demand is not
# a missing day). Measured on the 2026/27 seed: G14 asks 990 sheet hours
# against 115 hours of demand (ratio 8.6, the documented 9x G14 inflation
# artifact, CLAUDE.md Q23), every other group sits at or below 1.32.
SHEET_INFLATION_RATIO = 2.0

# Every marker below reads "occupied" through shared.real_slots, which drops
# the shared.KPI_EXCLUDED_PSEUDO rows (Stage/Esame/Fasullo): those are not real
# teaching, so they neither count as occupied nor can be blamed for a hole, a
# late start or an empty day (BUCO-INTERNO marker rule, 2026-07-26 maintainer).


def internal_hole_hours(day_lessons):
    """Hours (8..15) that sit strictly between a day's first and last REAL
    (non pseudo-teacher) lesson but hold none themselves - the same
    BUCO-INTERNO shape scripts/analyze/hole-topology-kpi.py classifies, via
    the shared shared.hole_slots core computation. Empty list if there's no
    such hole, including when the day has 0 or 1 real lessons."""
    return [8 + s for s in hole_slots(real_slots(day_lessons))]


def first_real_slot(day_lessons):
    """Canonical index (0-7) of the day's first REAL lesson, None if the day
    holds none (pseudo-teacher Stage/Esame rows do not open a day)."""
    slots = real_slots(day_lessons)
    return min(slots) if slots else None


def normal_first_slots(g_day, days):
    """(group, weekday) -> the GOLDEN's modal first real slot, i.e. the slot at
    which that group normally opens that weekday. Ties are broken towards the
    earlier slot so the mode is deterministic. Pairs the golden never teaches
    are absent, and a day of such a pair cannot be judged late."""
    firsts = defaultdict(Counter)
    for (g, d), lessons in g_day.items():
        if d not in days:
            continue
        s = first_real_slot(lessons)
        if s is not None:
            firsts[(g, d.weekday())][s] += 1
    return {k: min(c.items(), key=lambda kv: (-kv[1], kv[0]))[0]
            for k, c in firsts.items()}


def late_state(lessons, group, d, normal, golden_late):
    """LATE-START marker state for one (group, date) day on one side:
    0 = on time (or unjudgeable), 1 = late but GOLDEN-LEGITIMATE, 2 = late
    VIOLATION. Legitimate means the ceremonial first school day or a date the
    golden itself opens late for that same group (for the golden side that
    second clause is self-satisfied, hence its 0 violations by construction)."""
    normal_slot = normal.get((group, d.weekday()))
    if normal_slot is None:
        return 0
    s = first_real_slot(lessons)
    if s is None or s <= normal_slot:
        return 0
    return 1 if (d == YEAR_START or (group, d) in golden_late) else 2


def empty_state(lessons, group, d, expected, span_by_group, skip_groups, g_day):
    """EMPTY-IN-SPAN marker state for one (group, date) day on one side:
    0 = not an empty expected day inside the span (or not judgeable),
    1 = empty but GOLDEN-LEGITIMATE, 2 = empty VIOLATION. Legitimate means the
    golden leaves that same (group, date) empty too, so the golden side scores
    0 violations by construction. Groups in skip_groups are not judged at all
    (non-authoritative sheet, see SHEET_INFLATION_RATIO)."""
    if expected <= 0 or lessons or group in skip_groups:
        return 0
    span = span_by_group.get(group)
    if not span or not (span[0] <= d <= span[1]):
        return 0
    return 2 if g_day.get((group, d)) else 1


def used_spans(day_map_by_side, days):
    """group -> (first, last) date on which that side placed anything, the
    window inside which an expected-but-empty day is a hole in the delivery
    rather than a not-yet-started or already-finished stretch."""
    dates = defaultdict(list)
    for (g, d), lessons in day_map_by_side.items():
        if d in days and lessons:
            dates[g].append(d)
    return {g: (min(v), max(v)) for g, v in dates.items()}


def inflated_sheet_groups(exp, demand):
    """Groups whose per-date expected-hours sheet is not authoritative, see
    SHEET_INFLATION_RATIO."""
    sheet = defaultdict(int)
    for (g, _), v in exp.items():
        sheet[g] += v['h']
    return {g for g, h in sheet.items()
            if demand.get(g, 0) > 0 and h >= SHEET_INFLATION_RATIO * demand[g]}

# School closures: single Python-side source (claudedocs/.../data/closures.csv),
# loaded via shared.load_closures (TASK-444). Replaces the 28-date inline set that
# used to live here and was missing 2027-01-22; the canonical 29-date set now
# matches DataGenerator.CLOSURES_2026_27, so school_days() correctly excludes the
# closed Friday 2027-01-22 (a deliberate correctness fix - see VERDICT-444).
CLOSURES = load_closures()


def school_days():
    out, cur = [], YEAR_START
    while cur <= YEAR_END:
        if cur.weekday() < 5 and cur not in CLOSURES:
            out.append(cur)
        cur += timedelta(days=1)
    return out


def load_expected():
    """(group, date) -> {'h': expected hours, 'stage': bool}. FC sheet wins for its 3 groups."""
    exp = {}
    with open(SEED / 'calendario_fc.csv') as f:
        for r in csv.DictReader(f):
            d = date.fromisoformat(r['date'])
            h = int(r['aula_hours']) + int(r['lab_hours']) + int(r['stage_hours'])
            exp[(r['group'], d)] = {'h': h, 'stage': int(r['stage_hours']) > 0}
    with open(SEED / 'calendario_class_days.csv') as f:
        for r in csv.DictReader(f):
            if r['group'] in FC_GROUPS:
                continue
            d = date.fromisoformat(r['date'])
            exp[(r['group'], d)] = {'h': int(r['hours']), 'stage': r['modality'].strip() == 'S'}
    return exp


def load_demand():
    demand = defaultdict(int)
    with open(SEED / 'incarichi.csv') as f:
        for r in csv.DictReader(f):
            if r['teacher'].strip() == 'Totale ore corso':
                continue
            demand[r['group'].strip()] += int(float(r['hours']))
    return demand


def ideal_ends(exp, demand):
    """Mirror TargetEndDateCalculator's fact-based branch: cumulate expected to demand."""
    by_group = defaultdict(list)
    for (g, d), v in exp.items():
        if v['h'] > 0:
            by_group[g].append((d, v['h']))
    out = {}
    for g, rows in by_group.items():
        rows.sort()
        cum, last = 0, None
        for d, h in rows:
            cum += h
            last = d
            if cum >= demand.get(g, 0):
                break
        out[g] = min(last or YEAR_END, YEAR_END)
    return out


def load_golden():
    rows = []
    with open(GOLDEN) as f:
        for r in csv.DictReader(f):
            if r['closed'] == 'True':
                continue
            h = int(r['slot'].split(':')[0])
            rows.append((r['group'], date.fromisoformat(r['date']), h,
                         r['subject'].strip(), r['teacher'].strip()))
    return rows


def load_run(path):
    rows = []
    for line in open(path):
        t = line.strip()
        if not t or t == 'SET' or '|' not in t:
            continue
        f = [x.strip() for x in t.split('|')]
        if len(f) < 5:
            continue
        rows.append((f[0], date.fromisoformat(f[1]), int(float(f[2])), f[3], f[4]))
    return rows


def day_map(rows):
    m = defaultdict(list)
    for g, d, h, s, t in rows:
        m[(g, d)].append((h, s, t))
    return m


def build_payload(run_path):
    exp = load_expected()
    demand = load_demand()
    ideals = ideal_ends(exp, demand)
    g_rows, n_rows = load_golden(), load_run(run_path)
    g_day, n_day = day_map(g_rows), day_map(n_rows)
    days = school_days()
    groups = sorted({g for g, _ in exp} | {g for g, _, _, _, _ in g_rows} | {g for g, _, _, _, _ in n_rows})

    # APPLICABILITY TRIPLE support (see module docstring): the golden defines
    # both the normal opening slot per (group, weekday) and the legitimacy of
    # every late/empty day, so all of it is derived from g_day before the loop.
    dayset = set(days)
    normal = normal_first_slots(g_day, dayset)
    golden_late = {(g, d) for (g, d), lessons in g_day.items()
                   if d in dayset and late_state(lessons, g, d, normal, set()) == 2}
    spans = {'g': used_spans(g_day, dayset), 'n': used_spans(n_day, dayset)}
    skip_empty = inflated_sheet_groups(exp, demand)

    payload = {'days': [d.isoformat() for d in days], 'runLabel': str(run_path), 'groups': []}
    for g in groups:
        gd = {'name': g, 'demand': demand.get(g, 0),
              'ideal': ideals.get(g, YEAR_END).isoformat(), 'cells': [], 'fp': None,
              'stats': {}}
        gfull = nfull = gpast = npast = active = 0
        gholedays = nholedays = 0
        gdeep = ndeep = 0      # giornate a cui mancano >= 2 ore (forma del deficit)
        tri = {'gLate': 0, 'nLate': 0, 'gLateOk': 0, 'nLateOk': 0,
               'gEmpty': 0, 'nEmpty': 0, 'gEmptyOk': 0, 'nEmptyOk': 0}
        for d in days:
            e = exp.get((g, d), {'h': 0, 'stage': False})
            gl = sorted(g_day.get((g, d), []))
            nl = sorted(n_day.get((g, d), []))
            if e['h'] > 0:
                active += 1
                if len(gl) == e['h']:
                    gfull += 1
                if len(nl) == e['h']:
                    nfull += 1
                # Forma del deficit (maintainer 2026-07-29): dove finiscono le ore mancanti conta
                # piu' di quante siano. Il golden toglie UN'ora a molte giornate invece di
                # svuotarne alcune; concentrare lo stesso ammanco in giornate profonde alza
                # il tasso di giornate piene ma peggiora l'orario per la classe.
                if e['h'] - len(gl) >= 2:
                    gdeep += 1
                if e['h'] - len(nl) >= 2:
                    ndeep += 1
            if d > ideals.get(g, YEAR_END):
                gpast += len(gl)
                npast += len(nl)
            gHoles, nHoles = internal_hole_hours(gl), internal_hole_hours(nl)
            if gHoles:
                gholedays += 1
            if nHoles:
                nholedays += 1
            # applicability triple, markers 2 (late start) and 3 (empty in span)
            gLate = late_state(gl, g, d, normal, golden_late)
            nLate = late_state(nl, g, d, normal, golden_late)
            gEmpty = empty_state(gl, g, d, e['h'], spans['g'], skip_empty, g_day)
            nEmpty = empty_state(nl, g, d, e['h'], spans['n'], skip_empty, g_day)
            for side, late, empty in (('g', gLate, gEmpty), ('n', nLate, nEmpty)):
                if late:
                    tri[side + ('Late' if late == 2 else 'LateOk')] += 1
                if empty:
                    tri[side + ('Empty' if empty == 2 else 'EmptyOk')] += 1
            gd['cells'].append({
                'e': e['h'], 'st': 1 if e['stage'] else 0,
                'g': len(gl), 'n': len(nl),
                'gHoles': gHoles, 'nHoles': nHoles,
                'gLate': gLate, 'nLate': nLate,
                'gEmpty': gEmpty, 'nEmpty': nEmpty,
                'gs': [f"{h}:00 {s} ({t})" for h, s, t in gl],
                'ns': [f"{h}:00 {s} ({t})" for h, s, t in nl],
            })
        # weekday x slot occupancy fingerprints (share of the group's active days of
        # that weekday in which the slot is occupied) + top subjects per cell
        fp = {'g': [[0] * 8 for _ in range(5)], 'n': [[0] * 8 for _ in range(5)],
              'gsub': [[None] * 8 for _ in range(5)], 'nsub': [[None] * 8 for _ in range(5)]}
        wd_active = [0] * 5
        subs = {'g': defaultdict(lambda: defaultdict(int)), 'n': defaultdict(lambda: defaultdict(int))}
        for d in days:
            e = exp.get((g, d), {'h': 0})
            if e['h'] <= 0:
                continue
            wd = d.weekday()
            wd_active[wd] += 1
            for side, dm in (('g', g_day), ('n', n_day)):
                for h, s, _ in dm.get((g, d), []):
                    slot = h - 8 if h < 14 else h - 8  # hours 8..15 -> 0..7
                    if 0 <= slot < 8:
                        fp[side][wd][slot] += 1
                        subs[side][(wd, slot)][s] += 1
        for wd in range(5):
            for sl in range(8):
                denom = wd_active[wd] or 1
                for side in ('g', 'n'):
                    cnt = fp[side][wd][sl]
                    fp[side][wd][sl] = round(100 * cnt / denom)
                    top = sorted(subs[side][(wd, sl)].items(), key=lambda x: -x[1])[:3]
                    fp[side + 'sub'][wd][sl] = [f"{s} ({c})" for s, c in top]
        gd['fp'] = fp
        gd['stats'] = {'active': active, 'gfull': gfull, 'nfull': nfull,
                       'gpast': gpast, 'npast': npast,
                       'gholedays': gholedays, 'nholedays': nholedays,
                       'gdeep': gdeep, 'ndeep': ndeep,
                       'skipEmpty': 1 if g in skip_empty else 0, **tri}
        # subject x ISO-week hours matrix (G and N), rows sorted by golden volume desc
        weeks = sorted({d.isocalendar()[:2] for d in days})
        widx = {w: i for i, w in enumerate(weeks)}
        sw = {'g': defaultdict(lambda: [0] * len(weeks)), 'n': defaultdict(lambda: [0] * len(weeks))}
        for side, dm in (('g', g_day), ('n', n_day)):
            for d in days:
                for h, s, _ in dm.get((g, d), []):
                    sw[side][s][widx[d.isocalendar()[:2]]] += 1
        subjects = sorted(set(sw['g']) | set(sw['n']),
                          key=lambda s: -(sum(sw['g'][s]) + sum(sw['n'][s])))
        gd['sw'] = {'subjects': subjects,
                    'g': [sw['g'][s] for s in subjects],
                    'n': [sw['n'][s] for s in subjects]}
        payload['groups'].append(gd)

    payload['weekLabels'] = [f"{w[1]:02d}" for w in sorted({d.isocalendar()[:2] for d in days})]

    # applicability scoreboard totals (same numbers the header table and the
    # stdout summary print, so HTML and terminal can never disagree)
    keys = ['gholedays', 'nholedays', 'gdeep', 'ndeep', 'gLate', 'nLate', 'gLateOk', 'nLateOk',
            'gEmpty', 'nEmpty', 'gEmptyOk', 'nEmptyOk']
    payload['scoreboard'] = {k: sum(x['stats'][k] for x in payload['groups']) for k in keys}
    payload['scoreboard']['skippedGroups'] = sorted(skip_empty)

    # teacher weekly fingerprints (all groups combined; pseudo 'Stage' excluded)
    wd_school = [0] * 5
    for d in days:
        wd_school[d.weekday()] += 1
    teachers = sorted({t for _, _, _, _, t in g_rows} | {t for _, _, _, _, t in n_rows})
    payload['teachers'] = []
    for t in teachers:
        if t == 'Stage':
            continue
        fp = {'g': [[0] * 8 for _ in range(5)], 'n': [[0] * 8 for _ in range(5)],
              'gsub': [[None] * 8 for _ in range(5)], 'nsub': [[None] * 8 for _ in range(5)]}
        tot = {'g': 0, 'n': 0}
        subs = {'g': defaultdict(lambda: defaultdict(int)), 'n': defaultdict(lambda: defaultdict(int))}
        for side, rows in (('g', g_rows), ('n', n_rows)):
            for grp, d, h, s, tt in rows:
                if tt != t:
                    continue
                slot = h - 8
                if 0 <= slot < 8:
                    fp[side][d.weekday()][slot] += 1
                    subs[side][(d.weekday(), slot)][f"{s} · {grp}"] += 1
                    tot[side] += 1
        for wd in range(5):
            for sl in range(8):
                denom = wd_school[wd] or 1
                for side in ('g', 'n'):
                    cnt = fp[side][wd][sl]
                    fp[side][wd][sl] = round(100 * cnt / denom)
                    top = sorted(subs[side][(wd, sl)].items(), key=lambda x: -x[1])[:3]
                    fp[side + 'sub'][wd][sl] = [f"{s} ({c})" for s, c in top]
        payload['teachers'].append({'name': t, 'fp': fp, 'tot': tot})
    return payload


HTML = r"""<!DOCTYPE html>
<html lang="it"><head><meta charset="utf-8">
<title>Golden vs Run — confronto puntuale</title>
<style>
.viz-root{color-scheme:light;
  --surface-1:#fcfcfb;--surface-2:#f1f0ee;--line:#dedcd7;
  --text-primary:#0b0b0b;--text-secondary:#52514e;--text-muted:#8a887f;
  --full:#e6e5e1;
  --under-1:#f7d5c0;--under-2:#f0a577;--under-3:#eb6834;--under-4:#b23a10;
  --over-1:#c8dcf4;--over-2:#2a78d6;
  --stage:#4a3aa7;--alarm:#c81e1e;--late:#d97706;--ok:#177245;
  font-family:system-ui,sans-serif;background:var(--surface-1);color:var(--text-primary);}
body{margin:0}
.viz-root{padding:18px 22px}
h1{font-size:17px;margin:0 0 2px}
.sub{color:var(--text-secondary);font-size:12px;margin-bottom:12px}
.legend{display:flex;gap:14px;flex-wrap:wrap;align-items:center;font-size:11.5px;color:var(--text-secondary);margin:8px 0 14px}
.legend .sw{display:inline-block;width:13px;height:13px;border-radius:3px;vertical-align:-2px;margin-right:4px;border:1px solid var(--line)}
.tabs{margin:6px 0 12px}
.tabs button{font:inherit;font-size:12.5px;padding:5px 12px;border:1px solid var(--line);background:var(--surface-2);cursor:pointer;border-radius:6px;margin-right:6px}
.tabs button.on{background:var(--text-primary);color:#fff;border-color:var(--text-primary)}
.grp{margin:0 0 18px}
.grp h2{font-size:13px;margin:0 0 3px;display:flex;gap:14px;align-items:baseline}
.grp h2 .st{font-weight:400;font-size:11.5px;color:var(--text-secondary)}
.grp h2 .st b{color:var(--text-primary)}
.striprow{display:grid;grid-template-columns:26px 1fr;align-items:center;margin-bottom:2px}
.striplabel{font-size:10.5px;color:var(--text-muted);font-weight:600}
.strip{display:flex;height:20px}
.cell{flex:1 0 auto;min-width:5px;margin-right:1px;border-radius:2px;cursor:pointer;position:relative}
.cell.stage{background-image:repeating-linear-gradient(45deg,transparent 0 2px,rgba(74,58,167,.55) 2px 4px)}
.cell.pastideal{outline:1px solid rgba(11,11,11,.28);outline-offset:-1px}
.holebuco{box-shadow:inset 0 0 0 2px #c81e1e}
.holebuco::after{content:"\2715";position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:8px;line-height:1;color:#c81e1e;font-weight:700;pointer-events:none}
.sw.holebuco{position:relative}
/* APPLICABILITY TRIPLE markers 2 and 3. The late-start bar hugs the LEFT edge
   of the cell (the day opens too far right); solid amber = violation, dashed
   amber = golden-legitimate. The empty-in-span day is a hatch overlay
   (distinct from the hole's ring+X); a thin ring alone = golden-legitimate.
   Both are drawn as pseudo-element overlays plus box-shadow, NEVER as
   background-image: the script sets the cell colour with the inline
   `background` SHORTHAND, which resets background-image inline and so beats
   any stylesheet background-image (the reason .cell.stage's own hatch does
   not render either). Marker pairs cannot collide: an empty day has no
   lessons, hence no first slot to be late and no slot to hole. */
.latebar::before{content:"";position:absolute;left:0;top:0;bottom:0;width:3px;background:var(--late);
  border-right:1px solid rgba(0,0,0,.35);pointer-events:none}
.lateok::before{content:"";position:absolute;left:0;top:0;bottom:0;width:3px;pointer-events:none;
  background:repeating-linear-gradient(180deg,var(--late) 0 2px,transparent 2px 5px)}
/* An empty-in-span cell is ALWAYS painted --under-4 (dark red, "giorno atteso
   VUOTO") by fillColor, so its own marks are drawn in white for contrast: a
   thin ring alone is the legitimate case, ring + hatch the violation. */
.emptyok{box-shadow:inset 0 0 0 2px rgba(255,255,255,.9)}
.emptyspan{box-shadow:inset 0 0 0 1px rgba(255,255,255,.9)}
.emptyspan::after{content:"";position:absolute;inset:0;pointer-events:none;
  background:repeating-linear-gradient(-45deg,rgba(255,255,255,.9) 0 2px,transparent 2px 5px)}
.sw.latebar,.sw.lateok,.sw.emptyspan,.sw.emptyok{position:relative}
.score{border-collapse:collapse;font-size:11.5px;margin:2px 0 12px}
.score th,.score td{border:1px solid var(--line);padding:2px 7px;text-align:center;white-space:nowrap}
.score th{background:var(--surface-2);color:var(--text-secondary);font-weight:600}
.score td.g{text-align:left;font-weight:600}
.score tr.tot td,.score tr.tot th{font-weight:700;background:var(--surface-2)}
.score .par{color:var(--ok)}
.score .bad{color:var(--alarm);font-weight:700}
.score .na{color:var(--text-muted)}
.scorenote{font-size:10.5px;color:var(--text-muted);margin:-8px 0 12px}
.cell.monthstart{margin-left:4px}
.cell.sel{outline:2px solid var(--text-primary);outline-offset:-1px;z-index:2}
.months{display:grid;grid-template-columns:26px 1fr;font-size:10px;color:var(--text-muted);margin-bottom:2px}
.months .ax{display:flex}
.months .m{flex:1 0 auto;min-width:5px;margin-right:1px;overflow:visible;white-space:nowrap}
.months .m.monthstart{margin-left:4px}
.idealnote{font-size:10.5px;color:var(--text-secondary);margin:1px 0 0 26px}
#detail{position:fixed;right:16px;top:16px;width:330px;max-height:82vh;overflow:auto;background:#fff;border:1px solid var(--line);border-radius:10px;box-shadow:0 6px 24px rgba(0,0,0,.12);padding:12px 14px;font-size:12px;display:none;z-index:9}
#detail h3{margin:0 0 6px;font-size:12.5px}
#detail .side{margin:6px 0}
#detail .side b{font-size:11.5px}
#detail ul{margin:3px 0 0;padding-left:16px}
#detail .close{float:right;cursor:pointer;border:none;background:none;font-size:14px}
.fpgrp{display:flex;gap:26px;align-items:flex-start;margin:4px 0 8px}
.fpg table{border-collapse:collapse}
.fpg caption{font-size:11px;color:var(--text-secondary);text-align:left;margin-bottom:3px;font-weight:600}
.fpg td,.fpg th{border:1px solid var(--surface-1);font-size:10px;text-align:center;width:30px;height:22px;padding:0}
.fpg th{background:none;color:var(--text-muted);font-weight:500}
.fpg td{cursor:pointer;color:var(--text-primary)}
.hint{font-size:11px;color:var(--text-muted);margin-bottom:8px}
</style></head>
<body><div class="viz-root">
<h1>Golden (G) vs Run (N) — confronto puntuale per gruppo</h1>
<div class="sub" id="runlabel"></div>
<h2 style="font-size:13px;margin:10px 0 4px">Applicabilita' (triade): ogni violazione rende l'orario non consegnabile</h2>
<div id="scoreboard"></div>
<div class="tabs">
  <button id="tab-season" class="on">Stagione (riempimento per giorno)</button>
  <button id="tab-fp">Fingerprint settimanale (pattern ricorrenti)</button>
  <button id="tab-teach">Docenti (fingerprint)</button>
  <button id="tab-sw">Materie × settimana</button>
</div>
<div class="legend" id="legend-season">
  <span><span class="sw" style="background:var(--full)"></span>giorno pieno (= ore attese)</span>
  <span><span class="sw" style="background:var(--under-1)"></span>−1/−2h</span>
  <span><span class="sw" style="background:var(--under-2)"></span>−3/−4h</span>
  <span><span class="sw" style="background:var(--under-3)"></span>−5h o più</span>
  <span><span class="sw" style="background:var(--under-4)"></span>giorno atteso VUOTO</span>
  <span><span class="sw" style="background:var(--over-1)"></span>+1/+2h</span>
  <span><span class="sw" style="background:var(--over-2)"></span>+3h o più</span>
  <span><span class="sw" style="background:var(--surface-2)"></span>nessuna attesa</span>
  <span><span class="sw cell stage" style="background:var(--surface-2)"></span>giorno stage</span>
  <span><span class="sw" style="background:#fff;outline:1px solid rgba(11,11,11,.4);outline-offset:-1px"></span>oltre ideal end</span>
  <span><span class="sw holebuco" style="background:var(--surface-2)"></span>buco interno (mai nel golden)</span>
  <span><span class="sw latebar" style="background:var(--surface-2)"></span>inizio tardivo (violazione)</span>
  <span><span class="sw lateok" style="background:var(--surface-2)"></span>inizio tardivo legittimo (14/09 o come il golden)</span>
  <span><span class="sw cell emptyspan" style="background:var(--under-4)"></span>giorno vuoto dentro lo span (violazione)</span>
  <span><span class="sw cell emptyok" style="background:var(--under-4)"></span>giorno vuoto legittimo (vuoto anche nel golden)</span>
</div>
<div class="hint">Click su una cella per il dettaglio del giorno (materie e ore, G e N fianco a fianco). La riga G è il golden della scuola, la N è il nostro run.</div>
<div id="view-season"></div>
<div id="view-fp" style="display:none"></div>
<div id="view-teach" style="display:none"></div>
<div id="view-sw" style="display:none"></div>
<div id="detail"></div>
<script>
const DATA = __DATA__;
const days = DATA.days.map(s=>new Date(s+"T00:00:00"));
document.getElementById('runlabel').textContent = "Run: "+DATA.runLabel+"  ·  attese dal calendario per-data della scuola (FC sheet per 1/G08+G13)";
const css = getComputedStyle(document.querySelector('.viz-root'));
function fillColor(placed, exp){
  if(exp<=0) return css.getPropertyValue('--surface-2');
  const d = placed-exp;
  if(d===0) return css.getPropertyValue('--full');
  if(placed===0) return css.getPropertyValue('--under-4');
  if(d<0) return d>=-2?css.getPropertyValue('--under-1'):(d>=-4?css.getPropertyValue('--under-2'):css.getPropertyValue('--under-3'));
  return d<=2?css.getPropertyValue('--over-1'):css.getPropertyValue('--over-2');
}
function pct(a,b){return b? Math.round(100*a/b)+"%":"–";}
// Applicability scoreboard: N (run) vs G (golden) for the three killers.
// Verde = alla pari col golden (o meglio), rosso = peggio del golden.
function scoreCell(n,g,skipped){
  if(skipped) return `<td class="na" title="gruppo escluso dal conteggio: foglio attese non autoritativo">n/d</td>`;
  const cls = n<=g ? 'par' : 'bad';
  return `<td class="${cls}">${n} vs ${g}</td>`;
}
{
  const sb=DATA.scoreboard;
  let html=`<table class="score"><tr><th>gruppo</th><th>buchi interni<br>N vs G</th>`+
    `<th>inizio tardivo (violazioni)<br>N vs G</th><th>giorni vuoti in span<br>N vs G</th>`+
    `<th title="giornate a cui mancano 2 o piu' ore: il golden preferisce togliere UN'ora a `+
    `molte giornate invece di svuotarne alcune, quindi qui il tasso di giornate piene puo' `+
    `ingannare">giornate profonde (-2h o piu')<br>N vs G</th>`+
    `<th>inizi tardivi legittimi<br>N / G</th></tr>`;
  DATA.groups.forEach(g=>{
    const s=g.stats;
    html+=`<tr><td class="g">${g.name}</td>`+
      scoreCell(s.nholedays,s.gholedays,false)+
      scoreCell(s.nLate,s.gLate,false)+
      scoreCell(s.nEmpty,s.gEmpty,s.skipEmpty)+
      scoreCell(s.ndeep,s.gdeep,false)+
      `<td class="na">${s.nLateOk} / ${s.gLateOk}</td></tr>`;
  });
  html+=`<tr class="tot"><td class="g">TOTALE</td>`+
    scoreCell(sb.nholedays,sb.gholedays,false)+
    scoreCell(sb.nLate,sb.gLate,false)+
    scoreCell(sb.nEmpty,sb.gEmpty,false)+
    scoreCell(sb.ndeep,sb.gdeep,false)+
    `<td class="na">${sb.nLateOk} / ${sb.gLateOk}</td></tr></table>`;
  document.getElementById('scoreboard').innerHTML=html;
  const skipNote=sb.skippedGroups.length?` · esclusi dai giorni vuoti (foglio attese non autoritativo): ${sb.skippedGroups.join(', ')}`:'';
  const note=document.createElement('div');note.className='scorenote';
  note.textContent="Le violazioni del golden sono 0 per costruzione (e' il golden a definire la legittimita'): la colonna G serve da self-check, i casi legittimi restano visibili sulle strisce e contati a parte."+skipNote;
  document.getElementById('scoreboard').appendChild(note);
}
const seasonEl = document.getElementById('view-season');
let monthStarts = new Set();
{let prev=-1; days.forEach((d,i)=>{ if(d.getMonth()!==prev){monthStarts.add(i);prev=d.getMonth();}});}
const MONTHS=['gen','feb','mar','apr','mag','giu','lug','ago','set','ott','nov','dic'];
function monthAxis(){
  const ax=document.createElement('div');ax.className='months';
  ax.appendChild(document.createElement('span'));
  const inner=document.createElement('div');inner.className='ax';
  days.forEach((d,i)=>{const m=document.createElement('span');m.className='m'+(monthStarts.has(i)?' monthstart':'');
    if(monthStarts.has(i)) m.textContent=MONTHS[d.getMonth()];inner.appendChild(m);});
  ax.appendChild(inner);return ax;
}
DATA.groups.forEach((g,gi)=>{
  const div=document.createElement('div');div.className='grp';
  const idealIdx = DATA.days.findIndex(d=>d>g.ideal);
  const h=document.createElement('h2');
  h.innerHTML = `${g.name} <span class="st">giorni pieni G <b>${pct(g.stats.gfull,g.stats.active)}</b> vs N <b>${pct(g.stats.nfull,g.stats.active)}</b></span>`+
    ` <span class="st">ore oltre ideal-end (${g.ideal}) G <b>${g.stats.gpast}</b> vs N <b>${g.stats.npast}</b></span>`+
    ` <span class="st" style="color:#c81e1e">buchi interni G <b>${g.stats.gholedays}</b> vs N <b>${g.stats.nholedays}</b></span>`+
    ` <span class="st" style="color:#d97706">inizio tardivo (viol.) G <b>${g.stats.gLate}</b> vs N <b>${g.stats.nLate}</b></span>`+
    ` <span class="st" style="color:#c81e1e">vuoti in span G <b>${g.stats.skipEmpty?'n/d':g.stats.gEmpty}</b> vs N <b>${g.stats.skipEmpty?'n/d':g.stats.nEmpty}</b></span>`+
    ` <span class="st">domanda ${g.demand}h</span>`;
  div.appendChild(h);
  div.appendChild(monthAxis());
  [['G','g'],['N','n']].forEach(([lab,key])=>{
    const row=document.createElement('div');row.className='striprow';
    const l=document.createElement('span');l.className='striplabel';l.textContent=lab;row.appendChild(l);
    const strip=document.createElement('div');strip.className='strip';
    g.cells.forEach((c,i)=>{
      const cell=document.createElement('div');
      const holes=key==='g'?c.gHoles:c.nHoles;
      const late=key==='g'?c.gLate:c.nLate, empty=key==='g'?c.gEmpty:c.nEmpty;
      cell.className='cell'+(c.st?' stage':'')+(idealIdx>=0&&i>=idealIdx?' pastideal':'')+(monthStarts.has(i)?' monthstart':'')+(holes&&holes.length?' holebuco':'')
        +(late===2?' latebar':(late===1?' lateok':''))+(empty===2?' emptyspan':(empty===1?' emptyok':''));
      cell.style.background=fillColor(c[key],c.e);
      cell.title=`${g.name} ${DATA.days[i]} (attese ${c.e}h, G ${c.g}h, N ${c.n}h)`+
        (holes&&holes.length?` BUCO INTERNO ${lab} @ ${holes.map(h=>h+':00').join(', ')}`:'')+
        (late?` ${late===2?'INIZIO TARDIVO':'inizio tardivo legittimo'} ${lab}`:'')+
        (empty?` ${empty===2?'GIORNO VUOTO IN SPAN':'giorno vuoto legittimo'} ${lab}`:'');
      cell.onclick=()=>showDetail(gi,i,cell);
      strip.appendChild(cell);
    });
    row.appendChild(strip);div.appendChild(row);
  });
  seasonEl.appendChild(div);
});
function showDetail(gi,i,cellEl){
  document.querySelectorAll('.cell.sel').forEach(e=>e.classList.remove('sel'));
  if(cellEl) cellEl.classList.add('sel');
  const g=DATA.groups[gi],c=g.cells[i];
  const el=document.getElementById('detail');
  el.style.display='block';
  const holeLine=(label,holes)=>(holes&&holes.length)
    ?`<div style="color:#c81e1e"><b>buco interno ${label}</b>: slot ${holes.map(h=>h+':00').join(', ')}</div>`:'';
  const lateLine=(label,st)=>st?`<div style="color:${st===2?'#d97706':'var(--text-muted)'}"><b>inizio tardivo ${label}</b>${st===2?' (VIOLAZIONE)':' (legittimo: 14/09 o tardivo anche nel golden)'}</div>`:'';
  const emptyLine=(label,st)=>st?`<div style="color:${st===2?'#c81e1e':'var(--text-muted)'}"><b>giorno vuoto dentro lo span ${label}</b>${st===2?' (VIOLAZIONE)':' (legittimo: vuoto anche nel golden)'}</div>`:'';
  el.innerHTML=`<button class="close" onclick="this.parentElement.style.display='none'">✕</button>
    <h3>${g.name}, ${DATA.days[i]} ${c.st?'· <span style="color:var(--stage)">stage</span>':''}</h3>
    <div>ore attese: <b>${c.e}</b>${DATA.days[i]>g.ideal?' · <b>oltre ideal end</b>':''}</div>
    ${holeLine('G',c.gHoles)}${holeLine('N',c.nHoles)}
    ${lateLine('G',c.gLate)}${lateLine('N',c.nLate)}
    ${emptyLine('G',c.gEmpty)}${emptyLine('N',c.nEmpty)}
    <div class="side"><b>G (golden): ${c.g}h</b><ul>${c.gs.map(x=>`<li>${x}</li>`).join('')||'<li>(nessuna)</li>'}</ul></div>
    <div class="side"><b>N (run): ${c.n}h</b><ul>${c.ns.map(x=>`<li>${x}</li>`).join('')||'<li>(nessuna)</li>'}</ul></div>`;
}
// Fingerprint view
const fpEl=document.getElementById('view-fp');
const WD=['Lun','Mar','Mer','Gio','Ven'];
function fpColor(p){
  if(p<=0) return css.getPropertyValue('--surface-2');
  const stops=[[233,240,250],[188,214,242],[106,160,220],[42,120,214],[18,58,104]];
  const idx=p>=95?4:(p>=70?3:(p>=40?2:(p>=15?1:0)));
  const [r,gg,b]=stops[idx];return `rgb(${r},${gg},${b})`;
}
DATA.groups.forEach((g,gi)=>{
  const div=document.createElement('div');div.className='grp';
  div.innerHTML=`<h2>${g.name} <span class="st">% di occupazione della cella (giorno × ora) sull'anno — i pattern ricorrenti emergono come colonne/righe scure</span></h2>`;
  const wrap=document.createElement('div');wrap.className='fpgrp';
  [['G (golden)','g','gsub'],['N (run)','n','nsub']].forEach(([cap,key,skey])=>{
    const t=document.createElement('table');t.className='fpg';
    let html=`<caption>${cap}</caption><tr><th></th>${[...Array(8)].map((_,s)=>`<th>${8+s}:00</th>`).join('')}</tr>`;
    for(let wd=0;wd<5;wd++){
      html+=`<tr><th>${WD[wd]}</th>`;
      for(let s=0;s<8;s++){
        const p=g.fp[key][wd][s];
        const ink=p>=70?'#fff':'inherit';
        html+=`<td style="background:${fpColor(p)};color:${ink}" data-wd="${wd}" data-s="${s}" data-k="${skey}" data-gi="${gi}">${p||''}</td>`;
      }
      html+='</tr>';
    }
    t.innerHTML=html;
    const holder=document.createElement('div');holder.className='fpg';holder.appendChild(t);wrap.appendChild(holder);
  });
  div.appendChild(wrap);fpEl.appendChild(div);
});
fpEl.addEventListener('click',ev=>{
  const td=ev.target.closest('td');if(!td||td.dataset.gi===undefined)return;
  const g=DATA.groups[+td.dataset.gi];
  const wd=+td.dataset.wd,s=+td.dataset.s;
  const el=document.getElementById('detail');el.style.display='block';
  el.innerHTML=`<button class="close" onclick="this.parentElement.style.display='none'">✕</button>
    <h3>${g.name} — ${WD[wd]} ${8+s}:00</h3>
    <div class="side"><b>G: ${g.fp.g[wd][s]}% dei ${WD[wd]}</b><ul>${g.fp.gsub[wd][s].map(x=>`<li>${x}</li>`).join('')||'<li>—</li>'}</ul></div>
    <div class="side"><b>N: ${g.fp.n[wd][s]}% dei ${WD[wd]}</b><ul>${g.fp.nsub[wd][s].map(x=>`<li>${x}</li>`).join('')||'<li>—</li>'}</ul></div>`;
});
// Teacher fingerprint view (same grid machinery, keyed by teacher; denominator = school days)
const teachEl=document.getElementById('view-teach');
DATA.teachers.forEach((t,ti)=>{
  const div=document.createElement('div');div.className='grp';
  div.innerHTML=`<h2>${t.name} <span class="st">ore totali G <b>${t.tot.g}</b> vs N <b>${t.tot.n}</b> · % sui giorni di scuola — buchi e costanza del docente</span></h2>`;
  const wrap=document.createElement('div');wrap.className='fpgrp';
  [['G (golden)','g','gsub'],['N (run)','n','nsub']].forEach(([cap,key,skey])=>{
    const tb=document.createElement('table');tb.className='fpg';
    let html=`<caption>${cap}</caption><tr><th></th>${[...Array(8)].map((_,s)=>`<th>${8+s}:00</th>`).join('')}</tr>`;
    for(let wd=0;wd<5;wd++){
      html+=`<tr><th>${WD[wd]}</th>`;
      for(let s=0;s<8;s++){
        const p=t.fp[key][wd][s];
        const ink=p>=70?'#fff':'inherit';
        html+=`<td style="background:${fpColor(p)};color:${ink}" data-wd="${wd}" data-s="${s}" data-ti="${ti}" data-k="${key}">${p||''}</td>`;
      }
      html+='</tr>';
    }
    tb.innerHTML=html;
    const holder=document.createElement('div');holder.className='fpg';holder.appendChild(tb);wrap.appendChild(holder);
  });
  div.appendChild(wrap);teachEl.appendChild(div);
});
teachEl.addEventListener('click',ev=>{
  const td=ev.target.closest('td');if(!td||td.dataset.ti===undefined)return;
  const t=DATA.teachers[+td.dataset.ti];const wd=+td.dataset.wd,s=+td.dataset.s;
  const el=document.getElementById('detail');el.style.display='block';
  el.innerHTML=`<button class="close" onclick="this.parentElement.style.display='none'">✕</button>
    <h3>${t.name} — ${WD[wd]} ${8+s}:00</h3>
    <div class="side"><b>G: ${t.fp.g[wd][s]}%</b><ul>${t.fp.gsub[wd][s].map(x=>`<li>${x}</li>`).join('')||'<li>—</li>'}</ul></div>
    <div class="side"><b>N: ${t.fp.n[wd][s]}%</b><ul>${t.fp.nsub[wd][s].map(x=>`<li>${x}</li>`).join('')||'<li>—</li>'}</ul></div>`;
});
// Subject x week matrix view
const swEl=document.getElementById('view-sw');
function swColor(h){
  if(h<=0) return css.getPropertyValue('--surface-2');
  const stops=[[233,240,250],[188,214,242],[106,160,220],[42,120,214],[18,58,104]];
  const idx=h>=8?4:(h>=5?3:(h>=3?2:(h>=2?1:0)));
  const [r,gg,b]=stops[idx];return `rgb(${r},${gg},${b})`;
}
DATA.groups.forEach(g=>{
  const div=document.createElement('div');div.className='grp';
  div.innerHTML=`<h2>${g.name} <span class="st">ore per materia per settimana ISO — righe G sopra, N sotto: continuità, partenze/arresti, spalmone per materia</span></h2>`;
  const t=document.createElement('table');t.className='fpg swt';
  let html=`<tr><th style="text-align:left">materia</th><th></th>${DATA.weekLabels.map(w=>`<th>${w}</th>`).join('')}</tr>`;
  g.sw.subjects.forEach((s,si)=>{
    [['G','g'],['N','n']].forEach(([lab,key])=>{
      html+=`<tr><th style="text-align:left;font-size:9.5px;max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${lab==='G'?s:''}</th><th>${lab}</th>`;
      g.sw[key][si].forEach(h=>{
        const ink=h>=5?'#fff':'inherit';
        html+=`<td style="background:${swColor(h)};color:${ink};width:20px">${h||''}</td>`;
      });
      html+='</tr>';
    });
    html+=`<tr style="height:3px"><td colspan="${DATA.weekLabels.length+2}" style="border:none"></td></tr>`;
  });
  t.innerHTML=html;
  div.appendChild(t);swEl.appendChild(div);
});
// tabs
const tabs={'tab-season':'view-season','tab-fp':'view-fp','tab-teach':'view-teach','tab-sw':'view-sw'};
Object.keys(tabs).forEach(id=>{
  document.getElementById(id).onclick=()=>{
    Object.entries(tabs).forEach(([tid,vid])=>{
      document.getElementById(tid).classList.toggle('on',tid===id);
      document.getElementById(vid).style.display=(tid===id)?'':'none';
    });
    document.getElementById('legend-season').style.display=(id==='tab-season')?'':'none';
  };
});
</script>
</div></body></html>
"""


def print_scoreboard(payload):
    """Same numbers the HTML header table shows, on stdout with a fixed
    APPLICABILITY prefix so a pipeline can grep them without parsing HTML."""
    sb = payload['scoreboard']
    print(f"APPLICABILITY run={payload['runLabel']}")
    print(f"APPLICABILITY {'group':10s} {'holes N/G':>12s} {'lateViol N/G':>14s} "
          f"{'emptySpan N/G':>15s} {'deepDays N/G':>14s} {'lateLegit N/G':>15s}")
    for x in payload['groups'] + [{'name': 'TOTAL', 'stats': dict(sb, skipEmpty=0)}]:
        s = x['stats']
        holes = f"{s['nholedays']}/{s['gholedays']}"
        late = f"{s['nLate']}/{s['gLate']}"
        empty = 'n/d' if s['skipEmpty'] else f"{s['nEmpty']}/{s['gEmpty']}"
        legit = f"{s['nLateOk']}/{s['gLateOk']}"
        deep = f"{s['ndeep']}/{s['gdeep']}"
        print(f"APPLICABILITY {x['name']:10s} {holes:>12s} {late:>14s} "
              f"{empty:>15s} {deep:>14s} {legit:>15s}")
    if sb['skippedGroups']:
        print("APPLICABILITY empty-in-span not evaluated for "
              f"{', '.join(sb['skippedGroups'])} (non-authoritative expected-hours sheet)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('run', help='run lessons dump (group|date|hour|subject|teacher|pinned)')
    ap.add_argument('-o', '--out', default=None,
                    help='HTML output path (default <data-dir>/confronto-golden-vs-run.html)')
    add_kpi_args(ap)  # --year/--data-dir/--lessons-path/--closures-path/--ramp/--year-start/--year-end
    a = ap.parse_args()
    cfg = resolve_config(a)
    global GOLDEN, CLOSURES, YEAR_START, YEAR_END
    GOLDEN = Path(cfg.lessons_path)
    CLOSURES = load_closures(cfg.closures_path)
    YEAR_START, YEAR_END = cfg.year_start, cfg.year_end
    out = a.out or str(Path(cfg.data_dir).parent / 'confronto-golden-vs-run.html')
    payload = build_payload(a.run)
    html = HTML.replace('__DATA__', json.dumps(payload, ensure_ascii=False))
    Path(out).write_text(html)
    print_scoreboard(payload)
    print(f"written {out}  ({len(html)//1024} KiB, {len(payload['groups'])} groups, {len(payload['days'])} days)")


if __name__ == '__main__':
    main()
