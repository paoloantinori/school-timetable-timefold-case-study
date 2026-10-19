---
id: TASK-665
title: >-
  UX: nav e Docs riordinate per il pubblico scuola; spiegare le strisce
  per-classe (X/Y ore)
status: Done
assignee: []
created_date: '2026-09-24 08:46'
labels:
  - claude-planned
  - ux
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Findings minor/obsolescenza della UX review 24/09: (1) pagine dev nel nav principale a peso pieno: Complexity (task-087, explainer agent-authored), Phases (macchina interna, nomina TASK e B-TIMESLOT in stringhe visibili), Storia (STORIA.md/RIAPPROCCIO.md, narrativa di sviluppo), Docs guida per ID vincolo; TASK-514 (94690ca8) aveva gia' riconosciuto il problema spostando Benchmarks/Reports sotto la sezione dim 'SVILUPPO': completare quel movimento. (2) DocsPage espone ancora il blocco 'Golden calibration' (DocsPage.tsx:118-119): concetto invisibile alla scuola, in ritiro (TASK-441); task-511 gia' aperto per la semantica M20 stantia. (3) Sulla timeline la striscia per-classe mostra rapporti non spiegati: misurato sul pubblico 'G07 451/696', 'G14 0/75' accanto a '12551/12551 assigned (100%)': il planner legge 'questa classe ha solo il 65% delle ore' e non c'e' tooltip che spieghi (ore dichiarate vs collocate, stage off-grid, expectedUnscheduledHours). (4) La label nav 'Vincoli' e' hardcoded italiana bypassando i18n (App.tsx:61-63): nell'UI inglese resta italiana.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Complexity, Phases, Storia e Docs-by-ID non sono piu' nav principale a peso pieno per l'utente scuola (sezione dedicata o rimozione)
- [x] #2 Il blocco Golden calibration esce dalla Docs page scuola (golden e' artefatto interno in ritiro, TASK-441)
- [x] #3 Ogni indicatore X/Y visibile sulla timeline ha tooltip/copy che dice cosa conta (ore collocate vs ore dichiarate, con trattamento stage/off-grid)
- [x] #4 Il nav e' coerente con la lingua attiva (niente 'Vincoli' fisso dentro UI inglese)
<!-- AC:END -->
