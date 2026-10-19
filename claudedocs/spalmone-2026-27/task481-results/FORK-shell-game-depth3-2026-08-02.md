# FORK TASK-481: la cascata depth-3 e' uno SHELL GAME (close sostenibile 0/6). 2026-08-02.

> Sessione alba6 (zai), ripresa dal mandate Fedora (Opzione A). **La "GREEN" del gate depth-3
> (alba5, 6/6) era un FALSO POSITIVO**: la probe accettava con `net H17 < 0`, condizione troppo
> debole. Ristretta (close sostenibile = bloccante edge-removable), il verdetto e' **0/6
> sostenibili, 6/6 shell-game**. Stop onesto (mandate #3), A/B annullata, fork a Fedora.

## TL;DR (per il decisore)

1. **La cascata depth-3 cross-group CHIUDE il buco @10 di G11 solo come shell game.** Riempie @10
   (donor same-teacher) ma il bloccante ricollocato e' **mid-run** sul giorno del proprio gruppo:
   rimuoverlo apre un gap @10 per quel gruppo. Il buco @10 si **sposta di gruppo**, non si chiude.
2. **Il `-1` netto (e il `-2300` medium) della probe alba5 veniva da un fill non correlato**: la
   destinazione del bloccante riempie un gap altrove (es. G07 su 2026-09-15@14). Net H17 -1, ma
   il buco @10 e' solo migrato. La probe alba5 accettava perche' guardava il netto, non la
   sostenibilita'.
3. **Diagnostica decisiva (`diagnoseBlockerDeparture`): tutti i 66 bloccanti @10 (11 gruppi x 6
   mercoledi) sono midRun=true.** Ogni gruppo ha il giorno packato (`[0,1,2,3,4,5]` o `[0,1,2,3,4]`),
   bloccante @slot2 (ore 10) sempre in mezzo. Nessun bloccante edge-removable => nessuna chiusura
   sostenibile a depth 3.
4. **Una cascata piu' profonda (iterativa) non converge.** @10 e' uno slot saturo: tutti gli 11
   gruppi sono packati. Inseguire il nuovo gap del bloccante sposterebbe il buco al gruppo successivo,
   ciclando tra gli 11 senza mai azzerare H17. E' un artefatto di **bacino**, confermando lo spike
   originale ("cella @10 saturata di docenti d'aula, hard-forced nel vicinato 1/2-mosse").

## Cosa e' stato costruito (tutto committed/pushed o in questo diff)

- **`CompositionHoleCloser` (solver/, nuovo, flag default OFF opt-in)**: sibling di
  LabDayConsolidation che generalizza la cascata cross-group ai **giorni d'aula** (LDC scopa solo
  lab-day). Riusa tutti i primitive condivisi (BoxRepair gates, HoleRepair.findImport/
  commitCrossDayMove, SurplusRebalance.keepsDayAnchored, StudentDayCompaction indici), improve-or-
  noop con revert, CandidateBudget EF-style, runner `runCompositionHoleCloser` post-LDC/pre-AnchorRepair.
  **CORRETTO ma INERTE** sul bad-seed target: il gate `isDepartureSafe` rifiuta lo shell-game (66/66
  bloccanti departure-unsafe), 0 chiuse, 0 conflitti, nessun danno. Verificato dal test
  `compositionHoleCloserClosesBadSeed` (assert inert).
- **Discriminatore depth-3 ristretto (`depth3CrossGroupCascade`)**: ora classifica SUSTAINABLE
  (bloccante edge) vs SHELL-GAME (bloccante mid-run). Verdetto: **sustainable 0/6, shell-game 6/6**.
  `isMidRun` e' il detector. `showOneShellGame` mostra una cascata rappresentativa (net -1 via fill
  non correlato) come evidenza. Assert `sustainable == 0` come guardia di regressione.
- **`diagnoseBlockerDeparture`**: diagnostica decisiva (shape del giorno del bloccante + midRun).

## Il meccanismo dello shell-game (perche' la probe alba5 sbagliava)

Probe alba5 accettava una cascata con `h17d < 0 && ttier == 0`. Per il buco G11 @10:
- bloccante = Orientamento(T03)@10[G07] -> 2026-09-15@14
- donor = Orientamento(T03)@10[G11] (riempie il buco)
- `h17d = -1` perche': G11 @10 -1 (buco riempito) + G07 @10 +1 (bloccante mid-run, gap aperto
  sul suo giorno) + G07 2026-09-15@14 -1 (la DESTINATION del bloccante riempie un gap non correlato).

Il netto -1 nasconde che il buco @10 e' migrato da G11 a G07. La **close sostenibile**
richiede un bloccante edge (rimozione non apre gap): al slot @10 saturo non esiste. La probe
ristretta (midRun detector) lo prova: **0/6 sostenibili**.

## Catena causale verificata (link-by-link)

| # | Anello | Stato | Fonte |
|---|--------|-------|-------|
| 1 | Il buco G11 @10 e' un artefatto solver (golden 0/34 compatto) | CONFIRMED | diagnosi alba4 (verdetto invariato) |
| 2 | HoleRepair (same-group import) non chiude: nessun donor same-group con docente libero @10 | CONFIRMED | spike 1-move 0/6 |
| 3 | LabDayConsolidation non scopa il giorno: e' un giorno d'aula (0 lab, soglia >=4 non raggiunta) | CONFIRMED | LDC.isLabDay; CompositionHoleCloser e' il complemento d'aula |
| 4 | La cascata depth-3 riempie @10 ma il bloccante e' mid-run => apre gap @10 sul gruppo del bloccante | CONFIRMED | diagnoseBlockerDeparture: 66/66 midRun |
| 5 | Nessun bloccante @10 e' edge-removable (tutti i giorni packati) => nessuna close sostenibile depth-3 | CONFIRMED | probe ristretta: sustainable 0/6 |
| 6 | @10 saturo (11 gruppi packati) => cascata iterativa ciclerebbe senza azzerare H17 | INFERRED (meccanicamente) | tutti i gruppi @10 sono mid-run |

## Il fork (per Fedora/maintainer)

La cura NON e' una cascata locale limitata (depth 3 shell-game; depth N cicla sul bacino saturo).
Opzioni superstiti:

- **C. Multi-start (TASK-480), opzione leading.** Il problema e' strutturalmente di bacino: i good
  seed (run1/run3) hanno 0 buchi @10, i bad seed (run2/run4) saturano @10. Multi-start vero
  (costruzioni indipendenti) e' la via principale per H17, non cintura+bretelle. 0 lavoro su TASK-481
  (lavoro in TASK-480). COSTO: 0-hard "single-run deterministico" diventa "0-hard nel migliore di N
  run" (wall-clock ~Nx).
- **E. Costruzione composition-aware (nuova).** Costruire una composizione @10 compatta (come il
  golden) fin dalla fase 1, cosicche' il bacino bad-seed non si formi. RISCHIO: la LS rimescola la
  composizione per risolvere H1 globale (PROPOSTA Opzione D lo aveva gia' escluso per questo).
- **F. Mossa globale (2-Kempe / cartesian mirato).** Scambiare wholesale la composizione @10. Ma
  tabu+pillar+change+swap girano gia' e non la compongono (vicinato 1-mossa; pillar muove colonne,
  non questa cascata mirata). Incerto.
- **Mantenere CompositionHoleCloser default-OFF** come building block corretto per buchi H17 d'aula
  NON saturi (pattern non ancora osservato sui seed correnti). Inerte sul target, nessun danno.

**Raccomandazione alba6:** il bacino saturo @10 rende **C (multi-start)** la via onesta per questo
buco (0-hard via selezione del bacino, non chiusura locale). CompositionHoleCloser resta committed
default-OFF come sibling corretto, in attesa di un target non-saturo o di rimozione. La decisione
(spessore del multi-start vs costruzione composition-aware vs rimozione del pass) spetta a Fedora/maintainer.

## Puntatori ai raw / codice

- Probe ristretta + diagnostica: `src/test/java/com/school/planner/bootstrap/H17HoleCloseDiscriminatorTest.java`
  (`depth3CrossGroupCascade`, `probeDepth3`, `isMidRun`, `showOneShellGame`, `diagnoseBlockerDeparture`,
  `compositionHoleCloserClosesBadSeed`). Run: `PLANNER_TEST_H17_HOLE=1 ./scripts/core/test.sh -c H17HoleCloseDiscriminatorTest`.
- Pass di produzione: `src/main/java/com/school/planner/solver/CompositionHoleCloser.java`;
  wiring `ScheduleService.runCompositionHoleCloser`; flag `SchoolSchedule.isCompositionHoleCloserEnabled`
  (opt-in `-Dplanner.composition-hole-closer.enabled=true`).
- Bad-seed export: `claudedocs/spalmone-2026-27/task478-results/ab-ef-probe-1m-run2/export.json.gz`.
- PROPOSTA originale (fork A/B/C/D): `task481-results/PROPOSTA-cura-h17-2026-08-02.md`.
- Verdetto probe alba5 (il falso positivo, ora superato): `handoff-2026-08-02-alba5b-tasks479-481.md`.

## Regole standing rispettate

Verdetti dai raw (probe ristretta + diagnostica leggono lo score reale e il grid). Stop onesto al
gate (mandate #3: depth-3 insufficiente => nessuna A/B forzata). Nessun flip di default (source
resta golden; CompositionHoleCloser resta opt-in OFF). DoD sul diff: /simplify + /code-review high +
test.sh exit 0 (967/0/0, 20 skipped gated).
