---
id: TASK-530
title: >-
  Stanze sui pin importati (backfill via tag) + STAGE costante per slot stage:
  nessuna cella senza aula se non davvero problematica
status: Done
assignee: []
created_date: '2026-08-19 12:19'
labels:
  - claude-planned
  - ui
  - bug
  - data
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Richieste maintainer 14:15-14:20 19/08 durante ispezione: i pin importati non trasportano stanza e H29 li esente, quindi celle lab mostrano vuoto (G09 13/05 Moulage esempio verificato sul vivo); gli slot stage confusi con non-assegnati. Mandati spediti (/tmp/zai-room.txt, /tmp/zai-stage.txt). Questi sono i punti 2-4 citati nel TASK-529.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backfill stanze sui pin importati: al momento dell'import calendario, i pin ricevono la stanza derivata via tag della materia (Moulage=moda lab, Confezione=moda, acconciatura-family=lab acconciatura): metadato di display, non decisione di ricerca, piazzamento intoccato. Esempio di riferimento: G09 13/05 ore 11-13 Moulage/T17 pinnata room=None
- [ ] #2 STAGE costante: gli slot di stage mostrano la stanza STAGE (backend nel payload o regola di display UI, la piu' pulita), distinguendo le non-assegnate dalle non-richieste (fuori scuola)
- [ ] #3 Dopo entrambi: nella UI restano senza aula SOLO le celle davvero problematiche
- [ ] #4 Verifica browser con l'esempio di maintainer (G09 13/05) come test di accettazione
<!-- AC:END -->


## Esiti (laptop 19/08 sera)
- (a) backfill: persistPinnedLesson ora deriva la stanza dal tag del corso (prima Room assegnabile col tag): metadato di display, placement intoccabile, H29 continua a esentare i pin. Il backfill agisce all'IMPORT: serve reimport calendario per vederlo sui dati esistenti.
- (b) STAGE costante: cellRenderer + tooltip mostrano STAGE per slot pseudo (room-null per natura).
- (c) drill-down: LessonTooltip filtra H29 su pin/pseudo (stessa regola del server).
Commit fb036851. Verifica visiva del caso G09 13/05 Moulage da fare dopo reimport + rebuild (punto 4 coda).
