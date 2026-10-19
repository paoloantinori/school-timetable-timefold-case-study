# HANDOFF post-E1 (zai, 2026-07-31 ~10:08) - coda: T19 -> E2 -> E3

HEAD `feature/cartesian-block-entity`, pushato fino a `189d1f01` (E1 chiuso). Sessione fresca
ripresa da `handoff-2026-07-31-mattino.md` + `QUADRO-2026-07-30.md` (SINTESI RISVEGLIO).
E1 CHIUSO con DoD piena; la coda residua e' una sequenza di A/B a solve completo (autonoma,
lunga). Questo handoff e' autocontenuto per riprendere dopo /clear.

## FATTO e pushato (E1, commit 189d1f01)

- **(E1) Profiling leave-one-out dei costi dei constraint - CHIUSO.** Filtro per-ID
  `planner.constraints.diag.exclude` (inerte di default, dietro flag; NON debugConstraintsDisabled)
  in `SchoolScheduleConstraintProvider.applyDiagExclude` (path FULL solo, seam testabile, public
  `DIAG_EXCLUDE_PROPERTY`, one-pass canonicalId) + harness env-gated
  `ConstraintProfilingLeaveOneOutTest` (`PLANNER_TEST_PROFILE`) + unit test
  `ConstraintDiagExcludeFilterTest` (8/8, in-suite). Metrica: `DefaultSolver.getScoreCalculationSpeed()`.
  **Headline**: costosita' concentrata nei conflitti core + soft a stream-join (S3 +1741, S7 +1638,
  S12 +1400, H1 +1388, H3 +1320, S17 +1247 calcs/s su baseline 13852, >2x pavimento S9-no-op +616);
  **i constraint di modello NON sono costosi** (M32 template #30 nel rumore, M34/M35 mid-pack) ->
  il benchmark solver NON li insegua. Limite di metodo: sotto il top, delta speed confinato dal
  rumore di traiettoria. DoD: /simplify + /code-review high (CLEAN 4 angoli, C2 hardening FULL-mode,
  C4 refutato) + test.sh EXIT 0 (902/0/0). Dettaglio + ranking:
  `e1-profiling-leave-one-out-2026-07-31.md` + `e1-leave-one-out-ranking-2026-07-31.txt`.

## CODA RESIDUA (una solve alla volta, mai adozione, verdetti 3 righe dai raw)

### (T19) A/B CONSTRAINED_TEACHER_MAX_WEEKDAYS=2 - INSERITO DA maintainer 31/07 mattina, PRIMA di E2/E3
Golden-first: il golden incarna gia' la risposta a Q22 (T19 insegna solo lun+mar), quindi il
ramo "vincolo reale" si pre-misura ORA senza aspettare referente; la sua risposta confermera'/smentira',
in entrambi i casi il lavoro non e' sprecato.
- Protocollo: full-solve **staged** (`./scripts/api/api.sh solve-staged` con attesa staged-aware).
- Braccio **baseline** = default correnti (constrained-teacher ON, **max-weekdays=1**).
- Braccio **test** = **CONSTRAINED_TEACHER_MAX_WEEKDAYS=2** (soglia live-configurabile del build
  `9521939f`). **VERIFICARE il nome esatto della property/knob** (grep `9521939f` o
  `CONSTRAINED_TEACHER_MAX_WEEKDAYS` in `HybridPhase1aService`/`ConstrainedTeacherAllocator`): e'
  live-configurable come gli altri flag (letto da `java -jar` -D, non MAVEN_OPTS).
- Setup identico per braccio (production fresca: drop-and-create+seed + import-calendario +
  pre-assign-reset + pre-assign, **T01 231/231** verificato; GOTCHA: restart raw `java -jar`
  wipea il DB, vedi `handoff-2026-07-31-notte3.md`).
- KPI: batteria standard (`scripts/benchmark/capture-run.sh <outdir>`) + **bersagli tripletta**:
  lezioni non collocate del triangolo G06/Barberia, room-null mercoledi' acconciatura, slack
  T19, boxed.
- Verdetto 3 righe dai raw. Soglia resta default (nessuna adozione: se referente dira' "preferenza"
  si butta).

### (E2) A/B rimozione esenzione isG14 (spike S1)
- Full-solve staged, JAR nuovo. Baseline = default correnti (first-hour-slot ON). Test =
  esenzione isG14 **rimossa** (variante dietro flag o patch temporanea dichiarata; grep
  `isG14`/`G14` in `SchoolScheduleConstraintProvider` ~riga 1900). KPI standard +
  G14-specifici (il gruppo ora ha calendario reale 120h). LS-only: NO-OP al checkpoint
  costruzione, serve full-solve.

### (E3) A/B M20_PROGRESSION_STEP 1500 (spike S3)
- Full-solve staged, stesso protocollo E2. Baseline = step 500 (default). Test = step 1500.
  Knob: grep `M20_PROGRESSION_STEP` (o `m20.*step`) per il nome property. LS-only, serve full-solve.

## Standing (invariate)
- Una solve alla volta. Interleaving codice+test lecito senza rebuild DURING un solve.
- Flag default-OFF per ogni variante sperimentale. **Nessuna adozione** (decisioni a maintainer).
- DoD piena sul CODICE (T19/E2/E3 sono measurement puri: nessun code change -> niente
  /simplify//code-review, solo capture+verdetto; se serve una patch temporanea dichiarata per
  E2, revertirla prima del commit).
- Commit+push a ogni chiusura. Verdetti 3 righe coi numeri dai raw. QUADRO aggiornato man mano.
- Tempi assoluti. Domande auto-risolte e documentate. Handoff+/clear a ~450k.

## Stato ambiente
- HEAD `189d1f01` pushato. Working tree clean (dopo commit E1).
- **App DOWN** (killata per i test @QuarkusTest). DB = profilo **dev** (restart del rebuild).
- **JAR 08:31** (build first-hour-slot ON, commit ~401b5cac; antenato di 9521939f per il knob
  T19). **Valido per T19/E2/E3** (E1 filter inerte). Per pulizia: rebuild da HEAD prima della
  campagna solve (`./scripts/core/rebuild.sh`) - opzionale ma consigliato.
- **Default omogenei**: first-hour-slot ON + constrained-teacher ON (entrambi ratificati default
  ON). Un braccio OFF baseline richiede il flag esplicito =false.
- solverConfig = canonico (tabu7).
- caffeinate attivo (PID 22768).

## Come riprende
- Questo handoff + `QUADRO-2026-07-30.md` (SINTESI RISVEGLIO in cima, E1 ora in colonna 1).
- Primo step: rebuild da HEAD (consigliato) + setup production fresco + (T19) A/B.
- Riferimento tripletta/difetti: `spike-g06-barberia-t19-2026-07-30.md` + `domande-brevi.md` (Q22).
