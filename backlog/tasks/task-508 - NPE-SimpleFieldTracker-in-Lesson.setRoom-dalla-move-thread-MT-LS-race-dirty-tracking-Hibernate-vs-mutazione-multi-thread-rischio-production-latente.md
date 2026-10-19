---
id: TASK-508
title: >-
  NPE SimpleFieldTracker in Lesson.setRoom dalla move thread MT-LS (race
  dirty-tracking Hibernate vs mutazione multi-thread, rischio production
  latente)
status: Done
assignee: []
created_date: '2026-08-16 20:02'
labels:
  - claude-planned
  - solver
  - bug
  - multithreaded
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bug nuovo emerso nel round 3 (tentativo 5, 21:30:23): SimpleFieldTracker.contains -> "existing is null" dentro Lesson.setRoom richiamato dalla move thread del MT-LS. E' il dirty-tracking del bytecode enhancement Hibernate che collide con la mutazione multi-thread dell'entita' arricchita. Tentativo 5 abortito (staged solve FAILED - releasing the promoted pins anyway), score null. Race rara: emerge solo dopo ~2h di room-LS pesante (il treatment aveva molta piu' pressione room-null, quindi piu' mutazioni). RISCHIO PRODUCTION: il MT-LS e' DEFAULT ON (TASK-480): la race e' sul percorso ordinario, anche se i run good-basin da 23 min non la incontrano. Correlata al path no-0-hard e alla discrepanza score/analysis del round 3.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Stack completo della NPE estratto dall'app.log del run3 (tentativo 5, 21:30:23) e meccanismo identificato: quale thread muta, quale thread fa il dirty-tracking, quale asserzione fallisce
- [ ] #2 Analisi del bytecode enhancement Hibernate su Lesson (campo room) vs la mutazione dalle move thread del MT-LS: dove avviene la condivisione dell'entita' arricchita
- [ ] #3 Fix proposto e documentato (non applicato di default): opzioni valutate (es. detach/reattach per thread, copia difensiva nel move thread, sincronizzazione del tracker) con trade-off
- [ ] #4 Se il fix viene implementato: dietro flag o con A/B dedicato, mai default flip senza evidenza
- [ ] #5 Aggiornare il VERDICT round 3 e il task con l'impatto production stimato (MT-LS e' DEFAULT ON: la race e' rara ma sul percorso ordinario)
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
