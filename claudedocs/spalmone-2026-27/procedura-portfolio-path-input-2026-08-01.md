# PROCEDURA: portfolio multi-start per hard-0 robusto sul path source=input (DRAFT-005, NON RATIFICATO)

> ⚠️ **REFUTATO dall'n=3 (2026-08-02 alba)**: la stima `1-(1-p)^N` in questo doc ASSUME che gli N
> candidati del portfolio siano INDIPENDENTI. L'n=3 empirico (run6/7/8,
> `task452-results/verdict-portfolio-n3-2026-08-02.md`) ha mostrato che i 2 candidati CONVERGONO a
> hard near-identical in ogni round, perché partono dalla STESSA costruzione + PROMOTION (7504 pin
> identici) e la stessa LS B-TIMESLOT converge allo stesso ottimo locale: NON sono indipendenti, e
> `1-(1-p)^N` NON si applica (N=3 empirico = 33% pass vs teorico 87,5%). Portfolio ~= single solve,
> nessun miglioramento vs run4/run5 (1/2). Costo ~3x B-TIMESLOT non giustificato. **NON adottare.**
> Un multi-start VERO richiederebbe costruzioni indipendenti (seed diversi fin dalla fase CH, non
> solo nella LS B-TIMESLOT), ma l'**ExactFinisher** (TASK-478) supera il problema alla radice:
> enumera il residuo deterministicamente, non è soggetto all'ottimo locale. Il resto del doc resta
> come storico della teoria refutata.

> zai, 2026-08-01 notte7-bis. Da: ricerca esaustiva `research_hard-variance-timetabling_2026-08-01.md`
> (finding 1: portfolio/restart e' la cura canonica per heavy-tail variance) + verdetto run6
> (`task452-results/verify-source-input-run6/VERDICT-2026-08-01.md`: H1 confermato, portfolio size=2 = 0hard).
> **NON RATIFICATO**: il portfolio e' codice DRAFT-005 (gate laptop A/B decide), resta a maintainer. Questo doc e'
> la procedura operativa + la stima N, non un'adozione. Complemento (non sostituto) dell'ExactFinisher (TASK-478).

## COSA FA
`planner.portfolio.size=N` (DRAFT-005, `ScheduleService.portfolioSize()` riga 1352, `Integer.getInteger(...,1)`)
attiva il multi-start sulla fase B-TIMESLOT del ciclo staged: invece di 1 sola LS B-TIMESLOT, ne lancia **N
indipendenti** (seed fresco per ciascuna, NON_REPRODUCIBLE), poi **pickBest** (`picked best of N`) e continua
il ciclo staged (RELEASE -> HoleRepair -> AnchorRepair -> SurplusRebalance -> B-ROOM) sul migliore.
`size=1` (default) e' byte-identico al solve staged ordinario. L'interruttore e' LIVE per-build (lettura a
ogni solve), nessun rebuild per cambiarlo.

## PERCHE' (il problema che risolve)
Il path `source=input` ha hard-0 PROBABILISTICO: run4 (single) = 0hard, run5 (single) = -38hard (16 stranded,
treatment-test n=2 -> P(fail) single ~50%). La causa e' la distribuzione heavy-tailed della LS (ricerca
finding 1): una traiettoria subottimale strand i 16 su capacita' strette (T08/G03, T19/G06) NON
perche' siano infeasibili (lo spike TASK-478 AC#1 ha trovato 45h/377h di celle depth-0 collocabili), ma per
trappola dell'ottimo locale. N start indipendenti danno N shot a una traiettoria buona.

## COME SI LANCIA (operativo)
**REGOLA CRITICA:** il flag `-Dplanner.portfolio.size=N` va sulla riga `java -jar` (PLANNER_JAVA_OPTS), NON
in MAVEN_OPTS (MAVEN_OPTS resta nel JVM di Maven e non raggiunge l'app forkata - lezione CLAUDE.md).
Quindi: app lanciata via `prod-start-and-wait.sh` (java -jar diretto) o `java -jar` manuale, NON
`mvn quarkus:run`.

```bash
# 1. build jar da HEAD (--build-only, no restart)
./scripts/core/rebuild.sh --build-only

# 2. lancia app java -jar con il flag (e source=input) su DB production esistente (NO fresh reseed)
PLANNER_JAVA_OPTS="-Dplanner.portfolio.size=3 \
  -Dplanner.pre-assign.constrained-teacher.source=input \
  -Dquarkus.hibernate-orm.schema-management.strategy=update" \
  ./scripts/core/prod-start-and-wait.sh 300
# verifica banner: grep "Portfolio multi-start (DRAFT-005): size=3" /tmp/planner-prod.log

# 3. protocollo standard (apples-to-apples con run4/run5)
./scripts/api/api.sh reset
./scripts/api/import-calendario.sh          # 2859 pin MODA+Stage
./scripts/api/api.sh pre-assign-reset
./scripts/api/api.sh pre-assign              # +231 T01 (constrained-teacher default ON)
./scripts/api/api.sh wait 180                # pre-assign complete
./scripts/api/api.sh solve-staged            # avvia il ciclo (portfolio N su B-TIMESLOT)

# 4. presidia + cattura (polla stagedStage fino a null, poi capture-run)
./scripts/benchmark/portfolio-poll-capture.sh \
  claudedocs/spalmone-2026-27/task452-results/verify-source-input-runN 60
# produce: portfolio-candidates.txt (score per-start), timings.txt, score.txt, analysis.json,
# lessons.txt, KPI battery, confronto HTML.
```

## N RACCOMANDATO + STIMA P(fail)
Se P(success-hard-0 del singolo solve) = p (~0.5 da run4/run5 n=2), e gli N start del portfolio sono
indipendenti, allora P(almeno un hard-0 nel portfolio) approx 1 - (1-p)^N. Tabella (p=0.5, stima da n=2):

| portfolio size N | P(almeno un hard-0) | costo B-TIMESLOT (vs single) | run osservato |
|---|---|---|---|
| 1 (single) | ~0.50 | 1x (~17min) | run4=0, run5=-38 |
| 2 | ~0.75 | ~2x (~33min) | **run6=0 (1/1)** |
| **3 (raccomandato)** | **~0.875** | ~3x (~50min) | da misurare (n>=3 run) |
| 4 | ~0.94 | ~4x (~67min) | da misurare |

**Raccomandazione: N=3.** Raggiunge ~87.5% P(almeno un hard-0) al prezzo di ~3x B-TIMESLOT (~50min
B-TIMESLOT, ciclo totale ~60min). N=4 (94%) per margine extra se il flip m-13 diventa imminente e si
vuole robustezza massima. **La stima p=0.5 e' da n=2 (run4/run5)**: per una P(fail) Statistica del
PORTFOLIO stesso servono **n>=3 run indipendenti del portfolio** (es. 3 run a N=3), misurando la frazione
che raggiunge hard-0. Questo e' il gate statistico che il run6 (n=1) non copre.

## ONDE SI CAPTURA / COME SI LEGGE L'ESITO
- `portfolio-candidates.txt`: riga per ogni `portfolio B-TIMESLOT candidate k/N - score=...` + `picked best of N`.
  Se i candidati sono near-identical (come run6: -1831/-1832), la variabilita' e' bassa e N=2-3 basta; se
  divergono molto, la varianza e' alta e serve N maggiore.
- `score.txt`: hard finale (post-B-ROOM). **0hard = successo**; -Nhard = strand (guarda analysis.json per
  il breakdown H0/boxed/room-null).
- `lessons.txt` + `analysis.json`: i residui (quali docente/gruppo/materia strand), per confronto con run5
  (T08/T19) e per l'ExactFinisher (TASK-478).

## COSTO / TRADE-OFF (direttiva "correttezza logica vs costo")
- Tempo: ~+17min per ogni start in piu' (B-TIMESLOT domina). N=3 ≈ 60min/ciclo. Accettabile per il flip
  (input sostituira' golden come default; hard-0 robusto e' un requisito), non per il debug quotidiano.
- Medium: run6 N=2 = -1042k vs run5 single = -1012k (~-3%). Il portfolio ottimizza HARD (B-TIMESLOT tier),
  puo' sacrificare ~3% medium per garantire hard-0. Cercabile lato search (pesi/benchmark), non di modello.

## GATE / NON RATIFICA
- Il portfolio e' **DRAFT-005**: "NEVER adoption: laptop A/B x2 gate decides" (diagnosi run4/run5). Questa
  procedura e' il COME, non il SI. La decisione di ratifica (flip m-13 / adozione portfolio) e' di maintainer.
- Complemento dell'**ExactFinisher** (TASK-478, AC#1 feasible depth-0): il portfolio riduce P(strand),
  l'ExactFinisher chiude deterministicamente l'eventuale residuo. Insieme -> hard-0 robusto + deterministico.
- Link: ricerca `research_hard-variance-timetabling_2026-08-01.md`; verdetto run6
  `task452-results/verify-source-input-run6/VERDICT-2026-08-01.md`; diagnosi run4/run5
  `task452-results/run4-run5-variance-diagnosis-2026-08-01.md`; TASK-452 / TASK-476.
