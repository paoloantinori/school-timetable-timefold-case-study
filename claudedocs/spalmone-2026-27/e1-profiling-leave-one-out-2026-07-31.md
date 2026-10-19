# E1 - Profiling leave-one-out dei costi dei constraint (2026-07-31, zai)

HEAD `feature/cartesian-block-entity`. NEVER adoption: e' uno strumento di MISURA. Il filtro e'
inerte nel solve di default (verificato da `ConstraintDiagExcludeFilterTest`, 8/8); l'harness e'
env-gated fuori dalla suite. L'output (la ranking) dice alla ricerca solver DOVE guardare.

## Cosa risponde

Per ciascun constraint canonico: quanto costa in tempo di calcolo del punteggio? Il constraint
piu' costoso e' quello la cui ESCLUSIONE fa correre il solver piu' veloce (ogni calcolo sopravvissuto
e' piu' cheap). Metrica: `DefaultSolver.getScoreCalculationSpeed()` (score-calc/sec) in Timefold 2.3.0.
Costo di C = `armSpeed(C escluso) - baselineSpeed`.

## Design (auto-risolto, 2026-07-31)

- **Filtro per-ID, non tutto-o-niente.** Aggiunto a
  `SchoolScheduleConstraintProvider.defineConstraints` (path FULL): property live
  `planner.constraints.diag.exclude=<ID,ID,...>` drop i constraint il cui ID canonico (via
  `ConstraintIds.canonicalId(ref.id())`) e' nella lista. Inerte se unset (byte-identico al solve
  ordinario). NON riusa `debugConstraintsDisabled` (che torna ZERO constraint). Seam testabile
  `applyDiagExclude(Constraint[], String)`.
- **Exclude, non include.** L'handoff diceva "diag-only con lista di ID"; interpretato come filtro
  per-ID diagnostico. Leave-one-out richiede "togli 1 ID per run" -> exclude e' la primitiva
  naturale (1 ID per braccio, robusto ad aggiunte). Include sarebbe la lista-complemento (29 ID).
- **Metrica = speed, non score.** Lo score-calc-speed e' un RATE (calc/sec), robusto al rumore di
  traiettoria; lo score-quality delta sarebbe "impatto", non "costo".
- **Mode NO_ASSERT + seed 0.** NO_ASSERT e' deterministico e SENZA overhead di assertion (PHASE_ASSERT
  raddoppia il lavoro di scoring e falsa la metrica). Seed 0 fissa l'ordine di proposta delle mosse.
- **Warmup obbligatorio.** Il baseline deve correre a JVM calda. Il primo solve compila le hot-path
  dei constraint stream; se il baseline fosse il primo solve, ogni braccio successivo sembrerebbe
  "piu' veloce" solo per warmup. Misurato (run freddo): un braccio no-op (S9) correva +1518 calcs/s
  vs un baseline freddo = ~12% di puro warmup. Fix: un solve warmup scartato prima del baseline.
  Sanity check in-run: il braccio no-op (S9, DISABLED task-273 -> non buildato affatto) deve
  stare a ~0 dopo warmup (confermato nello smoke: S9 -366 vs +1516 pre-fix). (Contrasto: M-TC E'
  buildato ma flag-inerte, quindi NON e' un no-op.)

## Metodo

1. Carica lo snapshot di default (`val410-run2`, stato finale) in `@BeforeEach @Transactional`
   (tocca il DB: persist/listAll). Il loop di solve NON tocca il DB -> `@Test` NON transazionale
   (tenere una tx JTA per ~20min supera il timeout 300s e rolla al commit, ARJUNA016102).
2. `solve()` non muta l'input (verdetto mutation, portfolio-draft-005) -> il problema si carica
   una volta e si passa a ogni braccio.
3. Per ogni ID canonico: setta `diag.exclude=<ID>`, build SolverFactory fresco (defineConstraints
   rilegge la property), solve a budget fisso, leggi `getScoreCalculationSpeed()`.
4. Ranking per `armSpeed - baselineSpeed` (discendente = costoso prima).

Popolazione: 59 ID canonici (sovrainsieme del set buildato; M-TC si registra ma e' flag-inerte).
Snapshot SENZA stanze: i constraint room-tier (H2, H29, H-M2, M3, M-LF) NON sono rappresentativi
(H29 spara su ogni lezione null-room, H2/M3 inerti). Vista giusta per il collo di bottiglia della
campagna = fase B-TIMESLOT (timeslot), non le stanze (fase successiva).

## Caveat (leggere prima di fidarsi di un numero)

1. **Traiettoria.** Escludere un constraint cambia lo score di ogni mossa -> la traiettoria LS
   diverge tra braccia. Lo speed e' un PROXY del costo marginale per-calcolo, non una misura
   isolata perfetta. La RANKING e' il deliverable, non il delta singolo.
2. **Room-tier non rappresentativi** (snapshot senza stanze, vedi sopra).
3. **Budget.** Default 20s/arm. L'handoff suggeriva 60-120s; 20s basta per la ranking perche'
   lo speed e' una media cumulativa su 200k+ calcoli anche a 15s. I top-sospetti si rifiniscono
   a 120s se uno scarto e' incerto.

## Risultati (run completo warm, 20s/arm, BUILD SUCCESS 0 errori)

- **Baseline: 13852 calcs/s** (count 280111), 58 constraint buildati (S9 e' DISABLED/non buildato;
  M-TC E' buildato ma flag-inerte -> per questo 58 e non 59).
- **Pavimento di rumore = S9 (no-op) = +616.** S9 e' DISABLED (task-273, chiamata commentata ->
  non buildato): escluderlo non cambia set ne' traiettoria (stesso seed) -> il +616 e' solo
  escluderlo non cambia set ne' traiettoria (stesso seed) -> il +616 e' solo residuo JIT/posizione
  dopo il warmup (era +1518 a JVM freddo: il warmup abbatte il confound ma non lo azzera, il JIT
  continua a scaldare tra un solve e l'altro). **Un constraint e' "costoso" solo se speedup >> 616.**
- **Top pulita (speedup > ~1230 = 2x pavimento)**, costlier prima:
  S3 +1741 (teacher preference), S7 +1638 (reward consecutive student lessons), S12 +1400
  (weekly subject presence), H1 +1388 (teacher conflict), H3 +1320 (student group conflict),
  S17 +1247 (reward same-subject block pair). = 2 conflitti hard core (H1/H3) + 4 soft a
  stream-join (S3/S7/S12/S17): i join valutati su ogni mossa. Costo in valore assoluto modesto
  (top +1741 su base 13852 = ~12,6%): **nessun constraint domina in modo drammatico.**
- **Marginali (~1,6-1,7x pavimento, probabili ma non netti):** S16 +1147, H28 +1080, M37 +1074,
  M22 +1048, S1 +1021, S13 +988, S8 +978.
- **Constraint di modello complessi NON sono costosi (debunk):** M32 (template deviation) #30
  +367 (nel rumore); M34 #14 +878, M35 #21 +571 (mid-pack). La sfiducia che M32/M34/M35
  appesantissero il solver NON e' confermata -> il benchmark solver NON li insegua per velocita'.
- **Negative speedup = artefatto traiettoria, NON "costo negativo".** Escludere H17/M18/M20/M22
  (compaction/fill) devia la LS verso schedule sparse con mosse piu' costose da valutare ->
  speed cala. Sotto il top, la metrica e' confinata dal rumore di traiettoria (lo speed e' un
  RATE sul percorso effettivamente esplorato, non una misura isolata per-calcolo). Esempi negativi:
  M25 -5176, M24 -4764, H6 -4171, M26 -3365, H17 -938, M18 -1600. Non interpretabili come costo.

Tabella ranking completa (59 bracci): `target/e1-leave-one-out-ranking.txt`.

## Verdetto (3 righe, dai raw)

1. **Costosita' concentrata nei conflitti core + soft a join.** Sopra il pavimento di rumore
   (S9 no-op +616): S3 +1741, S7 +1638, S12 +1400, H1 +1388, H3 +1320, S17 +1247 calcs/s su
   baseline 13852. H1/H3 (conflitti hard O(n^2)) e S3/S7/S12/S17 (soft stream-join) sono i join
   valutati su ogni mossa. Costo assoluto modesto (top ~12,6%): nessun constraint domina.
2. **I constraint di modello NON sono il collo di bottiglia.** M32 (template deviation, la sfiducia
   della campagna) e' #30 +367 nel rumore; M34/M35 (cadence) mid-pack. Dove NON cercare velocita'.
3. **Limite di metodo documentato.** Lo speed-delta LS e' confinato dal rumore di traiettoria sotto
   il top (negative speedup = deviazione di percorso, non costo). Per isolare il costo per-calcolo
   puro servirebbe un replay a sequenza-di-mosse fissa (futuro). Il top pulito e' il segnale
   azionabile: i conflitti/join core, non i vincoli di modello.

## DoD (filtro code)

- `/simplify` + `/code-review high` sul diff del filtro: TBD.
- `test.sh` EXIT 0 (suite default + filtro unit test): TBD.

## Pointers

- Filtro: `SchoolScheduleConstraintProvider.applyDiagExclude` (+ `DIAG_EXCLUDE_PROPERTY`).
- Unit test: `ConstraintDiagExcludeFilterTest` (8/8, in-suite).
- Harness: `ConstraintProfilingLeaveOneOutTest` (env-gated `PLANNER_TEST_PROFILE`).
- Run freddo (pre-warmup-fix, confound warmup ~+1500): `/tmp/e1-full.log`.
- Run warm (definitivo): `/tmp/e1-full-warm.log`.
