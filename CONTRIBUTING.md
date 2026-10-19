# Contributing

Thanks for looking at this project. It is a constraint-solver application, which means the
usual "write code, run tests, open a PR" loop has a few extra rules: solver changes are easy
to make and hard to evaluate, and most of the conventions below exist to keep evaluation
honest and cheap.

If you are an AI coding agent, read **[docs/ai-solver-workflow.md](docs/ai-solver-workflow.md)**
first. It is the method this repository is developed with, and it will save you from the
expensive mistakes (iterating on full solves, trusting a clean score, tuning a weight that
turns out not to be the lever).

---

## Getting set up

Requirements: JDK 21, Maven, Node, and a container runtime (`podman` preferred, `docker`
works). There is **no Maven wrapper** in this repository, and direct `mvn` / `psql` / `curl`
invocations are deliberately not part of the workflow: use the scripts.

```bash
./planner.sh start dev          # database + app, seeded on first run
./scripts/core/rebuild.sh       # rebuild backend + frontend, restart, wait for readiness
./scripts/core/test.sh          # the test suite (H2 in memory, no database needed)
./scripts/ui/ui-typecheck.sh    # TypeScript type check
./scripts/api/api.sh --help     # REST helpers: status, score, analysis, export, ...
./scripts/db/db-query.sh "SQL"  # read-only by default, -w for writes
```

`scripts/README.md` and `claudedocs/scripts.md` document the rest. The frontend is baked into
the JAR at package time, so a frontend change needs a rebuild, not just a restart.

---

## Before you change a constraint

This is the part that is specific to this project.

**Pick the cheapest test that produces the verdict.** The pyramid is documented in
`claudedocs/localized-testing-guidelines.md`, with a matrix from change type to minimal test
chain. In short: constraint semantics are settled by `ConstraintVerifier` unit tests in
seconds; interaction with a real state is settled by snapshot harnesses in under a minute; a
full solve is a final certification, never a development loop.

**Check the reference solution first.** The school's own hand-made timetable lives in
`claudedocs/spalmone-2026-27/data/lessons.csv` and is treated as valid by definition. A new
hard constraint that the reference violates is wrong by construction, and counting the
violations takes a minute. `GoldenSolutionRegressionTest` guards this automatically and must
stay green.

**Watch the tier and the weight.** Scoring is lexicographic. Promoting a rule to hard at the
same weight as an existing hard rule can hand the solver a cheaper escape than the behaviour
you wanted (we once un-assigned 9,404 lessons that way). Write the arbitrage inequality down
before choosing a number, and sweep the weight before assuming it is the lever.

**Know which phases your constraint is alive in.** `forEach` sees only fully initialised
entities, so in a pipeline where a variable is assigned late, such a constraint is blind
earlier. Use `forEachIncludingUnassigned` plus an explicit filter when the constraint should
fire during those phases.

**Constraint IDs are canonical.** Every `.asConstraint(...)` string is `"<ID> - <description>"`
(for example `"H1 - Teacher conflict"`). Colons are rejected by Timefold. The ID list lives in
`CLAUDE.md` under "Canonical Constraint IDs"; add or rename an ID there in the same change, and
also in `src/main/webui/src/data/constraints.ts`, the two `messages_*.properties` files, and the
locale `docs.json` files if the constraint should appear in the app's documentation tab.

**Changing `solverConfig.xml` has a fan-out.** Phase names, progression tracking, and the
frontend phase documentation all mirror it; see `.claude/rules/solver-config-sync.md` for the
list, and validate the XML with `scripts/validate/validate-solver-xml.sh`.

---

## Data files are protected

`src/main/java/com/school/planner/bootstrap/DataGenerator.java` and the CSVs under
`src/main/resources/seed/` contain a real school's real data: teachers, courses, hours,
calendars. They are not sample data and they are not yours to tidy up. Do not modify them
without explicit permission from the maintainer, and when you get it, keep the change to the
scope that was authorised.

If an experiment needs different data, simulate it **in memory inside a test harness** and
deliver the measurement plus a proposed patch. `RoomPhaseSnapshotTest` shows the pattern: it
can strip a room tag or change a room's capacity in the fixture, measure the effect, and leave
the real files untouched.

---

## Definition of done

Every change, including small ones, closes with:

1. a simplification pass over the diff (reuse, dead code, redundant state), findings applied
   or explicitly declined;
2. a code review pass over the diff, with confirmed findings fixed;
3. **`./scripts/core/test.sh` run to completion with its exit code read**, plus
   `./scripts/ui/ui-typecheck.sh` when the frontend is touched.

Point 3 is not a formality: reading a tail of the log is not evidence, and "the tests should
pass" is not a claim this project accepts. If something fails, say so with the output.

For anything that changes solver behaviour, the evidence bar is higher: a measurement, from a
named test or a captured run, with the numbers before and after. Local search is stochastic,
so a single run is one trajectory, not a result; adoption evidence is two runs of the same
configuration.

---

## Commits and pull requests

- Conventional-commit style with the area in scope: `feat(solver): ...`, `fix(446): ...`,
  `docs(backlog): ...`, `test(analyze): ...`.
- The body explains **why**, and for behavioural changes carries the measurement. Commit
  messages in this repository are part of the evidence trail; several of them are the only
  record of why a weight is what it is.
- One logical change per commit. If a rename cascades across files, that cascade belongs in
  the same commit, not a follow-up.
- Do not commit run artifacts unless they are the evidence for the change; captured runs live
  under `claudedocs/spalmone-2026-27/` with a `VERDICT.md` explaining what was measured.

---

## Where the documentation lives

| You want | Read |
|---|---|
| The development method, and the Timefold traps | `docs/ai-solver-workflow.md` |
| Project rules, constraint IDs, campaign decisions | `CLAUDE.md` |
| Which test to run for which change | `claudedocs/localized-testing-guidelines.md` |
| Why a constraint is weighted the way it is | `claudedocs/constraint-recalibration-history.md` |
| The solver pipeline | `claudedocs/solver-phases.md` |
| Scripts, including the KPI battery | `claudedocs/scripts.md`, `scripts/README.md` |
| Repository layout and entry points | `PROJECT_INDEX.md` |

---

## Reporting a problem

A useful issue for a scheduling engine is rarely "the timetable is wrong". What makes it
actionable: which group and which date, what you expected there and why, and, if you have it,
the exported solution or the lessons dump from the run. If the problem is about a shape rather
than a single cell (days starting late, subjects clumped, rooms churning), the KPI scripts
under `scripts/analyze/` already measure most of those shapes and their output is the fastest
way to make the report concrete.
