#!/usr/bin/env python3
"""
Self-test for check-regression.py.

Convention (matches scripts/workbook/test_*.py identity gates): exit 0 on full
pass, non-zero on any failure, with a one-line-per-check summary. Run directly:

    python3 scripts/benchmark/test_check_regression.py

The test synthesises minimal capture-run.sh output directories in a tempdir
(just the files check-regression.py parses) and drives the comparator through
its public functions, asserting the documented behaviour for each scenario.
"""
from __future__ import annotations

import importlib.util
import sys
import tempfile
from pathlib import Path

# The module file is "check-regression.py" (hyphenated, not a legal module
# name), so load it by path via importlib instead of a normal import.
_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("check_regression", _HERE / "check-regression.py")
cr = importlib.util.module_from_spec(_spec)
# Register before exec: dataclass introspection reads sys.modules[cls.__module__].
sys.modules["check_regression"] = cr
_spec.loader.exec_module(cr)  # noqa: E402

FAILURES: list[str] = []


def check(label: str, cond: bool, detail: str = "") -> None:
    status = "PASS" if cond else "FAIL"
    print(f"  [{status}] {label}{(': ' + detail) if detail and not cond else ''}")
    if not cond:
        FAILURES.append(label)


def write_capture(d: Path, *, score: str, full_day: float = 90.0,
                  recurrence: float = 75.0, day_start: float = 99.0,
                  holes: int = 0) -> Path:
    """Write a minimal capture-run.sh-style directory (files directly in d)."""
    d.mkdir(parents=True, exist_ok=True)
    (d / "score.txt").write_text(f"score: {score}\n")
    (d / "kpi-fill.txt").write_text(
        f"FULL-DAY RATE: 1/1 = {full_day}%  (golden ~91%)\n"
    )
    (d / "kpi-resemblance.txt").write_text(
        f"ricorrenza UNPINNED: 1/1 = {recurrence}%\n"
    )
    (d / "kpi-daystart.txt").write_text(
        f"DAY-START ANCHORED RATE: 1/1 = {day_start}%\n"
    )
    (d / "kpi-holes.txt").write_text(
        "TOTALI PER CLASSE:\n  OK-pieno         1\n  BUCO-INTERNO     %d\n" % holes
    )
    return d


def test_parse_real_format() -> None:
    print("test_parse_real_format")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        d = write_capture(root, score="0hard/-963616medium/7997soft",
                          full_day=90.9, recurrence=75.6, day_start=98.1, holes=0)
        cap = cr.parse_capture(str(d))
        check("hard parsed", cap.hard == 0, str(cap.hard))
        check("medium parsed (negative preserved)", cap.medium == -963616, str(cap.medium))
        check("full_day parsed", abs(cap.full_day - 90.9) < 1e-9)
        check("recurrence parsed", abs(cap.recurrence_unpinned - 75.6) < 1e-9)
        check("day_start parsed", abs(cap.day_start - 98.1) < 1e-9)
        check("holes parsed from TOTALI block", cap.holes == 0, str(cap.holes))
        check("no missing files", cap.missing == [], str(cap.missing))


def test_parse_negative_hard() -> None:
    print("test_parse_negative_hard")
    with tempfile.TemporaryDirectory() as td:
        d = write_capture(Path(td), score="-6hard/-1074257medium/8115soft", holes=18)
        cap = cr.parse_capture(str(d))
        check("negative hard parsed", cap.hard == -6, str(cap.hard))
        check("holes=18 parsed", cap.holes == 18, str(cap.holes))


def test_pass_when_candidate_matches_baseline() -> None:
    print("test_pass_when_candidate_matches_baseline")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        b = write_capture(root / "base", score="0hard/-960000medium/8000soft")
        c = write_capture(root / "cand", score="0hard/-960000medium/8000soft")
        base = cr.parse_capture(str(b))
        cand = cr.parse_capture(str(c))
        vs = cr.compare(base, cand, cr.DEFAULT_SPEC, single=True)
        statuses = {v.name: v.status for v in vs}
        label, code = cr.overall_verdict(vs)
        check("all pass", all(s in ("pass", "n/a") for s in statuses.values()), str(statuses))
        check("verdict PASS", label == "PASS" and code == 0, f"{label}/{code}")


def test_regression_on_full_day_beyond_fail() -> None:
    print("test_regression_on_full_day_beyond_fail")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        b = write_capture(root / "base", score="0hard/-960000medium/8000soft", full_day=90.0)
        c = write_capture(root / "cand", score="0hard/-960000medium/8000soft", full_day=84.0)
        base = cr.parse_capture(str(b))
        cand = cr.parse_capture(str(c))
        vs = cr.compare(base, cand, cr.DEFAULT_SPEC, single=True)
        fd = next(v for v in vs if v.name == "full_day")
        label, code = cr.overall_verdict(vs)
        check("full_day regression (delta -6pp > fail 5)", fd.status == "regression", fd.detail)
        check("overall REGRESSION exit 1", label == "REGRESSION" and code == 1, f"{label}/{code}")


def test_warn_inside_band() -> None:
    print("test_warn_inside_band")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        b = write_capture(root / "base", score="0hard/-960000medium/8000soft", recurrence=75.0)
        c = write_capture(root / "cand", score="0hard/-960000medium/8000soft", recurrence=72.0)
        base = cr.parse_capture(str(b))
        cand = cr.parse_capture(str(c))
        vs = cr.compare(base, cand, cr.DEFAULT_SPEC, single=True)
        rec = next(v for v in vs if v.name == "recurrence_unpinned")
        label, code = cr.overall_verdict(vs)
        check("recurrence warn (delta -3pp in (warn2, fail5))", rec.status == "warn", rec.detail)
        check("overall PASS-with-warnings exit 0", label.startswith("PASS") and code == 0, f"{label}/{code}")


def test_single_hard_negative_is_inconclusive() -> None:
    print("test_single_hard_negative_is_inconclusive")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        b = write_capture(root / "base", score="0hard/-960000medium/8000soft")
        c = write_capture(root / "cand", score="-6hard/-1074257medium/8115soft")
        base = cr.parse_capture(str(b))
        cand = cr.parse_capture(str(c))
        vs = cr.compare(base, cand, cr.DEFAULT_SPEC, single=True)
        hard = next(v for v in vs if v.name == "hard")
        label, code = cr.overall_verdict(vs)
        check("single hard<0 is INCONCLUSIVE (not regression)", hard.status == "inconclusive", hard.detail)
        check("overall INCONCLUSIVE exit 2", label == "INCONCLUSIVE" and code == 2, f"{label}/{code}")


def test_aggregate_hard_negative_is_regression() -> None:
    print("test_aggregate_hard_negative_is_regression")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        base = write_capture(root / "base", score="0hard/-960000medium/8000soft")
        agg = root / "agg"
        # Three attempts, best hard is -3 (none reach 0-hard): basin got worse.
        for i, sc in enumerate(["-9hard/-1074000medium/8100soft",
                                "-3hard/-1060000medium/8200soft",
                                "-6hard/-1050000medium/8300soft"]):
            write_capture(agg / f"run{i}", score=sc, full_day=89.0, recurrence=74.0)
        b = cr.parse_capture(str(base))
        c = cr.aggregate_captures(str(agg))
        check("aggregate best hard = -3", c.hard == -3, str(c.hard))
        check("aggregate medium = median", c.medium == -1060000, str(c.medium))
        vs = cr.compare(b, c, cr.DEFAULT_SPEC, single=False)
        hard = next(v for v in vs if v.name == "hard")
        label, code = cr.overall_verdict(vs)
        check("aggregate hard<0 is REGRESSION (confirmed)", hard.status == "regression", hard.detail)
        check("overall REGRESSION exit 1", label == "REGRESSION" and code == 1, f"{label}/{code}")


def test_aggregate_one_zero_hard_passes() -> None:
    print("test_aggregate_one_zero_hard_passes")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        base = write_capture(root / "base", score="0hard/-960000medium/8000soft")
        agg = root / "agg"
        for i, sc in enumerate(["0hard/-960000medium/8000soft", "-6hard/-1074000medium/8100soft"]):
            write_capture(agg / f"run{i}", score=sc)
        b = cr.parse_capture(str(base))
        c = cr.aggregate_captures(str(agg))
        check("aggregate best hard = 0 (one attempt hit)", c.hard == 0, str(c.hard))
        vs = cr.compare(b, c, cr.DEFAULT_SPEC, single=False)
        hard = next(v for v in vs if v.name == "hard")
        check("aggregate reaching 0-hard is PASS", hard.status == "pass", hard.detail)


def test_missing_baseline_dir_is_error_exit3() -> None:
    print("test_missing_baseline_dir_is_error_exit3")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        c = write_capture(root / "cand", score="0hard/-960000medium/8000soft")
        code = cr.main(["--baseline", str(root / "does-not-exist"), "--candidate", str(c)])
        check("missing baseline -> exit 3", code == 3, str(code))


def test_strict_promotes_warn_to_fail() -> None:
    print("test_strict_promotes_warn_to_fail")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        b = write_capture(root / "base", score="0hard/-960000medium/8000soft", recurrence=75.0)
        c = write_capture(root / "cand", score="0hard/-960000medium/8000soft", recurrence=72.0)
        code = cr.main(["--baseline", str(b), "--candidate", str(c), "--strict"])
        check("--strict turns warn into exit 1", code == 1, str(code))


def test_missing_aggregate_dir_is_error_exit3() -> None:
    # Regression test for skeptic C4: a nonexistent --aggregate dir used to
    # return exit 0 (PASS) because the "<aggregate dir not found" sentinel did
    # not match the "<dir not found" substring guard. A false PASS is the worst
    # outcome for a regression detector, so this must be exit 3.
    print("test_missing_aggregate_dir_is_error_exit3")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        b = write_capture(root / "base", score="0hard/-960000medium/8000soft")
        code = cr.main(["--baseline", str(b),
                        "--aggregate", str(root / "nope-aggregate")])
        check("missing aggregate dir -> exit 3 (skeptic C4)", code == 3, str(code))


def test_empty_candidate_dir_is_error_exit3() -> None:
    # Soft gap from skeptic C4: a candidate dir that exists but yields zero
    # parseable KPIs would otherwise produce all-n/a -> PASS.
    print("test_empty_candidate_dir_is_error_exit3")
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        b = write_capture(root / "base", score="0hard/-960000medium/8000soft")
        empty = root / "empty"
        empty.mkdir()
        code = cr.main(["--baseline", str(b), "--candidate", str(empty)])
        check("candidate with no KPI files -> exit 3 (no false PASS)", code == 3, str(code))


def main() -> int:
    print("=== check-regression.py self-test ===")
    test_parse_real_format()
    test_parse_negative_hard()
    test_pass_when_candidate_matches_baseline()
    test_regression_on_full_day_beyond_fail()
    test_warn_inside_band()
    test_single_hard_negative_is_inconclusive()
    test_aggregate_hard_negative_is_regression()
    test_aggregate_one_zero_hard_passes()
    test_missing_baseline_dir_is_error_exit3()
    test_strict_promotes_warn_to_fail()
    test_missing_aggregate_dir_is_error_exit3()
    test_empty_candidate_dir_is_error_exit3()
    print("-" * 50)
    if FAILURES:
        print(f"RESULT: FAIL ({len(FAILURES)} check(s) failed)")
        for f in FAILURES:
            print(f"  - {f}")
        return 1
    print("RESULT: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
