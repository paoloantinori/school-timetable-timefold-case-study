---
id: TASK-319
title: Standardized experiment protocol and scorecard script
status: Done
assignee: []
created_date: '2026-07-12 17:41'
updated_date: '2026-07-14 07:43'
labels:
  - claude-planned
  - infrastructure
milestone: m-2
dependencies: []
references:
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
  - scripts/api/api.sh
  - claudedocs/insights/pre-assign-reset.md
priority: high
ordinal: 143000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Every solver experiment so far used different durations (90s / 120s / 6min), different branches and different datasets, so no two results are comparable and no decision is trustworthy. See claudedocs/research/critical-assessment-and-roadmap-20260712.md sections 2 and 5. This task builds the shared measurement harness that ALL experiment tasks in milestones m-3, m-4, m-5, m-6 depend on.

WHAT: Create scripts/benchmark/run-experiment.sh NAME CONFIG_DELTA_DESCRIPTION that executes one full standardized run and saves a machine-readable scorecard:
1. Verify the app JAR exists and the DB is running (reuse the checks in scripts/core/rebuild.sh and scripts/core/dev-start-and-wait.sh).
2. Full reset: ./scripts/api/api.sh reset (NOT pre-assign-reset, which only flips the status enum - see claudedocs/insights/pre-assign-reset.md).
3. Run pre-assignment: ./scripts/api/api.sh pre-assign, then poll ./scripts/api/api.sh pre-assign-status until COMPLETE.
4. Start solve: ./scripts/api/api.sh solve, poll ./scripts/api/api.sh status until solving stops (the solver terminates itself via solverConfig.xml termination).
5. Collect into local/experiments/NAME-TIMESTAMP.json: final score (api.sh score), per-constraint breakdown (api.sh analysis), unassigned lesson count (./scripts/db/db-query.sh with SELECT count on lesson where timeslot_id is null), avg lessons per day per student group (db-query.sh aggregate by student group and date), per-phase timings and scores (api.sh phase-progression or the /api/phase-progression/export endpoint), total wall-clock, git commit hash, and the CONFIG_DELTA_DESCRIPTION argument.
Also create scripts/benchmark/compare-experiments.py A.json B.json printing a side-by-side diff table of all scorecard fields.

HARD RULES: use ONLY the whitelisted wrapper scripts under scripts/ for API and DB access (raw HTTP and DB client commands are blocked by hooks). No background pipe chains (.claude/rules/no-background-pipes.md). DB column names are camelCase-collapsed (studentgroup_id, dayofweek).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 scripts/benchmark/run-experiment.sh exists, is executable, and a full run on the dev profile produces a JSON file in local/experiments/ containing ALL listed fields
- [x] #2 scripts/benchmark/compare-experiments.py prints a readable comparison table for two scorecard files
- [x] #3 Both scripts documented in claudedocs/scripts.md
- [x] #4 Only whitelisted wrapper scripts used for API/DB access; no background pipe chains
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented 2026-07-14 (offline-verified; live run pending):
- scripts/benchmark/run-experiment.sh (executable): health → full reset → pre-assign (poll COMPLETE, 10m cap) → solve (self-terminating, MAX_WAIT_SECONDS polling cap) → scorecard JSON to local/experiments/NAME-TIMESTAMP.json. Uses only api.sh / db-query.sh wrappers; no background pipe chains.
- scripts/benchmark/compare-experiments.py: side-by-side diff (scores closer-to-0-is-better, unassigned lower-is-better, per-group avg, top hard constraints, per-phase timings). Smoke-tested with synthetic scorecards — sign conventions correct.
- Both documented in claudedocs/scripts.md (Benchmark Scripts section).

Verified offline: bash -n + python -m py_compile pass. Key correctness details baked in: db-query.sh called with -f json + tail -1 (raw tuples-only, skips any SET chatter); timeslot grouped by CAST(startdatetime AS DATE) because date/dayOfWeek are JPA-computed, NOT persisted columns (per database-schema.md).

PENDING criterion #1: a full live run needs the app+DB up (./scripts/core/dev-start-and-wait.sh dev) plus a solver self-termination (minutes-to-hours under current solverConfig). That is effectively the first run of TASK-320 (baseline x3). Do NOT mark Done until a live run confirms the full pipeline and that api.sh analysis/phase-progression JSON shapes match the jq extraction against real data.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Delivered 2026-07-14. scripts/benchmark/run-experiment.sh + compare-experiments.py, documented in claudedocs/scripts.md. Validated end-to-end: a full dev-profile run produced a complete scorecard (local/experiments/baseline-20260714-094049.json) with final_score -115hard/-492331medium/+174794soft, 0 unassigned, 13 constraints, 6 phases, 13 groups. Three bugs found and fixed via live verification: (1) pre-assign-status needs -j for JSON, (2) analysis.score is a Timefold string to parse, (3) a bash brace-expansion bug in ${ANALYSIS:-{}} that appended a stray '}' and corrupted the JSON on every capture. Added --collect-only mode so a long server-side solve does not need a blocking poll loop. First baseline row recorded in claudedocs/research/experiment-log.md.
<!-- SECTION:FINAL_SUMMARY:END -->
