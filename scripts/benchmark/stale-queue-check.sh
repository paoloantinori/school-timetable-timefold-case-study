#!/usr/bin/env bash
# stale-queue-check.sh - the anti-inertia gate for in-flight measurement cycles
# (maintainer, 8/09: "ferma le code che sopravvivono per inerzia, alla mattina come
# in qualunque momento"). Precedents: 6/09 (zai's coda sospesa), 8/09 (the
# edfh33 cycle ran half a morning measuring an arm whose reading already
# existed via twin-tail, while the newly-found cure sat unmeasured).
#
# The mechanical proxy for "new evidence landed since the cycle started":
# commits touching src/main/java or src/main/resources after the cycle's
# launch commit. If any exist AND the in-flight cycle does not measure one of
# them (its own flags/classes are not in that diff), the cycle's misurando is
# STALE by default: the monitor proposes stop + replace, and the owner
# arbitrates. This script prints the verdict; it never stops anything itself.
#
# Usage:
#   stale-queue-check.sh <cycle-launch-commit> [cycle-flag-substring]
# Example:
#   ./scripts/benchmark/stale-queue-check.sh 540d4702 box-deficit-only
set -euo pipefail
CD_ERR=0
LAUNCH="${1:?usage: stale-queue-check.sh <cycle-launch-commit> [cycle-flag-substring]}"
NEEDLE="${2:-}"
git merge-base --is-ancestor "$LAUNCH" HEAD 2>/dev/null || { echo "STALE-QUERY-ERROR: $LAUNCH is not an ancestor of HEAD"; exit 2; }

COUNT=$(git log --oneline "$LAUNCH"..HEAD -- src/main/java src/main/resources | wc -l | tr -d ' ')
if [[ "$COUNT" -eq 0 ]]; then
    echo "QUEUE-FRESH: 0 code commits since $LAUNCH - no new evidence, the cycle keeps its mandate."
    exit 0
fi

echo "EVIDENCE-SHIFT: $COUNT commit(s) under src/ since the cycle launched ($LAUNCH):"
git log --oneline "$LAUNCH"..HEAD -- src/main/java src/main/resources | head -5 | sed 's/^/  /'
if [[ -n "$NEEDLE" ]]; then
    if git log --oneline "$LAUNCH"..HEAD -- src/main/java src/main/resources | grep -qi "$NEEDLE"; then
        echo "CYCLE-RELEVANT: the diff contains '$NEEDLE' - the cycle may be measuring the new evidence itself."
        echo "Verdict: CONFIRM the cycle's question still stands, then let it run."
        exit 0
    fi
fi
echo "STALE-QUEUE: the in-flight cycle measures none of the new evidence."
echo "Verdict: READ THE LISTED COMMITS and classify each: a diagnostic guard, a doc"
echo "or a refactor that does not touch the measured mechanism is a FALSE POSITIVE"
echo "(first case: 74a7974a, cancel-path NPE guard, 8/09) - override with the reason."
echo "Only behavior-relevant evidence justifies proposing STOP + REPLACE to the owner."
exit 1
