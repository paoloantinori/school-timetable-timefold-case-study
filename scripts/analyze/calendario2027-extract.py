#!/usr/bin/env python3
"""Extract the Calendario scolastico 2026/2027 xlsx (TASK-364 pre-assignment input).

Usage: python3 scripts/analyze/calendario2027-extract.py [path-to-xlsx]

Reading key (binding): claudedocs/spalmone-2026-27/calendario-scolastico-format.md.
Outputs three CSVs under claudedocs/spalmone-2026-27/data/:

  calendario_moda.csv        - MODA TUTTI: per (date, group, teacher-initial) signed
                                lab hours, decoded into hours + shift (first/second).
                                Only the 1/2/G09 PE/MU/BO columns (unambiguous
                                teacher-initial mapping) - G14's TOT/Au/La/St block
                                on the same sheet carries no teacher initials and is
                                already covered per-date by calendario_class_days.csv,
                                so it is not re-extracted here.
  calendario_fc.csv         - DEF FC TUTTI: per (date, group) Aula/Laboratorio/Stage
                                hour split, for G04 / G08 / G13 (the two spare
                                24-col blocks in the sheet are unnamed/unused and skipped).
  calendario_class_days.csv  - the 14 authoritative per-class sheets: per (group, date)
                                total hours + modality (A=Aula, S=Stage, L=Laboratorio).
                                Discarded variants (MODA NON USARE, FC duale piano B,
                                FC TUTTI (non-DEF), Copia di ..., def. G13, G14 2,
                                G12NEW, Base, bare 'FA', Calendario/Calendario
                                base/Istruzioni) are intentionally excluded.

Also prints a coherence report cross-checking known facts from the format doc
(G01 PE/MU/BO yearly totals, total MODA teacher-day cell count, G14's
Thu/Fri-only weekly shape).
"""
import sys, os, csv, warnings, datetime
from collections import defaultdict

warnings.filterwarnings('ignore')
import openpyxl

if len(sys.argv) < 2:
    sys.exit('usage: calendario2027-extract.py <path-to-xlsx>  (the ~/Downloads default was removed in TASK-444)')
XLSX = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'claudedocs', 'spalmone-2026-27', 'data')
os.makedirs(OUT, exist_ok=True)

wb = openpyxl.load_workbook(XLSX, data_only=True)


def require_sheet(name):
    if name not in wb.sheetnames:
        sys.exit(f"HEADER VALIDATION FAILED: expected sheet {name!r} not found in {XLSX}. "
                 f"Available sheets: {wb.sheetnames}")


def require_cell(ws, row, col, expected, context):
    actual = ws.cell(row=row, column=col).value
    if actual != expected:
        sys.exit(f"HEADER VALIDATION FAILED: {context} - sheet {ws.title!r} row={row} col={col} "
                  f"expected {expected!r}, got {actual!r}. The workbook layout has changed; re-verify "
                  f"every positional offset in this script before trusting its output.")


def validate_headers():
    """Fail loud (TASK-364 code review K3) before trusting any positional
    offset below: every sheet name, the per-class month-block anchors, and
    the MODA TUTTI / DEF FC TUTTI column headers this script's column
    indices depend on. A silent layout change (e.g. an inserted column) would
    otherwise misalign every extracted row without ever raising an error."""
    for sheet_name in CLASS_SHEETS:
        require_sheet(sheet_name)
    require_sheet('DEF FC TUTTI')
    require_sheet('MODA TUTTI')
    require_sheet(G14_SHEET)
    g14 = wb[G14_SHEET]
    for month_idx, (month_name, month_num, year) in enumerate(MONTHS):
        block_start = 1 + 5 * month_idx  # 5-col blocks, not the class sheets' 4
        require_cell(g14, 1, block_start, month_name, f"G14 2 month anchor (month {month_idx})")
        require_cell(g14, 2, block_start + 2, 'Au', f"G14 2 Au header (month {month_idx})")
        require_cell(g14, 2, block_start + 3, 'La', f"G14 2 La header (month {month_idx})")

    for sheet_name in CLASS_SHEETS:
        ws = wb[sheet_name]
        for month_idx, (month_name, month_num, year) in enumerate(MONTHS):
            block_start = 1 + 4 * month_idx
            require_cell(ws, 1, block_start, month_name, f"per-class month anchor (sheet {sheet_name!r}, month {month_idx})")

    ws = wb['MODA TUTTI']
    for group_name, pe_col, mu_col, bo_col in MODA_GROUPS:
        require_cell(ws, 1, pe_col - 3, group_name, f"MODA TUTTI group anchor ({group_name})")
        require_cell(ws, 2, pe_col, 'PE', f"MODA TUTTI PE column ({group_name})")
        require_cell(ws, 2, mu_col, 'MU', f"MODA TUTTI MU column ({group_name})")
        require_cell(ws, 2, bo_col, 'BO', f"MODA TUTTI BO column ({group_name})")

    ws = wb['DEF FC TUTTI']
    block_start = 7  # first month block only - the offsets repeat identically every month
    for group_name, off in FC_GROUPS:
        require_cell(ws, 2, block_start + off, group_name, f"DEF FC TUTTI group anchor ({group_name})")
        require_cell(ws, 3, block_start + off, 'TOT', f"DEF FC TUTTI TOT header ({group_name})")
        require_cell(ws, 3, block_start + off + 1, 'Au', f"DEF FC TUTTI Au header ({group_name})")
        require_cell(ws, 3, block_start + off + 2, 'La', f"DEF FC TUTTI La header ({group_name})")
        require_cell(ws, 3, block_start + off + 3, 'St', f"DEF FC TUTTI St header ({group_name})")

    print("Header validation passed: sheet names, month anchors, and MODA TUTTI/DEF FC TUTTI "
          "column headers all matched the expected layout.")


def num(v):
    """Render a whole-number float as a plain int string (e.g. 6.0 -> '6') -
    every hours value in this workbook is a whole number of hours."""
    return int(v) if isinstance(v, float) and v == int(v) else v


def w(name, header, rows):
    path = os.path.join(OUT, name)
    with open(path, 'w', newline='') as f:
        cw = csv.writer(f)
        cw.writerow(header)
        cw.writerows(rows)
    print(f"wrote {path} ({len(rows)} rows)")


# Month-block calendar: (sheet month name, calendar month number, calendar year).
# Shared by the per-class sheets (day-of-month columns) - DEF FC TUTTI carries
# its own datetime cells directly, so it doesn't need this table.
MONTHS = [
    ('settembre', 9, 2026), ('ottobre', 10, 2026), ('novembre', 11, 2026), ('dicembre', 12, 2026),
    ('gennaio', 1, 2027), ('febbraio', 2, 2027), ('marzo', 3, 2027), ('aprile', 4, 2027),
    ('maggio', 5, 2027), ('giugno', 6, 2027),
]

# Authoritative per-class sheets only (14 groups, matches the seeded 2026/27
# StudentGroup set) - see calendario-scolastico-format.md section 1 and 5 for
# why the other sheet variants are discarded. Note the literal leading space
# in ' G11' (a real quirk of the source workbook, not a typo here).
CLASS_SHEETS = {
    'G03': 'G03', 'G07': 'G07', ' G11': 'G11',
    'G02': 'G02', 'G06': 'G06', 'G10': 'G10', 'G12': 'G12',
    'G01': 'G01', 'G05': 'G05', 'G09': 'G09',
    'G13': 'G13', 'G04': 'G04', 'G08': 'G08',
}

# G14 is NOT in CLASS_SHEETS: the sheet named 'G14' is the workbook's
# uncompiled default (990h of aula, byte-identical calendar to G01 / the
# 'Base' sheet). The real block lives in 'G14 2' (TASK-462, maintainer
# 2026-07-30): 5-column month blocks (day, weekday, Au, La, notes) where a
# single date can carry BOTH aula and lab hours, and the 5th column (headed
# 'St') holds free-text teacher notes, never stage hours. Emitting one row
# per (date, modality) below; DataGenerator aggregates them per date.
G14_SHEET = 'G14 2'

# DEF FC TUTTI group blocks (see section 2 below for the column-offset rationale).
FC_GROUPS = [('G04', 2), ('G08', 6), ('G13', 10)]

# MODA TUTTI group blocks (see section 3 below for the column-offset rationale).
MODA_GROUPS = [('G01', 6, 7, 8), ('G05', 13, 14, 15), ('G09', 20, 21, 22)]

# TASK-364 code review K3: assert every positional offset below against its
# expected header text before trusting it - a silently inserted/reordered
# column would otherwise misalign extracted rows without ever raising an error.
validate_headers()

# ============ 1. Per-class sheets -> calendario_class_days.csv ============
class_days = []
for sheet_name, group_name in CLASS_SHEETS.items():
    ws = wb[sheet_name]
    for month_idx, (month_name, month_num, year) in enumerate(MONTHS):
        block_start = 1 + 4 * month_idx  # (day-of-month, weekday, hours, modality)
        for r in range(6, 37):  # rows 6..36 cover day 1..31
            day = ws.cell(row=r, column=block_start).value
            hours = ws.cell(row=r, column=block_start + 2).value
            modality = ws.cell(row=r, column=block_start + 3).value
            # Only real numeric teaching days: None (weekend) and holiday-name
            # text values are skipped - modality is pre-filled with a stale 'A'
            # default on those rows even though no lesson happens (verified by
            # inspection: Natale week and every Sat/Dom all carry modality='A').
            if day is None or not isinstance(hours, (int, float)):
                continue
            date = datetime.date(year, month_num, int(day))
            class_days.append((group_name, date.isoformat(), num(hours), modality or ''))

# G14 from 'G14 2' (TASK-462, layout documented at G14_SHEET above).
# Non-numeric cells (teacher notes leaking into the La column, e.g.
# 2027-01-16) are skipped as values but never as dates.
G14_ws = wb[G14_SHEET]
for month_idx, (month_name, month_num, year) in enumerate(MONTHS):
    block_start = 1 + 5 * month_idx
    for r in range(4, 40):
        day = G14_ws.cell(row=r, column=block_start).value
        if not isinstance(day, (int, float)):
            continue
        date = datetime.date(year, month_num, int(day))
        au = G14_ws.cell(row=r, column=block_start + 2).value
        la = G14_ws.cell(row=r, column=block_start + 3).value
        if isinstance(au, (int, float)) and au > 0:
            class_days.append(('G14', date.isoformat(), num(au), 'A'))
        if isinstance(la, (int, float)) and la > 0:
            class_days.append(('G14', date.isoformat(), num(la), 'L'))

class_days.sort(key=lambda x: (x[0], x[1]))
w('calendario_class_days.csv', ['group', 'date', 'hours', 'modality'], class_days)

# ============ 2. DEF FC TUTTI -> calendario_fc.csv ============
# Month blocks of stride 22: 2 duplicate date columns, then group blocks of 4
# cols (TOT|Au|La|St) at offsets +2 (G04), +6 (G08), +10 (G13); offsets
# +14/+18 are unnamed spare blocks (row2 header blank) and are skipped.
ws = wb['DEF FC TUTTI']
FC_rows = []
for month_idx in range(10):
    block_start = 7 + 22 * month_idx
    for r in range(5, 36):  # rows 5..35 cover day 1..31 of this month block
        date_cell = ws.cell(row=r, column=block_start).value
        if date_cell is None:
            continue
        date = date_cell.date() if hasattr(date_cell, 'date') else date_cell
        for group_name, off in FC_GROUPS:
            au = ws.cell(row=r, column=block_start + off + 1).value or 0
            la = ws.cell(row=r, column=block_start + off + 2).value or 0
            st = ws.cell(row=r, column=block_start + off + 3).value or 0
            FC_rows.append((group_name, date.isoformat(), num(au), num(la), num(st)))

FC_rows.sort(key=lambda x: (x[0], x[1]))
w('calendario_fc.csv', ['group', 'date', 'aula_hours', 'lab_hours', 'stage_hours'], FC_rows)

# ============ 3. MODA TUTTI -> calendario_moda.csv ============
# Single date-column layout (rows 4+, one row per calendar date). Per group,
# PE/MU/BO columns carry SIGNED lab hours: negative = first shift (slots 0-2,
# 08-11), positive = second shift (slots 3-5, 11-14) - see the shift-to-slot
# mapping rule documented in CalendarioImportService. The raw teacher INITIAL
# (PE/MU/BO) is kept as-is (not resolved to a teacher name here) - the
# initial-to-teacher mapping is owned by CalendarioImportService, configurable
# there rather than baked into this extraction step. G14's TOT|Au|La|St
# block (cols 24-27) carries no teacher initials and is intentionally not
# extracted here (see module docstring).
ws = wb['MODA TUTTI']
moda_rows = []
for r in range(4, 1000):
    date_cell = ws.cell(row=r, column=1).value
    if date_cell is None:
        continue
    date = date_cell.date() if hasattr(date_cell, 'date') else date_cell
    for group_name, pe_col, mu_col, bo_col in MODA_GROUPS:
        for initial, col in (('PE', pe_col), ('MU', mu_col), ('BO', bo_col)):
            value = ws.cell(row=r, column=col).value
            if value in (None, 0):
                continue
            hours = abs(value)
            shift = 'first' if value < 0 else 'second'
            moda_rows.append((date.isoformat(), group_name, initial, num(hours), shift))

moda_rows.sort(key=lambda x: (x[0], x[1], x[2]))
w('calendario_moda.csv', ['date', 'group', 'initial', 'hours', 'shift'], moda_rows)

# ============ COHERENCE REPORT ============
print("\n========== COHERENCE REPORT ==========")
print(f"total MODA teacher-day rows (1/2/G09 x PE/MU/BO, nonzero cells): {len(moda_rows)}")

by_group_teacher = defaultdict(float)
for date, group, teacher, hours, shift in moda_rows:
    by_group_teacher[(group, teacher)] += hours
print("\n[1] yearly hours per (group, teacher) - cross-check vs format doc's row-3 reference values:")
for (group, teacher), total in sorted(by_group_teacher.items()):
    print(f"    {group} {teacher}: {total:.0f}h")
print("    format doc: G01 = PE 143 + MU 180 + BO 24 (347 total)")

shift_hist = defaultdict(int)
for date, group, teacher, hours, shift in moda_rows:
    shift_hist[(shift, hours)] += 1
print("\n[2] (shift, hours) histogram - the odd values (other than shift/3.0) needing shift-rule verification:")
for k, v in sorted(shift_hist.items()):
    print(f"    {k}: {v}")

# Same-day, same-group multi-teacher rows (both shifts filled that day) -
# the naive anchor rule (first->slots[0,hours-1], second->slots[3,3+hours-1])
# can only collide when the combined hours exceed the 6-slot morning, which
# happens only when one side's hours > 3 (bleeds across the 2/3 boundary).
by_day_group = defaultdict(list)
for date, group, teacher, hours, shift in moda_rows:
    by_day_group[(date, group)].append((teacher, hours, shift))
collisions = []
for (date, group), entries in by_day_group.items():
    if len(entries) < 2:
        continue
    ranges = []
    for teacher, hours, shift in entries:
        start = 0 if (shift == 'first' or hours > 3) else 3
        ranges.append((start, start + hours - 1, teacher))
    ranges.sort()
    for i in range(len(ranges) - 1):
        if ranges[i][1] >= ranges[i + 1][0]:
            collisions.append((date, group, entries))
            break
print(f"\n[3] same-day multi-teacher rows: {len(by_day_group)} distinct (date, group) with >=2 teacher entries "
      f"{'(none)' if not any(len(v) > 1 for v in by_day_group.values()) else ''}")
multi = {k: v for k, v in by_day_group.items() if len(v) > 1}
print(f"    of which >=2 teacher-entries: {len(multi)}")
print(f"    of which the anchor rule computes an OVERLAPPING slot range (expected import validation failures): {len(collisions)}")
for date, group, entries in collisions:
    print(f"      COLLISION {date} {group}: {entries}")

G14_days = [r for r in class_days if r[0] == 'G14']
G14_weekdays = {datetime.date.fromisoformat(d).strftime('%A') for _, d, _, _ in G14_days}
G14_hours_hist = defaultdict(int)
for _, _, hours, _ in G14_days:
    G14_hours_hist[hours] += 1
print(f"\n[4] G14 shape (no prior documented expectation to check against - reporting as observed): "
      f"{len(G14_days)} teaching days, weekdays used = {sorted(G14_weekdays)}, "
      f"hours/day histogram = {dict(sorted(G14_hours_hist.items()))}")
