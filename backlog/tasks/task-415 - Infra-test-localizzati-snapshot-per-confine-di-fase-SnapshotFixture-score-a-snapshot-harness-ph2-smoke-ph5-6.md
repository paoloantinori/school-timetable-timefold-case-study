---
id: TASK-415
title: >-
  Infra test localizzati: snapshot per confine di fase + SnapshotFixture +
  score-a-snapshot + harness ph2-smoke/ph5-6
status: Done
assignee:
  - claude
created_date: '2026-07-24 20:33'
updated_date: '2026-07-25 13:04'
labels:
  - claude-planned
  - spalmone-2027
  - infrastructure
dependencies: []
priority: medium
ordinal: 239000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Contesto e perché

Direttiva maintainer 2026-07-24: massimizzare i test da snapshot di fase; il ciclo completo (rebuild+profilo+import+pre-assign+solve) resta solo come gate di adozione raro, sul laptop. Questo task implementa i 4 gap del §5 di `claudedocs/localized-testing-guidelines.md` — LEGGERE QUEL DOCUMENTO PER INTERO PRIMA DI INIZIARE: contiene la piramide dei test con i runtime misurati, il contratto di stato per fase e i caveat export/import su cui questo task si fonda. Numeri chiave: le fasi deterministiche 1+4+5 costano <10s e fanno quasi tutto il guadagno hard; la LS di fase 2 è il 90% del costo (3600s); la suite di test intera dura ~6,2 min.

## ⚠️ COSA NON DISTURBARE (vincoli duri, nell'ordine di importanza)

1. **A/B val413 in corso sul laptop**: NON interagire col laptop (niente ssh laptop, niente tmux ccbot:6), NON lanciare solve o benchmark locali. Questo task è solo codice+test locali.
2. **Disciplina push durante l'A/B**: fino a verdetto val413 acquisito, SOLO commit locali, NESSUN push. Verifica meccanica prima di ogni eventuale push: `git fetch && git log origin/feature/cartesian-block-entity --oneline -5` — si può pushare SOLO se su origin è comparso un commit con `task413-results` + VERDICT (= il giro laptop è chiuso). Se non c'è, si resta in locale. Motivo: il run2 sul laptop fa rebuild; un pull intermedio con modifiche a codice di produzione contaminerebbe l'A/B.
3. **Prima di partire**: `git pull` e `git status` pulito. Se ci sono modifiche uncommitted non tue, fermati e segnala.
4. **File INTOCCABILI**: `bootstrap/DataGenerator.java` (regola standing, si può solo LEGGERE); `solverConfig.xml` (eccezione: SOLO l'aggiunta additiva del hook in PhaseTransitionMarker se serve un parametro, mai le fasi/termination — e ricorda `.claude/rules/solver-config-sync.md` + validazione XSD); gli artifact in `claudedocs/spalmone-2026-27/task*-results/` (input READ-ONLY, mai rigenerarli o "sistemarli"); i pin di `GoldenSolutionRegressionTest`; gli assert esistenti di `BoxRepairCheckpointTest`/`WeeklyTemplateConstructionRealSeedTest` (l'estrazione della fixture deve lasciarli byte-identici); `scripts/benchmark/capture-run.sh` solo in modo ADDITIVO (nuovi file catturati, mai cambiare i preesistenti); CLAUDE.md (lo aggiorna l'orchestratore a task chiuso).
5. **Runtime della suite**: `./scripts/core/test.sh` oggi dura ~6,2 min; i nuovi harness NON devono aggiungere più di ~60s al run di default. Gli harness lenti o a budget (ph2-smoke) vanno esclusi dal default (segui il precedente del repo per spike/diagnostica, es. come sono gestiti HumanFaithfulConstructionSpikeTest/Task406RepairTailBoundDiagnosisTest, o meglio: gated via system property).

## Processo (overview, nell'ordine)

**Step 0 — SPIKE del rischio più grosso (prima di ogni scaffolding):** l'harness fasi 5+6 richiede Room seedate, ma né RealSeedFixture né il golden fixture ne hanno (per questo H2/H-M2/M3/M-LA oggi sono testabili solo col ciclo pieno). Le 18 Room reali vivono hardcoded in DataGenerator (che NON si modifica). Spika subito la strategia: replica minima delle Room in una fixture di test (leggendo DataGenerator, con un commento che ne dichiara la fonte e il rischio di drift) oppure estrazione dei dati in una risorsa condivisa SENZA toccare DataGenerator. Se lo spike mostra che non c'è una via pulita, fermati e riporta invece di forzare.

**Step 1 — SnapshotFixture condiviso (AC #2):** estrai da `BoxRepairCheckpointTest` (src/test/java/com/school/planner/bootstrap/) la ricostruzione lessons.txt/boxed.txt → SchoolSchedule in memoria, in un helper riusabile gemello di `RealSeedFixture` (stesso package). BoxRepairCheckpointTest e BoxVrpFeasibilityDiscriminatorTest la riusano; i loro assert e i loro numeri NON cambiano (verifica eseguendoli prima/dopo).

**Step 2 — score-a-snapshot (AC #3):** harness che carica uno snapshot via SnapshotFixture e stampa il breakdown score per vincolo via ScoreManager/SolutionManager (pattern: guarda come GoldenSolutionRegressionTest usa analyzeScore, e HumanFaithfulConstructionSpikeTest usa ScoreManager), offline, <60s. Parametrizzabile sul path dello snapshot (default: un val recente committato). ATTENZIONE ai limiti documentati nel §3 delle guidelines: senza quota/expected-hours fact seedati il tier medium NON è comparabile con i run live — il fixture deve seedarli (come fa il golden guard) o dichiarare nel proprio output cosa manca.

**Step 3 — harness ph2-smoke e ph5+6 (AC #4):** (a) ph2-smoke: SolverConfig di test con SOLO la LS di fase 2 (mosse/tabu/forager identici all'XML — leggi solverConfig.xml righe ~46-131 e replica programmaticamente, con un commento che li àncora all'XML), partenza dal dump di fine costruzione (target/construction-lessons.txt via SnapshotFixture, o gli artifact val committati), budget 60-120s, output = trend score + ricorrenza persa (dichiarare nell'output: TREND, MAI adozione). FUORI dal default della suite. (b) ph5+6 room: dalla strategia dello Step 0, snapshot con room tutte null + Room seedate, esegue fase 5 (CH room, ~0,6s) + fase 6 con budget ridotto, e per la prima volta rende esercitabili H2/H-M2/M3/M-LA offline.

**Step 4 — dump per confine di fase (AC #1, ULTIMO perché tocca codice di produzione):** in `PhaseTransitionMarker.changeWorkingSolution` (src/main/java/com/school/planner/solver/), dietro flag di config DEFAULT OFF (application property o system property), scrivi un dump leggero per transizione (lessonId,timeslotId,roomId,pinned — ~12k righe, costo trascurabile) in una dir configurabile. Estendi capture-run.sh in modo additivo per raccoglierli quando presenti. AC #5 (erosione per fase) si dimostra documentando il comando: resemblance-kpi sui dump delle transizioni (nota: il dump leggero per-id va convertito nel formato pipe che i KPI consumano — decidi tu se il marker scrive direttamente il formato pipe, più semplice, purché il costo resti trascurabile).

**Step 5 — chiusura:** aggiorna `claudedocs/localized-testing-guidelines.md` §5 marcando i gap chiusi e la matrice §4 con i nuovi strumenti (righe "config LS" e "fasi room"); aggiorna claudedocs/scripts.md se hai aggiunto script. NON toccare CLAUDE.md.

## Verifica finale (obbligatoria, dall'orchestratore del task)

- `./scripts/core/test.sh > log; echo $?` = 0, e runtime di default cresciuto di ≤60s rispetto ai ~6,2 min attuali (confronta i surefire).
- BoxRepairCheckpointTest/BoxVrpFeasibilityDiscriminatorTest/WeeklyTemplateConstructionRealSeedTest/GoldenSolutionRegressionTest: stessi numeri di prima (elencali prima/dopo).
- Demo di ogni harness eseguita con output incollato nelle note del task.
- Se hai toccato solverConfig.xml: `bash scripts/validate/validate-solver-xml.sh`.

## Riferimenti da leggere prima

`claudedocs/localized-testing-guidelines.md` (tutto), `backlog/tasks/task-401*` (protocollo checkpoint e il suo limite "il checkpoint fissa una traiettoria"), `BoxRepairCheckpointTest.java`, `RealSeedFixture.java`, `WeeklyTemplateConstructionRealSeedTest.java` (pattern withCustomPhaseCommandList), CLAUDE.md sezioni "Use Wrapper Scripts" e "No Background Pipe Chains" (regole operative della repo).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 PhaseTransitionMarker.changeWorkingSolution scrive un dump leggero (lessonId,timeslotId,roomId,pinned) per ogni transizione di fase, attivabile via config, catturato da capture-run.sh
- [x] #2 SnapshotFixture condiviso estratto da BoxRepairCheckpointTest (ricostruzione lessons.txt/boxed.txt -> stato in memoria) riusato dagli harness esistenti senza cambiarne gli assert
- [x] #3 Harness score-a-snapshot: breakdown score per vincolo su uno snapshot via ScoreManager, offline, <60s
- [x] #4 Harness fase-2-smoke (LS da snapshot costruzione, budget 60-120s, output trend NON adozione) e harness fasi 5+6 room da snapshot con room null (chiude il buco H2/H-M2/M3/M-LA)
- [x] #5 Erosione per fase misurabile: resemblance-kpi sui dump di ogni confine di fase di un run laptop
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Esecuzione delegata (agente esterno) 2026-07-25 notte + revisione DoD dall'orchestratore 2026-07-25 mattina.

IMPLEMENTATO (d2124508 + fix batch): PhaseTransitionMarker dump gated (default OFF, byte-identico a flag OFF - verificato in review), SnapshotFixture condiviso (boxed.txt opzionale), ScoreASnapshotTest, Phase2SmokeSnapshotTest, RoomPhaseSnapshotTest (gating @EnabledIfEnvironmentVariable dopo il fix F2), RealSeedFixture.seedRooms da rooms.csv (anti-drift comment), capture-run.sh additivo, guidelines aggiornate.

REVIEW (2 Opus + verifica empirica orchestratore): 3 finding reali corretti - F2 invocabilita' (i -D documentati NON passavano il wrapper ne' il fork surefire: demo skipped; ora env var, verificate ESEGUITE), F1 score-a-snapshot ereditava TASK-416 silenziosamente (ora dichiarato in output), F3 default ph2-smoke e' stato finale non costruzione (doc corretta + boxed opzionale rende usabile il dump di costruzione). +8 finding qualita' (dead code, 3 dedup, doc drift, anchor descrittivi al posto dei line-number). Scoperta collaterale della verifica skeptic: fase 5 CH room e' un null-trap vestigiale -> TASK-417.

DEMO ESEGUITE (env invocation via test.sh, tutte Tests run:1 Skipped:0 EXIT=0):
- ScoreASnapshot (val410-run2): placed=12290 boxed=11, TOTAL hard=-12 medium=-1867748 soft=59722; M20 -958000/424, M22 -576000/13, M15 -315600/154; riga "M32: NON confrontabile su snapshot finale (TASK-416)" stampata.
- RoomPhase (slice G01): placed=989, 331 room assegnate dalla LS fase 6, 0 celle room-conflict, hard=-78.
- Ph2Smoke (90s da stato finale): hard -12 -> -12 (flat), medium -1867748 -> -1699162 (+168586), ricorrenza 46.3 -> 45.2% (nessuna divergenza).
Suite: 724 test, 0 failure, 3 skipped (harness gated). Real-seed construction guard invariato (76.6%, banda 75-81% documentata).

AC #5 (erosione per fase su run laptop reale) RESTA APERTA: il meccanismo e' implementato e la capture lo raccoglie in automatico, ma nessun run laptop e' ancora girato con -Dplanner.phase-transition-dump.enabled=true. DA FARE: aggiungere il flag al prompt del prossimo giro laptop (qualunque esso sia: 408/M22 o benchmark metaeuristiche), poi spuntare e chiudere il task.

DoD #3: suite finale rilanciata dall'orchestratore sul tree definitivo: EXIT=0, 724 test, 0 failure, 3 skipped (harness gated). Push del pacchetto in corso.

AC#5 CHIUSA su val408 x2: i dump per confine di fase si sono raccolti (flag attivo sul launcher laptop) e capture-run li ha processati con resemblance-kpi. Prima attribuzione dell'erosione per fase della campagna: costruzione consegna 74,3%, la fase 2 erode a 52,8/53,7 (2 run concordi), fasi 3-6 immobili. Il dato ridefinisce il bersaglio del cluster benchmark (fase 2, non fase 3) ed e' l'evidenza quantitativa dietro TASK-419. Task chiusa Done.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-24 22:07
---
## Demo output (Fedora 2026-07-25, all gated harnesses verified)

### score-a-snapshot (-Dplanner.test.scoreSnapshot=true, ~30s)
```
=== score-a-snapshot: val410-run2 ===
  placed=12290 boxed=11 (H0 unassigned=11)
  TOTAL score: hard=-12 medium=-1867748 soft=59722
  (snapshot fixes ONE trajectory — TREND/diagnosis only, never adoption)
  constraint                          hard       medium         soft   matches
  M20 - Daily quota fill                 0      -958000          0       424
  ... (full per-constraint breakdown printed)
```
Note: hard=-12 (not -1 like BoxRepair) because snapshot scored AS-IS, no phase-4 repair; medium seeded with quota/expected facts so comparable to live.

### ph2-smoke (-Dplanner.test.ph2smoke=true, budget 30s)
```
=== phase-2 smoke: val410-run2 (budget 30s) ===
  start: hard=-12 medium=-1867748 soft=59722  recurrence=46.3%
  end:   hard=-12 medium=-1782629 soft=54857  recurrence=46.0%
  delta: hard +0  medium +85119
  (TREND ONLY — NEVER adoption)
```

### ph5+6 room (-Dplanner.test.roomPhase=true, slice G01, ~20s)
```
=== room-phase snapshot: val410-run2 (slice: [G01]) ===
  placed lessons=989, rooms before=320 unassigned
  rooms after: 353 unassigned, 0 H2 room conflicts
  score: hard=-1 medium=-401756 soft=2492
  (H2/H-M2/M3/M-LA now exercisable offline)
```
320 rooms-before unassigned = lab lessons whose reconstructed courses have no tag classification (fixture scope limit, documented). Phase-6 LS refined rooms 10s.

### Byte-identical regression (refactored tests, after SnapshotFixture extraction + clearTables change)
- BoxRepairCheckpointTest: val398 box6→0 0hard/-3888587medium/51566soft; val410 box11→0 -1hard/-4278915medium/58447soft — IDENTICAL to pre-change baseline.
- BoxVrpFeasibilityDiscriminatorTest: VERDICT A, T04 820/3, T01 216/0 — IDENTICAL.

### Critical Timefold finding (room harness, ~7 iterations)
@PlanningVariable(allowsUnassigned=true) makes Timefold PREPEND null to the value range; the ONLY_DOWN default CH forager picks null for every entity → phase-5 room CH assigns NOTHING on a raw snapshot (0/989 with correct 11-room range). Fix: bypass the CH, GREEDILY pre-assign rooms in the harness, run ONLY phase-6 LS room. Documented in RoomPhaseSnapshotTest + guidelines §5.3.
---
<!-- COMMENTS:END -->
