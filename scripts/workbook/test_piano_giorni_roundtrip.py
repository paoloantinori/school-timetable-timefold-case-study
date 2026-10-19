#!/usr/bin/env python3
"""Piano-giorni pattern/expansion equivalence gate (TASK-445 Tappa 4).

The cancello maintainer defined and made his own: compress the per-date day-plan into
weekly patterns per period (13) + dated exceptions with motivo (14), expand them
back (15), and the in-grid expansion must be byte-identical to the seeded
DailyActivityQuota on (class, date, aula, lab, stage). Any difference exits
non-zero.

Because pattern extraction has the same blind spot the availability gate has - an
expansion that happens to match can hide a pattern that lies, patched over by an
exception - the gate asserts more than the expansion equality:

  - RESIDUAL FAIL-LOUD: every in-grid quota date must be explained by a pattern or
    an exception. Unexplained dates are counted in the output and exit non-zero if
    the count is > 0. A spurious expansion date (hours > 0 not in the quota) is the
    symmetric failure and exits non-zero too.
  - PER-PATTERN HONESTY: for every pattern period, every in-grid school day on its
    weekday in [validoDa, validoA] either matches the pattern plan in the quota or
    is an explicit exception. A period that covers an undeclared deviation (or an
    undeclared no-session day) is a lie widened to make the counts fit, and fails.
  - SEGMENTATION NOT WIDENED: every pattern covers >= 2 dates (recurrence); a
    single date is always an exception, never a one-date "pattern". The gate does
    not inflate patterns to reduce the exception count.
  - OFF-GRID DECLARED, NOT HIDDEN: Saturday and closure quota rows (no Timeslot
    exists) are not in 13/14/15 because Saturday cannot be a pattern weekday. The
    gate asserts the off-grid set is exactly the in-grid complement and that every
    off-grid row is genuinely on a Saturday/closure; it is declared as
    oreFuoriGriglia (99), never silently dropped. Off-grid may be ANY modality:
    stage is the norm, but a declared non-stage off-grid row (e.g. G14 Saturday
    aula, Q28) is legitimate, since req 6 cares that it is DECLARED with a reason,
    not that it is stage. The reason/motivo is a template concern (validator V27).

Plus the row-level invariants the proposal makes binding: no exception on a closure
date (V6), no duplicate keys (V11), every exception has a non-empty motivo (V17).

Run with the venv python::

    scripts/workbook/.venv/bin/python scripts/workbook/test_piano_giorni_roundtrip.py
"""

from __future__ import annotations

import datetime
import sys
from collections import Counter, defaultdict

import sources as S
import piano_giorni_compress as P


def verify(patterns, exceptions, off_grid):
    """Run every gate check against (patterns, exceptions, off_grid) and return the
    error list. Extracted from main() so the negative test can corrupt a real
    compress() output and PROVE the checks fire (a gate that only ever sees clean
    data proves nothing about its failure mode)."""
    school_days = set(S.school_year_dates())
    closed = S.closure_dates()

    # Ground truth: in-grid quota (hours > 0), the byte-identical target.
    quota_all = S.daily_activity_quota()
    quota_pos = {}        # (group, date) -> (aula, lab, stage), in-grid, hours > 0
    for r in quota_all:
        key = (r["group"], r["date"])
        if r["date"] in school_days and (r["aula"] or r["lab"] or r["stage"]):
            quota_pos[key] = (r["aula"], r["lab"], r["stage"])

    expanded = P.expand(patterns, exceptions)
    errors = []

    # --- byte-identical on (class, date, aula, lab, stage), hours > 0 ----------
    exp_pos = {k: (v[0], v[1], v[2]) for k, v in expanded.items()
               if v[0] or v[1] or v[2]}
    for k in sorted(set(quota_pos) - set(exp_pos)):
        errors.append(f"residuo NON spiegato da pattern ne' eccezione: {k[0]} {k[1]} "
                      f"piano={quota_pos[k]}")
    for k in sorted(set(exp_pos) - set(quota_pos)):
        errors.append(f"espansione produce una riga inesistente nel piano reale: "
                      f"{k[0]} {k[1]} piano={exp_pos[k]}")
    for k in sorted(set(exp_pos) & set(quota_pos)):
        if exp_pos[k] != quota_pos[k]:
            errors.append(f"piano diverge per {k[0]} {k[1]}: atteso={quota_pos[k]} "
                          f"trovato={exp_pos[k]}")

    # --- per-pattern honesty --------------------------------------------------
    exc_keys = {(e["classe"], datetime.date.fromisoformat(e["data"])) for e in exceptions}
    quota_by_g = defaultdict(dict)
    for (g, d), plan in quota_pos.items():
        quota_by_g[g][d] = plan
    for p in patterns:
        g = p["classe"]
        wd = P.WEEKDAY_CODES.index(p["giorno"])
        da = datetime.date.fromisoformat(p["validoDa"])
        a = datetime.date.fromisoformat(p["validoA"])
        plan = (p["oreAula"], p["oreLab"], p["oreStage"])
        for d in school_days:
            if d.weekday() != wd or not (da <= d <= a):
                continue
            if (g, d) in exc_keys:
                continue  # explicit exception overrides: honest
            qplan = quota_by_g[g].get(d)
            if qplan is None:
                errors.append(f"pattern disonesto {g} {p['validoDa']}..{p['validoA']} "
                              f"{p['giorno']}: {d} e' giorno scolastico senza quota e "
                              f"senza eccezione (periodo allargato su un no-session)")
            elif qplan != plan:
                errors.append(f"pattern disonesto {g} {p['validoDa']}..{p['validoA']} "
                              f"{p['giorno']}: {d} ha piano {qplan}, il pattern dichiara "
                              f"{plan} senza eccezione")

    # --- segmentation not widened: recurring AND tight to the data -------------
    # Two distinct invariants, both named in the honesty contract:
    #  (a) a pattern is recurring (>= 2 same-weekday dates it actually covers); a
    #      single date is an exception, never a one-date "pattern".
    #  (b) the period [validoDa, validoA] is TIGHT: it starts at the first and ends
    #      at the last same-weekday quota date carrying the plan. Extending a period
    #      over no-session tail dates is invisible to byte-identity and to the
    #      honesty loop above (those dates are already excepted, so they emit nothing
    #      and are skipped), so without this explicit tightness check a widened
    #      period passes silently. This is exactly the "segmentazione mai allargata
    #      per far tornare i conti" invariant maintainer made his own for this tappa.
    for p in patterns:
        g = p["classe"]
        wd = P.WEEKDAY_CODES.index(p["giorno"])
        da = datetime.date.fromisoformat(p["validoDa"])
        a = datetime.date.fromisoformat(p["validoA"])
        plan = (p["oreAula"], p["oreLab"], p["oreStage"])
        p_dates = sorted(d for d in quota_by_g[g]
                         if d.weekday() == wd and da <= d <= a and quota_by_g[g][d] == plan)
        if len(p_dates) < 2:
            errors.append(f"pattern non ricorrente (copre {len(p_dates)} data/e del piano, "
                          f"deve essere eccezione): {g} {p['validoDa']}..{p['validoA']} {p['giorno']}")
        elif da != p_dates[0] or a != p_dates[-1]:
            errors.append(f"periodo allargato (boundaries non tight) {g} {p['giorno']}: "
                          f"dichiara {p['validoDa']}..{p['validoA']} ma le date reali del piano "
                          f"sono {p_dates[0].isoformat()}..{p_dates[-1].isoformat()}")

    # --- off-grid declared, not hidden ---------------------------------------
    offgrid_truth = {(r["group"], r["date"]) for r in quota_all if r["date"] not in school_days}
    offgrid_seen = {(r["group"], r["date"]) for r in off_grid}
    if offgrid_truth != offgrid_seen:
        for k in sorted(offgrid_truth - offgrid_seen):
            errors.append(f"riga off-grid non dichiarata (oreFuoriGriglia): {k}")
        for k in sorted(offgrid_seen - offgrid_truth):
            errors.append(f"dichiarata off-grid ma e' in-grid: {k}")
    # Req 6 (Tappa 6): off-grid hours may be ANY modality (stage is the norm; a
    # declared non-stage like G14 Sat aula Q28 is legitimate). The stage-only check
    # was pre-TASK-462 (all off-grid was stage then); G14 split-modality broke it.
    # The req-6 invariant DECLARED-not-hidden is already enforced by the set-equality
    # above (a phantom is caught there) + the Sat/closure check below; a motivo/reason
    # for non-stage off-grid is a TEMPLATE concern (a future motivo validator, not yet
    # wired - tracked in task445-tappa6-handoff), not this structural gate.
    for r in off_grid:
        if r["date"] not in closed and r["date"].weekday() != 5:
            errors.append(f"riga off-grid non su sabato/chiusura (inatteso): {r}")

    # --- row-level invariants (V6 / V11 / V17) --------------------------------
    for e in exceptions:
        d = datetime.date.fromisoformat(e["data"])
        if d in closed:
            errors.append(f"eccezione su data di chiusura (V6): {e['classe']} {e['data']}")
        if not (e["motivo"] or "").strip():
            errors.append(f"eccezione senza motivo (V17): {e['classe']} {e['data']}")
    dup14 = [k for k, c in Counter((e["classe"], e["data"]) for e in exceptions).items() if c > 1]
    for k in dup14:
        errors.append(f"chiave duplicata in 14 (V11): {k}")
    dup13 = [k for k, c in Counter((p["classe"], p["validoDa"], p["validoA"], p["giorno"])
                                   for p in patterns).items() if c > 1]
    for k in dup13:
        errors.append(f"chiave duplicata in 13 (V11): {k}")
    return errors, len(set(quota_pos) - set(exp_pos))


def main() -> int:
    patterns, exceptions, off_grid, metrics = P.compress()
    errors, n_residui = verify(patterns, exceptions, off_grid)

    if errors:
        print(f"FAIL: piano-giorni round-trip NON superato ({len(errors)} problemi, "
              f"{n_residui} residui non spiegati)")
        for e in errors[:80]:
            print("  - " + e)
        if len(errors) > 80:
            print(f"  ... e altre {len(errors) - 80} problemi")
        return 1

    # --- success: print the metrics + declare heavy groups --------------------
    n_pat = len(patterns)
    n_exc = len(exceptions)
    n_off = len(off_grid)
    # non-stage = any off-grid row with aula/lab hours (req-6-relevant: the G14 Sat aula
    # kind). Pure-stage rows are the complement; a hypothetical mixed row counts as non-stage.
    n_off_nonstage = sum(1 for r in off_grid if r["aula"] > 0 or r["lab"] > 0)
    n_off_stage = n_off - n_off_nonstage
    school_days = set(S.school_year_dates())  # build once (matches verify():57), not per row
    n_ingrid = sum(1 for r in S.daily_activity_quota() if r["date"] in school_days)
    nonstage_tag = f", {n_off_nonstage} non-stage dichiarate (req 6)" if n_off_nonstage else ""
    print(f"OK: piano-giorni round-trip equivalente. {n_ingrid} date in-grid "
          f"spiegate da {n_pat} pattern (13) + {n_exc} eccezioni (14); "
          f"0 residui non spiegati; {n_off} righe off-grid dichiarate oreFuoriGriglia "
          f"(sabati+chiusure: {n_off_stage} stage{nonstage_tag}).")
    print("  per-gruppo (pattern / deviazioni / no-session / date-piano):")
    heavy = set(P.heavy_groups(metrics))
    for g in sorted(metrics["groups"]):
        m = metrics["groups"][g]
        flag = "  <- eccezionioso (DICHIARATO in 99)" if g in heavy else ""
        print(f"    {g:9s} {m['patterns']:3d} / {m['deviation_exc']:2d} / "
              f"{m['nosession_exc']:2d} / {m['quota_dates']:3d}{flag}")
    if heavy:
        print(f"  gruppi eccezioniosi (fatto da dichiarare, non da nascondere): {sorted(heavy)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
