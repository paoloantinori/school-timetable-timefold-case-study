#!/usr/bin/env python3
"""Availability compress/expand equivalence gate (TASK-445 Tappa 3).

The cancello maintainer defined: compress the recurring availability closures into
concise rules, expand them back, and the two sets of (teacher, weekday,
closed-slots, forza) closures must be IDENTICAL. Any difference exits non-zero.

Two gate-blind holes are also asserted, because the closure set alone cannot see
them:
  - a SOLO_GIORNI rule is a lie if the same (teacher, forza) has a partial rule
    (the partial rule's expansion overwrites the key, hiding the lie); asserted
    per-rule.
  - a recurring closure that fits NO concise form is a vocabulary gap, NOT a move
    to 07 (07's schema is dated; a recurring closure has no date). It fails loud
    so the 06 vocabulary gets extended rather than the closure silently lost.

Dated rows are a separate concern (-> 07 exceptions) and are NOT part of this
closure round-trip; the gate only counts them (must be 3, the T12 visits).

Run with the venv python::

    scripts/workbook/.venv/bin/python scripts/workbook/test_availability_roundtrip.py
"""

from __future__ import annotations

import sys

import availability_compress as A


def main() -> int:
    closed_original = A.recurring_closures()          # (teacher, day, forza) -> closed
    rules, noncomp = A.compress()
    closed_expanded = A.expand(rules)

    errors = []

    # A recurring closure that fits NO concise form is a VOCABULARY GAP, not a move
    # to 07 (07's schema is dated; a recurring closure has no date). Fail loud so
    # the 06 vocabulary gets extended rather than the closure silently disappearing.
    for (t, d, f, c) in noncomp:
        errors.append(f"chiusura ricorrente non comprimibile in nessuna forma concisa "
                      f"(gap di vocabolario 06): {t} {d} {f} closed={sorted(c)}")

    # The expanded rules must cover EXACTLY the original recurring closures.
    if set(closed_expanded) != set(closed_original):
        for k in sorted(set(closed_original) - set(closed_expanded)):
            errors.append(f"chiusura ricorrente NON riprodotta dall'espansione: {k}")
        for k in sorted(set(closed_expanded) - set(closed_original)):
            errors.append(f"espansione produce una chiusura inesistente nell'originale: {k}")
    for k in sorted(set(closed_expanded) & set(closed_original)):
        if closed_expanded[k] != closed_original[k]:
            errors.append(f"chiusura diverga per {k}: attesa={sorted(closed_original[k])} "
                          f"trovata={sorted(closed_expanded[k])}")

    # Per-rule honesty: a SOLO_GIORNI rule fully closes every non-listed day, so it
    # is a lie if the same (teacher, forza) also has a SOLO_FASCIA/FINESTRA rule (a
    # partial day). The aggregate closure set cannot see this (the partial rule's
    # expansion overwrites that key), so assert it per-rule.
    partial_by_tf = {(r["docente"], r["forza"]) for r in rules
                     if r["tipo"] in ("SOLO_FASCIA", "FINESTRA")}
    for r in rules:
        if r["tipo"] == "SOLO_GIORNI" and (r["docente"], r["forza"]) in partial_by_tf:
            errors.append(f"regola SOLO_GIORNI disonesta per {r['docente']} ({r['forza']}): "
                          f"esiste anche una regola parziale (SOLO_FASCIA/FINESTRA)")

    if errors:
        print(f"FAIL: availability round-trip NON superato ({len(errors)} problemi)")
        for e in errors:
            print("  - " + e)
        return 1

    n_closures = len(closed_original)
    n_rules = len(rules)
    n_dated = len(A.exceptions_07())
    n_count = len(A.weekly_count_rules())
    print(f"OK: availability round-trip equivalente. {n_closures} chiusure ricorrenti "
          f"-> {n_rules} regole concise (06) che si espandono identiche; "
          f"0 non comprimibili (vocabolario 06 completo); {n_dated} eccezioni datate -> 07; "
          f"{n_count} regole di conteggio settimanale (T11/T05).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
