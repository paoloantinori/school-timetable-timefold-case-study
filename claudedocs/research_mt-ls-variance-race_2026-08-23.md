# Research Report: La varianza -6..-715 della MT-LS: quanto e' intrinseca, quanto e' difetto nostro, e se il sospetto MT sia fuorviante

**Date**: 2026-08-23
**Depth**: exhaustive
**Confidence**: HIGH sul meccanismo upstream, MEDIUM sulla decomposizione della nostra varianza (richiede l'esperimento decisivo)

## Executive Summary

Il timore di maintainer e' fondato in parte: incolpare il multi-thread **in se'** sarebbe fuorviante, perche' il design upstream di Timefold/OptaPlanner dichiara il multi-threaded solving **riproducibile** con moveThreadCount stabile, e lo ottiene proprio con il modello per-copia che noi NON abbiamo replicato. Ma il sospetto ha un nucleo solido: il nostro decider community si allontana dal modello upstream (thread che valutano su stato condiviso in mutazione invece di copie isolate con rebasing), e quella divergenza e' esattamente la classe di difetto che upstream documenta come causa di score corruption. La conseguenza pratica: esiste un esperimento decisivo ed economico (REPRODUCIBLE x2) che separa le due ipotesi PRIMA di investire giorni nel refactor single-writer.

## Findings

### 1. Il modello upstream: MT senza varianza aggiuntiva e' possibile ed e' progettato cosi'

Il blog ufficiale del designer di OptaPlanner/Timefold (Geoffrey De Smet) documenta il meccanismo del multithreaded incremental solving [1]:
- ogni move thread ha il **proprio score calculator e il proprio stato della soluzione** ("each move thread has its own score calculator and its own solution state");
- i thread **sincronizzano con il thread principale dopo ogni step** (modello single-writer di fatto: un solo thread muta lo stato autorevole, i move thread lavorano su stati allineati);
- le mosse passate tra thread vengono **ribasate** (rebased) sullo stato di destinazione.

La documentazione Enterprise di Timefold [2] e' esplicita: "Multi-threaded solving is *still reproducible*, as long as the resolved moveThreadCount is stable". Cioe': in una implementazione fedele al design, il multi-thread NON introduce varianza run-to-run; la variabilita' arriva dal random seed (NON_REPRODUCIBLE) o da moveThreadCount instabile.

**Implicazione per noi**: la nostra MT-LS e' una reimplementazione community (step-chasing, ratificata TASK-480 14/08) il cui wiring via reflection e' documentato nel nostro codice come fragile e pinnato alle internals 2.3.0. La race del SimpleFieldTracker trovata il 22/08 (array pubblicato con null prima del riempimento, valutazione concorrente su stato in mutazione) e' una deviazione dal modello upstream, non una proprieta' del multi-thread.

### 2. La score corruption e' un difetto noto e "notorio" proprio in questo territorio

De Smet chiama la score corruption "the notorious" causa di problemi nel calcolo incrementale [1], e il canale Stack Overflow ufficiale la collega a desincronizzazione tra score incrementale e score reale [3]. La nostra mitigazione (tracker-priming) riduce la finestra ma non elimina la classe: una valutazione su stato parzialmente mutato produce un delta sbagliato accettato come valido, che puo' mandare la traiettoria in un bacino diverso. Questo e' un meccanismo plausibile per la coda -715/-10877, MA non e' dimostrato che spieghi la forbice: l'alternativa (varianza intrinseca della ricerca stocastica) e' egualmente compatibile con i dati che abbiamo.

### 3. La varianza intrinseca run-to-run in metauristiche e' la norma, non l'anomalia

La letteratura di benchmark per timetabling e local search riporta sistematicamente media e deviazione standard su run indipendenti, perche' singoli run non sono confrontabili [4][5]. La prassi e' che run con randomizzazione attiva diano risultati diversi. Il nostro -6..-715 va letto contro questo sfondo: run NON_REPRODUCIBLE con semi diversi sono campioni i.i.d. e una forbice larga su un obiettivo a gradini (conteggi hard vicino a una scarpata di feasibility) e' compatibile con la norma del settore.

Tuttavia nessuna fonte supporta una forbice di due ordini di grandezza come "tipica" per la STESSA configurazione e costruzione: il nostro caso resta ambiguo tra (a) intrinseca e (b) difetto, e la letteratura non decide al posto nostro.

### 4. L'esperimento decisivo (che costa poco e va fatto prima del refactor)

Dichiarazione di meccanismo da [6]: in modalita' REPRODUCIBLE, due run della stessa configurazione sulla stessa macchina eseguono la stessa sequenza ("2 runs on the same computer will execute the same steps"). Quindi:

- **REPRODUCIBLE x2, stessa config, stesso thread count**: se i due run sono identici, la macchina e' deterministica-capace e la forbice -6..-715 e' interamente dovuta alla randomizzazione (semi) NON_REPRODUCIBLE = varianza intrinseca. Il refactor single-writer NON ridurrebbe la varianza; la risposta e' il pacchetto multi-start+riparazione.
- Se i due run DIVERGONO in REPRODUCIBLE, qualcosa non e' deterministico quando dovrebbe esserlo (race o wiring): il refactor single-writer e' giustificato con evidenza diretta.

Costo: due run da ~30-45 min, zero codice. E' il test che gerarchizza le opzioni prima di spendere giorni.

### 5. Verdetto sul "fuorviante"

Dire "la MT-LS causa la varianza" e' impreciso e puo' portare fuori strada (il multi-thread ben fatto e' riproducibile per design). Dire "il NOSTRO decider custom puo' avere difetti di concorrenza che la versione upstream non ha, e una parte della cotta potrebbe venire da li'" e' accurato e verificabile col test al punto 4. Il piano (c) va tenuto ma degradato a "condizionato all'esito del test": prima la misura, poi eventualmente il refactor.

## Confidence Assessment

- HIGH: il modello upstream per-copia/rebasing/sync-per-step e la dichiarazione di riproducibilita' del MT solving (fonti primarie ufficiali).
- HIGH: l'esperimento REPRODUCIBLE x2 e' diagnosticamente valido (meccanismo documentato della modalita').
- MEDIUM: la plausibilita' che parte della nostra coda sia race (meccanismo coerente con la nostra ricerca del 22/08, ma nessuna misura di attribuzione).
- NON VERIFICATO: la frazione della forbice dovuta a ciascuna causa; richiede il test del punto 4 e la decomposizione 1-vs-8 thread.

## Sources

1. OptaPlanner blog, "A giant leap forward with multithreaded incremental solving" (De Smet, 2018), https://www.optaplanner.org/blog/2018/07/03/AGiantLeapForwardWithMultithreadedIncrementalSolving.html (mirrors: javacodegeeks.com, dzone.com)
2. Timefold Solver docs, Enterprise Edition, sez. Multi-threaded solving, https://docs.timefold.ai/timefold-solver/1.x/enterprise-edition/enterprise-edition
3. Stack Overflow, "OptaPlanner's mythical score corruption", https://stackoverflow.com/questions/29672171/optaplanners-mythical-score-corruption
4. PATAT 2006 proceedings (iterative search timetabling, prassi multi-run), https://patatconference.org/patat2006/proceedings/3_2.pdf
5. Springer, "Simulated annealing-Tabu search integration" (media e deviazione standard su run indipendenti come prassi), https://link.springer.com/article/10.1007/s10732-026-09601-9
6. EnvironmentMode REPRODUCIBLE, javadoc OptaPlanner/Timefold, https://docs.jboss.org/drools/release/6.0.0.Final/optaplanner-javadoc/org/optaplanner/core/config/solver/EnvironmentMode.html
