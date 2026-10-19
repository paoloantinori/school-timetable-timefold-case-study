# HANDOFF notte 30->31/07 (zai) - programma ampliato, contesto esaurito

HEAD `feature/cartesian-block-entity` (tutto pushato). App UP su dev (rebuild fatto a 22:26).
Questo handoff parte dal "PROGRAMMA NOTTURNO AMPLIATO" di maintainer (30/07 sera). Contesto
esaurito dopo (A); (B)/(C)/(D)/(E) restano da fare. Fai `/clear` e riprendi da qui.

## FATTO e CHIUSO stanotte (pushato)
- **Spike 1 forma-deficit vs deriva tier** (447/30b/30d): DISTINTA come causazione. 85-88%
  delle celle deep ha il gruppo AVANTI sul monte-ore (non scarsita'); drenaggio generici
  globale non causa. `spike5-forma-deficit-vs-tierdrift-2026-07-30.md`. Commit 5b48bca0.
- **Spike 2 modo B su construction-end reale 30c (phase-2)**: BUTTARE leva separata. 18
  front-gap>=2, sopravvivenza al finale 1/18 (AnchorRepair li chiude). Modo B reale = G04
  ven (non G11 mer del test-harness). `spike6-modob-30c-phase2-2026-07-30.md`.
- **HTML orario**: esiste, committed su 30e (commit 433716a5, shape migliore), NON revertito
  a 30d (regola surface-overwrite). Link + flip-a-30d one-liner in QUADRO "Come riprendere".
- **(A) TASK-451 blocco prima-ora slot0-1 coordinato** (commit 92df21e7 + QUADRO 92faed9d):
  PROTOTIPO flag `planner.construction.first-hour-slot.enabled` default OFF. `reserveFirstHourBlock`
  generalizza `reserveSlot0Anchors` (1h greedy -> 2h slot0+slot1, docente=anchor slot0 modale
  golden, matching cross-gruppo). Misura costruzione OFF->ON: **hole slot1 53(2,7%)->25(1,3%)**,
  front-gap h9 28->19, conflitti 0/0, skipConflict 0, recurrence 76,5%->76,1%. front-gap>=2 h10+
  invariato a 9 (modo B). test.sh EXIT 0 (886). Review (focused, budget-adapted) CLEAN: nessun
  bug confermato (golden parse, H7/H11 +2, weeklyTargets non negativo, no double-book, TemplateCell
  (0,2) stamps 0-1, no null-deref). `/code-review high` e `/simplify` pieni (multi-agente) NON
  runnati per budget: il diff e' picccolo e specchia reserveSlot0Anchors, ma andrebbero rifatti
  con budget fresco. TENERE al checkpoint, **gate = A/B full-solve laptop x2** (flag OFF vs ON).

## DA FARE (in ordine di valore, come da mandato)

### (B) Coordinazione teacher-day Q22, ramo "vincolo reale", primo caso T19 lun+mar
- **Sappiamo:** `spike-g06-barberia-t19-2026-07-30.md`: T19 vincolato lun+mar (Q22), 271
  celle col docente libero ma solo 2 col gruppo libero, nessun blocco hard, golden le colloca
  tutte. Stesso family della tripletta (scarsita' congiunta risorsa-giorno).
- **Insight chiave per non sbagliare:** `ConstrainedTeacherAllocator` (TASK-435) GIA' pinna i
  docenti "constrained" (`CONSTRAINED_TEACHER_MAX_WEEKDAYS = 1` in HybridPhase1aService:95),
  leggendo il profilo golden. **T19 ha 2 weekday (lun+mar), quindi e' ESCLUSO dalla soglia =1.**
  (B) = estendere quel meccanismo (Phase 0) ai docenti 2-weekday, NON riscriverlo: alza la
  soglia a 2 dietro flag, o un allocator sibling `Q22ConstrainedTeacherAllocator`. L'infrastruttura
  (loadAllGoldenProfiles, runConstrainedTeacherPhase, persistPinnedLesson, canPinHardConstraints)
  e' riusabile cosi' com'e'.
- **Misura al checkpoint** (WeeklyTemplateConstructionRealSeedTest export-mode, come (A)):
  pile-up mercoledi' acconciatura, lezioni non collocate del triangolo G06/Barberia/T19,
  recurrence (guardia ~76-77%), skipConflict 0. **Ma attenzione:** il construction test ha
  pre-assignment OFF (CONSTRAINED_TEACHER_FLAG=false a riga 163)! Una cura Phase-0 NON gira in
  quel test. Per misurarla al checkpoint serve un test che accenda il flag (o usa il full staged
  solve, piu' lento). Valuta: e' meglio misurarla come (A) nel builder, o accendere pre-assign
  nel test. (A) l'ha messa nel builder proprio per questo.
- **Esito atteso:** se la scuola dira' "preferenza organizzativa" (non vincolo reale), si butta.
  Il ramo si misura ORA perche' la risposta della scuola e' bloccante (domande-brevi.md REVISIONE 30/07).

### (C) Benchmark metaeuristiche su B-TIMESLOT-1
- **Direttiva permanente:** performance = problema di RICERCA; tutti i benchmark esistenti sono
  pre-TASK-377 col provider cieco e NON fanno testo.
- **Protocollo:** stadio B-TIMESLOT-1, partendo dallo stesso stato post-PROMOTION. Braccia:
  config corrente vs late-acceptance (2-3 size) vs tabu (2 size), **budget identico e dichiarato
  per braccio**. laptop: UN solve alla volta, in sequenza. Cattura score decomposto + KPI per braccio.
- **Vincoli:** rispetta `.claude/rules/benchmark-xsd-validation.md` (`./scripts/validate/
  validate-solver-xml.sh` dopo ogni XML). La fase 2 (LS Compactness) e' quella costosa (~60min
  cap nel full solve, vedi localized-testing-guidelines); uno stadio singolo B-TIMESLOT-1 e' piu'
  breve ma va comunque sul laptop. `Phase2SmokeSnapshotTest` (env PLANNER_TEST_PH2SMOKE=1) e' il
  proxy offline LS-da-snapshot (90s, TREND non adozione) per scremare prima di spendere il laptop.
- **Config LS corrente:** tabu entity-tabu size 7 (vedi `solverConfig.xml` fase 2, e MEMORY.md
  "Best result yesterday: Tabu (entity tabu size 7)"). Confrontare vs LA e vs tabu altre size.

### (D) DRAFT-005 portfolio multi-start
- **Leggi il design:** `backlog/drafts/draft-005` (commit 063288ca "design Fable del portfolio
  parallelo stadio-scoped, statici mutati tra stadi = vietato il ciclo intero in-JVM, + protocollo
  profiling leave-one-out").
- **Vincolo architetturale critico:** il portfolio e' **SCOPED A UNO STADIO** (mai N cicli interi
  in-JVM): gli statici (SchoolSchedule static maps, WeeklyTemplatePlanner reference, M32 map,
  pinnedLessonTimeslots) MUTANO tra stadi, quindi rilanciare solve() nello stesso JVM_avanza gli
  statici del solve precedente = score corruption.
- **PRIMA IL TEST 5-MINUTI:** "solve() muta l'input?" Se sì (atteso: sì, per gli statici), clona
  via export/import TASK-401 (SnapshotFixture.loadFromExport) per ogni seed, NON rilanciare
  solve() in-JVM sullo stesso SchoolSchedule.
- **Build:** wrapper in `ScheduleService.runStagedSolver` dietro `-Dplanner.portfolio.size`
  default 1, N seed, pick-best su **score decomposto** (ScoreASnapshotTest / SolutionManager
  breakdown, non score scalare). Unit test sul wrapper (size=1 = no-op byte-identico al baseline).
- **Misura:** N=3 a budget/3 vs 1 a budget pieno sullo stadio B-TIMESLOT-1 (confronto onesto:
  stesso budget totale, frazionato vs concentrato).

### (E) Se resta notte, in ordine
- **(E1) Profiling leave-one-out** (DRAFT-005 punto 2): filtro per-ID al build del provider,
  property diag-only, K run brevi 60-120s stesso seed, ranking score-calc-speed. Identifica quali
  vincoli costano di piu' nel score-calc (target di ottimizzazione della velocita', non della qualita').
- **(E2) A/B full-solve rimozione esenzione isG14** (spike S1): S1 disse NO-OP al checkpoint di
  costruzione (l'esenzione e' LS-only, builder pre-score). Servono 2 full-solve laptop (con/zenza
  isG14 esente) per misurare l'effetto sulla ricerca. `spike-s1-isG14-exemption-2026-07-30.md`.
- **(E3) A/B M20 step 1500** (spike S3): stesso, step M20_PROGRESSION_STEP 500->1500, full-solve.
  `spike-s3-m20-progression-step-2026-07-30.md`. S3 NEUTRO al checkpoint.

## Stato / come riprendere
- **App UP su dev** (rebuild 22:26, JAR fresco con (A)). Per un run nuovo: stop+delete dev.
- **(A) flag:** `planner.construction.first-hour-slot.enabled` (OFF). Per l'A/B laptop: `java -jar`
  con `-Dplanner.construction.first-hour-slot.enabled=true` SULLA RIGA DI COMANDO (non MAVEN_OPTS,
  vedi CLAUDE.md), ciclo staged completo, x2.
- **Metro di misura costruzione:** `PLANNER_TEST_*` env o `System.setProperty` nel test (test.sh
  rifiuta -D espliciti). `WeeklyTemplateConstructionRealSeedTest` + KPI su dump
  `target/construction-lessons*.txt`. Costruzione = TREND, adozione = laptop A/B x2.
- **Decisione bloccante campagna (colonna 2 QUADRO):** constrained-teacher default ON (TASK-435).
  Verificato end-to-end sul 30d ma il flip del default richiede n>=2 run di conferma (zero codice).
- **DoD pendente per (A):** `/code-review high` e `/simplify` pieni (multi-agente) NON runnati
  (focused review clean, diff picccolo e specchia reserveSlot0Anchors). Rifarli con budget fresco
  prima di considerare (A) "DoD-completo" (anche se resta flag-OFF non adottato).
- **Serie storica KPI:** `serie-storica-kpi-2026-07-30.md` (metro DailyActivityQuota). Baseline
  campagna: 447/30b/30d full-day ~89,5-90,1% / boxed 3 / holes 0. 30e (M20-cap, non baseline):
  deep 28->15 shape migliore.
