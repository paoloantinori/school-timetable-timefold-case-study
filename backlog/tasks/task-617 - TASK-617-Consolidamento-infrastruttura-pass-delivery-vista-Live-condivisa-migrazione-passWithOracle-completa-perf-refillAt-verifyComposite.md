---
id: TASK-617
title: >-
  TASK-617 - Consolidamento infrastruttura pass delivery: vista Live condivisa,
  migrazione passWithOracle completa, perf refillAt/verifyComposite
status: To Do
assignee: []
created_date: '2026-09-15 13:38'
updated_date: '2026-09-17 08:34'
labels: []
dependencies: []
references:
  - src/main/java/com/school/planner/solver/HoleRelayFinisher.java
  - src/main/java/com/school/planner/solver/CompositeSwapFinisher.java
  - src/main/java/com/school/planner/service/ScheduleService.java
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla gate DoD post-hoc 612/613 (15/09, 8 reviewer, 3 agent indipendenti convergono). (1) STRUTTURALE: HoleRelayFinisher.Live e' la TERZA copia hand-rolled della vista occupancy (giugno a CompositeSwapFinisher.Live, SurplusHarvest.Occupancy, OneOffFinisher.Index) con ~150 righe per copia e DRIFT GIA' REALIZZATI: CSC.legalLanding porta H14 (startDate) + H11 (maxHoursPerDay) aggiunti dopo un finding review mentre HRF.rejectReason non li ha; CSC.roomFor NON controlla isAssignable sulla stanza propria mentre HRF si'; il cap H7 passa per capOf(teacher) in CSC e getEffectiveMaxDailyHours() diretto in HRF; le mappe ore sono keyed per teacher NAME (string) mentre SurplusHarvest usa id. La prossima regola appresa (stile H31-first-hour del TASK-612) finira' in una copia e non nelle altre. (2) MIGRAZIONE INCOMPLETA: passWithOracle e' nato in questo range col javadoc 'the next TASK-526-class contract change must land HERE' ma solo i 2 chiamanti nuovi lo usano; CSF e ~11 altri shell della delivery chain hand-rollano lo stesso blocco capture/before/after/ensureImproveOrNoop (il claim del commit 06feec68 'sostituisce anche le copie a mano' era sovrastimato). (3) PERF (once-per-delivery, non emergenza): refillAt ricalcola la lista GATE-E per ogni candidato intermedio (fino a O(D^2*L) con LocalDate.parse per voce, ~20M valutazioni spese per ~15 accettazioni); verifyComposite rescana TUTTO live.days per ogni data toccata (~12.5k visite + substring/IndexOf per lesson per verify). CONTESTO: tutto su codice ratificato e certificato 1332/0, quindi OGNI tocca va re-certificata al banco replay (outcomes identici) prima del merge; il refactor e' manutenzione, non urgenza.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Vista occupancy condivisa estratta e adottata da HoleRelayFinisher + CompositeSwapFinisher (le divergenze H14/H11/isAssignable/H7-accessor risolte o dichiarate per-policy)
- [ ] #2 passWithOracle migra i restanti shell hand-rolled della delivery chain (CSF incluso) o il backlog dichiara perche' no
- [ ] #3 refillAt: la lista GATE-E non piu' ricalcolata per candidato intermedio (hoist o re-verifica delle 2 chiavi toccate) + mappa keyToDate che elimina LocalDate.parse per voce
- [ ] #4 Banco replay pre/post refactor: outcomes IDENTICI (m39-off -11->0 catena completa), suite EXIT 0
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
17/09 (Fedora): la parte BLOCCANTE-PER-622 e' MERGIATA su main (e9398c17 + cure 209d1cac, ff, main=a51d37aa): CompositeRecorder condiviso in HRF (10 apply/13 revert, zero copie; cura STRUTTURALE della classe bug igiene banco 622), enumerateOvercaps unica (doppia scansione teacher-hours eliminata), findTailOnlyTolerantDonor(Timeslot) unico, quotaByName in Live + sameGroupDaysByDistance. Doppia gate /simplify + /code-review con 5 cure applicate (TreeMap ordine h7cap ripristinato, re-check overcap all'ingresso di tryH7D3Pair). AC#4 SATISFIED: re-cert 2x identica (recert e recert2 su branch, 209d1cac: step14 m39-off 0 (3/3/2/7), m39-on -2 (4/3/0/8), step40 -1->0, step48 -4->-1 moves=6; suite 1353/0/0/92 EXIT 0; il control step46 divergenza attribuita a test stantio, non comportamento). RESTANO APERTI: AC#1 (vista Live condivisa HRF+CSF+SurplusHarvest: le divergenze H14/H11/isAssignable non sono toccate da questo refactor), AC#2 (migrazione passWithOracle), AC#3 (perf refillAt). Non bloccanti, in coda dietro i bracci che chiudono l'ultimo -1.
<!-- SECTION:NOTES:END -->
