---
id: TASK-353
title: >-
  Reconciliation endpoint + dashboard: assigned vs scheduled hours (replicates
  the Classi sheet)
status: Done
assignee: []
created_date: '2026-07-16 09:37'
updated_date: '2026-07-17 12:46'
labels:
  - claude-planned
  - spalmone-2027
  - coherence
milestone: m-9
dependencies:
  - TASK-343
references:
  - src/main/java/com/school/planner/service/StudentGroupCompletionService.java
priority: high
ordinal: 177000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: the school's own Excel has a reconciliation sheet (Classi): per (teacher, subject, class) it compares assigned hours (Ore Inc.) vs scheduled hours in the timetable (Ore Prg.) with a Delta and A posto / Analizzare flags. This is the continuous-coherence view the user wants: any number that moves on one side but not the other must surface immediately. Today the data exists (Course.totalHours, lessons) but no view assembles it - the /api/completion payload is close but group-level only.

WHAT:
1. Backend: new aggregation endpoint (extend StudentGroupCompletionService or new ReconciliationResource) returning per (teacher, subject, group): assignedHours, scheduledHours, pinnedHours, delta, status (OK / MISMATCH / UNSCHEDULED). Include per-teacher and per-group rollups.
2. Frontend: new Reconciliation page rendering the matrix with red/amber/green status cells, filterable by teacher/group/subject, with drill-down listing the lessons behind a cell. Reuse FilterBar and admin table styles; a plain sortable table is fine for v1 - the fancy grid arrives with the m-10 grid editor.
3. Wire a visible badge into the main header when any MISMATCH exists (count), linking to the page.
4. REST Assured test on the endpoint with a small fixture.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Endpoint returns correct aggregation on a test fixture incl. delta and status
- [x] #2 UI page shows the matrix with status colors and drill-down; badge appears when mismatches exist
- [ ] #3 Verified against the golden profile: statuses match the Excel Classi sheet expectations for at least 5 spot-checked rows
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + code-review high (8 candidates, fixes G1-G8 incl. the critical session-frozen cache). GET /api/reconciliation (rows + rollups with issueCount + summary, pseudo-flagged) + per-course drill-down; ReconciliationPage with grouped subtotals, TTL-cached badge (only real issues count), Expected/Check chips for pseudo rows, refresh, a11y. AC3 (golden spot-check on the live app: G08 Stage 300/276 Expected, Esame 24/0, RA 40/0, T13 10/0 UNSCHEDULED real, Taglio Metalli 60/20 MISMATCH real) deferred to the next live cycle — spot-check rows documented in the task notes. Tests 486/486.

TASK-371 correction (17/07, Q3): 2 of the 5 AC3 spot-check rows above are stale post-correction. T13/Orientamento/G12 is no longer a course at all (the incarichi row was dropped - golden grid schedules 0h for it, confirmed a duplicate/never-scheduled entry, not an UNSCHEDULED row to display). Taglio Metalli/G04/T03 now reports OK, not MISMATCH: incarichi corrected 60h -> 20h to match golden (60/20 MISMATCH -> 20/20 OK). The remaining 3 spot-check rows (G08 Stage 300/276 Expected, Esame 24/0 Expected, RA 40/0 Expected) are unaffected by TASK-371 and still stand.
<!-- SECTION:FINAL_SUMMARY:END -->
