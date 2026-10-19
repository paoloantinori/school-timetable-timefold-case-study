---
id: TASK-359
title: Recurring teacher availability editor UI (Untis-style weekly blocking)
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
  - TASK-345
priority: medium
ordinal: 183000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: TeacherAvailAdmin only supports absolute datetime ranges, add/delete, no edit. The real constraints are weekly-recurring (TASK-345 backend model). Data entry must be a weekly grid, like Untis availability blocking.

WHAT:
1. Rework TeacherAvailAdmin: per selected teacher show a 5x8 weekday-slot grid; click/drag to paint UNAVAILABLE / PREFERRED; date-range validity selector; dated one-off absences remain as a list below (with edit, not just add/delete).
2. Reads/writes the recurring-rule model from TASK-345.
3. Show the affected teacher's current scheduled lessons that CONFLICT with a newly painted unavailability (pull from the watchdog/analysis) before saving - the user must see what breaks.

FILES: components/admin/TeacherAvailAdmin.tsx (rewrite), api/scheduleApi.ts, types/domain.ts.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Weekly paint-grid creates/edits recurring rules; dated absences editable
- [ ] #2 Saving an unavailability that clashes with existing lessons warns with the concrete lesson list first
- [ ] #3 ui-typecheck.sh clean; basic Playwright coverage
<!-- AC:END -->

## Implementation Notes

The backend CRUD (`RecurringAvailabilityRuleResource`) shipped in TASK-345,
and a code-review pass added validation: create/update now reject (400) a
slot range that overlaps another rule for the same teacher, `effectiveFrom`
after `effectiveTo`, and a single-date rule whose `dayOfWeek` doesn't match
that date's actual weekday. This editor must surface those 400s meaningfully
(not a generic error toast) — the paint-grid interaction in particular makes
it easy to draw an overlapping block, so the UI needs to explain *why* a save
was rejected in terms the user painted (e.g. "this clashes with the existing
Monday 13:00-16:30 rule"), not just relay the raw REST error string.

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
