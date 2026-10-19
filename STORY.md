# From a hand-made timetable to zero hard violations

How a solo maintainer and a team of AI agents took a real Italian vocational
school's timetable problem from "the solver cannot do it" to a certified
0-hard schedule, and then prepared the whole record for publication.

## Pseudonym legend

This repository contains **real school data, pseudonymized**. The mapping
below is the only key you need:

- `T01`..`T31`: teachers (current roster first, then references to the
  previous year's roster).
- `G01`..`G14`: student groups, ordered as in the seed.
- `FA..FD`: course-family codes used standalone in prose and file names.
- `R01`..`R18`: rooms.
- `C01`..`C14`: ministry course codes.
- `maintainer`: the repository maintainer; `referente`: the school contact.
- Hosts appear as `fedora-box`, `edge-box`, `laptop`.
- The campaign token `spalmone` appears in directory names and is kept
  as-is: it names the project, not the school.
- Dates are the real school-year calendar; they carry no direct identifier.

The full original-to-code mapping is not published.

## 1. The problem, and the golden timetable as an oracle

An Italian vocational school (fashion, hairdressing and technical tracks,
14 student groups, 26 teachers, 174 school days, 8 slots a day) had been
producing its weekly timetable by hand for years. The hand-made solution,
called the *golden timetable* in this repository, is a complete, valid
school year of 10,542 placed hours (the raw export holds more rows: the
documented calendar traps, placeholder rows and off-grid internship hours
are filtered before any comparison). The engineering goal was never "a
plausible timetable": it was a timetable the school would accept, which in
practice means one structurally comparable to the golden one.

That single decision shaped everything. The golden timetable became the
project's oracle:

- every constraint was calibrated against what the golden actually does,
  not what a regulation says it should do (see the constraint tables in
  [CLAUDE.md](CLAUDE.md) and the full recalibration history in
  [constraint-recalibration-history.md](claudedocs/constraint-recalibration-history.md));
- every input number that contradicted the golden was treated as an input
  bug, not a solver demand (the "golden-first on inputs" directive, with
  divergences reported to the school for confirmation a posteriori);
- a run is judged complete only when its per-group sanity table matches the
  golden's shape, not when an aggregate score looks good.

The lesson to export first: **when a domain expert's artifact exists,
make it the metric before making anything else.** Weeks were lost early by
optimizing against synthetic expectations that the school never had.

## 2. Golden-first calibration: constraints as a conversation with the oracle

The constraint set is the story of that conversation. A few examples, all
documented with the evidence that moved them:

- **Demotions.** Same-day same-subject adjacency started as a hard
  constraint; the golden itself splits such blocks 98 times, so it became a
  medium-weight preference (`M15`). The same fate hit teacher-room
  stickiness (`H16` to `M37`/`S16`) and afternoon-slot bans (`H21` to
  `M28`): rules stricter than the school's own practice were demoted until
  the golden itself could pass them. Source: the demotion ledger in
  [constraint-recalibration-history.md](claudedocs/constraint-recalibration-history.md).
- **Promotions.** The opposite also happened: "every scheduled lesson must
  have a room" was a medium-tier penalty (`M36`) until inspection showed
  the search was arbitraging it away; it was promoted to hard (`H29`)
  together with a re-weighting that made de-scheduling strictly worse than
  leaving a lesson room-less.
- **Plans, not just rules.** A second-order discovery: a pre-assignment
  plan derived from the golden must never be *more rigid* than the golden's
  own placement practice (per-teacher weekday distributions, anchor
  frequencies). A mechanical check
  ([placement-parity-check](scripts/analyze/placement-parity-check.py))
  now runs before any pinning mechanism is adopted.

The pattern to steal: **constraints are hypotheses; the oracle runs the
experiments.** Every recalibration above is backed by a measured count on
the golden, and most of them were ratified only after an A/B run on the
real seed.

## 3. The solver: five phases and a delivery chain

The production solve is not one optimizer. It is a staged pipeline
([solver-phases.md](claudedocs/solver-phases.md)):

1. a custom construction heuristic that first builds a weekly template per
   group and stamps it across the year;
2. local search on time slots;
3. a chain of deterministic repair passes (hole repair, anchor repair,
   surplus rebalance, quota fill, exact completion of the boxed tail:
   lessons construction left unassigned);
4. room assignment;
5. local search on rooms.

Around that core, an orchestration layer grew: multi-start with GRASP
(greedy randomized adaptive search procedure) construction randomization
(retry until a 0-hard attempt, pick the best), and, at the
end of every attempt, a **delivery chain** of deterministic finishers
(CompositeSwapFinisher, EmptyDayFinisher with its relay family,
HoleRelayFinisher, ViolationScopedSteepestFinisher) that each target a
specific family of residual violations under an improve-or-noop oracle.
Each finisher was born from a measured residual class, adopted by default
with a standard opt-out flag, and ratified only after paired A/B evidence.
The architecture is written up in
[orchestration-layer-architecture.md](claudedocs/orchestration-layer-architecture.md).

The most instructive finisher story is the one that failed:
CompositionHoleCloser was implemented, verified correct, and then proven
*useless on the known basin* (the region of search space a given
construction lands in) because every "fix" just relocated the hole
(the shell game). It stayed default-off for months as a correct building
block, until the endgame bundle adopted it default-on as pure bounded no-op
insurance; the real cure for that residual class was choosing a different
basin (multi-start), not a smarter local move. Failure file:
[task481-results](claudedocs/spalmone-2026-27/task481-results/)
contains the fork analysis.

## 4. How the work was actually done: agents, gates, and honest evidence

This repository was mostly written by AI agents under the direction of one
maintainer. The workflow those agents followed is part of the artifact:

- **Snapshot-first measurement.** Full solve cycles are the adoption gate,
  never the research tool. Candidates iterate on captured checkpoints and
  replay benches (the [localized testing
  guidelines](claudedocs/localized-testing-guidelines.md) define which test
  chain matches which change class). The bottleneck of this project was
  never solve time; it was the habit of paying for it unnecessarily.
- **Pre-registered A/B.** Every adoption gate predicts its outcome in
  writing before the arms run, with criteria a reviewer can check; the
  verdict files under each `task*-results` directory are the receipts (a
  worked example:
  [PREDIZIONE.md](claudedocs/spalmone-2026-27/task613-results/ab-gate/PREDIZIONE.md)
  and its [VERDICT.md](claudedocs/spalmone-2026-27/task613-results/ab-gate/VERDICT.md)).
- **Blind puzzles with Java arbitration.** Repeatedly, the agents were
  handed a real residual state with the score hidden and asked to fix it by
  hand ("blind puzzle"). The hand moves then became the specification for
  the next finisher, and a Java arbitrator confirmed the score afterwards.
  The `HoleRelayFinisher` lineage is the canonical example: round one's
  forty hand moves became the specification of a first mechanization, which
  round two's twenty-one moves then falsified while exposing the missing
  teacher-awareness, and the shipped pass converged (zero moves needed) on
  round three
  ([round-3 report](claudedocs/spalmone-2026-27/task613-results/puzzle-round3/ROUND3-BLIND-REPORT.md)).
- **Ratification with default-on opt-out flags.** Every adopted mechanism
  ships as a named flag, `null/absent -> enabled`, only the literal
  `false` disables it, with a startup banner (the full flag census is
  [knobs.md](claudedocs/knobs.md)). Regressions are readable at a glance,
  and a ratification never depends on remembering a flag.
- **Bounded no-op adoption.** Passes that provably cannot worsen the hard
  score (improve-or-noop oracle) get adopted even when they are no-ops on
  the known basin: on a different GRASP basin they may fire, and the
  oracle guarantees the insurance is free.
- **Sanity gate over aggregates.** No verdict without the per-group sanity
  table (minimum hours per day vs the golden, internal holes, roomless
  lessons:
  [solution-sanity-check.py](scripts/analyze/solution-sanity-check.py)).
  An aggregate score can hide eleven unacceptable 2-hour days behind a good
  average; that exact failure is why the gate exists. The distilled
  operational form of this whole loop is the
  [zero-hard-loop skill](.claude/skills/zero-hard-loop/SKILL.md).

The backlog directory of this repository (`backlog/`) is the uncurated work
journal: 700+ task files with plans, evidence, review findings, and honest
closure notes, including the closed-as-wrong ones.

## 5. Honest failures

A methodology story that only lists wins is marketing. These are the
mistakes the campaign kept:

- **The shell game** (see above): a net-hard-negative cascade that looked
  like a fix because an unrelated fill paid for the relocated hole. The
  guard that came out of it, verify-the-target-not-the-net, is now in
  every finisher's contract.
- **Evaporated evidence.** One mechanism was ratified on an A/B whose
  treatment arm ran a stale build; the effect vanished on re-run and the
  ratification was reverted the same day. Since then, arm freshness is a
  gate criterion.
- **The disarmed predictor.** An early-doom heuristic (discard
  constructions with more than 150 holes) was falsified by a paired
  experiment: a discarded 209-hole construction walked the full pipeline
  and delivered a residual of three hard violations in 72 minutes, better
  than the deterministic control. The predictor had silently degenerated
  the multi-start to a single attempt. It is now default-off with the
  falsification recorded
  ([near-miss report](claudedocs/spalmone-2026-27/task677-results/nearmiss/REPORT.md)).
- **Metric regressions that hid behind process.** A constraint weight knob
  sweep once "improved" a KPI while the underlying timetable did not move:
  the lesson (weights move the score, not the schedule) is recorded in
  [REJECTED.md](REJECTED.md).

## What this repository is, and is not

It is a working Quarkus + Timefold application with the real (pseudonymized)
school data, a full constraint set calibrated against a real hand-made
solution, the orchestration layer, the analysis scripts, and the complete
work journal. It is not a template: the constraint semantics are this
school's, the tuning is single-dataset (several A/B arms are n=1 or n=2,
declared as such in the verdicts), and the wall-clock economics assume a
patient operator. [LIMITS.md](LIMITS.md) enumerates the boundaries;
[REJECTED.md](REJECTED.md) indexes the roads not taken.
