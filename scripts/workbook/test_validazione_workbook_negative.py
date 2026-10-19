#!/usr/bin/env python3
"""Negative proof for the workbook-structural controls V24/V25/V26 (req 1/2/4).

A gate that only ever sees the clean generated workbook proves nothing about its
failure mode. This test builds the REAL workbook, corrupts it the way a school
compiling it would, and asserts each structural control FAILS LOUD on its
corruption. It exits 0 only if every corruption is detected. The clean baseline
must report ZERO structural findings (sanity).

Corruptions (grown one per requirement as the controls land):
  V24a a class refuso - 'G14 2' typed into 12_Incarichi.classe (req1)
  V24b a duplicated class row in 10_Classi (req1)

Run: scripts/workbook/.venv/bin/python scripts/workbook/test_validazione_workbook_negative.py
"""

from __future__ import annotations

import os
import shutil
import sys
import tempfile
from pathlib import Path

from openpyxl import load_workbook

import generate_workbook as GW
import validazione as V
import workbook_schema as WS

STRUCTURAL_CIDS = ("V24", "V25", "V26")


def _fresh_workbook() -> Path:
    """Generate the workbook into a temp file (no side effect on output/)."""
    fd, p = tempfile.mkstemp(suffix=".xlsx")
    os.close(fd)
    p = Path(p)
    GW.build_workbook().save(p)
    return p


def _corrupt_copy(src: Path, mutate) -> Path:
    """Copy the clean workbook to a temp path, apply `mutate(wb)`, save. Returns
    the corrupted path so run_all can load and check it."""
    fd, p = tempfile.mkstemp(suffix=".xlsx")
    os.close(fd)
    p = Path(p)
    shutil.copy(src, p)
    wb = load_workbook(p, data_only=False)
    mutate(wb)
    wb.save(p)
    return p


def _mut_class_refuso(wb) -> None:
    """V24a: a non-canonical class value typed into 12_Incarichi ('G14 2')."""
    ws = wb["12_Incarichi"]
    col = V._col_index(ws, "classe")
    ws.cell(row=2, column=col).value = "G14 2"


def _mut_class_duplicate(wb) -> None:
    """V24b: the same class appears twice in the 10_Classi anagraphic. Appends a
    true duplicate ROW (copy of row 2) so the canonical set is unchanged and only
    the duplication is tested, not a class-removal cascade."""
    ws = wb["10_Classi"]
    row2 = [ws.cell(row=2, column=c).value for c in range(1, ws.max_column + 1)]
    ws.append(row2)


def _mut_total_literal(wb) -> None:
    """V25a: the 12_Incarichi 'Totale ORE' formula replaced by a hand-typed number
    (the failure mode req2 exists for: hide a discrepancy with a static total)."""
    ws = wb["12_Incarichi"]
    doc = V._col_index(ws, "docente")
    ore = V._col_index(ws, "ore")
    for row in range(2, ws.max_row + 1):
        if str(ws.cell(row=row, column=doc).value).strip() == "Totale ORE":
            ws.cell(row=row, column=ore).value = 9999
            return


def _mut_calcolo_literal(wb) -> None:
    """V25b: a 99_Validazione CALCOLO TOTALE formula cell replaced by a literal."""
    ws = wb["99_Validazione"]
    in_section = False
    for row in range(1, ws.max_row + 1):
        a = str(ws.cell(row=row, column=1).value or "").strip()
        if a == "CALCOLO TOTALE":
            in_section = True
            continue
        if not in_section or a in ("", "classe"):
            continue
        ws.cell(row=row, column=2).value = 500  # literal replacing the =SUMIF
        return


def _mut_numeric_text(wb) -> None:
    """V26: text typed into a numeric column (the 'rosy' / 'colloqui t14'
    failure that landed in a St/La column on 2026/27)."""
    ws = wb["12_Incarichi"]
    ore = V._col_index(ws, "ore")
    ws.cell(row=2, column=ore).value = "rosy"


def _mut_delete_total(wb) -> None:
    """V25 (M2): the 12_Incarichi 'Totale ORE' row removed entirely. The control
    must NOT silent-pass: a required total going missing is itself a defect."""
    ws = wb["12_Incarichi"]
    doc = V._col_index(ws, "docente")
    for row in range(2, ws.max_row + 1):
        if str(ws.cell(row=row, column=doc).value).strip() == "Totale ORE":
            ws.delete_rows(row, 1)
            return


def _mut_delete_calcolo(wb) -> None:
    """V25 (M2): the whole 99_Validazione CALCOLO TOTALE section deleted."""
    ws = wb["99_Validazione"]
    for row in range(1, ws.max_row + 1):
        if str(ws.cell(row=row, column=1).value or "").strip() == "CALCOLO TOTALE":
            ws.delete_rows(row, ws.max_row - row + 1)
            return


def _mut_consistent_new_class(wb) -> None:
    """M1 no-false-positive: a new cohort added to 10_Classi AND referenced in
    12_Incarichi. Internally consistent -> V24 must NOT fire. The workbook is the
    INPUT for a new year, so a legitimate new class is expected, not an error."""
    new = "9 NEWCOHORT"
    ws10 = wb["10_Classi"]  # 12 cols; numeric: iscritti(3), maxOreGiorno(5), oreAnnueAttese(6)
    ws10.append([new, "NEW", 18, "", "", "", "NO", "NO", "NO", "NO", "scuola", "nuova classe"])
    ws12 = wb["12_Incarichi"]
    cls = V._col_index(ws12, "classe")
    ws12.cell(row=2, column=cls).value = new


def main() -> int:
    clean_path = _fresh_workbook()

    # Sanity: the clean generated workbook has ZERO structural findings.
    clean = [f for f in V.run_all(clean_path) if f.cid in STRUCTURAL_CIDS]
    if clean:
        print("FAIL baseline: il workbook pulito ha gia' finding strutturali:")
        for f in clean:
            print(f"  - [{f.cid}] {f.message}")
        return 1

    # Sanity (req4 prevention layer): the generator emits DataValidation on the
    # numeric columns of at least one data sheet, so Excel rejects text at entry.
    wb_check = load_workbook(clean_path, data_only=False)
    dv_sheets = [ws.title for ws in wb_check.worksheets if ws.data_validations.dataValidation]
    if not any(t in dv_sheets for t in WS.NUMERIC_COLUMNS):
        print("FAIL baseline: nessuna DataValidation sulle colonne numeriche (req4 prevention assente)")
        print(f"  fogli con DV: {dv_sheets}")
        return 1

    cases = [
        ("V24 refuso 'G14 2' in 12_Incarichi", _mut_class_refuso, "V24"),
        ("V24 classe duplicata in 10_Classi", _mut_class_duplicate, "V24"),
        ("V25 totale 12 sostituito con literal", _mut_total_literal, "V25"),
        ("V25 cella CALCOLO TOTALE sostituita con literal", _mut_calcolo_literal, "V25"),
        ("V25 riga totale 12 rimossa (no silent-pass)", _mut_delete_total, "V25"),
        ("V25 sezione CALCOLO TOTALE rimossa (no silent-pass)", _mut_delete_calcolo, "V25"),
        ("V26 testo in colonna numerica (ore='rosy')", _mut_numeric_text, "V26"),
    ]

    # M1 no-false-positive: a school adding a NEW cohort consistently (defined in
    # 10_Classi AND referenced in 12) is legitimate, not an error. V24 must stay
    # silent on it (the workbook is the input for a NEW year).
    nfp = _corrupt_copy(clean_path, _mut_consistent_new_class)
    nfp_v24 = [f for f in V.run_all(nfp) if f.cid == "V24"]
    if nfp_v24:
        print("FAIL no-false-positive: nuova classe coerente ha generato finding V24:")
        for f in nfp_v24:
            print(f"  - [{f.cid}] {f.message}")
        return 1
    results = []
    for name, mutate, expected_cid in cases:
        corrupted = _corrupt_copy(clean_path, mutate)
        found = [f for f in V.run_all(corrupted) if f.cid == expected_cid]
        results.append((name, len(found) > 0, found))

    failed = [r for r in results if not r[1]]
    if failed:
        print("FAIL: un controllo strutturale NON ha rilevato la sua corruzione:")
        for name, _ok, _found in failed:
            print(f"  - {name}: NESSUN finding {expected_cid}")
        print("Dettaglio di tutti i casi:")
        for name, ok, found in results:
            tag = "RILEVATA" if ok else "PERSA"
            print(f"  [{tag}] {name}: {len(found)} finding")
            for f in found[:3]:
                print(f"        - [{f.cid}] {f.message}")
        return 1

    print("OK: ogni corruzione strutturale e' rilevata (fail-loud verificato):")
    for name, _ok, found in results:
        print(f"  - {name}: {len(found)} finding ({found[0].message if found else ''})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
