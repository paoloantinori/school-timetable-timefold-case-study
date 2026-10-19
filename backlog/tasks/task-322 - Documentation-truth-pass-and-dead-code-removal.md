---
id: TASK-322
title: Documentation truth pass and dead code removal
status: In Progress
assignee: []
created_date: '2026-07-12 17:42'
updated_date: '2026-07-25 06:34'
labels:
  - claude-planned
  - housekeeping
milestone: m-2
dependencies: []
references:
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
priority: medium
ordinal: 146000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The project docs have drifted so far from the code that they actively mislead any executor agent. Verified 2026-07-12 (claudedocs/research/critical-assessment-and-roadmap-20260712.md sections 1 and 2, item 5): CLAUDE.md and PROJECT_INDEX.md still describe Timefold 1.31.0-patched (actual: 2.1.0, patch obsolete since 1.32.0), a LabBlock planning entity (deleted by TASK-267, commit 0a91e97), split constraint IDs H1.1/H1.2/H1.3 (actual: flat H1/H2/H3), and 28 teachers (DataGenerator has 31 names). branch-families.md claims 3 active branches and archive/* branches that do not exist on the remote (actual: only feature/cartesian-block-entity is active).

WHAT:
1. Update CLAUDE.md: Timefold version, canonical constraint table (add M21 max 1h per subject per day, M22 calendar compactness, M23 block lesson pair togetherness, H4.3, H11-T28; mark H18 demoted to M18; remove the Shadow/Block split-ID columns and the LabBlock references; note labs are pinned Lessons since TASK-228/267), teacher count, 6-phase pipeline description.
2. Update PROJECT_INDEX.md accordingly (also remove BacktrackingPreAssigner and LabBlock entries - neither exists).
3. Rewrite claudedocs/branch-families.md to reflect reality: one active branch, all others dormant with last-commit dates.
4. Delete dead files after grep-verifying zero references in src/: src/main/resources/preAssignmentPhase1a-labblocks.xml, preAssignmentPhase1b-lessons.xml (they reference the deleted LabBlock class), src/main/java/com/school/planner/solver/GapPreservingMoveFilter.java and UnpinnedLessonFilter.java (never wired into solverConfig.xml; GapPreservingMoveFilter is a stub returning null).
5. Backlog hygiene: TASK-302 duplicates TASK-298 (same H11 lab-block bug) - close one as duplicate with a cross-reference; two files share id task-038 - renumber or archive the stale one.
6. Create tasks/lessons.md (referenced by CLAUDE.md working principles but never created), seeded with the top operational lessons from claudedocs/insights/.
7. Rebuild and run tests to prove the deletions are safe.

DO NOT touch bootstrap/DataGenerator.java. DO NOT delete solverConfig.xml sections.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CLAUDE.md, PROJECT_INDEX.md and branch-families.md match verified code reality (version, entities, constraint IDs, branch status, teacher count)
- [ ] #2 Four dead files deleted; grep confirms no remaining references; ./scripts/core/rebuild.sh succeeds and ./scripts/core/test.sh passes
- [ ] #3 TASK-298/TASK-302 and task-038 duplications resolved with cross-references
- [ ] #4 tasks/lessons.md exists with seeded lessons
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-25: la meta' XML del punto (a) e' stata completata da TASK-418 (preAssignmentPhase1a/1b rimossi + riferimenti in PROJECT_INDEX/validate-solver-xml.sh; banner stale su claudedocs/solver-configurations.md in attesa della riscrittura che resta scope di QUESTO task). Non duplicare.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-14 17:03
---
PARTIAL PROGRESS 2026-07-14 (docs truth pass; done while rr-20-80 solve occupied the profile - no rebuild needed). Fixed the most misleading stale facts: Timefold version 1.31/1.19 -> 2.3.0 (README + PROJECT_INDEX x2) + obsolete 1.31 IdentityHashSet patch noted (fixed upstream in 1.32.0); Quarkus 3.19/Java 17 -> 3.31.4/Java 21 (README); 5-phase -> 6-phase pipeline + 'labs are pinned Lessons (no LabBlock)' (PROJECT_INDEX); teacher count 28 -> 31 (30 with courses) (CLAUDE.md); branch-families.md '3 active branches' -> 1 active + reality note on dormant families and the deleted LabBlock entity.
---

created: 2026-07-14 17:03
---
CORRECTION to the roadmap section 1.5 drift claim: UnpinnedLessonFilter is NOT dead code - it is referenced by src/test/resources/benchmarkConfig.xml (x2) and src/main/resources/benchmark/solverConfig-quality.xml (x2). Only GapPreservingMoveFilter is truly unreferenced (only its own file). Do NOT delete UnpinnedLessonFilter without first removing those benchmark-config references, or benchmark builds will break.
---

created: 2026-07-14 17:03
---
REMAINING (deferred - each needs a rebuild, which cannot run during the in-progress solve): (a) delete GapPreservingMoveFilter.java + the 2 orphaned preAssignmentPhase1a/1b XMLs after grep-verify + rebuild + test; (b) CLAUDE.md canonical constraint-table overhaul - remove the Block/Shadow split-ID columns, add M21/M22/M23/H4.3, note H18->M18; (c) create tasks/lessons.md; (d) backlog dedup (TASK-298/302, dual task-038).
---
<!-- COMMENTS:END -->
