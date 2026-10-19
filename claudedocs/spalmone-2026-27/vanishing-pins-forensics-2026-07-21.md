# Vanishing-pins incident — forensics (TASK-383, 2026-07-21)

## Incident (2026-07-20)
Between two Playwright screenshot runs against the shared `dev` app, all 866
pinned lessons vanished: `0/10501` assigned, sidebar H0 `9,635 → 10,501`.
Re-import restored the state; the getByRole-exact rewrite of the spec did
NOT reproduce it on the next run.

## Root cause — attribution by elimination (mechanical)
The symptom is precise: **10,501 lessons still present** (not deleted), but
**all unpinned and unassigned**. Exactly one code path produces that state:

- `ScheduleService.resetSchedule()` (line ~787):
  `Lesson.update("timeslot = null, room = null, pinned = false")` — the ONLY
  mass-unpin of every lesson in the codebase (verified: it is the sole
  `Lesson.update(... pinned = false)` affecting all rows).

Every other destructive path is ruled out by the symptom:
- **Bulk deletes** (`GoldenScheduleImportService.deleteAll`,
  `CalendarioImportService.delete(...)`, `LessonGenerator`, `CourseResource`)
  would REDUCE the lesson count — but 10,501 were still present, so no delete
  ran.
- **`healPinnedNullTimeslotLessons`** (line ~335) only unpins lessons that
  are pinned *with a null timeslot*; imported pins carry a timeslot, so a
  startup heal cannot mass-unpin placed pins.
- **`DataGenerator`** is guarded by `Timeslot.count()==0` — a no-op on a
  seeded profile.
- The reschedule flow (line ~749) unpins then immediately **re-pins**, so it
  never leaves everything unpinned.

Reset is reachable only via `POST /api/schedule/reset` (the UI Reset
button). The failing spec's broad locator `div:has-text("Classes").last().click()`
could match ANY container `div`, including one wrapping the Reset control;
the getByRole-exact rewrite (which targets only the intended element) not
reproducing the wipe **corroborates** that the broad locator was the
trigger. No `Schedule reset` log line survived (planner.log rotates on the
`rebuild.sh` restart that sat between the two runs), which is why the
original evidence was lost — the exact gap AC#2 closes.

**Conclusion:** the vanishing pins were a `POST /api/schedule/reset` fired by
a stray Playwright click on an over-broad locator. Not a startup/rebuild
mutation, not data corruption.

## AC#2 — destructive endpoints now attributable
`ScheduleService.resetSchedule()` logs at WARN, before and after the update,
the count of pinned/assigned lessons it destroys. `ScheduleResource`'s reset
endpoint logs the request **source** (remote IP + `User-Agent`, which
distinguishes a Playwright run from a browser from curl). A recurrence is
now definitively attributable in the log even after a restart, because the
WARN fires at the moment of the reset, not only in a rotated access log.

## AC#3 — decision on reset-button confirmation UX
**Decision: add a UI confirmation dialog on the Reset button** (deferred to
implement + e2e-verify when the app is next up, alongside TASK-367 — it
changes `reset-button.spec.ts`'s contract, which must be validated live).

Rationale: the incident was a stray click on the *legitimate* Reset control,
so a server-side guard (e.g. a `confirm=true` param) does NOT help — the UI
handler would pass it unconditionally and a stray click still resets. The
only real protection against an accidental click is a confirmation step the
user must deliberately clear. A server-side confirm token is worth adding
only as defense-in-depth against non-UI callers (stray curl/script), not as
the primary fix. Until the dialog lands: **do not run browser automation
against a `dev` profile whose state matters** (standing rule).
