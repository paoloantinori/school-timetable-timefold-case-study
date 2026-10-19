# HANDOFF mattino 31/07 (zai, post-DoD (D), 05:57)

HEAD `feature/cartesian-block-entity`, pushato fino a `6b208584`. Sessione fresca
ripresa da `handoff-2026-07-31-notte3.md`. Ordine del mattino (1)(2)(3) CHIUSO e
pushato; (4) DIFFERITO con prerequisito documentato.

## FATTO e pushato (commits 57ca1321 + 6b208584)

### (1) Mutation characterization: solve() muta l'input? -> NO
`Phase2SmokeSnapshotTest` (app down), due run indipendenti. Risultato identico:
`solved==input=false`, marker immobile (ts 269->269), in PHASE_ASSERT **e**
NON_REPRODUCIBLE (mode-indipendente). Timefold clona internamente e restituisce
istanza distinta; l'originale resta intatto. Corroborato dal contratto Timefold
("Multi bet solving: multiple **isolated** solvers"). => il futuro parallelo NON
richiede clone per-solve. Raw: `/tmp/ph2smoke-mutation{,-nr}.log`. Doc:
`portfolio-draft-005.md`.

### (2) DoD PIENA sul diff (D) (commit 280278f1) - FATTA
- **/simplify** (4 agent: reuse/simplification/efficiency/altitude). Applicato:
  comment stale "per-solve clone" rimosso (refutato dal mutation test), banner echo
  di `portfolio.size` in `refreshSolverStatics`, javadoc `PortfolioPickBest`
  allineato al contratto non-null fallback, printf mutation semplificata (via
  `inputIdentity`), env vars acceptor documentati nel class javadoc. Differito:
  triplicazione `phase2LocalSearch` (3 test fratelli, fuori diff), N `loadSchedule`
  nella loop (non materiale, <0,3%).
- **/code-review high** (4 agent: line-by-line/removed-behavior/cross-file/conventions).
  Applicato (size>1 branch; size=1 byte-identical mai toccato):
  - **cancel fra candidati**: il loop portfolio ora checka `stagedCancelRequested`
    (prima uno stop terminava solo il solve corrente, i restanti N-1 a budget pieno).
  - **mutation assert**: lo smoke ora ASSERT `solved != problem` e marker immobile
    (non solo print). Guarda la premessa no-clone contro un futuro cambio contratto.
  - **seed comment** riformulato (NON_REPRODUCIBLE = seed fresco per run, no-op).
  - **REFUTATO**: "latestScore finale stale" era falso (saveSchedule, chiamato da
    saveFinalSolution, setta latestScore = winner.getScore(); Angle C aveva guardato
    solo il body diretto di saveFinalSolution).
  - Differito: per-candidate persist (crash robustness), stagedStage label fisso sul
    candidato (osservabilita'), parseInt su ACCEPTOR_PARAM senza try/catch (test helper).
- **test.sh EXIT 0**: `PortfolioPickBestTest` 6/6 + `Phase2SmokeSnapshotTest` assertions
  hold (NON_REPRODUCIBLE). BUILD SUCCESS.

### (3) QUADRO + commit+push - FATTO
`QUADRO-2026-07-30.md`: "NOTTE 31/07 - SINTESI RISVEGLIO" consolidata a 3 colonne
(A/B/C/D chiusi) in cima; sezioni parziali stale (NOTTE2 continuazione) marcate
superseded. `portfolio-draft-005.md` (nuovo) e' la casa del design DRAFT-005 +
mutation verdict + DoD (applicato/refutato/differito). Push: `d27bfa3f..6b208584`.

## (4) Misura portfolio N=3 - RUNNING (override maintainer, 06:09)
maintainer ha override-il differito: il laptop e' libero, flag ON solo per il run di misura,
nessuna adozione. Lanciata a size=3 FULL-budget (il knob budget/3 non e' wired; scalare
serve terminazione proporzionale, non un solo campo), NON a budget/3. Quindi risponde a
"multi-start (3x budget pieno) batte single-start (arm A)?" + costo wall-clock/RAM, non al
confronto a parita' di wall-clock (quello resta differito, serve il knob).
Setup fresco come arm A (import-calendario + pre-assign-reset + pre-assign, T01
231/231 verificato). `-Dplanner.portfolio.size=3 -Xmx4g` sulla riga `java -jar` (mai
MAVEN_OPTS). JAR 04:25 (stesso di arm A, 280278f1; le fix DoD non toccano il behaviour
portfolio). Poll+capture in background (`scripts/benchmark/portfolio-poll-capture.sh`,
dir `bench-portfolio-n3/`). ~60min, verdetto 3 righe pending al completamento.
Riferimento: braccio A in `bench-btimeslot1/arm-A-control/`.

## Standing
- Flag default-OFF ovunque, NESSUNA adozione. solverConfig = canonico (tabu7).
- App DOWN. caffeinate attivo (PID 22768).
- HEAD `6b208584` pushato. Working tree clean.

## Come riprendere
- Quadro consolidato: `QUADRO-2026-07-30.md` (sezione SINTESI RISVEGLIO in cima).
- Portfolio design + mutation verdict: `portfolio-draft-005.md`.
- (C) verdetto: `bench-btimeslot1/VERDICT.md`. Notte3: `handoff-2026-07-31-notte3.md`.
- Prossimo step portfolio: wiredel knob budget/3 (sopra), poi misura N=3.
