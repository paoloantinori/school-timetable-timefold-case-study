# m-14 control manifest: fixtures, provenance, checksums (TASK-539.01, 2026-09-09)

Authoritative provenance record for the two evidence fixtures of milestone m-14.
Everything here was verified mechanically on 2026-09-09 (Fedora HEAD 5b406056,
laptop same HEAD); commands and exit codes are in REPRODUCTION-LOG.md in this dir.
Large artifacts (export.json.gz, checkpoints) stay laptop-side, referenced by sha256.

## Fixture A (near-delivery control): slotretry-9sep

The current delivered residual. Fresh full staged cycle, delivery 2026-09-09 13:29,
capture battery committed at git 5b406056 (dir `task577-results/slotretry-9sep/`).

| Field | Value |
|---|---|
| Source commit | 8a2c5287 (SlotRetryPass, 2026-09-09 04:52:48) |
| Jar build | 04:55:24 9/09, PREFLIGHT-OK classes=[SurplusHarvest] flags-verified |
| Launch | first launch 04:56 WITH slot-retry, killed ~04:59-05:00 (stop file /tmp/stop-slotretry.txt 04:59, killed log's last line 05:00:34; the VERDICT's "05:05" is unsupported); clean relaunch, live log starts 05:01:04 |
| Live flags (verified in /tmp/planner-sh9-final.log) | surplus-harvest=true, checkpoint capture on, SURPLUS_TRACE_ALL=1; slot-retry ABSENT (0 banner matches); attempt-1 grasp=false seed=0 |
| Flags NOT in this code yet (pre-dated) | pool-cap-fill (ratified default ON only at 567ffff0 11:06), pair-major (f51c4a15), golden-monthly present but inert at default (removed 1e994d83) |
| Input hashes @8a2c5287 | incarichi 3caf502c4befc3a4, class_days 15b90c0647000e0f, fc 20e17247ad9e3109, golden lessons 7c9c378dde159578 (all sha256, first 16 hex) |
| DB fingerprint (live profile) | 27 teachers, 14 groups, 274 courses, 1392 timeslots, 2257 quota rows, 12510 lessons, 276 pinned |
| Profile | slotretry-9sep (created 9/09, NOT recreated between killed run and relaunch; multistart-ab doctrine) |
| Attempts | 1 deterministic = WINNER (boundary timeslot-tier -13, DOOMED room-LS skip per TASK-498); 2 GRASP seed1 (boundary -55); 3 discarded repeatedly by profile predictor (construction H17 holes > 150, reseeds up to seed 10+); 4 done 11:25; 5 done 13:19. "no 0-hard attempt ran" |
| Delivery refinement | 13:19-13:29: restored attempt 1, freed 2822 non-authoritative rooms, CH rebuild + room LS. Timeslots UNCHANGED (12510/12510 agreement checkpoint-vs-export); rooms changed on 8621/12510 |
| Delivery score | [-13/0]hard/[-1245173/-16017]soft (score.txt); analysis medium recount -787915 (known score-vs-analysis medium divergence family, TASK-509; HARD concordant) |
| Concordance | H29 room-null memory=0 db=0, 14th consecutive |

Residual decomposition (provider analysis.json, independently recounted, see
RESIDUAL-LEDGER-2026-09-09.md): H34 -3, H33 -3, H0 -2 (1 lesson), H32 -1, H28 -4.

### Checkpoints (laptop `staged-checkpoints/attempt1/`, winner)

Selection rule: file mtime >= 2026-09-09 05:01 (the live log's first line;
the killed run's last write was 05:00:34, so this closes the overlap window).
The tree is SHARED with the boxdef-8sep cycle of Sep 8, which wrote the same
attempt<N>/<BOUNDARY>.json names; dir mtimes are stale. Attempt-1 files are
cleanly timestamped 05:01-06:33 with full boundary coverage. August iter files
coexist under different names (POST_PROMOTION_iter4 etc.), excluded by rule.

Key sha256 (full list committed as sha-ckpt-attempt1.txt in this dir):
CYCLE_START e6e37607, POST_CONSTRUCTION 726f3b96, POST_PROMOTION f30e93b7,
POST_RELEASE 6fc9c360, POST_B_ROOM c4d52910, POST_MEDIUM_PURE_ARM d02ce35d,
POST_FINAL_SURPLUS_HARVEST c24fc8c5 (all truncated to 8 hex here).

Boundary reconciliation (POST_FINAL_SURPLUS_HARVEST vs export.json.gz):
score [-7415/0] full (CH-only rooms noise), `timeslotTierHard -13`,
`boxed 1`; placements 12510; timeslot agreement with export 12510/12510;
room agreement 3889/12510; unassigned 1 in both; pinned 276 in both.
The delivered hard vector -13 was already fixed at attempt 1's final boundary.

### Capture checksums (committed battery + laptop-side export)

sha-capture.txt in this dir holds 21 sha256 lines: the 20 files committed at
5b406056 plus export.json.gz (111,067,389 bytes, laptop-side, untracked), whose
sha256 is 454ff42ae68e4195e30256be94d1a7301e908744286961873119a889a145e9b8.

## Fixture B (second, materially different): v132-7sep

Best delivery of the series, materially different defect mix (has H29, no H28,
different H34 third cell, different G14 floor dates).

| Field | Value |
|---|---|
| Source commit | 89e505d1 (authored 2026-09-07 12:42:23; the VERDICT-raw's "6/09" is a date slip), jar 12:43:33 on 7/09, PREFLIGHT-OK, surplus-harvest + SURPLUS_TRACE_ALL, deterministic construction |
| Input hashes @89e505d1 | incarichi 3caf502c (SAME), class_days ea160ab6, fc d153b5dd, golden lessons 7c9c378d (SAME) |
| Input delta A-vs-B | 13 quota rows deleted between 89e505d1 and 8a2c5287. Full enumeration: class_days 8 rows ALL past the 2027-06-15 timeslot horizon (G09 06-16, G10 06-16+17, G11 06-16+17, G12 06-22/23/24) + 5 G13 fc rows on 06-11..15, three of which are IN-grid dates. The G13 rows are immaterial by the SEEDING GATE, not the horizon: DataGenerator.PRE_EXTENSION_HORIZON = 2027-06-10 (DataGenerator.java:172, skip at :157) seeds past-horizon fc rows ONLY for the 1/G08 golden mirror, so those G13 rows were never seeded at EITHER commit. Verified: both fixture DBs carry the identical 2257-row quota set |
| Cycle | 12:44-19:37 on 2026-09-07 (~7h), delivery [-6/0]hard |
| Residual | H34 -3 (G06 2027-06-10, G09 2027-06-03, G12 2027-06-04), H33 -2 (G14 2027-03-12, 2027-04-30), H29 -1; no H32, no H28, no H0 |
| Artifacts | full battery + export.json.gz laptop-side (untracked); checkpoints in DEDICATED dir `staged-checkpoints-v132` (no contamination) |

CAVEAT (found in 2026-09-09 post-commit verification): the v132 DB volume
(planner-data-v132-7sep) has DRIFTED from its own export: 12510 placements
intact (provider recount at current HEAD still gives [-6/0], H34 3/H33 2/H29 1)
but pinned = 0, while the export taken at capture carries 274 pinned. Something
cleared the pin flags after the capture. The RESTORABLE v132 fixture is the
export + checkpoints, NOT the live volume; TASK-593 witnesses must replay from
those. The slotretry volume shows no such drift (276 pinned verified across
checkpoint, export and DB).

## What the fixtures say together (for TASK-593)

- G06 2027-06-10 and G09 2027-06-03 H34 cells are PERSISTENT across both
  basins and both input micro-versions; the third H34 cell and the G14 floor
  dates MOVE with the basin.
- The G14 Confezione lesson (id 8244, T16) is the H0 unit in fixture A
  but was PLACED in fixture B on the same input: placement is basin-dependent,
  NOT a global impossibility (existence witness = v132).
- H28 A..B..A is new to fixture A's basin (zero in v132).

## Disqualified candidates (recorded for reuse)

- boxdef-8sep (-14): score+analysis only, NO export -> not restorable.
- task584 runs: score-only (doc-001 evidence item 2).
- ciclo-v10 (-16) / ciclo-sh-on (-13): complete with export; kept as tertiary
  basins if a materially worse basin is wanted later.

## Pre-construction fixtures at current HEAD (TASK-590's substrate)

Replay verification (AC#3) PASSED on the DB-volume path: jar rebuilt at 5b406056
(16:19), slotretry profile reloaded, `api.sh analysis` reproduces `[-13/0]hard`
with identical per-family counts (H28 4 / H33 3 / H34 3 / H0 1 / H32 1) and the
same medium (-787915). The JSON-import path is structurally impossible today
(50M body cap; finding F1 in REPRODUCTION-LOG.md), so the restorable forms are:
the DB volume (verified) and the 1MB boundary checkpoints (tied to the export by
the 12510/12510 placement equality).

Two fixtures built (laptop volumes, app left stopped):

| | m14-precon-b (CANONICAL for TASK-590) | m14-precon (documented-flow variant) |
|---|---|---|
| Flow | fresh profile -> pre-assign (NO import-calendario) | fresh profile -> import-calendario -> pre-assign |
| Pinned | 274, pin-set sha 55ed4ae0a7a9ed5a | 3077 (1068 real + 2009 Stage) |
| Equivalence | SET-IDENTICAL to v132 CYCLE_START (symdiff 0); slotretry differs by exactly {7152, 7159} (T01 G06, pinned by the killed first launch) | matches the DOCUMENTED flow (laptop-test/full-test skills), NOT what the v-series measured |
| Banners | Pool-cap fill enabled=true (16:32:42), default flags, DayFloor table published | same (16:26:08) |

PROTOCOL DISCOVERY: the measured v-series ran WITHOUT import-calendario while the
documented flow includes it; TASK-590 must use m14-precon-b (measured protocol).
Full evidence and the open canonical-protocol question (finding F2, owned by
TASK-596): REPRODUCTION-LOG.md steps 13-15.
