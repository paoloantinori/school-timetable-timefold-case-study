---
id: TASK-084
title: Remove orphaned H10 constraint references from UI and docs
status: Done
assignee: []
created_date: '2026-02-23 13:57'
updated_date: '2026-03-09 19:50'
labels:
  - cleanup
  - ui
  - documentation
dependencies: []
priority: low
ordinal: 130000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

H10 (Preparazione before stage) constraint was removed from `SchoolScheduleConstraintProvider.java`. The Java codebase is clean, but orphaned references remain in the frontend and documentation layer.

## Problem

Users viewing the `/docs` page will see H10 listed as a valid constraint, causing confusion. Documentation files reference a non-existent constraint, creating maintenance debt.

## Scope

### In Scope (Active Code/UI)
| File | Action | Impact |
|------|--------|--------|
| `src/main/webui/src/data/constraints.ts` | Remove H10 entry | Constraint list in /docs |
| `src/main/webui/src/i18n/locales/en/docs.json` | Remove `h10` object | English translations |
| `src/main/webui/src/i18n/locales/it/docs.json` | Remove `h10` object | Italian translations |
| `CLAUDE.md` | Update constraint tables | Constraint ID reference |
| `PROJECT_INDEX.md` | Remove from list | Quick reference index |

### Out of Scope (Historical)
- `claudedocs/research/*.md` — Research records, not active docs
- `backlog/tasks/*.md` — Task history

## Implementation Notes

1. In `constraints.ts`, locate the H10 object (lines 68-73) and delete the entire block
2. In each locale JSON, find `"h10": { ... }` and remove the complete section
3. In CLAUDE.md, remove H10 row from both constraint tables (Hard Constraints table and canonical ID table)
4. In PROJECT_INDEX.md, remove H10 from the bullet list under Constraints

## Verification

```bash
# TypeScript check
./scripts/ui-typecheck.sh

# Full rebuild
./scripts/rebuild-ui.sh

# Grep for remaining references
grep -r "H10" src/main/webui/src/
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 H10 entry removed from constraints.ts array (no broken references)
- [x] #2 h10 section removed from en/docs.json (valid JSON)
- [x] #3 h10 section removed from it/docs.json (valid JSON)
- [x] #4 CLAUDE.md constraint tables updated - H10 row removed from both tables
- [x] #5 PROJECT_INDEX.md constraint list updated - H10 bullet removed
- [x] #6 TypeScript compiles: ./scripts/ui-typecheck.sh passes

- [x] #7 UI rebuilds: ./scripts/rebuild-ui.sh completes
- [x] #8 /docs page renders correctly with no H10 visible
- [x] #9 No H10 references in src/main/webui/src/ (verified by grep)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
1. In `constraints.ts`, locate the H10 object (lines 68-73) and delete the entire block
2. In each locale JSON, find `"h10": { ... }` and remove the complete section
3. In CLAUDE.md, remove H10 row from both constraint tables (Hard Constraints table and canonical ID table)
4. In PROJECT_INDEX.md, remove H10 from the bullet list under Constraints

## Verification

```bash
# TypeScript check
./scripts/ui-typecheck.sh

# Full rebuild
./scripts/rebuild-ui.sh

# Grep for remaining references
grep -r "H10" src/main/webui/src/
```
<!-- SECTION:DESCRIPTION:END -->
<!-- SECTION:NOTES:END -->
