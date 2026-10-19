# VERDETTO A/B ExactFinisher (TASK-478 AC#4) - source=input, flag ON, n=2 (2026-08-02)

> zai, alba 02/08. A/B formale del pass ExactFinisher (depth 0..1, MAX_K=30,
> MAX_CANDIDATES=10000) sul path `source=input` SENZA portfolio. App `java -jar` con
> `-Dplanner.exact-finisher.enabled=true -Dplanner.pre-assign.constrained-teacher.source=input`.
> Capture: `task478-results/ab-ef-run{1,2}/`. Verdetti dai raw. **Flag resta DEFAULT OFF,
> nessuna adozione.**

---

## RE-TEST BUDGET 100000 (run3/run4, 2026-08-02 05:xx). SUPERSEDE il "1/2 budget-capped" sopra

> Raise `MAX_CANDIDATES` 10000 -> **knob live `planner.exact-finisher.max-candidates` default
> 100000** (commit `abe8d892`, refactor `CandidateBudget` holder + disaccoppio `MCV_PROBE_CAP`).
> 2 run freschi source=input, ExactFinisher ON, NO portfolio, stesso protocollo run1/run2.
> Unica variabile vs run1/run2: il budget (10000 -> 100000). Banner app: `maxCandidates=100000`.

| run | score finale | seed | ExactFinisher | esito |
|-----|--------------|------|---------------|-------|
| run3 | **0hard**/-1044912medium/5419soft | BUONO (BoxRepair chiude boxed=0; 1 iter B-TIMESLOT) | INERTE (boxed=0 all'early-return, no log) | PASS (via LS/BoxRepair, EF mai partito) |
| run4 | **-16hard**/-1022677medium/1033soft | CATTIVO (18 boxed al finisher; 3 iter B-TIMESLOT + demotion) | **placed 13 of 18** poi budget 100000 capped (5 left boxed) | FAIL (residual 5) |

**Confronto EF placement al variare del budget (seed cattivo):**
| budget | boxed al finisher | EF placed | residual | run |
|--------|-------------------|-----------|----------|-----|
| 10000 (vecchio default) | 15 | **3 of 15** | 12 | run2 (-30hard) |
| 100000 (nuovo default) | 18 | **13 of 18** | 5 | run4 (-16hard) |

Il raise 10x ha migliorato il placement del EF da **3/15 a 13/18 (~4x)**: il meccanismo depth-0/1
**NON e' disprovato**, anzi con budget adeguato colloca molto meglio. Il WARN riscritto ("IS a
calibration knob") era corretto e fires come atteso. **MA 100000 e' ancora budget-capped** sul
seed cattivo (5/18 residui -> -16hard): il worst case NON si chiude a depth 0..1 a questo budget.

### Verdetto re-test (3 righe, dai raw)
1. **P(hard-0)=1/2 di nuovo.** run3=0hard (good seed, EF inerte boxed=0), run4=-16hard (bad seed,
   EF placed 13/18 @ 100000). **hard-0 DETERMINISTICO NON confermato** (non 2/2).
2. **Budget raise VALIDATO direzionalmente ma INSUFFICIENTE.** 10000->100000: EF placement 3/15 ->
   13/18 sui seed cattivi. Il meccanismo e' budget-starved non disprovato; MA 100000 caps ancora
   (5/18 residui sul worst seed). Non sappiamo da run4 se i 5 residui siano collocabili a depth 0..1
   con budget ancora piu' alto (la search e' stata troncata dal cap) o richiedano depth-2.
3. **Resta flag OFF, nessuna adozione.** Next step **gated a maintainer** (il knob live rende tutto
   cheap, no rebuild): (a) probe budget piu' alto (500000-1M: `-Dplanner.exact-finisher.max-candidates=500000`,
   ma deve pescare un seed cattivo ~2-3 run per la stochasticita' della LS); o (b) implementare
   **depth-2** (gate combinato bump+relocation deferito, design AC#2 sez. 5) per i residui piu'
   costretti; o (c) accettare che una frazione del residuo sia parzialmente strutturale.

Capture: `task478-results/ab-ef-run{3,4}/`. Log EF run4: `/tmp/planner-prod.log`
`grep "TASK-478 ExactFinisher"` (placed 13 of 18, WARN budget 100000). DoD change knob: `/simplify`
(holder + disaccoppio probeBudget) + `test.sh` EXIT 0 (962/0/16, ExactFinisherTest 7/0/0).

---

## DISCRIMINATORE UNCAPPED (alba3, 2026-08-02 ~05:55). CHIUDE la domanda aperta del re-test

> Il re-test lasciava aperto: "i 5 residui di run4 sono collocabili a depth 0..1 con budget piu'
> alto (search troncata dal cap) o richiedono depth-2?". Il discriminatore risponde con la STESSA
> gate chain del Java EF, UNCAPPED. Doc: `task478-results/discriminatore-uncapped-run4-2026-08-02.md`.
> Test: `src/test/java/.../bootstrap/ExactFinisherRun4DiscriminatorTest.java`.

**Risultato: ESITO (i), depth-0/1 completion ESISTE UNCAPPED.** `SnapshotFixture.loadFromExport`
ricostruisce il post-stato run4 (13 piazzati + 5 boxed) + `seedMediumTierFacts` (quota REALE,
materiale per i 4 residui lab Barberia quota-bound), poi `ExactFinisher.repair` budget 100M
(uncapped) depth 0..1 con la gate chain production. **placed 5 of 5, residual 0, conflicts 0.**
Tutti i 18 boxed originali sono depth-0/1-collocabili (13 run4 + 5 qui). **depth-2 NON necessaria**
per il worst case noto; il cap era l'unico ostacolo. Questo chiude anche i caveat di AC#1 (VRP
reale applica H20/H5/quota, EF non gate-checka H2).

**Conseguenza sul menu:** il ramo (b) depth-2 e' escluso per il worst case noto. Resta (a) probe
budget piu' alto, che ora e' FATTO con VERDETTO GREEN: probe LIVE knob **1000000**, run1 good seed
(0hard, EF inerte), run2 **bad seed** (18 boxed) -> **EF @ 1M placed 18 of 18 depth<=1 (residual 0,
conflicts 0)**, H0 -> 0, finale **-6hard = 6 H17 strutturale** (stesso count di run4, ortogonale a
EF). Confronto pulito: @ 100k (run4) EF 13/18 -> -16hard (5 H0 + 6 H17); @ 1M (probe-run2) EF 18/18
-> -6hard (0 H0 + 6 H17). **Budget 1M chiude la worst-case boxed tail; EF adottabile per H0
deterministico sul path input.** Capture: `task478-results/ab-ef-probe-1m-run{1,2}/`.

---

## Storico: run1/run2 @ budget 10000 (precedente al raise)

## Risultato (dai raw)

| run | score finale | unassigned | ExactFinisher | esito |
|-----|--------------|-----------|---------------|-------|
| run1 | **0hard**/-1042487medium/5583soft | 0 | INERTE (boxed=0 all'early-return, LS aveva chiuso tutto) | PASS (via LS) |
| run2 | **-30hard**/-1039910medium/367soft | ~12 (10 Matematica/G03) | **placed 3 of 15**, poi MAX_CANDIDATES=10000 capped | FAIL |

**P(hard-0) = 1/2. NON 2/2 → hard-0 NON dichiarato deterministico.**

## Cosa ha fatto ExactFinisher (run2, il seed cattivo informativo)

Run2 e' un seed "cattivo" (B-TIMESLOT finito a -2145hard, stessa classe di run7/run8 del n=3
portfolio). A fine repair chain restavano **15 boxed** (10 dei quali Matematica/G03, il gruppo
T08). ExactFinisher ha loggato:

```
WARN  ExactFinisher: candidate budget of 10000 reached - 12 of 15 residual(s) left boxed.
        AC#1 showed depth-0 capacity is 4x-75x, so this is a signal about the input, not a tuning knob.
INFO  ExactFinisher: placed 3 of 15 boxed lesson(s) at depth<=1 (residual 12, conflicts added 0)
```

**Ha piazzato 3 di 15, poi il budget di search (MAX_CANDIDATES=10000) e' terminato.** 12 boxed
restano (final -30hard = ~12 H0 + residuo room). Run1 invece era un seed "buono": la LS aveva
già azzerato i residui, ExactFinisher ha trovato boxed=0 ed e' tornato (early-return, no log) -
hard-0 via LS, ExactFinisher inerte.

## Diagnosi: il budget e' il collo di bottiglia, NON la depth 0..1 (non disprovato)

MAX_CANDIDATES=10000 conta le **valutazioni di cella candidate** (shared global, incrementato in
`findDepth0Cell`/`findDepth1Chain`/`findRelocationCell`). L'ordinamento MCV processa prima le
lezioni PIU' costrette (fewest depth-0 cells): quelle senza una cella depth-0 libera scansionano
tutto il VRP (~1368 celle) per `findDepth0Cell`, poi `findDepth1Chain` aggiunge altre valutazioni.
**15 lezioni costrette × ~1368 celle > 10000** dopo le prime ~7, quindi la ricerca abortisce
mid-way sulle ultime 8-12. Il WARN del design doc che dice "not a tuning knob" e' SBAGLIATO: il
budget E' un knob di calibrazione, ed e' tarato troppo basso per la dimensione del residuo reale.

Il meccanismo depth-0/1 **non e' disprovato**: e' budget-starved. Non sappiamo (da questo A/B)
se le 12 residue siano collocabili a depth 0..1 con budget adeguato, o se richiedano depth-2
(deferred). AC#1 misurava il residuo run5 (T08 45h/11h abbondanti); il residuo run2 (10
Matematica/G03) potrebbe essere piu' saturo o semplicemente non esplorato a causa del cap.

## Verdetto (3 righe, dai raw)

1. **1/2 hard-0.** Run1 hard-0 via LS (ExactFinisher inerte); run2 -30hard con ExactFinisher che
   piazza 3/15 poi capped. **hard-0 DETERMINISTICO NON confermato** (non 2/2).
2. **ExactFinisher e' budget-starved, non disprovato.** MAX_CANDIDATES=10000 abortisce la ricerca
   su un residuo reale di 15 lezioni costrette. Il WARN "not a tuning knob" e' errato.
3. **Resta flag OFF, nessuna adozione.** Il prossimo step immediato e' raise di MAX_CANDIDATES
   (es. 100000-200000, ~10x, garantisce scan depth-0 completo per 15+ residue + headroom depth-1)
   + rebuild + re-run n>=2 per ottenere la capacity depth-0/1 definitiva. Solo se hard-0 diventa
   2/2 con budget adeguato si porta a maintainer per il SI.

## Prossimo step (gated, NON adottato)
1. Raise `ExactFinisher.MAX_CANDIDATES` 10000 -> 100000 (o 200000). Correggere anche il WARN
   ("not a tuning knob" -> e' un knob di budget, vedi questo verdict).
2. Rebuild + 2 run freschi source=input. Verdetto: quante delle 15 residue chiude a budget
   adeguato? Se 15/15 -> depth 0..1 basta, candidato adozione. Se ancora residue -> depth-2
   (gate combinato bump+relocation, design AC#2 sez. 5) o il residuo e' strutturale.
3. DoD sul change di MAX_CANDIDATES: /simplify + /code-review high + test.sh EXIT 0.

## Raw di riferimento
- run1: `ab-ef-run1/` (score.txt 0hard, ExactFinisher inerte, 0 unassigned).
- run2: `ab-ef-run2/` (score.txt -30hard, ExactFinisher placed 3/15 budget-capped).
- Log ExactFinisher: `/tmp/planner-prod.log` grep `TASK-478 ExactFinisher`.
