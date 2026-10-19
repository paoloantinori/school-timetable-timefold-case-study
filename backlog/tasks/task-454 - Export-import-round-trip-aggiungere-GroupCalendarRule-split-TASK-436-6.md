---
id: TASK-454
title: 'Export/import round-trip: aggiungere GroupCalendarRule (split TASK-436 #6)'
status: Done
assignee: []
created_date: '2026-07-29 13:34'
updated_date: '2026-09-25 09:37'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: medium
ordinal: 277000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Split da TASK-436 #6 (audit genericita'). L'export (/api/schedule/export = SchoolSchedule serialization via getFullScheduleForExport) NON copre GroupCalendarRule, consumata dal solver via STATIC (SchoolSchedule.setGroupCalendarRulesStatic :577, wiring in ScheduleService.refreshSolverStatics :1772). Oggi export NON la porta -> import (ImportService.importSchedule:69 truncate+insert) la PERDE -> dopo un import la static e' vuota -> H20/calendar rotto su un re-solve A/B. E' l'unicco dei tavoli mancanti davvero solver-relevant.

VERIFICATO (approccio confermato, low-risk):
1. SchoolSchedule: aggiungere instance field List<GroupCalendarRule> + getter/setter NON annotato (@ProblemFactCollectionProperty ASSENTE: il solver usa la static, non la collection; Jackson serializza per export). Speculare a come e' aggiunto dailyActivityQuotas.
2. ScheduleService.getFullScheduleForExport: schedule.setGroupCalendarRules(GroupCalendarRule.listAll()).
3. ImportService.insertAllWithJdbc(:188): aggiungere insertGroupCalendarRules(conn, schedule.getGroupCalendarRules()) (method JDBC batch nuovo, mirror insertRooms :241).
4. ImportService.truncateAllTables: assicurarsi che groupcalendarrule sia troncata (oggi tronca labrotation + lesson,course,...).
5. Verificare che ScheduleService.importSchedule chiami refreshSolverStatics (cosi' la static si rewira dalla listAll post-import); se non lo fa, e' un bug separato (import rompe anche dailyActivityQuota).
6. Test round-trip: export -> import -> assert GroupCalendarRule count match (e che la static e' popolata).

DEFERITI con rationale (NON in questo task):
- RecurringAvailabilityRule: espansa in TeacherAvailability (che E' exported) da AvailabilityExpansionService -> re-derivabile; export utile solo per editing admin del template, non per round-trip del solve.
- LabRotation: deliberatamente "seed data, not exported/imported" (ImportService:204) + truncateAllTables la re-seeda; e' pre-assignment seed.
- ValidationSnapshot: artefatto di run di validazione (output), non schedule state; non deve round-trippare.
CORREZIONE audit "6 tabelle": 2 gia' coperte (dailyActivityQuota TASK-381, professionalizzantiDay), 1 da aggiungere (GroupCalendarRule), 3 deferite (RecurringAvailabilityRule, LabRotation, ValidationSnapshot). Scope: hotfix invisible sul solve 2026/27 (cambia solo import, non il solve); guard: full suite + golden + round-trip test. Non allargare a LabRotation/ValidationSnapshot (scope esatto).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-25 10:40 Fedora: cura landed nel working tree. Gates complete: /simplify 4-angle (reuse+efficienza CLEAN; 2 finding semplificazione applicati: commento TASK-666 reso coerente col nuovo mondo, storia ridotta a puntatori verso getFullScheduleForExport; altitude: warn vacuous-parity simmetrico + rename test in ExportCarriage), /code-review high 8-angle (F1 CRITICO curato: SchoolScheduleSolutionFileIO.read ora pubblica setGroupCalendarRulesStatic, il sito benchmark offline era l'unico consumatore non cablato; F3 curato: validateReferences ora valida la FK di tutte e tre le tabelle carried; F4 curato: javadoc SnapshotFixture aggiornato; F5 curato: helper warnIfVacuous). DEFERRED annotato: F2 test automatico della meta' import (SQL PG-only blocca H2; protezione = parita' runtime + round-trip vivi). Test: GroupCalendarRuleExportCarriageTest 2/2 EXIT=0. Round-trip vivo su lane laptop in corso.

2026-09-25 11:40 VERIFICA VIVA CHIUSA (log /tmp/uxlane-roundtrip-454.log): jar 6e494986 su lane DB uxlane2, export 21.337.952 byte con 83 GroupCalendarRule -> import http 200 -> conteggio DB 83 -> 83 (match esatto), log app 'Inserted 83 GroupCalendarRule entities' + 'Payload parity verified for 13 carried tables'. DoD completa (simplify 4-angle applicata, code-review high 8-angle con 5 finding curati incluso il critico FileIO-offline, test EXIT=0 orchestratore). TASK CHIUSO.
<!-- SECTION:NOTES:END -->
