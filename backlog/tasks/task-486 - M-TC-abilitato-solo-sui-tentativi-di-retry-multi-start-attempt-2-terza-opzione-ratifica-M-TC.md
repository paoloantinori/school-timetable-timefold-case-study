---
id: TASK-486
title: >-
  M-TC abilitato solo sui tentativi di retry multi-start (attempt >=2) - terza
  opzione ratifica M-TC
status: Done
assignee: []
created_date: '2026-08-03 15:52'
updated_date: '2026-08-04 04:38'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Terza opzione per la ratifica M-TC (maintainer, 2026-08-03), meglio di DEFAULT-ON e DEFAULT-OFF. M-TC OFF al tentativo 1 del multi-start (good basin: ~20min, nessun overhead 2x, byte-identico a oggi) e ON ai tentativi >=2 (bad basin: cura deterministica del room-null room-tier che il solo multi-start aggira solo probabilisticamente).

VERIFICA MECCANICA (gia' fatta): SchoolScheduleConstraintProvider.labTagCapacity riga ~1903 legge isTagCapacityEnabled() LIVE alla costruzione dello stream (non cached); ogni tentativo chiama runStagedCycle() che buildSolver() per fase e rilegge il flag; ESISTE IL PRECEDENT ESATTO applyGraspSeedForAttempt (riga ~1523) che setta grasp.enabled per-attempt. applyMtcForAttempt(attempt) segue lo stesso pattern riga per riga.

CONTESTO: respinta la ratifica M-TC DEFAULT-ON (wall-clock 2x su ogni run inaccettabile, good-seed A/B 1230s->2435s). Branch origin/ratification/m-tc-default-on resta disponibile ma non mergiato. Multi-start e' gia' DEFAULT ON (TASK-483), quindi la feature per-retry ha senso.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Prototipo applyMtcForAttempt(attempt) mirror di applyGraspSeedForAttempt (attempt 1 -> tag-capacity.enabled=false, >=2 -> true), chiamato in runOneMultiStartAttempt, save/restore property nel finally come GRASP
- [ ] #2 #2 Unit test puro mtcEnabledForAttempt(1)=false, mtcEnabledForAttempt(>=2)=true (come graspEnabledForAttempt)
- [ ] #3 #3 A/B good basin: attempt 1 M-TC off -> wall ~20min (non ~40), hard 0, confronto con ab-mtc-goodseed-off (1230s/0hard)
- [ ] #4 #4 A/B bad basin grasp-seed-6: attempt >=2 M-TC on cura room-null a 0-hard? (domanda cruciale: la cura deterministica arriva dove serve)
- [ ] #5 #5 DoD: /simplify + /code-review high + test.sh exit 0; VERDICT-mtc-per-retry con predizione ex-ante e esito
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RATIFICATO DEFAULT ON (maintainer, 2026-08-04). mtcPerRetryEnabled() ora opt-out (null/absent->true, solo -D...=false disattiva), stessa forma di multiStartEnabled/isExactFinisherEnabled. M-TC si attiva solo nei tentativi di retry multi-start (>=2), spento al tentativo 1: good basin nessun overhead (A/B 1558s/0hard vs control 1673s/0hard, no 2x), bad basin cura deterministica room-tier al retry (grasp-seed-6 a 0-hard, commit a35633f3). Branch ratification/m-tc-default-on (DEFAULT-ON FLAT respinto, wall 2x) CANCELLATO. M-TC flat (TASK-455 planner.tag-capacity.enabled) resta opt-in prototype per esperimenti isolati.
<!-- SECTION:FINAL_SUMMARY:END -->
