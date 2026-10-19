---
id: TASK-386
title: >-
  Honest metaheuristic benchmark — post-TASK-377 provider, current 5-phase
  pipeline
status: Done
assignee: []
created_date: '2026-07-21 11:19'
updated_date: '2026-07-22 00:00'
labels:
  - claude-planned
dependencies: []
ordinal: 210000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Per la direttiva primaria modello-vs-ricerca (CLAUDE.md 2026-07-21): run 15 chiude a -426 hard (3 H15 + 6 H17) con CH -57k e fase 2 che consuma l'intera ora ANCORA migliorando - residuo di ricerca, non di modello. Tutti i benchmark storici (12 config in src/main/resources/solverBenchmarkConfig*.xml) sono pre-TASK-377 (provider cieco) e pre-TASK-378 (pipeline 5 fasi con DayClusteredConstructionPhase custom): non fanno testo. SERVE: (0) RUN 16 di controllo - fase 2 secondsSpentLimit 3600->5400 (ipotesi 'e' solo tempo'); (1) audit del meccanismo benchmarker (come carica il problema? statics? funziona con la fase custom?); (2) nuova suite con bracci: controllo (config attuale), late-acceptance vs entity-tabu in fase 2, acceptedCountLimit 500/1000/2000, tabu 5/7/11, pesi mosse pillar, budget fase 2; (3) esecuzione (orchestratore, DB singolo attore) + report; (4) adozione del vincitore con ripple solver-config-sync. Obiettivo: hard=0 in budget sostenibile, modello INVARIATO.
<!-- SECTION:DESCRIPTION:END -->

## Audit Notes (2026-07-21, item 1 of the task's SERVE list)

**Verdict: the offline Timefold benchmarker mechanism is BROKEN for this pipeline** - not usable as an honest stand-in for runs 12-16. Full mechanical audit with file:line evidence: `claudedocs/research/benchmark-mechanism-audit-2026-07-21.md`.

- `local/data/schedule-production.json` (the input every `solverBenchmarkConfig*.xml` points at) does not exist - must be hand-produced via a live app export.
- Even with a fresh export, `SchoolScheduleSolutionFileIO.read()` populates only 7 of the 10 statics `ScheduleService.refreshSolverStatics()` sets in production. Missing: `GroupCalendarRule`, `DailyActivityQuota`-derived quota maps, and the MRV `lessonAvailableTimeslotCounts`. `GroupCalendarRule` cannot be fixed via `SolutionFileIO` at all - it isn't a field on `SchoolSchedule`, so it never round-trips through the exported JSON.
- None of this crashes (every gap has a graceful "fully open"/"equally hard" default, confirmed by reading the fallback code) - it silently biases any offline run toward an easier problem than production, so absolute scores wouldn't be comparable to runs 12-16 even though the bias hits every arm identically.
- The 12 existing `solverBenchmarkConfig*.xml` files are separately confirmed pre-TASK-377/378: generic `FIRST_FIT` CH + one combined timeslot+room `<localSearch>` per arm, zero `customPhase`/`DayClusteredConstructionPhase`/`PhaseTransitionMarker` references across all 12 files.
- Timefold 2.3 community edition itself has no relevant restriction (verified via WebSearch against docs.timefold.ai this session): custom phases, `parallelBenchmarkCount`, and warm-up are all supported in the open-source benchmarker module and present in `benchmark.xsd`. The break is entirely this project's `SolutionFileIO`/export-shape gap, not a platform limitation.

**Chosen path (per the task's own fallback clause):** skip fixing the offline mechanism (would require an export/import cycle, extending `SolutionFileIO`, and either a `SchoolSchedule` domain change for `GroupCalendarRule` or permanently accepting "calendar always open" offline - a bigger, riskier change than reusing the already-validated live path). Use the **sequential live A/B protocol** instead: `scripts/benchmark/run-honest-ab.sh` generates+validates one arm's `solverConfig.xml` variant at a time (via `scripts/benchmark/generate-benchmark-arm.py`, which patches ONLY phase 2's acceptor/forager/pillar-selectors from the current live config) and prints the manual apply/rebuild/`run-experiment.sh`/restore procedure for the orchestrator - single DB actor, one arm per invocation, ~90 min/arm. `src/main/resources/solverBenchmarkConfig-2026-honest.xml` was still authored per spec (6 arms A-F, XSD-valid against `benchmark.xsd`) as a documented design artifact, headed with a `NOT FUNCTIONAL AS-IS` banner and the exact prerequisites to ever make it runnable - it is not the recommended execution path.

Recommended arm order: A (control, 3600s budget) → E (tabu 11) → C/D (acceptedCountLimit 500/2000, read together) → F (pillar moves removed) → B (late acceptance 400, run last as the most different acceptor family).

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RUN 16 (controllo budget, 106 min): fase 2 3600->5400 su config attuale (tabu 7/acl 1000). Hard finale -3902 vs -426 di run 15 (stessa config, 60 min): PIÙ TEMPO = PEGGIO. Budget refutato come leva, revertito a 3600. Scoperta chiave: -426 vs -3902 dalla stessa config = convergenza instabile -> il benchmark deve cercare STABILITÀ, non solo il minimo assoluto (aggiungere ai criteri di verdetto: preferire arm con convergenza affidabile). Bracci confermati a 3600. Pronto per esecuzione laptop.

## ESECUZIONE + VERDETTO (2026-07-22, laptop, 8 run) — CHIUSO

Eseguito sul laptop (6 bracci A-F + re-run stabilità di C e D). Risultati e VERDICT.md del laptop in `claudedocs/spalmone-2026-27/benchmark-386-results/`; analisi consolidata verificata sui dati grezzi in `experiment-log.md` (sezione "Benchmark TASK-386"). Fase 1 CH byte-identica su tutti gli 8 run (`-57457hard`) → A/B pulito.

**Verdetto: NESSUN vincitore da adottare.** (1) Late acceptance (B) squalificato — unico braccio a far crollare un KPI (full-day 66,9% vs 77,8-78,6% tabu). (2) Nessuna config di fase 2 batte il control in modo riproducibile: i leader dei run singoli (C -123, D -302) collassano nei re-run (C-run2 -1002, D-run2 -1541), replicando la varianza di run-16 su un'altra macchina. (3) Esito bimodale (~-123..-303 vs ~-1000..-1541); la forbice ~1000-hard nasce sia in fase 2 (variabile a config fissa) sia in fase 3 (rescue binario del ruin-recreate). **Si tiene il control (braccio A). La domanda del task ("migliora con una metaeuristica di fase 2 diversa?") ha risposta: NO.**

Item 4 della SERVE list ("adozione del vincitore + ripple solver-config-sync") = N/A: nessun diff di codice/config prodotto → DoD #1/#2 (simplify/code-review sul diff) non applicabili, #3 non necessario. Il residuo -123..-303 e lo 0-hard di run 14 sono INSTABILITÀ DI CONVERGENZA sul nodo strutturale G05 slack-zero + H15, NON modello né metaeuristica di fase 2. Prossima leva (search-only, da confermare col richiedente prima di aprire un task): multi-start/restart, oppure hardening ruin-recreate fase 3, oppure fissare per costruzione il matching G05 nel template di fase 1.
<!-- SECTION:NOTES:END -->

DEPENDENCY (2026-07-21): TASK-387 (heap sizing) PRECEDE logicamente questo task. Il thread dump del run 16 ha rivelato che i solve girano su heap default ~260MB con pause young-GC da ~183ms - potenzialmente GC-strozzati. Se un heap adeguato (TASK-387) porta a 0 hard a parita' di config, questo benchmark potrebbe non servire, o va comunque rifatto con heap corretto (un benchmark GC-strozzato misura il GC, non la metaeuristica). Fare TASK-387 prima; il laptop-RUNBOOK gia' avverte di usare un heap vero.
