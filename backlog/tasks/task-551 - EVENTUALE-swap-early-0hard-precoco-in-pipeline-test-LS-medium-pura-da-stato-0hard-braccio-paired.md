---
id: TASK-551
title: >-
  EVENTUALE: swap-early / 0hard-precoco in pipeline (test LS medium-pura da
  stato 0hard, braccio paired)
status: Done
assignee: []
created_date: '2026-08-25 13:36'
labels:
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Da domanda di maintainer 25/08 (dopo il 0hard del RoomTailFinisher): le swap sono efficaci nel chiudere buchi; avrebbe senso usarle ALL'INIZIO della pipeline per arrivare a 0hard prima e lasciare la LS ottimizzare i medium? Reflect max 25/08: (a) ragione strutturale per cui stanno in fondo: le buche che chiudono sono CREATE dalle fasi successive (LS apre buchi, stanze aprono room-tail), a inizio ciclo non esistono; i repair mid-pipeline esistono gia'; (b) il beneficio immaginato (LS libera sul medium) e' contraddetto dal verdetto medium-post-repair (medium senza headroom locale, gap = costo del metro) MA quel test giro' dalla consegna di un ciclo gia' convergente, non da uno 0hard precoce: la differenza e' testabile; (c) beneficio reale alternativo: 0hard garantito a meta' pipeline = affidabilita', wall, checkpoint sicuri, LS sempre vicino alla frontiera feasible. Collegata a TASK-546 (relaxed-solve) come variante speculare di feasibility-first. Formato T549: un braccio paired 20 min, predizione prima.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Braccio checkpoint-paired (1200s): resume dal banco 0hard del 25/08, LS medium-pura con accettazione stretta hard-sicura (le swap/mosse non possono rompere lo 0hard)
- [ ] #2 Predizione PRIMA: se il medium si muove <= ~5k, la premessa e' settle-ata negativa (medium = costo del metro, conferma medium-post-repair); se si muove di decine di migliaia, il medium-post-repair fu bocciato per posizione nella pipeline, non per idea, e la proposta di maintainer riapre
- [ ] #3 Opzionale (secondo stadio, solo se il primo e' positivo): inserire PHR/finisher dopo OGNI confine di fase e misurare wall + medium finale del ciclo vs pipeline attuale
- [ ] #4 Raccomandazione a maintainer; nessuna adozione senza gate. PRIORITA' BASSA: scatta solo dopo che lo 0hard e' consolidato (dopo ratifica del ciclo in corso) e se ci sono slot liberi
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
