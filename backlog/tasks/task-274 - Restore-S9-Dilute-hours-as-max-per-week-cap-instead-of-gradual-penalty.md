---
id: TASK-274
title: Restore S9 (Dilute hours) as max-per-week cap instead of gradual penalty
status: Done
assignee: []
created_date: '2026-03-19 12:18'
updated_date: '2026-07-27 14:42'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context
S9 (Dilute hours over year) was disabled in task-273 because it actively opposes calendar compactness (M22). The original S9 penalizes having >1 lesson of the same subject in the same week, which spreads lessons across the full school year and creates calendar holes.

## Problem with original S9
- Weight: 1 soft per excess lesson (e.g., 2 lessons/week = -1 soft, 3 lessons/week = -2 soft)
- Effect: Creates strong spreading force — ~2,200 matches producing ~-43,000 soft
- Conflict: Directly opposes M22 (calendar compactness) and M20 (no sparse days)
- Root issue: Penalizes ALL concentration above 1h/week, but the school needs some subjects at 3-5h/week (e.g., Matematica with 66h/year needs ~2h/week)

## Proposed replacement: Max-per-week cap
Replace the gradual penalty with a hard cap on max hours per subject per week:
- Example: max 3 lessons of the same subject per week (configurable per group)
- This preserves the pedagogical goal (avoid 6h of one subject in a single day/week)
- Without creating a continuous spreading force that fills the calendar with holes
- Could be medium constraint (like M20) to allow solver flexibility

## Alternatives considered
1. **Reduce weight** from 1 to 0.1 — too weak to matter, but still adds spreading noise
2. **Conditional**: Only apply S9 after calendar is compacted — complex, solver would need two phases
3. **Per-group threshold**: Some groups need more concentration than others — adds configuration complexity

## Files to modify
- `SchoolScheduleConstraintProvider.java` — uncomment or replace `diluteHoursOverYear()`
- `SchoolScheduleConstraintProviderTest.java` — update tests
- CLAUDE.md — update constraint table (mark S9 as restored or replaced)

## Depends on
- task-273 (Calendar compactness constraint) — should be validated first
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Inserita in m-12: e' una delle 'leve di contorno' della cadenza fine (cap settimanale) - coordinare con TASK-425, che e' l'ombrello.

SUPERSEDED da TASK-425 / M34 (2026-07-27). 274 proponeva un 'max-per-week cap per (group,subject)' in sostituzione di S9 (che opponeva M22/M20 con una spreading force). M34 (weeklySubjectBurstCap, TASK-425) realizza ESATTAMENTE quel cap settimanale per (gruppo,materia), ma: (a) golden-faithful - il cap e' il max del golden per pair (self-gating, non flat configurabile); (b) senza il difetto di S9 - penalizza solo sopra il cap, nessuna spreading force sotto (non crea buchi calendario). Verificato su live (run1: M34 -8.900/59, burst 45->40%). CHIUSO come superseded; il task e' implementato in forma migliore da M34.
<!-- SECTION:NOTES:END -->
