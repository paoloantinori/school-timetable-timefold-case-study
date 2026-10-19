# AI-driven experimentation on a Timefold project

This is the working method behind this repository: how an AI agent (or a small fleet of
them) can explore, measure and improve a constraint-solver model without burning days on
guesswork. It was distilled from a real campaign on a school timetabling problem
(~12,500 lessons, 58 constraints, a 5-phase solver pipeline), and every rule below exists
because breaking it cost us something concrete. The examples are real and the numbers are
the ones we measured.

It is written for two audiences: humans who want to understand how the project is run, and
coding agents that get pointed at this repo and need to know what "good" looks like here.

---

## The five rules, in one screen

1. **A human-made reference solution outranks every opinion, including the humans'.** If the
   reference does something, the model may do it too. Measure the reference before asking
   anyone a question.
2. **Never iterate on live solves.** Find the shortest test that produces the verdict and
   iterate there. A full solve is a one-time certification, not a development loop.
3. **The score is not the reality.** Constraints can be silent on unassigned variables, so a
   clean score can hide a broken solution. Count entities, not just points.
4. **Kill the idea before building it.** Most design ideas can be refuted in a minute against
   the reference data. Do that first.
5. **Nothing gets adopted by the machine.** Behaviour changes land behind default-off flags,
   with evidence; a human ratifies.

---

## 1. The reference solution is the specification

Optimisation projects usually start from a document nobody can fully verify. If the domain
already has a hand-made solution that its owners consider valid, that artifact is worth more
than the document: it is a *complete, consistent, feasible* example of what "acceptable"
means, including all the exceptions nobody wrote down.

Concretely, in this repo the school's own timetable (`claudedocs/spalmone-2026-27/data/lessons.csv`)
is the reference, and it drives three practices.

**Never be stricter than the reference.** Before promoting any rule to hard, count how often
the reference violates it. Example: we were one commit away from adding a hard constraint
"lab lessons requiring the same room-tag must not overlap in a timeslot". The reference
violates it 242 times. The constraint would have made the school's own timetable infeasible.
Cost of finding out: one minute of Python, zero solver runs.

**Derive answers instead of asking.** People are slow and expensive to interrupt, and they
often cannot articulate constraints they follow unconsciously. We recovered from the
reference: which teachers work fixed weekdays (8 of them, none of it written in the
availability sheet), whether afternoons are usable, whether classes finish early or spread
to June, whether a subject is taught in a workshop or a classroom. Each derivation becomes a
*yes/no confirmation* for the humans instead of an open question, which is an order of
magnitude cheaper for them to answer.

**Let the reference arbitrate model disputes.** When two of our rules disagreed about room
continuity, the deciding argument was that the reference does not record rooms at all, so it
cannot defend a hard room-continuity constraint, while "every class must have a room" is
physically non-negotiable. The tier order followed from that.

A caveat that matters: the reference is authoritative about *practice*, not about *data
quality*. When the reference and a planning sheet disagree, that is a finding to surface, not
a conflict to silently resolve.

---

## 2. The measurement pyramid

The central discipline. Every change type has a cheapest test that yields its verdict; using
anything more expensive is waste, and using a full solve as a development loop is the classic
failure mode of solver projects.

| Level | Instrument | Runtime | Answers |
|---|---|---|---|
| L0 | `ConstraintVerifier` unit tests | seconds | does this constraint fire exactly where it should? |
| L1 | Regression guard against the reference solution | ~10 s | does this change make the human timetable illegal? |
| L1 | Offline KPI scripts on a lessons dump | seconds | shape metrics: fill, day-start, recurrence, cadence |
| L2 | Snapshot/replay harnesses on real captured states | 40-90 s | does the phase behave on a real, hard state? |
| L3 | Full unit suite | minutes | nothing broke elsewhere |
| L4 | Short live solve | 10-40 min | trend only, never adoption evidence |
| L5 | Full A/B solve, twice | hours | the only adoption gate |

Two rules make the pyramid work.

**The development loop is the lowest level that measures the verdict.** When we suspected a
systemic defect where one teacher's 231 hours ended up unplaceable, the verdict was
observable at the end of the construction phase: a two-minute harness, not a two-hour solve.
We iterated there and the live run happened once, at the end, as certification.

**Live runs do not get repeated.** Before launching one, ask whether known upcoming work will
invalidate it. We once launched a 2-hour certification while an architectural change was
queued that would have forced a re-run: the right move was to fold the certification into the
A/B that the architectural change needed anyway.

Corollary for agents: "this change needs validation" is never a reason to defer work. It is
the beginning of the question *which level validates it today*.

---

## 3. Snapshot replay: how to get a solver verdict in seconds

This is the technique that makes the pyramid possible on a solver project, and it is worth
copying wholesale.

Every expensive run leaves a full artifact set (`scripts/benchmark/capture-run.sh`): the score,
the per-constraint analysis, the phase progression, a **complete solution export**, a flat
lessons dump, the KPI battery output, and HTML comparators. Two things then become possible:

- **Replay a phase in a unit test from a real state.** `RoomPhaseSnapshotTest` loads a real
  export (including entities whose room is genuinely null) and re-runs only the room phases in
  90 seconds. That is how we measured, without a single live solve, that demoting a constraint
  from hard to medium moved 283 of 291 room-less lessons into rooms.
- **Ask counterfactuals in memory.** The same harness can mutate the fixture before solving:
  strip a tag from two subjects, give a zero-capacity room a real capacity, change a weight.
  We answered "how many lessons would a data fix rescue?" (85 for one subject, 18 for another,
  72 for the pair we were authorised to change) without touching a single protected data file.

Design implications: make your capture script *canonical* (one script, used by everyone, so
artifacts are comparable), keep a fixture loader that can rebuild solver state from a dump,
and gate slow harnesses behind environment variables so the default suite stays fast.

---

## 4. The score is not the reality

The most expensive mistake of the campaign, and the one most specific to constraint solvers.

We ran a certification, and the room-related hard constraints reported **zero violations**.
The verdict said the architecture worked. It did not: 12,259 of 12,490 placed lessons had **no
room at all**. Every room constraint was silent on a null room, so "no violations" was
achieved by assigning nothing. The solver was behaving rationally: assigning a room could only
add penalty, leaving it null cost nothing.

Three lessons follow.

**Measure the population, not the score.** Every acceptance criterion should name a count of
entities in a state, verified from the exported solution: "unpinned lessons of a real teacher
with no room = 0", not "room constraints = 0".

**A silent constraint is not a satisfied constraint.** Before trusting a zero, ask what makes
it zero: satisfaction, or absence of the thing it constrains?

**Verify claims on raw artifacts, never on summaries.** All of the above was found by
recomputing from the export, contradicting the verdict document that summarised the same run.
Since then, the rule in this project is that whoever consumes a result recomputes the
load-bearing numbers from the raws.

---

## 5. Constraint tiers, weights, and arbitrage

Lexicographic scoring (hard/medium/soft) creates arbitrage opportunities that are invisible
until the solver finds them. Two real examples, both from the same week:

**Equal weights within a tier create escape hatches.** We promoted "an assigned lesson must
have a room" to hard, at the same weight as "a lesson must be assigned". The solver promptly
un-assigned 9,404 lessons: leaving a lesson unplaced cost exactly as much as leaving it
room-less, and unplacing it also escaped every medium penalty the placed lesson carried.
Emptying the timetable became an optimisation. The fix was weights, not tiers: unplaced costs
2, room-less costs 1, so un-assigning is strictly worse.

**Do the arbitrage arithmetic before changing a weight.** When we later wanted to raise a
medium constraint's weight, the ceiling was computable: a lesson can belong to two adjacent
pairs, so at weight 500 the two pairs would tie with the 1000-weight "must have a room" and
the trap would reopen. Write the inequality down before picking the number.

**And measure whether the weight is even the lever.** We swept that weight across 100, 200,
400, 800 and 1500. The count of undesired room changes stayed flat at ~106 the whole way: the
residue was forced by physical room availability, not by the penalty. Fifteen times the weight
bought exactly nothing. Sweep before you tune.

---

## 6. Timefold-specific traps worth knowing

**`forEach` vs `forEachIncludingUnassigned`.** `forEach` iterates only fully-initialised
entities. In a multi-phase pipeline where one variable is assigned in a later phase, every
constraint built on `forEach` is **blind** during the earlier phases. We had constraints that
silently allowed 6,343 stacked lessons because they were blind while the room variable was
still unset. Audit every constraint for which phases it is actually alive in; write the audit
down (this repo keeps a per-constraint census in the task backlog).

**`allowsUnassigned` variables make null a legal terminal value.** Combined with the above, a
constraint set can be perfectly happy with a solution that assigns nothing. If the domain says
the variable is mandatory, the model must say so with an explicit constraint, at a tier that
dominates the alternatives.

**Assert semantics with the real ScoreDirector, not by reasoning.** Whether a null-variable
entity is "initialised" is version-dependent behaviour. Our harness asserts the two relevant
constraints score exactly zero on an all-null grid, so a future Timefold upgrade that changes
the semantics breaks a test instead of silently corrupting a year of timetables.

**`ConstraintVerifier` cannot assert score tiers.** `penalizesBy` counts matches; it will not
tell you a constraint moved from hard to medium. Tier changes need a different guard (a
snapshot score breakdown, or an explicit assertion on the constraint's score in an integration
test).

**Deterministic custom phases beat stochastic search for structural repairs.** Local search is
bad at coordinated multi-move fixes (shifting a whole day one slot earlier requires N moves,
each intermediate state worse). Where the fix is deterministic, write a `PhaseCommand`: it is
testable in isolation, reproducible, and measurable with exact deltas. This repo has three
(hole repair, anchor repair, surplus rebalance), each with a fail-closed invariant that aborts
if the pass makes the score worse.

**Publish static facts before solving, never mid-solve.** Reference maps consumed by
constraints (targets, templates, pinned-hour tables) must be computed pre-solve. Mutating them
while the solver runs corrupts score calculation in ways that surface much later.

---

## 7. Aggregate rates hide structural defects

A percentage is the wrong instrument for a structural problem, and this took a human's eye to
catch: our "day-start anchoring" metric read 99.2%, which sounds finished. The 18 failures were
not noise: 15 of them were **the same class on the same weekday, fifteen consecutive weeks**.
For anyone reading the timetable, that is a broken weekly pattern; for the metric, it was a
rounding error.

The practice that came out of it: for every headline metric, also break it down along the
structural axes of the domain (here: group × weekday) and diff it cell-by-cell against the
reference. `scripts/analyze/day-start-anatomy.py` does exactly that and now ships in the
standard capture battery, so the shape is visible in every run, not just when someone
suspects something.

The generalisation: **report the distribution, not just the rate**, and always next to the
reference's own distribution. A defect the reference also has is a domain fact; a defect only
you have is a bug.

---

## 8. Negative results, delivered fast, are the point

An exploration workflow that only reports successes is a workflow that hides its cost. In one
night, the highest-value outputs were three refutations:

- the hard tag-capacity constraint, killed by the reference in one minute (242 counter-examples);
- the weight sweep, flat across a 15x range, which stopped a whole line of tuning;
- the repair-based fix for the weekly anchor, refuted by measuring the donor pool: 396 blocked
  by teacher availability, 159 unsafe to move, 111 legitimately excluded by the value-range
  filter. Zero legal donors, so the direction cannot work and the fix belongs in the
  construction phase instead.

Each cost minutes and saved days. The delivery format that makes this work is deliberately
tiny: **hypothesis, measured result, keep or discard**, three lines. Anything longer does not
get read; anything shorter is not evidence.

---

## 9. Orchestration: separate the executor from the verifier

The setup that produced this campaign: one agent on a workstation acting as **decider and
verifier** (holds the model, writes the mandates, recomputes every load-bearing number from
raw artifacts, talks to the human), and one agent on a bigger machine acting as **executor**
(runs long solves, implements, measures, never adopts). They communicate exclusively through
the git repository: mandates in, artifacts and verdicts out.

Why it works, and what it needs:

- **The verifier never trusts the executor's summary.** In this campaign the verifier caught a
  green verdict that was an artifact of a silent constraint, a root-cause analysis that had
  examined only a subset of the teachers, and stale numbers in a table header. None of these
  were sloppiness; they are the normal error rate of any agent working alone.
- **Everything durable goes through files.** Task backlog for decisions and evidence, capture
  directories for artifacts, status documents for the state of play. Any fresh session must be
  able to resume from files alone, because context windows end at the worst moment.
- **A watchdog on the executor.** An idle executor is a silent failure; so is a dead one. A
  periodic check (a cron in our case) reads the executor's terminal and the repository, and
  either consumes the finished step or escalates.
- **The machine never idles.** When the approved queue empties, the executor moves to
  exploratory spikes on hypotheses nobody approved yet, behind default-off flags. The
  bottleneck in this kind of project is human decision time, so the machine's job overnight is
  to produce a menu of measured options for the morning, not to consolidate what is already
  known.

---

## 10. Adoption discipline

The counterweight to all that autonomy, and the reason it is safe.

- **Every behaviour change lands default-off**, is measured, and is adopted only by explicit
  human ratification. The flag is what makes an experiment reversible and comparable.
- **Real-world input data is protected.** Files containing the domain's real data (here, the
  seed generator and the school's CSVs) cannot be modified without explicit permission, per
  change, in writing. Experiments that need different data simulate it **in memory inside the
  harness** and deliver a proposed patch, not an applied one.
- **Adoption is a package, not a drip.** Certification runs certify a set of changes together,
  and the acceptance criteria are written before the run, including the population counts.
- **Two runs, not one.** Local search is stochastic; a single run fixes one trajectory. The
  adoption evidence is two independent runs with the same configuration, and the verdict says
  explicitly whether the numbers reproduce.
- **Record the reversals.** When a decision gets overturned by evidence, the task keeps both
  the original reasoning and the refutation. Half of this document is reversals.

---

## Appendix: the artifact battery

What a costly run must leave behind, so that nobody ever has to re-run it to answer a
question:

- `score.txt`, `analysis.json` (per-constraint hard/medium/soft breakdown with match counts),
  `phases.json` (per-phase timings and scores), and the phase-boundary log lines;
- a **complete solution export** (compressed JSON), which is what makes snapshot replay and
  post-hoc population counts possible;
- a flat `lessons.txt` dump in a stable pipe format, which every KPI script consumes;
- the KPI battery output (fill, day-start plus its per-group-per-weekday anatomy, recurrence,
  cadence, hole topology, teacher fingerprints);
- an HTML comparator against the reference solution, for the human eye;
- a `VERDICT.md` written for a reader who was not there: what was asked, what was measured,
  what it means, and what is still open.

If an agent finds itself unable to answer a question about a past run, that is a gap in this
list, and the fix is to extend the battery, not to re-run the solve.
