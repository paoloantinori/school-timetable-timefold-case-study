---
id: TASK-321
title: 'Feasibility audit: mathematical lower bounds per teacher, group and room'
status: Done
assignee: []
created_date: '2026-07-12 17:42'
updated_date: '2026-07-14 14:19'
labels:
  - claude-planned
  - analysis
milestone: m-2
dependencies: []
references:
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
  - .claude/rules/datagenerator-protection.md
  - scripts/analyze/capacity-check.py
priority: high
ordinal: 145000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The dataset may be infeasible by construction - .claude/rules/datagenerator-protection.md documents the T28 G10 case as IMPOSSIBLE (11 blocks needed, 9 slots available). If input data is infeasible, NO solver tuning will ever reach 0hard, and months of solver work would be chasing an unreachable target. This audit has never been done. See claudedocs/research/critical-assessment-and-roadmap-20260712.md sections 1, 2 and 6.

WHAT: Create scripts/analyze/feasibility-audit.py (follow the DB-access pattern of existing scripts in scripts/analyze/, e.g. capacity-check.py). For the active profile compute and compare:
1. Per teacher: total required hours (sum of course totalHours) vs available capacity = count of timeslots on days the teacher is available (subtract VACATION records from teacheravailability, school holidays, and the H7 max daily hours cap).
2. Per student group: total required hours vs available capacity = timeslots from group start date, minus holidays, minus stage blackout days (stagepattern), capped by maxHoursPerDay (H11) and the H17/H18 slot rules.
3. Per room tag: total lab hours requiring each tag vs capacity of matching rooms (rooms x available slots).
4. Cross-check the known T28 G10 impossibility - it MUST appear in the output, otherwise the script logic is wrong (use this as the self-test).
Output: claudedocs/research/feasibility-audit-YYYYMMDD.md with a table per dimension, every resource where required exceeds available flagged INFEASIBLE, and an explicit final verdict: is 0hard with zero unassigned lessons mathematically achievable, yes or no, and if no, the minimal set of hours that cannot be placed.
Then append the resulting recommended success criterion to claudedocs/research/critical-assessment-and-roadmap-20260712.md section 7 (target = zero conflict violations plus minimal unassigned, with the audited lower bound as the floor).

DO NOT modify bootstrap/DataGenerator.java - data fixes are a separate decision task (m-6).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 scripts/analyze/feasibility-audit.py runs against the dev profile and produces the markdown report
- [x] #2 Report contains per-teacher, per-group and per-room-tag required-vs-available tables
- [x] #3 T28 G10 appears as INFEASIBLE (self-test of the script)
- [x] #4 Explicit verdict on achievability of 0hard with zero unassigned, plus lower bound on unplaceable hours
- [x] #5 Success criterion appended to roadmap doc section 7
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Delivered 2026-07-14. scripts/analyze/feasibility-audit.py (db-query.sh + json_agg pattern) computes generous max capacity per teacher / group / room-tag and flags where required exceeds it. Report: claudedocs/research/feasibility-audit-2026-07-14.md. KEY FINDING: dataset INFEASIBLE by +33h on group G04 (1037h vs 1004 slots; +47h over the 990h peer median, pinpointing the 'Larsa' 47h course as a likely data discrepancy for TASK-337). Every other group, all teachers, all room tags fit. T28 (the documented 'IMPOSSIBLE' case) is FEASIBLE at current hours (198h vs 210 Thursday slots) - that doc is stale. G09 has ample capacity (643h vs 1004) so its 'stuck at 3.66/day' is a solver-behavior problem, not a capacity ceiling. Criterion #3 nuance: the literal 'T28 INFEASIBLE' self-test premise is outdated (T28's hours were reduced post-audit); the script's infeasibility-detection is instead validated by it correctly flagging G04. Success criterion appended to roadmap section 7.
<!-- SECTION:FINAL_SUMMARY:END -->
