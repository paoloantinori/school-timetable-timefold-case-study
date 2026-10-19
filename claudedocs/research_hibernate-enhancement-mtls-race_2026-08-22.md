# Research Report: Hibernate bytecode enhancement + multi-threaded LS in Timefold (NPE SimpleFieldTracker)

**Date**: 2026-08-22
**Depth**: exhaustive
**Confidence**: HIGH (sorgente Hibernate letta direttamente; meccanismo verificabile riga per riga)

## Executive Summary

Il crash del 22/17:24 (NPE `SimpleFieldTracker.contains: "existing is null"` da `Lesson.setRoom` in un move thread) e' una **race condition documentabile riga per riga nel codice sorgente di Hibernate**: il dirty tracker non e' thread-safe perche' pubblica un array con una cella null prima di riempirla. La causa strutturale nostra: Quarkus fa bytecode enhancement build-time di TUTTE le entita' (dirty tracking), e noi usiamo le entita' Panache direttamente come planning entity del solver, il nostro MT-LS custom applica le mosse DAI move thread (non dal thread decider). Nessuna combinazione dei tre e' mai stata pensata per stare insieme.

## Findings

### 1. La race e' nel codice di Hibernate, verificabile (HIGH)
`SimpleFieldTracker.add(String)` (letto dal sorgente main di hibernate-orm):
```java
names = Arrays.copyOf(names, names.length + 1); // pubblica array con ultima cella NULL
names[names.length - 1] = name;                 // la riempie DOPO
```
`contains` itera e dereferenzia `existing.equals(name)`: un thread che legge fra le due righe di un altro vede la cella null → NPE esatta che abbiamo. Zero sincronizzazione nella classe (campo `String[] names` non volatile, nessun lock). Non e' un bug "nostro": e' un componente dichiaratamente single-thread usato oltre contratto.

### 2. Il design di riferimento Timefold NON usa entita' JPA come planning entity (HIGH)
Il quickstart ufficiale Quarkus modella Timeslot/Room/Lesson come **POJO puri** ("all elements of the planning domain model are plain objects"), caricati da dove si vuole e passati al solver. La nostra scelta (Panache entity = planning entity, comoda per persistere il risultato) e' l' deviazione che rende il setter di room un punto di scrittura concorrente su codice enhanced. I docs Quarkus avvertono gia' che l'enhancement rompe perfino `clone()` sulle entita': lo stesso meccanismo (campi iniettati `$$_hibernate_*`) e' fragile per usi non-standard.

### 3. Il nostro MT-LS applica le mosse dai thread: il terzo ingrediente (HIGH)
Lo stack (`MoveThreadRunner:105 executeSetter`) mostra che i move thread applicano le mosse alla working solution. Nel design del paper PATAT 2020 (step chasing, quello che TASK-486 ratifica), i thread **valutano** in parallelo e il **decider applica** la mossa vincente: single-writer. La nostra forma e' multi-writer e questo espone ogni setter enhanced (room, timeslot) alla race. Nota: il MT-LS nativo Enterprise di Timefold (moveThreadCount) e' progettato per essere sicuro con entita' plain (e richiede @PlanningId); non protegge codice esterno che scrive concorrentemente.

### 4. Opzioni di fix, in ordine di correttezza (MEDIUM-HIGH per la scelta, meccanismi HIGH)

**A. Single-writer: il decider applica, i thread solo valutano** (allineamento al paper e all'architettura Timefold). Costo: rivedere MoveThreadRunner/Decider (la valutazione incrementale oggi avviene applicando la mossa... se il thread deve valutare senza applicare in modo persistente, serve apply-undo, che e' il pattern del paper: "apply, evaluate, undo" per candidate, e solo il winner viene applicato dal decider). E' il fix che rimuove la classe intera di race (anche future).

**B. SOSPENDERE il dirty tracking durante il solve**: il tracker ha `suspend(boolean)` e le entita' enhanced espongono `$$_hibernate_getTracker()`... ma NON e' API pubblica/stabile, e sospendere il tracking su entita' managed rischia che il flush di fine solve non veda i cambiamenti (o serve resume + touch). Fragile: sconsigliato come fix principale.

**C. Disattivare l'enhancement**: Quarkus lo fa build-time su tutte le entita' del progetto; NON esiste flag per-entity per escluderlo (verificato sui docs Quarkus: enhancement automatico e non opzionale per-unit; l'unica via e' spostare le entita' dominio in POJO separati o in un jar non-enhanced). Refactor grande (le entita' sono anche la persistenza): sproporzionato oggi.

**D. Lock per-entita' sui setter**: impossibile senza toccare bytecode generato.

Raccomandazione: **A**, con la nota che il nostro MT-LS era gia' in zona grigia (multi-writer) e il paper prescrive il single-writer: il fix rimuove anche un difetto latente di correttezza score (due apply concorrenti fra valutazione e apply del winner).

### 5. Perche' e' successo SOLO oggi, dopo mesi di MT-LS (MEDIUM)
La race richiede due thread sulla STESSA entita' nella finestra fra copyOf e l'assegnazione (nanosecondi). E' probabilistica: la fase stanze concentra le mosse room sulle stesse entita' sature (il marcatore zona-stage, la finestra ristretta dei tag), e l'iter-12/13 hanno aumentato la densita' di mosse room sui pin. Raro ma non mai impossibile: era una bomba a orologeria, non una regressione di oggi.

## Confidence Assessment
- Meccanismo della race: HIGH (sorgente letto, riproducibile leggendo 6 righe).
- single-writer come fix giusto: HIGH (paper PATAT + architettura Timefold concordi).
- Perche' proprio oggi: MEDIUM (inferenza dalla densita' delle mosse room, non misurato).
- Non verficato: se il nostro decider possa applicare il winner senza perdere il beneficio di performance (da misurare dopo il fix).

## Sources
1. SimpleFieldTracker.java (sorgente Hibernate main, letto via raw.githubusercontent): la race in `add`/`contains`.
2. Timefold Quarkus quickstart (docs.timefold.ai): planning domain = POJO, non entita' JPA.
3. Quarkus Hibernate ORM guide (quarkus.io): enhancement build-time automatico, warning su clone(), nessuna esclusione per-entity.
4. Timefold Enterprise docs (multi-threaded incremental solving): il modello ufficiale richiede @PlanningId ed e' internal al solver.
5. PATAT 2020 step-chasing paper (De Smet/Wauters): threads valutano, il passo (apply) e' del decider: single-writer.
6. Vlad Mihalcea / Baeldung su bytecode enhancement dirty tracking: meccanismo `$$_hibernate_tracker` per campo.
