---
id: TASK-194
title: Update constraint documentation and fix M12 ID collision
status: Done
assignee: []
created_date: '2026-03-07 15:35'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - documentation
  - constraints
dependencies: []
ordinal: 89000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Task**: Synchronize frontend constraint definitions with actual implementation and fix constraint ID collisions.

**Changes made**:

1. **Fixed M12 ID collision** (`SchoolScheduleConstraintProvider.java`):
   - Renamed "M12: Horizontally compact" to **M20**
   - M12 now exclusively refers to "Overflow beyond target end date" (in `BaseConstraintProvider.java`)

2. **Updated frontend constraint definitions** (`src/main/webui/src/data/constraints.ts`):
   - Added **H19**: Max one lab block per day per pair
   - Added **M13**: Vertically compact (medium priority)
   - Added **M17**: Lab recurring pattern (same day)
   - Added **M18**: Multi-day lab start-time consistency
   - Added **M20**: Horizontally compact (renamed from M12)
   - Removed incorrect H13 entry (had wrong nameKey h13.name)

3. **Updated i18n translations** (`src/main/webui/src/i18n/locales/en/it/docs.json`):
   - Added h19, m13, m17, m18, m20 entries with descriptions and examples
   - Both English and Italian translations updated

4. **Updated CLAUDE.md**:
   - Added M12, M13, M20, M-LA to Medium Constraints table
   - Clarified branch-specific constraint IDs

**Constraints NOT in code** (documented but unimplemented):
- H8: Min student group daily lessons — listed in docs but not found in code
- H-D1: Doppio teacher conflict — listed in docs but not found in code
- S5: Minimize student group gaps — replaced by M13 (vertically compact)
- S6: Lab block contiguity — implicit via LabBlock entity construction

**Next steps** (if needed):
- Implement H8 if required by school
- Implement H-D1 (doppio teacher conflict) if required
- Remove S6 from docs if not implementing
- Remove h13.name from i18n (now uses m13.name)
<!-- SECTION:DESCRIPTION:END -->
