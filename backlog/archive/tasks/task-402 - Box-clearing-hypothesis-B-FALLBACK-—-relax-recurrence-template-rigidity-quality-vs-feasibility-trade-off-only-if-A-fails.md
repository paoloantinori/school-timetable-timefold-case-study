---
id: TASK-402
title: >-
  Box-clearing hypothesis B (FALLBACK) — relax recurrence/template rigidity
  (quality-vs-feasibility trade-off); only if A fails
status: To Do
assignee: []
created_date: '2026-07-23 11:04'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - search
  - construction
  - spalmone-2027
dependencies:
  - TASK-399
priority: low
ordinal: 226000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
HYPOTHESIS B (FALLBACK) for the TASK-398 box: if the boxed lessons are genuinely VRP-deadlocked (the VRP discriminator says NO feasible slot), the cause is the template's modal rigidity OVER-regularizing — construction recurrence is 75-81% vs the golden's 41.5% strict / 72% modal, so the template forces recurring cells that misfit lessons can't occupy, boxing them. The golden is less regular → more placement flexibility → 0 hard but lower recurrence.

Nuance (measured): the over-regularity is a CONSTRUCTION phenomenon (template 75-81%); the FINAL solve's recurrence is already ~36-37% (BELOW golden strict 41.5%) because LS trades it away — yet LS can't un-box the 5-6. So B is about the template's construction-time modal stamp creating the box, not final recurrence being too high.

WHAT (only if A fails): relax the template's modal stamp (allow non-modal cells when the modal one boxes a lesson) or reduce S13/S12 recurrence pressure, and measure box + construction-recurrence (expect box→0, recurrence→golden level ~41-72%). This sacrifices regularity for feasibility — the quality-vs-hard trade-off.
PRIORITY: explicitly a FALLBACK per user — pursue ONLY if hypothesis A (single-hour-targeted repair) doesn't clear the box. Not an alternative to A. Gated by the VRP discriminator (pursue B only if boxed lessons have NO VRP-legal conflict-free slot).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): superata dai fatti (campaign-status 2026-07-24: l'ipotesi B si e' risolta con l'eviction TASK-414, non rilassando il template).
<!-- SECTION:NOTES:END -->
