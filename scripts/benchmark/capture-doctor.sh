#!/bin/bash
#
# capture-doctor.sh - the post-delivery gate a capture must pass BEFORE anyone
# reads its numbers (TASK-579; full hard-vector + verdict semantics, TASK-579.01).
# Born from the 02/09 evening: a capture that had "completed" held a 1-byte export
# (api.sh TASK-567 bug) and a later push died on a 106MB gzip - both discovered
# AFTER the verdict conversation had started.
#
# Checks (dir = a capture-run.sh output directory):
#   1. export.json.gz: present, >= 1KB (the 1-byte corruption signature), gunzip
#      integrity; beyond ~99MiB -> WARN not-pushable (GitHub caps at 100MB).
#   2. hard VECTOR agreement score.txt vs analysis.json, BOTH coordinates
#      (Bendable "[-13/0]hard" first+quasi, legacy plain "0hard" single). A
#      mismatch in EITHER coordinate is a FAIL: the two hard sides disagree and
#      the capture cannot be quoted at all. The medium side stays WARN-only
#      (the known in-memory vs DB-recount divergence, TASK-509).
#   3. sanity-report.txt present, red count reported (the sanity gate itself is
#      the reader's judgement; this just refuses a SILENT capture).
#   4. lint: the recurring "claudodocs" misspelling in the path.
#
# Verdict (one greppable line):
#   DOCTOR-OK                    hard=[0/0]: the ONLY capture a zero-hard claim may cite
#   DOCTOR-CONSISTENT-NONZERO hard=[N/M]: internally consistent, NOT zero-hard (AC#2: distinct token, an OK-grep must never match it)
#   DOCTOR-WARN ... NOT-DELIVERY-CERTIFIABLE:<reasons>  integrity gaps (no export / no score
#                                    evidence / no sanity): cannot be presented as a
#                                    certified delivery, exit 0 (intermediate captures)
#   DOCTOR-WARN ...               non-certifying warnings only (size, lint)
#   DOCTOR-FAIL <why>             corrupt export or hard-vector mismatch, exit 1
#
# Runbook (TASK-579.01 AC#5): capture INTEGRITY (this doctor), ZERO-HARD (both
# coordinates zero from the provider), and PER-GROUP SANITY (canonical gate, no
# reds) are three SEPARATE acceptances; a delivery needs all three, and its
# restore path must be demonstrated (DB volume reload or checkpoint apply;
# the JSON import is capped server-side, see import-schedule.sh).
#
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIR="${1:-}"
[[ -n "$DIR" && -d "$DIR" ]] || { echo "usage: capture-doctor.sh <capture-dir>"; exit 2; }
cd "$DIR"

WARNS=""
CERT_BLOCK=""
fail() { echo "DOCTOR-FAIL $*"; exit 1; }
warn() { WARNS="$WARNS  WARN: $*"; }
block() { CERT_BLOCK="$CERT_BLOCK $1"; warn "$2"; }

# lint: only the MISSPELLING (a campaign-path check would false-positive on every
# /tmp probe and next year's tree; the incident is the claudodocs typo itself)
case "$PWD" in
  *claudodocs*) warn "capture dir is $PWD: the claudodocs typo (results must live under claudedocs/spalmone-2026-27/<task>-results/)." ;;
esac

# ── 1. export ──
E="export.json.gz"
if [[ -f "$E" ]]; then
  SZ="$(stat -c %s "$E" 2>/dev/null)" || SZ=""
  [[ -z "$SZ" ]] && SZ="$(stat -f %z "$E" 2>/dev/null)" || true
  [[ "$SZ" =~ ^[0-9]+$ ]] || fail "cannot stat size of $E"
  [[ "$SZ" -ge 1024 ]] || fail "export.json.gz is ${SZ}B: the 1-byte-corruption signature (api.sh TASK-567 giant-body). Rerun the capture with the fixed api.sh before reading ANY number."
  gunzip -t "$E" 2>/dev/null || fail "export.json.gz fails gunzip -t: truncated/corrupt export."
  (( SZ > 99 * 1024 * 1024 )) && warn "export.json.gz is $((SZ / 1024 / 1024))MiB: over GitHub's 100MB cap, it will NOT push. Keep it on the laptop and note the path in the commit."
# plain-file tolerance for hand-assembled dirs (capture-run.sh always gzips):
# no gzip integrity to test, so the content itself must at least parse as JSON
elif [[ -f "export.json" ]]; then
  SZ="$(stat -c %s export.json 2>/dev/null)" || SZ=""
  [[ -z "$SZ" ]] && SZ="$(stat -f %z export.json 2>/dev/null)" || true
  [[ "$SZ" =~ ^[0-9]+$ ]] || fail "cannot stat size of export.json"
  [[ "$SZ" -ge 1024 ]] || fail "export.json is ${SZ}B: corrupt (TASK-567 signature)."
  python3 -c 'import json; json.load(open("export.json"))' 2>/dev/null \
    || fail "export.json does not parse as JSON: garbage cannot certify anything."
  (( SZ > 99 * 1024 * 1024 )) && warn "export.json is $((SZ / 1024 / 1024))MiB: over GitHub's 100MB cap, it will NOT push."
else
  block "no-export" "no export.json[.gz] in the capture: the run is not checkpoint-reproducible (protocol TASK-401)."
fi

# ── 2. hard-VECTOR agreement: score.txt vs analysis.json (BOTH coordinates) ──
# both sides parse through shared.parse_hard_vector / json with an implicit
# zero quasi tier: a pre-Bendable capture (score.txt plain, analysis.json with
# no quasi key) compares -234/0 vs -234/0 instead of being discarded, and a
# score.txt carrying an init line above the final score compares the LAST match
S_VEC=""
if [[ -f score.txt && -f analysis.json ]]; then
  VEC="$(python3 - "$REPO" <<'PYEOF' 2>/dev/null || true
import json, sys
sys.path.insert(0, sys.argv[1] + "/scripts/analyze")
import shared
try:
    s = shared.parse_hard_vector(open("score.txt").read()) or ""
except OSError:
    s = ""
try:
    d = json.load(open("analysis.json"))
    a = "{}/{}".format(d["hard"], d.get("quasi", 0)) if d.get("hard") is not None else ""
except Exception:
    a = ""
print(s + "|" + a)
PYEOF
)"
  S_VEC="${VEC%%|*}"
  A_VEC="${VEC##*|}"
  if [[ -n "${S_VEC:-}" && -n "${A_VEC:-}" ]]; then
    [[ "$S_VEC" == "$A_VEC" ]] \
      || fail "hard VECTOR diverges: score.txt [$S_VEC] vs analysis.json [$A_VEC]: either coordinate disagrees, the two hard sides cannot both be quoted (investigate before reading ANY number)."
  else
    block "no-score-evidence" "could not parse the hard vector from score.txt/analysis.json (${S_VEC:-?}/${A_VEC:-?}): incomplete score evidence."
  fi
else
  block "no-score-evidence" "score.txt or analysis.json missing: no cross-check of the delivery score."
fi

# ── 2b/4. SEED GENETICS (TASK-605): the delivery must carry the post-RA-cure seed.
# The G08 pool is 667 with the RA distribution (9 Recuperi rows, committed 11/09);
# 626 with zero Recuperi rows means the JAR that produced this delivery predates the
# cure (the worktree-JAR class, 12/09 incident): the capture is NOT measuring the
# current quadro regardless of every other check passing.
if [[ -f lessons.txt ]]; then
  FC2_HOURS="$(grep -E '^ ?G08\|' lessons.txt | grep -vc '\|Stage\|' || true)"
  RCU_ROWS="$(grep -cE '^ ?G08\|[^|]*\|[^|]*\|Recuperi' lessons.txt || true)"
  if [[ "${RCU_ROWS:-0}" -eq 0 && "${FC2_HOURS:-1}" -le 630 ]]; then
    warn "jar-stale: pool G08=${FC2_HOURS:-?}h con ZERO righe Recuperi (atteso 667/41 con la cura RA): la consegna e' stata prodotta da un build pre-11/09 (classe JAR-worktree, TASK-605): NON misura il quadro attuale."
  fi
fi
# no lessons.txt: the genetics check simply does not run (pre-battery captures are
# legitimately without it; capture-run.sh always produces it for new runs)

# ── 3/4. sanity + phases ──
if [[ -f sanity-report.txt ]]; then
  # groups, not detail lines: the header says "rosso su N gruppo/i"; the ROSSO
  # lines are up to three per group (sotto-min, buchi, stanza) and would
  # mis-rank captures if counted
  REDS="$(sed -nE 's/.*rosso su ([0-9]+) gruppo.*/\1/p' sanity-report.txt | head -1)"
  [[ "$REDS" =~ ^[0-9]+$ ]] || REDS="$(grep -c "ROSSO" sanity-report.txt 2>/dev/null || true)"
  REDS="${REDS:-0}"
else
  REDS=0
  block "no-sanity" "no sanity-report.txt: the validation sanity gate never ran (it is MANDATORY before any verdict, CLAUDE.md)."
fi
[[ -f phases.json ]] || warn "no phases.json: phase chronology not captured."

if [[ -n "$CERT_BLOCK" ]]; then
  echo "DOCTOR-WARN sanity-reds=$REDS hard=[${S_VEC:-?}] NOT-DELIVERY-CERTIFIABLE:$CERT_BLOCK$WARNS"
  exit 0
fi
if [[ -n "$WARNS" ]]; then
  echo "DOCTOR-WARN sanity-reds=$REDS hard=[$S_VEC]$WARNS"
  exit 0
fi
if [[ "$S_VEC" == "0/0" ]]; then  # both coordinates zero (post-normalization form)
  echo "DOCTOR-OK sanity-reds=$REDS export=${SZ:-?}B hard=[$S_VEC] (zero-hard: both coordinates)"
else
  echo "DOCTOR-CONSISTENT-NONZERO sanity-reds=$REDS export=${SZ:-?}B hard=[$S_VEC] (internally consistent, NOT zero-hard: cannot support a zero-hard claim)"
fi
