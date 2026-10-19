# Research Report: Reimplementing Timefold Enterprise Features in Community

**Date**: 2026-08-12
**Depth**: exhaustive
**Confidence**: HIGH

## Executive Summary

**Nessuno su GitHub ha reimplementato le feature Enterprise di Timefold (moveThreadCount, partitioned search, nearby selection) nella community edition.** Le uniche due alternative identificate sono: (1) il paper originale di Geoffrey De Smet (creatore di Timefold) che descrive l'algoritmo "step chasing" implementato nel codice Enterprise, e (2) SolverForge, un solver da zero in Rust (non un fork Timefold) che include multi-threaded move evaluation come feature nativa. Nessun fork della community di Timefold/OptaPlanner aggiunge il multithreading al LS.

## Findings

### 1. Nessun fork community con multithreading trovato (HIGH confidence)

Ricerche esaustive su GitHub (forks, code search, topics), Stack Overflow, blog, e forum: **zero progetti che patchano Timefold/OptaPlanner community per aggiungere moveThreadCount**. I ~107 fork di timefold-solver sono per lo più tutorial, esercizi, o personalizzazioni di dominio (nessuno tocca il core solver per il threading). La feature è sempre citata come "Enterprise only" da fonti ufficiali e community.

### 2. Il paper originale descrive l'algoritmo completo (HIGH confidence)

**"Multithreaded incremental solving for local search based metaheuristics with step chasing"** (Geoffrey De Smet, Tony Wauters, PATAT 2020). URL: `https://patatconference.org/patat2020/proceedings/papers/8.%20PATAT_2020_paper_41.pdf`

L'algoritmo ("step chasing") è descritto in dettaglio nel paper:
- **Principio**: il parent thread seleziona `b` mosse (b = 10×n child threads), le mette in una operation queue. I child thread pullano le mosse, calcolano lo score incrementale (delta), mettono il risultato in una result queue. Il parent thread riceve i risultati, applica la mossa vincente (acceptor), poi inizia il prossimo step.
- **Score director isolato per thread**: ogni child thread ha un suo `ScoreDirector` che replica lo stato del parent. Quando il parent applica una mossa vincente (doMove), trasmette la mossa ai child thread che la replicano ("step chasing") per mantenere il loro stato sincronizzato.
- **Reproducibilità**: il parent thread usa un PRNG seeded per la selezione mosse + acceptor. I child thread non usano PRNG (solo score calculation, che è deterministica). Il numero di mosse attive `b` è fisso (deterministico). Thread count fisso = run reproducibile.
- **Benchmark**: 5550 run su 4 problemi (nurse rostering, VRP, course timetabling, cloud balancing) con Tabu/SA/Late Acceptance, fino a 16 thread. Speedup significativo specialmente su nurse rostering (il più vicino al nostro dominio: timetabling).

Il paper dice esplicitamente: "implemented within OptaPlanner, a Java-based open source solver, and can thus be used by anyone". MA: questa è la versione OptaPlanner pre-fork (7.x), dove il multithreading era nel core open-source. Con il fork Timefold (2023) + ristrutturazione 2.x (2025), il multithreading è stato **spostato nell'Enterprise jar**.

### 3. L'algoritmo "step chasing" è implementabile come custom layer (MEDIUM confidence)

Dalla lettura del paper, l'algoritmo ha 3 componenti implementabili sopra Timefold community:
1. **Move evaluation parallel**: invece di usare il forager single-thread di Timefold, un wrapper Java prende `b` mosse dal move selector, le sottomette a un `ExecutorService` con `n` thread, ogni thread ha un suo `ScoreDirector` (clonato), valuta il delta. Il wrapper raccoglie i risultati e applica la mossa vincente.
2. **Step chasing**: dopo ogni step (doMove vincente), il wrapper trasmette la mossa a tutti i child ScoreDirector per mantenerli sincronizzati.
3. **Reproducibility**: PRNG seeded nel parent, thread count fisso, `b` fisso.

**Il problema**: Timefold community non espone API per creare ScoreDirector isolati per thread. `ScoreDirector` è interno (package-private). Per implementare lo step chasing sopra Timefold senza modificare il core, servirebbe o:
- (a) Reflection/accessibilità ai internals di Timefold (fragile, breaka ad ogni release).
- (b) Fork del core Timefold (copia del repo + modifica `LocalSearchDecider`/forager per aggiungere il threading).
- (c) Un Solver custom che NON usa il LS di Timefold ma implementa il proprio LS con step chasing sopra il constraint evaluation di Timefold (usando `ConstraintProvider` + score calculation, ma con un LS wrapper proprio).

### 4. SolverForge: alternativa da zero (non fork Timefold) (HIGH confidence)

**SolverForge** (`github.com/solverforge/solverforge`): un constraint solver scritto in Rust, Apache-2.0, ispirato a Timefold. Include **multi-threaded move evaluation + partitioned search come feature native community** (non Enterprise). Ha una ConstraintStream API simile a Timefold, construction heuristics, local search (Tabu/SA/LA/VND).

Differenze chiave da Timefold:
- **Linguaggio**: Rust (non Java). Integrazione con il planner Java richiederebbe un bridge (FFI/gRPC).
- **API**: simile ma non identica a Timefold (modeling macros diverse, naming diverso).
- **Maturità**: molto più giovane di Timefold (v0.19, used in production da pochi).

Non è un fork Timefold ma una **reimplementazione da zero** con le feature Enterprise gratis. Potrebbe essere un'alternativa se il multi-threading è critico, ma la migrazione del dominio (tutti i constraint, entity, VRP) sarebbe sostanziale.

### 5. OptaPlanner 7.x: l'ultima versione con multithreading community (HIGH confidence)

Il multithreading era nel core di OptaPlanner (pre-Timefold fork, 7.x). Il blog post "A giant leap forward with multithreaded incremental solving" (2018) mostra `<moveThreadCount>` funzionante in OptaPlanner open-source. Con il fork Timefold (2023) + ristrutturazione editions (2.x, 2025), il multithreading è stato **estratto dal core e spostato in Enterprise**.

Quindi teoricamente si potrebbe usare OptaPlanner 7.x (ultima release 8.x, che è la stessa codebase prima del fork) per avere il multithreading. MA:
- OptaPlanner 8.x è EOL (Red Hat non lo mantiene più).
- La API è compatibile ma non identica a Timefold 2.x (ci sono state refactor).
- Nessun fix recente (bug, security, performance).

### 6. Struttura del codice Enterprise in Timefold (MEDIUM confidence)

Dal jar analysis del code-review agent: `timefold-solver-core-2.3.0.jar` contiene l'enum `ChildThreadType` (che definisce i tipi di thread) ma nessun codice che lo consuma per spawnare thread. L'implementazione del multithreading è nel jar Enterprise (`timefold-solver-enterprise-core`), che è closed-source e richiede licenza.

La struttura probabile (dal paper + dalla codebase):
- `DefaultSolver` → `LocalSearchPhase` → `LocalSearchDecider` → forager
- L'Enterprise jar sostituisce il forager con uno multi-threaded (`MultiThreadedLocalSearchForager` o simile)
- Il forager usa un `ExecutorService` + code queue (operation/result)
- Ogni child thread ha un `ScoreDirector` clonato

Per un fork community, il punto di modifica è il **forager**: sostituire `AcceptedLocalSearchForager` (single-thread) con un forager multi-thread custom che implementa lo step chasing.

## Confidence Assessment

| claim | confidence | fonte |
|-------|-----------|-------|
| Nessun fork community con multithreading esiste | HIGH | 15+ search su GitHub/SO/blog/forum, zero match |
| Il paper descrive l'algoritmo completo | HIGH | PDF letta e analizzata (18 pagine, algoritmo dettagliato) |
| L'algoritmo è implementabile come custom forager | MEDIUM | la struttura del forager è nota, ma l'accessibilità ai ScoreDirector internals è incerta |
| SolverForge ha multithreading nativo | HIGH | README GitHub + docs |
| OptaPlanner 7.x/8.x aveva multithreading community | HIGH | blog post ufficiale + docs |
| Il codice Enterprise è in un jar separato | HIGH | jar analysis del code-review agent |

## Sources

1. **Paper PATAT 2020** - De Smet, Wauters: "Multithreaded incremental solving for local search based metaheuristics with step chasing". `https://patatconference.org/patat2020/proceedings/papers/8.%20PATAT_2020_paper_41.pdf` - L'algoritmo completo.
2. **Blog OptaPlanner 2018** - "A giant leap forward with multithreaded incremental solving". `https://optaplanner.io/blog/2018/07/03/AGiantLeapForwardWithMultithreadedIncrementalSolving` - Speedup dimostrato, config XML.
3. **SolverForge** - `https://github.com/solverforge/solverforge` - Solver Rust con multithreading nativo community.
4. **Timefold docs Enterprise** - `https://docs.timefold.ai/timefold-solver/latest/commercial-editions/commercial-editions` - Feature list Enterprise.
5. **GitHub issue #1350** - "Suspicious move evaluation speed with multi-threaded solving". `https://github.com/TimefoldAI/timefold-solver/issues/1350` - Bug su ARM (fixed v1.19).
6. **Timefold research page** - `https://timefold.ai/research` - Citazioni accademiche.
7. **Timefold fork blog** - `https://timefold.ai/blog/optaplanner-fork` - Storia del fork da OptaPlanner.

## Raccomandazioni (per il task fork Timefold)

1. **La base è il paper**: l'algoritmo "step chasing" è completamente descritto (requirement + method + benchmark). È la specifica per l'implementazione.
2. **Il punto di innesto**: custom forager che rimpiazza `AcceptedLocalSearchForager`. fork del core Timefold (non solo wrapper, perché serve accesso ai `ScoreDirector` internals).
3. **OptaPlanner 8.x come riferimento**: il codice del multithreading era open-source in OptaPlanner 8.x. Il repo `kiegroup/optaplanner` (ultimo tag 8.x) contiene l'implementazione del forager multi-thread che è stata poi spostata in Enterprise. È il **reference implementation** open-source.
4. **Scope del fork**: il minimo è il forager + lo ScoreDirector-per-thread. Non serve forkare tutto Timefold: basta un modulo custom che sostituisce il forager.

## Per TASK-480

TASK-480 resta aperto come attività di fork Timefold (community replacement dell'Enterprise multithreaded). L'A/B multi-start seriale (TASK-483, ratificato) è il workaround production temporaneo. Il fork è il lavoro strutturale per eliminare la dipendenza Enterprise a lungo termine. La ricerca fornisce: (a) il paper-algoritmo, (b) il reference code OptaPlanner 8.x, (c) la conferma che nessuno lo ha fatto prima (vantaggio competitivo).
