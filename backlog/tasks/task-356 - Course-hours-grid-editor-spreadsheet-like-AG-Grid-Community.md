---
id: TASK-356
title: 'Course-hours grid editor (spreadsheet-like, AG Grid Community)'
status: Done
assignee: []
created_date: '2026-07-16 09:38'
updated_date: '2026-07-17 01:47'
labels:
  - claude-planned
  - spalmone-2027
  - ux
milestone: m-10
dependencies: []
priority: high
ordinal: 180000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: the spalmone holds ~290 (teacher, subject, class, hours) assignments. CoursesAdmin edits one row at a time via a form - slower than Excel, no totals, no bulk edit. This is the number-one blocker to replacing the Excel (frontend gap analysis, 2026-07-16).

WHAT:
1. Add AG Grid Community (MIT) to src/main/webui. Rebuild CoursesAdmin around an editable grid: inline cell editing, teacher/group dropdown cell editors, multi-row selection, fill-down, clipboard paste, column totals, and row grouping by teacher OR by class with hour subtotals (this replicates the Excel Classi rollups).
2. Batch save: PUT changes as a batch (new endpoint or loop with optimistic UI - prefer a batch endpoint to keep the watchdog TASK-354 firing once).
3. Live per-teacher and per-group total-hours footer so overload is visible while typing (Untis-style teacher cards can come later).
4. Keep FilterBar text filtering. Keep the per-course Generate Lessons action.
5. E2E: Playwright test editing hours in two cells, checking subtotal update and persistence after reload.

FILES: src/main/webui/src/components/admin/CoursesAdmin.tsx (rewrite), new CourseGrid component, api/scheduleApi.ts, backend batch endpoint in CourseResource.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Grid supports inline edit, dropdown editors, multiselect fill-down, grouping with subtotals, column totals
- [x] #2 Batch save endpoint exists and is used; single watchdog validation per batch
- [x] #3 Playwright e2e passes; ui-typecheck.sh clean
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-17 night: e2e EXECUTED live — 4/4 passed (labBlockSize regression test skips by design until seed has labBlockSize>1). Spec robustness fixes committed. Course delete on seeded courses fails on FK (no cascade) — noted in TASK-362.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
