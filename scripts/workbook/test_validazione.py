#!/usr/bin/env python3
"""Regression gate for the 99_Validazione system-side controls (Tappa 5).

Two distinct jobs, do not confuse them:

- ``validazione.py`` (the tool) reports the school's data state and exits 1 when
  there is any ERRORE. That is the *anticipo della difesa* the proposal wants.
- THIS file (the gate) is the developer regression guard. It pins the CURRENT
  finding set as the baseline and exits 1 only when that set CHANGES. The pinned
  ERROREs are not bugs to fix here: they are the known state of the 2026/27 seed
  (monte-ore residuals that trace to the declared gaps - GAP-1 Esame/RA hours not
  yet compiled as tipo=esame|pool rows, and stage hours not yet in 16_Stage, which
  V3 will name). A seed change that moves any finding - a new residual appearing,
  T01's saturation shifting, a control moving from DEFERRED to implemented -
  fails this gate on purpose, so the change is seen and the baseline updated
  deliberately rather than absorbed silently (the Tappa 3/4 gate-blind-hole rule).

Run: scripts/workbook/.venv/bin/python scripts/workbook/test_validazione.py
"""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

import validazione as V
import generate_workbook as GW


def _fresh_workbook() -> str:
    """Generate the workbook into a temp file so the gate validates the CURRENT
    generator output. The seed baseline (V1-V23) is unchanged; the structural
    controls (V24+) read the compiled workbook, so the gate now also asserts it is
    structurally clean. No side effect on output/."""
    fd, p = tempfile.mkstemp(suffix=".xlsx")
    os.close(fd)
    GW.build_workbook().save(Path(p))
    return p

# Frozen baseline. ERRORE/AVVISO are (cid, message) tuples; DEFERRED is the cid
# set (the message is human guidance, not a fact to pin). Transcribed verbatim
# from validazione.run_all() on 2026-07-29 against the committed 2026/27 seed.
EXPECTED_ERRORE = [
    # req5 (Tappa 6): G04 stage now DECLARED in 16_Stage (sources.STAGE_PATTERNS,
    # 224h = 208 in-grid + 16 off-grid). V1 drops from the phantom +240h (stage was
    # undeclared) to the REAL +16h gap (990 attese vs 750 incarichi + 224 stage).
    ("V1", "G04: incarichi+stage 974h contro oreAnnueAttese 990h (residuo +16h)"),
    # TASK-592 cure (11/09): the RA pool row became 9 real rows (41h, the golden's
    # own split): incarichi 626 -> 667, residuo +364 -> +323.
    ("V1", "G08: incarichi+stage 667h contro oreAnnueAttese 990h (residuo +323h)"),
    ("V1", "G09: incarichi+stage 666h contro oreAnnueAttese 990h (residuo +324h)"),
    ("V1", "G10: incarichi+stage 670h contro oreAnnueAttese 990h (residuo +320h)"),
    ("V1", "G11: incarichi+stage 966h contro oreAnnueAttese 990h (residuo +24h)"),
    ("V1", "G12: incarichi+stage 970h contro oreAnnueAttese 1000h (residuo +30h)"),
    ("V1", "G13: incarichi+stage 683h contro oreAnnueAttese 990h (residuo +307h)"),
    ("V1", "G14: incarichi+stage 115h contro oreAnnueAttese 120h (residuo +5h)"),
    # req5: the V3 "G04: ...nessuna riga 16_Stage" finding is GONE - G04 stage
    # is now a declared 16_Stage row that reconciles (208 in-grid + 16 off-grid = 224).
    ("V3", "G07: stage in piano 300h in-grid + 0h off-grid = 300h non concilia con 16_Stage.oreTotaliAttese 295h (delta +5)"),
    ("V3", "G08: 276h stage in-grid (+24h off-grid) nel piano ma nessuna riga 16_Stage (dichiarare lo stage; off-grid = oreFuoriGriglia)"),
    ("V3", "G09: 292h stage in-grid (+8h off-grid) nel piano ma nessuna riga 16_Stage (dichiarare lo stage; off-grid = oreFuoriGriglia)"),
    ("V3", "G10: 228h stage in-grid (+72h off-grid) nel piano ma nessuna riga 16_Stage (dichiarare lo stage; off-grid = oreFuoriGriglia)"),
    ("V3", "G13: 295h stage in-grid (+0h off-grid) nel piano ma nessuna riga 16_Stage (dichiarare lo stage; off-grid = oreFuoriGriglia)"),
    ("V2", "G03: incarichi 990h vs piano aula+lab 969h (delta +21h)"),
    ("V2", "G04: incarichi 750h vs piano aula+lab 835h (delta -85h)"),
    ("V2", "G07: incarichi 695h vs piano aula+lab 690h (delta +5h)"),
    # TASK-592 cure (11/09): incarichi 667 (= golden count) vs piano 666 - the old
    # -40h skip hole is closed; the +1h residue IS the notified 40-vs-41 sheet rounding.
    ("V2", "G08: incarichi 667h vs piano aula+lab 666h (delta +1h)"),
    # TASK-550 input cure (27/08), re-baselined 11/09 (the drift sat unrecorded since):
    # the sheet's 4 phantom hours were cut (04/06 5->4, 07/06 row dropped) -> piano 662.
    ("V2", "G09: incarichi 666h vs piano aula+lab 662h (delta +4h)"),
    ("V2", "G10: incarichi 670h vs piano aula+lab 666h (delta +4h)"),
    # --- G14 baseline RIGENERATA per TASK-462 (2026-07-30), confronto dichiarato ---
    # BEFORE (G14 990h, calendario copiato da G01): piano aula+lab 990h, e G14
    #   sforava su V7/V8/V9 (70 gg oltre finestra 11 / 101 gg su weekday chiuso / 136 gg
    #   oltre maxOreGiorno 5). V2 era "115h vs 990h (delta -875h)".
    # AFTER  (G14 120h = 70 aula + 50 lab, calendario 'G14 2' corretto): piano
    #   aula+lab 120h (V2 -> "115h vs 120h, delta -5h"); V7/V8/V9 NON firano piu' (G14
    #   120h non sfora finestra/maxOreGiorno/weekday); nuovo V16 AVVISO (41 gg no-session:
    #   G14 part-time non copre tutti i giorni scolastici). V1 invariato (oreAnnueAttese
    #   120h viene da groups.csv, non dal calendario). Prima/dopo dichiarato, non silenzioso.
    ("V2", "G14: incarichi 115h vs piano aula+lab 120h (delta -5h) [il motore esenta G14 dal fill: isG14, SchoolScheduleConstraintProvider.java:1839; sanare in input o ratificare l'esenzione]"),
]
EXPECTED_AVVISO = [
    ("V4b", "T01: saturazione 80.2% (231h / 288h)"),
    ("V13", "tag 'acconciatura': stanza max capienza 20 < iscritti 22 di una classe che la usa"),
    ("V16", "G07: 1 giorni scolastici aperti senza riga in 15 (probabile no-session: aggiungere riga 0,0,0 con motivo)"),
    ("V16", "G11: 2 giorni scolastici aperti senza riga in 15 (probabile no-session: aggiungere riga 0,0,0 con motivo)"),
    ("V16", "G10: 12 giorni scolastici aperti senza riga in 15 (probabile no-session: aggiungere riga 0,0,0 con motivo)"),
    ("V16", "G12: 21 giorni scolastici aperti senza riga in 15 (probabile no-session: aggiungere riga 0,0,0 con motivo)"),
    # TASK-550 input cure (27/08), re-baselined 11/09: dropping the 07/06 quota row left
    # two more school dates without a sheet row (3 -> 5 open days).
    ("V16", "G09: 5 giorni scolastici aperti senza riga in 15 (probabile no-session: aggiungere riga 0,0,0 con motivo)"),
    ("V16", "G08: 3 giorni scolastici aperti senza riga in 15 (probabile no-session: aggiungere riga 0,0,0 con motivo)"),
    ("V16", "G14: 41 giorni scolastici aperti senza riga in 15 (probabile no-session: aggiungere riga 0,0,0 con motivo)"),
]
# The controls not yet wired (need a generated-sheet source not in the seed).
# V4c is deferred: its single-day form is redundant with V4, and the proposal's
# full intent (per-(docente,materia,giorno) concentration) needs data not in the seed.
EXPECTED_DEFERRED = {
    "V4c", "V12", "V14", "V17",
}


def main() -> int:
    findings = V.run_all(_fresh_workbook())
    erre = sorted((f.cid, f.message) for f in findings if f.level == V.ERRORE)
    avv = sorted((f.cid, f.message) for f in findings if f.level == V.AVVISO)
    defer = {f.cid for f in findings if f.level == V.DEFERRED}

    expected_erre = sorted(EXPECTED_ERRORE)
    expected_avv = sorted(EXPECTED_AVVISO)

    ok = True
    if erre != expected_erre:
        ok = False
        print("ERRORE findings drift from baseline:")
        _diff(expected_erre, erre)
    if avv != expected_avv:
        ok = False
        print("AVVISO findings drift from baseline:")
        _diff(expected_avv, avv)
    if defer != EXPECTED_DEFERRED:
        ok = False
        print("DEFERRED control set drift from baseline:")
        print(f"  expected: {sorted(EXPECTED_DEFERRED)}")
        print(f"  actual:   {sorted(defer)}")

    if ok:
        print(f"test_validazione OK: baseline pinned "
              f"({len(erre)} ERRORE, {len(avv)} AVVISO, {len(defer)} DEFERRED).")
        return 0
    print("test_validazione FAIL: finding set changed. If the change is intended, "
          "update EXPECTED_* in this gate and document why.")
    return 1


def _diff(expected: list, actual: list) -> None:
    e, a = set(expected), set(actual)
    for x in sorted(a - e):
        print(f"  + unexpected: {x}")
    for x in sorted(e - a):
        print(f"  - missing:    {x}")


if __name__ == "__main__":
    sys.exit(main())
