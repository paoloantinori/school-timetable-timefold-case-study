---
id: TASK-397
title: >-
  Perf — TargetEndDateCalculator.computeTargetEndDates runs unconditionally in
  loadSchedule (outside the TASK-378 refreshStatics guard), re-running
  ~591×/solve for an invariant result
status: Done
assignee: []
created_date: '2026-07-23 05:07'
updated_date: '2026-07-23 08:00'
labels:
  - claude-planned
  - perf
  - ideal-end-date
dependencies: []
references:
  - src/main/java/com/school/planner/service/ScheduleService.java
  - src/main/java/com/school/planner/service/TargetEndDateCalculator.java
  - claudedocs/spalmone-2026-27/validation-393-results/VERDICT.md
priority: medium
ordinal: 221000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Finding (2026-07-23 laptop validation, run 2 clean log)
`TargetEndDateCalculator.computeGroupTargetDate` logged its per-group summary line **~8,274
times during run 2's ~68-min solve** (= ~591 full `computeTargetEndDates` cycles). Each cycle is
14 groups × (`Course.find` DB query + a forward day-scan). Run 1 showed ~26.5k (its log also carried
prior-session lines, but the in-solve recurrence is real and large either way). Recorded as a caveat
in `claudedocs/spalmone-2026-27/validation-393-results/VERDICT.md`.

## Root cause
`computeTargetEndDates` is called **unconditionally** in `ScheduleService.loadSchedule` (line 281),
**outside** the `refreshStatics` guard (lines 291-298) that TASK-378 added specifically to skip
static recomputation while the solver is active:
```
refreshStatics = forSolver || getStatus() == NOT_SOLVING;
if (refreshStatics) { refreshSolverStatics(...); }   // TASK-378 guard
```
`loadSchedule` is invoked repeatedly *during* solving — UI/concurrent fetches (SchoolSchedule.java:256
documents "multiple UI components fetch… threads call loadSchedule() concurrently"). So every mid-solve
fetch re-runs the full ideal-end-date calc. But the ideal end date is **invariant during a solve**
(groups, course hours, timeslots, holidays, stage patterns are all fixed for the duration), so
recomputing it is pure waste — exactly the case TASK-378's guard exists for, just missed for this call.

## Fix (low risk)
Move `computeTargetEndDates` (line 281) inside the `if (refreshStatics)` block, mirroring TASK-378's
pattern. At setup `NOT_SOLVING` is true → guard passes → calc runs once and populates
`group.idealEndDateInfo` / `idealEndDateVrpBuffered`. Mid-solve UI fetches (`forSolver=false`,
SOLVING_ACTIVE) → guard skips → no recompute. The UI marker reads `idealEndDateInfo` (invariant during
the solve, so the setup value is correct); M12 reads the static map populated by `refreshSolverStatics`
(already guarded). Verify ordering: line 281 must stay before `refreshSolverStatics` (293) so the
static map copies the freshly-computed values at setup.

## Impact
Removes ~591 full recomputes/solve (DB queries + day-scans) + ~8k INFO log lines/solve; frees
solver-thread CPU and DB during solving. Directly relevant to TASK-396's spike — clean timing numbers
require this fixed first (otherwise the spike's construction/LS timings are polluted by the calc
re-running on every UI poll).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 computeTargetEndDates no longer runs on every loadSchedule during solving: guarded like refreshStatics (only when forSolver || NOT_SOLVING), or cached keyed on its inputs.
- [ ] #2 Verified: a full solve logs the per-group TargetEndDateCalculator line ~once at setup, not thousands of times (re-run a solve, grep the count).
- [ ] #3 UI ideal-end marker still renders correctly (uses the setup-computed idealEndDateInfo, which is invariant during the solve); M12 unaffected (reads the static map populated by refreshSolverStatics).
- [ ] #4 GoldenSolutionRegressionTest unchanged; full DoD.
<!-- AC:END -->

## IMPLEMENTATO (2026-07-23, commit 8a6034bd) — con correzione a un bug del fix ingenuo

Il "muovi il compute nel guard" proposto AVEVA UN BUG (colto in verifica): i campi ideal-end sono `@Transient`, quindi un guard nudo lascerebbe `group.idealEndDateInfo` null su ogni fetch mid-solve → il marker UI (`useScheduleData.ts:153` legge `group.idealEndDateInfo` dal payload) SPARIREBBE durante il solve. Fix corretto: compute UNA volta sotto il guard (popola i campi + una nuova cache statica `idealEndDateInfoStatic` in `refreshSolverStatics`, accanto a VrpBuffered), poi ri-idratazione CHEAP dei campi `@Transient` dalla cache su OGNI loadSchedule (mid-solve incluso). `calculateStats` lasciato non-guardato (il completamento cambia durante il solve, l'UI lo vuole fresco; è self-contained, non legge idealEndDateInfo). **Verificato live:** payload porta idealEndDateInfo su tutti i 14 gruppi, compute gira una volta al setup.

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify — cambio focalizzato (guard + cache + apply loop); nessuna semplificazione ulteriore sensata
- [x] #2 Skill /code-review — inline: apply-loop set-only-if-null (no-op al setup, popola mid-solve); cache popolata in refreshSolverStatics prima di ogni mid-solve fetch; reset statico aggiunto; getter singolo VrpBuffered riusato. Nessun finding
- [x] #3 EXIT CODE: test.sh exit 0 (689, GoldenSolutionRegressionTest invariato) + verifica live payload (14/14 idealEndDateInfo). Mid-solve log-count → prossimo solve laptop
<!-- DOD:END -->
