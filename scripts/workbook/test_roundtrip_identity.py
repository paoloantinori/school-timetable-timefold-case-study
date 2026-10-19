#!/usr/bin/env python3
"""Round-trip identity gate (TASK-445 Tappa 2).

Proves the lossless half of the import chain maintainer defined: the canonical seed
CSVs survive a round trip through the workbook, cell for cell, for every sheet
that has a clean mapping. The chain tested::

    seed CSV  --generate_workbook-->  workbook .xlsx  --xlsx_to_csv-->  seed CSV

The two CSV ends must be identical cell for cell (header + body). A single
differing cell is a FAILED migration and exits non-zero with the divergence
printed - never a warning, never a silent pass.

Scope is the three sheets with a clean mapping (subjects/rooms/groups), exactly
the set :mod:`xlsx_to_csv` converts. The sheets the workbook reorganizes
(06/07, 13/14/15) or emits partially by design (12, 05) or that have no seed CSV
(01/02/03/04/09/11/16/18/19/20/21) are GAPs documented in the README, not tested
here: asserting identity on them would be asserting a loss the architecture
chose, which is the opposite of a gate.

Scope note: the gate exercises the generator-fed chain inside one process. It
does NOT exercise a human-edited workbook (the real migration input) nor one
authored by Excel/LibreOffice. Catching human-input inconsistencies (e.g.
laboratorio=SI with an empty tagAula, or a stray space in a multi-tag cell) is
Tappa 5 (system-side validation), not this gate's job.

Run with the venv python::

    scripts/workbook/.venv/bin/python scripts/workbook/test_roundtrip_identity.py
"""

from __future__ import annotations

import csv
import filecmp
import sys
import tempfile
from pathlib import Path

from openpyxl import load_workbook

import xlsx_to_csv as C
from generate_workbook import build_workbook
from sources import SEED_DIR


def _read_seed(name: str) -> list[list[str]]:
    """Read a seed CSV as rows of stripped cells (header + body)."""
    with (SEED_DIR / name).open(newline="", encoding="utf-8") as fh:
        return [[(c or "").strip() for c in row] for row in csv.reader(fh)]


def _diff(name: str, expected: list[list[str]], actual: list[list[str]]) -> list[str]:
    """Cell-by-cell diff of two row lists; returns human-readable divergences."""
    errs = []
    if len(expected) != len(actual):
        errs.append(f"{name}: numero righe attese={len(expected)} trovate={len(actual)}")
    for i in range(max(len(expected), len(actual))):
        e = expected[i] if i < len(expected) else []
        a = actual[i] if i < len(actual) else []
        if len(e) != len(a):
            errs.append(f"{name} riga {i}: colonne attese={len(e)} trovate={len(a)} "
                        f"(attesa={e} trovata={a})")
            continue
        for j, (ev, av) in enumerate(zip(e, a)):
            if ev != av:
                errs.append(f"{name} riga {i} col {j}: atteso={ev!r} trovato={av!r}")
    return errs


def main() -> int:
    # Build the workbook, round-trip it through save+reload, then for each
    # converter WRITE the CSV via xlsx_to_csv.write_csv and prove the WRITTEN
    # file matches the seed. This closes the last arrow (the file write): a
    # write_csv dialect/quoting/line-ending change cannot ship a broken CSV
    # behind a green gate.
    wb = build_workbook()
    errors = []
    counts = {}
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        xlsx = tmp / "roundtrip.xlsx"
        wb.save(xlsx)
        wb = load_workbook(xlsx)
        for csv_name, extract in C.CONVERTERS.items():
            rows = extract(wb)
            counts[csv_name] = len(rows) - 1
            written = tmp / csv_name
            C.write_csv(rows, written)
            # (a) byte-identical to the seed: catches a write_csv dialect,
            # quoting or line-ending change (and any emitted whitespace).
            if not filecmp.cmp(written, SEED_DIR / csv_name, shallow=False):
                errors.append(f"{csv_name}: il file riscritto NON e' byte-identico al seed "
                              f"(write_csv dialect/quoting/line-ending cambiato?)")
            # (b) cell-for-cell on the re-read file: readable divergence message.
            with written.open(newline="", encoding="utf-8") as fh:
                written_rows = [[(c or "").strip() for c in row] for row in csv.reader(fh)]
            errors.extend(_diff(csv_name, _read_seed(csv_name), written_rows))

    if errors:
        print(f"FAIL: round-trip identity NON superato ({len(errors)} divergenze)")
        for e in errors:
            print("  - " + e)
        return 1

    summary = ", ".join(f"{n}={counts[n]} righe" for n in C.CONVERTERS)
    print(f"OK: round-trip identity byte-identico + cella per cella su {summary}. "
          f"I CSV canonici sopravvivono al giro seed->workbook->CSV (write+read) senza perdita.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
