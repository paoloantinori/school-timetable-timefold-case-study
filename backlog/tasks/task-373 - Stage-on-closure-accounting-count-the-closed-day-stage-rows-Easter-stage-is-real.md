---
id: TASK-373
title: >-
  Stage-on-closure accounting: count the closed-day stage rows (Easter stage is
  real)
status: Done
assignee: []
created_date: '2026-07-17 09:50'
updated_date: '2026-07-17 22:21'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-8
dependencies: []
priority: medium
ordinal: 197000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: referente (17/07, Q8): G08 stage = 300 in the grid, and mechanically verified: 276 open-day rows + 24 rows on CLOSURE days (Easter break 2027-03-25/26/30) that our extractor/import discard as closed. The docx rule 'lo stage può essere fatto anche nei giorni di chiusura' is literally exercised by the school. Our accounting undercounts stage hours for every group with closure-overlapping stage.

WHAT:
1. Measure the full extent: count closed-day stage rows per group in lessons.csv (G08 has 24; check all groups).
2. Accounting fix WITHOUT touching the Timeslot model (no slots exist on closures, and pinning lessons there is out of scope): the golden import classifies closed-day STAGE rows into a dedicated report bucket (stageOnClosure) instead of generic skippedClosed, and the synthetic Stage course's totalHours (reconciliation 'assigned') gains those hours so assigned==scheduled+stageOnClosure reconciles — decide the cleanest accounting (recommend: stage course totalHours = grid total INCLUDING closure rows; scheduled shows the placeable subset; reconciliation marks the closure remainder as an expected pseudo delta with its own label).
3. Update design doc section A inline + golden-audit note + reconciliation expectations; dry-run test categories.
4. Do NOT generate closure Timeslots — document why (solver never places there; stage-on-closure is a data/accounting fact).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 stageOnClosure measured for all groups and reported distinctly by the import
- [x] #2 Reconciliation for stage groups closes to zero unexplained delta; tests updated mechanically
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHECKPOINT 2026-07-17 (riavvio PC): implementazione + simplify completati e committati (vedi commit 'feat(TASK-373)'), verifica orchestratore 515/515 exit 0 eseguita. MANCA la code-review high sul diff del commit prima di chiudere: alla ripresa, review del commit TASK-373, fix, poi --check-dod 2 3, --check-ac 1 2 e Done.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Stage-on-closure accounting complete: 311 closure + 16 beyond-horizon hours classified and counted (per-group table in the design doc; G12 183/295 on closures). Course.expectedUnscheduledHours carries structurally-unschedulable hours; reconciliation actionable-delta logic keeps expected stage shortfalls neutral; G04 Totale residual closes exactly as a side effect. Derivation re-runnable (derive-golden-stage-on-closure.py, horizon-aware). Full DoD: simplify + code-review high (6 findings fixed) + orchestrator exit-code verification (520/520).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
