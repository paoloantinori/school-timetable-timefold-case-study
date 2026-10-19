# Research Report: Nearby selection / move filtering. Alternative implementations and missing pieces (TASK-499 post-mortem)

**Date**: 2026-08-15
**Depth**: exhaustive
**Confidence**: HIGH (mechanism), MEDIUM (transferability to our domain)

## Executive Summary

Il nostro TASK-499 (filtro a gradi hard su SelectionFilter) e' implementato correttamente ma e' il **meccanismo meno sofisticato** della famiglia: l'Enterprise usa un **bias probabilistico su distribuzione** (non un filtro), che preserva l'escape per costruzione; la letteratura timetabling vincente (ITC 2007/2011/2019) **non usa nearby selection**: usa **multi-neighborhood random selection con fasi multiple** (UniTime: IFS+HC+GD/SA con 4-6 neighborhood a probabilita' uguali) o **ALNS destroy-repair** (Lectio: 5 rimozioni + 3 inserimenti adattivi). Il nostro ritrovato (guadagno wall non replicabile = varianza di traiettoria) e' **coerente** con l'evidenza Enterprise: nearby selection e' documentata come leva di **scalabilita' su n grande** (VRP 2000+), non come leva di wall su problemi con value-range modeste come il nostro (1368 timeslot: gia' gestibile dal forager). I pezzi che mancano davvero: estensione a swap/pillar (5/11 dello stream oggi) e un bias vero invece del filtro.

## Findings

### 1. Il meccanismo Enterprise e' un BIAS su distribuzione, non un filtro (HIGH)

OptaPlanner/Timefold Enterprise nearby selection (`NearEntityNearbyValueSelector` + `NearbyDistanceMatrix` + `NearbyRandom`):
- Costruisce una **matrice delle distanze** precomputata (origin x destination, ordinata per vicinanza) in phaseStarted
- Il value selector **campiona dalla distribuzione**, non filtra: `PARABOLIC_DISTRIBUTION` (default, i piu' vicini piu' probabili), `LINEAR_DISTRIBUTION`, `BLOCK_DISTRIBUTION` (solo gli n piu' vicini, probabilita' uguale = il piu' simile al nostro grado-0), `BETA_DISTRIBUTION` (documentata come piu' lenta)
- **Ogni elemento resta selezionabile** (parabolic senza distributionSizeMaximum): l'escape non e' mai bloccato per costruzione. Il nostro keep-probability 0.20 sul grado-3 e' un'approssimazione di questo principio, ma i gradi 0-2 sono filtri HARD: un movimento weekday-crossing fuori dal grado-1 non e' mai generato, mentre nell'Enterprise sarebbe solo improbabile.
- La nearby selection Enterprise e' esplicitamente **disabilitata nei CH** ("would only result in unnecessary costs involving the generation of the distance matrix and sorting"): coerente col nostro wiring solo-LS.

Fonti: docs Timefold move-selector-reference (sez 1.6), blog OptaPlanner 2015 (De Smet), sorgente `NearEntityNearbyValueSelector` (javatips mirror di optaplanner-master), issue TimefoldAI#837.

### 2. Il claim Enterprise e' SCALABILITA' su n grande, non wall (HIGH)

Il benchmark di riferimento (blog 2015): VRP con **2750 locations**, parabolic su 40 piu' vicini. Il problema motiva la feature: 2000 locations -> 4.000.000 mosse per singolo cambio -> giorni di solve. Il claim: "heavily increase scalability". Il nostro caso: 1368 timeslot per lezione, ~100k eval/sec misurate: la matrice completa e' gia' nel budget del forager. La nearby selection Enterprise nasce per domini dove il value range esplode (TSP/VRP geografici); il nostro wall non sta nel volume delle mosse ma nel costo per join (H1/H3, confermato dal profiler). La mancata replica del guadagno non e' un fallimento nostro: e' il risultato atteso trasferendo una leva di scalabilita'-n in un dominio dove n non e' il collo.

Fonti: blog javacodegeeks/OptaPlanner 2015 (numerical example), docs Timefold ("scale out to huge datasets").

### 3. I vincenti timetabling NON usano nearby selection (HIGH)

- **UniTime (finalista ITC 2007, ITC 2019)**: Iterative Forward Search + Hill Climbing + Great Deluge (+SA opzionale), con **4-6 neighborhood problem-specific selezionate a probabilita' fissa** (Period/Room/Event/Student/Exam change/swap). Nessun filtro per vicinanza: la restrizione e' per TIPOLOGIA di mossa, non per distanza.
- **GOAL (vincitore ITC 2011)**: SA + Iterated Local Search su **7 neighborhood structures**. Nessuna nearby.
- **Lectio (2 ITC 2011, il piu' rilevante per noi: scuola superiore danese)**: **ALNS** con 5 operatori di rimozione (Random, Related/Shaw, Time, Class, Room) e 3 di inserimento (Greedy, Regret-N, Worst), **pesi adattivi** per operatore basati sulla performance recente.
- **Demirovic-Musliu (MaxSAT-LNS, XHSTT)**: destroy su 2 vettori di neighborhood (risorse-paia, giorni) con visita equa + skip di neighborhood gia' esplorate senza successo.
- **ITC2021 sports (Rosati et al.)**: SA su portfolio di **6 neighborhood**.

Il pattern dominante: **diversificazione per struttura di neighborhood + adattivita' dei pesi**, non restringimento geografico. Il nostro solver ha gia' una versione di questo pattern: l'union move selector (change/swap/pillar, 11 stream) con la fase B-TIMESLOT/B-ROOM separata. La direzione letteratura suggerirebbe piuttosto pesi adattivi tra i nostri 11 stream che un filtro per vicinanza.

Fonti: Muller ITC2007 paper (muller.unitime.org/itc2007.pdf), Sorensen-Stidsen (orbit.dtu.dk PDF), Demirovic-Musliu (dbai.tuwien.ac.at PDF), Rosati et al. PATAT2020, Fonseca et al. GOAL.

### 4. Incremental/delta scoring: gia' lo abbiamo, il collo e' altrove (HIGH)

Timefold constraint streams SONO incremental scoring. I trick di performance che restano applicabili (dai docs ufficiali): filtri PRIMA dei join (ridurre il cross-product), **Joiners indicizzati invece di filtri dopo il join** (quando una lezione cambia, solo le pair con lo stesso teacher vengono rivalutate), custom shadow variables per precomputare aggregati. Il nostro profiler E1 ha appena confermato: nessun vincolo domina (~14% il top: H1/H3/H28), il costo vive nei join per docente/gruppo che SONO il modello. H28 e' l'unico candidato shadow-variable concreto (join a tre).

Fonti: docs Timefold performance (filters-before-joins, joiners-over-filters), nostro E1 profiler re-run (commit e5672334).

### 5. Il nostro ritrovato (varianza di traiettoria) e' coerente con la letteratura (MEDIUM)

Non esiste uno studio pubblicato "nearby selection su timetabling con wall non replicabile" (la feature e' Enterprise, i benchmark pubblicati sono VRP dove scala). Per inferenza: (a) il blog Enterprise raccomanda parabolic proprio perche' le alternative piu' aggressive (block) sacrifichino qualita'; il nostro filtro hard e' piu' vicino a block; (b) la traiettoria del nostro LS e' NON_REPRODUCIBLE per dichiarazione: un filtro che cambia la sequenza di mosse generate cambia il seed della traiettoria, quindi run1 -33% e run2 +45% sono entrambi campioni dalla stessa distribuzione di varianza, esattamente come misurato. La letteratura ALNS supporta l'interpretazione: i guadagni stabili vengono dall'adattivita' dei pesi (che apprende nel run), non dal restringimento statico.

### 6. Pezzi mancanti nel nostro TASK-499 (il gap concreto)

1. **Copertura stream**: il filtro copre solo i 5 change-move stream dell'union (5/11): swap/pillar non sono filtrati. L'Enterprise agisce a livello di ValueSelector (sotto ogni move selector), coprendo tutto per costruzione.
2. **Bias vs filtro**: nessun grado distribuito; l'Enterprise ha 4 distribuzioni configurabili. Il nostro keep-probability 0.20 e' l'unico meccanismo probabilistico.
3. **Matrice di distanza**: noi non precomputiamo (il filtro valuta per-mossa con predicati); l'Enterprise ordina una volta per fase. Per 1368 slot x 12k lezioni una matrice e' ~16M celle: probabilmente il motivo per cui non e' adatta a noi comunque.
4. **Adattivita'**: nessuna. La direzione ALNS (pesi per stream che apprendono) e' l'alternativa con piu' supporto dalla letteratura per il nostro dominio.

## Confidence Assessment

| claim | confidence | base |
|-------|-----------|------|
| Enterprise = bias su distribuzione, non filtro | HIGH | docs ufficiali + sorgente |
| Nearby Enterprise e' leva di scalabilita'-n, non wall | HIGH | benchmark ufficiale 2750 locations + claim docs |
| I timetabling vincenti non usano nearby | HIGH | 4 paper vincitori/finalisti letti (ITC2007/2011/2019/2021) |
| La nostra non-replica e' coerente | MEDIUM | inferenza da NON_REPRODUCIBLE + letteratura ALNS (nessuno studio diretto) |
| Swap/pillar non coperti (5/11) | HIGH | nostro codice (design doc TASK-499) |

## Sources

1. Timefold docs, Move Selector Reference sez 1.6 nearby selection: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference
2. OptaPlanner blog (De Smet) 2015, Scaling VRP with Nearby Selection: https://www.javacodegeeks.com/2015/02/optaplanner-scaling-vehicle-routing-with-nearby-selection.html (+ originale optaplanner.org/blog/2015/01/27)
3. NearEntityNearbyValueSelector source (optaplanner-master mirror): https://www.javatips.net/api/optaplanner-master/optaplanner-core/src/main/java/org/optaplanner/core/impl/heuristic/selector/value/nearby/NearEntityNearbyValueSelector.java
4. TimefoldAI issue #837 Dynamic nearby: https://github.com/TimefoldAI/timefold-solver/issues/837
5. Muller, ITC 2007 solver paper: https://muller.unitime.org/itc2007.pdf
6. Sorensen & Stidsen, High School Timetabling ALNS (Lectio): https://backend.orbit.dtu.dk/ws/files/52417410/High_School_Timetabling.pdf
7. Demirovic & Musliu, MaxSAT-Based LNS for HSTT: https://dbai.tuwien.ac.at/user/demir/papers/MaxSAT%20Based%20Large%20Neighborhood%20Search%20for%20High%20School%20Timetabling-%20Demirovi%C4%87,%20Musliu.pdf
8. Timefold docs, Performance tips: https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance
9. OptaPlanner multithreaded blog 2018 (contesto: nearby e MT sono leve Enterprise complementari): https://www.optaplanner.org/blog/2018/07/03/AGiantLeapForwardWithMultithreadedIncrementalSolving.html
10. Fonseca et al., GOAL solver ITC 2011: https://www.repositorio.ufop.br/items/8805c0d0-d09e-495f-a29d-0aef8dd4d8b1
11. Rosati et al., Multi-Neighborhood SA ITC2021: https://patatconference.org/patat2020/proceedings/papers/41.ITC2021_paper7.pdf
