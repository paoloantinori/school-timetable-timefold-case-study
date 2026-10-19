#!/usr/bin/env python3
"""
Self-test for capture-doctor.sh (TASK-579.01, AC#4).

Convention (matches scripts/benchmark/test_check_regression.py): exit 0 on full
pass, non-zero on any failure, one line per check. Run directly:

    python3 scripts/benchmark/test_capture_doctor.py

Synthesises minimal capture directories in a tempdir (score.txt, analysis.json,
export.json.gz, sanity-report.txt as needed) and drives the bash doctor through
subprocess, asserting the verdict line and exit code for every documented
scenario, including the zero-first-coordinate Bendable case the old doctor
was blind to.
"""
from __future__ import annotations

import gzip
import json
import subprocess
import sys
import tempfile
from pathlib import Path

DOCTOR = Path(__file__).resolve().parent / "capture-doctor.sh"

FAILURES: list[str] = []


def make_capture(root: Path, name: str, files: dict) -> Path:
    d = root / name
    d.mkdir()
    for fname, content in files.items():
        p = d / fname
        if fname.endswith(".gz"):
            p.write_bytes(gzip.compress(content.encode()))
        else:
            p.write_text(content)
    return d


def run_doctor(d: Path) -> tuple[int, str]:
    r = subprocess.run(["bash", str(DOCTOR), str(d)], capture_output=True, text=True)
    return r.returncode, (r.stdout + r.stderr).strip()


def check(name: str, d: Path, want_exit: int, *substrings: str) -> None:
    code, out = run_doctor(d)
    ok = code == want_exit and all(s in out for s in substrings)
    print(f"{'PASS' if ok else 'FAIL'}  {name}: exit={code} {out[:140]}")
    if not ok:
        FAILURES.append(name)


# must stay >= 1KB after gzip (the doctor's corruption floor), so use
# poorly-compressible content, not a long repeated run
GOOD_EXPORT = json.dumps({"lessons": [f"{i:09d}-{'qzw7k2m9v'[i % 9]}" for i in range(400)]})
SANITY_OK = "SANITY: CONFORME\n"
SANITY_RED = "SANITY: NON CONFORME - rosso su 1 gruppo/i: G14\nROSSO gg sotto min\n"


def analysis(hard: int, quasi: int = 0, medium: int = -787915) -> str:
    return json.dumps({"score": f"[{hard}/{quasi}]hard/[{medium}/-1]soft",
                       "hard": hard, "quasi": quasi, "medium": medium, "soft": -1,
                       "constraints": []})


def main() -> int:
    # the scenario table: every capture starts from this complete baseline and
    # each scenario states ONLY its delta (one readable line per case)
    base = {"score.txt": "score: [0/0]hard/[0/0]soft\n",
            "analysis.json": analysis(0, 0),
            "export.json.gz": GOOD_EXPORT,
            "sanity-report.txt": SANITY_OK,
            "phases.json": "{}"}
    cases = [
        # (name, file-overrides-or-missing-keys, want_exit, expected substrings)
        ("zero-hard-bendable", {}, 0,
         ["DOCTOR-OK", "hard=[0/0]", "zero-hard: both coordinates"]),
        ("matching-nonzero-consistent",
         {"score.txt": "score: [-13/0]hard/[-1245173/-16017]soft\n",
          "analysis.json": analysis(-13, 0), "sanity-report.txt": SANITY_RED}, 0,
         ["DOCTOR-CONSISTENT-NONZERO", "hard=[-13/0]", "NOT zero-hard"]),
        ("coord1-mismatch-fails",
         {"score.txt": "score: [-13/0]hard/[-1/-2]soft\n", "analysis.json": analysis(-12, 0)}, 1,
         ["DOCTOR-FAIL", "hard VECTOR diverges", "[-13/0]", "[-12/0]"]),
        # the exact shape the old first-coordinate-only extraction was blind to:
        # SECOND coordinate mismatch with a ZERO first coordinate
        ("coord2-mismatch-zero-first-fails",
         {"score.txt": "score: [0/-3]hard/[-1/-2]soft\n"}, 1,
         ["DOCTOR-FAIL", "hard VECTOR diverges", "[0/-3]", "[0/0]"]),
        ("legacy-plain-score",
         {"score.txt": "score: -5hard/-1000soft\n", "analysis.json": analysis(-5, 0)}, 0,
         ["DOCTOR-CONSISTENT-NONZERO", "hard=[-5/0]"]),
        ("legacy-zero-ok",
         {"score.txt": "score: 0hard/-1000soft\n"}, 0,
         ["DOCTOR-OK", "hard=[0/0]"]),
        ("missing-export-not-certifiable", {"export.json.gz": None}, 0,
         ["DOCTOR-WARN", "NOT-DELIVERY-CERTIFIABLE", "no-export"]),
        ("missing-analysis-not-certifiable", {"analysis.json": None}, 0,
         ["DOCTOR-WARN", "NOT-DELIVERY-CERTIFIABLE", "no-score-evidence"]),
        ("missing-sanity-not-certifiable", {"sanity-report.txt": None}, 0,
         ["DOCTOR-WARN", "NOT-DELIVERY-CERTIFIABLE", "no-sanity"]),
        ("one-byte-export-fails", {"export.json.gz": "x"}, 1,
         ["DOCTOR-FAIL", "1-byte-corruption"]),
        # pre-Bendable capture: analysis.json carries NO quasi key - the implicit
        # zero tier must make both sides compare instead of blocking the evidence
        ("pre-bendable-analysis-compares",
         {"score.txt": "score: -234hard/-964300medium/17548soft\n",
          "analysis.json": json.dumps({"hard": -234, "medium": -964300, "soft": 17548})},
         0, ["DOCTOR-CONSISTENT-NONZERO", "hard=[-234/0]"]),
        # a score.txt with an init line above the final: the LAST match compares
        ("init-line-above-final-score",
         {"score.txt": "init score: [-2226/0]hard/[-1/-2]soft\nscore: [-13/0]hard/[-1245173/-16017]soft\n",
          "analysis.json": analysis(-13, 0)}, 0,
         ["DOCTOR-CONSISTENT-NONZERO", "hard=[-13/0]"]),
        # hand-assembled plain export with garbage content must not certify
        ("garbage-plain-export-fails",
         {"export.json.gz": None, "export.json": "GARBAGE NOT JSON " * 120}, 1,
         ["DOCTOR-FAIL", "does not parse as JSON"]),
        # sanity-reds counts red GROUPS (the header), not the up-to-3 ROSSO detail
        # lines each group can emit
        ("sanity-reds-counts-groups",
         {"score.txt": "score: [0/0]hard/[0/0]soft\n",
          "sanity-report.txt": "SANITY: NON CONFORME - rosso su 2 gruppo/i: G14, G07\nROSSO a\nROSSO b\nROSSO c\nROSSO d\nROSSO e\nROSSO f\n"}, 0,
         ["DOCTOR-OK", "sanity-reds=2"]),
    ]
    with tempfile.TemporaryDirectory(prefix="doctor-test-") as tmp:
        root = Path(tmp)
        for name, overrides, want_exit, substrings in cases:
            files = {**base, **{k: v for k, v in overrides.items() if v is not None}}
            for k, v in overrides.items():
                if v is None:
                    files.pop(k, None)
            check(name, make_capture(root, name, files), want_exit, *substrings)

    print()
    if FAILURES:
        print(f"FAILED: {len(FAILURES)} -> {FAILURES}")
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
