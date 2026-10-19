#!/usr/bin/env bash
# verify-baseline.sh {seed|import} — deterministic ground-truth gate for
# benchmark/validation runs. Companion to scripts/benchmark/laptop-RUNBOOK.md.
#
# Replaces the hand-derivation of the canonical baseline numbers every executor
# otherwise re-does (and can get wrong): the 866 MODA lab-skeleton in particular
# is NOT a number import-calendario prints — it is total_pinned - stage_pinned,
# and even that only works because G09's 292 stage pins fold into the MODA-
# group total (1158 = 866 lab + 292 stage). This turns the runbook's prose gate
# ("MUST report 866 MODA + 1800 stage + 280 off-grid") into a machine assertion.
#
# Two modes, each a distinct moment in the runbook's per-arm cycle:
#   verify-baseline.sh seed     AFTER fresh start, BEFORE import-calendario
#   verify-baseline.sh import   AFTER import-calendario, BEFORE pre-assign
#   (the solver never moves pinned lessons, so the import gate also holds
#    post-solve — safe to re-run any time after import.)
#
# Expected values reflect the 2026/27 seed (CLAUDE.md seed volumes). If the seed
# changes, a failing assertion is the intended signal — then update it here.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
Q() { "$ROOT/scripts/db/db-query.sh" -f json "$1" | tail -1; }
MODE="${1:-}"
ok() { # name expected actual
  if [ "$2" = "$3" ]; then echo "  OK   $1 = $3"
  else echo "  FAIL $1: expected $2, got $3" >&2; exit 1; fi
}

case "$MODE" in
  seed)
    echo "Seed ground-truth gate (post-start, pre-import):"
    line="$(Q "SELECT (SELECT count(*) FROM timeslot),(SELECT count(*) FROM teacher),(SELECT count(*) FROM studentgroup),(SELECT count(*) FROM course),(SELECT count(*) FROM lesson),(SELECT count(*) FROM dailyactivityquota),(SELECT count(*) FROM room)")"
    IFS='|' read -r ts tc sg co le dq rm <<<"$line"
    ok timeslot           1376 "$ts"
    ok teacher              27 "$tc"
    ok studentgroup         14 "$sg"
    ok course              273 "$co"   # CLAUDE.md documents 268 incarichi + 7 stage = 275; seed produces 273 (2-row drift, deterministic)
    ok lesson            12581 "$le"   # the 12,581-lesson problem size cited by the ruin 5-20 rationale
    ok dailyactivityquota 2393 "$dq"
    ok room                 18 "$rm"
    ;;
  import)
    echo "Import ground-truth gate (post-import, pre-pre-assign):"
    line="$(Q "SELECT (SELECT count(*) FROM lesson l JOIN course c ON l.course_id=c.id WHERE l.pinned AND c.subject='Stage'),(SELECT count(*) FROM lesson l JOIN course c ON l.course_id=c.id JOIN studentgroup g ON c.studentgroup_id=g.id WHERE l.pinned AND g.name LIKE '%MODA%' AND c.subject<>'Stage'),(SELECT COALESCE(sum(c.expectedunscheduledhours),0) FROM course c WHERE c.subject='Stage')")"
    IFS='|' read -r stage moda offgrid <<<"$line"
    ok "stage pins"        1993 "$stage"   # 8 stage-eligible groups (TASK-434 added G04), all stage lessons pinned
    ok "MODA lab skeleton"  866 "$moda"    # = total_pinned(2859) - stage(1993); the non-obvious one
    ok "stage off-grid h"   327 "$offgrid" # = sum(Course.expectedUnscheduledHours) for stage; off-grid closure/Saturday hours (TASK-434 G04 +~47)
    ;;
  *)
    echo "Usage: $0 {seed|import}" >&2
    echo "  seed   — run AFTER fresh start, BEFORE import-calendario" >&2
    echo "  import — run AFTER import-calendario, BEFORE pre-assign" >&2
    exit 2 ;;
esac
echo "All $MODE ground-truth assertions passed."
