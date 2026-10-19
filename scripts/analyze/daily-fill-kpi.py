#!/usr/bin/env python3
"""Daily-fill resemblance KPI (TASK-385, maintainer 2026-07-21).

Full-day rate = share of (group, date) days where the PLACED lesson-hours
equal the school's own EXPECTED hours for that day. EXPECTED is the authoritative
DailyActivityQuota (aulaHours+labHours, the same source the solver's M20 targets),
read from the export.json.gz capture-run.sh writes next to the dump; falls back to
calendario_class_days.csv (legacy, inflated) only when no export is found.
Reference values (Saturdays excluded): golden ~91,9% ex-G14; run 30b 90,1%
(31 deep-deficit days). Pre-2026-07-30 this read class_days.csv for all groups,
which carried closure/no-session phantoms + the FC aula/lab split error and read
deep 51/full-day 88,6% (the artifact; see task457-falsificazione "METODOLOGIA RISOLTA").

Usage:
  python3 scripts/analyze/daily-fill-kpi.py <lessons-dump.txt>

where <lessons-dump.txt> is the output of:
  ./scripts/db/db-query.sh "SELECT g.name || '|' || ts.startdatetime::date ||
    '|' || extract(hour from ts.startdatetime) || '|' || c.subject || '|' ||
    t.name || '|' || l.pinned FROM lesson l JOIN course c ON l.course_id=c.id
    JOIN studentgroup g ON c.studentgroup_id=g.id JOIN teacher t ON
    c.teacher_id=t.id JOIN timeslot ts ON l.timeslot_id=ts.id"

Prints: full/under/over day counts, deficit histogram, per-group breakdown.

Java mirror (NOT the same definition - do not compare denominators directly):
WeeklyTemplateConstructionRealSeedTest's KPI block excludes ALL stage-modality
rows (~270, including weekday-placeable ones, because that fixture seeds no Stage
course) plus G14, so its denominator (~1972) is smaller than this script's. This
Python script excludes only off-grid Saturdays (26) from a production-run dump where
weekday stage rows ARE placeable via stage pins, so its denominator (~2387) is
larger. The Saturday fix is Python-only; the Java mirror already drops Saturdays via
its broader stage-modality filter, so no Java change is needed for the Saturday fix.
"""
import csv
import datetime
import gzip
import json
import os
import sys
from collections import Counter

import argparse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shared import SCHOOL_YEAR_START, SCHOOL_YEAR_END, add_kpi_args  # noqa: E402

LO, HI = SCHOOL_YEAR_START.isoformat(), SCHOOL_YEAR_END.isoformat()
SEED = 'src/main/resources/seed/calendario_class_days.csv'


def _is_offgrid_saturday(date: str) -> bool:
    """True for Saturday dates. The solver grid is Mon-Fri (no Saturday Timeslot
    exists), so any expected hours calendario_class_days.csv lists for a Saturday
    (26 off-grid Saturday stage rows = 187h, G10 7 / G12 19) are unplaceable phantoms.
    Excluded SCRIPT-WIDE at the expected-hours source so no metric (full-day rate,
    deficit shape, deep days, monthly bucket) counts them as a deficit the school
    could fill. Pre-2026-07-30 only the monthly bucket excluded them, which made
    the run-30b headline deep count read 77 when the placeable deep days were 51.
    KNOWN RESIDUAL (2026-07-30 altitude review): Saturday is one off-grid subset;
    ~12 weekday closure-date phantoms (Natale/Pasqua + 2027-01-22, where
    DataGenerator emits no Timeslot so placed==0) are the SAME root cause but are
    NOT excluded here, so the deep count still reads ~51 vs ~37 truly placeable (7
    of them land in March, contaminating the mar-giu tier-drift bucket). Generalizing
    to all off-grid dates needs the closure source resolved first (closures.csv
    carries a 2026-01-22 typo vs the real 2027-01-22 closure). Tracked as follow-up."""
    return datetime.date.fromisoformat(date).weekday() == 5


def main(dump_path: str) -> None:
    # EXPECTED source = authoritative DailyActivityQuota (aula+lab), read from the
    # export.json.gz that capture-run.sh writes next to the dump. This is the SAME
    # source the solver's M20 targets, and it excludes by construction the three
    # artifacts that calendario_class_days.csv carries: (1) weekday-closure phantoms
    # (rows on closure dates with no Timeslot), (2) no-session end-of-year dates
    # (group finished, quota 0), (3) the FC aula/lab overstatement (class_days
    # collapses the split wrong). On run 30b this takes deep-deficit 51->31 and
    # full-day ex-G14 88.6%->90.1% (denominator 2198->1928); 31 matches SPIKE 3
    # and the M20 target. Falls back to class_days.csv (legacy, inflated) with a
    # warning if no export is found, so the script still runs on a bare dump.
    # CAVEAT (declared, not fixed): `placed` below counts ALL dump rows incl the Stage
    # pseudo-teacher, while `expected` is aula+lab. On a mixed aula+stage day the bases
    # differ; exactly 1 such day exists in 2026/27 (G08 2027-04-01), effect negligible
    # and no impact on certified numbers. Not fixed because excluding Stage from `placed`
    # would perturb the whole historical series for a 1-day effect.
    export_gz = os.path.join(os.path.dirname(os.path.abspath(dump_path)), 'export.json.gz')
    expected = {}
    if os.path.exists(export_gz):
        root = json.load(gzip.open(export_gz))
        for r in root.get('dailyActivityQuotas', []):
            g = (r.get('studentGroup') or {}).get('name')
            dt = str(r.get('date', ''))
            h = int(r.get('aulaHours', 0) or 0) + int(r.get('labHours', 0) or 0)
            if g is None or h <= 0 or not (LO <= dt <= HI) or _is_offgrid_saturday(dt):
                continue
            expected[(g, dt)] = h
        print(f'expected source: DailyActivityQuota (aula+lab) from {export_gz}')
    else:
        print(f'WARNING: no export.json.gz next to dump; FALLING BACK to {SEED} '
              f'(carries closure/no-session phantoms + FC split errors; deep count inflated)')
        for r in csv.DictReader(open(SEED)):
            h = int(float(r['hours']))
            if h > 0 and LO <= r['date'] <= HI and not _is_offgrid_saturday(r['date']):
                expected[(r['group'], r['date'])] = h

    placed = Counter()
    for line in open(dump_path):
        line = line.strip()
        if '|' not in line:
            continue
        p = [x.strip() for x in line.split('|')]
        if len(p) != 6:
            continue
        placed[(p[0], p[1])] += 1

    full = under = over = deficit = 0
    deficit_hist = Counter()
    by_group = Counter()
    by_group_tot = Counter()
    deep_by_group = Counter()
    shape_hist = Counter()   # deficit shape, G14 excluded (see the note below)
    for (g, d), exp in expected.items():
        got = placed.get((g, d), 0)
        by_group_tot[g] += 1
        if got == exp:
            full += 1
            by_group[g] += 1
        elif got < exp:
            under += 1
            deficit += exp - got
            deficit_hist[exp - got] += 1
            if g != 'G14':
                shape_hist[exp - got] += 1
                if exp - got >= 2:
                    deep_by_group[g] += 1
        else:
            over += 1
    tot = len(expected)
    print(f'giorni attesi in orizzonte: {tot}')
    print(f'FULL-DAY RATE: {full}/{tot} = {100 * full / tot:.1f}%  '
          f'(golden ~91,9% ex-G14; run 30b 90,1%)')
    # G14 is exempt from the day-fill constraints in the solver itself (Q16 /
    # SchoolScheduleConstraintProvider.isG14: the sheet gives it 990h, the real
    # course is ~115h - a short overview course, confirmed by maintainer 2026-07-29).
    # Counting its days here measures nothing the model tries to achieve, and it
    # drags both the run and the golden down by the same phantom 171 days, so the
    # exempt rate is printed alongside the raw one rather than replacing it.
    lap_f, lap_t = by_group['G14'], by_group_tot['G14']
    if lap_t:
        print(f'FULL-DAY RATE senza G14 (esente nel motore, Q16): '
              f'{full - lap_f}/{tot - lap_t} = {100 * (full - lap_f) / (tot - lap_t):.1f}%')
    print(f'sotto: {under} (deficit {deficit}h), sopra: {over}')
    print('istogramma deficit:', dict(sorted(deficit_hist.items())))

    # DEFICIT SHAPE (maintainer 2026-07-29). The full-day rate alone rewards the wrong shape.
    # When a class is planned more capacity than its courses contain (G04: 835 aula+lab
    # hours planned in the FC sheet against 750 available), some hours cannot be filled by
    # anyone, and WHERE the shortfall lands is what a class actually feels. The golden shaves
    # ONE hour off many days rather than emptying a few: on G04 it runs 90 days at -1h and
    # zero deeper, where our runs concentrate the same shortfall into deeper days and thereby
    # score BETTER on the full-day rate while being worse for the school. So: deep days are
    # the number to watch, the rate alone can move the wrong way.
    # Golden baseline (CSV conversion, APPROXIMATE - no golden DB dump exists, so the
    # exact deep count is conversion-dependent, ~9): the golden concentrates its few
    # deep days on G08 and keeps G04 at ZERO deep. Measure on a golden DB dump
    # (GoldenScheduleImportService) for the authoritative count.
    def_days = sum(shape_hist.values())
    shallow = shape_hist.get(1, 0)
    deep = def_days - shallow
    if def_days:
        print(f'FORMA DEL DEFICIT (senza G14): {def_days} giorni sotto quota, di cui {shallow} a -1h '
              f'({100 * shallow / def_days:.0f}%) e {deep} profondi >=2h '
              f'(golden: pochi profondi, G04 zero - conta su golden DB dump)')
        if deep_by_group:
            worst = ', '.join(f'{g} {n}' for g, n in
                              sorted(deep_by_group.items(), key=lambda kv: -kv[1])[:5])
            print(f'  giorni profondi per gruppo (peggiori): {worst}')

    print('\nper gruppo (full/tot):')
    for g in sorted(by_group_tot):
        f, t = by_group[g], by_group_tot[g]
        print(f'  {g}: {f}/{t} = {100 * f / t:.0f}%')

    # DEFICIT DETAIL (maintainer 2026-07-29): the histogram hides WHO drives each mode. Break each
    # deficit bin down by group + weekday, and list the deepest days concretely (group, date,
    # weekday, expected, got). G14 excluded (exempt, Q16). Uses this script's own expected
    # (DailyActivityQuota from export.json.gz; class_days.csv only the fallback) - no CSV reconstruction, no FC
    # double-count.
    _WD = ['lun', 'mar', 'mer', 'gio', 'ven', 'sab', 'dom']
    deep_days = []  # (deficit, group, date, weekday, exp, got)
    mode_group = {}   # deficit -> Counter(group)
    mode_wd = {}      # deficit -> Counter(weekday)
    for (g, d), exp in expected.items():
        if g == 'G14':
            continue
        got = placed.get((g, d), 0)
        df = exp - got
        if df <= 0:
            continue
        wd = _WD[datetime.date.fromisoformat(d).weekday()]
        mode_group.setdefault(df, Counter())[g] += 1
        mode_wd.setdefault(df, Counter())[wd] += 1
        if df >= 2:
            deep_days.append((df, g, d, wd, exp, got))
    print('\nFORMA DEL DEFICIT per modo (senza G14): deficit | #giorni | top gruppi | top giorni')
    for df in sorted(mode_group, reverse=True):
        n = sum(mode_group[df].values())
        tg = ', '.join(f'{g} {c}' for g, c in mode_group[df].most_common(3))
        tw = ', '.join(f'{w} {c}' for w, c in mode_wd[df].most_common(3))
        print(f'  -{df}h | {n} | {tg} | {tw}')
    deep_days.sort(reverse=True)
    print(f'\ngiorni piu profondi (top 15, senza G14): group | date | weekday | exp/got/deficit')
    for df, g, d, wd, exp, got in deep_days[:15]:
        print(f'  {g} | {d} | {wd} | exp {exp} got {got} deficit {df}')

    # DEEP-DEFICIT PER MONTH (TASK-457 falsification, 2026-07-30): bucket the deep days (>=2h)
    # by calendar month. The tier-drift hypothesis says deep deficits concentrate late in the
    # year, where the generic-teacher quota drops below golden (generics spent early leave a
    # specialist-heavy residue that cannot fill end-of-year holes). Cross this with
    # teacher-tier-kpi.py's monthly generic quota to falsify or support it. Reuses deep_days
    # (same expected source, same G14 exclusion) - no second deficit computation.
    # Saturdays are excluded script-wide at the expected-hours source, so no filter is needed here.
    deep_by_month = Counter(d[:7] for _df, _g, d, _wd, _exp, _got in deep_days)
    print('\nDEFICIT PROFONDO (>=2h) per mese (senza G14): mese | #giorni deep')
    for m in sorted(deep_by_month):
        print(f'  {m} | {deep_by_month[m]}')
    total_deep = sum(deep_by_month.values())
    late = sum(c for m, c in deep_by_month.items() if m >= '2027-03')  # mar-giu, last 4 months
    if total_deep:
        print(f'  totale deep: {total_deep} | mar-giu: {late} '
              f'({100 * late / total_deep:.0f}% del totale)')
    else:
        print('  totale deep: 0')


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('lessons', help='lessons dump (group|date|hour|subject|teacher|pinned)')
    add_kpi_args(ap)  # --year-start/--year-end drive the LO/HI horizon (+ other parametric flags)
    args = ap.parse_args()
    LO, HI = args.year_start, args.year_end  # override module globals from CLI (defaults = today)
    main(args.lessons)
