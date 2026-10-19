#!/usr/bin/env python3
"""Identity gate for the cadence reference (TASK-445/441, sheet 21_CadenzaAttesa).

The contract: the workbook emits the SAME set M34/M35 actually cap, derived from the
SAME ``golden-cadence-reference.json`` the constraints consume. Two things must hold:

1. The JSON ``pairs`` array is UNFILTERED (258 on 2026/27), but the Java consumer
   ``ScheduleService.publishCadenceReferenceStatics`` skips ``total_hours < 10``
   (micro-pairs), so M34/M35 cap 245. The module (``cadence_reference.MIN_TOTAL_HOURS``)
   applies the SAME filter. Tier 2 asserts the module's output matches the filtered set
   AND that the excluded count equals the JSON's <10h pairs: this is the load-bearing
   "matches the constraint, not the raw JSON" check (code-review I1).
2. The reshape only renames JSON fields (no re-derivation), so it cannot drift from the
   reference within the filtered set. Tier 1 asserts reshape fidelity; tier 3 round-trips
   the SHEET the school opens (read by header name), like
   :mod:`test_theory_block_identity`.

Drift handling: if the JSON is regenerated (``cadence-kpi.py -j``) or the Java filter
changes, tiers 1-2 fail LOUD. Silent absorption is what the gate prevents.

Run with the venv python (round-trip tier needs openpyxl)::

    scripts/workbook/.venv/bin/python scripts/workbook/test_cadence_identity.py
"""

from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

from openpyxl import load_workbook

import cadence_reference as C
from generate_workbook import build_workbook

JSON_REF = (Path(__file__).resolve().parents[2] / "claudedocs" / "spalmone-2026-27"
            / "data" / "golden-cadence-reference.json")


def _pair_key(classe, materia, max_h, active):
    return (classe, materia, int(max_h), int(active))


def _live_keys() -> set:
    return {_pair_key(r["classe"], r["materia"], r["oreSettimanaMax"], r["settimaneAttive"])
            for r in C.cadence_pairs()}


def _sheet_keys() -> set:
    """Build the workbook, reload, read 21_CadenzaAttesa BY HEADER NAME."""
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "gate.xlsx"
        build_workbook().save(path)
        ws = load_workbook(path)["21_CadenzaAttesa"]
    names = ["classe", "materia", "oreSettimanaMax", "settimaneAttive"]
    col = {str(ws.cell(row=1, column=c).value): c for c in range(1, ws.max_column + 1)}
    out = set()
    for r in range(2, ws.max_row + 1):
        out.add(tuple(ws.cell(row=r, column=col[name]).value for name in names))
    return out


def main() -> int:
    errors = []

    # Load the JSON once: the filtered authority + the <10h exclusion count both come from it.
    with JSON_REF.open(encoding="utf-8") as fh:
        data = json.load(fh)
    raw_pairs = data["pairs"]
    authority = {_pair_key(p["group"], p["subject"], p["max"], p["active_weeks"])
                 for p in raw_pairs if p.get("total_hours", 0) >= C.MIN_TOTAL_HOURS}
    under10 = sum(1 for p in raw_pairs if p.get("total_hours", 0) < C.MIN_TOTAL_HOURS)
    live = _live_keys()

    # 1. Reshape fidelity: the module's filtered output == the filtered authority set.
    if live != authority:
        errors.append("reshape/filter NON fedele: "
                      f"missing={sorted(authority - live)[:5]}, "
                      f"extra={sorted(live - authority)[:5]}")

    # 2. Constraint-reference match: the module emits exactly the >= MIN_TOTAL_HOURS pairs
    #    (what M34/M35 cap) and excludes exactly the <10h micro-pairs the Java consumer skips.
    if len(live) != len(authority):
        errors.append(f"count mismatch: live={len(live)} vs authority(filtered)={len(authority)}")
    if len(raw_pairs) - len(live) != under10:
        errors.append(f"filter mismatch: raw {len(raw_pairs)} - live {len(live)} = "
                      f"{len(raw_pairs) - len(live)} excluded, but JSON has {under10} "
                      f"pairs < {C.MIN_TOTAL_HOURS}h (the module must skip exactly those)")

    # 3. Round-trip: the SHEET the school opens carries the same rows.
    sheet = _sheet_keys()
    if sheet != live:
        errors.append("foglio 21_CadenzaAttesa (round-trip) != derivazione: "
                      f"missing={sorted(live - sheet)[:5]}, extra={sorted(sheet - live)[:5]}")

    if errors:
        print(f"FAIL: gate cadence identity NON superato ({len(errors)} problemi)")
        for e in errors:
            print("  - " + e)
        return 1

    print(f"OK: cadence reference live (cadence_reference) == set M34/M35-consumed "
          f"({len(live)} coppie >= {C.MIN_TOTAL_HOURS}h, filtro match Java; reshape fedele; "
          f"{under10} micro-pairs <{C.MIN_TOTAL_HOURS}h esclusi). Round-trip foglio "
          f"21_CadenzaAttesa OK. Cadenza come input dichiarato (condizionale).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
