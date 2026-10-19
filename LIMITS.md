# Limits

What this repository does not promise. Read together with
[STORY.md](STORY.md) and [REJECTED.md](REJECTED.md).

## Single-dataset tuning

Every constraint weight, every solver knob, and every adoption gate in this
repository was calibrated against **one school year of one school**. Several
A/B adoption arms are n=1 or n=2 per arm, declared as such in their verdict
files. The methodology (oracle-first calibration, pre-registered A/B,
improve-or-noop adoption) is the transferable part; the numbers are not.
Porting the constraint weights to another school without re-running the
calibration conversation would be cargo-culting.

## Seed-dependent outcomes

The same code and the same data deliver a *distribution* of outcomes, not
one answer: the randomized (GRASP) construction lands in different basins,
and the delivered hard score varies with the seed (the measured census:
about one construction in four fails to reach 0-hard on its own, which is
what the retry loop is for; see the
[bad-seed frequency verdict](claudedocs/spalmone-2026-27/task480-results/badseed-frequency/VERDICT-badseed-frequency-2026-08-03.md)).
Ratified verdicts declare live deltas that are basin variability rather
than mechanism effects where that is the honest reading, and even the
multi-threaded local search is explicitly not byte-exact. Reproduce with
fixed seeds before believing a delta.

## The golden has an expiry date

The core advantage of this dataset, a complete hand-made solution used as an
oracle, is also its scheduled death: in production the school supplies
inputs, not a reference solution. The architecture already treats
golden-derived runtime reads as declared inputs to be retired (the ledger
and the input-only directive are in [CLAUDE.md](CLAUDE.md)), and the
cadence references survive as declared workbook sheets. A fork of this
repository without a golden must expect to lose: the golden-oracle
calibration loop, the golden regression guard tests, and any mechanism still
reading golden-derived references.

## Version pins and fragile internals

Timefold Solver 2.7.0 (community) is pinned. The multi-threaded local search
decider wires itself into Timefold internals via reflection: that wiring is
documented in the code as fragile, verified across the 2.3.0 to 2.7.0 range,
and explicitly requiring a re-check on any upgrade. The benchmark harness
needed manual repair to run on the 2.3.0 generation. Upgrading is a
project, not a version bump.

## Wall-clock economics

A full staged solve runs roughly 25 to 90 minutes; a worst-case multi-start
(no attempt reaches 0-hard) is hours. The replay benches and snapshot
iteration exist
precisely because full cycles are expensive ([localized testing
guidelines](claudedocs/localized-testing-guidelines.md)). Budget accordingly:
this is not a sub-second optimizer.

## Machine-specific lessons, not requirements

Some workflow rules in the journal were learned on specific hardware (test
heap sizing, port conflicts, background-process hygiene). They are recorded
because they were load-bearing there; they are not requirements for building
or running the code (the test suite runs on a plain H2 in-memory database).

## Mixed-language documentation

The code, identifiers, and this front matter are in English; the campaign
journal (ratifications, verdicts, backlog) is mostly Italian. The verdict
files are the evidence layer: a reader who cannot read Italian can still
follow the numbers, the A/B tables, and the score pins, but the narrative
nuance lives in the original language. Translating 700+ task files was
judged to cost more than it buys.

## Agent-workflow coupling

The working journal assumes an AI-agent workflow with mandatory review gates
(`/simplify`, `/code-review`, a Definition of Done enforced per task in
[CLAUDE.md](CLAUDE.md) and the `.claude/` directory). A human-only team can
ignore that machinery; they should not mistake its conventions (the DoD
checklists in the backlog files, the gate markers in commit messages) for
application code.

## CI scope

Continuous integration (`.github/workflows/ci.yml`) builds the full package
(backend plus the React frontend via Quinoa, which needs Node at build
time) and runs the Java test suite on H2, with the test heap capped at 3g
to fit runner memory. It does NOT run the Playwright end-to-end suite,
and a green CI run proves the build and the unit/integration tests, not
that every interactive feature has been exercised. Running the suite
locally is `./scripts/core/test.sh`.
