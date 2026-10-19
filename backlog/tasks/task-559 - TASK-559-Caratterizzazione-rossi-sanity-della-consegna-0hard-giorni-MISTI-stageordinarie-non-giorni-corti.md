---
id: TASK-559
title: >-
  TASK-559 - Caratterizzazione rossi sanity della consegna 0hard: giorni MISTI
  stage+ordinarie, non giorni-corti
status: Done
assignee: []
created_date: '2026-08-26 08:17'
labels:
  - claude-planned
  - measurement
  - task556-family
dependencies: []
priority: medium
type: task
ordinal: 317000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RENAMED da TASK-557 locale (26/08): l'id 557 appartiene al task H34 empty-declared-day pushato da Fedora (remoto 529ab269); il file locale non-rinominabile dal worktree resta come artefatto da pulire nel checkout principale (vedi nota nel quadro-risveglio). CONTENUTO (misure 26/08 mattina, commit 7c71e5c9): i 5 gruppi rossi sanity della consegna 0hard qfrv2-on sono TUTTI giorni MISTI (ordinarie+stage): run 6 misti vs golden 2; 5 dei 6 nascono nella prima LS B-TIMESLOT-1 e restano frozen; 1 (G08 01/04) e' dichiarato dal foglio. Non sono giorni corti: H33 e' 0 e resta 0 (scope esclude pure-stage), l'ipotesi floor-nel-pricer e' CONFUTATA. I buchi sono artefatto script sanity (stage esclusi dal conteggio occupati). Cure candidate nel doc, NON implementate. Doc: claudedocs/spalmone-2026-27/task556-results/caratterizzazione-rossi-sanity-2026-08-26.md + registro iterations-542.md.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
