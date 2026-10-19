#!/usr/bin/env python3
"""
check-regression.py - Read-only regression checker for the spalmone campaign.

Compares a CANDIDATE solver run (a capture-run.sh output directory) against a
BASELINE run on the campaign's guard KPIs, and emits a machine verdict with a
configurable exit code. It is READ-ONLY: it never launches a solve, never
imports, never touches the database. Both inputs are artefact directories
produced by `./scripts/benchmark/capture-run.sh <dir>`.

WHY THIS EXISTS (see claudedocs/spalmone-2026-27/task239-regression-benchmark-design.md
for the full design). The local search is NON_REPRODUCIBLE, so two runs on the
same commit + config + seed differ. That makes naive single-run-vs-single-run
comparison misleading in two ways, and this tool handles both honestly:

  * HARD score is binary and blocante (0 or not). A single candidate run that
    lands hard<0 is NOT a regression by itself: a single construction reaches
    0-hard only ~75% of the time (bad-seed variance). So a single-run hard<0
    against a 0-hard baseline is INCONCLUSIVE, not a regression. A regression
    on hard is declared only when a multi-start aggregate (best of N attempts)
    still cannot reach 0-hard that the baseline reached.
  * Continuous KPIs (full-day, recurrence, day-start, holes, medium) carry
    run-to-run noise even at a fixed commit. They are compared against the
    baseline with a tolerance (warn/fail bands), and a single-run verdict is
    labelled INDICATIVE. For an authoritative verdict, point --candidate at a
    multi-start aggregate directory.

USAGE
  # Single candidate run vs a baseline capture:
  python3 scripts/benchmark/check-regression.py \
      --baseline claudedocs/spalmone-2026-27/task469-results/ab-cps-on-run1 \
      --candidate <fresh-capture-dir>

  # Multi-start aggregate candidate (dir of N capture subdirs) vs baseline:
  python3 scripts/benchmark/check-regression.py \
      --baseline <baseline-capture-dir> \
      --aggregate <dir-containing-N-capture-subdirs>

  # Override the comparison rules with a spec file (see DEFAULT_SPEC below):
  python3 scripts/benchmark/check-regression.py --baseline B --candidate C --spec my-spec.json

CONFIG-GATING / NOT IN CI
  This is invoked manually. It is intentionally NOT wired into any CI hook or
  pre-commit. The thresholds live in the spec (embedded default or --spec file),
  which is the single configuration knob. Adoption into CI is maintainer's call.

EXIT CODES
  0  PASS            - no regression (warnings allowed; see --strict to fail on warns)
  1  REGRESSION      - a guard KPI broke its fail band, or hard regressed on a
                       confirmed multi-start aggregate
  2  INCONCLUSIVE    - single candidate run has hard<0 vs a 0-hard baseline
                       (could be bad-seed variance; rerun as --aggregate)
  3  ERROR           - bad input: missing dir, unreadable/missing KPI file
"""
from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import sys
from dataclasses import dataclass, field, asdict

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "analyze"))
from shared import parse_score_string  # noqa: E402 (dual-format: legacy + BendableScore bracket)
from pathlib import Path
from typing import Optional

# --- Spec -------------------------------------------------------------------
# The comparison RULES. Absolute KPI values always come from the parsed capture
# dirs, never from this spec, so the spec does not go stale when a ratified run
# shifts a few points. Only the policy (which KPI, sense, warn/fail deltas) lives
# here, and that policy is stable. `source` is human provenance (which file the
# parser reads); parsing itself is imperative because file formats are ad-hoc.
#
# Senses:
#   binary          - hard score: 0 = good, <0 = bad. Blocante, variance-aware.
#   higher_better   - bigger is better (percentages; medium score, less-negative).
#   lower_better    - smaller is better (hole count).
# Units:
#   pp    - percentage points (full-day, recurrence, day-start)
#   count - integer count (holes)
#   score - absolute score points (medium)
DEFAULT_SPEC: dict = {
    "version": 1,
    "kpis": {
        "hard": {
            "sense": "binary",
            "unit": "count",
            "source": "score.txt  hard field",
            "doc": "unassigned/boxed + room-null + timeslot-tier hard",
        },
        "full_day": {
            "sense": "higher_better",
            "unit": "pp",
            "warn": 2.0,
            "fail": 5.0,
            "source": "kpi-fill.txt  FULL-DAY RATE",
        },
        "recurrence_unpinned": {
            "sense": "higher_better",
            "unit": "pp",
            "warn": 2.0,
            "fail": 5.0,
            "source": "kpi-resemblance.txt  ricorrenza UNPINNED",
        },
        "day_start": {
            "sense": "higher_better",
            "unit": "pp",
            "warn": 1.0,
            "fail": 3.0,
            "source": "kpi-daystart.txt  DAY-START ANCHORED RATE",
        },
        "holes": {
            "sense": "lower_better",
            "unit": "count",
            "warn": 5,
            "fail": 15,
            "source": "kpi-holes.txt  BUCO-INTERNO",
        },
        "medium": {
            "sense": "higher_better",
            "unit": "score",
            "warn": 50000,
            "fail": 120000,
            "source": "score.txt  medium field (less-negative is better)",
        },
    },
    "notes": [
        "single-run verdict is INDICATIVE: the LS is NON_REPRODUCIBLE, so a",
        "single candidate run carries run-to-run noise. Confirm any hard<0 with",
        "a multi-start aggregate (--aggregate, best-of-N).",
    ],
}


# --- Parsing ----------------------------------------------------------------
# Verified against real captures, e.g. claudedocs/spalmone-2026-27/task469-results/ab-cps-on-run1:
#   score.txt:            "score: 0hard/-963616medium/7997soft"
#   kpi-fill.txt:         "FULL-DAY RATE: 1778/1957 = 90.9%  (golden ...)"
#   kpi-resemblance.txt:  "ricorrenza UNPINNED: 7107/9404 = 75.6%"
#   kpi-daystart.txt:     "DAY-START ANCHORED RATE: 2166/2209 = 98.1%"
#   kpi-holes.txt:        "  BUCO-INTERNO     0"   (inside "TOTALI PER CLASSE")
# soft is validated (the regex requires a numeric soft field) but not stored: it
# is not a guard KPI.
_SCORE_RE = re.compile(r"score:\s*(\S+)")  # full token; dual-format parse via shared.parse_score_string (TASK-538 B)
# Single source of truth for the three percentage KPIs: (file, line prefix).
# _PCT_RE is derived from it so the mapping cannot drift between regex and parser.
_PCT_SOURCES = {
    "full_day": ("kpi-fill.txt", "FULL-DAY RATE"),
    "recurrence_unpinned": ("kpi-resemblance.txt", "ricorrenza UNPINNED"),
    "day_start": ("kpi-daystart.txt", "DAY-START ANCHORED RATE"),
}
_PCT_RE = {k: re.compile(prefix + r":\s+\d+/\d+\s+=\s+([\d.]+)%")
           for k, (_, prefix) in _PCT_SOURCES.items()}
# Matches both the "TOTALI PER CLASSE" form ("  BUCO-INTERNO     0") and any
# future summary-line form ("BUCO-INTERNO: 0 giorni / ...").
_HOLES_RE = re.compile(r"(?m)^\s*BUCO-INTERNO[\s:]+(\d+)")


@dataclass
class Capture:
    """Parsed KPI values from one capture-run.sh output directory."""

    dir: str
    found: bool = True  # False when the dir is missing or has no capture subdirs
    hard: Optional[int] = None
    medium: Optional[int] = None
    full_day: Optional[float] = None
    recurrence_unpinned: Optional[float] = None
    day_start: Optional[float] = None
    holes: Optional[int] = None
    missing: list = field(default_factory=list)

    def as_display(self) -> dict:
        return {
            "dir": self.dir,
            "hard": self.hard,
            "medium": self.medium,
            "full_day": self.full_day,
            "recurrence_unpinned": self.recurrence_unpinned,
            "day_start": self.day_start,
            "holes": self.holes,
        }


def _read(d: Path, name: str, missing: list) -> Optional[str]:
    f = d / name
    if not f.is_file():
        missing.append(name)
        return None
    try:
        return f.read_text(errors="replace")
    except OSError as exc:
        missing.append(f"{name}({exc})")
        return None


def parse_capture(directory: str) -> Capture:
    """Parse a capture-run.sh output directory into a Capture."""
    d = Path(directory)
    if not d.is_dir():
        # Caller handles missing-dir as ERROR; return empty with a sentinel.
        cap = Capture(dir=directory, found=False)
        cap.missing.append(f"<dir not found: {directory}>")
        return cap

    cap = Capture(dir=str(d))
    score_txt = _read(d, "score.txt", cap.missing)
    if score_txt:
        m = _SCORE_RE.search(score_txt)
        parsed = parse_score_string(m.group(1)) if m else None
        if parsed:
            cap.hard, cap.medium = parsed[0], parsed[1]
        else:
            cap.missing.append("score.txt(unmatched format)")

    for key, (fname, _) in _PCT_SOURCES.items():
        rx = _PCT_RE[key]
        txt = _read(d, fname, cap.missing)
        if txt:
            m = rx.search(txt)
            if m:
                setattr(cap, key, float(m.group(1)))
            else:
                cap.missing.append(f"{fname}(unmatched format)")

    holes_txt = _read(d, "kpi-holes.txt", cap.missing)
    if holes_txt:
        m = _HOLES_RE.search(holes_txt)
        if m:
            cap.holes = int(m.group(1))
        else:
            cap.missing.append("kpi-holes.txt(unmatched BUCO-INTERNO)")

    return cap


def aggregate_captures(directory: str) -> Capture:
    """Parse a directory of N capture subdirs into one aggregate Capture.

    hard = best (max, 0 > -6) across runs; continuous KPIs = median; holes = max
    (worst). The aggregate's hard is the honest "did ANY attempt reach 0-hard"
    signal, which is what the binary rule needs.
    """
    d = Path(directory)
    if not d.is_dir():
        cap = Capture(dir=directory, found=False)
        cap.missing.append(f"<aggregate dir not found: {directory}>")
        return cap

    subs = sorted([p for p in d.iterdir() if p.is_dir() and (p / "score.txt").is_file()])
    if not subs:
        cap = Capture(dir=str(d), found=False)
        cap.missing.append(f"<no capture subdirs (with score.txt) under {directory}>")
        return cap

    parts = [parse_capture(str(p)) for p in subs]
    agg = Capture(dir=f"{d} (aggregate of {len(parts)} runs)")

    def vals(attr):
        return [v for p in parts if (v := getattr(p, attr)) is not None]

    hards = vals("hard")
    if hards:
        agg.hard = max(hards)  # best: 0 if any attempt hit 0-hard
    mediums = vals("medium")
    if mediums:
        agg.medium = int(statistics.median(mediums))
    for attr in ("full_day", "recurrence_unpinned", "day_start"):
        vs = vals(attr)
        if vs:
            setattr(agg, attr, round(statistics.median(vs), 2))
    hv = vals("holes")
    if hv:
        agg.holes = max(hv)  # worst-case hole count

    # Surface parse problems from children so they are not silently dropped.
    child_missing = [f"{p.dir}: {', '.join(p.missing)}" for p in parts if p.missing]
    agg.missing.extend(child_missing)
    return agg


# --- Comparison -------------------------------------------------------------
# Per-KPI status values: pass | warn | regression | inconclusive | n/a
STATUS_ORDER = {"regression": 4, "inconclusive": 3, "warn": 2, "n/a": 1, "pass": 0}


@dataclass
class KpiVerdict:
    name: str
    status: str
    baseline: object = None
    candidate: object = None
    delta: Optional[float] = None
    detail: str = ""


def _compare_continuous(name, spec, b_val, c_val):
    sense = spec["sense"]
    unit = spec.get("unit", "")
    warn = spec["warn"]
    fail = spec["fail"]
    delta = c_val - b_val
    worse = -delta if sense == "higher_better" else delta  # magnitude of degradation
    if worse >= fail:
        status = "regression"
    elif worse >= warn:
        status = "warn"
    else:
        status = "pass"
    sign = "+" if delta >= 0 else ""
    detail = f"delta {sign}{delta:.2f} {unit} (warn {warn}, fail {fail})"
    return KpiVerdict(name, status, b_val, c_val, round(delta, 2), detail)


def compare(baseline: Capture, candidate: Capture, spec: dict, single: bool) -> list:
    """Produce a KpiVerdict per KPI in the spec."""
    verdicts = []
    kpis = spec["kpis"]
    # Iterate the spec's own keys: a KPI added to the spec cannot be silently
    # dropped (a hardcoded tuple would skip additions it doesn't list).
    for name, kspec in kpis.items():
        b_val = getattr(baseline, name, None)
        c_val = getattr(candidate, name, None)
        if b_val is None or c_val is None:
            verdicts.append(KpiVerdict(name, "n/a", b_val, c_val, None,
                                       "value missing in baseline or candidate"))
            continue

        if kspec["sense"] == "binary":
            # HARD rule (binary, blocante, variance-aware).
            if b_val < 0:
                verdicts.append(KpiVerdict(name, "n/a", b_val, c_val, None,
                                           "baseline itself infeasible (hard<0): not a valid reference"))
            elif c_val == 0:
                verdicts.append(KpiVerdict(name, "pass", b_val, c_val, None,
                                           "candidate reached 0-hard"))
            elif c_val < 0:
                if single:
                    verdicts.append(KpiVerdict(name, "inconclusive", b_val, c_val, None,
                                               f"candidate hard={c_val} on a SINGLE run vs 0-hard "
                                               "baseline; could be bad-seed variance (p(0-hard)"
                                               "~0.75/construction). Rerun as --aggregate."))
                else:
                    verdicts.append(KpiVerdict(name, "regression", b_val, c_val, None,
                                               f"aggregate best-of-N hard={c_val}: the candidate "
                                               "basin cannot reach 0-hard the baseline reached"))
            else:  # c_val > 0 (defensive: hard is normally <= 0)
                verdicts.append(KpiVerdict(name, "pass", b_val, c_val, None, "candidate hard improved"))
            continue

        verdicts.append(_compare_continuous(name, kspec, b_val, c_val))
    return verdicts


def overall_verdict(verdicts: list) -> tuple:
    """Roll per-KPI statuses into (verdict_label, exit_code)."""
    worst = "pass"
    for v in verdicts:
        if STATUS_ORDER[v.status] > STATUS_ORDER[worst]:
            worst = v.status
    if worst == "regression":
        return "REGRESSION", 1
    if worst == "inconclusive":
        return "INCONCLUSIVE", 2
    if worst == "warn":
        return "PASS (with warnings)", 0
    return "PASS", 0


# --- Reporting --------------------------------------------------------------
def render_stdout(baseline: Capture, candidate: Capture, verdicts: list, label: str,
                  verdict_label: str, exit_code: int) -> str:
    lines = []
    lines.append(f"=== regression check ({label}) ===")
    lines.append(f"baseline : {baseline.dir}")
    lines.append(f"candidate: {candidate.dir}")
    lines.append("")
    lines.append(f"{'KPI':<22}{'baseline':>12}{'candidate':>12}{'status':>16}  detail")
    lines.append("-" * 90)
    for v in verdicts:
        b = "-" if v.baseline is None else v.baseline
        c = "-" if v.candidate is None else v.candidate
        lines.append(f"{v.name:<22}{str(b):>12}{str(c):>12}{v.status:>16}  {v.detail}")
    lines.append("-" * 90)
    lines.append(f"VERDICT: {verdict_label}  (exit {exit_code})")
    return "\n".join(lines)


def render_json(baseline: Capture, candidate: Capture, verdicts: list, label: str,
                verdict_label: str, exit_code: int, spec: dict) -> str:
    payload = {
        "verdict": verdict_label,
        "exit_code": exit_code,
        "mode": label,
        "baseline": baseline.as_display(),
        "candidate": candidate.as_display(),
        "baseline_missing": baseline.missing,
        "candidate_missing": candidate.missing,
        "kpis": [asdict(v) for v in verdicts],
        "spec_version": spec.get("version"),
        "notes": spec.get("notes", []),
    }
    return json.dumps(payload, indent=2)


# --- Main -------------------------------------------------------------------
_KPI_FIELDS = ("hard", "medium", "full_day", "recurrence_unpinned", "day_start", "holes")


def _has_any_kpi(cap: Capture) -> bool:
    """True if at least one guard KPI was parsed. A dir that exists but yielded
    zero KPIs would otherwise produce all-n/a -> PASS; for a regression detector
    a false PASS is the worst outcome, so callers treat "no usable data" as ERROR."""
    return any(getattr(cap, k) is not None for k in _KPI_FIELDS)


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="Read-only regression checker (spalmone campaign).")
    p.add_argument("--baseline", required=True, help="baseline capture-run.sh output dir")
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--candidate", help="single candidate capture-run.sh output dir")
    src.add_argument("--aggregate", help="dir of N candidate capture subdirs (multi-start)")
    p.add_argument("--spec", help="JSON spec overriding DEFAULT_SPEC (thresholds/senses)")
    p.add_argument("--out-json", help="write machine-readable JSON report to this path")
    p.add_argument("--out-md", help="write markdown report to this path")
    p.add_argument("--strict", action="store_true", help="exit 1 on warnings too (default: 0)")
    args = p.parse_args(argv)

    spec = DEFAULT_SPEC
    if args.spec:
        try:
            spec = json.loads(Path(args.spec).read_text())
        except (OSError, json.JSONDecodeError) as exc:
            print(f"[ERROR] cannot load spec {args.spec}: {exc}", file=sys.stderr)
            return 3

    baseline = parse_capture(args.baseline)
    if not baseline.found:
        for m in baseline.missing:
            print(f"[ERROR] baseline: {m}", file=sys.stderr)
        return 3

    single = args.candidate is not None
    cand_dir = args.candidate if single else args.aggregate
    candidate = parse_capture(cand_dir) if single else aggregate_captures(cand_dir)
    if not candidate.found:
        for m in candidate.missing:
            print(f"[ERROR] candidate: {m}", file=sys.stderr)
        return 3
    if not _has_any_kpi(candidate):
        print(f"[ERROR] candidate produced no parseable KPIs in {cand_dir}", file=sys.stderr)
        return 3

    label = "single" if single else "aggregate"
    verdicts = compare(baseline, candidate, spec, single)
    verdict_label, exit_code = overall_verdict(verdicts)
    if args.strict and verdict_label == "PASS (with warnings)":
        exit_code = 1

    print(render_stdout(baseline, candidate, verdicts, label, verdict_label, exit_code))

    if baseline.missing or candidate.missing:
        # Non-fatal parse gaps (a KPI file present but unmatched, or a child of
        # an aggregate). Surface them so nothing is silently dropped.
        for src_name, cap in (("baseline", baseline), ("candidate", candidate)):
            for m in cap.missing:
                print(f"[WARN] {src_name} parse gap: {m}", file=sys.stderr)

    if args.out_json:
        Path(args.out_json).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out_json).write_text(
            render_json(baseline, candidate, verdicts, label, verdict_label, exit_code, spec))
        print(f"[INFO] json report -> {args.out_json}", file=sys.stderr)
    if args.out_md:
        md = _render_md(baseline, candidate, verdicts, label, verdict_label, exit_code, spec)
        Path(args.out_md).parent.mkdir(parents=True, exist_ok=True)
        Path(args.out_md).write_text(md)
        print(f"[INFO] md report -> {args.out_md}", file=sys.stderr)

    return exit_code


def _render_md(baseline, candidate, verdicts, label, verdict_label, exit_code, spec) -> str:
    rows = []
    for v in verdicts:
        b = "-" if v.baseline is None else v.baseline
        c = "-" if v.candidate is None else v.candidate
        rows.append(f"| {v.name} | {b} | {c} | {v.status} | {v.detail} |")
    notes = "\n".join(f"- {n}" for n in spec.get("notes", []))
    return f"""# regression report ({label})

verdict: **{verdict_label}** (exit {exit_code})

- baseline: `{baseline.dir}`
- candidate: `{candidate.dir}`

| KPI | baseline | candidate | status | detail |
|-----|----------|-----------|--------|--------|
{chr(10).join(rows)}

## notes
{notes}
"""


if __name__ == "__main__":
    sys.exit(main())
