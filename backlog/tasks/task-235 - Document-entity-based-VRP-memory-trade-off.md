---
id: TASK-235
title: Document entity-based VRP memory trade-off
status: To Do
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-08-04 01:48'
labels:
  - timefold
  - documentation
  - architecture
dependencies: []
references:
  - MEMORY.md
  - src/main/java/com/school/planner/domain/Lesson.java
documentation:
  - >-
    https://timefold.ai/docs/timefold-solver/latest/configuration#valueRangeProvider
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold best practice note: "Entity-level @ValueRangeProvider annotations consume more memory than solution-level providers because each entity gets its own filtered value range copy."

Current Architecture: Lesson uses entity-level VRPs (e.g., @ValueRangeProvider on timeslotListSorted) that filter values based on entity properties. This is an intentional design choice to enable dynamic filtering per entity.

Impact: This architectural decision should be documented so future developers understand the trade-off:
- PRO: Dynamic filtering per entity (can't be done at solution level)
- CON: Higher memory usage (each entity has its own filtered list)
- ALTERNATIVE: Solution-level VRP with custom MoveSelector filtering (less flexible)

This documentation helps evaluate if the memory cost is justified for the filtering benefit.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Memory.md updated with entity-based VRP trade-off documentation
- [ ] #2 Lesson.java annotated with design decision reference
- [ ] #3 Alternative approaches documented (solution-level VRP + MoveSelector filtering)
- [ ] #4 Guidance on when to use entity-level vs solution-level VRPs
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Doc storico scritto: claudedocs/entity-vrp-memory-tradeoff-2026-08-04.md (read-only, no code). Distingue due assi di trade-off spesso confusi: (A) entity-level vs solution-level VRP (costo memoria/ottimizzazioni/cloning vs filtering per-entita'; VIVO su Lesson oggi), (B) singola vs multiple entita' planning (espressivita' LabBlock/LabFollower vs 9 set split + piu' fasi + validazione runtime H9/M4/M17/M18). Asse B risolto da TASK-228/267 (denormalize a Lesson pinnate: +50% entita' a 10.550 ma medium -387M->-219M 43% meglio, 9->3 set conflitto). DoD: /simplify (3 agent, applicati via rewrite) + verifica skeptic. CATCH chiave dello skeptic: l'attribuzione 'TASK-456 ha aggiunto il VRP room' era SBAGLIATA - il VRP fu aggiunto da TASK-153 (c79c6752, 2026-03-04, 5 giorni dopo il research doc 2026-02-27 che lo sconsigliava); TASK-456 ha solo allargato il filtro (lab-fallback). Corretto. Altro catch: proiezione pre-impl del design doc (~7.600 +5,5%) divergeva dalla finalSummary actual (10.550 +50%); il doc cita gli actual. AC#2 (annotare Lesson.java) lasciata gated maintainer. Commit 5eeddbdc.
<!-- SECTION:FINAL_SUMMARY:END -->
