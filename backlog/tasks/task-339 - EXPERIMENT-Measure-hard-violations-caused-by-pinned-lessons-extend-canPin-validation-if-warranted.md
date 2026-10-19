---
id: TASK-339
title: >-
  EXPERIMENT: Measure hard violations caused by pinned lessons; extend canPin()
  validation if warranted
status: Done
assignee: []
created_date: '2026-07-12 17:49'
updated_date: '2026-08-04 01:11'
labels:
  - claude-planned
  - experiment
milestone: m-6
dependencies:
  - TASK-319
references:
  - claudedocs/research/quick-preassignment-fix-all-hard-2026-02-19.md
  - src/main/java/com/school/planner/service/HybridPhase1aService.java
priority: medium
ordinal: 163000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: In February the pre-assignment validator only checked H1/H2/H3, and pinned entities caused -1668 hard violations of which H15 alone was -1473 (88 percent) - violations the solver can NEVER fix because pinned lessons are immutable. A full canPin() covering 7 constraint types was fully specced in claudedocs/research/quick-preassignment-fix-all-hard-2026-02-19.md with a PROJECTED 88 percent reduction, but the projection was never measured, and the pipeline has since been rewritten (HybridPhase1aService). Current pinned-lesson violation share is unknown. See roadmap doc section 2, item 3.

WHAT:
1. MEASURE FIRST: reset + pre-assign (no solve) via the wrapper scripts, then run api.sh analysis and the per-lesson violations endpoint. Attribute each hard violation to whether it involves at least one pinned lesson. Deliverable numbers: total hard violations post-pre-assign, share involving pinned lessons, breakdown by constraint ID.
2. DECISION GATE: if pinned-lesson-attributable violations are under 5 percent of total hard, close the task with the measurement - the February problem no longer exists.
3. Otherwise: extend the validation in the current pipeline (PreAssignmentValidator / HybridPhase1aService slot-testing path) for the top offending constraint IDs from step 1, following the approach specced in the February doc (adapted to current code - the doc predates the rewrite).
4. Re-measure post-fix with the identical procedure; append before/after to experiment-log.md.
5. Watch the metrics convention: report LAB pin rate and total pin rate separately (claudedocs/insights/preassign-pin-rate-metrics.md) - a validation that pins fewer lessons is acceptable if solver-reachable violations drop.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Post-pre-assign hard violations measured and attributed to pinned vs unpinned lessons, by constraint ID
- [x] #2 Decision gate applied (close if under 5 percent, extend validation otherwise)
- [ ] #3 If extended: before/after measured with identical procedure, lab pin rate reported separately, experiment-log.md updated
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Giro pulizia 2026-07-25: TENUTA (claim di supersessione SMENTITO dalla verifica: la misurazione generale hard-da-pin non fu mai eseguita e canPin() esiste ancora, PreAssignmentValidator.java:451). Nota di scope: il caso G08 sheet-vs-golden e' segnale INTENZIONALE e non va 'fixato'.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Misurato (script read-only scripts/analyze/pinned-hard-impact.py, DoD'd): su 5 run (4 bad-seed/H17-residual + 1 controllo 0hard, famille task478/480, source=input GRASP), le lezioni PINNATE causano ZERO violazioni hard. analysis.json conferma che sui 5 run le uniche hard che fireano sono H17 (gap) + H29 (room-null); H1/H2/H3/H7/H11 = 0 hard. Le pinnate causano 0 delle H17/H29: H29 esenta le pinnate per costruzione (!pinned filter) e i residui H29 sono UNPINNED (G06 T26 / G04 T15, room-phase failure); H17 scored gap hanno 0 pinnate al bordo (gap tra unpinned, LS non compattato); H1/H3 = 0 conflitti. Ricomputazione H17/H29 validata == score reale su tutti i 5 run; verificatore Opus CONFIRMED con ricalcolo indipendente. DECISION GATE AC#2: pinned-attributable = 0% < 5% -> TASK CHIUSO con la misura, NESSUNA estensione di canPin() warranted. Il problema febbraio (-1668 hard, H15 -1473 da pinnate) non esiste piu' (canPin valida; H15->M15). Caveat: promotedPin=0 in tutti gli export (pinnate tutte authoritative; il rischio ex-ante "promoted escape canPin" non testabile qui - H1/H2/H3 con promoted pin resta caso aperto). H2/H7/H11 non hanno filtro !pinned (le pinnate potrebbero parteciparvi su run ipotetici) ma fireano 0 sui run misurati. AC#3 (estendi validazione) NON triggerata (gate <5% non richiede estensione). DoD: /simplify + /code-review high + script EXIT 0 (test.sh N/A Python). NESSUNA adozione, NESSUN cambio a canPin(). Doc: claudedocs/spalmone-2026-27/task339-results/VERDICT-339-2026-08-04.md.
<!-- SECTION:FINAL_SUMMARY:END -->
