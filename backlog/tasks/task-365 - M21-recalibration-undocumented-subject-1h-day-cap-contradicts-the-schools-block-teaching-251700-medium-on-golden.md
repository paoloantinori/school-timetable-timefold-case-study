---
id: TASK-365
title: >-
  M21 recalibration: undocumented subject 1h/day cap contradicts the school's
  block teaching (-251,700 medium on golden)
status: Done
assignee: []
created_date: '2026-07-17 00:36'
updated_date: '2026-07-18 00:42'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies:
  - TASK-347
priority: high
ordinal: 189000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: TASK-347's golden audit (claudedocs/spalmone-2026-27/golden-audit.md) found M21 — a constraint absent from CLAUDE.md's canonical table — capping subjects at 1h/day. It is the single largest score component on the human solution: 861 matches, -251,700 medium. The school demonstrably teaches multi-hour blocks (block-size histogram in analysis.md section 4.5: 680 two-hour, 319 three-hour, 155 five-hour blocks). A medium constraint this heavy that the reference solution violates 861 times is mis-calibrated by construction.

WHAT: 1) Locate M21 in the constraint provider, reconstruct its intent from git history (it may have been meant as 'discourage MORE than N consecutive' or a distribution nudge). 2) Decide with the golden numbers: demote weight, parametrize per-subject (lab subjects legitimately block 4-6h), or replace with the existing S4/H15/H16 family semantics. 3) Document in CLAUDE.md's table (it must appear there whatever the outcome). 4) ConstraintVerifier tests for the recalibrated behavior; re-run the golden audit numbers for M21 and record the delta.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 M21's intent reconstructed and documented in CLAUDE.md's canonical table
- [x] #2 Recalibration decided with golden numbers; tests updated; golden M21 score delta recorded
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ANSWER 2026-07-17 (Q18, referente): 'i LABORATORI, Informatica e Disegno possono avere due ore vicine, gli altri POSSIBILMENTE no' — whitelist finale = ALL lab courses (isLab) + Informatica + Disegno (+ Competenze Digitali dal docx); tier = SOFT/MEDIUM ('possibilmente'). Recalibrate M21 with this exact whitelist.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
M21 intent reconstructed (accidental broadening in TASK-243, git-archaeologically documented) and recalibrated per the school's Q18 whitelist: labs + Informatica(lab-tagged) + Competenze Digitali + Disegno/Disegno Edile + pseudo exempt; weight ofMedium(100) -> ONE_MEDIUM. Golden: 867/-252,300 -> 452/-621 (~400x), no longer the dominant component. Residual honestly composed (Scienze 85, Matematica 63, ...); Ra lab aperto + RA micro-courses flagged as seed isLab candidates pending school confirmation — NOT silently whitelisted. Latent NPE on null subject found by in-agent review, fixed + regression-tested. Guard pin calibrated with mechanism comment. Full DoD; 547/547.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
