---
id: TASK-436
title: >-
  HOTFIX audit: letterali anno-stantio vivi + slot hour-8 per coincidenza +
  AFTERNOON index derivato + export tabelle mancanti
status: Done
assignee: []
created_date: '2026-07-28 08:23'
updated_date: '2026-07-29 13:35'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: high
ordinal: 259000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
I fix di correttezza IMMEDIATI dall'audit (claudedocs/audit-genericita-multi-anno-2026-07-28.md, sez. bug #1-#4, #6): DayClusteredConstructionPhase:479 (2025-09-10 VIVO) e :521 (fallback 2026-07-01), TargetEndDateCalculator:95/99/317, Lesson:38+StudentGroupCompletionService:37 (hour-8 -> TimeslotSequence.slotIndexOf), AFTERNOON_START_SLOT_INDEX derivato dal pranzo, ImportService export +6 tabelle. Indipendenti dalla migrazione, abilitano C7 futura. DoD standard.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSO 29/07 (zai): #1-#4 hotfix fatti e verificati invisible su 2026/27. #1 DayClustered anno literal -> SCHOOL_YEAR_START/END (09c8d523, ricorrenza costr 76.3% identica). #2 TargetEndDate anno literal -> SCHOOL_YEAR constants, fix anno-errato :99 (ddac6e2b, FactBasedTest verde). #3 slotIndexOf invece di hour-8 in Lesson+StudentGroupCompletionService (8d2e12ef, equivalence test + WeeklyTemplateConstructionRealSeedTest verde). #4 AFTERNOON_START_SLOT_INDEX derivato da LUNCH_BREAK_END invece di literal 6 (8a7d0ce7, value 6 invariato). GUARD: golden regression verde (vincoli pinned invariati); per-increment targeted tests green. #6 (export tabelle) SPLIT a m-13 come TASK-454 (piu' grosso di un hotfix: method JDBC insert nuovo + campo SchoolSchedule + truncate + verify static-refresh + round-trip test; approach confermato low-risk nel task; deferiti RecurringAvailabilityRule/LabRotation/ValidationSnapshot con rationale; audit '6 tabelle' corretto a 2 gia' coperte + 1 da fare + 3 deferite).
<!-- SECTION:NOTES:END -->
