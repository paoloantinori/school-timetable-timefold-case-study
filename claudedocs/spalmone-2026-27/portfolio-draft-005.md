# Portfolio multi-start B-TIMESLOT-1 (DRAFT-005, research lever, flag OFF)

HEAD `feature/cartesian-block-entity`. Build commit `280278f1`. NEVER adoption: behind
`-Dplanner.portfolio.size` default 1 (a single solve, byte-identical to the ordinary stage);
a value >1 enables N-start. Performance is a SEARCH problem (direttiva primaria), so whether
N>1 beats 1 full-budget solve is a laptop measurement, not something this scaffolding decides.

## Mutation characterization: does solve() mutate the input? -> NO (verified 2026-07-31 mattino)

La domanda aperta era: `solver.solve(problem)` tratta l'istanza passata come soluzione di
lavoro mutandola in place? Se si', N solve paralleli che condividono una istanza mutabile si
corrompono a vicenda, e ognuno richiede il proprio clone. **Risposta: NO, non muta.**

Misurato in `Phase2SmokeSnapshotTest` (app down, due run indipendenti), catturando due segnali
attorno a `solver.solve(problem)`:
- `solved == problem` (reference equality tra l'istanza passata e quella restituita);
- un marker (`problem.getLessons()` -> prima lesson con timeslot) lett prima e dopo il solve.

| env mode (PLANNER_TEST_ENV_MODE) | solved==input | marker ts | identity hash |
|---|---|---|---|
| PHASE_ASSERT (default del test) | **false** | 269 -> 269 (immobile) | 1391268303 -> 1199980209 |
| **NON_REPRODUCIBLE** (mode di produzione) | **false** | 269 -> 269 (immobile) | 782157654 -> 504421460 |

**Verdetto.** Timefold clona l'input internamente e restituisce un'istanza **distinta**; il
problema originale resta intatto. Mode-indipendente (stesso segnale in REPRODUCIBLE e
NON_REPRODUCIBLE). Corroborato dal contratto Timefold ("Multi bet solving: solve 1 dataset
with multiple, **isolated** solvers and take the best result"): l'isolamento e' lavoro del
solver, non del chiamante.

**Conseguenza sul design (semplifica il futuro parallelo).**
- Il clone per-solve **non e' richiesto** per la correttezza: il solver si isola da solo.
- Il portfolio odierno carica gia' un problema fresco per ogni solve
  (`loadSchedule(true)` dentro il loop `size>1`), quindi l'isolamento per-solve gia' vale
  anche oggi, in sequenziale.
- Il futuro parallelo (misure (a)) serve solo un executor, non la clone-machine che si temeva.
- Il commento `ScheduleService` (~riga 1239) che dice "parallel speedup needs an executor +
  per-solve clone" e' ora superato sul punto del clone: va rimosso/aggiornato (DoD (D)).

## Design attuale (commit 280278f1)

- `PortfolioPickBest.pickBest(candidates)`: deterministico, ordina per `HardMediumSoftScore`
  (hard, poi medium, poi soft, nell'ordine di verdetto della campagna); null/unsolved ultimi;
  parita' tiene il primo. 6 unit test passano (`PortfolioPickBestTest`). Isolato
  dall'orchestrazione cosi' e' testabile senza Quarkus o un solve live.
- Wrapper in `ScheduleService.runTimeslotSolveStage` (riga 1224):
  - `size <= 1`: singolo `runStagedSolver(timeslotPhasesConfig(), loadSchedule(true))`,
    `saveFinalSolution`, ritorna. Byte-identical allo stage ordinario (inerte sul benchmark).
  - `size > 1`: loop N volte, `config.setRandomSeed((long) i + 1L)` per traiettorie
    deterministiche distinte, ognuna `runStagedSolver(config, loadSchedule(true))`,
    `PortfolioPickBest.pickBest`, salva e ritorna il migliore.
- `portfolioSize()`: `Integer.getInteger("planner.portfolio.size", 1)`, letto LIVE (stesso
  motivo dei flag di costruzione: deve arrivare alla JVM app sulla riga `java -jar`).

## Misure aperte (laptop diurno, mai adozione)

- **(a) Speedup parallelo.** Oggi e' sequenziale = Nx wall-clock. Con un executor, N solve
  concorrenti (safe: solve non muta l'input + ognuno carica il proprio problema). Misura lo
  speedup, non la qualita'.
- **(b) N-start a budget/3 batte 1-start a budget pieno?** La domanda reale del portfolio:
  3 solve da ~1200s ciascuno (seed distinti) pickBest, contro 1 solve da ~3600s. AC#2
  DRAFT-005. Braccio A (tabu7, budget pieno, `bench-btimeslot1/arm-A-control/`) e' il
  riferimento gia' catturato.
- Mai N cicli interi in-JVM: gli statici (pin promossi, quote, riferimento M32) sono
  read-only DURANTE un singolo solve (grep-verified 2026-07-30) ma sono mutati TRA stadi,
  quindi il portfolio agisce solo sullo stage B-TIMESLOT, mai sull'intero ciclo staged.

## Pulizie differite (DoD /simplify, 2026-07-31 mattino)
- **Triplicazione `phase2LocalSearch`** (debito, FUORI dal diff (D)): tre copie field-for-field
  dello stesso phase-2 mirror esistono in `Phase2SmokeSnapshotTest`, `Task415BenchmarkScreeningDemoTest`
  e `Task419Stage1SpikeTest`, differiscono solo nel come parametrizzano l'acceptor. Consolidare in
  `SnapshotFixture.phase2LocalSearch(long, LocalSearchAcceptorConfig)` (forma piu' generale; i due
  fratelli passerebbero il loro `LocalSearchAcceptorConfig`) tocca test fuori dal diff (D), per cui
  e' differito a una pulizia dedicata. `phase2AcceptorFromEnv`/`phase2AcceptorLabel` restano locali
  allo smoke (smoke-banner-specific).
- **N `loadSchedule(true)` nella loop portfolio** (ottimizzazione, non materiale): ogni iterazione
  rilegge tutto il DB + ricostruisce gli statici (13 tabelle, 4 normalizzazioni, 8 mappe statiche);
  una sola lettura basterebbe (solve clona, vedi sopra). Costo ~3-15s su solve da 14-42min (<0,3%):
  si pulisce quando l'esecutore parallelo (misure (a)) entra, non ora. La versione per-solve resta
  come isolamento difensivo (corretta a prescindere dal contratto di mutazione).

## /code-review high (DoD, 2026-07-31 mattino)

APPLICATO (correttezza, size>1 research branch; size=1 byte-identical mai toccato):
- **Cancel fra candidati**: il loop portfolio ora checka `stagedCancelRequested` (`for (...; i < size && !stagedCancelRequested; ...)`). Prima, uno stop durante size>1 terminava solo il candidato corrente e i restanti N-1 correvano a budget pieno (lo stop documento "ends the CYCLE" era silenziosamente ignorato fra un candidato e l'altro).
- **Mutation assert**: lo smoke ora ASSERT `solveReturnedSameInstance == false` e `inputMarkerMoved == false` (non solo print). Un futuro cambio del contratto Timefold (solve che muta l'input) fa rosso il test, non stampa solo un boolean flippato.
- **Seed comment riformulato**: sotto NON_REPRODUCIBLE (default produzione) Timefold usa un seed fresco per run, quindi `setRandomSeed` e' no-op, ma ogni solve ha comunque traiettoria distinta (il portfolio funziona in entrambi i mode; solo la riproducibilita' cambia).

REFUTATO (verifica meccanica, conflitto fra agent):
- **"latestScore finale stale"** (Angle B/C): FALSO. `saveSchedule` (chiamato da `saveFinalSolution`, riga 515) setta `latestScore = schedule.getScore()` = score del WINNER. Angle C aveva ispezionato solo il body diretto di `saveFinalSolution` mancando la delega. Il reset mid-run di latestScore ad ogni candidato e' atteso (multi-start: ogni candidato riparte dalla costruzione), non un bug.

DIFFERITO (research-path, non correttezza; size=1 mai toccato):
- **Per-candidate persist / try-catch**: un crash mid-portfolio (OOM, drop SSH) perde i candidati gia' completati perche' `saveFinalSolution(best)` corre solo a fine loop (robustness, non correctness; il single-solve persisteva subito). Da aggiungere se la misura portfolio diventa lunga.
- **`stagedStage` label fisso sul candidato**: l'operatore vede `B-TIMESLOT-1` per ore col score che resetta ad ogni candidato; la log line mostra il candidato N/N ma `/status` no (osservabilita').
- **`phase2AcceptorFromEnv` robustezza**: `Integer.parseInt` su `PLANNER_TEST_PH2SMOKE_ACCEPTOR_PARAM` senza try/catch (garbage -> `NumberFormatException` invece del banner trend); `startsWith("la")` rutta il non-riconosciuto su entityTabu (visibile nel banner). Test helper opt-in, fail-fast accettabile.

## Pointers
- Build diff: `git show 280278f1` (`PortfolioPickBest`, `PortfolioPickBestTest`, wrapper
  `ScheduleService`, mutation harness in `Phase2SmokeSnapshotTest`).
- Smoke screen 5 armi: `bench-btimeslot1-smoke/SUMMARY.md`.
- Verdetto metaeuristica (C): `bench-btimeslot1/VERDICT.md` (KEEP tabu7).
- Log mutation raw: `/tmp/ph2smoke-mutation.log` (PHASE_ASSERT), `/tmp/ph2smoke-mutation-nr.log`
  (NON_REPRODUCIBLE). End grid dump: `target/ph2smoke-lessons.txt`.
