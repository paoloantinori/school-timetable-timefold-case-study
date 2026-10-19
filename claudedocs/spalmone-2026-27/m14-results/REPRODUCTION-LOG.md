# TASK-539.01 reproduction log (2026-09-09, Fedora + laptop)

Every verification step with the command and the ACTUAL exit code / result.
Fedora HEAD = laptop HEAD = 5b406056 throughout. laptop work via ssh (podman/tmux at
/opt/homebrew/bin). No solve cycle was launched for this task.

## A. Provenance and checksums

1. `git pull --ff-only --no-rebase` (Fedora) -> 5b406056, exit 0.
2. Winner identification: `grep "attempt .* done\|best attempt" /tmp/planner-sh9-final.log`
   -> attempt 1 deterministic = winner ("best attempt 1 shipped with CH-only rooms
   ... refining its rooms before delivery", 13:19:09).
3. Relaunch flags: `grep -c SlotRetry /tmp/planner-sh9-final.log` -> 0 (slot-retry
   NOT armed); flags banner 05:01:05 `surplus-harvest=true`, `grasp=false seed=0`.
4. `shasum -a 256` over the capture set: 20 files committed at 5b406056 plus the
   laptop-side export.json.gz (sha-capture.txt, committed in this dir) and 7
   attempt-1 checkpoints (sha-ckpt-attempt1.txt, committed in this dir).
5. Boundary-vs-export reconciliation (python over
   staged-checkpoints/attempt1/POST_FINAL_SURPLUS_HARVEST.json vs export.json.gz):
   placements 12510 both; timeslot agreement 12510/12510; room agreement
   3889/12510; unassigned 1 both; pinned 276 both; checkpoint score
   [-7415/0]hard full (CH-only rooms), timeslotTierHard -13.

## B. Residual identity (AC#5)

6. `python3 recount-residual.py` (Fedora, this dir) -> EXIT 0:
   H34 3 cells (G06 2027-06-10 q4; G09 2027-06-03 q5; G12 2027-04-16 q5),
   H33 3 cells deficit 3 (G14 2027-01-29/02-05/03-05, q3 placed 2 floor 3),
   H32 1 cell deficit 1 (G13 2026-11-06 q5 placed 4). Provider analysis.json
   agrees family-by-family (H28 4 / H33 3 / H34 3 / H0 1 / H32 1 = -13).
   Inputs: committed lessons.txt + quota rows and timeslot dates dumped from the
   live slotretry DB (quotas.csv 2257 rows, tsdates.csv 174 dates, both staged).
7. H28 decomposition: constraint source read (teacherNoReturnWithinGroupSubjectDay
   penalizes ordered same-teacher PAIRS with a different-teacher same-subject
   lesson strictly between); lessons.txt confirms T03 8,9 / T22 10,11,12
   / T03 13 on 2026-09-24 AND 2026-10-01 -> pairs (8,13),(9,13) x 2 days = 4.
8. H0 identity: db-query on live slotretry DB -> lesson 8244 Confezione T16
   G14 unassigned (the only one).
9. Same method on v132-7sep lessons.txt -> H34 3 (G06 10/06, G09 03/06,
   G12 2027-06-04), H33 2 (G14 03-12, 04-30), H32 0: matches doc-001's
   documented v132 decomposition (H34 -3, H33 -2, H29 -1).

## C. Replay (AC#3)

10. Import-path attempts on fresh profile m14-replay (laptop):
    - `./scripts/api/import-schedule.sh /tmp/m14/export.json` -> `curl: option -d:
      out of memory` (1.2GB body cannot be posted; script reports success line but
      curl failed; recorded as finding F1).
    - streaming probe (`curl -T` variant in /tmp/m14/try-import.sh) -> HTTP 413:
      `quarkus.http.limits.max-body-size=50M` (application.properties:43) rejects
      full-size exports at the HTTP layer (finding F1, structural).
11. DB-volume replay (the delivered state persisted in planner-data-slotretry-9sep):
    rebuilt jar at current HEAD (`./scripts/core/rebuild.sh`, BUILD_EXIT=0,
    quarkus-run.jar 16:19), `PROFILE=slotretry-9sep ./scripts/core/prod-start-and-wait.sh
    300` -> START_EXIT=0, then `./scripts/api/api.sh analysis` ->
    `[-13/0]hard/[-787915/-16017]soft` with H28 4 / H33 3 / H34 3 / H0 1 / H32 1:
    IDENTICAL to the delivery capture's analysis.json on every family and both
    hard coordinates (replay-analysis.json staged here).
12. Inventory on the reloaded DB (replay-inventory.csv): 13 groups fully placed,
    G14 114/115 placed, pinned 276 (matches checkpoint and export).

## D. Pre-construction fixtures at current HEAD (AC#4)

13. Fixture m14-precon (DOCUMENTED flow, with calendario): fresh profile ->
    `./scripts/api/import-calendario.sh -t 300` -> CAL_EXIT=0 (295 lab pins,
    2009 stage pins, 5 no-budget skips) -> `api.sh pre-assign` + wait -> COMPLETE ->
    pinned 3077 (1068 real-teacher + 2009 Stage). Banners verified in
    /tmp/planner-prod.log: `Pool-cap fill (TASK-577, RATIFIED 9/09): enabled=true`
    (16:26:08, first current-HEAD sighting of the ratified banner), flags line
    default (surplus-harvest=false: no flags passed), DayFloor table published.
14. PROTOCOL DISCOVERY: the measured v-series runs did NOT import calendario.
    slotretry first launch loaded "0 pinned Lessons" at pre-assign (04:55:57);
    relaunch loaded 274; CYCLE_START shows placed=pinned=276 (2 extra = lessons
    7152, 7159, Servizio tecnico e stilistico, T01, G06, pinned by the
    killed run's pre-assign). v132 all 5 attempts: CYCLE_START placed=pinned=274.
    Finding F2 recorded; canonical-protocol decision belongs to TASK-596.
15. Fixture m14-precon-b (MEASURED protocol, no calendario): fresh profile ->
    pre-assign -> COMPLETE -> pinned 274. Pin-set comparison (compare-pins.py,
    committed in this dir):
    fixture-B sha 55ed4ae0a7a9ed5a == v132 attempt-1 CYCLE_START sha (symdiff 0);
    slotretry differs by exactly {7152, 7159}. The pre-assignment is deterministic
    and reproduces the v-series start state exactly.
16. Banners for fixture B verified (16:32:42 Pool-cap fill enabled=true; flags
    line default).

## Findings fed to other tasks (all recorded same turn)

- F1: canonical export cannot round-trip via POST (50M body cap + curl -d memory
  limit at 1.2GB). Restore paths that DO work: DB volume reload (verified, step
  11) and path-based checkpoint apply (solve-staged-resume?path=). -> TASK-579.01.
- F2: the measured series runs WITHOUT import-calendario (274-pin start), the
  documented flow includes it (3077-pin start). Fixture B matches the measured
  protocol; m14-precon kept as the documented-flow variant. -> TASK-596 (which
  protocol is the school's), noted in manifest.
- F3: `constraint-violations.sh` calls the removed `api.sh raw` -> "API error" for
  every constraint. Worked around via `api.sh get`. -> TASK-579.01.
- F4: VERDICT-raw narrative errors on slotretry-9sep (three items). Detail and
  verification live in RESIDUAL-LEDGER-2026-09-09.md, section "Corrections to
  retained narratives" (that section is authoritative). TASK-579.01 evidence family.

## laptop end state

App stopped; volumes kept: planner-data-slotretry-9sep (delivery),
planner-data-m14-precon (documented-flow fixture),
planner-data-m14-precon-b (measured-protocol fixture, the canonical one for
TASK-590). planner-data-m14-replay removed. /tmp/m14/ on laptop holds raw dumps.

## Post-commit adversarial verification (2026-09-09 evening, same HEAD)

Re-checked the load-bearing claims with independent commands:
- analyzeScore() read at source: loadSchedule() + solutionManager.update() +
  fresh score director with ConstraintMatchPolicy.ENABLED -> the replay is a
  genuine recompute, not an echo of a persisted score.
- v132 profile mounted at current HEAD: quota rows = 2257 (the manifest claim,
  previously inferred, now measured; identical to slotretry); provider recount
  from the volume = [-6/0]hard with H34 3 / H33 2 / H29 1 and NO H32/H28/H0
  (third source confirming the v132 decomposition; my Python recount and
  doc-001 both agree).
- NEW FINDING from that mount: the v132 DB volume has pinned=0 while its export
  carries 274 pinned (placements intact) -> volume drifted post-capture; the
  v132 fixture is the export + checkpoints, not the volume. Recorded in the
  manifest fixture-B caveat.
- H28 drill-down artifact re-read: the two 13:00 lessons carry 2 entries each,
  the four morning lessons 1 each (8 total = 2 x 4 provider matches) -> the
  pair decomposition is in the data, not just the prose.
- Winner line re-grepped verbatim ("best attempt 1 shipped with CH-only
  rooms", 13:19:09; GRASP starts at attempt 2, 06:33).
- Input hash re-computed: incarichi @8a2c5287 = 3caf502c4befc3a4 (manifest row
  correct).
- Fixture-B pin set re-confirmed: 274, sha 55ed4ae0a7a9ed5a, agreeing with
  v132 CYCLE_START checkpoints AND the v132 export (three sources; the drifted
  volume is the only outlier).
- Commits inspected: 54060ed9 (12 files, backlog) and a853a952 (12 files,
  evidence) contain exactly the intended content.
