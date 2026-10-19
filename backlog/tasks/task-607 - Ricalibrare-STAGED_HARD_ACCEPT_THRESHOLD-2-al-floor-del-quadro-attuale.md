---
id: TASK-607
title: Ricalibrare STAGED_HARD_ACCEPT_THRESHOLD (-2) al floor del quadro attuale
status: To Do
assignee: []
created_date: '2026-09-12 12:24'
labels:
  - claude-planned
  - thresholds
  - process
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Rilievo 12/09 durante il banco M39 (braccio A, tentativo 1 DOOMED a -12 vs soglia -2): la soglia STAGED_HARD_ACCEPT_THRESHOLD=-2 e' tarata sull'era in cui il 0-hard era raggiungibile nei bacini buoni (A/B TASK-498 del 21/08, stop-al-primo-0-hard). Nel quadro attuale il miglior delivery noto e' -11 hard (TASK-599), quindi la soglia -2 e' probabilmente irraggiungibile per costruzione: ogni tentativo esce doomed, l'early-stop non parte mai, il wrapper brucia sempre maxAttempts=5 + churn reseed (cap 32), e la parola DOOMED nei log non distingue piu' "bacino condannato" da "quadro con floor strutturale". Il pick-best sul boundary tier e il corner-b (rifinitura del vincitore prima della consegna) rendono il risultato CORRETTO comunque; il costo e' solo wall e leggibilita'. Quando m-14 stabilisce il floor reale del residuo, ricalibrare la soglia o rinominare la semantica. NON toccare durante il banco M39 in volo (stessa macchina in entrambe le braccia = confronto appaiato onesto).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Censire i call-site della soglia (gate B-TIMESLOT, DOOMED post-RELEASE TASK-498) e il loro comportamento combinato quando il floor > -2
- [ ] #2 Proporre la forma: soglia adattiva al floor misurato del quadro, o rinominare la semantica (doomed -> not-zero-capable) per non affidare informazione a una parola morta
- [ ] #3 Wall budget del ciclo rivalutato col regime all-doomed: 5 tentativi pieni + churn reseed (maxDiscards 32) sono il caso normale attuale, non l'eccezione
- [ ] #4 Decisione finale a maintainer; nessun cambio di default senza ratifica
<!-- AC:END -->
