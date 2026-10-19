#!/usr/bin/env bash
# Canonical post-solve artifact capture (spalmone campaign, 2026-07-24 —
# consolidates the historical task406/task410/task414-results copies).
#
# Run AFTER a solve completes on the dev profile. Captures score, analysis,
# phase progression, export, a lessons.txt dump, the full KPI battery
# (daily-fill, day-start, resemblance, moda-knot, cadence, fingerprint
# sharpness, pair-stability), the golden-vs-run HTML comparator, the Q22 teacher day-of-week
# fingerprint, and phase-marker log lines - everything an A/B verdict needs,
# in one place.
#
# Usage: ./scripts/benchmark/capture-run.sh <output-dir>
#   e.g. ./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/taskNNN-results/valNNN-run1
# <output-dir> is created if missing; may be relative (resolved against the
# repo root) or absolute.
#
# History: this consolidates 3 near-duplicate per-task copies
# (claudedocs/spalmone-2026-27/task{406,410,414}-results/capture-run.sh),
# each hand-copied and re-tweaked at the start of its own A/B cycle. Those
# copies are historical instances of a run, kept for provenance - they are
# NOT templates to re-copy. New capture cycles use THIS script.
set -euo pipefail

OUT_ARG="${1:?usage: capture-run.sh <output-dir>}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

case "$OUT_ARG" in
  /*) OUT="$OUT_ARG" ;;
  *)  OUT="$ROOT/$OUT_ARG" ;;
esac
mkdir -p "$OUT"
RUN="$(basename "$OUT")"

echo ">> capturing into $OUT"

# Score + analysis + phase progression + export (all via the whitelisted api.sh)
./scripts/api/api.sh score               > "$OUT/score.txt"
./scripts/api/api.sh analysis -j -t 60   > "$OUT/analysis.json"
./scripts/api/api.sh phase-progression -t 60 > "$OUT/phases.json"
# api.sh export writes the export to its ARGS[0] path and prints a status line to
# stdout; redirecting stdout captured the status line instead of the export.
./scripts/api/api.sh export "$OUT/export.json" -t 120
if [ -s "$OUT/export.json" ]; then gzip -f "$OUT/export.json"; fi

# Lessons dump (the exact query daily-fill-kpi.py's docstring specifies), then the
# KPI battery that consumes it.
./scripts/db/db-query.sh "SELECT g.name || '|' || ts.startdatetime::date || '|' || extract(hour from ts.startdatetime) || '|' || c.subject || '|' || t.name || '|' || l.pinned FROM lesson l JOIN course c ON l.course_id=c.id JOIN studentgroup g ON c.studentgroup_id=g.id JOIN teacher t ON c.teacher_id=t.id JOIN timeslot ts ON l.timeslot_id=ts.id" > "$OUT/lessons.txt"

python3 scripts/analyze/daily-fill-kpi.py  "$OUT/lessons.txt" | tee "$OUT/kpi-fill.txt"
python3 scripts/analyze/day-start-kpi.py   "$OUT/lessons.txt" | tee "$OUT/kpi-daystart.txt"
# day-start-anatomy: same population as day-start-kpi/M18 split per (group, weekday) vs golden,
# flags cells with scarto >= 3 (TASK-450). HTML artifact alongside the other KPI outputs.
python3 scripts/analyze/day-start-anatomy.py "$OUT/lessons.txt" --html "$OUT/day-start-anatomy.html" | tee "$OUT/kpi-daystart-anatomy.txt" || true
python3 scripts/analyze/resemblance-kpi.py "$OUT/lessons.txt" | tee "$OUT/kpi-resemblance.txt"
# pair-stability (TASK-575/576 opzione C, ratificata da maintainer 31/08): the docente-sullo-slot
# regularity metric. The golden CSV as the reference, the run dump compared against it.
# Fail-soft (code-review notte): under set -euo pipefail a bare failure here would kill the
# capture and lose every KPI after it, sanity-report.txt included; when the golden retires
# (TASK-452 plan) this line degrades to a WARN, it does not brick the battery.
python3 scripts/analyze/pair-stability-kpi.py --run src/main/resources/seed/lessons.csv --compare "$OUT/lessons.txt" --html "$OUT/kpi-pair-stability.html" | tee "$OUT/kpi-pair-stability.txt" || echo "WARN: pair-stability-kpi failed (non-fatal)"
python3 scripts/analyze/moda-knot-count.py "$OUT/lessons.txt" | tee "$OUT/kpi-moda-knot.txt"
python3 scripts/analyze/cadence-kpi.py     "$OUT/lessons.txt" | tee "$OUT/kpi-cadence.txt"
python3 scripts/analyze/fingerprint-sharpness-kpi.py "$OUT/lessons.txt" | tee "$OUT/kpi-sharpness.txt"
python3 scripts/analyze/hole-topology-kpi.py "$OUT/lessons.txt" | tee "$OUT/kpi-holes.txt"

# Golden-vs-run visual comparator (maintainer reads this): per-group day strips, our run vs golden.
python3 scripts/analyze/golden-vs-run-viz.py "$OUT/lessons.txt" \
    -o "$OUT/confronto-${RUN}.html" \
    || echo "WARN: golden-vs-run-viz.py failed for $RUN (non-fatal)"

# TASK-535 sanity gate (BLINDANTE): the FIRST table read in any validation - per
# group: min hours/day vs the golden's own per-group floor, days below that
# floor, internal 1h holes (slot-index based), placed lessons without a room
# (pins included). A single red = run NON CONFORME (exit 1). The capture itself
# must not die on a red: the verdict belongs to whoever reads the report.
SANITY_RC=0
python3 scripts/analyze/solution-sanity-check.py "$OUT/export.json.gz" | tee "$OUT/sanity-report.txt" || SANITY_RC=$?
if [ "$SANITY_RC" -ne 0 ]; then
  echo ">> SANITY: NON CONFORME (rossi presenti) - $OUT/sanity-report.txt"
fi

# Teacher day-of-week fingerprint for the 7 Q22 fixed-day teachers (TASK-412, the
# "Q22 golden-derived" 0-7 full-day VACATION rows in availability_rules.csv):
# T01, T07, T19, T24, T22, T12, T17.
# rows = teacher|weekday|count, for the run. Compared against the golden's concentration
# (golden lessons.csv is at claudedocs/spalmone-2026-27/data/lessons.csv).
TEACHERS="T01|T07|T19|T24|T22|T12|T17"
./scripts/db/db-query.sh "SELECT t.name || '|' || extract(dow from ts.startdatetime) || '|' || count(*) FROM lesson l JOIN course c ON l.course_id=c.id JOIN teacher t ON c.teacher_id=t.id JOIN timeslot ts ON l.timeslot_id=ts.id WHERE t.name ~ '^(${TEACHERS})$' GROUP BY t.name, extract(dow from ts.startdatetime) ORDER BY t.name, extract(dow from ts.startdatetime)" > "$OUT/teacher-fingerprint.txt" || true

# Phase markers: construction CH complete + box-repair/eviction/compaction lines (phase 1 + 4).
# The app logs to /tmp/planner.log (planner.sh start/run internal redirect); dev-start-and-wait
# also writes a startup-only /tmp/planner-dev.log. Check both.
for LOG in /tmp/planner.log "${PLANNER_LOG:-/tmp/planner-dev.log}"; do
  if [ -f "$LOG" ]; then
    grep -aE "Day-clustered CH complete|box-the-residual|box repair|evict|still boxed|compaction: student-day gaps|Weekly-template stamp|Empty expected day|M20.2|conflicts added" "$LOG" \
      | tail -45 > "$OUT/phase-markers-log.txt" || true
    [ -s "$OUT/phase-markers-log.txt" ] && break
  fi
done

echo ">> done. score: $(cat "$OUT/score.txt")"
echo ">> full-day: $(grep -E 'FULL-DAY RATE' "$OUT/kpi-fill.txt" || true)"
echo ">> buchi interni: $(grep -E 'BUCO-INTERNO:' "$OUT/kpi-holes.txt" || true)"
echo ">> day-start scarti (>=3 vs golden): $(grep -E 'SCARTO' "$OUT/kpi-daystart-anatomy.txt" || echo none)"
echo ">> confronto: $OUT/confronto-${RUN}.html"

# TASK-415: phase-transition dumps (per-phase recurrence-erosion measurement).
# When the solve was run with -Dplanner.phase-transition-dump.enabled=true,
# PhaseTransitionMarker wrote a phase-<n>-<slug>.txt pipe dump at every phase boundary
# (the end state of the phase that just completed). Collect them and run resemblance-kpi
# on each so the per-phase recurrence erosion (construction ~77% -> final ~37%) is
# attributable to a phase, not just measured end-to-end. No-op if the dumps are absent
# (the flag is OFF by default), so existing captures are unaffected.
#
# TASK-419 (additive): the marker now also writes a phase-<n>-<slug>-boxed.txt sibling
# (group|teacher|subject, the boxed.txt format) next to each phase-<n>-<slug>.txt - the
# glob below already picks both up in one cp. Boxed dumps are excluded from the
# resemblance-kpi loop (that KPI reads the 6-field placed-lesson format only) and counted
# separately so the existing "collected" count keeps meaning "phase boundaries", not
# "files".
TRANS_DIR="${PLANNER_PHASE_TRANSITION_DIR:-target/phase-transitions}"
if [ -d "$TRANS_DIR" ] && ls "$TRANS_DIR"/phase-*.txt >/dev/null 2>&1; then
  mkdir -p "$OUT/phase-transitions"
  cp "$TRANS_DIR"/phase-*.txt "$OUT/phase-transitions/"
  BOXED_COUNT=$(ls "$OUT/phase-transitions"/phase-*-boxed.txt 2>/dev/null | wc -l)
  PLACED_COUNT=$(ls "$OUT/phase-transitions"/phase-*.txt 2>/dev/null | { grep -vc -- '-boxed\.txt$' || true; })
  echo ">> phase-transition dumps: $PLACED_COUNT collected (+ $BOXED_COUNT boxed, TASK-419)"
  for DUMP in "$OUT/phase-transitions"/phase-*.txt; do
    case "$DUMP" in
      *-boxed.txt) continue ;;
    esac
    NAME=$(basename "$DUMP" .txt)
    python3 scripts/analyze/resemblance-kpi.py "$DUMP" > "$OUT/phase-transitions/${NAME}-resemblance.txt" || true
  done
  echo ">> per-phase resemblance: $OUT/phase-transitions/*-resemblance.txt"
else
  echo ">> phase-transition dumps: none (run solve with -Dplanner.phase-transition-dump.enabled=true to capture)"
fi
