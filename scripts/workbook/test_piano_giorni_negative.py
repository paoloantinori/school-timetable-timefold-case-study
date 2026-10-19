#!/usr/bin/env python3
"""Negative proof for the piano-giorni gate (TASK-445 Tappa 4).

A gate that only ever sees clean data proves nothing about its failure mode. This
test takes a REAL compress() output, corrupts it the way a buggy extractor (or a
planner diluting a pattern) would, and asserts the gate (:func:`test_piano_giorni_roundtrip.verify`)
reports errors for every corruption. It exits 0 only if the gate FAILS LOUD on:

  A. a dropped deviation exception - the date it covered is now unexplained, or the
     underlying pattern plan mismatches the quota (the exact blind spot maintainer named:
     a lie patched over by an exception must resurface when the exception is removed).
  B. a widened pattern period - extending validoA forces the pattern over dates with
     a different plan or no quota, which is "allargare la segmentazione per far
     tornare i conti" and must be caught.
  C. a dropped pattern - its dates become unexplained residuals (the residual count
     must drive exit != 0).
  D. a phantom off-grid row that is not stage - the off-grid declaration honesty.

The clean baseline must report ZERO errors (sanity). Run::

    scripts/workbook/.venv/bin/python scripts/workbook/test_piano_giorni_negative.py
"""

from __future__ import annotations

import copy
import datetime
import sys

import sources as S
import piano_giorni_compress as P
from test_piano_giorni_roundtrip import verify


def _clone(patterns, exceptions, off_grid):
    return copy.deepcopy(patterns), copy.deepcopy(exceptions), copy.deepcopy(off_grid)


def main() -> int:
    patterns, exceptions, off_grid, _metrics = P.compress()

    # Sanity: the real, uncorrupted output must pass cleanly.
    errs, _ = verify(patterns, exceptions, off_grid)
    if errs:
        print(f"FAIL baseline: il compress() pulito ha gia' {len(errs)} errori (bug nel gate o nell'estrattore):")
        for e in errs[:10]:
            print("  - " + e)
        return 1

    results = []

    # A. Drop one DEVIATION exception (hours > 0, a real plan break). The date it
    #    covered must now be either a residual or a pattern/quota mismatch.
    pats, excs, og = _clone(patterns, exceptions, off_grid)
    deviation = next((e for e in excs if e["oreAula"] + e["oreLab"] + e["oreStage"] > 0), None)
    if deviation is None:
        print("FAIL A: nessuna eccezione di deviazione da corrompere (dato inatteso)")
        return 1
    excs.remove(deviation)
    errs, _ = verify(pats, excs, og)
    results.append(("A drop-deviation", deviation["classe"] + " " + deviation["data"], len(errs) > 0, errs))

    # B. Widen one pattern: extend validoA to the year end. The pattern is now forced
    #    over later same-weekday dates whose plan differs or that are no-session.
    pats, excs, og = _clone(patterns, exceptions, off_grid)
    pats[0] = dict(pats[0])
    pats[0]["validoA"] = S.ANNO_DATA_FINE
    errs, _ = verify(pats, excs, og)
    results.append(("B widen-period", pats[0]["classe"] + " " + pats[0]["giorno"], len(errs) > 0, errs))

    # C. Drop one pattern: its covered dates (not under an exception) become residuals.
    pats, excs, og = _clone(patterns, exceptions, off_grid)
    dropped = pats[0]
    del pats[0]
    errs, n_res = verify(pats, excs, og)
    detected = len(errs) > 0 and n_res > 0
    results.append(("C drop-pattern", dropped["classe"] + " " + dropped["giorno"], detected, errs))

    # D. Inject a phantom off-grid row that is NOT stage: the honesty check must fire.
    pats, excs, og = _clone(patterns, exceptions, off_grid)
    phantom = datetime.date.fromisoformat("2027-02-13")  # a Saturday, not a closure
    og.append({"group": "G01", "date": phantom, "aula": 6, "lab": 0, "stage": 0})
    errs, _ = verify(pats, excs, og)
    results.append(("D phantom-offgrid-not-stage", "G01 2027-02-13", len(errs) > 0, errs))

    failed = [r for r in results if not r[2]]
    if failed:
        print("FAIL: il gate NON ha rilevato una corruzione (fail-loud rotto):")
        for name, where, _ok, _errs in failed:
            print(f"  - {name} su {where}: NESSUN errore riportato")
        print("Dettaglio di tutte le corruzioni:")
        for name, where, ok, errs in results:
            tag = "RILEVATA" if ok else "PERSA"
            print(f"  [{tag}] {name} ({where}): {len(errs)} errori")
            for e in errs[:3]:
                print(f"        - {e}")
        return 1

    print("OK: il gate fallisce rumoroso su ogni corruzione (fail-loud verificato):")
    for name, where, _ok, errs in results:
        print(f"  - {name} ({where}): {len(errs)} errori")
    return 0


if __name__ == "__main__":
    sys.exit(main())
