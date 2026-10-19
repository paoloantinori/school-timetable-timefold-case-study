# HANDOFF 2026-08-02 alba6 (zai). TASK-481: depth-3 e' SHELL GAME, STOP + fork a Fedora.

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity`. **App UP** (prod, `java -jar`,
> jar pre-alba6 con EF ON via -D; CompositionHoleCloser e' default OFF quindi il jar senza rebuild non
> lo carica ancora: per esercitarlo serve rebuild). **DB = A/B run2 finale (0hard)** su
> `planner-data-production`. Container `planner-pg` up. Verdetti dai raw. Regole standing invariate.
> **maintainer sveglio/attivo.**

## MANDATO Fedora (Opzione A) - ESITO: STOP ONESTO + FORK

La decisione Fedora (coerente con "0-hard binario") era Opzione A: composition-aware hole-closer
deterministico depth 2-3, sibling di EF/StudentDayCompaction, flag default OFF, A/B su bad seed.
Mandate #3: se depth-3 non basta in modo sostenibile, STOP con la misura, niente forzature.

### Cosa e' successo (4 punti mandate)

1. **CORREZIONI DI RECORD (fatte, commit `d8008c79`).** Errore fattuale della diagnosi alba4 sulla
   config corretto in CLAUDE.md (RATIFICA TASK-478) + `diagnosi-h17-g11-mercoledi-2026-08-02.md`
   (sez.4, verdetto, puntatori): "no metaheuristic / hill-climbing / change+swap / zero cartesian"
   era FALSO (verificato `solverConfig.xml:46-131`); tabu `entityTabuSize 7` + pillar (4 selettori)
   + forager `acceptedCountLimit 1000` SONO gia' attivi in Phase 2 e lasciano il buco.

2. **GATE depth-3 (commit `f6c58944`, poi RISTRETTO in alba6).** Prima stesura della probe
   `depth3CrossGroupCascade`: 6/6 close con `net H17 < 0`. **FALSO POSITIVO**: la condizione era
   troppo debole. Ristretta (close SUSTAINABLE = bloccante edge-removable; SHELL-GAME = bloccante
   mid-run): **SUSTAINABLE 0/6, SHELL-GAME 6/6**. Diagnostica decisiva `diagnoseBlockerDeparture`:
   tutti i 66 bloccanti @10 (11 gruppi x 6 mercoledi) sono `midRun=true` (giorno packato
   `[0,1,2,3,4,5]` o `[0,1,2,3,4]`, bloccante @slot2 in mezzo). Nessun edge => nessuna close
   sostenibile.

3. **IMPLEMENTAZIONE Opzione A (commit in questo giro).** `CompositionHoleCloser` (solver/, nuovo):
   sibling di LabDayConsolidation che generalizza la cascata cross-group ai **giorni d'aula** (LDC
   scopa solo lab-day, soglia >=4 lab lessons; il giorno G11 @10 e' d'aula). Riusa tutti i
   primitive condivisi (BoxRepair gates, HoleRepair.findImport/commitCrossDayMove,
   SurplusRebalance.keepsDayAnchored, StudentDayCompaction indici), improve-or-noop con revert,
   CandidateBudget EF-style (knob `planner.composition-hole-closer.max-candidates`, default 1M),
   runner `runCompositionHoleCloser` post-LDC/pre-AnchorRepair, flag `isCompositionHoleCloserEnabled`
   (opt-in `-Dplanner.composition-hole-closer.enabled=true`, default OFF). **CORRETTO ma INERTE**
   sul bad-seed: il gate `isDepartureSafe` rifiuta tutti i 66 bloccanti mid-run (lo shell-game),
   0 chiuse, 0 conflitti, H17 invariato. Test `compositionHoleCloserClosesBadSeed` pinna l'inertezza.

4. **A/B ANNULLATA (mandate #3).** Depth-3 non basta in modo sostenibile => nessuna A/B forzata.
   STOP onesto con la misura. FORK a Fedora.

### Il meccanismo dello shell-game (perche' la probe alba5 sbagliava)

Per il buco G11 @10: bloccante Orientamento(T03)@10[G07] -> 2026-09-15@14, donor
Orientamento(T03)@10[G11]. `H17d=-1` perche': G11 @10 -1 (buco riempito) + G07 @10 +1
(bloccante mid-run, gap aperto sul suo giorno) + G07 2026-09-15@14 -1 (la DESTINATION riempie un
gap non correlato). Il netto -1 nasconde che il buco @10 e' migrato da G11 a G07. La close
sostenibile richiede un bloccante edge (rimozione non apre gap): al slot @10 saturo non esiste. Una
cascata iterativa ciclerebbe tra gli 11 gruppi packati senza azzerare H17. E' un artefatto di bacino
(good seed run1/run3 = 0 buchi), che conferma lo spike originale "cella @10 saturata di docenti
d'aula, hard-forced nel vicinato 1/2-mosse".

### Il fork (per Fedora/maintainer)

- **C. Multi-start (TASK-480), leading.** Bacino: good seed 0 buchi, bad seed satura @10. Multi-start
  vero e' la via principale per H17. COSTO: 0-hard "single-run" -> "0-hard nel migliore di N run".
- **E. Costruzione composition-aware.** Costruire @10 compatto (come il golden) dalla fase 1. RISCHIO:
  LS rimescola per H1 (PROPOSTA Opzione D esclusa per questo).
- **F. Mossa globale (2-Kempe / cartesian mirato).** tabu+pillar girano gia' e non la compongono.
- **Mantenere CompositionHoleCloser default-OFF** come scaffold corretto per buchi d'aula NON saturi
  (pattern non osservato). Inerte sul target, 0 danni.

Raccomandazione alba6: **C (multi-start)** e' la via onesta per @10 (bacino). CompositionHoleCloser
resta committed default-OFF in attesa di decisione (keep per altri pattern / extend iterativo /
remove).

## DoD (sul diff TASK-481)

- **/simplify**: 3 review agent (reuse + quality + efficiency). Finding applicati: bug critico
  contract-lie (javadoc di CompositionHoleCloser/SchoolSchedule/ScheduleService/CLAUDE.md dicevano
  "gate 6/6 passato" mentre e' REFUTED; corretti a "inerte, scaffold verificato"), dead javadoc
  rimosso, duplicazione measure+restore estratta in `scoreTriple`. Efficiency: no-action (pass
  ben bound, findImport non budgetato non e' blowup, matches pattern HoleRepair). Reuse: similarita'
  LDC intenzionale (sibling), CandidateBudget dup di ExactFinisher (private, triviale, non vale la
  condivisione).
- **/code-review high**: skeptic Opus = **CONFIRMED** sul claim "pass corretto, 0-hard-additive
  (isSlotConflictFree + commitCrossDayMove live), refuse shell-game (isDepartureSafe, 66/66 mid-run
  refused), no-harm inert (0 mutation), two-move revert (blockerOrigin catturato pre-commit, revert
  simmetrico)". Runner PlacementSnapshot + ensureImproveOrNoop = defense-in-depth. GAP onesto: NESSUN
  test GREEN esercita il path successful-cascade (bad-seed non ha bloccanti edge-removable; commit-
  and-import verificato solo per code-reading). PRE-KEEP: se Fedora mantiene il pass, aggiungere un
  fixture sintetico con bloccante edge-removable. Per ora non-adopted default-OFF: gap di copertura,
  non bug.
- **test.sh EXIT 0**: 967/0/0 (20 skipped gated). Gated `H17HoleCloseDiscriminatorTest`
  (PLANNER_TEST_H17_HOLE=1): depth3 SUSTAINABLE 0/6 SHELL-GAME 6/6 PASS; compositionHoleCloserClosesBadSeed
  inert PASS.

## STATO RUN / APP / INFRA

- **App UP** production, jar = HEAD pre-alba6 (EF ON via -D). CompositionHoleCloser e' codice nuovo:
  per carireferente serve rebuild (`./scripts/core/rebuild.sh`). Default OFF => inerte senza -D.
- **DB = A/B run2 (0hard)** su `planner-data-production`. Per run nuovo: reset + import-calendario +
  pre-assign-reset + pre-assign + solve-staged.
- Container `planner-pg` up (db-query.sh intermittente al primo tentativo, ritenta).
- HEAD: commit record-correction `d8008c79` + depth-3 gate `f6c58944` pushati; implementation alba6
  (CompositionHoleCloser + discriminatore ristretto + docs) in questo giro (commit+push pendenti).

## PUNTI DI RIPRESA

- **DECISIONE Fedora/maintainer** (bloccante): C multi-start (leading) vs E costruzione composition-aware
  vs F mossa globale vs keep/remove CompositionHoleCloser. Il fork e' in
  `task481-results/FORK-shell-game-depth3-2026-08-02.md`.
- Se C: lavoro in TASK-480 (multi-start vero da costruzioni indipendenti). TASK-481 si chiude come
  "superseded al bacino / deferred a 480".
- Se si vuole comunque esplorare E/F: nuovo task, NON riaprire Opzione A (refutata come close locale).
- Backlog TASK-481 = In Progress, commento #2 + note alba6 (shell-game + fork). AC#3 (A/B 0-hard) NON
  metto. m-13 resta BLOCCATO dai 6 H17.
- Handoff precedenti: `alba5b` (fork al decisore), `alba5` (ratifica EF), `alba4` (diagnosi H17 + A/B EF).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su `java -jar`, rebuild prima di
  run misurati, handoff+/clear a ~450k, mai fermo, DoD obbligatoria su codice solver adottato, default
  source resta golden, CompositionHoleCloser resta opt-in OFF.
