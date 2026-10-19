# TASK-401 FINAL A/B — 398+401 (box-the-residual construction + enumeration BoxRepair): VERDICT **ADOPT**

**Branch:** `feature/cartesian-block-entity` @ `9edf3f64` (TASK-398 `allowsUnassigned`+box-the-residual
+ TASK-401 `BoxRepair` enumeration box-clearing in phase 4). **Date:** 2026-07-23 (laptop).
**Procedure:** 2 live solves, fresh `dev` profile each, heap 2g, `verify-baseline` seed+import gates,
caffeinate-guarded. Baseline = TASK-393 (`{-1,0}` hard, box 0). **laptop measures only — no adopt/revert/
config change on the laptop.** n=2.

## VERDICT: ADOPT

Both runs pass **every** gate condition. 398+401 reaches **box=0 / hard=-1** in both runs — matching the
TASK-393 baseline, with a far cleaner construction (-1,160 vs -44,100). The BoxRepair enumeration phase
clears the residual the LS leaves (boxed 4→0 / 6→0, **conflicts added 0**), so **no un-taught lessons**
survive. The human-faithful approach (box-the-residual construction + targeted repair) works end-to-end.

## Per-run results vs baseline

| metric | run 1 | run 2 | baseline (TASK-393) | gate |
|---|---|---|---|---|
| **final hard** | **-1** | **-1** | -1 / 0 | ≤ baseline (≤ -1) → **PASS** |
| **box (H0, un-taught)** | **0** | **0** | 0 | == 0 → **PASS (critical)** |
| H17 gaps | 1 | 1 | 1 / 0 | — |
| construction (ph1) end hard | **-1,160** | **-1,160** | -44,100 | cleaner (box-the-residual worked) |
| **BoxRepair (phase 4): boxed→placed** | **4 → 0** | **6 → 0** | n/a | still-boxed 0 → **PASS** |
| phase-4 conflicts added (BoxRepair + compaction) | **0** | **0** | 0 | == 0 → **PASS** |
| full-day rate | 77.7% | 77.8% | 77.4–77.5% | ≥~74 → **PASS** |
| recurrence unpinned | 35.8% | 36.7% | ~36% | ≥~35 → **PASS** |
| blocks ≥2h | 2,139 | 2,092 | 2,018–2,078 | ≥~1800 → **PASS** |
| G05 knot | 0 | 0 | 0 | **PASS** |
| M15 (scatter check) | 23 / -43,200m | 19 / -37,200m | 13–14 | no explosion (golden 98) → PASS (see caveat) |

## How the box clears (the mechanism TASK-401 added)
- Construction (ph1) boxes **52** lessons (0.5% — box-the-residual, NOT the old -44,100 scatter). Placed
  lessons stay feasible.
- ph2 (LS-Compactness) + ph3 (RuinRecreate) place most of the box, leaving a small residual (run 1: ~4,
  run 2: ~6) that the generic LS can't crack (the TASK-398 finding).
- **ph4 BoxRepair (TASK-401) enumerates the VRP-legal, conflict-free cells for each residual and places
  them — boxed 4→0 (run 1), 6→0 (run 2), conflicts added 0.** Then ph4 compaction + ph5/ph6 rooms
  finish. Final: box 0, hard -1 (1 H17 gap), zero un-taught.

## Notes / caveats
- **n=2** — both runs agree (hard -1, box 0, BoxRepair still-boxed 0, construction -1,160 byte-identical).
  Stable, but a small sample.
- **M15 crept up (13→23 / 19).** The BoxRepair places the residual feasibly but into whatever cell is
  conflict-free, which can create a few more same-day-same-subject splits. Still far under the golden's
  98 (no scatter) and M15 is medium-tier, so it doesn't gate — but it's a real trade: clearing the box
  costs a handful of extra M15 splits. Worth watching if it climbs further.
- **No solve-time win.** Both runs still burned the full ph2 (60 min) + ph3 (4–18 min); the solve is
  ~the same wall-time as baseline. The benefit is **feasibility-by-construction** (the human-faithful
  property: cleaner construction, no -44,100 scatter, box=0 with no un-taught) — not speed.
- **Construction box = 52 (0.5%)**, smaller than the pin-less spike's 141 (1.3%) — confirms the spike
  RESULTS' "pins shrink the box" (production pins occupy slots but also remove pin-able lessons from the
  unpinned set the box draws from). Fedora's earlier caveat #1 (larger box) was wrong, already corrected
  in the TASK-398 VERDICT.
- **Not tested here:** the queued mid-pipeline repair arm ("repair between ph2 and ph3", TASK-401
  follow-up) — a separate sequential A/B needing a solverConfig change not in HEAD; not run per the brief.
- **Checkpoint snapshots** (ph1-end, ph3-end exports) are NOT API-exposed (async solve, no mid-phase
  export endpoint) — skipped per the brief (no pipeline hacking). The FINAL export is captured
  (export.json.gz) for Fedora checkpoint re-import.

## Net
**ADOPT 398+401.** The human-faithful construction (box-the-residual) + the enumeration BoxRepair
together reach the TASK-393 baseline's {-1} hard with **box=0 (no un-taught lessons)** and zero repair
conflicts, with a far cleaner construction (-1,160 vs -44,100). This is the path the revised TASK-398
verdict pointed to (route through TASK-399 → 401), validated. Fedora's call on the final adopt/merge
(solo TASK-398 was reverted for box>0; 398+401 together clear the gate). Artifacts in
`val401-run{1,2}/` (score, phases, analysis, lessons, KPIs, boxrepair-log, export.json.gz).
