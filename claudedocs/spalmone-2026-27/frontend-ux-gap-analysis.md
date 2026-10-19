# UX Gap Analysis: Planner Frontend as Excel ("spalmone") Replacement

*Produced 2026-07-16 by an Opus review agent; load-bearing claims verified mechanically (grep) in the main session: `assignLesson` is exported by `api/scheduleApi.ts:93` and called by no component; no `draggable`/`onDrop` exists anywhere in the frontend; `ALL_TIME_PERIODS` is hardcoded in `components/timeline/types.ts`.*

## Summary

The frontend is a solver-visualization tool with thin CRUD admin pages bolted on, not a data-maintenance workbench. Every admin page follows the same pattern: one single-row "add" form at top, a flat searchable table below, edit-in-place-via-form, one row at a time. There is **no grid/bulk editing, no reconciliation dashboard, no manual timetable editing, no audit trail, and no Excel import** anywhere. Two capabilities the codebase *appears* to have are in fact absent: the `assignLesson(timeslotId, roomId)` API exists but is **never called from the UI** (so you cannot manually move a lesson to a slot), and the "draggable unassigned lessons" / drag-drop claimed in `PROJECT_INDEX.md` **does not exist** — the only drag code repositions the detail modal, and `UnassignedSidebar` is a read-only list. Closing the gap to replace the Excel is a substantial build, not a polish pass.

Key files: all under `src/main/webui/src/`.

## Current State (per admin page)

All pages live in `components/admin/`, share `hooks/useAdminList.ts`, `admin/styles.tsx`, and `admin/FilterBar.tsx`, and are wired in `admin/AdminLayout.tsx`.

- **TeachersAdmin.tsx** — CRUD for name, nickname (≤20), color, pattern swatch. One row at a time. No subject/hours/availability shown here.
- **CoursesAdmin.tsx** — The ~300-row heart of the spalmone: subject, teacher (select), group (select), totalHours, isLab, requiredRoomTags (comma string), color. Single-row form, flat table, text filter. No grouping by teacher/class, no column totals, no bulk edit. Per-course "Generate Lessons" button. **No `doppioTeacher` / co-teaching field** despite it being a domain concept.
- **StudentGroupsAdmin.tsx** — Edits only name, size, `allowProfessionalizzantiDuringStage`. Notably **does not expose `maxHoursPerDay` or `startDate`** (`startDate` is edited in SchoolSettings; `maxHoursPerDay` is editable **nowhere**).
- **RoomsAdmin.tsx** — name, capacity, tags (comma string). Fine for the scale.
- **HolidaysAdmin.tsx** — date + description, add/delete only (no edit). One date per row; no range entry.
- **StagePatternsAdmin.tsx** — group, startDate, endDate, day-of-week checkboxes. Add/delete only. No calendar/visual overlap check.
- **TeacherAvailAdmin.tsx** — teacher, start datetime, end datetime, type (SICK/VACATION/PREFERENCE). Add/delete only. Availability is **absolute datetime ranges** — no recurring/weekly rule and no free-text parsing.
- **LabAssistantsAdmin.tsx** — teacher + roomTag. Hand-rolled state (doesn't use `useAdminList`).
- **ProfessionalizzantiDaysAdmin.tsx** — group, date, dayOfWeek, description. Add/delete.
- **SchoolSettingsAdmin.tsx** — inline-edit of per-group `startDate` + read-only count links. The only page with true inline editing.
- **CsvImportWizard.tsx** — single-file CSV upload for exactly 4 types (teachers, groups, courses, holidays), fixed column order, no preview, no validation, no column mapping, **no .xlsx**.
- **FilterBar.tsx / useAdminList.ts** — shared text filter + fetch/edit/error state. Solid but single-entity, no cross-entity views.

### Schedule views (`components/`, `components/timeline/`)

- **ScheduleTimeline.tsx** — orchestrator: fetch, poll (500 ms while solving/pre-assigning), week vs infinite view, group-by student/teacher/room, filter bar, sidebar, cell-click → detail modal.
- **WeekViewGrid.tsx / TimelineView.tsx / VirtualizedDayRow.tsx / cellRenderer.tsx** — read-only grids. Cells are **click-to-open-modal only**; empty cells do nothing; no drop targets. Slots hardcoded to **6 morning periods 08:00–14:00** (`ALL_TIME_PERIODS` in `timeline/types.ts`). Per-group variation is only "5h vs 6h" + "Friday has 5h", hardcoded in `isSlotAvailable()` in `hooks/useScheduleData.ts`. **No afternoon 14:30–16:30 support, no per-weekday opening slots.**
- **LessonTooltip.tsx** — detail modal. Actions are **Pin/Unpin and Close only**. The drag handles move the modal, not the lesson. No slot/room reassignment control.
- **UnifiedSolverControls.tsx** — Pre-assign, Reset, Solve/Stop, "Reactive Fix" (re-solve one teacher's day via `reschedule`), Analyze, Progression, and **JSON Export/Import** (whole-schedule blob for solver A/B testing — not a data-entry import). Rich solver telemetry.
- **AnalysisPanel.tsx** — post-solve constraint breakdown + per-constraint lesson-level drill-down. The strongest existing "coherence" surface, but **solver-score-oriented**, only meaningful after a solve — not hour reconciliation.
- **ScheduleSidebar.tsx / UnassignedSidebar.tsx** — stats (score cards, global assigned/total progress bar, top-5 violations), unassigned-by-subject list. Read-only.

## Gap Analysis (ranked by impact on the Excel-replacement goal)

1. **No bulk/grid editing for the ~300 course-hour assignments (highest impact).** Editing one modal-form-row at a time is slower than Excel; no column totals, no per-teacher/per-class roll-ups, no fill-down, no multi-select. → TASK-356
2. **No reconciliation dashboard (assigned vs scheduled hours).** The data exists but is never assembled; `/api/completion` is fetched but only used for slot badges. No view answers "does each teacher's/subject's/class's scheduled hours equal its assigned hours, and where's the mismatch?" → TASK-353
3. **No manual timetable editing with instant re-validation.** `assignLesson` API unused; no drag-drop, no slot/room picker. Validation only happens by running the solver's analysis after a solve. → TASK-357, TASK-354
4. **Free-text teacher availability has no ingestion path and no recurring model.** Rules like "unavailable every Monday morning" must be hand-exploded into dated rows. → TASK-345, TASK-359
5. **Per-group calendars not modelable in the UI.** Afternoon slots don't exist in the grid; per-weekday opening slots not representable; `maxHoursPerDay` not editable anywhere. → TASK-342, TASK-358
6. **Excel import absent; CSV import minimal.** No `.xlsx`; 4 of ~11 entity types; no preview/dry-run/mapping. → TASK-360
7. **No audit/versioning of data changes.** No history, undo, or per-record snapshots. → TASK-361
8. **Minor:** several entities add/delete-only; no admin UI for `LabRotation` at all. → TASK-362

## Borrowed Ideas from External Tools

- **FET — activities hour-matrix entry** with split-hours (2+1) idioms → course-hours grid (TASK-356).
- **FET — always-visible constraint satisfaction list** (pre-solve framing) → inline validation feedback (TASK-354/357).
- **UniTime — course-demand reconciliation** (requested vs scheduled, continuous) → reconciliation dashboard (TASK-353).
- **UniTime — interactive grid with drag-drop and immediate conflict highlighting** → manual placement (TASK-357).
- **Timefold/OptaWeb rostering demos — unassigned tray as drag source + score-delta feedback** → UnassignedSidebar + grid (TASK-357).
- **ASC Timetables / Untis — per-teacher/per-class summary cards** (total hours, gaps, over/under load) → reconciliation rollups (TASK-353/356).
- **Untis — recurring weekly availability blocking** → availability editor (TASK-359).
- **AG Grid Community (MIT)** — inline editors, dropdown cells, row grouping with aggregation, fill-handle, clipboard paste. Recommended default for the grid editor.
- **Glide Data Grid (MIT)** — canvas-rendered alternative if grids grow very large.
- **Handsontable** — Excel-like UX but non-commercial license caveat; prefer AG Grid.
- **SheetJS (xlsx)** — client-side parsing option for the import wizard (backend parsing preferred for consistency with the importer).
