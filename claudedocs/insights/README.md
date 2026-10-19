# Insights — session-captured knowledge

This folder consolidates **captured session memories** that previously lived only in local
Claude/`zai` auto-memory (outside the repo). They are copied here so the project's accumulated
operational and research knowledge is version-controlled and shareable.

## ⚠️ How to read these

These are **point-in-time observations**, not live documentation. Most were captured
~March 2026 (90–120 days before import). Before relying on any specific detail:

- **File paths / script names** → verify with `ls scripts/` and `./scripts/api/api.sh --help`
- **Constraint IDs** → cross-check the authoritative table in [`../CLAUDE.md`](../CLAUDE.md)
  ("Canonical Constraint IDs") — some IDs here (e.g. M21, M22, H18→M18) may lag
- **API endpoints** → verify against [`rest-api.md`](../rest-api.md)
- **Scores / configs** → re-run; solver results are point-in-time

Where a note conflicts with `CLAUDE.md` / `PROJECT_INDEX.md`, **verify either side** — both can
drift. (Example: `frontend-dev-rules.md` was confirmed correct on 2026-07-12 while the repo docs'
`api.sh raw` reference was stale.) Known conflicts are called out inline in the relevant file.

The originals remain in local auto-memory, so live cross-session recall still works.

## Index

### Research findings & benchmark data
| File | What it captures |
|------|------------------|
| [ch-only-benchmark-results.md](ch-only-benchmark-results.md) | Custom DayClustered CH vs ALLOCATE_FROM_POOL: scores, constraint breakdown, XML config lessons |
| [benchmark-running-instructions.md](benchmark-running-instructions.md) | How to run Timefold benchmarks: `run-benchmarker.sh`, 3-config vs 23-config, env vars, outputs (reconstructed) |
| [5hour-day-groups.md](5hour-day-groups.md) | Which student groups have `maxHoursPerDay=5` per the spalmone CSV (with corrected column mapping) |
| [preassignment-csp-limits.md](preassignment-csp-limits.md) | Why the hand-rolled DayAssignmentCsp is a dead end for general lab pre-assignment |
| [timefold-sorter-workaround.md](timefold-sorter-workaround.md) | TimSort "Comparison method violates its general contract" crash in RuinRecreate Phase 4 |

### Operational lessons & gotchas
| File | What it captures |
|------|------------------|
| [script-usage-gotchas.md](script-usage-gotchas.md) | Corrected script paths, `db-query.sh` read-only/camelCase gotchas, ImportService LabRotation deletion |
| [frontend-dev-rules.md](frontend-dev-rules.md) | Playwright cache busting, `api.sh` subcommands, command-directory rules |
| [full-test-workflow-lessons.md](full-test-workflow-lessons.md) | Bootstrap vs iterative test workflow, `rebuild-checked.sh` PROJECT_ROOT fix, hook violations |
| [pre-assign-reset.md](pre-assign-reset.md) | Always full-reset before pre-assign (`pre-assign-reset` only flips the status enum) |
| [preassign-pin-rate-metrics.md](preassign-pin-rate-metrics.md) | Distinguish lab pin rate (~97%) from total pin rate (~30%) |

### Conventions
| File | What it captures |
|------|------------------|
| [constraint-documentation-rule.md](constraint-documentation-rule.md) | Always annotate constraint IDs with a description; quick-reference list |
| [italian-spelling-professionalizzanti.md](italian-spelling-professionalizzanti.md) | It's *professionalizzanti* (ends in "ti"), not "professionalizzani" |

## Relationship to the rest of `claudedocs/`

These notes complement, not replace, the curated docs:
- Authoritative architecture/usage docs live in the parent [`claudedocs/`](../) directory
- Deeper research write-ups live in [`claudedocs/research/`](../research/)
- Design decisions are summarised inline in `CLAUDE.md` ("Design Decisions"), with the full
  historical essays in [`claudedocs/constraint-recalibration-history.md`](../constraint-recalibration-history.md)
  (moved there verbatim by TASK-431)
