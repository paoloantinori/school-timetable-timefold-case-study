# Frontend dev rules & gotchas

> 📌 **Session memory** (~March 2026, operational). **Verified 2026-07-12 (live test):** this note is correct. `api.sh` has **no `raw` subcommand** — `api.sh raw ...` returns "Unknown command: raw" (exit 2). There is also **no generic `fetch` command** — `fetch` is an internal function only. Use the named commands below. (`CLAUDE.md` / `PROJECT_INDEX.md` previously documented the stale `api.sh raw /api/path`; corrected 2026-07-12.)

## Playwright cache busting

Always hard-refresh after a rebuild when verifying frontend changes with Playwright.

**Why:** Playwright's `browser_navigate` reuses the browser's HTTP cache. Even after `./scripts/core/rebuild.sh` produces a new bundle hash, the browser may serve the old cached JS/CSS — causing false negatives.

**How to apply:** after navigating to `http://localhost:8080`, run:
```javascript
await page.evaluate('() => { location.reload(true); return "hard reload"; }');
```
Never trust the first render after a `browser_navigate` — always reload first.

## api.sh subcommands (verified 2026-07-12)

`api.sh` exposes only these named commands (confirm with `./scripts/api/api.sh --help`):

`status`, `score`, `analysis`, `lessons`, `teacher`, `group`, `health`, `completion`, `export`, `reset`, `solve`, `stop`, `reschedule`, `pre-assign`, `pre-assign-status`, `pre-assign-reset`, `compaction-mode`, `post`, `delete`, `put`, `wait`.

- For writes: `post`, `put`, `delete` (e.g. `api.sh post /api/path -d '{...}'`).
- There is **no generic GET** for an arbitrary path — use the matching named command (e.g. `api.sh pre-assign-status` rather than a raw GET to `/api/schedule/pre-assign/status`).

> ✅ **Migration (2026-07-12):** the runtime benchmark scripts (`overnight-benchmark.sh`, `simple-overnight-benchmark.sh`, `benchmark-pillar-moves.sh`) and the `full-test` / `laptop-test` skills were migrated off `api.sh raw` to named commands; a new `phase-progression` command was added to `api.sh` for the one endpoint that lacked a named equivalent. A few dated `claudedocs/` reports and `scripts/README.md` still reference `api.sh raw` as historical record — they don't affect runtime.

## Frontend command directory

Some frontend commands require `src/main/webui`, others run from project root.

- `src/main/webui` required: `npx playwright test`, `npx playwright ...`, `npm run ...`
- Project root: `./scripts/core/rebuild.sh`, `./scripts/api/api.sh ...`, `./planner.sh ...`
