# Research Report: paired/seeded A-B testing vs repeated measures per il confronto 3-livelli vs BendableScore

**Date**: 2026-08-21
**Depth**: standard
**Confidence**: HIGH (fonte primaria: il paper del meccanismo esatto che usiamo, piu' letteratura CRN)

## Executive Summary

Il timore di maintainer e' fondato e la letteratura lo conferma con precisione inquietante: il multithread step-chasing (IL NOSTRO meccanismo, PATAT 2020, De Smet) non e' solo piu' veloce del single-walk, **produce soluzioni MIGLIORI**. Confrontare le due configurazioni di score (3 vs 4 livelli) con LS a thread singolo significherebbe testarle entrambe su un regime di ricerca degradato e non rappresentativo. Il disegno corretto e': configurazione di produzione (8 thread), N run indipendenti per braccio sugli stessi seed di costruzione, confronto fra distribuzioni.

## Findings

### 1. Il single-thread degrada la qualita', non solo la velocita' (HIGH)
Il paper PATAT 2020 "Multithreaded incremental solving for local search based metaheuristics with step chasing" (De Smet/Wauters: lo step-chasing e' ESATTAMENTE il meccanismo che abbiamo ratificato in TASK-486) mostra su nurse rostering/VRP/course scheduling: con 8 thread, step chasing **outperforms a single walk** in qualita' della soluzione (media su 10 run), e per Late Acceptance e Tabu Search batte anche il multi-walk (best-of-8 indipendenti). Il nostro dato empirico (stesso attempt 1 deterministico: -23 con 8 thread vs -643 con 1 thread) e' la direzione attesa, non un'anomalia.

### 2. La pratica di benchmark corretta: N run indipendenti, non singole corse appaiate (HIGH)
Lo stesso paper e la guida benchmark di OptaPlanner/Timefold usano 10 run per configurazione con seed diversi e confrontano le medie (con warmup). Nessuna fonte autorevole confronta metaheuristiche su singole corse appaiate complete: la variabilita' dei bacini viene gestita con repliche, non con determinismo forzato.

### 3. Common Random Numbers: utili SOLO se l'allineamento non cambia il sistema sotto test (MEDIUM-HIGH)
La letteratura CRN/paired sampling (Inria HAL hal-01194510 e correlati) e' chiara: il pairing riduce la varianza quando la stessa realizzazione di rumore puo' essere riusata tra configurazioni SENZA alterarle. Nel nostro caso l'allineamento completo richiede di cambiare il sistema stesso (1 thread, PHASE_ASSERT), quindi il presupposto del CRN e' violato: stiamo misurando una configurazione diversa da quella che consegneremo. Il paper cita esplicitamente casi in cui CRN "does not work" o e' detrimentale.

### 4. Le mode deterministiche di Timefold hanno effetti collaterali noti (HIGH)
De Smet (blog OptaPlanner): le mode ASSERT rendono le performance irrilevanti (asserzioni costose); REPRODUCIBLE forza seed 0 (da noi osservato: azzerava i seed per-attempt). Coerente con l'incidente di oggi.

## Raccomandazione

Abbandonare l'appaiamento completo via single-thread. Disegno: (a) configurazione di produzione identica ai due rami (8 thread, portfolio come_PROD, seed GRASP espliciti per attempt); (b) 3 run indipendenti per braccio sugli stessi 5 seed; (c) confronto su distribuzione (media + minimo per seed) con la regola 0hard-binaria; (d) la riproducibilita' stretta resta per i FIXTURE della biblioteca bacini (costruzione + replay deterministici), non per i run di confronto. Costo: 6 run totali ma rappresentativi; il verdetto vale per il solver di produzione.

## Sources

1. PATAT 2020 paper 41: Multithreaded incremental solving for local search based metaheuristics with step chasing (De Smet, Wauters) - patatconference.org/patat2020/proceedings/papers/8.PATAT_2020_paper_41.pdf
2. OptaPlanner blog: The path to enlightenment starts in the log (env modes, ASSERT cost, seed 0) - optaplanner.io/blog/2021/05/06/...
3. Inria HAL hal-01194510: Variance Reduction in Population-Based Optimization (CRN/paired sampling, quando funziona e quando no)
4. ResearchGate 317724973: Best Practices for Comparing Optimization Algorithms (indirizzo generale: repliche multiple, mai corse singole)
