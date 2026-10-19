# HANDOFF 2026-08-02 alba7 (zai). TASK-480: gate INDIPENDENZA PASSATO, Fase 3 A/B in corso.

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (commit `71d3806e`,
> pushato al `77510051`). **maintainer sveglio/attivo.** Regole standing invariate (presidio
> sincrono dei solve, verdetti dai raw, tempi assoluti, commit+push, handoff+/clear a ~450k,
> mai fermo, DoD obbligatoria, default source resta golden, GRASP resta opt-in OFF).

## STATO LIVE (19:12 CEST, A/B IN CORSO)

A/B LANCIATO e validato: `caffeinate -is ./scripts/benchmark/multistart-ab.sh 3 multistart
--baseline --no-setup` (background task `b1ag0alwy`, log `/tmp/task480-ab.log`).
- 19:12:06 setup skipped (--no-setup, profilo multistart READY: fresh seed + import-calendario
  + pre-assign source=input COMPLETE).
- 19:12:20 baseline run booted in PROD mode (java -jar, `-Dsource=input` confermato), poll
  readiness via api.sh health (`boot: ready pid 58878`). solve-staged started.
- 19:15: baseline solve in `staged: B-TIMESLOT-1` (LS). NOT_SOLVING e' NORMALE nel staged
  (stagedStage e' il segnale di verita'). Ciclo ~50min/run.

4 run previsti (baseline + grasp-seed-1/2/3), ~3.3h totali. Output in
`claudedocs/spalmone-2026-27/task480-results/multistart-ab/` + `SUMMARY.txt` (verdetto).

### Babysit (post-clear, PRESIDIO SINCRONO - la shell in background NON risveglia a fine run)
- Poll ogni ~9min: `cat .../multistart-ab/SUMMARY.txt | tail` + `./scripts/api/api.sh status`
  (leggi `stagedStage`; null = ciclo finito per quel run). Cronologia ~31-50min/run; hard
  converge solo nell'ultima fase (NON interrompere per sospetto stallo: NOT_SOLVING +
  stagedStage non-null = ciclo vivo).
- "wait TIMEOUT/FAILED" nel log e' un falso allarme (api.sh wait exit non-zero): il capture
  prende comunque lo score reale. Fidati dello score in `<run>/score.txt`, non del log wait.

### RISULTATI PARZIALI (19:56 CEST)
- baseline-grasp-off: **0hard/-1029611medium/6433soft, ~31min** (1848s). LS seed buono: il
  bad-seed H17 NON si e' riprodotto su questo run (H17 e' stocastica su source=input, ~0.6
  P(0hard)). 0hard e' comunque il target.
- grasp-seed-1: running (B-TIMESLOT-1, started 19:43). grasp-seed-2/3: in coda.

### VERDETTO FINALE (Fase 4) - PROMEMORIA
Il verdetto A/B (P(0-hard) su n>=3 + wall-clock, in SUMMARY.txt) va unito in UN UNICO doc di
verdetto Fase 4 che DEVE riportare anche il RISULTATO DEL GATE DI INDIPENDENZA con i numeri
(premessa di tutto): i profili di saturazione @10 delle 5 costruzioni GRASP dal doc
`task480-results/VERDICT-gate-indipendenza-2026-08-02.md` (5/5 teacher-multisets distinti,
13/14 gruppi variano, docenti liberi @10 per seed). Senza il gate, l'A/B non ha premessa.
Interpretazione onesta: se tutti i run sono 0hard, l'H17 non si e' riprodotta (stocastica) e
il multi-start raggiunge 0hard trivially; se alcuni -6 + alcuni 0hard, il multi-start picka
i 0hard (valore dimostrato). Verifica anti-bug: lessons.txt baseline vs grasp-seed-1 DEVONO
differire (GRASP arrivato alla app).

### Fix infra applicate (commit `71d3806e`)
- `planner.sh prod --fresh <profile>` e' BUGGATO (dispatch `cmd_prod "${1:-}"` riga 599 passa
  un solo arg, droppa il profile). Workaround: `planner.sh prod <profile>` NON-fresh su volume
  non-istente crea+seeda; il fresh reset e' esplicito (stop + delete volume).
- Readiness poll via `api.sh health` (grep ^HEALTHY), non curl /q/health (lagga dietro gli
  startup statics su fresh-seed boot). WAIT_BOOT=420s.


## COSA E' SUCCESSO (alba7)

1. **Fase 1 DONE**: TASK-481 chiuso Done esito onesto (cura locale esclusa, H17 deferito a
   TASK-480, CompositionHoleCloser resta committed default-OFF). Commit `23747ace`.
2. **Fase 2 DONE - GATE INDIPENDENZA PASSATO** (commit `77510051`, pushato). GRASP-RCL
   iniettato in `WeeklyTemplateBuilder` (default OFF byte-identico, skeptic Opus CONFIRMED).
   Gate `ConstructionIndependenceGateTest`: la cella @10 (Wed slot2, buco H17 di G11) e'
   DIVERSIFICABILE non constraint-forced. 5/5 teacher-multisets distinti, 13/14 gruppi
   variano @10. Costruzioni GRASP lasciano liberi docenti diversi @10 -> bacini LS diversi.
   E' la precondizione che il portfolio DRAFT-005 refutato non aveva. Doc:
   `task480-results/VERDICT-gate-indipendenza-2026-08-02.md`.
3. **Fase 3 IN CORSO**: A/B N-processi sequenziale. Script `scripts/benchmark/multistart-ab.sh`.

## LA DECISIONE FORK (maintainer 02/08 sera, invariata)

OPZIONE C: multi-start VERO (TASK-480) e' la via PRINCIPALE per H17, NON cintura+bretelle.
Ma con GATE DI INDIPENDENZA OBBLIGATORIO prima di qualunque solve (research
_multistart-vs-alternative_ finding 4a). Il gate e' PASSATO. Piano B = TASK-482 Kempe-chain
(si apre SOLO se l'A/B mostra P(0-hard)=0 o costo Nx inaccettabile).

## FASE 3: L'A/B MULTISTART (come riprendere)

**Domanda**: N staged solve con N seed GRASP indipendenti (source=input, regime bad-seed)
danno P(almeno un run 0-hard) > 0, dove un single-run deterministico talvolta becca H17=-6?

**Script**: `scripts/benchmark/multistart-ab.sh <N> [profile] [--baseline] [--setup-only]`
- Sequenziale su un profilo (regola single-DB-actor + 23 static volatile vietano in-JVM
  parallel; GRASP -D deve stare su java -jar per reachare la app, quindi reboot per seed).
- Setup一次: rebuild JAR + profilo fresh + import-calendario + pre-assign (source=input).
- Per ogni run: `PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.source=input
  -Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed=$i"` ->
  `prod-start-and-wait.sh` -> `api.sh solve-staged` -> `api.sh wait 5400` ->
  `capture-run.sh outdir` -> `kill-app`.
- Pick-best su (hard, medium) + P(>=1 0-hard) in `SUMMARY.txt`.

**Lancio previsto**: `caffeinate -is ./scripts/benchmark/multistart-ab.sh 3 multistart --baseline`
(1 baseline GRASP-off + 3 GRASP seed = 4 solve ~50min ciascuno = ~3.3h wall-clock).

**Output**: `claudedocs/spalmone-2026-27/task480-results/multistart-ab/` con `<run>/` per
ogni run (capture-run artifacts) + `SUMMARY.txt` (verdetto).

### Come babysittare (presidio sincrono)
- Poll: `tail -f claudedocs/spalmone-2026-27/task480-results/multistart-ab/SUMMARY.txt` e
  `./scripts/api/api.sh status` (stagedStage e' il segnale di verita', NON solverStatus).
- NOT_SOLVING per tutto il solve staged e' NORMALE (costruzione del ciclo ad-hoc Solver).
  Il ciclo e' finito quando `stagedStage` e' null. Cronologia di riferimento: ~49min,
  hard converge solo nell'ultima fase (non interrompere per sospetto stallo, vedi
  handoff alba6 "due falsi segnali").
- Un run che fallisce NON abortisce l'A/B (script resilient, logga e salta).

### Verdetto (dai raw, 3 righe)
- P(>=1 0-hard sui run GRASP) > 0 -> MULTI-START WORKS. Fase 4 = pacchetto operativo
  (parallelo N-container per wall-clock ~1x). AGGIORNARE CLAUDE.md con la procedura.
- P = 0 -> multi-start non chiude H17 qui. Aprire TASK-482 Kempe-chain (connectivity cure,
  non paga Nx). Aumentare N non aiuta se le costruzioni non diversificano il bacino giusto.
- Confronto baseline (GRASP off) vs GRASP: se baseline=-6 e un GRASP=0, prova pulita.
  Se baseline=0 gia', la stochasticita' LS ha favorito (nota, non invalida).

### Verifica che il flag GRASP reachi la app (anti-bug)
Confronta le `lessons.txt` di `baseline-grasp-off` vs `grasp-seed-1`: se DIFFERISCONO nella
fase di costruzione, GRASP e' arrivato alla app. Se sono identici, PLANNER_JAVA_OPTS non e'
giunto al java -jar (bug da fixare prima di trustare il verdetto).

## FASE 4 (dopo il verdetto A/B)
- Se P>0: pacchetto operativo per maintainer (procedura, NON flip di default). Script parallelo
  N-container (N postgres su porte 5432+i + N app su 8080+i + N volumi) per wall-clock ~1x.
  DoD completa. AGGIORNARE CLAUDE.md + QUADRO. Verdetto comparativo multi-start vs
  ExactFinisher (AC#3 TASK-480).
- Se P=0: handoff a TASK-482, multi-start archiviato con evidenza.

## STATO RUN / APP / INFRA (al lancio, 18:45 CEST)
- App era UP (jar pre-GRASP, profilo production = A/B run2 finale 0hard). L'A/B fa kill-app
  + stop (ferma planner-pg production) + avvia planner-pg col volume `multistart` fresh.
  **Per ripristinare production dopo l'A/B**: `planner.sh stop && planner.sh start production`.
- planner-pg up (container singolo, porta 5432, volume = il profilo attivo).
- Risorse: 12 core / 36 GB RAM (parallelo N-container fattibile in Fase 4).

## PUNTI DI RIPRESA
- Gate verdict: `task480-results/VERDICT-gate-indipendenza-2026-08-02.md`.
- Script: `scripts/benchmark/multistart-ab.sh` (syntax-check OK, comandi api.sh verificati).
- Research: `claudedocs/research_multistart-vs-alternative_2026-08-02.md`.
- Backlog: TASK-480 In Progress (AC#1 done, AC#2 A/B in corso, AC#3 verdetto comparativo da
  fare); TASK-481 Done; TASK-482 To Do (piano B).
- Handoff precedenti: alba6 (TASK-481 shell-game fork), alba5b (TASK-479 Done + TASK-481 fork).
