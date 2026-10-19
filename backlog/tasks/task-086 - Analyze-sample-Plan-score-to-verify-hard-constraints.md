---
id: TASK-086
title: Analyze sample Plan score to verify hard constraints
status: Done
assignee: []
created_date: '2026-02-23 14:54'
updated_date: '2026-03-09 19:50'
labels:
  - analysis
  - constraints
  - validation
  - claude-planned
dependencies: []
priority: medium
ordinal: 56000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The school provided a sample schedule/plan. We need to validate it against our constraint system to verify that all hard constraints (H1-H16) are respected, and identify any discrepancies between our constraint model and the school's expectations.

## Input Required
- Sample plan file from the school (format TBD: likely Excel or PDF)
- Confirmation of school's constraint expectations (which rules are hard vs soft in their view)

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 #1 Receive and document the sample plan file format and structure
- [x] #2 #2 Create data mapping: school's schema → our entity model (teachers, groups, rooms, lessons, timeslots)
- [x] #3 #3 Create a test database profile with the sample data loaded
- [x] #4 #4 Run `/api/schedule/analysis` endpoint to get constraint breakdown
- [x] #5 #5 Document results in `claudedocs/sample-plan-analysis.md`:
- [x] #6 #6 Compare our constraint definitions with school's stated expectations
- [x] #7 #7 Report discrepancies: constraints we have that they don't expect, and vice versa
- [x] #8 #8 Obtain sample plan file from school (BLOCKER)
- [ ] #9 #9 Confirm school's constraint expectations

## Edge Cases
- Sample plan has incomplete data (missing rooms, teachers, timeslots)
- Sample plan uses different naming conventions
- Sample plan includes entities we don't model (e.g., break times, assemblies)
- Sample plan date range differs from our timeslot coverage

## Dependencies

## Risks
- Data mapping may be time-consuming if schemas differ significantly
- School may not have documented their constraint expectations clearly

## Definition of Done
- Analysis document created and reviewed
- All hard constraint violations documented with examples
- Discrepancy report ready for discussion with school
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Definition of Done
- Analysis document created and reviewed
- All hard constraint violations documented with examples
- Discrepancy report ready for discussion with school
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Progress Update (2026-02-23)

**Completed**: AC#1 (received spalmone CSV with 14 groups, 11,136 lessons), AC#2 (created parser scripts), AC#3 (imported 10,127 pinned lessons via spalmone-db-importer.py). T28 (ID 14) mapped correctly - 156 lessons all on Thursdays.

**Blocker on AC#4**: Analysis endpoint returns empty constraints list. Score: `-17328init/0hard/0medium/0soft` but `constraints: []` is empty. Backend may not be using SolutionManager.analyze() correctly. Need to investigate ScheduleResource/ScheduleService implementation.

**Files Created**: scripts/spalmone-parser.py, scripts/spalmone-db-importer.py, scripts/spalmone-report.py. Source CSV in Downloads folder.

**Next**: Verify backend uses SolutionManager.analyze() for proper constraint breakdown. Without this, we cannot confirm if human planner violated hard constraints.

## Analysis Completed (2026-02-23)

**Fix applied**: Added `solutionManager.update(schedule)` before `analyze()` in `ScheduleService.analyzeScore()`. Added `?all=true` query parameter to analysis endpoint to show all constraints including satisfied ones. Added `init` field to analysis response.

**Root cause of empty constraints**: The spalmone-test profile had been recreated (DataGenerator seed data, all planning variables null). After re-importing and assigning placeholder rooms, analysis returns full results.

**Key findings** (documented in `claudedocs/sample-plan-analysis.md`):
- H3 (student group conflict) = 0 violations → validates our constraint model
- H1.1 (teacher conflict) = 7 real double-bookings in the school's schedule
- H6.1 (school holiday) = 6 G14 lessons on Christmas/St. Stephen's Day
- H4.1 (stage blackout) = 86 violations (partially due to missing professionalizzanti flags)
- S4 (consecutive same-subject) = 2,461 matches → validates LabBlock entity design

**Files modified**: `ScheduleService.java`, `ScheduleResource.java`
**Files created**: `claudedocs/sample-plan-analysis.md`

Completed: Turnkey spalmone analysis pipeline implemented.

Deliverables:
- scripts/spalmone-parser.py: CSV→JSON parser (restored from deleted import-spalmone.py)
- scripts/spalmone-validate.py: Analysis engine (REST API + SQL queries → markdown report)
- scripts/spalmone-analyze.sh: Orchestrator (single command runs full pipeline)
- scripts/data/spalmone.csv: Master schedule CSV as project artifact
- claudedocs/scripts.md: Documentation for all scripts

Verified: 32/32 data points match the previous manual analysis exactly.
Commit: b7248e7
<!-- SECTION:NOTES:END -->
