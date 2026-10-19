#!/usr/bin/env python3
"""Generate a per-arm solverConfig.xml variant for the honest live-A/B protocol.

TWO benchmark families, same machinery:
  - Phase-2 metaheuristic sweep (arms A-F, TASK-386): varies phase 2's
    (LS - Compactness) acceptor/forager/pillars only. VERDICT: no phase-2 config
    beats control reproducibly (see claudedocs/spalmone-2026-27/benchmark-386-results/).
  - Phase-3 Ruin & Recreate sweep (arms R1-R2, TASK-391): RETIRED by TASK-420
    (2026-07-27), which removed the RuinRecreate local search from the pipeline
    after 6 consecutive runs in which it changed nothing. The arms are kept here
    as a record and now abort with an explanation instead of generating a config
    that cannot exist; R1's own finding (ruin 5-20 beats 2-5) is what the removed
    phase shipped with.

Source of truth: the CURRENT src/main/resources/solverConfig.xml. Every phase and
transition marker NOT named by the chosen arm is copied byte-for-byte via
ElementTree round-trip. A phase-2 arm touches only phase 2; an R arm touches only
nothing else. Never writes back to the live solverConfig.xml
- output goes to local/benchmark-arms/ only, and this script never calls
planner.sh/mvn/api.sh. ALWAYS validate the output against the XSD before use:
  bash scripts/validate/validate-solver-xml.sh local/benchmark-arms/solverConfig-arm-<K>.xml

Usage:
  python3 scripts/benchmark/generate-benchmark-arm.py --arm A     # phase-2 arm
  python3 scripts/benchmark/generate-benchmark-arm.py --all
  python3 scripts/benchmark/generate-benchmark-arm.py --all --source path/to/solverConfig.xml --out-dir local/benchmark-arms
"""
import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

NS = "https://timefold.ai/xsd/solver"
ET.register_namespace('', NS)

# Comparable to runs 12-15 (the live solverConfig.xml currently carries 5400 for
# the run-16 "just needs time" control experiment, TASK-386 note) - the honest
# metaheuristic arms hold the budget fixed so only the search knob varies.
PHASE2_SECONDS_SPENT_LIMIT = "3600"

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class Arm:
    desc: str
    # Phase-2 knobs — None on an R arm means "leave phase 2 exactly as the source
    # config has it" (which IS the control, so R arms isolate the phase-3 change).
    acceptor: Optional[dict] = None
    accepted_count_limit: Optional[str] = None
    remove_pillars: bool = False
    # Phase-3 ruin counts (min, max), RETIRED with the phase (TASK-420): a non-None
    # value now only marks the arm as unbuildable.
    ruin: Optional[tuple] = None


ARMS = {
    # --- Phase-2 metaheuristic sweep (TASK-386) ---
    "A": Arm("control: entityTabu 7 / acceptedCountLimit 1000 (today's config)",
             {"entityTabuSize": "7"}, "1000"),
    "B": Arm("lateAcceptanceSize 400 instead of tabu",
             {"lateAcceptanceSize": "400"}, "1000"),
    "C": Arm("tabu 7 / acceptedCountLimit 500",
             {"entityTabuSize": "7"}, "500"),
    "D": Arm("tabu 7 / acceptedCountLimit 2000",
             {"entityTabuSize": "7"}, "2000"),
    "E": Arm("tabu 11 / acceptedCountLimit 1000",
             {"entityTabuSize": "11"}, "1000"),
    "F": Arm("tabu 7 / acceptedCountLimit 1000, pillar moves removed (change/swap only)",
             {"entityTabuSize": "7"}, "1000", remove_pillars=True),
    # --- B-TIMESLOT-1 metaheuristic sweep (notte2 2026-07-31): the 2026/27 campaign
    # benchmarks the staged B-TIMESLOT-1 stage (which runs solverConfig.xml's phase-2 LS
    # verbatim via timeslotPhasesConfig). Two extra knobs the TASK-386 family did not
    # cover: a second late-acceptance size and a shorter entity-tabu. Every arm below
    # holds acceptedCountLimit at 1000 so ONLY the acceptor knob varies. ---
    "G": Arm("lateAcceptanceSize 1000 / acceptedCountLimit 1000",
             {"lateAcceptanceSize": "1000"}, "1000"),
    "H": Arm("entityTabuSize 3 / acceptedCountLimit 1000 (short memory)",
             {"entityTabuSize": "3"}, "1000"),
    # --- Phase-3 Ruin & Recreate sweep (TASK-391), RETIRED by TASK-420 ---
    "R1": Arm("RETIRED (TASK-420): phase-3 ruin 5-20, the phase no longer exists", ruin=("5", "20")),
    "R2": Arm("RETIRED (TASK-420): phase-3 ruin 10-40, the phase no longer exists", ruin=("10", "40")),
}


def q(tag):
    return f"{{{NS}}}{tag}"


def _localsearch_phases(root):
    """The <localSearch> phases in document order. Since TASK-420 the pipeline has
    two of them: [phase 2 (Compactness), phase 5 (room refinement)]. Phase ordering
    is fixed by the pipeline design, not by any id/name attribute, so document order
    is the only reliable selector."""
    ls_elements = root.findall(q("localSearch"))
    if len(ls_elements) < 2:
        raise SystemExit(
            f"Expected at least 2 <localSearch> phases (compactness, room refinement) in source config, "
            f"found {len(ls_elements)} - the pipeline shape changed since this script was written; "
            "fix the selector before trusting its output.")
    return ls_elements


def find_phase2_localsearch(root):
    return _localsearch_phases(root)[0]


def patch_phase2(localsearch_el, arm_key):
    arm = ARMS[arm_key]
    assert arm.acceptor is not None and arm.accepted_count_limit is not None, \
        f"patch_phase2 called for arm {arm_key} without phase-2 knobs set"

    termination = localsearch_el.find(q("termination"))
    if termination is None:
        raise SystemExit("phase-2 <localSearch> has no <termination> - source config shape changed, aborting")
    ssl = termination.find(q("secondsSpentLimit"))
    if ssl is None:
        raise SystemExit("phase-2 <termination> has no <secondsSpentLimit> - source config shape changed, aborting")
    ssl.text = PHASE2_SECONDS_SPENT_LIMIT

    if arm.remove_pillars:
        union = localsearch_el.find(q("unionMoveSelector"))
        if union is None:
            raise SystemExit("phase-2 <localSearch> has no <unionMoveSelector> - source config shape changed, aborting")
        for tag in ("pillarChangeMoveSelector", "pillarSwapMoveSelector"):
            for el in union.findall(q(tag)):
                union.remove(el)

    acceptor = localsearch_el.find(q("acceptor"))
    if acceptor is None:
        raise SystemExit("phase-2 <localSearch> has no <acceptor> - source config shape changed, aborting")
    for child in list(acceptor):
        acceptor.remove(child)
    for tag, value in arm.acceptor.items():
        el = ET.SubElement(acceptor, q(tag))
        el.text = value

    forager = localsearch_el.find(q("forager"))
    if forager is None:
        raise SystemExit("phase-2 <localSearch> has no <forager> - source config shape changed, aborting")
    acl = forager.find(q("acceptedCountLimit"))
    if acl is None:
        raise SystemExit("phase-2 <forager> has no <acceptedCountLimit> - source config shape changed, aborting")
    acl.text = arm.accepted_count_limit


def generate(arm_key, source_path, out_dir):
    tree = ET.parse(source_path)
    root = tree.getroot()
    arm = ARMS[arm_key]
    if arm.ruin is not None:
        raise SystemExit(
            f"Arm {arm_key} tunes the RuinRecreate local search, removed from the pipeline by "
            "TASK-420 (inert in 6 consecutive measurements). Nothing to generate.")
    patch_phase2(find_phase2_localsearch(root), arm_key)
    touched = ("only phase 2's acceptor, forager,\n"
               "     pillar-selectors (arm F only) and secondsSpentLimit were changed.")

    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / f"solverConfig-arm-{arm_key}.xml"
    tree.write(out_path, encoding="UTF-8", xml_declaration=True)

    header = (
        f"<!-- AUTO-GENERATED by scripts/benchmark/generate-benchmark-arm.py (TASK-386/391).\n"
        f"     Arm {arm_key}: {arm.desc}\n"
        f"     Source: {source_path} (every phase + transition marker NOT named by\n"
        f"     this arm copied verbatim via ElementTree round-trip; {touched}\n"
        f"     Comments from the source file are lost in the round-trip (ElementTree\n"
        f"     drops them on parse) - read solverConfig.xml itself for phase rationale.\n"
        f"     VALIDATE against the XSD before use:\n"
        f"       bash scripts/validate/validate-solver-xml.sh {out_path}\n"
        f"     Orchestrator use only: see scripts/benchmark/run-honest-ab.sh for the\n"
        f"     manual apply/rebuild/restore procedure. Never copied automatically. -->\n"
    )
    content = out_path.read_text()
    lines = content.split("\n", 1)
    out_path.write_text(lines[0] + "\n" + header + (lines[1] if len(lines) > 1 else ""))
    return out_path


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--arm", choices=list(ARMS.keys()), help="Single arm to generate")
    ap.add_argument("--all", action="store_true", help="Generate all phase-2 arms A-F")
    ap.add_argument("--source", default="src/main/resources/solverConfig.xml")
    ap.add_argument("--out-dir", default="local/benchmark-arms")
    args = ap.parse_args()

    source_path = Path(args.source)
    out_dir = Path(args.out_dir)
    if not source_path.exists():
        raise SystemExit(f"Source config not found: {source_path}")

    # --all covers the live phase-2 family only; the retired R arms abort by design.
    targets = ([k for k, a in ARMS.items() if a.ruin is None] if args.all
               else ([args.arm] if args.arm else []))
    if not targets:
        raise SystemExit("Specify --arm {A..F} or --all")

    for arm_key in targets:
        out_path = generate(arm_key, source_path, out_dir)
        print(f"[{arm_key}] {ARMS[arm_key].desc}")
        print(f"    -> {out_path}")


if __name__ == "__main__":
    main()
