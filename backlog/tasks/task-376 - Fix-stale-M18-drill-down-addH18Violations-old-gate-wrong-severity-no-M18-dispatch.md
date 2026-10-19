---
id: TASK-376
title: >-
  Fix stale M18 drill-down (addH18Violations): old gate, wrong severity, no M18
  dispatch
status: Done
assignee: []
created_date: '2026-07-18 01:41'
updated_date: '2026-07-25 07:31'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-9
dependencies: []
priority: medium
ordinal: 200000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: found by TASK-350's code-review (3 finder angles independently): ScheduleService.addH18Violations still uses the pre-TASK-342/350 static maxHoursPerDay gate, a hardcoded 08:00 anchor, severity 'HARD' and the dead 'H18' label — and the analysis dispatch has no 'M18' branch at all, so the per-lesson drill-down (/api/schedule/analysis/lessons) disagrees with the actual M18 score.

WHAT: rewrite the mirror to M18's current semantics (calendar-window gate + first-open-slot anchor via SchoolSchedule.getGroupCalendarWindow, MEDIUM severity, 'M18' label + dispatch branch), reuse the shared helpers from TASK-350, GroupByViolationAnalysisTest coverage, i18n violation message keys if labeled.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Drill-down matches the M18 constraint's semantics; dispatch branch exists; tests green
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
IMPLEMENTATA 2026-07-25 (treno): il drill-down vero era gia' allineato dalla 405; sistemato il contorno stantio: case H18 morto -> M18 (buildDayStartOffsetArgs), FiveHourGroupStartJustification -> DayStartOffsetJustification, messages en/it, commenti. Review treno: la catena justification era ancora MORTA (nessun justifyWith) -> ora VIVA: .justifyWith aggiunto al vincolo M18 (pattern H7/H11/M20), il path i18n TASK-103 per M18 funziona; assertion del test irrobustita (offset 7, non ambiguo con la data).
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
