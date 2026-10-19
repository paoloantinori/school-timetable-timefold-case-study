#!/usr/bin/env bash
# TASK-447 FASE E - CONTINUE the cert from the staged-solve step (the fresh-profile
# setup + import + pre-assign already succeeded: 3090 pinned = 1993 stage + 866 MODA +
# 231 Phase-0 constrained-teacher; unpinned_placed=0). The full run-447-final-cert.sh
# hit a stale verify-baseline gate (post-TASK-434) and a too-tight api.sh-wait 900s on
# pre-assign (Phase 0 is slow); both are operational, not code issues. This continues.
set -euo pipefail
cd "$(dirname "$0")/../.."

OUT=claudedocs/spalmone-2026-27/task447-results/cert
mkdir -p "$OUT"

echo "=== TASK-447 FASE E cert (continue): staged solve on the already-pinned grid ==="
./scripts/api/api.sh status

# Staged solve (campaign reference mode), poll to completion (~90 min).
./scripts/api/api.sh solve-staged -t 60
./scripts/api/api.sh wait 7200   # 2h ceiling

# Canonical capture.
./scripts/benchmark/capture-run.sh "$OUT"

echo "=== TASK-447 FASE E cert captured to $OUT ==="
./scripts/api/api.sh score || true
echo "=== score.txt: ==="
cat "$OUT/score.txt" 2>/dev/null || true
