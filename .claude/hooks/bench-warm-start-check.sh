#!/bin/bash
set -euo pipefail

# Bench warm-start check (maintainer, 8/09 sera: "voglio qualcosa di meglio di una
# promessa"): when a command runs one of the env-gated BENCH tests
# (PinDrainBench / BoxDeficitOnlyBench / EdfH33LiveState / SurplusHarvest*),
# remind to check whether the bench NEEDS a full construction or can start
# from an already-captured checkpoint. The rule:
#   - benches measuring CONSTRUCTION (journal, pin, shape, prebooking) MUST
#     reconstruct (their product is born during the phase: not warm-startable);
#   - benches measuring the LS or the repair tail SHOULD start from the closest
#     checkpoint (POST_CONSTRUCTION for LS, POST_B_ROOM for the tail) when one
#     exists from a recent cycle: the construction is then skipped entirely.
# A full-construction bench when a checkpoint would do costs ~20 wasted minutes
# per arm (8/09: every bench rebuilt from zero while the cycles' checkpoints
# sat free on the laptop).

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

if echo "$command" | grep -qE 'PLANNER_TEST_(PIN_BENCH|BOX_BENCH|PAIR_CELLS)'; then
  cat <<'EOF'
{
  "reminder": "BENCH-WARM-START-CHECK (8/09, la promessa diventa hook): questo banco misura la COSTRUZIONE? Allora deve ricostruire (il suo prodotto nasce durante la fase). Misura la LS o la CODA di repair? Allora VERIFICA prima se esiste un checkpoint fresco: POST_CONSTRUCTION per la LS (salta la costruzione), POST_B_ROOM per la coda (usa snapshot-tail.sh -l). Un banco a costruzione piena quando bastava un checkpoint costa ~20 min/braccio buttati. Strumento: ./scripts/benchmark/current-cycle-checkpoints.sh [laptop] [data-ora] per elencare i checkpoint freschi del ciclo in corso."
}
EOF
  exit 0
fi

exit 0
