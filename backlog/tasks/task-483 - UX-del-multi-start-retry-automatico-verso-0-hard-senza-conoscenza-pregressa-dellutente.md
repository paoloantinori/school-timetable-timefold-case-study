---
id: TASK-483
title: >-
  UX del multi-start - retry automatico verso 0-hard senza conoscenza pregressa
  dell'utente
status: Done
assignee:
  - claude
created_date: '2026-08-03 07:49'
updated_date: '2026-08-03 11:50'
labels:
  - claude-planned
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Requisito di maintainer (03/08): il multi-start non deve restare una procedura documentata che l'utente deve conoscere (oggi e' scripts/benchmark multistart + flag -Dplanner.construction.grasp.*). Va INCAPSULATO nel prodotto, esplicitamente o implicitamente, in modo che nessuna conoscenza pregressa sia necessaria per ottenerne il beneficio.

Design proposto (implicito by default, esplicito solo come rifinitura avanzata):
1. BACKEND. `startStagedSolving()` diventa un ciclo di tentativi: esegue il ciclo staged; se l'esito NON e' 0-hard, riavvia automaticamente con una costruzione diversa (seed GRASP incrementale, meccanismo TASK-480 gia' in casa) fino a maxAttempts o a un budget di tempo; tiene il migliore su (hard asc, medium asc). Il primo tentativo resta il default deterministico (GRASP off), che e' il migliore su medium quando riesce: i tentativi randomizzati sono il fallback. NOTA dal recon TASK-480: i tentativi devono essere SEQUENZIALI in-process (gli static volatile di SchoolSchedule vietano il parallelismo in-JVM).
2. STATO OSSERVABILE. Estendere lo stato del solve con (attempt corrente, maxAttempts, esito del tentativo precedente) accanto a stagedStage, cosi' la UI puo' raccontarlo senza inventare.
3. UI. Nessuna opzione nuova obbligatoria: resta UN pulsante Risolvi (UnifiedSolverControls.handleSolve). Durante il ciclo il pannello dice in linguaggio naturale cosa sta succedendo ("Tentativo 2 di 3: la prima soluzione aveva 6 conflitti irrisolti, sto riprovando con una distribuzione iniziale diversa"). A fine ciclo: badge "Orario valido" oppure "Non e' stato possibile: N conflitti residui" con il drill-down esistente sulle violazioni.
4. ONESTA' SUL COSTO. Il ciclo puo' durare N volte il tempo di un solve: la UI mostra la stima ("fino a ~X minuti") prima o durante, senza sorprese.
5. SETTING AVANZATO (opzionale, non necessario all'uso): numero massimo di tentativi e/o budget di tempo, con default sensato; l'utente ordinario non lo tocca mai.

Vincoli: nessun cambio ai default del modello; il comportamento a singolo tentativo resta ottenibile (maxAttempts=1). DoD piena (il backend tocca il percorso di produzione del solve), piu' ui-typecheck/ui-build. Riferimenti: VERDICT-fase4-multistart-ab-2026-08-02.md (procedura e numeri), orchestration-layer-architecture.md §4.1, recon task480-recon-timefold-multistart.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Backend: ciclo di tentativi con pick-best interno a startStagedSolving, primo tentativo deterministico, sequenziale in-process, maxAttempts/budget configurabili con default
- [x] #2 Stato osservabile esteso (attempt/maxAttempts/esito precedente) e consumato dalla UI
- [x] #3 UI: un solo pulsante, narrazione in linguaggio naturale del retry, badge finale, stima di durata; nessuna opzione obbligatoria
- [x] #4 DoD piena + ui-typecheck + ui-build; comportamento single-attempt preservato
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 /simplify sul diff (4 agent reuse/simplification/efficiency/altitude; finding applicati: droppato Config.enabled dead-code -> gate singolo, null-check ridondante, method-ref, commento DRAFT-005)
- [x] #2 /code-review high (Opus): 1 Important (busy-guard gap durante construction dei tentativi 2+: stagedStage null -> isBusy() false + stopSolving no-op; FIX: runStagedCycle re-pubblica STAGE_CONSTRUCTION all'entry) + 1 Minor (restore saveSchedule non wrapped -> thread muore silente; FIX: try/catch + lastSolverError). Entrambi verificati per trace indipendente e corretti.
- [x] #3 test.sh EXIT 0 (979 pass, 0 fail) + ui-typecheck clean + ui-build clean
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Design brief (verificato sul codice, 2026-08-03)

### Dove si innesta (VERIFICATO)
`startStagedSolving()` (ScheduleService:875) spawn il thread daemon che chiama `runStagedCycle()` (:896, package-private). Il gate e' li': se multi-start OFF -> `runStagedCycle()` come oggi (byte-identical, zero regressione); se ON -> wrapper `runStagedCycleWithRetry()`.

### Stato pulito fra tentativi (VERIFICATO - non e' un problema)
`runStagedCycle()` e' gia' autosufficiente per il retry sequenziale: inizia con `unassignUnpinnedPlacements()` + `releasePromotedPins()` e finisce con `finishStagedCycle()` (closing release + assert di conservazione pin autoritativi). Chiamarlo N volte in sequenza e' sicuro: ogni tentativo riparte pulito e finisce pulito. I campi static volatile di SchoolSchedule (promotedPin etc.) non vietano il retry, vietano il PARALLELISMO: il retry e' sequenziale in-JVM, un tentativo alla volta. Nessun nuovo rischio.

### Propagazione seed GRASP per tentativo (VERIFICATO)
`WeeklyTemplateBuilder` legge il seed LIVE: `System.getProperty("planner.construction.grasp.{enabled,seed}")`, per-build via `newGraspRandom()`. Nessuna static mutable da toccare. Strategia: tentativo 1 = deterministico (grasp.enabled=false), tentativi k>=2 = grasp.enabled=true + seed=k. Il wrapper save/set/restore delle property originali attorno al loop (System.setProperty e' globale: quando retry e' ON ne prende possesso, quando e' OFF non la tocca = zero regressione).

### Pick-best senza sporcare lo stato (VERIFICATO - riusa path provato)
`saveSchedule(schedule)` ri-applica le assegnazioni di un SchoolSchedule in memoria al DB via `Lesson.findById(id)` + copia timeslot/room (NON tocca i pinned, NON dipende dalle entita' gestite). Quindi: tengo in memoria la soluzione migliore (il `solved` del room stage), e se a fine loop best.attempt != ultimo tentativo, la ri-salvo con `saveSchedule(best)` = ripristino, usando l'identico path di salvataggio che ogni ciclo gia' percorre. Score comparato con `HardMediumSoftScore.compareTo` (riuso `PortfolioPickBest`). Restore via `saveSchedule` NON via `saveFinalSolution` (per non toccare due volte `progressionService.finalizeCurrentPhase`).

### Core testabile (no solve reale)
`MultiStartRunner` in `solver/` (sibling di PortfolioPickBest): orchestrazione PURA che prende (Config, AttemptRunner IntFunction, stateListener, clock) e ritorna Result{best, attemptsRun}. Runner iniettabile -> i test simulano gli esiti (hard-6, hard-3, hard-0) senza Quarkus ne' solve. ScheduleService cabla il runner reale (set seed + runStagedCycle + leggi solved).

### Knob (LIVE, forma opt-out delle altre ratifiche; default OFF)
- `planner.multistart.enabled` (default false) - master.
- `planner.multistart.max-attempts` (default 3).
- `planner.multistart.budget-seconds` (default 0 = solo maxAttempts).
Banner in `refreshSolverStatics`: `multistart=%b maxAttempts=%d budgetSec=%d`.

### Stato osservabile (speculare a stagedStage, basso costo)
Campi transient `SchoolSchedule.stagedAttempt`/`stagedMaxAttempts` + getter/setter; settati in `getScheduleWithStatus()` (:2344); esposti nella status map di ScheduleResource (:87). Frontend: domain.ts + UnifiedSolverControls ("Tentativo X di Y" solo se stagedMaxAttempts>1). Badge finale e stima durata: differibili se la sessione stringe (dichiararlo).

### Stop / cancel
`stagedCancelRequested` e' gia' gestito dentro runStagedCycle (room stage skipped). Il wrapper tentativo lo rispetta: se cancel prima del tentativo, ritorna null -> MultiStartRunner ferma. Restore best comunque nel finally (mostra il miglior tentativo completato).

### Fuori scope (NOTA contesto)
Il retry non cura i bacini strutturali (seed-1 H17, seed-6 saturazione tag meccanica): li AGGIRA ripartendo. Cure vere: TASK-481/482 (H17), M-TC/TASK-455 (tag capacity). Nessuna adozione: source resta golden, retry default-OFF.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-03 10:44
---
DEFERITO (maintainer, decidi se vale la sessione): AC3 badge finale + stima durata non implementati. L'indicatore "Tentativo X di Y" nel banner copre la narrazione del retry (nucleo AC3). Il badge "Orario valido/N conflitti" richiede UI score-driven oltre il retry; la stima durata richiede il mean-solve lato backend (non affidabile lato frontend, meglio nessuna che una sbagliata). Entrambi dietro escape-hatch del mandato.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
TASK-483 implementato dietro flag DEFAULT-OFF (`planner.multistart.enabled`, forma opt-in uguale a GRASP). Il multi-start e' ora dentro il prodotto: l'utente preme un solo comando, se il ciclo staged finisce con hard residuo ritenta con costruzione randomizzata (GRASP seed progressivo), pick-best, stop a 0-hard. Core puro `MultiStartRunner` (sibling di PortfolioPickBest, testabile senza solve) + cablaggio ScheduleService + stato osservabile (stagedAttempt/stagedMaxAttempts) + UI "Tentativo X di Y". NESSUNA adozione: source resta golden, retry OFF di default (la ratifica e' di maintainer, segnalata con i numeri p(0-hard)=0.750 strict, N=3 -> P(>=1)>0.95, costo quasi nullo sui bacini buoni). Verifiche: 979 test pass (exit 0), /simplify + /code-review high (1 Important + 1 Minor trovati e corretti). Zero regressione quando OFF (path singolo ciclo byte-identical; gate esterno = singolo gate dopo simplify). DEFERITO (escape-hatch del mandato "fermati al backend+campo e dichiaralo"): badge finale "Orario valido/N conflitti" e stima durata "~X min" nel pannello UI; il nucleo AC3 (un solo pulsante, narrazione retry, nessuna opzione obbligatoria) e' coperto dall'indicatore "Tentativo X di Y". Note di contesto: il retry AGGIRA i bacini strutturali (H17 seed-1, saturazione tag meccanica seed-6) ripartendo, non li cura (cure vere: TASK-481/482, M-TC/TASK-455).

[2026-08-03] AC3 CHIUSA: badge finale esito (Orario valido / N conflitti residui, da parseHardScore) + stima durata nell'indicatore (fino a ~Z min dal budget configurato planner.multistart.budget-seconds, nessun tempo fittizio quando budget=0) implementati in UnifiedSolverControls + i18n en/it. Backend: esposto stagedBudgetSeconds nello stack status (SchoolSchedule + ScheduleService + ScheduleResource + rest-api.md). Verifica: test.sh exit 0 (979 pass) + ui-typecheck + ui-build clean.
<!-- SECTION:FINAL_SUMMARY:END -->
