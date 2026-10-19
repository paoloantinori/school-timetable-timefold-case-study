---
id: TASK-360
title: Excel (xlsx) import wizard with preview and dry-run diff
status: To Do
assignee: []
created_date: '2026-07-16 09:39'
updated_date: '2026-07-17 12:31'
labels:
  - claude-planned
  - spalmone-2027
  - ux
milestone: m-10
dependencies:
  - TASK-343
  - TASK-354
priority: medium
ordinal: 184000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: CsvImportWizard covers only 4 entity types, fixed columns, no preview, no xlsx. The school's source of truth is the spalmone workbook; yearly onboarding and mid-year updates must not require hand-typing.

WHAT:
1. Backend-parse approach (preferred over client SheetJS for consistency with TASK-343): endpoint accepting an xlsx upload, reusing the TASK-343 importer parsing, returning a structured PREVIEW: per entity type the rows detected, normalization applied, pseudo-rows skipped, and a DRY-RUN DIFF vs current DB (create/update/delete counts with samples).
2. Frontend wizard: upload -> preview grids per sheet -> confirm -> commit. Commit is transactional; failures roll back and report row-level errors.
3. Warn loudly that commit follows the override semantics (replaces matching data), and trigger the legality watchdog after commit.
4. Extend to availability rules and stage windows when TASK-345 lands (structure permitting) - otherwise scope to teachers/groups/rooms/courses/holidays and say so.

FILES: components/admin/CsvImportWizard.tsx -> ImportWizard.tsx, new backend resource reusing TASK-343 services.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Uploading the real spalmone xlsx yields a correct preview incl. skipped pseudo-rows and normalizations
- [ ] #2 Dry-run diff shown before any write; commit transactional with row-level error reporting
- [ ] #3 Watchdog fires post-commit; REST Assured + Playwright coverage
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SCOPE ADDITION 2026-07-16: the wizard must also accept the Calendario scolastico workbook (second standard input format, reading key in claudedocs/spalmone-2026-27/calendario-scolastico-format.md) reusing the TASK-364 parser - same preview + dry-run-diff flow.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
