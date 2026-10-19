# Orchestration checkpoint — overnight autonomous run (2026-07-16)

**DO NOT COMMIT this file. Delete it when the run ends.**
User instruction (before sleeping): proceed autonomously through the backlog activated by today's new documents (spalmone 2026/27 + calendario scolastico), one task after another. EVERY completed task gets: my independent verification (test.sh / typecheck / build), then code-review HIGH (finder batches → Opus verify → fix agent), then simplify (agents run it themselves), then commit. Never mark done without green evidence I ran myself.

## State (as of writing)

- Branch: feature/cartesian-block-entity. Commits today: 1879f51 (plan), 4256075 (352 doc), 8f5669d (341), ea86769 (356 merge), da17775 (342), dfc494c (epoch-guard flake fix), 181f23b (356 fixes), 217ef66 (342 fixes), 88a9fb0 (344 design doc). NOTHING pushed.
- DONE+committed: TASK-341, TASK-342, TASK-356 (e2e execution pending live app), TASK-352 (open collector), TASK-344 design doc (implementation deferred into TASK-346 per doc).
- IN FLIGHT: TASK-343 fix agent (fixes Y1-Y12 on the uncommitted working tree; baseline 359 tests). On delivery: verify test.sh in MAIN TREE (beware: cd — a previous check accidentally ran in the 358 worktree), commit, close task with final summary.
- PARKED: TASK-358 committed on worktree branch `worktree-agent-a2c89d8011e6e6b0e` (commit 131b546, based on 217ef66, worktree at .claude/worktrees/agent-a2c89d8011e6e6b0e). After 343 commits: merge branch into main branch, npm install in src/main/webui if needed, verify typecheck+build+tests, then code-review HIGH on its diff (2 finder batches + Opus verify + fix agent), commit fixes, close task (e2e authored-not-run), remove worktree + branch.

## Night queue (dependency order, spalmone-focused)

1. Finish TASK-343 (verify fix agent → commit → close).
2. TASK-358 merge + review cycle (see PARKED).
3. TASK-345 recurring teacher availability + Vincoli Docenti transcription (backend; full text in analysis.md §5; SeedCsv utility now exists for loading; expansion approach preferred; NOT-YET-MODELED list mandatory; new questions → domande-per-la-scuola.md).
4. TASK-346 golden solution import (depends 343+344: stage rows = pinned pseudo-lessons per claudedocs/design/stage-esame-ra-modeling-2026-07-16.md; Esame skip/stub; RA under real teachers; rooms null; dedicated profile planner-data-golden + wrapper script; report unmatched rows).
5. TASK-364 calendario scolastico importer (format doc: claudedocs/spalmone-2026-27/calendario-scolastico-format.md; MUST validate pinned placements against GroupCalendarService — annotated in task).
6. TASK-347 golden audit (depends 346; predictions in analysis.md §3-4; write claudedocs/spalmone-2026-27/golden-audit.md; NO constraint changes).
7. TASK-353 reconciliation endpoint+dashboard, TASK-354 legality watchdog (m-9; 354 before 357 dependency-wise).
8. TASK-348 (Teacher.maxDailyHours, T01 8), TASK-349 (H15/16 recalibration), TASK-350 (lunch-aware H17/H11/H18 + ScheduleService mirrors note) — use audit numbers from 347.
9. If app rebuild becomes safe (no agents editing), run the full local cycle + Playwright e2e to close 356/358 ACs.

## Operating rules learned this session

- ONE backend implementation agent at a time on the main tree (mvn target/ collisions). Frontend work → worktree. Docs-only tasks parallel OK.
- Review flow per task: 2-3 Sonnet finder batches (correctness angles A/B/C + quality reuse/simplification/efficiency/altitude/conventions) → Opus verifier(s) on load-bearing candidates → Sonnet fix agent with verified list → my own test.sh/typecheck rerun → commit (code + backlog together).
- Always `cd /home/user/data/repo/personal/planner` before verification commands.
- backlog CLI: `backlog task edit N -s Done --final-summary "..." --check-ac K`; milestones done via file edits.
- Implementation agents: Sonnet, spec in one shot, "do NOT commit", finish with Skill simplify + rerun tests.
- New school questions go into claudedocs/spalmone-2026-27/domande-per-la-scuola.md (Q1-Q9 exist).
- Deferred findings get annotated into the owning backlog task immediately.
- Teacher count 26, groups 14 (incl. G14), rooms 18 (R17 + R18 cap 0), courses 268 real / 29 pseudo, timeslots 1368 (171 days × 8 slots; TASK-408 was 1376/172 before the 2027-01-22 closure typo fix), year 2026-09-14..2027-06-10.

## Update (night, post TASK-345/346/364)

- DONE additionally: TASK-345 (f1f343c), TASK-346 (4b747a3), TASK-344 doc, epoch submission fix. TASK-364 PARKED on worktree branch worktree-agent-a77286a3465ca5b73 (commit 194fce2) — merge + code-review high AFTER 347 finishes (its report is delivered; review still owed!).
- IN FLIGHT: TASK-347 audit agent running against the LIVE app (owns the DB exclusively now).
- INCIDENT + NEW RULE: the 364 worktree agent ran against the SHARED live Postgres and truncated tables mid-347-audit (DataGenerator auto-reseeded; 347 agent was told to re-verify/redo). RULE: implementation agents must NEVER touch the live DB/app — live-system steps (planner.sh, api.sh, db-query.sh) are reserved to the single designated live-cycle agent or the orchestrator; say so explicitly in every worktree agent prompt.
- Q16 (G14 calendario-vs-spalmone divergence) added to domande.
- After 347: run Playwright e2e (356/358) against the live app, close their ACs; merge+review 364; then 353/354 (m-9).

## Update 2 (early morning 2026-07-17)

- DONE additionally: TASK-347 (audit, 6b0fba4 + TASK-365/366 created), e2e first live run (537fc50, 356/358 ACs closed, TASK-367 created), TASK-364 (9f83c260), TASK-353 (59f4b036 + 5115af2c fixes), TASK-354 (merged 27e83b07 + repair c0edc298 + fixes e5f91437). m-7 COMPLETE; m-9 core (353+354) COMPLETE. 494/494 tests. ~24 commits, nothing pushed.
- IN FLIGHT: TASK-366 (main tree: H4 pseudo exclusion + RA micro-course import) and TASK-348 (worktree: Teacher.maxDailyHours + H7 + T01 8). On delivery: verify (EXIT CODES, not piped tails — a tail-masked typecheck failure slipped a broken merge commit earlier, repaired in c0edc298), review high, fix, commit, close.
- REMAINING m-8 after these: 349 (H15/H16 recalibration, audit says 98 organic), 350 (lunch-aware H17/H11/H18 + ScheduleService mirrors + M18 gate note), 365 (M21 recalibration — biggest medium component), 355 (golden regression suite — needs 348/349/350/366 landed), 363 (benchmark solver vs human — needs live app + long solve, likely NOT tonight).
- The live app on profile dev has the golden import loaded (audit state). AC-live steps pending morning: reconciliation golden spot-check (353 notes), TASK-366 AC1 re-audit (H4=12, H17=1 expected), import-calendario live run.
- App may need a rebuild before live checks (commits since last rebuild: 353/354/366/348 changes).

## FINAL (morning consolidation, 2026-07-17 ~06:30)

Overnight run ENDED deliberately here (context limit; remaining tasks deserve fresh context).
- Additionally DONE: TASK-353 (59f4b036+5115af2c), TASK-354 (27e83b07+c0edc298+e5f91437), TASK-366 (be8f9d87), TASK-348 (merge+d3257f68). 512/512 tests, typecheck+build clean, ~29 commits on feature/cartesian-block-entity, NOTHING pushed.
- NEXT SESSION queue (all specs+audit numbers already in the task files): TASK-349 (H15/H16, audit: 98 organic), TASK-350 (lunch-aware H17/H11 + ScheduleService mirrors + M18 gate), TASK-365 (M21 — biggest medium miscalibration), TASK-355 (golden regression suite — now unblocked by 348/366), TASK-367 (e2e triage), TASK-363 (benchmark, needs long live solve), UX: 357/359/360/361/362.
- MORNING LIVE CHECKS (app on profile dev still runs PRE-d3257f68 code): rebuild.sh, re-import golden, re-run audit numbers (expect H4=12, H17=1, H7=0), reconciliation spot-check (rows in TASK-353 notes), import-calendario live dry-run.
- DELETE THIS FILE when the run is reviewed.

## PAUSA per riavvio PC (2026-07-17 pomeriggio)

STATO AL CHECKPOINT: branch feature/cartesian-block-entity, tutto committato, working tree pulito (solo questo file untracked). 515/515 test verdi (exit 0). NIENTE push.
- Strategia autonoma codificata: fasi in m-8 (0-2), m-11 (3), m-10 (4); DoD obbligatoria in backlog/config.yml + CLAUDE.md + task aperti.
- DONE oggi (dopo le risposte scuola): TASK-371, TASK-374 (DoD completa), risposte trascritte, vincoli-in-parole analizzato, TASK-365/368/369/370/371/372/373/374 creati/aggiornati.
- IN SOSPESO: TASK-373 committato ma NON chiuso — manca la code-review high sul suo commit (nota nel task). ALLA RIPRESA: 1) review high del diff TASK-373 → fix → chiudi con DoD; 2) gate TASK-355 (suite regressione golden); 3) ondate Fase 2: 349/350/365/368/369/370/372; 4) m-11 Fase 3.
- App live: era sul profilo dev con golden import PRE-fix; dopo il riavvio servirà comunque un ciclo fresco (dev-start-and-wait + import-golden) prima di audit live/e2e.
- Nessun agente in volo al momento della pausa.

## Notte 2 (2026-07-17→18, "portati avanti più che puoi")

DONE stanotte (ciclo DoD completo ciascuno): TASK-373 (a24fcb7e — expectedUnscheduledHours), TASK-351 (4aa8eefa — NO-GO template-first, TASK-375 creato), TASK-355 (a45bbf41 — GUARDIANO ATTIVO: pins H1/H3/H6/H20/H11/M20=0, H4=-12, H15=-9420/98, H7=0, H17=-3/2 root-caused), TASK-372 (9122bd4e — H21 + VRP filter). TASK-367 quasi (1b95bde9): manca SOLO il run e2e live per AC1.
IN FLIGHT: TASK-368 (worktree, choice-day M-constraints + revisioni CSV disponibilità), TASK-357 (worktree, pannello desiderata manual placement).
BASELINE: 528/528 exit 0 al commit 9122bd4e.
PROSSIMI dopo i due in flight: 365 (M21 whitelist) → 349 (H15/H16) → 350 (lunch-aware H17/H11 + mirrors) → 369 → 370. POI il ciclo live finale: rebuild + fresh profile + import-golden + re-audit (attesi = i pin del guardiano) + ui-test.sh completo (chiude 367 AC1, 356/358 note) + import-calendario live + eventuale primo solve (m-11; NO-GO template ⇒ pipeline attuale; TASK-363 protocollo se il tempo regge).
REGOLE INVARIATE: un solo attore sul DB live (io); worktree per parallelismo; exit code sempre; DoD spuntata a ogni chiusura.

## Notte 2, aggiornamento 2 (~04:30)

DONE aggiunti (DoD completa): TASK-357 (418fc88d, desiderata panel), TASK-368 (95b967a1+merge 6fa32f8e, M24/M25 choice-day), TASK-365 (15f5c2f9, M21 -252,300->-621), TASK-349 (86a87bf6, H15 break-level pinned exemption -> golden 0/0), TASK-350 (c378f532, H17 0/0 + M18 calendar-gate 0/0). TASK-376 creato (drill-down M18 stantio). Baseline 560/560 exit 0.
GUARDIANO ORA: zero-tolerance H1/H3/H6/H20/H11/M20/H21/H15/H16a/H16b/H17/M18; H4=-12 (rientri); H7=0; M21=-621/452; M24=-16/15; M25=-27/27.
IN CODA Fase 2: TASK-369 (in lancio), TASK-370. POI: ciclo live finale (rebuild + fresh dev + import-golden + re-audit vs pin del guardiano + ui-test.sh completo -> chiude 367 AC1 + import-calendario live + eventuale primo solve m-11 con pipeline attuale, NO-GO template deciso in 351).
TRAPPOLA RICORRENTE: git merge va eseguito nel MAIN tree (cd /home/user/data/repo/personal/planner) — tre sviste stanotte, mai committato nulla di sbagliato ma tempo perso.

## FINALE notte 2 (mattina 2026-07-18)

FASE 2 (m-8) COMPLETA: 349/350/365/368/369/370/372 tutti Done con DoD. Guardiano: zero-tolerance su H1/H3/H6/H15/H16/H17/H20/H21/H22/H11/M18/M20; H4=-12; H7=0; M21=-621/452; M24=-16/15; M25=-27/27; M26/M27=0; S12=+5623.
CICLO LIVE ESEGUITO: fresh profile + rebuild + import-golden (report identico ai pin: 10.527+1.993, 311 stageOnClosure, 0 unmatched) + audit live IDENTICO al guardiano. DUE BUG DI PRODOTTO trovati dal ciclo e chiusi (2113c0e6): pin-su-non-assegnata 409+healing; DELETE corso con cascade lezioni. Round finale e2e (9058af4f): AG Grid warning, toast race + fetch sequence guard, spec repairs.
E2E LIVE: verdi navigation/dark-mode/course-grid/group-calendar/admin/view-switch; rossi residui in TASK-367 punch list (preassign-toast, reset-button, manual-placement, progression-button, timeline-colors/scroll) — servono error-context freschi, NON rattoppare oltre.
NON FATTO (prossima sessione): TASK-363 primo solve+benchmark (m-11 — pipeline attuale per il NO-GO template; l'app è pronta, basta import-calendario + solve), TASK-375 weekly-stability, 376 drill-down M18, 361/359/360/362 UX, TASK-364 re-import quando arrivano i fogli riorganizzati.
Commits notte 2: ~15 (da a24fcb7e a 9058af4f). Tutto locale, NIENTE push. Tests 596/596.

## Giorno 3 (2026-07-18 mattina, utente sveglio: "fai 375+363, poi docs")

- TASK-375 Done (dda6a3e8): S13 weekly stability, join direzionale, pinned=anchor-only; A/B protocol nelle note per il run 363.
- Divergenza #8 esclusa (23a35db2): calendario_moda 313->312, import ora persiste pulito.
- DOCS IN-APP Done (afea2eb7 + merge 8128ef09): sezione "Processo 2026/27" en+it, audit carte vincoli vs SORGENTE Java (H9/H19 stantii rimossi, H0/H20/M18/M22-M25 aggiunti), SolverPhasesPage allineata a solverConfig reale. M22 id/tier mismatch flaggato in TASK-376.
- PRIMO SOLVE IN CORSO: profilo fresco + 922 pin calendario + solve avviato ~08:30. Score: -88.8k hard @08:33 -> -75.1k hard @08:47 (H0-dominated, CH in coda), medium/soft in discesa. Monitor b28t5fzjj attivo.
- AL TERMINE DEL SOLVE: catturare score finale + feasibility, confronto per-vincolo col golden (via analysis API + i numeri del guardiano), aggiornare experiment-log/363, decidere A/B S13 (secondo run senza S13 a pari budget) o rimandare. NON chiudere 363 senza il protocollo 2-run — o chiuderlo parziale con nota onesta.

### TASK-377 (2026-07-18 mattina): la cecita' forEach — svolta della giornata

- Il primo solve ha esposto: 6.343 impilamenti con H3=0. Root cause provata con stash-test:
  forEach salta entita' con room null; stanze assegnate solo in fase 5 → H1/H2/H3/H16a spenti
  nelle fasi timeslot. Esenzioni same-course/isSameLabSession = pura superficie di exploit
  (slot 1h half-open non si sovrappongono mai tra slot diversi) → rimosse, metodo cancellato.
- Fallout di taratura: H16a demansionato a S16 soft (golden: 15 passaggi docente deliberati,
  RA/Recuperi + Scienze T09→T25; guardiano pinna S16=-15/15). M22 riallineato a medium
  1000 (il suo ONE_HARD contraddiceva l'id M, zero evidenza golden perche' pinned-exempt).
- Config test: swap moves + late acceptance (il vecchio forager first-non-tabu era random walk
  che passava solo grazie all'impilamento illegale). Integration test ora con dump per-vincolo
  nel messaggio di fallimento (InnerScoreDirector, pattern ScheduleService).
- 605/605 verdi exit 0. DoD in corso: /simplify (4 agenti Opus) → /code-review high → commit.
- POI: rebuild, profilo fresco, re-import calendario, RILANCIARE il primo solve (run 2).

### Stato a fine pomeriggio 2026-07-18 (~15:50)

- Commits del giorno: 945c082a (TASK-377 unblinding), 6a27dd97 (TASK-379
  golden-first), af93e8b7 (TASK-378 comparatori/statici/lastSolverError),
  49fa74d6 (forensica run 5). Docs in-app mergiate (8128ef09).
- Run 1-5 tutti registrati in experiment-log.md. Run 5 fermato: la coda
  del fallback CH ignora il filtro; causa vera = fase 0 senza stanze +
  room nullable. PIANO PRECISO nella sezione run-5 dell'experiment log e
  nelle note TASK-378 (a: debug findAvailableRoom; b: fase 0 completa
  tutto; c: gate post-CH; d: eventuale rimozione fase 2 con ripple
  solver-config-sync).
- Prossima sessione: eseguire il piano TASK-378 fase 2, poi run 6.

### Sera 2026-07-18 (~18:40) — stato al momento del run 8

- PUSH ATTIVATO dall'utente: branch allineato a origin (ultimo 0a9a3757).
- Review-high recuperata sul merge docs (4ec73913: H20 exempt-claim, pomeriggi
  stantii, config fasi fittizie, chiavi morte).
- TASK-378 avanzata in 3 iterazioni oggi pomeriggio:
  it.1 (af93e8b7) comparatori/statici/lastSolverError;
  it.2 (ff3bc474+18284fad) fallback CH eliminata (pipeline a 5 fasi,
  rinumerata ovunque) + LA SCOPERTA PIU' PROFONDA: la fase custom mutava i
  campi senza notificare lo score director → context.execute(Moves.change);
  run 7-bis = PRIMO "Solving ended" della storia (117 min, -521.445 hard,
  stanze ok, nessun crash);
  it.3 (0a9a3757) force-place senza stanze occupate (-211k H2), pomeriggi
  esclusi dal riempimento base (M28), costo M28-aware → fase 1 da -2,04M a
  -1,63M.
- RUN 8 IN CORSO (avvio 18:37, monitor attivo): primo run completo con
  l'iterazione 3. Al termine: confronto con run 7-bis (-521.445), breakdown,
  log, commit+push.
- PROSSIME LEVE (378 it.4): H15-aware placement (partner adiacenti),
  ridurre i 1.913 leftover del round-robin (i suoi filtri variety/teacher
  sono piu' rigidi del necessario), H11 nel force-place cost gia' presente.
- Flake noto: testCumulativeTimeAccumulates (timing, verde in isolamento).

### 22:50 — TRAGUARDO STORICO
Run 9: PRIMA SOLUZIONE FATTIBILE (0 hard, 107 min, fino al 10 giugno).
TASK-378 chiusa (4 iterazioni). Tutto pushato (1ff92b09). Prossimi:
calibrazione M12 vs pratica golden, S1, G14 startdate (28/1, per la
scuola), A/B S13 (task-375), TASK-367 e2e punch list.

### Giornata autonoma 2026-07-19 (pomeriggio) — riepilogo
- A/B S13 ESEGUITO (task-375 AC2): S13 resta (+29% stabilita, 14.7% vs 11.4%).
- M12 ricalibrato golden-first (1000/g -> 1/g; il golden piazza 29.3% oltre
  i target; Q20 per referente). Non ancora esercitato in un run.
- TASK-380: endpoint status leggero 22ms (vs 8.7s) + poll change-driven —
  causa radice della punch list e2e. CHIUSO.
- e2e: preassign-toast 2/2, reset-button 3/3, state-transitions 13/15
  (2 residui diagnosticati in TASK-367). progression/timeline da rigirare.
- Tutto pushato (36ad0eeb). PROSSIMI: run 10 con M12 nuovo + confronto,
  2 residui e2e in isolamento, chunk timeline/manual-placement, benchmark
  onesto configurazioni.

### Sera 2026-07-19 — direzione approvata: TEMPLATE-FIRST (B+C)
- Run 10: 0 hard in 84 min, M12 sano. KPI somiglianza attivi: ricorrenza
  14,3% vs 41,5-72% golden; blocchi 209x2h vs 1.299 del golden.
- maintainer conferma il processo umano: scheletro lab/stage dai fogli TUTTI
  (DEF FC TUTTI = ore per giorno per lab, GIA estratto in
  calendario_fc.csv, 990h esatte/gruppo, oggi solo report) + pattern
  settimanali ricorrenti rotti solo dai vincoli maggiori.
- Design 3 strati nel nuovo TASK (skeleton -> template CH -> vincoli di
  forma golden-first). Prossima sessione: implementare L1.

### 21:05 2026-07-19 — TASK-381 L1 DEPLOYED, run 11 in corso
- L1 committato/pushato (e980b39d): scheletro lab FC/G13 pinnato dal
  golden (706 pin, 0 skipped; 170 giorni quota-mismatch vs DEF FC TUTTI,
  report-only, famiglia delle 518 discrepanze note - per la scuola).
- Run 11 avviato 21:01: 1.572 pin totali (866 MODA + 706 FC lab), CH
  piazza 8.929 (conto esatto), -25.562 hard a fine fase 0.
- Monitor: /tmp/run11-monitor.log. PROSSIMA SESSIONE: leggere esito run 11,
  calcolare i KPI somiglianza (ricorrenza, blocchi - attesi in
  miglioramento: i lab pinnati sono ricorrenti per natura), poi L2
  (template CH settimanale) e L3 (S4/M21 golden-first) da TASK-381.
- DoD di L1 da completare a inizio prossima sessione: /simplify +
  /code-review high sul diff e980b39d (piccolo, un metodo + hook).

### 2026-07-20 — verifica visiva pin + INCIDENTE APERTO
- Screenshot pins-only inviati a maintainer (Telegram): G01 15/09 = 3h
  Moulage/T17 slot 0-2 pinnate = riga MODA TUTTI esatta. Fonti pin
  CONFERMATE: solo Calendario Scolastico (866 MODA; FC gated OFF).
- Fix: SCHOOL_YEAR_START/END frontend erano hardcoded 2025/26 (week view
  partiva un anno prima dei dati); TODO derivarli dall API.
- INCIDENTE da investigare (prossima sessione): i 866 pin sono SPARITI
  dal DB tra due screenshot (0/10501) - sospetti: locator largo
  div:has-text del vecchio spec temporaneo che puo aver cliccato qualcosa
  di distruttivo, o un percorso al riavvio di rebuild.sh. Re-import li ha
  ripristinati; lo spec sicuro (getByRole) non li tocca. Serve root cause
  prima di fidarsi dei run con app condivisa + browser.

### 2026-07-21 — TASK-385 landed (f105569c); RUN 15 FERMATO per sospensione laptop
- TASK-385 completo con DoD (expected-hours per tutti i 14 gruppi, FCQuota
  guard, M20/template/stamp sull'atteso per-data; fix review: cap stage-
  exclusive nel CH, min-chain H11 in M20, multi-pass trim, 678/678).
- RUN 15 AVVIATO E FERMATO dopo pochi minuti (maintainer in movimento; le
  terminazioni Timefold sono wall-clock -> una sospensione lunga tronca le
  fasi e invalida la misura). ATTENZIONE ALLA RIPRESA: lo stop ha salvato
  nel DB uno stato PARZIALE - per il run 15 pulito rifare il ciclo intero:
  rebuild --build-only (se ci sono commit nuovi) -> kill-app/stop ->
  delete dev -> dev-start-and-wait -> import-calendario (attesi
  866/1800/280) -> pre-assign -> solve -> monitor (script scorewatch, NON
  api-get) -> a fine corsa: analysis + daily-fill-kpi.py + KPI ricorrenza/
  blocchi (script inline noti) -> experiment-log run 15 vs run 14 vs golden.
- Verdetto atteso da run 15: full-day rate da 55,5% verso 85,4% golden
  SENZA perdere ricorrenza 41,5% e 0 hard.

### 2026-07-21 (notte) — TASK-381 CHIUSO: run 14 = 0 hard + ricorrenza al livello golden
- L2 landed (b8f99404, DoD completa): WeeklyTemplateBuilder + timbro in
  DayClusteredConstructionPhase (round-robin = coda di riparazione).
- RUN 14 (82 min): 0 hard / -3,46M medium / +25,6k soft. Ricorrenza
  unpinned 16,9% -> 41,5% = ESATTAMENTE la stretta del golden; totale
  50,3%; blocchi >=2h 1.863 (golden 1.299); coppia G05 zero-slack
  sciolta per costruzione. Prezzo: M20 +626k (tuning futuro).
- TASK-381 Done, tutti gli AC. Aperti: TASK-384 (stage-day come fact ->
  confinamento VRP), TASK-382/383, TASK-367 2 e2e, M20-vs-template tuning,
  esenzioni M21 candidate (RA/Ra lab aperto/Tecn.creativita, bloccate su
  risposte scuola), Q21 per referente. Tutto pushato (4d4b934d).

### 2026-07-20 (sera 2) — L3 SHAPE RECALIBRATION landed (be55d4a9) + RUN 13 in corso
- L3 (DoD completa): M21 soglia 1->2 (golden: 363 giorni 2h ordinari, 272
  blocchi; pin -169/89, residuo = Ra lab aperto 81/28 + RA 43/25 + Tecn.
  creativita 24x3h - esenzioni candidate annotate nel task), S2 RIMOSSA
  (il golden la viola 13.259 volte; H15+M21 la superano), S17 nuova
  (reward 5/coppia adiacente stessa materia, pin +12.635/2.527),
  unimprovedSecondsSpentLimit(600) su fasi 2 E 3 (complemento assoluto al
  test relativo di diminishedReturns per i re-solve convergenti).
- RUN 13 avviato ~17:50 (import identico al run 12: 866+1800+280+5).
  Obiettivo AC#3: blocchi >=2h da 774 verso >=600 gia raggiunto al run 12 -
  la vera misura e' quanto sale la parte UNPINNED (253 al run 12) e la
  ricorrenza unpinned (16,9%). AC#4: 0 hard (run 12: -1.005 per una coppia
  H3 non pinnata + 5 H17).
- Al termine: breakdown + KPI (stesso script del run 12) + experiment-log +
  spunte AC. POI: L2 (timbro settimanale) resta l'ultima leva grossa.

### 2026-07-20 (pomeriggio 2) — STAGE ON THE GRID landed (9703ee20, pushato)
- maintainer: RA = recupero a discrezione del docente (estende le materie del
  docente stesso - spiega T15/officina); stage valido anche a scuola
  chiusa E DI SABATO (G10 7 sabati, G12 19 - scoperti dai tab classe).
- Autorizzata la modifica a DataGenerator: 7 corsi Stage pseudo + 2080 stub
  al seed; planStagePins dual-source (quota FC + S-rows dei tab classe per
  gli altri 5 gruppi); 1800h pin / 280h off-grid (expectedUnscheduledHours) /
  5h oltre budget - conservazione esatta, verificata su CSV reali.
- Review-high: fix esenzione pseudo in VRP H4.1 (885h di stage FB/FD
  altrimenti mai piazzabili nella propria finestra), completion service
  (ideal-end gonfiato 40-60gg), parse decimale, dedup ask, /full-test ora
  include import-calendario. TASK-384: persistere gli stage-day come fact ->
  confinamento VRP (da coordinare con L2).
- WORKFLOW RUN 12: fresh profile -> import-calendario (866 MODA + ~1800
  stage + 280 off-grid attesi) -> solve. 653/653 exit 0.

### 2026-07-20 (sera) — TASK-381 L1-bis LANDED (38c33c97, pushato)
- DoD completa sul diff: /simplify (4 Opus, 7 fix applicati) + /code-review high
  (8 finder Sonnet + verifiche Opus; 3 fix di correttezza: idempotenza stage-pin
  same-course, contatore closure-day 24h, matchesFor scoped) + 634/634 exit 0,
  typecheck/build 0. AC#1 e DoD spuntati nel backlog; task resta In Progress
  (AC#2 L2 template CH, AC#3 L3 forma, AC#4 run 12 con KPI).
- Attese live prossimo import-calendario: 866 MODA + 571 stage pin (276 G08 +
  295 G13) + stageSkippedNoTimeslot=24 (chiusure pasquali, Q21.3).
- Q21 aggiornata a 3 punti (stage G08 23/26-apr vs golden; quota lab G04
  245vs222; stage su chiusure 24h). G13 23-apr NON in disputa (coincide).
- Deferred in TASK-381 notes: fallback forcePlaceLeftovers non filtrato (bassa
  severita, da gestire nel CH quota-aware di L2).

### 2026-07-20 — REGOLA PERMANENTE (pre-compattazione) + stato di rientro
- REGOLA di maintainer: "se lo hanno fatto nel golden, possiamo farlo anche noi" -
  la pratica golden legittima il solver anche contro i requisiti dichiarati;
  incongruenze segnalate a posteriori (Q-list), MAI vincoli piu rigidi della
  pratica. Salvata anche in memoria persistente (golden-practice-legitimizes).
- STATO: branch pushato (d166d9e3). App dev: profilo con 866 pin MODA
  verificati visivamente (screenshot inviati). TASK-381 = priorita: L1-bis
  formalizzato nelle note (DailyActivityQuota fact + VRP + vincoli gemelli +
  CH quota-aware + stage full-day pins; riconciliare G04 232vs245 prima).
  Poi L2 timbro settimanale, L3 forma (S4/M21 vs blocchi golden). Aperti:
  TASK-382 (anno da API), TASK-383 (incidente pin spariti - niente browser
  automation su profili che contano), TASK-367 2 e2e residui, Q19-Q21 per
  referente. KPI somiglianza in experiment-log (run 10: ricorrenza 14,3%,
  blocchi 209x2h vs golden 41-72% / 1.299 blocchi).
