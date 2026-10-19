---
id: TASK-400
title: >-
  Tiered human-faithful construction — labs → recurring 2h blocks (skeleton) →
  single-hour manipulation (fill layer)
status: To Do
assignee: []
created_date: '2026-07-23 11:03'
updated_date: '2026-07-27 04:48'
labels:
  - claude-planned
  - search
  - construction
  - spalmone-2027
dependencies: []
priority: medium
ordinal: 224000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User's refined model of the human planner (2026-07-23): the human builds the schedule in TIERS — (1) labs (pinned first), (2) 2-hour blocks placed as RECURRING WEEKLY PATTERNS stamped across the valid dates (the stable skeleton), then (3) focus on MOVING ONLY the remaining single-hour slots (the flexible fill layer). This is richer than TASK-396's "greedy place all + box": it explicitly separates the stable recurring-block skeleton from the single-hour manipulation layer.

What already exists: pre-assignment pins labs; WeeklyTemplateBuilder (TASK-381 L2) builds the modal weekly template + stamps it (construction-end recurrence 75-81%, near/above golden); round-robin/LS handle singles. So the tiers are PARTLY implemented — the gap is making the single-hour tier an explicit, targeted manipulation layer rather than generic LS.

Connection to the box: the TASK-398 residual (5-6 un-taught) IS single hours; the tiered model predicts the human clears them by rearranging singles around the fixed block skeleton, not by generic moves. This task generalizes TASK-396's direction with the explicit tier structure; evaluate alongside the VRP discriminator (does the single-hour tier need targeted moves, or is the block skeleton too rigid?).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERATO NELL'ESITO (2026-07-27): la costruzione tiered proposta (lab -> skeleton 2h ricorrente -> fill) e' realizzata in sostanza dal template settimanale L2/TASK-381 (skeleton = celle template con blocchi 2h preferiti, stamp = fill, box-the-residual = manipolazione finale) piu' la protezione staged 419. Archiviato insieme a TASK-396.
<!-- SECTION:NOTES:END -->
