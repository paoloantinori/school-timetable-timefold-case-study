#!/usr/bin/env python3
"""TASK-441 - Verify whether M21_EXEMPT_SUBJECTS is derivable from INPUT alone.

The no-golden-year question (direttiva solo-input): when the golden disappears,
which solver references survive? M21_EXEMPT_SUBJECTS (the subjects allowed >2h/day,
M21 cap bypassed) is a Java constant today. TASK-441 asks for a reproducible
derivation. This script tests whether any rule over the INPUT properties in
subjects.csv (roomTags, professionalizzanti) reproduces the authoritative set.

Answer (verified): NO. The three exempt subjects share no consistent INPUT property:
- Competenze Digitali: no tag, professionalizzanti=false
- Disegno: no tag, professionalizzanti=false
- Disegno Edile ed Impiantistico: no tag, professionalizzanti=true
professionalizzanti is mixed; "no roomTag" is shared with ~30 other subjects. So
M21_EXEMPT is a SCHOOL CHOICE (which subjects admit long blocks), not derivable. The
no-golden path is the DECLARED column 08_Materie.capOreGiorno (=0 for exempt,
sistema default, school confirms), not a data derivation.

This mirrors the censimento's constrained-teacher verification
(``/tmp/measure_constrained_teacher.py``, 2026-07-30): a "vale solo adesso" check
that isolates derivable-vs-declared while the golden still cross-validates. Negative
result is the load-bearing finding (it justifies the declared-input path).

Read-only. No solver dependency.

Usage: python3 scripts/analyze/derive-m21-exempt-no-golden.py
"""
from __future__ import annotations

import csv
import sys
from pathlib import Path

# Authoritative M21_EXEMPT_SUBJECTS (SRC: SchoolScheduleConstraintProvider.java:90,
# the set whose members bypass the M21 2h/day cap). Hardcoded here as the oracle;
# the test is whether INPUT properties reproduce it.
M21_EXEMPT = {"Competenze Digitali", "Disegno", "Disegno Edile ed Impiantistico"}

SUBJECTS_CSV = (Path(__file__).resolve().parents[2] / "src" / "main" / "resources"
                / "seed" / "subjects.csv")


def _load_subjects() -> list[dict]:
    """subjects.csv -> [{subject, tags(set), prof(bool)}]."""
    out = []
    with SUBJECTS_CSV.open(newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            tag_cell = (r.get("roomTags") or "").strip()
            tags = frozenset(t.strip() for t in tag_cell.split(";") if t.strip())
            out.append({
                "subject": r["subject"].strip(),
                "tags": tags,
                "prof": (r.get("professionalizzanti") or "").strip().lower() == "true",
            })
    return out


def _pr(rule_name: str, predicted: set, oracle: set) -> str:
    """Precision/recall of a candidate derivation rule vs the oracle set."""
    if not predicted:
        return f"  {rule_name:38} predict=0  (no match possible)"
    tp = len(predicted & oracle)
    precision = 100.0 * tp / len(predicted)
    recall = 100.0 * tp / len(oracle)
    return (f"  {rule_name:38} predict={len(predicted):3d}  "
            f"precision={precision:5.1f}%  recall={recall:5.1f}%")


def main() -> int:
    subs = _load_subjects()
    print(f"M21_EXEMPT oracle ({len(M21_EXEMPT)}): {sorted(M21_EXEMPT)}")
    print(f"subjects.csv: {len(subs)} subjects\n")
    print("Properties of the 3 exempt subjects (the oracle):")
    for s in subs:
        if s["subject"] in M21_EXEMPT:
            print(f"  {s['subject']:38} tags={set(s['tags']) or '{}':14} prof={s['prof']}")
    print("\nCandidate derivation rules (INPUT properties only) vs oracle:")
    no_tag = {s["subject"] for s in subs if not s["tags"]}
    prof = {s["subject"] for s in subs if s["prof"]}
    no_tag_prof = {s["subject"] for s in subs if not s["tags"] and s["prof"]}
    no_tag_notprof = {s["subject"] for s in subs if not s["tags"] and not s["prof"]}
    print(_pr("no roomTag", no_tag, M21_EXEMPT))
    print(_pr("professionalizzanti=true", prof, M21_EXEMPT))
    print(_pr("no roomTag AND prof", no_tag_prof, M21_EXEMPT))
    print(_pr("no roomTag AND NOT prof", no_tag_notprof, M21_EXEMPT))
    print(_pr("EXACT oracle (sanity)", set(M21_EXEMPT), M21_EXEMPT))

    # Verdict: does any rule achieve precision=100% AND recall=100%?
    rules = {"no roomTag": no_tag, "professionalizzanti": prof,
             "no roomTag AND prof": no_tag_prof,
             "no roomTag AND NOT prof": no_tag_notprof}
    perfect = [n for n, p in rules.items() if p == M21_EXEMPT]
    print()
    if perfect:
        print(f"DERIVABLE: rule(s) {perfect} reproduce M21_EXEMPT exactly.")
        return 0
    print("NOT DERIVABLE: no INPUT-property rule reproduces M21_EXEMPT.")
    print("Conclusion: M21_EXEMPT is a school choice (which subjects admit >2h blocks),")
    print("not derivable from subjects.csv. No-golden path = the DECLARED workbook column")
    print("08_Materie.capOreGiorno (=0 for exempt, sistema default, school confirms),")
    print("not a data derivation. Same shape as T20 (settimanaInizioMax/Min).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
