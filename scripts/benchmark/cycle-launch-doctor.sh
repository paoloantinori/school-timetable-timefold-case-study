#!/usr/bin/env bash
# cycle-launch-doctor.sh - the post-launch verification checklist, mechanical.
# (maintainer, 8/09: "hai bisogno di ulteriori hook?" - the incident class of the
# day is the INCOMPLETE LAUNCH CONTRACT: three flag near-misses (PROFILE x2,
# checkpoint-capture x1), all mine, all caught minutes-to-hours late.)
#
# Run right after launching a measurement cycle (solve-staged / prod-start).
# It does not know what the launch SHOULD contain - the mandate knows - it
# makes the three facts impossible to miss:
#   1. the -D flags the running JVM ACTUALLY has (ps, not the command line you
#      think you typed)
#   2. the first banners of the cure/tracers in the log
#   3. the checkpoint capture directories (the iteration substrate; absence
#      after the first boundary = the 15:05 incident)
# Usage: cycle-launch-doctor.sh [laptop]
set -uo pipefail
TARGET="${1:-local}"

run() { if [[ "$TARGET" == "laptop" ]]; then timeout 40 ssh -o ConnectTimeout=15 -o ClearAllForwardings=yes laptop "$*"; else bash -c "$*"; fi; }

echo "== 1. I flag REALI sulla JVM =="
run 'ps aux | grep "[j]ava.*quarkus" | head -1 | tr " " "\n" | grep "^-D" || echo "NESSUNA JVM ATTIVA"' | sed 's/^/  /'

echo "== 2. Banner della cura/tracer (prime occorrenze nel log) =="
run 'grep -m1 "deficit-discipline holds" /tmp/planner-prod.log 2>/dev/null | tail -c 120; echo; grep -c "OVERQUOTA-TRACE" /tmp/planner-prod.log 2>/dev/null' | sed 's/^/  /'

echo "== 3. Cattura checkpoint (il substrato di iterazione) =="
# Both homes: the DEFAULT dir (no suffix: any launch without -Dplanner.checkpoint.dir)
# AND the profile-suffixed ones (zai's convention). The 15:05 lesson: the doctor
# originally looked only at the suffixed pattern and false-alarmed.
if [[ "$TARGET" == "laptop" ]]; then run 'ls -dt /Users/user/planner/staged-checkpoints* 2>/dev/null | head -3; ls -t /Users/user/planner/staged-checkpoints/ 2>/dev/null | head -2'; else ls -dt staged-checkpoints* 2>/dev/null | head -3; ls -t staged-checkpoints/ 2>/dev/null | head -2; fi | sed 's/^/  /'

echo "== LETTURA OBBLIGATORIA: confronta (1) col contratto del mandato =="
echo "== se un flag del mandato manca in (1): FERMA ORA, non aspettare la consegna =="
