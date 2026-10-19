---
id: TASK-372
title: 'Afternoons are stage-only: constraint for non-stage lessons in slots 6-7'
status: Done
assignee: []
created_date: '2026-07-17 09:50'
updated_date: '2026-07-17 23:23'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies: []
priority: high
ordinal: 196000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: referente (17/07, C1+Q17): the afternoon slots (14:30-16:30) 'vanno usati solo per lo STAGE'. This year's 575 afternoon real lessons are a thing NOT to repeat — the solver must keep normal lessons out of slots 6-7 going forward.

WHAT:
1. New constraint 'afternoon slots reserved for stage': penalize non-pseudo (non-Stage) lessons in slotIndex >= 6. Tier decision with numbers: HARD would declare the imported golden solution infeasible (575 matches — but golden lessons are pinned; check whether the constraint can exclude pinned lessons like M20 does, making it hard for SOLVER-PLACED lessons only — prefer that: hard + pinned-exempt). Canonical ID next free H-number, format 'Hxx - Afternoon slots reserved for stage' (no colons), CLAUDE.md table + docs.
2. Interaction check: GroupCalendarRule seeds derived from the golden still mark slots 6-7 open for 9 groups — leave the calendar as CAPACITY (stage needs those slots) and let the new constraint govern lesson-type usage; document the separation of concerns.
3. ConstraintVerifier tests (pinned exempt, unpinned penalized, stage pseudo exempt); golden audit measurement note.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Constraint implemented (pinned-exempt hard or calibrated medium — decided with numbers), tests green
- [x] #2 CLAUDE.md table + constraint docs updated; calendar-vs-constraint separation documented
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
H21 'Afternoon slots reserved for stage' shipped: hard, pinned-exempt (golden's 575 afternoon lessons stay legal), pseudo-exempt (stage hours ARE the reservation), include-null (test-locked), with the matching VRP structural filter in Lesson.getAvailableTimeslots (H20 precedent — review catch). Calendar-vs-constraint separation documented in code/CLAUDE.md/frontend docs (en+it). Guard pin H21=0. Full DoD; tests 528/528.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
