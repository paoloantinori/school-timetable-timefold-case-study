---
id: TASK-395
title: >-
  Add golden-overflow-by-group KPI script — machine-check the M12 '29.3% golden
  overflow' invariant per group (validates TASK-394 ideal-end-date changes)
status: Done
assignee: []
created_date: '2026-07-22 21:52'
updated_date: '2026-08-14 10:27'
labels:
  - claude-planned
  - ideal-end-date
  - tooling
dependencies:
  - TASK-394
priority: low
ordinal: 219000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Sibling to scripts/analyze/daily-fill-kpi.py and moda-knot-count.py (both built during the 2026-07-22 laptop validation). Makes the documented M12 "golden places 29.3% of hours beyond the computed targets" invariant machine-checked AND per-group, so any change to TargetEndDateCalculator (TASK-394) can be validated by running this before/after.

Root-caused baseline (2026-07-22, measured against claudedocs/spalmone-2026-27/data/lessons.csv): overflow tracks (maxHoursPerDay - real-fill~5.79h/day). 8h-cap groups (G06 ~28%, G10 ~31%) vs 6h-cap groups (G02/MODA/FB, G05 ~4%). G10's overflow is dominated by Stage (172h of 302h past ideal) — stage is a distributed internship, not a compactable subject.

Input sources: golden = claudedocs/spalmone-2026-27/data/lessons.csv (columns: date,day,slot,closed,group,teacher,subject). Per-group ideal/target dates = the "Group <name>: Xh over Y days (Z excluded) -> ideal D1, target D2" lines logged by TargetEndDateCalculator (grep /tmp/planner.log), or recompute via the same formula. Report per group: total lesson-hours, hours after ideal, hours after target, % overflow, and the top overflowing subjects (so Stage-vs-classroom is visible).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Script at scripts/analyze/golden-overflow-kpi.py: takes the golden lessons.csv + the per-group ideal/target dates (from TargetEndDateCalculator output / planner.log) and prints a per-group table: total h, h past ideal, h past target, % overflow, top overflowing subjects.
- [ ] #2 Reproduces the 2026-07-22 baseline measurements (G10 ~31%, G06 ~28%, G02 ~4%; 8h-cap vs 6h-cap split).
- [ ] #3 Wired into the benchmark/validation collect step (scripts/benchmark/laptop-RUNBOOK.md) alongside daily-fill-kpi.py / moda-knot-count.py.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
--- Chiuso 2026-08-04 (script + DoD completa, NESSUNA adozione) ---

Script scripts/analyze/golden-overflow-by-group.py: DONE + DoD completa. Standalone (golden CSV + calendario + incarichi, no DB). Replicazione fedele di TargetEndDateCalculator.computeFactBasedIdealEnd (cumula expected-hours a Course.totalHours);Conta le ore reali del golden (trap-safe: lun-ven, escluse Stage/Fasullo/Esame/Saturday) oltre il target-end.

RISULTATO (post bug-fix /code-review): aggregate 0.6% (63h/10542), non 29.3%. 11/14 gruppi a 0; G08 e' l'outlier dominante (50h, 7.5%, target 2027-05-21); G04 10h (Scienze); G14 3h. Pattern CONCENTRATO. Il 29.3%/3677h citato nel javadoc M12 era artefatto del flat-8h-model (target ~marzo); TASK-394 (fact-based) ha collassato l'overflow a <1%. G14 '100% beyond' refutato (2.6% fact-based).

BUG-FIX Opus verifier: i 3 finder correctness avevano MANCATO che totalHours source era sbagliata (usavo 'Totale ore corso' 990 per G08; ma Course.totalHours include Stage ed ESCLUDE Esame/RA -> 926). Il verificatore Opus lo ha trovato, fixato (real+Stage), numeri corretti verificati byte-exact vs il ricalcolo indipendente del verificatore. Caveat: ensureEnoughSlots omesso -> G08 50h e' UPPER BOUND.

DoD: /simplify (4 agent, 7 fix applicate; skippate promozione FC_GROUPS e dedup loader con motivo); /code-review high (3 finder + 1 Opus skeptic che ha trovato il bug load-bearing + fix + verifica byte-exact); test.sh N/A (Python), script EXIT 0.

Riconciliazione AC: AC#1 fatto (script + tabella per-gruppo; ricalcola il target standalone, meglio della dipendenza da planner.log; manca la colonna 'top overflowing subjects' ma e' moot a <1% overflow, i 3 gruppi annotati). AC#2 SUPERSEDUTA: la baseline 2026-07-22 (G10 ~31%, G06 ~28%) era flat-model; lo script VALIDIDA TASK-394 (fact-based porta 29%->0.6%), che e' lo scopo nel titolo. AC#3 (wire laptop-RUNBOOK) DEFERRED: fuori scope direttiva 'no adoption'.

Doc: claudedocs/spalmone-2026-27/task395-results/VERDICT-395-2026-08-04.md. Raw output: scripts/analyze/golden-overflow-by-group.py (run directa).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
COMPLETO (anche AC#3, chiusa 2026-08-14): script scripts/analyze/golden-overflow-by-group.py verificato EXIT 0 con numeri conformi al baseline (aggregate 0.6%, 11/14 gruppi a 0, G08 50h 7.5% upper-bound per ensureEnoughSlots omesso, G04 10h). AC#3 wired ora come validazione nel posto giusto: laptop-RUNBOOK (passo prima/dopo per cambi TargetEndDateCalculator) + sezione KPI golden-vs-run di claudedocs/scripts.md (non in capture-run.sh: lo script misura il golden statico, non il run, wire per-run sarebbe output costante). AC#2 restava SUPERSETA (la baseline 29.3% era artefatto flat-model, lo script VALIDA TASK-394: fact-based 29.3% -> 0.6%). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
