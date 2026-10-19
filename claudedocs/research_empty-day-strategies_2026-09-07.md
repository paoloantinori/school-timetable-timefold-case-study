# Research: Strategie astratte per celle senza donatori (il residuo coda-prima)

**Date**: 2026-09-07
**Depth**: exhaustive
**Confidence**: HIGH (le strategie sono ben documentate nella letteratura OR; la mappatura sul nostro caso e' diretta)

## Executive Summary

Il problema "4 celle vuote a fine anno senza donatori vicini" e' un caso classico di **scarsita' di risorsa combinata con ordine di allocazione sbagliato**. La letteratura OR offre tre strategie principali, in ordine di impatto crescente:

1. **Most-constrained-first ordering** (costruzione): le celle piu' vincolate vanno piazzate PER PRIME
2. **Ruin-and-Recreate** (ALNS): distruggi un'intera zona (celle + donatori insieme) e ricostruisci ottimalmente
3. **Score protection** (il tuo score-incentivo): le celle pre-piazzate non si distruggono, si proteggono

La strada che chiude il cerchio e' la combinazione 1+3: **coda-prima in costruzione + protezione in LS**. Non servono pass piu' potenti: serve cambiare QUANDO le celle vengono riempite e COME vengono protette.

## Findings

### 1. Most-constrained-first: la letteratura conferma

"Those vertices that are most likely to cause trouble if deferred until later" [10]. Il principio: le entita' con meno opzioni vanno assegnate prima. Le nostre celle di coda hanno 1-2 giorni donatori vicini contro i 20-30 delle celle mid-year: sono le piu' vincolate e vanno prima. Il TASK-590 (coda-prima) e' esattamente questo principio. Il pre-booking (TASK-564) riserva le celle ma NON riordina il pool: le ore ancora scorrono cronologicamente.

### 2. ALNS: il destroy-repair e' la generalizzazione della nostra catena

La letteratura ALNS [1,2,3] dice: il destroy operator estrae TUTTO un sottoinsieme (non una lezione alla volta), il repair operator ricostruisce ottimalmente. La nostra catena v13 e' un destroy-repair di raggio 1 (una lezione donor + un blocker). L'ALNS generalizza: distruggi TUTTO il periodo Marzo-Giugno di un gruppo (donatori + celle insieme), poi ricostruisci il periodo Ottobre-Giugno ottimalmente. Questo vede il sotto-problema intero e lo risolve in un colpo, invece di cercare la mossa singola giusta.

### 3. Score protection: la tua idea e' la forma piu' economica

Il tuo score-incentivo ("penalizza chi li distrugge") e' la cura a minor costo implementativo: un constraint stream che conta le celle pre-booked vuote e penalizza a tier hard o a tier alto. Non richiede un nuovo pass, un nuovo ordine, o un move selector: richiede SOLO la lista delle celle pre-booked (che il pre-booking gia' ha) e un constraint in piu' (gia' lo schema: H34 e' la forma esatta ma con popolazione diversa).

### 4. La combinazione che chiude il cerchio

Costruzione (coda-prima) → LS (con score protection) → pass deterministico (per il residuo). Le tre fasi non sono alternative: sono stratificate. Ogni strato riduce il residuo per lo strato successivo.

## Sources

1. ALNS: Adaptive Large Neighborhood Search (Ropke & Pisinger, il paper fondatore)
2. ALNS for curriculum-based course timetabling (Springer, 2016)
3. Evolving construction ordering heuristics for educational timetabling (hyper-heuristics: most-constrained-first)
4. Timefold Solver construction heuristics docs (First Fit, First Fit Decreasing: l'ordine conta)
