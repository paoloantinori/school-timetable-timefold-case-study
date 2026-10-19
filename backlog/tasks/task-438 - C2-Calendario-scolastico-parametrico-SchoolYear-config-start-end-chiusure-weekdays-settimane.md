---
id: TASK-438
title: >-
  C2 Calendario scolastico parametrico (SchoolYear config:
  start/end/chiusure/weekdays/settimane)
status: To Do
assignee: []
created_date: '2026-07-28 08:23'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: high
ordinal: 261000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Audit C2: sostituisce SCHOOL_YEAR_START/END, CLOSURES_2026_27, 3 StagePattern letterali, FALLBACK_END_DATE, DEFAULT_WEEKS, 21 siti Mon-Fri. Sforzo M, rischio basso (meccanico). Include la regola primo-giorno-cerimoniale come dato di calendario, non costante.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
