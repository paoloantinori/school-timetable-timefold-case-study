#!/usr/bin/env bash
#
# run-honest-ab.sh — TASK-386 fallback protocol driver.
#
# WHY THIS SCRIPT EXISTS: the offline Timefold benchmarker (PlannerBenchmarkFactory
# + solverBenchmarkConfig*.xml, see claudedocs/research/benchmark-mechanism-audit-2026-07-21.md)
# is NOT a faithful stand-in for the current 6-phase pipeline: its input file
# (local/data/schedule-production.json) does not exist, and even if regenerated,
# SchoolScheduleSolutionFileIO.read() never populates the GroupCalendarRule /
# DailyActivityQuota / MRV statics that DayClusteredConstructionPhase and
# H20/M18/M20 depend on (GroupCalendarRule isn't even a field on the SchoolSchedule
# solution class, so it cannot round-trip through the exported JSON at all).
# The audit's recommendation is a SEQUENTIAL LIVE A/B protocol instead: one arm
# per invocation, against the real app/DB, using the exact same 6-phase pipeline
# that runs 12-16 used.
#
# THIS SCRIPT ITSELF NEVER TOUCHES THE LIVE APP/DB. It only:
#   1. Generates one arm's solverConfig.xml variant under local/benchmark-arms/
#      (via generate-benchmark-arm.py, which reads the CURRENT
#      src/main/resources/solverConfig.xml as its source of truth and changes
#      ONLY phase 2's acceptor/forager/pillar-selectors + secondsSpentLimit).
#   2. Validates the generated file against Timefold's solver.xsd.
#   3. Prints the manual apply/rebuild/run/restore procedure for the ORCHESTRATOR
#      to execute by hand (or via its own scripted cycle) - rebuilds, planner.sh,
#      api.sh and db-query.sh calls are the orchestrator's job, never this script's,
#      per the project's "single DB actor" rule (only one process may touch the
#      live app/DB/solverConfig.xml at a time - a live solve is running elsewhere).
#
# Usage:
#   ./scripts/benchmark/run-honest-ab.sh <ARM>      # ARM in A B C D E F
#   ./scripts/benchmark/run-honest-ab.sh --all      # generate+validate all 6, print the full run order
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ARM_DESC_A="control: entityTabu 7 / acceptedCountLimit 1000 (today's live config)"
ARM_DESC_B="lateAcceptanceSize 400 instead of entity tabu"
ARM_DESC_C="entityTabu 7 / acceptedCountLimit 500 (narrower forager)"
ARM_DESC_D="entityTabu 7 / acceptedCountLimit 2000 (wider forager)"
ARM_DESC_E="entityTabu 11 (longer tabu memory) / acceptedCountLimit 1000"
ARM_DESC_F="entityTabu 7 / acceptedCountLimit 1000, pillar moves removed (change/swap only)"

usage() {
  echo "Usage: $0 <A|B|C|D|E|F|--all>" >&2
  echo "  A: $ARM_DESC_A" >&2
  echo "  B: $ARM_DESC_B" >&2
  echo "  C: $ARM_DESC_C" >&2
  echo "  D: $ARM_DESC_D" >&2
  echo "  E: $ARM_DESC_E" >&2
  echo "  F: $ARM_DESC_F" >&2
  exit 1
}

[[ $# -eq 1 ]] || usage
ARM="$1"

OUT_DIR="local/benchmark-arms"

print_procedure() {
  local arm="$1"
  local cfg="$OUT_DIR/solverConfig-arm-${arm}.xml"
  cat <<PROC

============================================================
 Arm $arm — manual live-A/B procedure (orchestrator runs this, NOT this script)
============================================================
Generated config: $cfg

  1. Back up the live config:
       cp src/main/resources/solverConfig.xml /tmp/solverConfig.xml.bak-before-arm-${arm}

  2. Apply the arm (ONLY after confirming no other solve/build is in flight —
     this project's single-DB-actor rule):
       cp $cfg src/main/resources/solverConfig.xml

  3. Rebuild + restart:
       ./scripts/core/rebuild.sh

  4. Run the standard experiment protocol (reset -> pre-assign -> solve to
     self-termination -> scorecard), same script runs 12-15 used:
       ./scripts/benchmark/run-experiment.sh arm-${arm} "TASK-386 honest metaheuristic arm ${arm}: <fill from --all output>"
     (expect roughly the phase-2 budget (3600s) + phases 1/3/4/5's own budgets,
      i.e. the construction, the compaction and the two room phases;
      budget ~90 min wall clock per arm per the task's own estimate)

  5. Restore the live config before starting the next arm or resuming normal work:
       cp /tmp/solverConfig.xml.bak-before-arm-${arm} src/main/resources/solverConfig.xml
       ./scripts/core/rebuild.sh

  6. Record the scorecard path printed by run-experiment.sh (local/experiments/arm-${arm}-*.json)
     for the cross-arm comparison.
PROC
}

if [[ "$ARM" == "--all" ]]; then
  echo "Generating + validating all 6 arms..."
  python3 scripts/benchmark/generate-benchmark-arm.py --all --out-dir "$OUT_DIR"
  echo ""
  echo "Validating against Timefold solver.xsd..."
  bash scripts/validate/validate-solver-xml.sh "$OUT_DIR"/solverConfig-arm-*.xml
  echo ""
  echo "============================================================"
  echo " Recommended sequential run order (single DB actor, ~90 min/arm)"
  echo "============================================================"
  cat <<ORDER
  1. A (control)       - $ARM_DESC_A
     Baseline; must be run even though it matches today's config, because the
     phase-2 budget here is pinned to 3600s (not run 16's 5400s experiment) so
     every arm is comparable on the SAME budget as runs 12-15.
  2. E (tabu 11)        - $ARM_DESC_E
     Cheapest structural variant of the control (same move mix, same forager).
  3. C (acceptedCountLimit 500)  - $ARM_DESC_C
  4. D (acceptedCountLimit 2000) - $ARM_DESC_D
     C and D bracket the control's forager width; run together to read the
     acceptedCountLimit sensitivity in one pass.
  5. F (pillar moves removed)    - $ARM_DESC_F
     Structural move-mix change; run after the tabu/forager sweep so any
     improvement can be attributed to move mix, not confounded with an
     already-tested acceptor/forager change.
  6. B (late acceptance 400)     - $ARM_DESC_B
     Most different acceptor family; run last so its interpretation benefits
     from having the tabu-family sweep (A/C/D/E) as a baseline.

  Total: 6 runs x ~90 min = ~9 hours wall clock if run back-to-back. Consider
  splitting across sessions; NEVER run two arms concurrently against the same
  DB (single-actor rule) and never start an arm while another solve is active.
ORDER
  for arm in A B C D E F; do
    print_procedure "$arm"
  done
  exit 0
fi

case "$ARM" in
  A|B|C|D|E|F) ;;
  *) usage ;;
esac

echo "Generating arm $ARM..."
python3 scripts/benchmark/generate-benchmark-arm.py --arm "$ARM" --out-dir "$OUT_DIR"

echo ""
echo "Validating against Timefold solver.xsd..."
bash scripts/validate/validate-solver-xml.sh "$OUT_DIR/solverConfig-arm-${ARM}.xml"

print_procedure "$ARM"
