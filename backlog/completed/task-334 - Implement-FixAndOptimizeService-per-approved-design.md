---
id: TASK-334
title: Implement FixAndOptimizeService per approved design
status: Done
assignee: []
created_date: '2026-07-12 17:47'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - implementation
milestone: m-5
dependencies:
  - TASK-333
references:
  - src/main/java/com/school/planner/rest/PreAssignmentResource.java
  - scripts/api/api.sh
priority: high
ordinal: 158000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Implementation of the fix-and-optimize matheuristic designed in TASK-333. PREREQUISITE: the design document claudedocs/design/fix-and-optimize.md must exist AND have been reviewed/approved by the user - do not start otherwise.

WHAT:
1. Implement service/FixAndOptimizeService.java exactly per the design: outer loop, the 4 neighborhood selectors, adaptive growth, diversification, stop conditions, transactional pin manipulation with guaranteed restore (try/finally or persisted pin-snapshot).
2. REST: rest/FixAndOptimizeResource.java with POST /api/schedule/fix-and-optimize (budget parameter) and GET /api/schedule/fix-and-optimize/status. Follow the existing resource patterns (PreAssignmentResource is the closest analogue: long-running background operation with status polling).
3. Never violate the single-solver invariant: reject start if a solve or pre-assign is running.
4. Progress reporting through PhaseProgressionService (one entry per accepted neighborhood round).
5. Tests: (a) unit tests for neighborhood selection and pin snapshot/restore; (b) @QuarkusTest integration test on the H2 dataset running 2 rounds with tiny termination proving loop mechanics, acceptance logic, and pin restoration (lab-pinned lessons still pinned at the end - assert it explicitly); (c) all existing tests still pass.
6. Add an api.sh subcommand (fix-and-optimize, fix-and-optimize-status) following the existing subcommand pattern in scripts/api/api.sh, and document in claudedocs/scripts.md and claudedocs/rest-api.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Service and REST resource implemented per design; single-solver invariant enforced
- [ ] #2 Lab-pinned lessons provably still pinned after any run (asserted in integration test)
- [ ] #3 Unit + integration tests pass; full ./scripts/core/test.sh green
- [ ] #4 api.sh subcommands added and documented
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
ASSORBITO da TASK-478 ExactFinisher (ratificato DEFAULT ON 2026-08-02): l'implementazione e' solver/ExactFinisher.java (gate chain H1/H3/H14/H11/H7/H17 condivisa BoxRepair, VRP last, improve-or-noop). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
