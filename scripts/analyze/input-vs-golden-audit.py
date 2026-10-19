#!/usr/bin/env python3
"""Input-vs-golden audit (2026-09-01, TASK-576 morning: 'ce ne sono altre come le
fantasma?'). Flags every place where a DECLARED, CONSUMED input row contradicts the
school's PRACTICE (the golden timetable).

Consumption filters mirror the GENERATOR, not the raw sheets (the lesson of the
first run: 38 raw phantoms shrank to 2 live ones once the code's own skips were
applied):
- calendario_class_days.csv rows for G04/G08/G13 are NEVER consumed
  (DataGenerator skips them, the finer calendario_fc.csv is the live source).
- Rows past 2027-06-10 (PRE_EXTENSION_YEAR_END) are skipped by the TASK-550
  extension in BOTH loaders; capacity there is golden-mirrored instead.
- Saturday rows and the Fasullo placebo are excluded from the golden by protocol.

Classes:
  VIVO-*        the solver consumes the row AND practice contradicts it: real damage
  SPENTO-*      the sheet is contradictory but the loader never consumes it
Usage: python3 scripts/analyze/input-vs-golden-audit.py   (exit 0 always; the
verdict belongs to the reader)
"""
import csv
import datetime
from collections import Counter
from pathlib import Path

SEED = Path(__file__).resolve().parents[2] / 'src/main/resources/seed'
GOLDEN_END = datetime.date(2027, 6, 15)
PRE_EXT_END = datetime.date(2027, 6, 10)   # TimeslotSequence.PRE_EXTENSION_YEAR_END
FC_QUOTA_GROUPS = {'G04', 'G08', 'G13'}  # DataGenerator.FC_QUOTA_GROUP_NAMES


def load_golden():
    non_stage, stage = Counter(), Counter()
    for r in csv.DictReader(open(SEED / 'lessons.csv')):
        d = datetime.date.fromisoformat(r['date'])
        if d.weekday() == 5 or r['teacher'] == 'Fasullo' or d > GOLDEN_END:
            continue
        (stage if r['subject'] == 'Stage' else non_stage)[(r['group'], d)] += 1
    return non_stage, stage


def main():
    non_stage, stage = load_golden()
    out = []

    # A. class_days (live for the 11 non-FC groups, up to 06-10)
    decl = Counter()
    for r in csv.DictReader(open(SEED / 'calendario_class_days.csv')):
        g = r['group'].strip()
        if g in FC_QUOTA_GROUPS or r['modality'].strip().upper() == 'S':
            continue
        decl[(g, datetime.date.fromisoformat(r['date']))] += int(r['hours'] or 0)
    for (g, d), dv in sorted(decl.items()):
        gv_ns, gv_st = non_stage.get((g, d), 0), stage.get((g, d), 0)
        if d > PRE_EXT_END or d.weekday() >= 5 or d > GOLDEN_END:
            if dv and not gv_ns and not gv_st:
                out.append(('SPENTO(class_days oltre 06-10: TASK-550 skip)', g, str(d), dv, 0))
            continue
        if dv and not gv_ns and not gv_st:
            out.append(('VIVO giorno fantasma', g, str(d), dv, 0))
        elif not dv and (gv_ns or gv_st):
            out.append(('VIVO praticato-non-dichiarato', g, str(d), 0, gv_ns + gv_st))
        elif dv - (gv_ns + gv_st) >= 4:
            out.append(('VIVO sovradichiarato >=4h', g, str(d), dv, gv_ns + gv_st))

    # B. calendario_fc.csv (live for the 3 FC-quota groups, up to 06-10)
    for r in csv.DictReader(open(SEED / 'calendario_fc.csv')):
        d = datetime.date.fromisoformat(r['date'])
        g = r['group'].strip()
        au, la, st = int(r['aula_hours'] or 0), int(r['lab_hours'] or 0), int(r['stage_hours'] or 0)
        gv_ns, gv_st = non_stage.get((g, d), 0), stage.get((g, d), 0)
        if d > PRE_EXT_END or d.weekday() >= 5 or d > GOLDEN_END:
            continue
        if (au or la) and not gv_ns and not gv_st:
            out.append(('VIVO fc aula/lab fantasma (golden tutto zero)', g, str(d), au + la, 0))
        elif (au or la) and not gv_ns and gv_st:
            out.append(('VIVO fc aula/lab su giorno di stage', g, str(d), au + la, gv_st))
        elif st and not gv_st and not gv_ns:
            out.append(('VIVO fc stage fantasma', g, str(d), st, 0))

    # C. incarichi pool per group (the 'Totale ore corso' summary rows are TEACHER-
    # column markers, not subjects: excluding on subject doubled every pool in the
    # first run)
    pool = Counter()
    for r in csv.DictReader(open(SEED / 'incarichi.csv')):
        if 'Totale' in r['teacher']:
            continue
        pool[r['group'].strip()] += int(float(r['hours'] or 0))
    golden_tot = Counter()
    for (g, _), n in list(non_stage.items()) + list(stage.items()):
        golden_tot[g] += n
    for g in sorted(pool):
        if abs(pool[g] - golden_tot.get(g, 0)) >= 4:
            # NOT VIVO by design: the TASK-550 golden-open seeding generates lessons
            # only for golden-held hours and parks the difference in
            # expectedUnscheduledHours, so the sheet's 990 never becomes demand.
            # Kept as documentation of the declared-vs-practiced gap for referente.
            out.append(('NOTA incarichi pool dichiarato vs pratica (gestito da expectedUnscheduled)', g, 'ANNO', pool[g], golden_tot.get(g, 0)))

    print('classe\tgruppo\tdata\tdichiarato\tgolden')
    for row in out:
        print('\t'.join(map(str, row)))
    live = sum(1 for r in out if r[0].startswith('VIVO'))
    print(f'# VIVO: {live}, SPENTO: {len(out) - live}', )


if __name__ == '__main__':
    main()
