# TASK-398 A/B — box-the-residual on the FINAL solve: don't ship 398-as-is (box>0); construction VALIDATED — repair via 399/401 is the open work

**Branch:** `feature/cartesian-block-entity` @ `ea35ad83` (TASK-398: `allowsUnassigned=true` on
`Lesson.timeslot` + `boxUnplaceableLeftovers` replacing `forcePlaceLeftovers`). **Date:** 2026-07-23 (laptop).
**Procedure:** 2 live solves, fresh `dev` profile each, heap 2g, `verify-baseline` seed+import gates,
caffeinate-guarded. Baseline = the TASK-393 validation (`claudedocs/spalmone-2026-27/validation-393-results/VERDICT.md`).
**laptop measures only — no adopt/revert/commit on the laptop.** n=2 (small sample — see caveat).

## VERDICT: don't ship 398-as-is — but the construction is VALIDATED; the repair (399/401) is the open work

**On 398-as-is (the measured state): not shippable.** Both runs leave **5–6 un-taught lessons** (box > 0)
and end at **hard -6**, worse than the baseline's -1/0 (which has zero un-taught). The gate's critical
condition (box == 0) fails. So 398-as-is must not be merged/shipped alone — that part of "revert" stands.

**But this is NOT "the approach failed" — do not abandon box-the-residual.** The change *worked at the
construction tier*: ph1 ends at **-1,160** (box) vs the baseline's **-44,100** (force-place scatter) —
exactly the spike's thesis, confirmed on the real seed. The **construction is validated.** What failed
is a *separate* thing: the **box-clearing** (the generic LS can't empty the residual to 0). TASK-399/401
target exactly that. Honest read: **construction tier = good; repair tier = open work** — not "the
approach is bad."

**So the action is a workflow call, not a flat "undo it":**
- **Don't merge 398-as-is to production** (it's a measured regression — un-taught lessons).
- **Revert-now** (clean HEAD; re-introduce box-the-residual bundled with 401) — cleanest if 401 is more
  than a few days out.
- **Keep 398 on the feature branch as 401's base** — defensible because it's a feature branch (not
  production), 398 is experimental, and the discriminator leans A (109–126 placement-conflict-free
  cells per boxed lesson). Avoids churn, since the construction would be re-added anyway.
  **Run 399 first** (small, decisive): if it confirms A, keep 398 + build 401 on top; if B, or 401 is
  slow, revert-now is cleaner.

This refines an earlier flat "REVERT" framing: the box-the-residual construction is validated, not
rejected; 399/401 are the path to make the full solve (construction + clearing) reach box=0. (Fedora's
TASK-398 caveat #2 — "if LS doesn't empty the box, final is -N, worse than -1" — is confirmed by
measurement; caveat #1 — production box ~1,160, not the spike's 141 — also confirmed.)

## Per-run results

| metric | run 1 | run 2 | baseline (TASK-393) | gate |
|---|---|---|---|---|
| **final hard** | **-6** | **-6** | -1 / 0 | ≤ baseline → **FAIL** (-6 > -1) |
| **box (H0, un-taught)** | **5** | **6** | 0 | == 0 → **FAIL (critical)** |
| H17 gaps | 1 | 0 | 1 / 0 | — |
| construction (ph1) end hard | **-1,160** | **-1,160** | -44,100 | (cleaner — the change worked at construction) |
| ph2 LS budget consumed | 60 min (full) | 60 min (full) | 60 min | smaller → **FAIL** (no efficiency win) |
| full-day rate | 77.5% | 76.6% | 77.4–77.5% | ≥~74 → ✅ |
| recurrence unpinned | 35.1% | 35.1% | ~36% | ≥~35 → ✅ |
| blocks ≥2h | 2,108 | 2,092 | 2,018–2,078 | ≥~1800 → ✅ |
| G05 knot | 0 | 0 | 0 | ✅ |
| M15 (scatter check) | 19 / -33,600m | 14 / -21,600m | 13–14 | no explosion → ✅ |

Gate = "ADOPT only if ALL hold across both runs." **3 of 4 fail** (hard, **box**, LS budget); only KPIs
hold. The critical condition — **box == 0 (no un-taught lessons)** — fails in both runs.

## What the change did right (the spike's prediction held)
- Construction no longer scatters: ph1 ends at **-1,160 hard** (box) vs the baseline's **-44,100**
  (force-place conflicts). Placed lessons stay feasible; the residual is boxed (H0) instead of forced
  into conflicts. The spike's core thesis — box the residual rather than scatter — is validated at the
  construction tier.
- KPIs are intact (full-day ~77%, blocks ~2,100, knot 0, M15 no scatter).
- Box composition (run 2): G03×2, G05×2, G02×2 (T02/Storia×2, T04, T11,
  T13, T14) — concentrated in a few groups/teachers, not spread evenly.

## Why it fails the gate (the box doesn't clear)
- **[CORRECTED, Fedora 2026-07-23 — the original text here conflated the ph1 END SCORE with the box
  SIZE.]** The construction box is **52 lessons** (ph1 log: "placed 439 conflict-free leftovers,
  BOXED 52"), NOT ~1,160 — the −1,160 is phase 1's end HARD SCORE (52 H0 + ~1,108 of other hard:
  pre-LS H17 gaps, room-tier, etc.). So Fedora's caveat #1 did NOT hold — it went the other way:
  with production pins the box is 52 (0.5%), SMALLER than the pin-less spike's 141 (1.3%); the
  spike RESULTS' "pins make the box smaller" was right after all.
- LS clears most of the hard (-1,160 → -7/-8 by ph2 end), placing 46-47 of the 52 boxed, but **stalls
  at ~5–6** through all of ph2 (60 min) and ph3 (ruin-recreate) — those last lessons are stuck. ph4
  compaction closes 1 H17 gap (so the residual is a mix of box + H17, not purely box), but the box
  itself never reaches 0.
- Net: the change trades 0 un-taught / -1 hard (force-place scatter, repaired by LS) for **5–6 un-taught
  / -6 hard** (box, not cleared by LS). Strictly worse on the final outcome — but the small box size
  (52 at construction, 5-6 at final) + TASK-399's discriminator result (each stuck lesson has only
  3-7 feasible cells among 820-995 VRP-legal ones) frames this as a narrow LS coverage miss, not a
  structural failure.

## Discriminator (leans A, inconclusive — routes the follow-up)
The partial check (`scripts/analyze/box-feasibility.py`, committed) tests H1/H3/H7/H11 only: all 6
run-2 boxed lessons have **109–126 placement-conflict-free cells** — none are placement-deadlocked, so
it **leans A** (a feasible slot likely exists; LS missed it). But it does NOT include the VRP gate
(calendar/quota/availability/stage), so it's inconclusive. The clean A-vs-B test
(`Lesson.getAvailableTimeslots` + `canPlaceLesson` against the final placements) is **TASK-399** (Fedora,
small) — it routes whether to pursue hypothesis A (single-hour-targeted repair, TASK-401) or B (relax
template/recurrence rigidity, TASK-402 fallback).

## Caveats
- **n=2** — both runs agree closely (box 5/6, hard -6, construction -1160 byte-identical), so the
  failure is stable, not variance; but it's still a small sample.
- **Fixture vs production**: the A/B used the FULL production seed (pins, Stage, the real solve), so
  unlike the spike, this is the real problem — the box failure is real, not a fixture artifact.
- **laptop measures only.** The adopt/revert call (and the box-clearing follow-ups TASK-399/400/401/402)
  is Fedora's on this evidence.

## Net
**398-as-is should not ship/merge alone** (5–6 un-taught lessons, hard -6 — a measured regression vs
the -1/0 / 0-un-taught that force-place scatter already achieves). But the box-the-residual
**construction is validated** (-1,160 vs -44,100): this is not a failed approach, it's a validated
construction tier waiting on its repair tier. The open problem is **clearing the box** (TASK-399 →
401/402), not the construction itself. Fedora's call: revert-now for a clean HEAD, or keep 398 on the
feature branch as the base for 401 (run 399 first to route A vs B).
