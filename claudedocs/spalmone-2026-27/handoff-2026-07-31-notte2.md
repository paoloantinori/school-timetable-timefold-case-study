# HANDOFF notte 31/07 (zai, programma ampliato - continuazione) - run2 ON in volo

HEAD `feature/cartesian-block-entity` (tutto pushato, ultimo 8dc0d7f9). App UP su production
con `-Dplanner.construction.first-hour-slot.enabled=true` (run2 catturato). Riprendi da qui col /clear.

## AGGIORNAMENTO 00:56 (post run2 + diagnosi)
- **run2 ON completato e catturato** (`task451-results/ab-firsthour-on-run2/`): -109hard, conferma
  run1 (n=2 stabile). Verdetto (A) in `task451-first-hour-slot-2026-07-30.md` + QUADRO.
- **DIAGNOSI 18 buchi H17 FATTA** (`spike-h17-holes-g13-friday-2026-07-31.md`): UN pattern (G13
  ven slot 11, lab meccanica 5h), curabile a valle. QUADRO colonna 2 aggiornato. Affila la decisione (A) di maintainer.
- **(A) e (B) CHIUSI e pushati** (a92d4b9e DoD, bb7bfd2b run1, 6d6dde5e run2, 9521939f B, 8dc0d7f9 diagnosi).
- **(C) chiarito (grep runStagedSolver fatto):** il ciclo staged CARICA solverConfig.xml
  (`@ConfigProperty quarkus.timefold.solver.solver-config-xml` ScheduleService:733) e seleziona le fasi
  per stadio (B-TIMESLOT-1 = phase-2 LS). Phase-2 LS corrente = **tabu entity-7** (solverConfig.xml:124,
  acceptedCountLimit 1000, secondsSpentLimit 3600, unimproved 600). **Quindi i bracci (C) via variazione
  phase-2 LS in solverConfig.xml SONO VALIDI per il B-TIMESLOT-1 staged** (generate-benchmark-arm.py
  patcha solverConfig.xml: tabu-7 [corrente] vs late-acceptance size 400/1000 vs tabu size 3/11).
  Protocollo: laptop-RUNBOOK sequenziale, validate-solver-xml.sh dopo ogni XML, capture B-TIMESLOT-1 end
  score da phases.json/phase-marker log, budget identico dichiarato per braccio. Phase2SmokeSnapshotTest
  (PLANNER_TEST_PH2SMOKE=1, 90s) per scremare offline prima del laptop.
- **(D) prossimo:** wrapper portfolio in `runStagedSolver` dietro `-Dplanner.portfolio.size` default 1.
  Test gating "solve() muta l'input?": confermare empiricamente (design DRAFT-005 lo assume: clone via
  export/import TASK-401). Stanotte non iniziato (budget): sessione fresca.


## FATTO e CHIUSO stanotte (pushato)
- **(A) TASK-451 DoD PIENA** (commit a92d4b9e): /simplify + /code-review high multi-agente sul diff
  92df21e7. 3 fix codice (verificati, test.sh BUILD SUCCESS 886/0/0):
  - **Corr1**: sort tiebreaker course.id -> `semanticCourseKey` (L2 determinismo, 75-81% varianza run-to-run).
  - **Corr2**: `dailyTargetHours` -1 sentinella non piu' skip (<2 -> `>=0 && <2`, hoistato).
  - **Clean1**: 4 copie inline teacher-id -> `teacherIdOf` (byte-identico, single-source).
  - **Debito**: `loadGoldenSlot0Anchors` censito in TASK-452 #6 (nuova lettura golden runtime su percorso costruzione).
  - Scarti con motivazione: parameterizzazione C2, side-effect Clean3, doppio parse Clean4, countInDay Clean5.
- **(A) A/B run1 ON vs 30d OFF** (commit bb7bfd2b, capture `task451-results/ab-firsthour-on-run1/`):
  - **WIN**: hard -173->-108, boxed 3->0, room-null 167->90, ricorrenza UNP 74.3->75.6%, e **AZZERA il
    difetto primario day-start G11 ven (16 late -> 2)**, anchored 99.3->99.9%.
  - **CAVEAT**: 18 buchi H17 HARD mid-day (0->18). (A) chiude late-start M18 (medium) ma apre holes H17
    (hard): tier-regression locale. Decomposizione hard ON: -108 = H29 room-null -90 + H17 holes -18 + H0 boxed 0.
  - Verdetto: numeri reggono (con caveat holes). Dettaglio in `task451-first-hour-slot-2026-07-30.md` sezione "A/B laptop run1".
- **(B) T19/Q22** (commit 9521939f): soglia `CONSTRAINED_TEACHER_MAX_WEEKDAYS` live-configurabile
  (`planner.pre-assign.constrained-teacher.max-weekdays`, default 1 inerte, 2 -> T19 2-weekday incluso
  nello STESSO phase-0). Unit test PASSA. Review CLEAN (nessun bug, nessun nuovo debito golden).
  DEFERITO: full-solve A/B T19 (richiede slot solve + stanze produzione; bloccato su risposta scuola Q22).

## IN VOLO: run2 ON di conferma (lancio 00:08:00, ~46min -> ~00:54)
- Braccio OFF = 30d, run1 ON = `task451-results/ab-firsthour-on-run1/`. Run2 = conferma stabilita' dei 18 H17 holes.
- **COME FINIRE run2** (l'app gira gia' col flag ON, profilo production fresco, T01 231 pinnato):
  1. Poll: `./scripts/api/api.sh status -j | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('stagedStage'),d.get('score'))"`
     fino a `stagedStage=None`. (caffeinate attivo fino a ~00:17; se run2 finisce dopo, rilancia `caffeinate -d -i -t 3600 &`.)
  2. Capture: `./scripts/benchmark/capture-run.sh "claudedocs/spalmone-2026-27/task451-results/ab-firsthour-on-run2"`
  3. Confronta vs run1 e 30d: `kpi-holes.txt` (BUCO-INTERNO: run1=18, 30d=0), `kpi-daystart.txt` (late: run1=2, 30d=16),
     `analysis.json` hard breakdown (H17 holes), `kpi-resemblance.txt`, full-day.
  4. Verdetto 3 righe in `task451-first-hour-slot-2026-07-30.md` (sezione "A/B laptop run2"):
     - Se holes ~18 stabili -> (A) adottabile col caveat ( maintainer decide): 18 mid-day gap HARD vs 16 late-start MEDIUM.
     - Se holes ~0 o molto <18 -> run1 era varianza, (A) netto-positivo pulito.
  5. Commit+push. Aggiorna QUADRO.

## DA FARE (dopo run2, in ordine di valore)
- **(C) Benchmark metaeuristiche B-TIMESLOT-1**: bracci sequenziali (config corrente tabu entity-7 vs
  late-acceptance 2-3 size vs tabu 2 size), budget identico per braccio, UN solve alla volta.
  Phase2SmokeSnapshotTest (PLANNER_TEST_PH2SMOKE=1, 90s) per scremare. XSD validation dopo ogni XML.
  Protocollo: laptop-RUNBOOK, generate-benchmark-arm.py. Performance = problema di RICERCA (direttiva primaria).
- **(D) DRAFT-005 portfolio stadio-scoped** (design letto in `backlog/drafts/draft-005`): MAI N cicli
  interi in-JVM (statici mutano tra stadi -> corruzione). Wrapper in `ScheduleService.runStagedSolver`
  dietro `-Dplanner.portfolio.size` default 1. PRIMA il test 5-min "solve() muta l'input?": se si',
  clona via export/import TASK-401. Stanotte solo build (wrapper + unit test pick-best + flag OFF).
- **(E) Profiling leave-one-out + A/B isG14 + A/B M20-1500** (se resta notte): vedi DRAFT-005 (2) e
  spike-s1/s3. isG14 e M20-1500 sono NO-OP al checkpoint (LS-only), serve full-solve.

## Standing
- Flag default-OFF ovunque, NESSUNA adozione (A e B sono prototipi misurati, decisione a maintainer).
- DoD piena su ogni incremento. Commit+push a ogni chiusura. Verdetti in 3 righe con numeri dai raw.
- Braccio OFF per (A) = 30d (`certification-2026-07-30d/`): hard -173, boxed 3, room-null 167, full-day 89.8%, ricorrenza 74.3%, day-start 16 late (G11 ven), holes 0.
- App launch per run ON: `PLANNER_JAVA_OPTS="-Dplanner.construction.first-hour-slot.enabled=true" ./scripts/core/prod-start-and-wait.sh 240` (mai MAVEN_OPTS). Profilo production fresco: kill-app + stop + delete production + restart.
