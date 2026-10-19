---
id: TASK-639
title: >-
  TASK-639 - Port wiring MT-LS a Timefold 2.6.0: runner stall in
  executeTemporaryMove (canale pieno, offerLoud scatta)
status: Done
assignee: []
created_date: '2026-09-21 20:04'
labels:
  - claude-planned
  - mtls
  - timefold-2.6
  - blocker
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
BLOCCANTE ritrovato dal laptop (21/09 notte, A/B 637 braccio A): sotto Timefold 2.6.0 il wiring MT-LS di casa (TASK-480/543) rompe a RUNTIME in B-TIMESLOT dopo ~3 min: 'MT-LS evaluation: channel 5 did not accept the evaluation in 5000ms (full or diverged). Runners alive: true' - la cura anti-stallo 543 fa il suo lavoro e fallisce loud. Costruzione/promozione/cadence-pace regolari (banner verificati); un runner era fermo DENTRO childScoreDirector.executeTemporaryMove (MoveThreadRunner:118). SOSPETTI (ordinati): (1) PR 2596 'restore working-solution score after undo' tocca MoveDirector/RecordedUndoMove = il percorso esatto dove il runner sta; (2) fairness iterator (BiasedRandomUnionMoveIterator/FilteringMoveSelector/SelectedCountLimitMoveSelector) e RandomUtils cambiati; (3) DefaultLocalSearchPhaseFactory +30/-22. API di superficie IDENTICHE fra i tag (verificato: decider field sopravvive, executeTemporaryMove/MoveDirector bytecode testa-identica): il contratto rotto e' comportamentale negli internals. INTERIM ADOTTATO (stasera): A/B 637 con -Dplanner.multithreaded-ls.enabled=false ENTRAMBI i bracci (fair, config misurata TASK-480). Log completo: task637-results/mtls-failure-2.6.0.log (committuto dal laptop 0460b310).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Riproduzione locale controllata: banco con MT-LS attivo su 2.6.0 che arrivi allo stallo + jstack dei runner al momento dell'offerLoud (dove e' fermi il canale pieno: executeTemporaryMove? iterator? barrier?)
- [ ] #2 Causa radicale identificata nel diff 2.3.0->2.6.0 (sospetti: RecordedUndoMove/PR 2596 nel percorso executeTemporary; fairness iterator mosse; RandomUtils; NeighborhoodsBasedMoveRepository) e curata nel wiring o nel nostro codice
- [ ] #3 MT-LS su 2.6.0 completa un banco replay completo (LsVarianceBenchTest n>=2) senza IllegalStateException e senza stalli silenziosi
- [ ] #4 Suite byteman (-Pbyteman) verde sulle regole di guardia esistenti
- [ ] #5 A/B parita' MT-LS on/off su 2.6.0 (wall e qualita' in banda storica) prima di dichiarare il port completo
<!-- AC:END -->

## NOTA revapi (domanda maintainer 21/09 notte)

Revapi (che Timefold usa in CI) confronta la superficie API: NON vede questo break (firme identiche verificate via bytecode; il guaio e' comportamentale: undo-score, fairness iterator, timing canali). Utile comunque come CONTROLLO INCROCIATO della parte API del port: leggere il loro report revapi 2.3.0->2.6.0 (se pubblicato nel repo/CI) e spuntare ogni voce che tocca i simboli del nostro wiring (DefaultLocalSearchPhase, LocalSearchDecider, MoveDirector, RecordedUndoMove, AbstractSolver, iterator fairness, RandomUtils) completa la mappa dei cambiamenti ufficiali accanto al nostro diff a campione (l'agente ha diff-ato le classi specifiche, non tutti i 300 file).

## AC#1 SODDISFATTA (21/09 notte, riproduzione Fedora + thread-dump della cura 543)

Ripro controllata: LsVarianceBenchTest n=1 dal checkpoint catalogato integrato-6/attempt1 su 2.6.0, stall in ~6 min di ciclo (B-TIMESLOT MT-LS). EVIDENZA DECISIVA (sezione Stack Trace della cura, nel log committato): gli 8 runner timefold-move-thread sono TUTTI nella barrier doppia ma SPLITTATI fra la prima await (MoveThreadRunner.run:109) e la seconda (run:110): il protocollo step-chasing ha perso la parita' dei trip della CyclicBarrier (qualche thread ha svolto un numero diverso di await: la barrier non trip-parta mai, i canali restano pieni, offerLoud scatta a 5s). Il decider era in awaitChannelsDrained. NOTA: il primo sospetto (PR 2596/executeTemporaryMove) e' DEAUMENTATO: nessun runner e' dentro executeTemporaryMove allo stallo (il primo stack visto dal laptop era un runner catturato LI' per caso nel print parziale). La causa e' il PROTOCOLLO nostro esposto al diverso stepping/cadence della 2.6.0 (fairness/factory cambiati). DIREZIONE CURA: rendere il protocollo immune al desync (trip-contatore per thread con realign a timeout, o barrier sostituita da broadcast-epoch idoneo), non inseguire il timing engine. Artifact: task639-results/repro-failover-2.6.0.log.

## RIPRO con cura v2 (22/09 mattina): QUADRO RIBALTATO

Stallo a 8 min in MT-LS con la STESSA firma offerLoud, ma gli stack dicono altro: TUTTI i runner al punto-consumo (poll canale, MoveThreadRunner:64, affamati), NESSUNO in executeTemporaryMove o alla barrier (il protocollo della cura regge: byteman 2/2 + questa stampa). Il DECIDER e' dentro FieldAccessingSolutionCloner.cloneSolution (deep-clone 12.5k lezioni: registrazione best di fine-step, COSTOSA). Firma "canale pieno + runner che polla vuoto" = DIVERGENZA DI GENERAZIONE dei canali (la causa-root 543 in forma nuova: un decider che offera su canali di una fase precedente) OPPURE interazione clone-storm/dispatch. SETACCIO BUG-TRACKER (agente, enumerazione completa): NESSUN hang/OOM mai riportato upstream; PR 2642 (allocazioni shadow 40->10 GB/s, in rc-1), 2649 (riduce overhead executeTemporaryMove, rc-1), 2660 (+17% moves/sec, rc-1): la rc-1 cura entrambi i sospetti. IPOTESI UNIFICANTE (dubbio di maintainer fondato): throughput collapse da tempesta di allocazioni, non lock. PROSSIMA DIAGNOSI (se serve): identityHashCode canali a offer vs poll + jstack DENTRO la finestra 5s. MA PRIMA: esperimento rc-1 (pom 2.7.0-rc-1, stessa ripro): se passa, chiude 639+640 insieme e da' le Neighborhoods.

## SINTESI DEI TRE SETACCI (22/09 mattina, dubbio di maintainer: "esiste davvero la regressione?")

Risposta: UN MECCANISMO REALE ESISTE ma NON e' nella nostra via (setaccio commit-diff completo, 108 commit):
la nostra wrapper path e' byte-identical (DefaultLocalSearchPhase, LocalSearchDecider,
EphemeralMoveDirector, VariableChangeRecordingScoreDirector; PR 2596 = 2 accessi campo).
TOP SUSPECT: la macchina DEFERRED-SETTLE dei filtering-join bavet (#2461 2.4.0 ->
4f9bec3 2.5.0 -> #2576 2.6.0: riscritta TRE volte nella finestra, codice nuovissimo
che gira a OGNI calculateScore di OGNI runner, esattamente sui nostri join H1/H2/H3/M-LA
con joiner equal+lessThan e membership toggle null<->slot in B-TIMESLOT). Meccanismi:
(a) lista intrusiva ciclica -> walk infinito in settle/TupleList.clear = il runner
dentro executeTemporaryMove (il primo stack laptop); (b) tuple out mai ritratte ->
ritenzione illimitata = OOM a qualunque heap. Aggravante: +3 slot store per lato per
ogni nodo filtering x ~10 nodi x 7k lezioni x 8 director. EVIDENZA di classe-bug VIVA
nella finestra: la famiglia TupleList.add next-reset e i TRE fix consecutivi 'stale
tuples'. AUDIT NOSTRO del delta semantico PR 2596 (campo score working solution):
PULITO, leggiamo solo stepScope/moveScope. ESSESPERIMENTI DISCRIMINANTI (in coda):
(1) ripro su 2.7.0-rc-1 (IN VILO: le cure rc-1 sono perf/shadow, NON bavet: se
suspect 1 e' giusto, rc-1 potrebbe NON chiudere l'hang ma alleviare l'OOM);
(2) BISECT per tag: 2.5.0 e 2.4.0 (stall gia' presente -> suspect 1 confermato;
solo 2.6.0 -> #2576 finale); (3) jstack del runner parcheggiato DENTRO la finestra;
(4) heap histo mid-phase. NOTA rc-1 sweep: MoveIteratorProvider = ponte first-class
per il 638; SolutionView strutturale (no score/match: lo scope va dal nostro
bookkeeping); ordine iteratore MAI garantito (il nostro steepest ordina dentro);
API preview gia' rinominata 2.6->rc1 (instabilita' dichiarata).

## BISECT A META' (22/09 12:30, REPORT.md del laptop)

rc-1 COMPLETA (6818s, -12/0 boxed 1); 2.5.0 COMPLETA (9470s, -3/0 boxed 0); 2.6.0 STALL+OOM (9 min). LA FINESTRA DI REGRESSIONE E' ESATTAMENTE 2.5.0 -> 2.6.0. Nel delta: #2576 (riscrittura finale bavet filtering-join deferred-settle = PRIME SUSPECT per eliminazione: #2596 assolta byte-level, #2582 inerte per noi). Tutti i bracci con la cura barrier attiva e MT-LS ON: la cura regge su tutte le versioni sane. IN CORSA: 2.4.0 (profilo storico) poi MAIN (clone+build locale: il braccio obbligatorio per la proposta upstream, ordine maintainer). Se MAIN stalla: issue upstream con i numeri nostri (bisect + stack + quale meccanismo). Se MAIN completa: la 2.6.0 resta il ramo malato e rc-1 e' la destinazione.

## CAUTELA STATISTICA (obiezione di maintainer 22/09: n=1 + indeterminismo MT non dimostra nulla)

Giusto: banco NON_REPRODUCIBLE, 8 thread, stallo probabilistico (il gemello upstream 2666 si dichiarava ~1/8 run). Conteggio onesto: 2.6.0 = 5 stall osservati vs 1 fase pulita; 2.5.0 e rc-1 = 3 fasi pulite ciascuno, 0 stall. Se avessero p=0.7 come il empirico 2.6.0, 3 fasi pulite di fila = ~2-3%: SUGGESTIVO non PROBANTE. Il bisect si legge "coerente con la finestra 2.5.0->2.6.0" finche': (A) ripetizioni per tasso (2 extra di 2.5.0, poi rc-1: mandate al laptop), (B) braccio-5 meccanico su QUALSIASI stall (jstacktriplo: frame in settleLayer/TupleList = evidenza indipendente dal tasso), (C) MAIN con la stessa cautela. Emendamento inviato al laptop.


## CHIUSURA (maintainer 22/09: "abbandonare l'indagine, tenere gli occhi aperti")

Indagine CHIUSA: la destinazione e' 2.7.0-rc-1 (completa il ciclo) e la root-cause della sola 2.6.0 non serve. RESTANO VALIDI E COMMITTATI: la cura barrier (decider parte N+1, trip espliciti: chiude una finestra VERA del nostro protocollo, utile su ogni versione) con regression byteman 2/2, il banco ripro, i tre setacci (colpevole per eliminazione #2576, finestra 2.5.0->2.6.0) e la cautela statistica. RIAPERTURA SOLO se la firma (offerLoud 5s + runner in executeTemporaryMove/clone) ricompare RIPRODUCIBILE su rc-1+; memoria stall-2060-firma-occhi-aperti con firma e percorso. Gates residui sulla cura (simplify/code-review/suite completa) passano nel giro di adozione rc-1.
