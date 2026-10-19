---
id: m-10
title: "Data-Entry UX - Excel Replacement"
---
## Description

Make the app the primary data-entry and maintenance tool, replacing the Excel. Based on the verified frontend gap analysis (claudedocs/spalmone-2026-27/frontend-ux-gap-analysis.md): no grid/bulk editing, assignLesson API never wired to the UI, no drag-drop (PROJECT_INDEX claim false), slots hardcoded to 6 mornings, minimal CSV import, no audit trail. Borrowed patterns: FET activity matrix, UniTime live conflict highlighting, Timefold rostering drag-drop with score feedback, Untis weekly availability blocking, AG Grid Community for spreadsheet UX.

Tasks: TASK-356 (course-hours grid editor), TASK-357 (manual placement + drag-drop + instant re-validation), TASK-358 (data-driven periods + calendar editor), TASK-359 (availability editor), TASK-360 (xlsx import wizard), TASK-361 (audit log), TASK-362 (admin completeness).

**Exit criteria:** the yearly spalmone can be imported, maintained and hand-adjusted entirely in-app with immediate legality feedback; no data lives only in Excel.

**DoD OBBLIGATORIA per ogni task**: /simplify + /code-review high (verifica Opus, fix dei confermati) + verifica finale con exit code dall'orchestratore. Nessun task è Done senza.
