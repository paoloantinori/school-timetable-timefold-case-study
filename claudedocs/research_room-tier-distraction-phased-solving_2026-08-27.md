# Research Report: Room-tier distraction nel solving a fasi (stanze assegnate solo alla fine)

**Date**: 2026-08-27
**Depth**: exhaustive
**Confidence**: HIGH sui fatti interni (codice + ledger misurati), HIGH sulla meccanica Timefold (docs + sorgente 2.3.0), MEDIUM-HIGH sulle ancore di letteratura (classici, abstract letti)

## Executive Summary

L'ipotesi di maintainer e' **PARZIALMENTE REALE e gia' dimezzata**: i gate del ciclo staged (pick-best, skip-doomed, soglia B-TIMESLOT) ignorano gia' il room-tier (`ROOM_TIER_HARD_FAMILIES`), ma **lo score che la LS di B-TIMESLOT ottimizza lo contiene ancora integralmente**, e nel regime attuale quella massa e' ~99% del hard visibile (-4045 room-tier vs -27 di segnale timeslot allo stesso confine, misurato nel ledger). La cura proposta (accendere i vincoli stanza solo alla fine via `ConstraintWeightOverrides`) e' fattibile per-stadio senza rischi mid-solve, ha ancore dirette in letteratura (GLS, strategic oscillation, two-stage decomposition ITC) con UNA cautela documentata dai vincenti ITC: non spegnere anche i proxy di roomability, o la fase stanze rischia di non chiudere.

## Findings

### 1. Stato interno: cosa e' gia' decoupled e cosa no

- **Gate decoupled (gia' fatto)**: `ScheduleService.ROOM_TIER_HARD_FAMILIES = [H2, H-M2, H29]` (ScheduleService.java:875); `timeslotTierHard()` (:3160) filtra quelle famiglie da pick-best a due livelli, skip-doomed-room e soglia di stage. Il commento a :3120-3140 lo dichiara apertamente: giudicare il template congelato sul room-tier nasconderebbe il residuo timeslot sotto un numero due ordini di grandezza piu' grande.
- **Score della LS NON decoupled**: ogni stage gira su un `SolverConfig` ad-hoc per le FASI (runStagedSolver, :1549) ma il constraint provider e' unico: B-TIMESLOT ottimizza lo score pieno, room-tier compreso. Nell'era TASK-446 il full-score al confine timeslot era ~-31 (coerente col tier: le room constraints erano fuori dal build); oggi il ledger misura **-4045 pieno vs -27 tier allo stesso confine** (commit e78def9e, nota "room-tier variabile per era"). La massa e' tornata nell'era corrente: costruzione con stanze provvisorie/null + H29 promosso hard.
- **Scomposizione della massa**: a fine costruzione il registro notturno ha decomposto ~-13k in H29 roomless ~-10.4k + H0 boxed x2 + H34 19 celle. In B-TIMESLOT: H29 e' ~costante (le mosse timeslot non toccano le stanze: offset inerte per i delta degli accettatori, ma inquina i confronti assoluti); **la parte VARIABILE e' H2 provvisorio + M3/M-LF/M-TC/M37/M-LA**, che le mosse timeslot spostano pseudo-casualmente: quella e' la distrazione vera, piu' il medium (-1.3M con termini room che B-ROOM risolvera').

### 2. Perche' questo spiega (in parte) le metauristiche fallite

Le metauristiche esauste (08-05, tabu/late-acceptance/ACL, post-446) valutavano candidate su uno score dove ~99% dell'hard era massa non sistemabile in quella fase: il gradiente utile (timeslot-tier) era sepolto. Late-acceptance e tabu confrontano DELTA (la costante H29 si cancella), ma la parte variabile room-tier entra nei delta come rumore correlato alle mosse timeslot: rapporto segnale/rumore degradato. Precedente interno convergente: l'emendamento corner-b del 21/08 esisto' proprio perche' il pick su score pieno sceglieva sempre l'ultimo (room-tier sommergeva il confronto). Nota di onesta': non e' l'unica causa (il campaign-status aveva gia' identificato l'esaurimento della LS come strutturale); e' un fattore di confondimento mai rimosso dai benchmark di quell'epoca.

### 3. La cura runtime: ConstraintWeightOverrides per-stadio

- Meccanica verificata (docs 2.5 + sorgente 2.3.0 presente: `ConstraintWeightOverrides.java` nel sources-jar estratto sul laptop): campo sul @PlanningSolution, popolato PRIMA del solve; pesi per nome di constraint, incluso ZERO. Il pattern per-stadio e' naturale nel nostro ciclo: si setta l'override sul problema prima di ogni `runStagedSolver` (zero su H2/H-M2/H29/M3/M-LF/M37/M-TC nelle fasi timeslot, pieno da B-ROOM in poi). **Nessuna mutazione mid-solve richiesta** (il caso "routine update" era un bug segnalato, issue #1131; la via sana e' per-solve; l'issue #1172 documenta l'uso dinamico come possibile ma tedioso).
- Rischi da gate: (a) i tie-break su score pieno cambiano significato tra stadi (gia' true oggi per il tier: da riconciliare col metro del predittore A5); (b) snapshot/analysis con pesi zero riportano zero per quelle famiglie (le autopsie vanno consapevoli dell'era dei pesi); (c) lo stream continua a calcolare i match (costo marginale: si guadagna la segnalazione, non la perf del constraint rimosso).

### 4. Letteratura (ancore)

- **Guided Local Search** (Voudouris/Tsang): aggiustare penalita' di constraint DURANTE la ricerca e' meccanica classica collaudata; il nostro schema per-stadio ne e' la versione deterministica.
- **Strategic oscillation** (Glover 1989; Wei-Hao-Glover EJOR 2023 "responsive SO"): oscillare intorno al confine ammissibile/non e' la cornice teorica per attivare/disattivare regioni di vincoli in fasi diverse.
- **Two-stage decomposition timetabling** (vincitore ITC2011, DTU "Timetabling at High Schools"; PATAT 2022 Abou Kasm et al.): la decomposizione timeslot-prima/stanze-poi e' la prassi vincitrice, CON la cautela chiave: i room-cuts implementati nel modello di assegnazione timeslot garantivano la fattibilita' del modello stanze. I vincitori NON ignorano le stanze nella fase 1: ci mettono tagli di fattibilita'. Tradotto per noi: **non azzerare anche i proxy di roomability** (famiglia tag-scarcity M-TC, H-COPRESENCE): azzerare solo cioe' che B-ROOM risolve comunque (H2 pairwise provvisorio, M3, M-LF, M37).

### 5. Verdetto sull'ipotesi

**Distrazione reale, gia' mitigata nei gate, NON mitigata nello score della LS**: la LS di B-TIMESLOT lavora con ~99% di hard-massa non sistemabile e termini room nel medium. Non e' la causa unica dei fallimenti metaeuristici, ma e' un confondimento mai rimosso e ora rimovibile a costo quasi zero.

### 6. Disegno sperimentale (zero nuovi cicli per la diagnosi)

- **E1 (minuti, autopsia A3-style)**: re-scorare i checkpoint esistenti (POST_BT1/POST_RELEASE del ciclo multistart e h34on) con override room-zero vs pieno: quantifica la massa variabile room-tier per stadio (predizione: hard visibile in B-TIMESLOT da ~-4000 a ~-30, medium -1.3M decresce di una frazione room da misurare).
- **E2 (un banco localizzato, ~10 min)**: resume dal POST_BT1 con finestra LS 120s in due braccia appaiate (room-zero vs pieno): confronto del tasso di miglioramento del SOLO timeslot-tier. Se room-zero migliora il tier piu' velocemente a parita' di wall: distrazione confermata CAUSALMENTE (pattern A4-burst, due braccia).
- **E3 (solo se E2 positivo)**: ciclo completo A/B come gate di adozione dello schema per-stadio, con la cautela roomability (proxy M-TC/H-COPRESENTE restano accesi) e la riconciliazione dei tie-break e del predittore A5 (che gia' legge il tier: coerente).

## Confidence Assessment

- Interno (gate decoupled, score non decoupled, masse -4045/-27): HIGH, da codice e ledger.
- Meccanica ConstraintWeightOverrides per-stadio in 2.3.0: HIGH (docs + classe nel sources-jar).
- Ruolo nelle metauristiche fallite: MEDIUM (confondimento plausibile e quantificato; manca il controfattuale dell'epoca, E2 lo costruisce).
- Cautela roomability dai vincenti ITC: MEDIUM-HIGH (snippet PATAT/DTU letti; PDF completo non scrapato).

## Sources

1. https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/library/constraint-overrides (meccanica overrides, campo su solution, set pre-solve)
2. https://github.com/TimefoldAI/timefold-solver/issues/1172 (uso dinamico dei pesi)
3. https://github.com/TimefoldAI/timefold-solver/issues/1131 (bug aggiornamenti ripetuti: perche' restare sul per-solve)
4. https://www.researchgate.net/publication/201976843_Partial_constraint_satisfaction_problems_and_guided_local_search (GLS)
5. https://leeds-faculty.colorado.edu/glover/fred pubs (strategic oscillation, The Case for Strategic Oscillation, ANOR)
6. https://leria-info.univ-angers.fr/~jinkao.hao/papers/WeiHaoGloverEJOR2023.pdf (responsive strategic oscillation)
7. https://backend.orbit.dtu.dk/ws/files/99520418/Timetabling_at_High_Schools.pdf (ITC2011 two-stage decomposition)
8. https://patatconference.org/patat2022/proceedings/PATAT_2022_paper_06.pdf (room-cuts nella fase timeslot)
9. Interni: ScheduleService.java:875, :3120-3200, :1549; SchoolScheduleConstraintProvider (M37/H29/H-COPRESENCE); commit e78def9e (full -4045 vs tier -27); registro notturno (decomposizione costruzione ~-13k).
