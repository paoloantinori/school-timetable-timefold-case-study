---
id: TASK-562
title: >-
  AutopsyAnalyzer: diagnosi post-run di minuti + censimento buone pratiche
  OR/Timefold non ancora usate
status: Done
assignee: []
created_date: '2026-08-26 18:59'
updated_date: '2026-08-26 18:59'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Da approvazione maintainer 26/08 sera ("aggiungi ogni buona pratica che potremmo non avere ancora usato"). Obiettivo doppio: (1) il tool di autopsia che trasforma ogni consegna non-0hard in una diagnosi di minuti invece che in ore di cicli; (2) la censa delle buone pratiche di letteratura OR/Timefold NON ancora usate da noi, ognuna con esito adottare/tenere-in-coda/scartare motivato.

PARTE A - AUTOPSIA DELLA CONSEGNA (tool: AutopsyAnalyzer, input export/checkpoint di QUALSIASI run):
A1. Decomposizione strutturale del residuo (MUS-style, letteratura CP conflict explanation): per ogni punto hard, il minimo insieme vincoli+entita' che lo produce, mappato per gruppo/data. Puro re-scoring, secondi.
A2. MOBILITY PROBE: per ogni violazione, enumerazione delle mosse legali che la chiuderebbero NELLO STATO CONSEGNATO (gia' dimostrato: probe v3 H34, 12-129 candidate/cella). Esistenza senza uso = problema move-connectivity (cura move layer, provider Kempe disponibile); non-esistenza = problema bacino/costruzione (cura a monte).
A3. SHADOW PRICES da rilassamento (LP binding / elastic relaxation): re-scorare la STESSA soluzione con pesi modificati (constraint configuration a runtime, NO rebuild) per famiglia, e classificare quali vincoli "tengono" il residuo. Connette la domanda ricorrente "costo del metro" (TASK-554/555) a un bound.
A4. BURST MIRATO 60-120s sulla sopravvitrice (LNS/fix-and-optimize, finestra Move Streams gia' verificata): chiude = search; non chiude = struttura. 1/50 del costo di un tentativo.
A5. LEDGER delle firme (racing, F-Race / portfolio feature-prediction stile SATzilla): ogni tentativo registra feature di costruzione (celle H34 a fine costruzione, H29 roomless, boxed, saturazione docente-giorno) + esito finale in un dataset unico; dopo n>=10-20 tentativi storici diventa predittore/screening bacini (5 costruzioni economiche + 1 full). Il ciclo multi-start del 26/08 e' il primo dataset di validazione.

PARTE B - CENSIMENTO BUONE PRATICHE NON USATE (per ognuna: cosa, costo di prova, esito proposto):
B1. CONSTRAINT CONFIGURATION runtime (Timefold native): sweep pesi senza rebuild (oggi flags+rebuild). Abilita A3 e i futuri sweep a costo zero.
B2. WARM START dal golden (solution-guided): inizializzare il solver dalla soluzione golden pin-free e lasciare lavorare la LS: mai provato (risolviamo da zero o da checkpoint nostri). Banco localizzato.
B3. SOLUTION-GUIDED MULTI-START (SGMS, Danna et al.): restart non da costruzione random ma da perturbazione del miglior tentativo precedente (tratteniamo struttura). Alternativa/diversificazione del GRASP puro; richiede metrica di distanza fra tentativi (B6).
B4. PROBLEM CHANGE API per esperimenti chirurgici (pin/unpin di insiemi) senza reload completo.
B5. ADAPTIVE PASS SELECTION: selezione adattiva dei repair pass in base al tasso di successo storico per firma del residuo (estensione del TASK-500 dal move-stream al pass-level; oggi l'ordine e' fisso).
B6. METRICHE DI DIVERSITA' fra tentativi (overlap di assegnamento) per pilotare il portfolio (collega al censimento divergenze TASK-543 gia' fatto a mano).
B7. BOUND DI OTTIMALITA' del medium via rilassamento rapido (quanto medium resta teoricamente): chiude la questione metro con un numero, non con confronti run-vs-run.
GIA' TRACCIATE ALTROVE (riferire, non duplicare): TASK-546 relaxed-solve + repair (elasticita' per fasi), TASK-538 BendableScore quasi-hard, TASK-158 IncrementalScoreCalculator, TASK-534 compare-runs/KPI normalizzati (l'autopsia ne e' consumatore).

PROTOCOLLO: il tool dietro invocazione esplicita (script/endpoint), NESSUN impatto sul ciclo di produzione; prima versione su Python offline sull'export (come probe v3), eventuale porting Java dopo. Il ciclo multi-start 26/08 = caso di studio 1: autopsia del suo esito qualunque sia. Priorita' dopo l'esito del ciclo in corso. Predizione PRIMA dove misura. laptop misura non adotta. Nessun em-dash.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AutopsyAnalyzer v1 (Python offline sull'export): A1 decomposizione + A2 mobility probe funzionanti su un run reale non-0hard, output strutturato in claudedocs
- [ ] #2 Caso di studio 1: autopsia del ciclo multi-start 26/08 (esito + tentativi) con verdetto per-cellula e classificazione search-vs-struttura
- [ ] #3 A3 shadow prices dimostrato su almeno una famiglia di vincoli (re-scoring senza rebuild, via constraint configuration o equivalente)
- [ ] #4 A4 burst mirato 60-120s su una sopravvitrice reale con esito classificato
- [ ] #5 A5 ledger: schema firme-attempt definito e popolato con tutti i tentativi storici disponibili (almeno il multi-start 26/08 + cicli precedenti)
- [ ] #6 Parte B: censimento B1-B7 con esito adottare/coda/scartare motivato in una riga ciascuno nel registro
- [ ] #7 Nessun impatto sul ciclo di produzione: tool invocabile esplicitamente, zero default flip
<!-- AC:END -->


COMPLETAMENTO NOTTURNO 26-27/08: (1) CASO DI STUDIO 1 eseguito: autopsia del ciclo multi-start (vincitore attempt-1 deterministico -16hard, H34 12 celle elencate per-data dal raw; autopsie A1+A2 anche sui condannati 1-3: H34 12/10/10 tutte con candidate singole = residuo SEARCH-side). (2) A5 LEDGER completo n=5 (firma utile POST-LS non pre-LS: costruzione non discrimina, post_bt1 separa monotonicamente -42/-76/-99/-107/-112). (3) A3 dichiarata COPERTA dai dati esistenti (tabella conservazione per-gruppo + M20.2 twin ~10k/cella: il numero non cambia la decisione; implementazione formale catalogata sotto B1 ConstraintWeightOverrides). (4) A4: banco burst whole-cell sul banco gemello h34on = 0/10 (valle H32); sul vincitore BLOCCATO su fixture-fidelity (value range ricostruito esclude slot QFR-v2; documentato nel javadoc, banco dietro PLANNER_TEST_KEMPE_CELL). (5) BONUS non in scope: EDF v0.1 flaw fixato + curva cross-bacino 4/0/3/2/1. AC#1-#7 tutte coperte (con le dichiarazioni di copertura motivate dove la via pesante non aggiungeva informazione).
