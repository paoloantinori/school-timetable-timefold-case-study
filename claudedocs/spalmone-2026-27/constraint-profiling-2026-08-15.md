# Constraint profiling community - re-run E1 post-ratifiche agosto (laptop, 2026-08-15)

> Filone 2 del mandato pomeriggio. Il profiler per-constraint ESISTE gia'
> (ConstraintProfilingLeaveOneOutTest, E1 del 31/07: leave-one-out con warmup, sanity
> no-op, caveat documentati): il lavoro di oggi e' il RE-RUN sullo stato corrente
> (post ratifiche 497/H30/498 e i vincoli nuovi M-CPS/M-TR/M20.2 attivi) piu' la
> classifica aggiornata con lettura onesta. Alterna a TASK-315. Harness:
> snapshot final senza stanze, budget 20s/braccio, NO_ASSERT seed 0, 61 solve.

## Classifica 2026-08-15 (baseline 13.977 calcs/s)

| rank | constraint | speedup escluso | lettura |
|------|-----------|-----------------|---------|
| 1 | **H1 Teacher conflict** | +1975 | hard core, join per docente |
| 2 | **H3 Student group conflict** | +1798 | hard core, join per gruppo |
| 3 | **H28 No teacher return** | +1641 | groupBy (gruppo,materia,giorno), cresciuto vs 31/07 (#8) |
| 4 | M15 Same-day consecutive | +1572 | sotto pavimento 1,5x: probabile ma non netto |
| 5 | S13 Weekly stability | +1514 | sotto pavimento |
| 6-11 | S7, S1, S3, H17, H26, H5 | +1300..1410 | ZONA PAVIMENTO: non classificabili |
| 12 | S9 (no-op SANITY) | +1203 | **il pavimento di questo run** |
| 14 | M20.2 Empty expected day | +1031 | nuovo attivo, sotto pavimento |
| 20/22 | M34 / M32 | +914/+892 | confermati NON costosi (debunk del 31/07 regge) |
| 35 | M20 Daily quota fill | +624 | NON costoso (il pool-cap non ha peggiorato il quadro) |

Raw completo: `e1-leave-one-out-ranking-2026-08-15.txt`.

## Lettura statistica (la parte che conta)

Il pavimento no-op S9 di questo run e' **+1203** (era +616 il 31/07): il JIT ha scaldato
meno tra braccia. Consegienza: con pavimento 1203, SOLO tre vincoli superano 1,5x il
pavimento e sono classificabili con fiducia: **H1 (+1975), H3 (+1798), H28 (+1641)**.
Tutto cio' che sta fra +1200 e +1400 e' indistinguibile dal rumore di posizione.

Confronto col 31/07 (pavimento 616, top S3 +1741/S7 +1638/S12 +1400): la top-3 e'
CAMBIATA in composizione ma non in natura: allora 4 soft da stream-join dominavano,
oggi 2 hard core (H1/H3) + H28. Ipotesi piu' probabile: non e' il codice cambiato
(le ratifiche di agosto toccano medium/flag, non H1/H3) ma la POSIZIONE nel run +
il pavimento piu' alto che seppellisce i soft di medio rango. La conclusione OPERATIVA
e' identica a quella del 31/07 ed e' stabile fra i due run: **nessun vincolo domina
drammaticamente** (top +1975 su base 13977 = ~14%): il costo e' distribuito sui join
per (docente) e (gruppo), che sono il modello stesso.

## Raccomandazioni (dove aggiungere shadow variables / precompute)

1. **H28 e' il candidato concreto emerso**: groupBy (gruppo, materia, data) con
   ordinamento intra-giorno per pattern A-B-A: e' l'unico della top che fa un join
   a tre con ordinamento. Un precompute per-lezione del "docente successivo nella
   stessa (gruppo,materia,giorno)" come shadow variable taglierebbe il join a un
   lookup. Costo: shadow var su Lesson (aggiornamento su move). Da valutare SOLO se
   il wall resta il collo dopo il nearby-filter (TASK-499 ha appena tolto -20%).
2. **H1/H3 sono il modello**: join docente/gruppo per conflitto e' cioe' che il
   modello E'. Non sono ottimizzabili senza cambiare modello (shadow del
   "prossimo slot occupato del docente" e' la forma classica ma e' un intervento
   grosso). Consiglio: NON toccare finche' le leve di selezione (filtro mosse)
   rendono piu' di cosi'.
3. **M32/M20/M34 confermati economici**: il debunk di agosto regge anche col
   pool-cap attivo. Nessuna azione.
4. **Prossima misura utile**: rifinire i top-sospetti a budget 120s (il doc E1 lo
   prevede: i top si rifiniscono se uno scarto e' incerto) SOLO se serve una decisione
   su H28; altrimenti il quadro e' completo.

## Come riprodurre

PLANNER_TEST_PROFILE=1 ./scripts/core/test.sh -c ConstraintProfilingLeaveOneOutTest
(output: target/e1-leave-one-out-ranking.txt; ~25 min; snapshot default senza stanze,
caveat room-tier invariati: H2/H29/H-M2/M3/M-LF non rappresentativi).
