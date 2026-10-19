# HANDOFF 2026-08-02 alba8 (zai). TASK-480 DONE. Multi-start validato + adottato (procedura).

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (commit `7dd96660`, pushato).
> **TASK-480 chiuso Done.** Regole standing invariate (verdetti dai raw, commit+push, tempi assoluti,
> handoff+/clear a ~450k, mai fermo, DoD obbligatoria, default source resta golden, GRASP opt-in OFF).

## ESITO TASK-480 (Fase 4 chiusa, alba8)

A/B multistart completato (4 run, 8480s = ~2.35h sequenziale, source=input):

| run | score | wall | 0-hard? |
|---|---|---|---|
| baseline-grasp-off | 0hard/-1029611medium | 1848s | YES |
| grasp-seed-1 | **-11hard**/-1173327medium | 2486s | no (bacino bad-seed H17) |
| grasp-seed-2 | 0hard/-1308087medium | 2544s | YES |
| grasp-seed-3 | 0hard/-1297715medium | 1602s | YES |

**2/3 GRASP seeds a 0-hard; grasp-1 nel bacino bad-seed (-11hard = H17 -10/10match + H29 -1).** Il
fenotipo H17 e' lo stesso di TASK-481 (G11 mercoledi, gap mid-day in giorno packato, 10 mercoledi
consecutivi), slot migrato @10 -> @11 (coerente col finding "il buco migra", NON riproduzione
byte-exact della cella @10 del gate). p_hat(good)=0.667, Wilson 95% CI [0.21, 0.94]; **P(>=1 0-hard |
N=3) = 0.96 [0.50, 1.00]**. pick-best winner = baseline (0hard, best medium). Varianza reale: il
pick-best ha lavoro genuino (non il caso "tutti 0-hard, H17 non stressata"). Coerente col prior del
research (p~0,6 -> ~0,95).

**AC#3 (comparativo): multi-start ORTOGONALE/COMPLEMENTARE a ExactFinisher.** EF chiude la coda boxed
(H0) deterministico ed e' ortogonale a H17 (cella H1-bloccata); multi-start seleziona un bacino buono.
Compongono: N costruzioni, ognuna con EF, pick-best.

**Raccomandazione adottata:** multi-start = PROCEDURA operativa per source=input (NON default flip).
Nessun cambio default (source=golden, GRASP opt-in OFF). Limitazione onesta: n=3 non dimensiona N
stretto (CI larga); frequenza bad-seed = misura DIFFERITA in background (~10-20 run, non bloccante).
Piano B = TASK-482 Kempe-chain (aprire solo se la stima differita mostra frequenza alta o N inaccettabile).

Verdetto integrale + pacchetto operativo (procedura, N, wall-clock, quando, pick-best):
`claudedocs/spalmone-2026-27/task480-results/VERDICT-fase4-multistart-ab-2026-08-02.md`.

## COSA E' STATO FATTO (alba8)

1. **A/B presidiato e completato** (presidio sincrono via background task + cron one-shot, cancellato
   quando il task ha notificato prima del cron). Risultati dai raw (score.txt/analysis.json/phases.json).
2. **Bug di conteggio del SUMMARY scoperto e corretto**: lo script riportava "0-hard GRASP runs: 3 / 3"
   (includeva il baseline nel numeratore GRASP). Raw truth = 2/3 GRASP. Script fixato (baseline contato
   separatamente + riga pick-best winner); correzione appesa in fondo al SUMMARY (raw preservato).
3. **DoD completa** sul diff TASK-480 (GRASP `WeeklyTemplateBuilder` + `multistart-ab.sh`):
   - `/simplify` (4 agent paralleli): 2 finding applicati (banner `grasp=%b seed=%d` in
     `ScheduleService.refreshSolverStatics` + `graspEnabled`/`graspSeed` resi `public`, per
     silent-seed-drop observability; pick-best `best_medium` guard). 4 skippati con motivazione (loop-
     unify su hot path validato; parsing dedup; verdict restructure; api.sh-wait swap; shared-helper
     generalization -> fuori scope).
   - `/code-review high`: 2 Important fixati + verificati (boot-timeout kill-app + `trap INT TERM EXIT`
     contro orphan java; pick-best first-run poisoning). Reviewer ha confermato i 4 invarianti
     load-bearing (byte-identity, reservoir uniforme Algorithm R, no static Random, seed!=disabled).
   - `test.sh` EXIT=0, **968/0/0/21**, BUILD SUCCESS.
4. **Architettura**: `claudedocs/orchestration-layer-architecture.md` sez 4.1 (roadmap -> ADOPTED, con
   A/B + contratto + compl. EF) e sez 4.3 (fork chiuso, TASK-481 Done, multi-start chosen).
5. **CLAUDE.md**: aggiunto pointer TASK-480 DONE nella RATIFICA TASK-478.
6. **Backlog TASK-480 Done** (AC#1/#2/#3 + DoD spuntati, final summary).
7. **Commit `7dd96660`** (code + verdict + arch + backlog). Handoff in commit separato.

## STATO RUN / APP / INFRA

- **App DOWN.** L'A/B termina col kill-app dell'ultimo run. Profilo `multistart` (volume) esiste con lo
  stato dell'ultimo run GRASP. **Per ripristinare production**: `./planner.sh stop && ./planner.sh start
  production` (l'A/B aveva fermato production e usato il volume `multistart`). Non auto-ripristinato
  (scelta operativa dell'utente).
- planner-pg fermo o sul volume `multistart`. Risorse: 12 core / 36 GB RAM.
- Cron one-shot `eebdfa0f` (21:55) cancellato (l'A/B ha notificato prima).

## PUNTI DI RIPRESA / DEBITO RESIDUO

- **Misura differita (NON bloccante, background):** stimare la frequenza bad-seed con ~10-20 run GRASP
  addizionali sul profilo `multistart` (`multistart-ab.sh` e' resiliente, gira detached; caffeinate).
  Da k/n si dimensiona N per un target P(>=1 0-hard). Costo: solo wall-clock nei tempi morti.
- **TASK-482 Kempe-chain (piano B):** aprire SOLO se la stima differita mostra p(buono) basso o N
  richiesto inaccettabile. Con p_hat=0,67 non e' giustificato ora.
- **Deitto censito (skipped simplify/code-review, fuori scope di questo diff):**
  - `planner.sh prod --fresh` dispatch bug (`cmd_prod` shift-after-one-arg, `planner.sh:599`): il
    multistart script lo workarounda; fix upstream e' una riga (`"$@"`), ma tocca shared infra (task separato).
  - Same-seed GRASP reproducibility test mancante (`ConstructionIndependenceGateTest` asserte solo
    GRASP-OFF reproducibility; un test seed=1 twice -> equal profilerebbe la regressione static-Random).
  - `multistart-ab.sh` capture su wait-timeout cattura uno snapshot live (flaggare come non-final, o
    kill-app prima del capture su wait-fail).
- **Quarto run opzionale per raffinare la stima** (già coperto dalla misura differita sopra).

## PUNTatori

- Verdetto + pacchetto operativo: `task480-results/VERDICT-fase4-multistart-ab-2026-08-02.md`.
- A/B raw: `task480-results/multistart-ab/{baseline-grasp-off,grasp-seed-1..3}/` + `SUMMARY.txt`.
- Architettura: `claudedocs/orchestration-layer-architecture.md` sez 4.1 / 4.3.
- Gate: `task480-results/VERDICT-gate-indipendenza-2026-08-02.md`. Research: `research_multistart-vs-alternative_2026-08-02.md`. Recon: `task480-recon-timefold-multistart-2026-08-02.md`.
- Script: `scripts/benchmark/multistart-ab.sh`. Codice GRASP: `WeeklyTemplateBuilder.java`.
- Backlog: TASK-480 Done; TASK-481 Done; TASK-482 To Do (piano B); TASK-479 Done (arch page).
- Handoff precedenti: alba7 (A/B in corso), alba6 (TASK-481 shell-game fork).
