---
id: TASK-223
title: 'Option: Remove swaps from Phase 4/4b for pure timeslot optimization'
status: To Do
assignee: []
created_date: '2026-03-10 06:10'
updated_date: '2026-07-27 04:48'
labels: []
dependencies:
  - TASK-280
priority: medium
ordinal: 240
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
If benchmarking shows significant room degradation during Phase 4/4b (Phase 5 takes >30s), implement this option to remove swap moves entirely.

**Changes required**:
- `solverConfig.xml` Phase 4: Remove both `swapMoveSelector` elements (Lesson and LabBlock)
- `solverConfig.xml` Phase 4b: Remove `swapMoveSelector` element

**Impact**:
- ✅ Pure timeslot optimization during compactness/conflict resolution
- ✅ No room thrashing during LS phases
- ❌ Loses effective conflict resolution via swaps (swaps are excellent for resolving H1/H3 conflicts)
- ❌ May need to increase Phase 4 duration to compensate for reduced move palette

**Acceptance Criteria**:
- [ ] Swaps removed from Phase 4 and 4b
- [ ] Re-benchmark and compare performance
- [ ] Verify Phase 5 duration is significantly reduced
- [ ] Verify final score quality is maintained
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERATO (2026-07-27): riferiva le 'Phase 4/4b' di una pipeline che non esiste piu' (oggi 5 fasi post-TASK-420; le LS sono fase 2 e 5). Gli esperimenti sulle mosse vivono nel cluster benchmark 280/281/284/332. Archiviato.
<!-- SECTION:NOTES:END -->
