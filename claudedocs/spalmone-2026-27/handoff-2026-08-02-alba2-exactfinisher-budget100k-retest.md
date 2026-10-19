# HANDOFF 2026-08-02 alba2 (zai). ExactFinisher budget-raise re-test (run3/run4): 1/2, non deterministico

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (`abe8d892`, push).
> **App UP** (prod, `java -jar` con `-Dplanner.exact-finisher.enabled=true`
> `-Dplanner.pre-assign.constrained-teacher.source=input`
> `-Dquarkus.hibernate-orm.schema-management.strategy=update`; PID 41408, banner
> `maxCandidates=100000`). **DB = run4 leftover (-16hard)** su `planner-data-production`.
> Container `planner-pg` up. Verdetti dai raw. Regole standing invariate.

## MANDATO. TUTTO CHIUSO

### (1) Ricalibrazione MAX_CANDIDATES -> knob live default 100000. FATTO + COMMITTATO (abe8d892)
- Sostituita la costante hardcoded `MAX_CANDIDATES=10000` con **knob live
  `planner.exact-finisher.max-candidates`** (default 100000, floored 1, malformed->default), letto
  LIVE per `repair()` via `ExactFinisher.getMaxCandidates()`. Ricalibrabile senza rebuild.
- **Refactor `CandidateBudget` holder** (finding /simplify quality#1 + efficiency#3 sinergici):
  collassa l'`int[1]` array-as-counter + il sibling param `maxCandidates` in un holder
  (`acquire`/`exhausted`/`limit`); 4 firme perdono un param, l'asimmetria `>=`/`>` si encoda nei
  nomi dei metodi. Disaccoppiato `MCV_PROBE_CAP=64` (il vecchio `maxCandidates/MAX_K` era vacuo a
  100000: 3333 > pool VRP ~1368, il cap non scattava mai).
- WARN riscritto ("IS a calibration knob", nomina property+default); banner `ScheduleService:2036`
  legge l'effective value; design doc `ac2` riga 69 corretta; test non-vacuo sul knob
  (`candidateBudgetKnobControlsTheScanBound`: BUDGET=2 -> cap fires -> not placed; default -> placed).
- **mini-DoD**: `/simplify` (3 reviewer; holder+disaccoppio applicati, reuse+name-const skip con
  motivo) + `test.sh` EXIT 0 (**962 test, 0 falliti, 16 skipped**; ExactFinisherTest 7/0/0).

### (2) RE-TEST n=2 (run3/run4) budget 100000. FATTO + CAPTURE. ESITO 1/2, NON deterministico
- **run3 = 0hard**/-1044912medium/5419soft. Seed BUONO (BoxRepair chiude boxed=0; 1 iter
  B-TIMESLOT). ExactFinisher **INERTE** (boxed=0 all'early-return, no log).
- **run4 = -16hard**/-1022677medium/1033soft. Seed CATTIVO (18 boxed al finisher; 3 iter
  B-TIMESLOT + demotion). ExactFinisher **placed 13 of 18 @ budget 100000** poi capped (5 residual).
- **Confronto EF placement al variare del budget (seed cattivo):** 10000 -> 3/15 (run2, -30hard);
  100000 -> 13/18 (run4, -16hard). Il raise 10x ~4x migliore: meccanismo depth-0/1 **NON disprovato**,
  WARN "IS a calibration knob" corretto. **MA 100000 caps ancora** sul worst seed (5/18).
- Capture: `task478-results/ab-ef-run{3,4}/`.

### (3) Docs + backlog. FATTO
- `VERDICT-ab-exactfinisher-2026-08-02.md`: sezione "RE-TEST BUDGET 100000" in cima (supersede).
- TASK-478: comment #3 (run3/run4, AC#4 = PARZIALE, n=4 cumulativo).
- `QUADRO-2026-07-30.md`: sezione "ALBA 02/08" (ExactFinisher + portfolio refutato + menu maintainer).

## VERDETTO (3 righe, dai raw)
1. **P(hard-0)=1/2 di nuovo.** run3=0hard (good seed, EF inerte), run4=-16hard (bad seed, EF placed
   13/18 @ 100000). **hard-0 DETERMINISTICO NON confermato** (non 2/2).
2. **Budget raise VALIDATO direzionalmente ma INSUFFICIENTE.** 10000->100000: EF placement 3/15 ->
   13/18 sui seed cattivi. Il meccanismo e' budget-starved non disprovato. MA 100000 caps ancora
   (5/18 residui). Non sappiamo se i 5 siano collocabili a depth 0..1 con budget piu' alto (search
   troncata dal cap) o richiedano depth-2.
3. **Resta flag OFF, nessuna adozione.** Next step GATED a maintainer (knob live = cheap, no rebuild):
   (a) probe budget 500000-1M su seed cattivo (~2-3 run per stochasticita LS), o (b) **depth-2**
   (gate bump+relocation deferito, design AC#2 sez. 5), o (c) residual parzialmente strutturale.

## PROSSIMO STEP (gated, NON adottato)
Il knob live rende il probe a budget piu' alto un'operazione da 5s (relancio `java -jar` con
`-Dplanner.exact-finisher.max-candidates=500000`, no rebuild) + ~30-40min solve. **Caveat
stochasticita'**: la LS ha seed fresco per solve, quindi un probe deve pescare un seed cattivo
(~50% per run) per esercitare l'EF; run3 e' capitato good (EF inerte, no info sul budget). Per una
risposta definitiva sul "depth-2 necessaria?" servono ~2-3 probe a budget alto su seed cattivo, o
implementare depth-2. **Decisione a maintainer** (probe budget / depth-2 / accettare residual).

## STATO RUN / APP / INFRA
- **App UP** (dalle 04:14): `java -jar` ExactFinisher ON + source=input + schema update. Log
  `/tmp/planner-prod.log`. Banner conferma `maxCandidates=100000` (grep "ExactFinisher residual-completion").
- **DB = run4 leftover (-16hard)** su `planner-data-production`. Per run nuovo: reset +
  import-calendario + pre-assign-reset + pre-assign + solve-staged (no fresh reseed).
- Container `planner-pg` up. Jar `target/quarkus-app/` = HEAD (04:14, col knob + holder).

## PUNTI DI RIPRESA
- Questo handoff + `task478-results/VERDICT-ab-exactfinisher-2026-08-02.md` (sezione RE-TEST) +
  `QUADRO-2026-07-30.md` (sezione ALBA 02/08).
- Capture: `task478-results/ab-ef-run{1,2,3,4}/` (run1/2 @10000 storico, run3/4 @100000 re-test).
- Commit (push): `abe8d892` (knob + holder + DoD).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su `java -jar`
  (PLANNER_JAVA_OPTS non MAVEN_OPTS), presidio sincrono dei solve, DoD obbligatoria su codice solver
  adottato, handoff+/clear a ~450k, **flag ExactFinisher resta OFF default**.

## QUADRO ALBA2 02/08
- **Obiettivo mandato**: ricalibrazione MAX_CANDIDATES + re-test n=2. **TUTTO CHIUSO.**
- **Risultato chiave (onesto, negativo ma informativo)**: il budget raise (10000->100000) aiuta
  davvero (EF 3/15 -> 13/18) ma NON rende hard-0 deterministico (100000 caps ancora sul worst seed).
  Il meccanismo depth-0/1 resta valido; il colpevole e' che il worst-case residual serve o piu'
  budget o depth-2. Cura cheap e clarissima grazie al knob live; resta il SI di maintainer su quale via.
