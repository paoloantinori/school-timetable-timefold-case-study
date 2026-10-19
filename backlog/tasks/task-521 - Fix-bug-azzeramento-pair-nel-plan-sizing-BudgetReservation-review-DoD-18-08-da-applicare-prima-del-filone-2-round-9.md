---
id: TASK-521
title: >-
  Fix bug azzeramento pair nel plan sizing BudgetReservation (review DoD 18/08,
  da applicare prima del filone 2 round 9)
status: Done
assignee: []
created_date: '2026-08-18 09:10'
labels:
  - claude-planned
  - solver
  - bug
  - pre-assign
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla DoD review anticipata 18/08 (due revisori indipendenti sul diff 1bbe4d9a..cc62f69a, parte del percorso di ratifica della chain): BudgetReservation.applyPlanSizing puo' AZZERARE un pair (violazione della regola round 6 che il commento promette). Trigger: (a) fallback spread non cappato a cap-1 (col golden illeggibile 110h/5=22h > cap 6 -> budget 0 -> tutte le sessioni droppate); (b) granularita': sessioni tutte oltre il budget residuo. Il test esistente copre solo il caso comodo. Verifiche di design POSITIVE dalla stessa review: gate prima del pinning, teacher-wide, ritmo golden con provenienza, flag-off byte-identical. Fix instradato a zai via messaggio accodato il 18/08: questo task ne traccia la chiusura.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Fix applicato nel flusso zai PRIMA del filone 2 (instradato 18/08 via messaggio accodato): fallback spread capped a cap-1 come il path golden, guardia kept.size()==1 nel while del trim, test con budget 0 e sessioni oversized
- [ ] #2 Log del fail-non-silente sui pair saltati (labDays<=0, cap nulli)
- [ ] #3 DEFERITO documentato nel task 518: asimmetria plan-sizing (per-pair) vs greedy (teacher-wide max) e mappa morta groupNamesById, da valutare nel round 10 esteso
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
