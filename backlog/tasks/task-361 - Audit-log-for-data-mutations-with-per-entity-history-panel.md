---
id: TASK-361
title: Audit log for data mutations with per-entity history panel
status: To Do
assignee: []
created_date: '2026-07-16 09:40'
updated_date: '2026-07-17 12:31'
labels:
  - claude-planned
  - spalmone-2027
  - ux
milestone: m-10
dependencies: []
priority: medium
ordinal: 185000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: as the app becomes the system of record (replacing Excel), silent edits are a real risk: no history, no who-changed-what, no undo. The user explicitly wants to notice when a number changes on one side only - the audit log is the forensic complement to the reconciliation dashboard (TASK-353) and watchdog (TASK-354).

WHAT:
1. Backend: audit table {timestamp, entityType, entityId, field, oldValue, newValue, source} written on every mutating CRUD operation - a JPA listener or Panache lifecycle hook, choose the least invasive uniform mechanism. No auth exists, so source = API endpoint + optional client-supplied label.
2. Endpoint: GET history filtered by entity type/id, paginated.
3. Frontend: History panel accessible from each admin page showing recent changes for that entity type; a compact global recent-changes feed on the reconciliation page.
4. Out of scope (say so in code comments only if needed): undo/restore, full snapshotting - keep the JSON export as the snapshot mechanism for now.

FILES: new backend AuditLog entity + resource, hooks in the mutation path, components/admin/HistoryPanel.tsx.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All CRUD mutations produce audit rows incl. old/new values; verified per entity type in tests
- [ ] #2 History panel renders filtered history in each admin page
- [ ] #3 No measurable slowdown on bulk import (batch writes)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
