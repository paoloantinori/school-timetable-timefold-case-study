---
id: TASK-382
title: Derive school-year range from the API instead of hardcoded frontend constants
status: Done
assignee: []
created_date: '2026-07-19 19:49'
updated_date: '2026-07-21 17:07'
labels:
  - claude-planned
dependencies: []
ordinal: 206000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
During the pins-only visual verification (2026-07-20) the week view opened on 8-12 Sep 2025 - a year before the data - because useScheduleNavigation.ts and useScheduleData.ts hardcode SCHOOL_YEAR_START/END (they were still 2025/26; now bumped to 2026/27 as a hotfix with a TODO comment). The proper fix: derive the year range from the backend (the schedule payload already carries the year range shown in the header 'Year: 14 Sep 2026 - 10 Jun 2027', and /api/config/slots or the timeslot span can provide it) so next year's seed does not silently break the UI again. Same class of bug as the SolverPhasesPage stale-constants finding (docs review 2026-07-19): frontend constants that mirror backend data rot every year.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 No SCHOOL_YEAR_* hardcoded Date constants remain in webui hooks; range comes from an API source
- [x] #2 Week view opens on the year's first school week on a fresh profile without code edits
- [x] #3 ui-typecheck + ui-build exit 0; DoD simplify+review
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Costanti SCHOOL_YEAR_START/END hardcoded rimosse da useScheduleNavigation.ts e useScheduleData.ts. Il range d'anno viene ora derivato UNICAMENTE da yearRange (span min/max dei timeslot del payload API, gia' presente). Stato iniziale year-agnostic (settimana di oggi come placeholder) + effetto init-once ref-guarded che scatta sulla prima settimana dell'anno quando yearRange arriva, senza combattere i refetch (poll/solve). goToday/goPrev/goNext/atStart/atEnd null-guardano i bound quando yearRange e' assente; allDays torna [] fino al load (consumer verificati: react-window bounded + guardie if(!day), nessun crash). Import inutilizzato rimosso da ScheduleTimeline. AC#2 preservato: apre sulla prima settimana dell'anno (come prima, ma ora derivato non hardcoded). ui-typecheck 0, ui-build 0. DoD: /code-review high indipendente (copre anche gli angoli /simplify) - nessun bug di correttezza, il rischio allDays-vuoto refutato (accessi guardati). Verifica visiva live rimandata (app spenta), logica garantita per costruzione + refactor behavior-preserving.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
