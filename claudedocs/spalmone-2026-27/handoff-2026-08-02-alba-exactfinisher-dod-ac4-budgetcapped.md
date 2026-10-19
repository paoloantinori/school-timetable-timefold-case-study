# HANDOFF 2026-08-02 alba (zai). ExactFinisher DoD gate + AC#4 A/B (1/2, budget-capped)

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (4 commit notturni pushati).
> **App UP** (prod, `java -jar` con `-Dplanner.exact-finisher.enabled=true`
> e `-Dplanner.pre-assign.constrained-teacher.source=input`). **DB = run2 leftover (-30hard)** su
> `planner-data-production`. Container `planner-pg` up. Verdetti dai raw. Regole standing invariate.

## MANDATO. TUTTO CHIUSO

### (1) Verifica Fedora n=3 dai raw. FATTO
run6 **0hard** (0 unassigned, portfolio -1831/-1832 convergenti). run7 **-36hard** (15 unassigned,
portfolio -2137/-2137 poi -2141/-2141 poi -2145/-2145). run8 **-56hard** (~28 unassigned, -2091/-2091
poi -2094/-2095 poi -2098/-2098). P(pass)=1/3. Candidati near-identical within-round, NON indipendenti,
quindi `1-(1-p)^N` non si applica. **Refutazione onesta confermata.**

### (2) GATE FORMALI DoD su ExactFinisher. FATTO + COMMITTATO (77eb62c3)
- **`/simplify`** (4 agent paralleli): 5 refactor applicati. Deleghe a BoxRepair di
  `singleUnpinnedBlocker` + `passesPlacementGatesForBump` (widen package-private, delete clone),
  sort key pre-compute (`Keyed` wrapper, ~208 vs 30 eval), `boolean[] capped` a plain, javadoc
  `0..2` corretto a `0..1`.
- **`/code-review high`** (8 finder + skeptic Opus): 6 fix applicati.
  (a) **bug CONFIRMED**: `findDepth1Chain` chiamava `isDepartureSafe` incondizionato; per same-group
  L riempie lo slot di O (chain.headCell == bump.origin == ts) quindi la departure e' gap-neutral,
  era un false-negative che strandava. Gated `!sameGroup` mirroring `BoxRepair.findEviction`.
  **Test non-vacuo revert-checkato** (fallisce movesApplied=0 senza fix, passa con). Piu' 5 fix
  doc/cleanup/perf. 2 defer backlog (#2 first-relocation greedy, #8 commitPlace/relocate dedup).
- **`./scripts/core/test.sh` EXIT 0** (orchestratore): 961 test, 0 falliti, 16 skipped. Flag OFF.

### (3) A/B EXACTFINISHER (AC#4) n=2. FATTO + COMMITTATO (3fa30eed). ESITO 1/2, BUDGET-CAPPED
- **run1: 0hard**/-1042487medium/5583soft, 0 unassigned. Seed buono: LS chiude tutto,
  ExactFinisher **INERTE** (boxed=0 all'early-return, nessun log "placed").
- **run2: -30hard**/-1039910medium/367soft, ~12 unassigned (10 Matematica/G03). Seed cattivo
  (B-TIMESLOT -2145hard, 15 boxed al finisher). **ExactFinisher: `placed 3 of 15` poi
  MAX_CANDIDATES=10000 capped** (WARN: "candidate budget of 10000 reached, 12 of 15 left boxed").
- **Verdetto: P(hard-0)=1/2. NON 2/2, hard-0 DETERMINISTICO NON confermato.** Il meccanismo
  depth-0/1 NON e' disprovato, e' **budget-starved**: 15 lezioni costrette per ~1368 celle VRP
  superano 10000 dopo ~7 (MCV ordina le piu' costrette prima, ognuna scansiona tutto il VRP se
  nessuna cella depth-0). Il WARN del design "not a tuning knob" e' **SBAGLIATO** (il budget E' un
  knob, miscalibrato). Capture: `task478-results/ab-ef-run{1,2}/`.
  Verdetto: `task478-results/VERDICT-ab-exactfinisher-2026-08-02.md`.

### (4) Docs + backlog. FATTO + COMMITTATO (4668c94c)
- `procedura-portfolio-path-input-2026-08-01.md`: banner **REFUTATO** in cima (teoria `1-(1-p)^N`
  refutata dall'n=3; nota: multi-start vero richiederebbe costruzioni indipendenti, ExactFinisher
  supera il problema enumerando).
- TASK-478: AC#2/#3 spuntati DONE, AC#4 commentata (esito 1/2 budget-capped, prossimo step).
- TASK-452: commento #9 (n=3 portfolio refutato + ExactFinisher budget-starved).

## VERDETTO NOTTE (dai raw)
1. **ExactFinisher depth 0..1 e' BUDGET-STARVED, non disprovato.** Su run2 (seed cattivo, 15 boxed)
   ha piazzato 3/15 poi MAX_CANDIDATES=10000 ha abortito la ricerca. Non sappiamo da questo A/B se
   le 12 residue siano collocabili a depth 0..1 con budget adeguato o richiedano depth-2.
2. **hard-0 del path input resta PROBABILISTICO** (1/2): run1 good-seed 0hard via LS, run2
   bad-seed -30hard. ExactFinisher NON lo ha reso deterministico (e il portfolio e' refutato).
3. **Nessuna adozione.** ExactFinisher flag OFF, portfolio DRAFT-005 refutato, default invariato.

## PROSSIMO STEP (lavoro aperto, gated)
**Raise `ExactFinisher.MAX_CANDIDATES` 10000 a ~100000** (10x; garantisce scan depth-0 completo
per 15+ residue con headroom depth-1; correggere anche il WARN "not a tuning knob" in "e' un knob
di budget"). Poi rebuild + 2 run freschi source=input. Verdetto: se 2/2 hard-0 a budget adeguato
diventa candidato adozione, resta il SI di maintainer. Se ancora residue serve depth-2 (gate combinato
bump+relocation, design AC#2 sez. 5) o il residuo e' strutturale. **DoD sul change di MAX_CANDIDATES**:
/simplify + /code-review high + test.sh EXIT 0.

## STATO RUN / APP / INFRA
- **App UP** (dalle 02:11): `java -jar` con ExactFinisher ON + source=input. Log `/tmp/planner-prod.log`.
  Per re-run con MAX_CANDIDATES raised: kill-app + rebuild + `PLANNER_JAVA_OPTS` + prod-start-and-wait.
- **DB = run2 leftover (-30hard)** su `planner-data-production`. Per run nuovo: reset +
  import-calendario + pre-assign + solve-staged (no fresh reseed; T20 LabAssistant gia' in cassa).
- Container `planner-pg` up. Jar `target/quarkus-app/quarkus-run.jar` = HEAD (02:11, con refine).

## PUNTI DI RIPRESA
- Questo handoff + `task478-results/VERDICT-ab-exactfinisher-2026-08-02.md` (A/B 1/2) +
  `task452-results/verdict-portfolio-n3-2026-08-02.md` (n=3 refutato).
- Capture: `task478-results/ab-ef-run{1,2}/`, `task452-results/verify-source-input-run{6,7,8}/`.
- Commit notturni (push): `9a35fc3e` (AC#2/#3), `77eb62c3` (DoD gates), `3fa30eed` (A/B n=2),
  `4668c94c` (procedura + backlog).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su `java -jar`
  (PLANNER_JAVA_OPTS non MAVEN_OPTS), presidio sincrono dei solve (la shell in background NON
  risveglia), DoD obbligatoria su codice solver adottato, handoff+/clear a ~450k.

## QUADRO ALBA 02/08
- **Obiettivo mandato**: gate formali ExactFinisher + A/B AC#4. **TUTTO CHIUSO.**
- **Risultato chiave (onesto, negativo ma informativo)**: ExactFinisher NON ha reso hard-0
  deterministico (1/2). Il colpevole e' MAX_CANDIDATES=10000 troppo basso, non il meccanismo
  depth-0/1. Cura clarissima e cheap (raise del budget + re-run).
- **Contesto**: notevole ma nei limiti. Handoff pulito; prossimo /clear riparte da qui.
