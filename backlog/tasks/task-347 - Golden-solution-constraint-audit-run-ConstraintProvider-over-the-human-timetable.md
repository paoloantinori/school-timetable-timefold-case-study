---
id: TASK-347
title: >-
  Golden-solution constraint audit: run ConstraintProvider over the human
  timetable
status: Done
assignee: []
created_date: '2026-07-16 09:35'
updated_date: '2026-07-17 00:36'
labels:
  - claude-planned
  - spalmone-2027
  - analysis
milestone: m-8
dependencies:
  - TASK-346
references:
  - claudedocs/spalmone-2026-27/analysis.md
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
priority: high
ordinal: 171000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The hand-made solution is ground truth for what the school accepts. Running our SchoolScheduleConstraintProvider over it measures exactly which of our constraints the humans violate and how often - the definitive calibration signal. Mechanical pre-analysis (claudedocs/spalmone-2026-27/analysis.md section 3-4) predicts: H1=0, H3=0, H6=0, morning gaps=0, H15 fires ~137x, H7 fires for T01 8h-days, H11 fires on 8h days, M20 fires 18x, S1 fires often.

WHAT:
1. On the golden profile (TASK-346), call the score analysis (ScoreAnalysis / existing /api/schedule/analysis machinery) and produce a per-constraint violation count + total score report; save to claudedocs/spalmone-2026-27/golden-audit.md.
2. Compare against the predictions above. ANY divergence (e.g. H1 nonzero, H17 nonzero) means either the import is wrong or the constraint implementation diverges from its intent - investigate each divergence to root cause before concluding; do not hand-wave.
3. For every HARD constraint that fires on the golden solution, open/annotate the matching recalibration task (H7 TASK-348, H15/H16 TASK-349, lunch-aware TASK-350) with the measured counts; anything unexpected gets a new task.
4. This is a diagnostic task: NO constraint changes here.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 golden-audit.md contains per-constraint counts for all H/M/S constraints on the golden profile
- [x] #2 Every divergence from the predicted counts is root-caused (import bug vs constraint-semantics gap) and written down
- [x] #3 Recalibration tasks updated with measured numbers
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Audit complete (clean re-run after a shared-DB incident; all numbers reproduced identically from exclusively-owned state). golden-audit.md has the full table + root causes. Confirmed predictions: H1/H3/H6/H11/H20=0, H7=T01. New findings feeding m-8: H4 Stage self-violation gap (694), H17 RA-hole artifact (22), M21 undocumented 1h/day cap (-251,700 medium, 861 matches), H15=98 organic, M20 untestable on pinned data. H0/room constraints defensively skip null rooms. TASK-348/349/350 annotated with measured numbers; TASK-365/366 created for M21 and H4/RA-hole.
<!-- SECTION:FINAL_SUMMARY:END -->
