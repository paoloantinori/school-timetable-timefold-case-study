"""TASK-471 - Derive the theory-block policy per (classe, materia) from the golden.

The school places ~94% of theory at 1h/day and admits 2h blocks only for a few
subjects (verdetto TASK-470: the residual "bound repetitions" is a school
PRACTICE, not discoverable from construction input alone). This module turns that
practice into a DECLARED INPUT for the workbook sheet ``22_TeoriaBlocco``: a
proposed ``bloccoTeoria`` policy per (classe, materia), with the evidence (hours,
active days, days with a >=2h block, average hours per active day) so referente can
see WHY and confirm or correct.

This is a GOLDEN-RUNTIME READ (direttiva solo-input + TASK-466: the golden is the
oracle that reveals implicit input to make explicit). The golden window closes once
the school hands us inputs only, so this derivation has an expiry date like every
golden read on a production path (censito TASK-452): once referente confirms a row its
``origine`` flips to ``scuola`` and the golden is no longer consulted for it.

``is_theory`` is imported, not re-transcribed, from
:mod:`scripts.analyze.theory_adjacent_residual_dump` (the authoritative Python
mirror of the M-TR constraint's predicate, TASK-468; itself a hand-mirror of the
Java ``SubjectNormalization.isTheoryForRule``, kept in lockstep like every Java
constant in :mod:`shared`). The TASK-471 draft taught the hard rule "mai
ri-battere i set a mano": a hand-retyped LAB set with a typo ("mecaniche" vs
"meccaniche") had classified a lab as theory. Reusing the one mirror instead of a
second hand-pinned copy makes that class of bug impossible.

Trappole, identical to ``theory_adjacent_residual_dump`` and the KPI family:
Saturdays (no Timeslot), placeholder teachers/subjects (Fasullo/Fasutto/Stage),
and ``closed`` rows (handled by :func:`shared.parse_lessons_csv`). On the 2026/27
golden that leaves 10542 real lessons, 6800 of which are theory over 196
(classe, materia) pairs.

Classification thresholds are DECLARED and arbitrable (not physics): the signal is
``avg_hours_per_active_day = theory_hours / active_days``. They match the Fedora
draft (task471-theory-distribution-draft-2026-08-01.md) and the gate
(:mod:`test_theory_block_identity`) pins the live derivation to that proven CSV.

Read-only. No openpyxl: the sheet emitter (:mod:`generate_workbook`) consumes the
list[dict] this returns.
"""

from __future__ import annotations

import sys
from collections import defaultdict
from pathlib import Path

# This module lives at scripts/workbook/; the authoritative Python mirror of
# is_theory lives at scripts/analyze/theory_adjacent_residual_dump.py (itself a
# hand-mirror of the Java SubjectNormalization.isTheoryForRule, kept in lockstep
# like every Java constant in shared.py). Import instead of re-transcribing: a
# second hand-pinned copy here could drift and silently misclassify (the
# "mecaniche" lesson), so we reuse the one mirror that already matches M-TR.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "analyze"))
import shared  # noqa: E402  (import after sys.path tweak above)
from theory_adjacent_residual_dump import PLACEHOLDER, is_theory  # noqa: E402

# The golden solution the school handed us (10.542 real lessons post-trappole).
GOLDEN = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "seed" / "lessons.csv"

# Declared classification thresholds on avg_hours_per_active_day (arbitrable).
# <= STRETTA_MAX  -> 1h/giorno (stretta): the golden places the subject almost
#                    always at 1h/day.
# >= BLOCCO_MIN  -> 2h ammessa (blocco): the golden uses >=2h blocks regularly.
# in between     -> mista/da-chiedere: mostly 1h/day but with significant 2h days
#                    (typically Scienze/Matematica/Lingua) -> referente question.
STRETTA_MAX = 1.15
BLOCCO_MIN = 1.85

STRETTA = "1h/giorno (stretta)"
MISTA = "mista/da-chiedere"
BLOCCO = "2h ammessa (blocco)"

# Display order within a classe: the action items (blocco, then mista) bubble to
# the top so referente meets the rows that need a decision first; the trivial 1h
# defaults follow. Used by the sheet emitter.
_RANK = {BLOCCO: 0, MISTA: 1, STRETTA: 2}


def classify(avg: float) -> str:
    """Map an average-hours-per-active-day signal to a proposed block policy.

    Operates on the RAW average (not the 2-decimal display): e.g. 1.1538 is mista
    even though it rounds to 1.15, which is what separates G09 Merceologia
    (1.1538 -> mista) from G01 Figurino (1.125 -> stretta)."""
    if avg <= STRETTA_MAX:
        return STRETTA
    if avg >= BLOCCO_MIN:
        return BLOCCO
    return MISTA


def theory_block_policy() -> list[dict]:
    """Per-(classe, materia) theory-block policy derived live from the golden.

    Each row: ``{classe, materia, oreTeoria, giorniAttivi, giorniConBlocco,
    avg (raw float), bloccoTeoria}``. ``avg`` is left raw; callers format to 2
    decimals for display (classification already happened on the raw value)."""
    # parse_lessons_csv skips closed rows and yields (group, date, hour, subject,
    # teacher). strip_fields stays False to mirror the Fedora draft exactly (the
    # golden is clean, so stripped == unstripped; the gate pins this).
    lessons = shared.parse_lessons_csv(GOLDEN, include_hour=True)

    # Trappole identical to theory_adjacent_residual_dump: drop Saturdays and
    # placeholder teacher/subject (Fasullo/Fasutto/Stage). One structure,
    # per_day[(group, subject)][date] = lesson count: hours, active days and
    # days-with-block are all derivable from it, so no parallel dicts to keep in
    # sync.
    per_day: dict[tuple[str, str], dict] = defaultdict(lambda: defaultdict(int))
    for group, d, _hour, subject, teacher in lessons:
        if d.weekday() == 5:  # Saturday: no Timeslot, off-grid
            continue
        if teacher in PLACEHOLDER or subject in PLACEHOLDER:
            continue
        if not is_theory(subject, group):
            continue
        per_day[(group, subject)][d] += 1

    rows: list[dict] = []
    for (group, subject), counts in per_day.items():
        h = sum(counts.values())
        days = len(counts)
        days_2plus = sum(1 for c in counts.values() if c >= 2)
        avg = h / days
        rows.append({
            "classe": group,
            "materia": subject,
            "oreTeoria": h,
            "giorniAttivi": days,
            "giorniConBlocco": days_2plus,
            "avg": avg,
            "bloccoTeoria": classify(avg),
        })
    return rows


def sort_key(row: dict, classe_order: dict[str, int]) -> tuple:
    """Presentation order for the sheet: classe (canonical), then policy rank
    (blocco/mista first), then evidence weight (days_2plus, oreTeoria desc),
    then materia as a deterministic tiebreak."""
    return (
        classe_order.get(row["classe"], 999),
        _RANK[row["bloccoTeoria"]],
        -row["giorniConBlocco"],
        -row["oreTeoria"],
        row["materia"],
    )
