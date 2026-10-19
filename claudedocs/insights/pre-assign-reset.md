# Always reset the schedule before running pre-assign

> 📌 **Session memory** (operational gotcha). Verify endpoint names against `claudedocs/rest-api.md` / `api.sh --help` before use.

## Rule

Always reset the schedule before running pre-assign. The correct sequence:

1. `./scripts/api/api.sh post /api/schedule/reset` — clears ALL lesson assignments (timeslots, rooms, pins)
2. `./scripts/api/api.sh pre-assign` — run pre-assignment fresh
3. Poll `./scripts/api/api.sh pre-assign-status` until COMPLETE

## Why

`pre-assign-reset` (`POST /api/schedule/pre-assign/reset`) only flips the status enum from ERROR/COMPLETE back to IDLE — it does **not** clear pinned lessons or assignments. Pre-assignment can be in ERROR or COMPLETE state from a previous run; without a full reset you get an "already running" guard or stale pinned assignments.

## How to apply

Every time you need to run pre-assign, run `post /api/schedule/reset` first. No exceptions.
