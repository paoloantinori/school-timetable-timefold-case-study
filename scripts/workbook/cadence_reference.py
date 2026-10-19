"""TASK-445/441 - Emit the weekly cadence reference per (classe, materia) as DECLARED INPUT.

Sibling of :mod:`theory_block_policy` (TASK-471): a golden-derived ``sistema`` default
proposed for the workbook sheet ``21_CadenzaAttesa``, for referente to confirm. The cadence
(max hours/week + active weeks per (group, subject)) is what M34/M35 (TASK-425) consume
today from ``golden-cadence-reference.json``. In a no-golden year that JSON disappears
(M34/M35 self-gate inert), so the cadence must become DECLARED input to survive: this
module emits the golden-derived values as the workbook column so the school can confirm
them into ``scuola`` and keep the caps alive (censimento #3, TASK-441 policy).

This is a GOLDEN-RUNTIME READ with an expiry: the JSON is a golden-derived artifact, so
this derivation expires when the golden does. Once referente confirms a row its ``origine``
flips to ``scuola`` and the golden is no longer consulted for it (censimento + direttiva
solo-input).

Match to the constraint, not to the raw JSON (code-review I1): the JSON ``pairs`` array
is UNFILTERED (258 pairs on the 2026/27 seed), but the Java consumer
``ScheduleService.publishCadenceReferenceStatics`` applies its own ``total_hours < 10``
skip (micro-pairs whose cadence stats are unreliable), so M34/M35 actually cap **245**
pairs. This module applies the SAME filter (``MIN_TOTAL_HOURS``) so the sheet emits
exactly what the constraint caps, not the raw 258. The gate
(:mod:`test_cadence_identity`) asserts this match (tier 3), not just reshape fidelity.

The cadence is CONDITIONAL (censimento #3): if the school does NOT want the M34/M35 caps,
it leaves the sheet sistema/blank and M34/M35 stay inert (graceful). Declaring it
(origine=scuola) activates the caps. No break either way.

Read-only. No openpyxl: the sheet emitter consumes the list[dict].
"""

from __future__ import annotations

import json
from pathlib import Path

# The golden-derived cadence reference the M34/M35 constraints already consume
# (ScheduleService.publishCadenceReferenceStatics loads this same file).
JSON_REF = (Path(__file__).resolve().parents[2] / "claudedocs" / "spalmone-2026-27"
            / "data" / "golden-cadence-reference.json")

# SRC: ScheduleService.publishCadenceReferenceStatics (`if (totalHours < 10) { skipped++;
# continue; }`). The constraint skips micro-pairs (<10h) because their cadence stats are
# unreliable, so the workbook must too, or it would declare caps the constraint never
# enforces. Mirrored here as the single filter (the JSON's own min_hours_threshold is
# applied only to aggregate buckets, NOT to the pairs array the constraint reads).
MIN_TOTAL_HOURS = 10

# M34 binds where the weekly max is high (the school bursts past the average). Rows at or
# above this threshold get a referente note; below it the cap is trivial (1-2h) and no note.
HIGH_MAX_THRESHOLD = 4


def cadence_pairs() -> list[dict]:
    """The M34/M35-consumed cadence pairs, reshaped to sheet columns.

    Each row: ``{classe, materia, oreSettimanaMax, settimaneAttive}``. Reads
    ``golden-cadence-reference.json`` ``pairs``, applies the same ``total_hours >=
    MIN_TOTAL_HOURS`` skip the Java consumer applies (so the sheet matches what M34/M35
    actually cap, not the raw 258), and renames ``max``/``active_weeks``.

    No-golden year: the JSON is absent by design, so this returns ``[]`` and the sheet
    emits headers-only for the school to fill (the declared-input survival path). The
    generator is the golden-year populator; the no-golden path is school-filled."""
    try:
        with JSON_REF.open(encoding="utf-8") as fh:
            data = json.load(fh)
    except FileNotFoundError:
        return []  # no-golden year: sheet emits headers-only (school fills)
    out = []
    for p in data["pairs"]:
        if p.get("total_hours", 0) < MIN_TOTAL_HOURS:
            continue  # match the Java consumer's micro-pair skip
        out.append({
            "classe": p["group"],
            "materia": p["subject"],
            "oreSettimanaMax": p["max"],
            "settimaneAttive": p["active_weeks"],
        })
    return out
