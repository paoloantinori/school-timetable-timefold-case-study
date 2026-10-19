#!/usr/bin/env python3
"""badseed-frequency-stats.py - summarise a multi-start bad-seed frequency batch.

Reads a RESULTS directory produced by multistart-ab.sh (subdirs baseline-grasp-off,
grasp-seed-N, each with a score.txt holding "<h>hard/<m>medium/<s>soft" plus an
appended "wall_clock_seconds=<n>" line) and computes:

  - per-run table (score, wall-clock, 0-hard?)
  - p_hat(good basin) over the GRASP seeds (independent constructions), Wilson 95% CI
  - P(>=1 0-hard | N) = 1-(1-p)^N for N=1..8, at the point estimate and the CI bounds
  - recommended N to clear P>=0.95 and P>=0.99

The baseline (GRASP-off) is a deterministic CONTROL, not part of the GRASP sample: the
default construction is reproducible (baseline1==baseline2, TASK-480 independence gate),
so it contributes one reference point, not a draw from the basin distribution. Only the
GRASP seeds (varied construction) sample p(good basin).

Usage: ./scripts/analyze/badseed-frequency-stats.py <results-dir>
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shared import parse_score_string  # noqa: E402 (dual-format: legacy + BendableScore bracket)

WALL_RE = re.compile(r"wall_clock_seconds=(-?\d+)")
SEED_RE = re.compile(r"^grasp-seed-\d+$")

Z95 = 1.959963984540054  # z for a two-sided 95% CI


def parse_run(run_dir):
    score_path = os.path.join(run_dir, "score.txt")
    if not os.path.isfile(score_path):
        return None
    with open(score_path) as f:
        text = f.read()
    m = parse_score_string(text)
    if not m:
        return None
    hard, medium, soft = m
    wm = WALL_RE.search(text)
    wall = int(wm.group(1)) if wm else None
    return {"hard": hard, "medium": medium, "soft": soft, "wall": wall}


def wilson_ci(k, n, z=Z95):
    """Wilson score interval for k successes in n trials. Returns (center, lo, hi)."""
    if n == 0:
        return (float("nan"), float("nan"), float("nan"))
    p = k / n
    denom = 1 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    half = (z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))) / denom
    return (center, max(0.0, center - half), min(1.0, center + half))


def p_at_least_one(p, n):
    """P(>=1 success in n independent draws) = 1-(1-p)^n."""
    return 1.0 - (1.0 - p) ** n


def recommend_n(p, target):
    """Smallest N with P(>=1 | N, p) >= target. None if p<=0 (never) or target unreachable."""
    if p >= 1.0:
        return 1
    if p <= 0.0:
        return None
    return math.ceil(math.log1p(-target) / math.log1p(-p))


def main(results_dir):
    if not os.path.isdir(results_dir):
        sys.exit(f"not a directory: {results_dir}")
    runs = {}
    for name in sorted(os.listdir(results_dir)):
        rd = os.path.join(results_dir, name)
        if not os.path.isdir(rd):
            continue
        parsed = parse_run(rd)
        if parsed is not None:
            runs[name] = parsed
    if not runs:
        sys.exit(f"no run subdirs with a parseable score.txt under {results_dir}")

    baseline = {n: r for n, r in runs.items() if n.startswith("baseline")}
    seeds = {n: r for n, r in runs.items() if SEED_RE.match(n)}

    print(f"# bad-seed frequency summary: {results_dir}")
    print(f"# parsed {len(runs)} runs ({len(seeds)} GRASP seeds + {len(baseline)} baseline)\n")

    print(f"{'run':<22} {'hard':>8} {'medium':>10} {'soft':>8} {'wall(s)':>9} {'0-hard?':>8}")
    for n in sorted(baseline) + sorted(seeds):
        r = runs[n]
        wall = r["wall"] if r["wall"] is not None else "?"
        flag = "YES" if r["hard"] == 0 else "no"
        print(f"{n:<22} {r['hard']:>8} {r['medium']:>10} {r['soft']:>8} {wall:>9} {flag:>8}")

    n = len(seeds)
    k = sum(1 for r in seeds.values() if r["hard"] == 0)
    print(f"\n# GRASP seeds (independent constructions): k={k} good / n={n}")
    if n == 0:
        print("# no GRASP seeds parsed yet; cannot estimate p (batch still running?).")
        return
    p_hat = k / n  # MLE point estimate (matches the TASK-480 verdict convention)
    _, lo, hi = wilson_ci(k, n)  # Wilson bounds only; its center is NOT the point estimate
    print(f"# p_hat(good basin) = {p_hat:.3f} (MLE k/n), Wilson 95% CI [{lo:.3f}, {hi:.3f}]")

    print("\n# P(>=1 0-hard | N seeds) = 1-(1-p)^N   [point, CI_lo, CI_hi]")
    print(f"{'N':>3} {'point':>8} {'CI_lo':>8} {'CI_hi':>8}")
    for nn in range(1, 9):
        print(f"{nn:>3} {p_at_least_one(p_hat, nn):>8.3f} "
              f"{p_at_least_one(lo, nn):>8.3f} {p_at_least_one(hi, nn):>8.3f}")

    for target in (0.95, 0.99):
        n_pt = recommend_n(p_hat, target)
        n_lo = recommend_n(lo, target)
        n_hi = recommend_n(hi, target)
        print(f"# recommended N for P>={target}: point={n_pt}, CI_lo={n_lo}, CI_hi={n_hi}")

    if baseline:
        b = next(iter(baseline.values()))
        verdict = ("0-hard (default construction lands in good basin)"
                   if b["hard"] == 0 else "NOT 0-hard (default lands in bad basin)")
        print(f"\n# baseline (GRASP-off, deterministic control): "
              f"{b['hard']}hard/{b['medium']}medium => {verdict}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: badseed-frequency-stats.py <results-dir>")
    main(sys.argv[1])
