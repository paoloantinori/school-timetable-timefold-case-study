#!/usr/bin/env python3
"""
compare-experiments.py — Side-by-side diff of two run-experiment.sh scorecards.

Usage:
    python3 scripts/benchmark/compare-experiments.py A.json B.json

Score convention (Timefold HardMediumSoftScore): hard/medium/soft scores are
NEGATIVE, and closer to zero is better. Unassigned-lesson count: lower is better.
Avg lessons/day per group: closer to the human target (higher) is better.
"""
import json
import sys


def load(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def fmt_int(v):
    try:
        return f"{int(v):,}"
    except (TypeError, ValueError):
        return str(v) if v is not None else "—"


def delta(a, b, lower_is_better=None):
    """Return 'B - A' as a signed string, tagged better/worse by convention."""
    try:
        d = int(b) - int(a)
    except (TypeError, ValueError):
        return ""
    sign = "+" if d > 0 else ""
    tag = ""
    if d != 0 and lower_is_better is not None:
        improved = (d < 0) if lower_is_better else (d > 0)
        tag = "  (better)" if improved else "  (worse)"
    return f"{sign}{d:,}{tag}"


def fmt_time(s):
    try:
        s = int(s)
        return f"{s//60}m{s%60:02d}s"
    except (TypeError, ValueError):
        return "—"


def fmt_ms(ms):
    try:
        return f"{int(ms)/1000:.1f}s"
    except (TypeError, ValueError):
        return "—"


def section(title):
    print(f"\n── {title} " + "─" * max(1, 60 - len(title)))


def main():
    if len(sys.argv) != 3:
        print("Usage: compare-experiments.py A.json B.json", file=sys.stderr)
        return 2
    a, b = load(sys.argv[1]), load(sys.argv[2])
    label_a = a.get("name", sys.argv[1])
    label_b = b.get("name", sys.argv[2])

    print(f"{'':32}{label_a:>22}  {label_b:>22}  {'delta':>18}")
    print("=" * 98)

    section("Identity")
    for key in ("config_delta", "git_commit", "timestamp"):
        print(f"{key:32}{str(a.get(key))[:22]:>22}  {str(b.get(key))[:22]:>22}")
    print(f"{'wall_clock_seconds':32}{fmt_time(a.get('wall_clock_seconds')):>22}  {fmt_time(b.get('wall_clock_seconds')):>22}  {delta(a.get('wall_clock_seconds'), b.get('wall_clock_seconds')):>18}")

    section("Scores (closer to 0 is better)")
    for key in ("hard_score", "medium_score", "soft_score"):
        print(f"{key:32}{fmt_int(a.get(key)):>22}  {fmt_int(b.get(key)):>22}  {delta(a.get(key), b.get(key), lower_is_better=False):>18}")
    print(f"{'final_score (string)':32}{str(a.get('final_score'))[:22]:>22}  {str(b.get('final_score'))[:22]:>22}")

    section("Unassigned (lower is better)")
    print(f"{'unassigned_lessons':32}{fmt_int(a.get('unassigned_lessons')):>22}  {fmt_int(b.get('unassigned_lessons')):>22}  {delta(a.get('unassigned_lessons'), b.get('unassigned_lessons'), lower_is_better=True):>18}")

    section("Avg lessons / active day per group (higher = closer to target)")
    avg_a = a.get("avg_lessons_per_day_per_group", {}) or {}
    avg_b = b.get("avg_lessons_per_day_per_group", {}) or {}
    for grp in sorted(set(avg_a) | set(avg_b)):
        va, vb = avg_a.get(grp), avg_b.get(grp)
        sa = f"{va:.2f}" if isinstance(va, (int, float)) else "—"
        sb = f"{vb:.2f}" if isinstance(vb, (int, float)) else "—"
        d = f"{vb - va:+.2f}" if isinstance(va, (int, float)) and isinstance(vb, (int, float)) else ""
        print(f"{grp:32}{sa:>22}  {sb:>22}  {d:>18}")

    section("Top constraints by hard impact (closer to 0 is better)")
    cb_a = {c.get("constraint"): c for c in (a.get("constraint_breakdown") or [])}
    cb_b = {c.get("constraint"): c for c in (b.get("constraint_breakdown") or [])}

    def hard_of(c):
        try:
            return int((c or {}).get("hard", 0))
        except (TypeError, ValueError):
            return 0

    ranked = sorted(set(cb_a) | set(cb_b),
                    key=lambda k: max(abs(hard_of(cb_a.get(k))), abs(hard_of(cb_b.get(k)))),
                    reverse=True)
    for k in ranked[:12]:
        ha, hb = hard_of(cb_a.get(k)), hard_of(cb_b.get(k))
        print(f"{str(k)[:32]:32}{fmt_int(ha):>22}  {fmt_int(hb):>22}  {delta(ha, hb, lower_is_better=False):>18}")

    section("Per-phase timing (durationMs) and score")
    pa = {p.get("phase"): p for p in (a.get("phase_progression") or [])}
    pb = {p.get("phase"): p for p in (b.get("phase_progression") or [])}
    for ph in sorted(set(pa) | set(pb), key=lambda x: (x is None, x)):
        ra, rb = pa.get(ph), pb.get(ph)
        da, db = (ra or {}).get("durationMs"), (rb or {}).get("durationMs")
        sa, sb = (ra or {}).get("score"), (rb or {}).get("score")
        print(f"phase {str(ph):<24}{fmt_ms(da):>14} / {str(sa):<12}{fmt_ms(db):>14} / {str(sb)}")

    print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
