# CHECKPOINT, TASK-419 train batch (HEAD bffcf412), night 2026-07-26

Measure-only. No adoption, no revert, no config change (Fedora decides on the evidence). This file survives context compaction.

## Run status
- run1 (val419-train-run1): DONE + captured. Numbers below.
- run2 (val419-train-run2): in progress (fresh dev profile, same sequence).
- VERDICT-train.md: not yet written (after run2).

## Launch discipline (both runs)
- App via `nohup java -Xmx2g -Dplanner.phase-transition-dump.enabled=true -jar target/quarkus-app/quarkus-run.jar`, log to /tmp/planner.log. The -D is on the command line, NOT MAVEN_OPTS (no fork, reaches the app JVM). Run1 app PID was 39930.
- Fresh dev profile each run: planner.sh kill-app/stop/delete dev, then podman volume create + podman run postgres:17, then db-query.sh readiness. No podman exec and no podman ps (the hook blocks them).
- Prep chain: verify-baseline seed (observe; the seed GATE is STALE post-TASK-408, expects timeslot 1376 vs 1368 actual which is correct), then import-calendario, then verify import (GATE STALE, expects 1800/280 vs 1785/295 actual which is correct), then pre-assign (a NO-OP for 2026/27, candidatesTried=0, pinned stays 2651), then solve-staged.
- Authoritative pins = 2651 (866 MODA + 1785 stage). Stage off-grid 295, beyond-budget skip 5.
- Staged wait: robust loop around `api.sh wait 5400` (retries on early-exit or timeout by re-checking stagedStage). caffeinate -i sleep 14400 session-wide.

## RUN1 RESULTS (val419-train-run1): final score -3hard / -2,281,494medium / +9,883soft
- hard: -3, which is H0=3 ONLY (the box=3 residual accepted after 2 demotion rounds). H17=0, H16b=0 (arithmetic: total hard equals H0, so every other hard constraint is 0).
- ricorrenza UNPINNED: 73.9% (7114/9632). TOTALE 75.4%. PINNED 81.2%. Watch threshold is 70, PASS.
- full-day: 75.5% (1808/2395). Tonight was 76.8 to 77.1, val408 79.9 to 80.0. The M20-progressive lever did NOT push full-day up in run1, it is 1.3pp below tonight. Watch run2 for direction.
- day-start: 95.9% (2128/2218).
- sharpness pooled: 79.8% (macro 76.9%).
- burst greater than 2x: 45.9%. gap-weeks: 1826. Watch threshold is 55, PASS.
- moda-knot: 0 (PASS).
- buchi interni: 0 days / 0 slots (HoleRepair closed them; golden has 1). Expectation 0, PASS.
- M32 residual: -123,535medium / 787 matches (tonight -124k/793, stable). Watch no explode, PASS.
- M15 matches: 11 (-19,200medium). Watch no explode, PASS.
- M20: -1,452,500medium / 470 matches. M22: -585,000/13. M18: -7,050/90.
- phase-transition dumps: 5 collected (+5 boxed). The -D flag worked (default dir target/phase-transitions; tonight's "none" was a path mismatch, fixed by the plain java -jar launch).

### RUN1 staged-cycle log signals (grep /tmp/planner.log)
- promoted: 7132 (= tonight). authoritative 2651 to 2651 intact.
- B-TIMESLOT iterations: 3 (B-TIMESLOT-1 plus 2 demotion rounds). Walls: B-TIMESLOT-1 3713s, later 831s. B-ROOM 266s. Total about 112min.
- DEVIATION: 2 demotion rounds, 4 pins demoted (all G12 / T13 / "Relazione con il cliente", M32 weight=132). Tonight had 0. box=3 residual ACCEPTED after MAX_DEMOTION_ITERATIONS=2.
- HoleRepair: `M20 day-fill cost 1452500 -> 1452500` (NEUTRAL, on-template imports lent from days that could afford it). `H17 hole slots 2 -> 0 (closed 2: 0 free import, 2 on-template import, 0 min-slide), conflicts added 0`.
- min-slide DE-ANCHORS: 0 (expected 0). move-budget WARN: none.

## Deviations to flag at TOP of VERDICT-train.md (measured, not alarming)
1. 2 demotion rounds / 4 pins demoted (tonight 0). The mechanism worked as designed (box=3 -> demote cap -> accept residual). This is the source of the extra B-TIMESLOT iterations and the longer runtime.
2. hard -3 is H0-driven (box=3), not H17-driven (tonight was H17-driven -15/-22). H17=0 (HoleRepair), H16b=0.
3. full-day 75.5% is slightly BELOW tonight 76.8 to 77.1 (the M20-progressive lever did not push toward 79-80 in run1). Awaiting run2 for direction.

## RUN2 plan (identical sequence)
kill-app, stop, delete dev, volume create, podman run postgres:17, db-query ready, java -jar -D, health, import-calendario, pre-assign, solve-staged, background robust wait, capture val419-train-run2, verify kpi-holes non-empty.

## VERDICT-train.md plan
Table mirroring tonight's VERDICT.md plus columns train-run1 and train-run2. New rows: buchi interni, HoleRepair import (free/template/min-slide plus M20 day-fill cost before to after), M15 matches, demotion rounds. Top flags: the 3 deviations above. No adoption judgment.
