---
id: TASK-149
title: >-
  Fix acconciatura LabRotation data in DataGenerator (T26 + T18
  per-group days)
status: Done
assignee: []
created_date: '2026-03-03 11:53'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - data-fix
dependencies: []
references:
  - bootstrap/DataGenerator.java
  - claudedocs/human-planner-heuristics.md
priority: high
ordinal: 103000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Update T26's and T18's LabRotation records in DataGenerator to use correct per-group days derived from spalmone analysis.

**Problem**: T26 has ALL 4 groups assigned to WEDNESDAY — 66 blocks can't fit in ~36 Wednesdays. T18 has all groups on TUESDAY.

**T26 changes** (lines 749-752):
- G02: WEDNESDAY → TUESDAY (spalmone: 90% on Tuesday)
- G06: WEDNESDAY → WEDNESDAY (correct)
- G10: WEDNESDAY → WEDNESDAY (58%, overflow to Fri handled by solver)
- G12: WEDNESDAY → MONDAY (spalmone: 92% on Monday)

**T18 changes** (lines 758-761):
- G02: TUESDAY → TUESDAY (same day as T26/G02, different weeks)
- G06: TUESDAY → WEDNESDAY (same day as T26/G06)
- G10: TUESDAY → FRIDAY (T26 overflow day)
- G12: TUESDAY → REMOVE (no Lavaggio course for G12)

**Capacity after fix**: All 5 weekdays within capacity (12-34 blocks per day vs 18-36 available weeks).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 T26 LabRotation: G02=TUESDAY, G06=WEDNESDAY, G10=WEDNESDAY, G12=MONDAY
- [ ] #2 T18 LabRotation: G02=TUESDAY, G06=WEDNESDAY, G10=FRIDAY, G12 record removed
- [ ] #3 Capacity check: no day exceeds available weeks (verified in plan)
- [ ] #4 All existing tests still pass (mvn test)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Spalmone Interpretation\n\nThe spalmone-derived days (TUE/WED/WED/MON for T26) are the best available evidence of the school's intended assignment, but they are PATTERNS not hard constraints. What matters is the DISTRIBUTION (different days per group for room capacity), not the specific day choices. The school can adjust individual day assignments — the algorithm doesn't care which day, only that each pair has a defined day and the overall capacity works.\n\n**Key evidence strength:**\n- T26/G02: 90% on Tuesday → very strong signal\n- T26/G06: 67% on Wednesday → reasonable signal\n- T26/G10: 58% Wed + 40% Fri → suggests overflow (human planner tried consistency but couldn't)\n- T26/G12: 92% on Monday → very strong signal
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by LabRotation removal (commit e71bc85). All T26 and T18 LabRotation entries were REMOVED from DataGenerator. CSP now discovers days for all internal teachers automatically. No entries left to fix.
<!-- SECTION:FINAL_SUMMARY:END -->
