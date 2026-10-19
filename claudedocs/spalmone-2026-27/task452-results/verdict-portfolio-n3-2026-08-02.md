# VERDETTO STATISTICO n=3 - portfolio size=2 sul path source=input (2026-08-02)

> zai, notte8. Tre run indipendenti (seed NON_REPRODUCIBLE) del ciclo staged con
> `-Dplanner.portfolio.size=2 -Dplanner.pre-assign.constrained-teacher.source=input`,
> protocollo identico (reset + import-calendario + pre-assign +23 + solve-staged), DB
> `planner-data-production`, app `java -jar`. Verdetti dai raw. Capture: `verify-source-input-run{6,7,8}/`.

## Risultato (dai raw)

| run | score finale | unassigned | full-day | esito |
|-----|--------------|-----------|----------|-------|
| run6 | 0hard | 0 | 87,5% | PASS |
| run7 | -36hard | 15 | 89,3% | FAIL |
| run8 | -56hard | ~28 | (capture) | FAIL |

**P(pass hard-0) = 1/3. P(fail) = 2/3.** n=3 sul treatment portfolio size=2.

## Finding chiave: il portfolio NON riduce la varianza (candidati convergenti)

In OGNI round i 2 candidati del portfolio convergono a hard quasi identico:
- run7 round1: -2137 / -2137 (pickBest -2137); round2: -2141 / -2141; round3: -2145 / -2145.
- run8 round1: -2091 / -2091 (pickBest -2091); round2 pickBest -2094; round3 pickBest -2098.

I candidati NON sono indipendenti: parte dalla stessa costruzione + PROMOTION (7504 pin
promossi identici), poi la LS B-TIMESLOT converge allo stesso ottimo locale da traiettorie
near-identical. Quindi `1-(1-p)^N` (la stima della procedura, che assumeva candidati
indipendenti) NON si applica: portfolio size=2 ~ single solve. Conferma: single run4/run5
era 1/2 pass; portfolio n=3 e' 1/3 pass. **Nessun miglioramento.**

La variabilita' e' a livello di SEED (run6 seed -> 0hard; run7/run8 seed -> fail), non
within-round. Il portfolio multi-start non la doma perche' i suoi candidati non esplorano
regioni diverse dello spazio.

## I residui sono collocabili (AC#1 lo aveva gia' mostrato)

run7 15 unassigned, run8 ~28 unassigned, entrambi nella classe T08/G03 + T19/G06
(gli stessi del residuo run5). AC#1 ha dimostrato che depth-0 celle 0-hard sono abbondanti
(45h per 11h necessarie su T08, 377h per 5h su T19): il residuo NON e' infeasibile,
e' trappola dell'ottimo locale LS. Il portfolio non la risolve (candidati convergenti); la
risolve l'enumerazione deterministica (ExactFinisher, TASK-478).

## Costo del portfolio (non giustificato dai risultati)

~3x B-TIMESLOT vs single (run7/run8 ~75min/ciclo per 3 round vs ~44min single). A parita'
di P(pass) con il single, il portfolio e' costo puro. **Non adottare.**

## Conclusione / implicazione per il flip m-13

1. **Il portfolio multi-start e' NEUTRO su P(hard-0)** (candidati convergenti -> ~single).
   La procedura `procedura-portfolio-path-input-2026-08-01.md` va corretta: la tabella
   `1-(1-p)^N` e' REFUTATA dai dati (N=3 empirico 33% vs teorico 87,5%). Resta DRAFT-005,
   non ratificare.
2. **L'ExactFinisher (TASK-478) e' il complemento deterministico che serve**: chiude il
   residuo enumerandolo (non soggetto all'ottimo locale). AC#1 feasible, AC#2 design +
   AC#3 prototipo (depth 0..1, flag OFF, 5/5 test PASS, compile OK) consegnati. L'A/B
   (AC#4) e' ora la priorita' per il flip: ExactFinisher ON sui residui run7/run8 dovrebbe
   chiuderli deterministicamente (provando il valore).
3. **P(pass) single ~33-50%** + ExactFinisher (chiude il residuo) = hard-0 deterministico
   per il path input. Questo e' il piano robusto, non il portfolio.

## Raw di riferimento
- run6: `verify-source-input-run6/` (VERDICT-2026-08-01.md, 0hard).
- run7: `verify-source-input-run7/` (score.txt -36hard, portfolio-candidates.txt 3 round).
- run8: `verify-source-input-run8/` (score.txt -56hard, portfolio-candidates.txt 3 round).
