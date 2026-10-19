#!/usr/bin/env python3
"""Convert a generated workbook back into the CANONICAL seed CSVs (TASK-445 Tappa 2).

This is the inverse of :mod:`generate_workbook` for the sheets that have a clean
1:1 (or clean subset) mapping to an existing seed CSV. The round-trip identity
gate (:mod:`test_roundtrip_identity`) proves the chain
``seed CSV -> workbook (generate) -> seed CSV (this module)`` is lossless, cell
for cell, for those sheets. A difference fails the gate with a non-zero exit,
never a warning.

Scope is deliberately narrow: only sheets whose workbook columns map losslessly
onto a seed CSV are converted here. Sheets whose data was reorganized for the
workbook (06/07 availability -> Tappa 3; 13/14/15 piano-giorni -> Tappa 4), or
that have no seed CSV at all (01/02/03/04/09/11/16/18/19/20/21), or that the
generator emits only partially by design (12_Incarichi: 266 ordinario, the
Esame/RA hours are a declared GAP-1; 05_Docenti: the weekly-count columns moved
to 06) are NOT converted: see the GAP MAP in the README. Improvising a lossy
converter would be exactly the "second ingestion path" the architecture forbids.

Coverage (canonical CSV <- workbook sheet):
  seed/subjects.csv  <- 08_Materie   (subject, roomTags, professionalizzanti)
  seed/rooms.csv     <- 17_Aule      (room, capacity)
  seed/groups.csv    <- 10_Classi    (group, course_code, enrolled)

Usage::

    scripts/workbook/.venv/bin/python scripts/workbook/xlsx_to_csv.py [--workbook X] [--outdir D]
"""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

from openpyxl import load_workbook

OUTPUT_DIR = Path(__file__).resolve().parent / "output"


def _header_index(ws) -> dict[str, int]:
    """Map header name -> 1-based column index, by reading row 1."""
    return {str(ws.cell(row=1, column=c).value): c for c in range(1, ws.max_column + 1)}


def _cell(ws, row, idx) -> str:
    """A cell value as a stripped string, '' for None (matches seed empty cells)."""
    v = ws.cell(row=row, column=idx).value
    return "" if v is None else str(v).strip()


def _is_marker_or_blank(first_cell: str) -> bool:
    """Skip non-data rows ([GAP ...]/[NOTA] grey markers, or a blank first cell)."""
    s = (first_cell or "").lstrip()
    return s.startswith("[") or s == ""


# --- extractors: workbook sheet -> canonical seed-CSV rows (header + body) ---

def extract_subjects(wb) -> list[list[str]]:
    """08_Materie -> rows for seed/subjects.csv (subject, roomTags, professionalizzanti).

    Inverse of the generator: tagAula ';' list stays as-is (empty cell -> empty),
    laboratorio is NOT used (subjects.csv keys lab on roomTags non-empty), the
    professionalizzante SI/NO cell maps back to true/false.
    """
    ws = wb["08_Materie"]
    idx = _header_index(ws)
    out = [["subject", "roomTags", "professionalizzanti"]]
    for r in range(2, ws.max_row + 1):
        materia = _cell(ws, r, idx["materia"])
        tag = _cell(ws, r, idx["tagAula"])
        prof = _cell(ws, r, idx["professionalizzante"])
        if _is_marker_or_blank(materia):
            continue
        out.append([materia, tag, "true" if prof.upper() == "SI" else "false"])
    return out


def extract_rooms(wb) -> list[list[str]]:
    """17_Aule -> rows for seed/rooms.csv (room, capacity)."""
    ws = wb["17_Aule"]
    idx = _header_index(ws)
    out = [["room", "capacity"]]
    for r in range(2, ws.max_row + 1):
        aula = _cell(ws, r, idx["aula"])
        cap = _cell(ws, r, idx["capienza"])
        if _is_marker_or_blank(aula):
            continue
        out.append([aula, cap])
    return out


def extract_groups(wb) -> list[list[str]]:
    """10_Classi -> rows for seed/groups.csv (group, course_code, enrolled)."""
    ws = wb["10_Classi"]
    idx = _header_index(ws)
    out = [["group", "course_code", "enrolled"]]
    for r in range(2, ws.max_row + 1):
        classe = _cell(ws, r, idx["classe"])
        codice = _cell(ws, r, idx["codiceCorso"])
        iscritti = _cell(ws, r, idx["iscritti"])
        if _is_marker_or_blank(classe):
            continue
        out.append([classe, codice, iscritti])
    return out


# The registry of (sheet -> (canonical CSV name, extractor)). Extend ONLY with a
# lossless mapping; a lossy or absent mapping is a documented GAP, not an entry.
CONVERTERS = {
    "subjects.csv": extract_subjects,
    "rooms.csv": extract_rooms,
    "groups.csv": extract_groups,
}


def write_csv(rows: list[list[str]], path: Path) -> None:
    """Write rows as a plain comma-separated CSV (QUOTE_MINIMAL, no comma-in-field
    by project rule) so it matches the seed CSVs cell-for-cell."""
    with path.open("w", newline="", encoding="utf-8") as fh:
        csv.writer(fh).writerows(rows)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--workbook", default=str(OUTPUT_DIR / "AnnoScolastico_2026-27.xlsx"))
    ap.add_argument("--outdir", default=str(OUTPUT_DIR / "canonical_csv"))
    args = ap.parse_args()
    wb = load_workbook(args.workbook)
    outdir = Path(args.outdir)
    outdir.mkdir(parents=True, exist_ok=True)
    for name, extract in CONVERTERS.items():
        rows = extract(wb)
        write_csv(rows, outdir / name)
        print(f"  {name:14} {len(rows) - 1:4d} righe -> {outdir / name}")
    print(f"convertito: {args.workbook} -> {outdir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
