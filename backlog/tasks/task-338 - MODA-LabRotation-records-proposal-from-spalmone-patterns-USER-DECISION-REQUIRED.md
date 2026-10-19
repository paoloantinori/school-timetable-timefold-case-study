---
id: TASK-338
title: >-
  MODA LabRotation records proposal from spalmone patterns (USER DECISION
  REQUIRED)
status: Done
assignee: []
created_date: '2026-07-12 17:49'
updated_date: '2026-07-16 09:41'
labels:
  - claude-planned
  - analysis
  - user-decision
  - experiment
milestone: m-6
dependencies:
  - TASK-337
references:
  - claudedocs/research/pre-assignment-vs-spalmone-comparison-20260314.md
  - claudedocs/insights/preassign-pin-rate-metrics.md
priority: medium
ordinal: 162000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: MODA groups (1/2/G09) have ZERO LabRotation records, so their lab lessons get pinned by solver CH output instead of deterministic human-style patterns - one of the four open priorities from claudedocs/research/pre-assignment-vs-spalmone-comparison-20260314.md, never resolved. Missing rotations also mean the pre-assignment pipeline cannot mimic the human planner for a third of the groups.

WHAT:
1. Extract the actual weekly patterns for MODA lab subjects (Confezione, Modello, Moulage, Tecnologia tessile, Informatica) from the spalmone data using scripts/analyze/spalmone-parser.py output: dominant day-of-week, slot range, block size per subject per group per trimester.
2. Translate into LabRotation rows (teacher, group, dayOfWeek, slotStart, blockSize, periodStart-periodEnd, roomTag) - schema per CLAUDE.md Pre-Assignment Workflow section and the labrotation table (camelCase-collapsed column names).
3. Deliverable: claudedocs/research/moda-labrotation-proposal-YYYYMMDD.md with the pattern evidence AND ready-to-run INSERT statements for ./scripts/db/db-query.sh -w.
4. Verify on a SCRATCH profile only (planner.sh start scratch-moda): apply inserts, run pre-assign, confirm HybridPhase1aService consumes the rotations (lab pin rate for MODA groups rises toward the ~97 percent global metric - see claudedocs/insights/preassign-pin-rate-metrics.md) and no INFEASIBLE errors.
5. STOP before applying to the main dev profile - the exact rotation assignments need school confirmation (CLAUDE.md flags this explicitly). Present the proposal to the user.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Patterns extracted from spalmone with evidence tables per subject/group/trimester
- [ ] #2 INSERT statements provided and verified on a scratch profile (pre-assign consumes them, MODA lab pin rate reported)
- [ ] #3 Main profile NOT modified; proposal presented for user/school confirmation
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERSEDED 2026-07-16: 2026/27 spalmone overrides all data; T28 no longer teaches (absent from Docenti and the timetable) so T28-based MODA LabRotation proposals are obsolete. Lab-day patterns for 2026/27 are analyzed in claudedocs/spalmone-2026-27/analysis.md section 4.4; the fixed-day assumption itself is under review in TASK-351.
<!-- SECTION:NOTES:END -->
