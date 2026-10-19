---
id: TASK-539.01
title: Freeze a reproducible current control and residual ledger
status: Done
assignee:
  - fable-fedora
created_date: '2026-09-09 10:10'
updated_date: '2026-09-09 15:07'
labels:
  - claude-planned
  - zero-hard-recovery
  - checkpoint
milestone: m-14
dependencies: []
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
parent_task_id: TASK-539
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create the trusted evidence baseline for the zero-hard recovery campaign. TASK-539 owns the broader basin library; this subtask supplies a current pre-construction fixture and near-delivery fixtures for doc-001. Historical v132 and TASK-584 results are not current-production certification. Use existing captures first; missing artifacts or non-reproducing replay block candidate interpretation. This work does not require referente decisions and must not launch an unbudgeted full cycle.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Manifest records source/build identity, input hashes, relevant flags, pre-assignment, seed, attempt, boundary, and capture checksums.
- [x] #2 One near-delivery control and one materially different failing fixture have complete restorable artifacts and named provenance.
- [x] #3 An unchanged replay reproduces full provider hard-family counts, authoritative pins, and per-course/group placed-plus-unassigned inventory; mismatches are resolved before candidate comparisons.
- [x] #4 A production-equivalent pre-construction fixture preserves pre-assignment and published facts for construction experiments.
- [x] #5 Residual ledger names each failing group/date or lesson, first rejection, movable versus authoritative blockers, and enumeration scope.
- [x] #6 Reproduction commands and actual exit codes are documented; no stochastic rerun is represented as byte-identical replay.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Plan (drafted 2026-09-09 16:20, awaiting maintainer approval — no implementation started)

Fixture decisions (from inventory):
- **Near-delivery control = slotretry-9sep** (today's delivery, [-13/0]hard, complete battery + export on laptop + per-attempt checkpoints). Freshest protocol run; its residual IS the current delivered residual TASK-593 attacks.
- **Second/regression fixture = v132-7sep** ([-6/0], export on laptop, materially different defect mix: H29 present, H28 zero). Best-known delivery, so witness regressions checked on it are the strongest safety signal. (Alternative considered: ciclo-v10 -16; older code, further from delivery — kept as tertiary.)
- Source caveat recorded in manifest: control's source 8a2c5287 predates pool-cap-fill DEFAULT ON (567ffff0). Import-replay is construction-independent so witness work is unaffected; current-HEAD construction behavior is TASK-590's own control (step D below supplies its pre-construction fixture at current HEAD).

### A. Manifest + checksums (AC#1, #2) — Fedora+laptop, read-only
A1. Write `claudedocs/spalmone-2026-27/m14-results/MANIFEST-slotretry-9sep.md`: source commit 8a2c5287, jar 04:55:24, flags (surplus-harvest, checkpoint capture, SURPLUS_TRACE_ALL, OFF flags), input hashes (seed CSVs at 8a2c5287 + workbook-relevant sheets), profile identity, 5 attempts, delivery boundary 13:29, sha256 of export.json.gz + all committed capture files + today's staged-checkpoints files (selected by mtime > 2026-09-09 05:00, per the contamination hazard).
A2. Same manifest form for v132-7sep (source/build/flags from its dir + logs).
A3. Resolve checkpoint contamination: verify winning attempt's final boundary checkpoint against export (placement equality via script) before trusting any checkpoint.

### B. Replay verification (AC#3) — laptop, budgeted minutes (import+recount, NO solve)
B1. Fresh profile on laptop at current HEAD; import slotretry-9sep export; provider analysis recount.
B2. Compare BOTH hard coordinates + per-family counts (H28 4, H33 3, H34 3, H0 1, H32 1) + medium, authoritative pins preserved, per-course/group placed+unassigned inventory vs export. Mismatch = harness failure, stop and diagnose (doc-001 rule).
B3. Export the comparison (analysis.json diff + counts table + exit codes) into m14-results/.

### C. Pre-construction fixture at CURRENT HEAD (AC#4) — laptop, budgeted minutes (pre-assign only, NO solve)
C1. Fresh profile, import-calendario, run pre-assign to COMPLETE (production-equivalent), capture: DB export, banner-verified log (pool-cap-fill ON banner, surplus-harvest banner), statics publication.
C2. This is the substrate TASK-590's census runs on ("current pool-cap defaults" satisfied by construction).

### D. Residual ledger (AC#5) — Fedora analysis from laptop raw data
D1. Per hard unit of slotretry-9sep (13 units): identity (group/date/lesson) via constraint drill-down on the imported replay; first rejection from construction/app logs where retained (/tmp/planner-slotretry*.log, app log); classification movable vs authoritative pin; enumeration scope.
D2. Cross-reference v132 residual (-6) and sanity reds (5 groups); include the H32 VERDICT-vs-provider discordance note (579.01 evidence).
D3. Deliver as `m14-results/RESIDUAL-LEDGER-2026-09-09.md`.

### E. Documentation + gates (AC#6, DoD)
E1. Reproduction commands with ACTUAL exit codes for every verification step.
E2. Evidence-only work: /simplify + code-review exemption documented UNLESS step B/C reveal the need for scripts (if any script lands in scripts/, gates run on it).
E3. Commit Fedora side; large artifacts stay laptop-side referenced by sha256 (no 100MB commits).
E4. Acceptance criteria checked off one by one; task finalized per workflow.

Budget: NO full solve cycles. laptop compute = import/recount + pre-assign (minutes each), within existing doctrine.

Risks: (a) import-recount medium divergence vs score.txt is a KNOWN issue family (TASK-509) — only HARD concordance is a blocker, medium divergence gets recorded not chased; (b) if winning-attempt checkpoints turn out incomplete (overwritten), the boundary checkpoint verification degrades to export-only replay for that attempt — acceptable, recorded; (c) if replay mismatches, task blocks (by design) and the mismatch becomes the finding.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
[2026-09-09 16:15, fable-fedora] Inventory completed BEFORE plan approval (read-only, no code). Fixture landscape verified on laptop (ssh) + git 5b406056 pulled to Fedora:

(1) NEAR-DELIVERY CANDIDATE slotretry-9sep (delivery 13:29 today, committed 5b406056): full capture battery committed + export.json.gz 111MB ON laptop (untracked) + staged-checkpoints per attempt on laptop. Source = HEAD 8a2c5287 (SlotRetryPass commit 04:52:48), jar 04:55:24, flags: surplus-harvest=true, checkpoint capture on, SURPLUS_TRACE_ALL=1, slot-retry OFF (bounced by bench), pool-cap-fill ABSENT at that HEAD (pre-ratifica 567ffff0), golden-monthly present-but-inert (Boolean.getBoolean default false per removal commit 1e994d83). 5 attempts, no GRASP reseed verdict ambiguity flagged.

(2) Residual slotretry-9sep [-13/0]hard fully identified from analysis.json provider recount: H28 -4 (4 matches), H33 -3, H34 -3, H0 -2 (1 unassigned lesson), H32 -1. DISCORDANCE FOUND: VERDICT-raw.md and commit message claim 'H32=3 celle (-3)' but provider says H32 -1/1 match; the -13 total only reconciles with H32=-1. Recorded as TASK-579.01-relevant evidence (score-vs-analysis narrative divergence).

(3) H28 -4 is NEW vs v132 (v132 had H34 -3, H33 -2, H29 -1, H28 0): the crystallized-at-delta-zero invariant broke in this basin.

(4) v132-7sep DOES have export.json.gz on the laptop (doc-001 evidence #3 'lacks an export' applies to the Fedora copy only). Best delivery -6, different defect mix (has H29, no H28).

(5) Other candidates: ciclo-v10 -16 (export present, 6 sanity reds), ciclo-sh-on -13 (export present), boxdef-8sep -14 (NO export -> not restorable), task584 runs (score-only per doc-001).

(6) PROVENANCE HAZARD: staged-checkpoints/ on laptop is SHARED between yesterday's boxdef cycle (Sep 8 16:43-23:09, attempts 1-5) and today's slotretry cycle (Sep 9 05:01-13:29): today's cycle overwrote same-named attempt<N>/<BOUNDARY>.json files in place; dir mtimes are stale. Manifest must select by file mtime > 2026-09-09 05:00 and verify the winning attempt's final boundary checkpoint against the export (placement equality), not trust the tree as a whole.

(7) Cycle completed BEFORE monitoring read: no stale-queue action needed; commits 1e994d83/567ffff0/f51c4a15 (10:06-11:06) landed mid-cycle but after the 04:55 jar, so the cycle measured the pre-ratification quadro by construction.

[2026-09-09 18:40, fable-fedora] EXECUTION COMPLETE (plan approved by maintainer 16:20, executed same day). Deliverables in claudedocs/spalmone-2026-27/m14-results/: CONTROL-MANIFEST-2026-09-09.md (AC#1/#2), RESIDUAL-LEDGER-2026-09-09.md (AC#5), REPRODUCTION-LOG.md (AC#6), recount-residual.py + staged raw dumps (quotas/tsdates/viol-H28/replay-analysis/replay-inventory).

AC#3 REPLAY PASSED on the DB-volume path: jar rebuilt at current HEAD 5b406056, slotretry profile reloaded, provider recount IDENTICAL to the delivery capture on both hard coordinates and every family (H28 4, H33 3, H34 3, H0 1, H32 1 = [-13/0]); pinned 276 reconciled across checkpoint/export/DB; inventory staged (G14 114/115, all other groups full).

AC#4: TWO fixtures built on laptop (app left stopped): planner-data-m14-precon-b = CANONICAL (fresh + pre-assign, NO calendario, 274 pins, pin-set sha 55ed4ae0a7a9ed5a SET-IDENTICAL to v132 CYCLE_START, symdiff 0) and planner-data-m14-precon = documented-flow variant (calendario + pre-assign, 3077 pins). Banners verified: 'Pool-cap fill (TASK-577, RATIFIED 9/09): enabled=true' - first current-HEAD sighting.

PROTOCOL DISCOVERY (finding F2 -> TASK-596): the measured v-series ran WITHOUT import-calendario (slotretry first launch pre-assign loaded 0 pinned; v132 all 5 attempts start at 274 pins; slotretry started at 276 via 2 extra T01/G06 pins 7152+7159 inherited from the killed first launch). TASK-590's census must use m14-precon-b.

RESIDUAL LEDGER (13 units, all identified + provider-matched): cluster year-end H34 G06 2027-06-10 + G09 2027-06-03 (persistent across basins) + G12 2027-04-16 (moves with basin; slot 13 free, pre-booking never tries it) + H32 G13 2026-11-06 (q5 placed 4); cluster G14 H33 3 Fridays (01-29/02-05/03-05, q3 placed 2 floor 3) + H0 lesson 8244 Confezione T16 (placed in v132 = existence witness, basin-dependent); cluster H28 4 = T03 8-9/T22 10-12/T03 13 on 09-24 AND 10-01 (pairs (8,13),(9,13) x2). Corrections to VERDICT narratives recorded (H32 '3 celle' wrong, G12 date, 'niente GRASP' qualifier).

Findings F1 (canonical export cannot round-trip: 50M body cap + curl -d OOM at 1.2GB; DB-volume + checkpoint-apply ARE the working restore paths), F3 (constraint-violations.sh calls removed api.sh raw -> API error) filed to TASK-579.01 same turn. No repo code changed (evidence-only; DoD exemption documented): the script fixes belong to 579.01's scope, not this task.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Evidence baseline for m-14 delivered 2026-09-09 (single day, zero solve cycles, minutes of laptop compute). (1) CONTROL-MANIFEST: two fixtures frozen with full provenance - slotretry-9sep ([-13/0], current delivered residual, winner=attempt-1 deterministic, boundary-vs-export placement equality 12510/12510) and v132-7sep ([-6/0], materially different defect mix); input hashes, DB fingerprints, flags, committed sha256 lists. (2) REPLAY VERIFIED: reload of the delivery DB at current HEAD 5b406056 reproduces the provider analysis identically on both hard coordinates and all families; JSON-import restore is structurally impossible (50M cap, F1) so DB-volume + checkpoint-apply are the sanctioned restore paths. (3) PRE-CONSTRUCTION FIXTURES on laptop: m14-precon-b (canonical, 274 pins, set-identical to v132 start) + m14-precon (documented-flow variant, 3077 pins); protocol divergence F2 to TASK-596. (4) RESIDUAL LEDGER: all 13 hard points / 12 violation units identified, provider-matched and clustered (year-end H34/H32; G14 H33/H0 with v132 as placement existence witness; H28 T03/T22 A..B..A x2 days); four VERDICT-narrative corrections recorded (incl. v132's own 6/09-vs-7/09 date slip). GATES: /simplify (4 agents, 9 findings applied) + code-review high (6 verified findings, all fixed: checksums now committed in-dir, input-delta mechanism corrected to the PRE_EXTENSION_HORIZON seed gate with full 13-row enumeration, launch chronology corrected via /tmp mtimes, v132 dates corrected, inventory CSV re-headered and sums verified 12510/12509/276, H28 4-vs-8 reconciliation printed by the script). Final script run EXIT=0 full MATCH. Findings F1/F3/F4 to TASK-579.01, F2 to TASK-596, fixture pointer to TASK-590. Unblocked: TASK-590 and TASK-593.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Apply /simplify and high-effort code review to any code changes; document exemption for evidence-only work.
- [x] #2 Run the main-session applicable wrapper tests with exit codes for code changes and verify evidence calculations for artifact-only work.
- [x] #3 Persist replay artifacts and verify their checksums and ledger reconciliation.
<!-- DOD:END -->
