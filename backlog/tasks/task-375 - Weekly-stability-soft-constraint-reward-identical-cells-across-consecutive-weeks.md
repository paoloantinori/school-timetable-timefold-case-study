---
id: TASK-375
title: >-
  Weekly-stability soft constraint (reward identical cells across consecutive
  weeks)
status: Done
assignee: []
created_date: '2026-07-17 21:59'
updated_date: '2026-07-19 10:50'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-11
dependencies: []
priority: medium
ordinal: 199000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: TASK-351's research (claudedocs/design/weekly-template-redesign-2026-07-17.md) concluded NO-GO on template-first (41.5% strict within-segment recurrence, 72% modal ceiling, ~16 distinct weekly grids per group) and recommends instead a cheap soft constraint on the current per-date pipeline: reward lessons whose (group, weekday, slotIndex, teacher, subject) cell matches the same cell in the PREVIOUS week — nudging the solver toward human-like weekly regularity without forcing a template.

WHAT:
1. New SOFT constraint 'Sxx - Weekly stability' (next free S-number, canonical format): for each unpinned lesson, reward if the previous week's same (group, weekday, slotIndex) cell holds the same (teacher, subject). Implementation sketch in the design doc (join lessons 7 days apart on group+slot; mind closures — a closed previous week should not penalize; the doc discusses the bi-directional double-count, follow its resolution).
2. Weight calibration: start low (the doc suggests a value); measure on a full solve that it does not fight M12/M20/S9; golden measurement note (the golden itself scores 41.5-72% — the constraint should reward, never punish).
3. ConstraintVerifier tests (match rewarded, mismatch neutral, closure-gap neutral, pinned excluded from reward to avoid inflating pinned-heavy solutions); CLAUDE.md table + docs per solver-config-sync if applicable.
Ref: claudedocs/design/weekly-template-redesign-2026-07-17.md (task breakdown item).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Constraint implemented with tests; golden measurement recorded; weight documented
- [ ] #2 A/B note on one full solve: stability metric improves without hard/medium regression
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AC2 EXECUTED 2026-07-19: A/B at equal budget (fresh profiles, 866 pins, ~2h each). ARM A (run 9, S13 on): 0 hard / -10,163,070 medium / -61,606 soft, weekly stability 14.7% (1,420/9,635 unpinned matching previous-week cell; cross-check 1,420 x weight 2 = +2,840 = exact S13 score). ARM B (S13 off): -2,001 hard / -10,470,580 medium / -80,551 soft, stability 11.4% (1,099). VERDICT: S13 KEPT — +3.3pp (+29% relative) stability, no cannibalization (A better on medium AND soft). Caveat: single run per arm, NON_REPRODUCIBLE (arm B's -2,001 hard = 2 unresolved conflicts is the noise floor). Exports archived in ~/data/planner-experiments/.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
