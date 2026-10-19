# TASK-538 AC#2: mappa completa dei consumatori dello score (migrazione BendableScore 4 livelli)

Data: 2026-08-20. Metodo: grep meccanico su `src/main/java`, `src/test/java`, `src/main/webui/src`,
`scripts/`, `src/main/resources` con i pattern `HardMediumSoftScore`, `hardScore`, `mediumScore`,
`getScore`, `parseScore`, `hard/.*medium`, `timeslotTierHardScore`, `ROOM_TIER_HARD_FAMILIES`,
`ScoreManager`, `ConstraintMatch`, `STAGED_HARD_ACCEPT_THRESHOLD`. Nessuna modifica al codice.

Target: `HardMediumSoftScore` (3 livelli) -> `BendableScore` con 2 hard-level (veri-hard,
quasi-hard) + 2 soft-level (medium, soft). La domanda chiave per OGNI consumatore di hard e'
"conta solo i veri-hard oppure veri+quasi?".

Legenda classi: **(a)** interfaccia pubblica che cambia forma; **(b)** logica di confronto/gate
(deve decidere veri vs veri+quasi); **(c)** pura lettura display; **(d)** test golden-oracle /
snapshot che asseriscono punteggi.

---

## Stadio 0: dichiarazione del tipo (tutto si rompe qui per primo, tutto passa da qui)

| File | Classe | Punti-modifica | Note |
|---|---|---|---|
| `src/main/resources/solverConfig.xml` | (a) | 1 | `<scoreDirectorFactory>`: il tipo e' implicito dal constraint provider, nessun tag score type esplicito, ma l'initializing-score-trick commentato al riga 9 va riverificato sui docs Bendable. |
| `src/main/resources/solverBenchmarkConfig-2026-honest.xml` + `...AcceptedCountLimit.xml` | (a) | 9 scoreDirectorFactory (8+1) | Contengono anche score format literal nei benchmark (`5000hard/0medium/0soft`-style, vedi regola `.claude/rules/benchmark-xsd-validation.md`): ogni score string va riscritta in forma bendable `[a/b]hard/[c/d]soft`. |
| `src/main/java/.../domain/SchoolSchedule.java` | (a)(c) | ~4 | campo `score` (riga 1484), getter/setter (1594-1595), import. Punto unico di tipo per il planning solution. |

## Stadio 1: dichiarazioni dei constraint (62+ `.asConstraint`, meccanicamente neutre fino alla scelta veri/quasi)

| File | Classe | Punti-modifica | Note |
|---|---|---|---|
| `solver/SchoolScheduleConstraintProvider.java` | (a) | ~44 occorrenze hard/medium nel file; 44+ dichiarazioni `.asConstraint` | La parte piu' numerosa: ogni `penalizeHard`/`ofMedium`/`ofSoft` va mappato al livello giusto. La scelta SEMANTICA (quali ID diventano quasi-hard) e' input TASK-537, qui conta solo il censimento. Il file contiene anche i commenti-canonicali di `timeslotTierHardScore`/`ROOM_TIER_HARD_FAMILIES` (righe 968, 2078-2079) da aggiornare. |
| `solver/BaseConstraintProvider.java` | (a) | ~27 occorrenze | Blocchi condivisi (H1/H3/H7/H17 gate chain, H30, M-LF knob inline). Anche il javadoc `timeslotTierHardScore` (riga 588). |

## Stadio 2: meccanismi neutri (registrano/trascinano lo score senza decidere) - migrare presto, a rischio basso

| File | Classe | Punti-modifica | Note |
|---|---|---|---|
| `solver/PhaseTransitionMarker.java` | (c) | ~3 (righe 102-109) | Legge `solution.getScore()` e lo passa al progression service. Cast/tipo da cambiare, nessuna decisione. |
| `service/PhaseProgressionService.java` | (a)(c) | ~10 | `PhaseRecord.startScore/endScore`, ctor, `finalizeCurrentPhase`. Il campo `startScore: string` arriva anche in UI (`ProgressionPanel.tsx`, `types/domain.ts:500` che documenta il formato `-1000hard/-200medium/-300soft`). |
| `service/ConstraintMessageFormatter.java` | (c) | ~2 | Formattazione messaggi: mappa livello -> messaggio i18n, va aggiunto il ramo quasi-hard. |
| `service/ValidationService.java` + `ValidationSnapshotRepository.java` + `domain/ValidationSnapshot.java` | (a)(d) | ~10 (4+6+3) | ValidationSnapshot ha colonne `hardScore`/`mediumScore`/`softScore` (righe 36-40): SCHEMA DB. Con 4 livelli serve una colonna nuova (`quasiHardScore`) + migrazione schema (`schema-management=update` la crea, ma il codice che ricostruisce il report alla riga 108 hardcodla lista). |
| `rest/ScheduleResource.java` (analysis/feasibility JSON) | (a) | ~15 (righe 170-283) | Mappa JSON `hard/medium/soft` scritta in 2 posti (analysis + feasibility snapshot). Contratto pubblico consumato dalla UI: aggiungere `quasiHard` e' additivo, rinominare no. La feasibility (righe 252-283) pero' DECIDE (`hardScore == 0` riga 258, soglia medium riga 265): e' anche classe (b), va nel gate semantico. |
| `solver/multithreaded/MultiThreadedLocalSearchDecider.java` | (c) | ~2 | `stepScope.setScore/getScore` (righe 179, 223): polimorfico via Timefold, ma il ranking/acceptance custom se presente va ricontrollato sui tipi. |

## Stadio 3: gate e decisioni (il cuore: ogni sito deve dichiarare veri vs veri+quasi)

Tutti in `service/ScheduleService.java` (~43 occorrienze totali nel file; e' il file piu' critico insieme al constraint provider):

| Consumatore | Righe | Decisione da prendere |
|---|---|---|
| `STAGED_HARD_ACCEPT_THRESHOLD = -2` + gate B-TIMESLOT | 832, 1039-1044 | Il gate accetta `boxed==0 && hard >= -2`: i quasi-hard entrano nel budget o no? Proposta task: `0-veri-e-quasi<=budget`. |
| `timeslotTierHardScore()` + `ROOM_TIER_HARD_FAMILIES` | 857, 2488-2519, 2505 | Ricostruisce l'hard per famiglia da ConstraintMatchTotal: con Bendable va ricostruito su DUE hard-level; la lista famiglie va partizionata per livello. |
| `ensureImproveOrNoop` (5 call-site: hole repair 2196, lab-day 2220, composition 2242, anchor 2263, surplus 2290) | + ExactFinisher gate chain | Confronto `hardBefore` vs `after`: il revert scatta se peggiora l'hard. Quale hard? Se conta veri+quasi, la LS non puo' piu' fare i trade al margine che sono la RAGIONE del task: qui la semantica della "pazza idea" si vince o si perde. |
| Multi-start pick-best inline (`improvesBest`, `score.compareTo`, `score.hardScore()==0` stop) | 1996-2025 | `compareTo` bendable e' lessicografico su 4 livelli (ok nativo); lo stop-al-primo-0-hard e il retry-signature trigger (`hardScore() < 0` riga 2013) vanno ridefiniti: 0-veri con quasi residuo accettabile? |
| skip-doomed-room (`isDoomed` righe 1512-1548) | 1512-1548 | `timeslotTierHardScore < threshold` condanna il tentativo: stessa domanda del gate. |
| banner/stato `refreshSolverStatics` + `addH17Violations` + drill-down `addConstraintMatch` (righe 3611, 3710, 3733-3737) | ~8 | `level = hard<0 ? HARD : medium<0 ? MEDIUM : SOFT` (3734): serve il quarto ramo QUASI. `absScore` riga 3737 SOMMA hard+medium+soft: con 4 livelli e' una somma di mele e pere, da spezzare. |
| `solver/MultiStartRunner.java` | (b) | ~6 (righe 88-121) | cast + `score.hardScore()==0` (104): stop condition. |
| `solver/PortfolioPickBest.java` | (b) | ~4 | ranking `(hard, medium)`: con Bendable diventa (veri, quasi, medium) o (veri+quasi, medium). Ha test dedicato. |
| `solver/RetrySignature.java` | (b) | ~5 | classifica il breakdown hard per famiglia: input = mappa id->unita' hard; va esteso a distinguere veri/quasi. |
| `rest/dto/FeasibilityReport.java` | (a) | ~3 | payload stringa score + `isHardFeasible`: forma pubblica + decisione. |

## Stadio 4: UI (display + contratto JSON)

| File | Classe | Punti-modifica |
|---|---|---|
| `src/main/webui/src/types/domain.ts` | (a)(c) | ~8 (righe 233, 270, 281, 338, 350-359, 500): tutti i tipi `{hard, medium, soft}` e il commento sul formato score string. |
| `src/main/webui/src/components/AnalysisPanel.tsx` | (c) | ~12 (getConstraintType 35-36, getPrimaryScore 117-119, filtri, colori `score-hard-bg`): serve una quarta categoria QUASI con colore/badge. |
| `src/main/webui/src/components/UnifiedSolverControls.tsx` + `ValidationBadge.tsx` + `ScheduleSidebar.tsx` + `ProgressionPanel.tsx` | (c) | ~2-4 ciascuno: badge "0-hard e' binario" (direttiva 2026-08-02) va ridefinito con budget quasi; score string display. |
| `src/main/webui/src/api/scheduleApi.ts`, `benchmarkApi.ts`, `types/benchmark.ts`, `data/benchmarkData.ts`, `components/benchmark/BenchmarkTable.tsx`, `BenchmarkResultsPage.tsx` | (a)(c) | ~6 file x 1-3: parser/formati benchmark (i benchmark XML generano score bendable, i risultati storici 3-livelli restano leggibili? da decidere: solo forward). |
| Minori (solo menzioni/legenda): `LessonTooltip.tsx`, `cellRenderer.tsx`, `timeline/types.ts`, `useLessonViolations.ts`, `useScheduleData.ts`, `theme.tsx` (variabili colore), `ComplexityPage.tsx`, `DocsPage.tsx`, `ConstraintCard.tsx`, `SolverPhasesPage.tsx`, `data/constraints.ts`, `data/solverPhases.ts` | (c) | 1-2 ciascuno, prevalentemente legende e testi. |

## Stadio 5: parser KPI e script (interfacce testuali)

| File/script | Classe | Punti-modifica |
|---|---|---|
| `scripts/benchmark/check-regression.py` (+ `test_check_regression.py`) | (a)(d) | regex `_SCORE_RE = score:\s*(-?\d+)hard/(-?\d+)medium/(-?\d+)soft` (riga 143): il formato del file `score.txt` cambia con Bendable. Anche i cap `hard/medium` (righe 210). |
| `scripts/analyze/compare-runs.py`, `score-stream-separation.py`, `spalmone-report.py`, `spalmone-validate.py`, `btimeslot-bench-summary.py`, `badseed-frequency-stats.py`, `chain-feasibility-kpi.py`, `solution-sanity-check.py`, `feasibility-audit.py`, `teacher-tier-kpi.py`, `curriculum-poststage-kpi.py` | (a) | 1-3 ciascuno: chi parsea `Xhard/Ymedium/Zsoft` si rompe silenziosamente (regex non matcha -> 0), chi lo produce va aggiornato. |
| `scripts/benchmark/*.sh` (~25 script: capture-run.sh, multistart-ab.sh, run-experiment.sh, benchmark-progress-log.sh, task4xx-*.sh...) | (a) | 1-2 ciascuno: grep/awk su `hard` nei log e score.txt. NOTA: la maggior parte sono ISTANZE STORICHE di run passati: da migrare solo gli script ancora in uso operativo (capture-run.sh, multistart-ab.sh, api.sh, check-regression.py sono i vivi). |
| `scripts/api/api.sh` (cmd `score`, righe 232-238) | (c) | 1: pass-through della stringa score, non si rompe ma il formato cambia a valle. |

## Stadio 6: test (golden-oracle e snapshot)

| Test | Classe | Note |
|---|---|---|
| `bootstrap/GoldenSolutionRegressionTest.java` | (d) | 35 occorrenze: IL golden-oracle. Pinna punteggi per constraint del golden: ogni assert va riclassificato veri/quasi secondo TASK-537 (il golden deve restare 0-veri; i quasi del golden diventano il budget). |
| `bootstrap/*SnapshotTest` / `*CheckpointTest` / `*SpikeTest` / `*DiscriminatorTest` (BoxRepairCheckpoint, Phase2SmokeSnapshot, RoomPhaseSnapshot, ScoreASnapshot, Task403/415/419, H17HoleClose, KempeChain, LateStartSwap, HumanFaithful, WeeklyTemplateConstructionRealSeed, ConstraintProfilingLeaveOneOut) | (d) | 2-6 occorrenze ciascuno, ~15 file: asseriscono triple hard/medium/soft pinnate. Strategia consigliata: helper unico `assertBendable(veri, quasi, medium, soft)` invece di 60 edit sparse. |
| `solver/*ConstraintTest` (SchoolScheduleConstraintProviderTest, CoPresence, CurriculumPostStage, LabTagCapacity, TheoryAdjacent) + `MultiStartRunnerTest`, `PortfolioPickBestTest`, `multithreaded/*Test` | (d) | 2-9 ciascuno: verificatori ConstraintVerifier, la forma `penalizeBy`/assert cambia con il tipo bendable (API verifier a 4 livelli). |
| `integration/SchoolScheduleSolverIntegrationTest`, `rest/ScheduleResourceTest` | (d) | 4 ciascuno: end-to-end, seguono il JSON. |

---

## Ordine di migrazione consigliato (safe, con test verdi a ogni stadio)

1. **Stadio 0-1 dietro ramo**: cambio tipo in SchoolSchedule + solverConfig + le 62+ dichiarazioni,
   mantenendo la semantica identica (tutto cio' che era hard -> hard-L0 veri, medium -> soft-L0).
   A questo punto BendableScore con [1,1] livelli e' isomorfo al 3-livelli: TUTTI i test devono
   restare verdi (a parte i formati stringa: `-5hard/...` diventa `[-5]hard/...`). E' il test
   di reversibilita' della meccanica.
2. **Stadio 2 (meccanismi neutri)**: marker, progression, validation snapshot (+ colonna DB),
   formatter, JSON additivo (`quasiHard` accanto a `hard`).
3. **Stadio 3 (gate)**: UNA decisione di semantica per sito, ognuna col suo test TDD prima
   (direttiva maintainer nel task): gate B-TIMESLOT, ensureImproveOrNoop, pick-best/stop-0-hard,
   skip-doomed, RetrySignature, feasibility.
4. **Stadio 4-5 (display + parser)**: UI (badge budget-quasi) e parser KPI; qui il formato
   `score.txt` cambia e check-regression.py va aggiornato nello stesso commit di qualunque run A/B.
5. **Stadio 6**: rinormalizzazione dei test golden-oracle col budget quasi di TASK-537.

## I tre rischi veri

1. **`ensureImproveOrNoop` + gate** (`ScheduleService` righe 830-2548): se i quasi-hard contano
   come hard nei guard, il beneficio del 4-livello e' nullo (stessa rigidita'); se non contano,
   un run puo' consegnare con quasi-hard illimitati. Serve il budget esplicito, non un booleano.
2. **Formato stringa score** (`score.txt`, JSON, UI, ~15 parser python/bash): la regex di
   check-regression.py fallisce SILENTE (score non matchato) se il formato cambia senza la
   regex: un A/B post-migrazione produrrebbe KPI vuoti scambiati per zeri.
3. **ValidationSnapshot/DB**: e' l'unico consumatore con stato persistente; snapshot storici
   a 3 colonne letti da codice a 4 livelli (riga 108 del repository) vanno gestiti con default
   0 sul quasi-hard, altrimenti la pagina validazione rompe su dati vecchi.

## Totale consumatori (files)

- Java main: 15 files (~180 occorrenze score, di cui ~150 nel provider + ScheduleService).
- Java test: 25 files (~120 occorrenze assert/formato).
- Frontend: 18 files coinvolti (~35 punti-modifica reali).
- Scripts: ~35 files, di cui ~8 "vivi" (check-regression.py, api.sh, capture-run.sh,
  compare-runs.py, multistart-ab.sh, score-stream-separation.py, badseed-frequency-stats.py,
  spalmone-report.py) e il resto istanze storiche.
- Config XML: solverConfig.xml + 2 solverBenchmarkConfig (9 scoreDirectorFactory + score strings).

Stima punti-modifica totali: ~300, di cui ~120 meccanici (dichiarazioni provider), ~100 test,
~25 gate semantici (i soli che richiedono judgement), ~55 display/parser.
