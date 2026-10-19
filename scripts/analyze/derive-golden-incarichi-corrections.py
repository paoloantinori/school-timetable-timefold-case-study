#!/usr/bin/env python3
"""
Derive golden-grid corrections for incarichi.csv (TASK-371).

WHY: referente's answer to Q3 (17/07, domande-per-la-scuola.md) is that "Orario
in righe" (the hand-made golden grid, lessons.csv) is authoritative and
"Incarichi in riga" (incarichi.csv) is stale for the FC DUALE mismatches.
This script re-derives, MECHANICALLY from lessons.csv, the corrected hours
for every real-teacher (teacher, subject, group) triple, and prints a diff
against the current incarichi.csv so the correction can be applied by hand
with a clear paper trail (no hand-tuned numbers).

Scope: real teachers only. Pseudo-teacher rows (Stage, Esame, RA pool,
Totale ore corso) are out of scope for this correction - they are handled by
a separate accounting model (claudedocs/design/stage-esame-ra-modeling-2026-07-16.md).
The raw abbreviated subject "RA" in lessons.csv (G08's floating pool,
covered by per-teacher micro-courses per that same design doc section C) is
also excluded from this diff: it has no matching incarichi row keyed "RA" -
G08's incarichi RA row is keyed by the full subject name "Recuperi ed
Approfondimenti", not by "RA".

Usage:
    python3 scripts/analyze/derive-golden-incarichi-corrections.py
    python3 scripts/analyze/derive-golden-incarichi-corrections.py --selftest

--selftest runs categorize_mismatches() (the same function the real run
uses) against small inline fixture rows instead of the real CSVs, covering
all four branches (OK/already-reconciled, CORRECT, DROP, and ADD) and prints
PASS/FAIL. This exists because the real data currently has zero ADD cases
(TASK-371 already reconciled everything mechanically) - without a fixture,
the ADD branch (line ~121 below) would be unexercised code with no test
ever having observed its output (TASK-374 code review E8).
"""
import csv
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

sys.path.insert(0, SCRIPT_DIR)
from shared import (  # noqa: E402
    SUBJECT_CASE_NORMALIZATION, PSEUDO_TEACHERS,
    LESSONS_CSV, INCARICHI_CSV, iter_open_lessons_rows,
)

# Raw abbreviated subject in lessons.csv that historically never appeared as
# a subject name in incarichi.csv real-course rows (G08's floating pool).
# TASK-592 cure (11/09): the pool is now distributed into real per-teacher
# rows matching the golden's own split, so the golden rows ARE compared -
# under the import's documented rename (GoldenScheduleImportService renames
# raw "RA" to the full subject name).
RAW_POOL_RENAME = {"RA": "Recuperi ed Approfondimenti"}


def normalize(subject: str) -> str:
    return RAW_POOL_RENAME.get(subject, SUBJECT_CASE_NORMALIZATION.get(subject, subject))


def load_golden_counts():
    """(teacher, subject, group) -> count of open (non-closed) lesson-hours."""
    counts = {}
    for row in iter_open_lessons_rows(LESSONS_CSV):
        teacher = row['teacher'].strip()
        if not teacher or teacher in PSEUDO_TEACHERS:
            continue
        subject = normalize(row['subject'].strip())
        group = row['group'].strip()
        key = (teacher, subject, group)
        counts[key] = counts.get(key, 0) + 1
    return counts


def load_incarichi_rows():
    """Return list of (line_no, teacher, subject, group, hours) preserving order."""
    rows = []
    with open(INCARICHI_CSV, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for i, row in enumerate(reader, start=2):  # header is line 1
            rows.append((i, row['teacher'].strip(), normalize(row['subject'].strip()),
                          row['group'].strip(), row['hours'].strip()))
    return rows


def categorize_mismatches(incarichi_keys, golden):
    """Shared by main() and --selftest. incarichi_keys: {key: (line_no, hours)},
    golden: {key: count}. Returns a list of (key, incarichi_hours, golden_hours,
    action) tuples for every key that doesn't already reconcile, in sorted-key
    order - skips (key, in-sync) pairs entirely, same as the printed report."""
    results = []
    for key in sorted(set(incarichi_keys) | set(golden)):
        in_incarichi = key in incarichi_keys
        in_golden = key in golden
        incarichi_hours = incarichi_keys[key][1] if in_incarichi else None
        golden_hours = golden.get(key)

        if in_incarichi and in_golden and incarichi_hours == golden_hours:
            continue  # already reconciled

        if not in_golden:
            action = f"DROP (0 in golden, line {incarichi_keys[key][0]})"
        elif not in_incarichi:
            action = "ADD (row absent from incarichi.csv)"
        else:
            action = f"CORRECT (line {incarichi_keys[key][0]}: {incarichi_hours} -> {golden_hours})"

        results.append((key, incarichi_hours, golden_hours, action))
    return results


def main():
    golden = load_golden_counts()
    incarichi_rows = load_incarichi_rows()

    incarichi_keys = {}
    for line_no, teacher, subject, group, hours in incarichi_rows:
        if teacher in PSEUDO_TEACHERS:
            continue
        incarichi_keys[(teacher, subject, group)] = (line_no, float(hours))

    print(f"{'teacher':<15} {'subject':<32} {'group':<8} {'incarichi':>10} {'golden':>7}  action")
    print("-" * 100)

    results = categorize_mismatches(incarichi_keys, golden)
    for (teacher, subject, group), incarichi_hours, golden_hours, action in results:
        print(f"{teacher:<15} {subject:<32} {group:<8} "
              f"{('' if incarichi_hours is None else incarichi_hours):>10} "
              f"{('' if golden_hours is None else golden_hours):>7}  {action}")

    print("-" * 100)
    print(f"{len(results)} mismatched triples (real teachers only, pseudo rows excluded)")


def selftest():
    """Inline fixture covering all four categorize_mismatches() branches,
    including ADD - which never fires on the real 2026/27 data (TASK-371
    already reconciled everything), so it would otherwise be unexercised."""
    incarichi_keys = {
        ("Rossi", "Matematica", "G04"): (10, 40.0),   # in both, hours match -> OK (not returned)
        ("Verdi", "Scienze", "G08"): (11, 30.0),       # in both, hours differ -> CORRECT
        ("Bianchi", "Storia", "G13"): (12, 20.0),       # only in incarichi -> DROP
    }
    golden = {
        ("Rossi", "Matematica", "G04"): 40,
        ("Verdi", "Scienze", "G08"): 55,
        ("Neri", "Disegno", "3 FC"): 15,                # only in golden -> ADD
    }

    results = categorize_mismatches(incarichi_keys, golden)
    by_key = {key: (h_in, h_gold, action) for key, h_in, h_gold, action in results}

    # Verdi/Bianchi/Neri are guaranteed to be in `results` by construction
    # (they're exactly the CORRECT/DROP/ADD fixture rows above), so a plain
    # by_key[...] lookup is safe - no fallback default needed.
    checks = [
        (("Rossi", "Matematica", "G04") not in by_key, "Rossi/Matematica must be OK (omitted), not a mismatch"),
        (by_key[("Verdi", "Scienze", "G08")][2].startswith("CORRECT"), "Verdi/Scienze must be CORRECT"),
        (by_key[("Bianchi", "Storia", "G13")][2].startswith("DROP"), "Bianchi/Storia must be DROP"),
        (by_key[("Neri", "Disegno", "3 FC")][2].startswith("ADD"), "Neri/Disegno must be ADD"),
        (len(results) == 3, f"expected exactly 3 mismatches (CORRECT+DROP+ADD), got {len(results)}"),
    ]

    print(f"{'check':<70} verdict")
    print("-" * 80)
    all_pass = True
    for ok, description in checks:
        print(f"{description:<70} {'PASS' if ok else 'FAIL'}")
        all_pass = all_pass and ok

    print("-" * 80)
    for key, h_in, h_gold, action in results:
        print(f"  {key}: incarichi={h_in} golden={h_gold} -> {action}")
    print("-" * 80)
    print("SELFTEST " + ("PASS" if all_pass else "FAIL"))
    return all_pass


if __name__ == '__main__':
    if '--selftest' in sys.argv:
        sys.exit(0 if selftest() else 1)
    main()
