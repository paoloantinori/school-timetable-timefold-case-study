---
id: TASK-507
title: >-
  Diagnosi H29 -7739 del round 3 (perche' i blocchi rifiutati dal pre-assign
  travolgono la B-ROOM) + ipotesi TASK-498 corner-b
status: Done
assignee: []
created_date: '2026-08-16 20:01'
updated_date: '2026-08-16 20:14'
labels:
  - claude-planned
  - solver
  - diagnosis
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Esito round 3 (01ad26d9, verificato Fedora): la cura TASK-502 funziona sui bersagli (H20 0, H7 -2) ma il failure migra sul room-tier: H29 -7739 dominante, 5/5 tentativi falliti, full-day 67,7. Il verdict dichiara il meccanismo non stabilito. Questo task e' la diagnosi differita che il verdict indica come precondizione per il round 4. IPOTESI da verificare (Fedora, 2026-08-16 sera): i tentativi condannati del treatment ricevono stanze CH-only per TASK-498 (skip della rifinitura LS su tentativi non-ultimi condannati), e la rifinitura finale del vincitore condannato (corner b) potrebbe non essere mai girata perche' il tentativo 5 e' abortito con NPE prima della consegna. Questa catena spiegherebbe anche la discrepanza score.txt (-42) vs analysis (-7795): score in-memory pre-abort contro stato DB post-rilascio pin.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Meccanismo H29 -7739 stabilito dai run3 artifact catturati (nessun nuovo solve richiesto): traiettoria room-null per fase e per tentativo dal app.log/driver.log/analysis
- [ ] #2 IPOTESI FEDERA da verificare per prima: interazione con TASK-498 skip-doomed-room. Tutti i tentativi treatment sono condannati (timeslot-hard sotto soglia) -> CH-only rooms; verificare nei log se il corner (b) (rifinitura wrapper del vincitore condannato) HA GIRATO dopo l'abort NPE del tentativo 5. Se non ha girato: quello e' il meccanismo (o gran parte di esso)
- [ ] #3 Verificata anche l'alternativa: la domanda aggregata di stanze dei blocchi unpinned concentrati supera l'offerta di tag per (weekday, slot) gia' alla CH (saturazione strutturale)
- [ ] #4 Esito scritto nel VERDICT come sezione ROUND 3-DIAGNOSI con la catena causale dimostrata dai log
- [ ] #5 Precondizione per il round 4 della chain 364+467
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## DIAGNOSI COMPLETATA (16/08 notte, commit b6b1aa11)

VERDICT.md sezione ROUND 3-DIAGNOSI. Tre fatti dimostrati dai log:

(1) room CH INERTE su questo problema in ENTRAMBE le braccia (CH 467/563ms score invariato; control: LS refinement -9404hard -> 0hard in 601s fa tutta l'assegnazione). Assunzione TASK-498 'CH assegna stanze ai condannati' FALSA.

(2) tentativi treatment condannati a timeslot-tier -42 (20 boxed x H0-peso-2 + 1 H17): skip-doomed rimuove l'LS => H29 = 12494-4735-20 = 7739 non-pinnate senza stanza (conti tornati). Gap REALE treatment-vs-control: solo -42.

(3) corner-b HA GIRATO dopo l'abort NPE (ipotesi Fedora confutata: rifinitura lanciata 21:30:23,375, LS 21:30:24,584, -42hard in-memory alle 21:32:38, FUNZIONA) ma il driver la uccide: quiescenza NOT_SOLVING+stagedStage-null scatta mentre la rifinitura gira su solver ad hoc, capture congela il DB mid-refinement (-7795hard), score legge latestScore mai persistito (-42hard), app stop 21:36:59 prima del save.

AC#1 (ipotesi 498 verificata per prima): confutata nella forma 'non e' partita', confermata nella sostanza 'e' stata uccisa'. AC#3 (alternativa saturazione CH): assorbita dal Fatto 1, la CH non piazza nulla quindi non e' saturazione della CH ma inerzia della CH. Proposte operative nel VERDICT (soglia/comparabilita', quiescenza driver, target round 4 = -42 costruzione): NESSUNA applicata.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
