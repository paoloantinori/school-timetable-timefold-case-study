#!/usr/bin/env python3
"""Snapshot-first bench gate (maintainer, 21/09: "non te lo devo dire io").

Launching a full measure bench or solve cycle from scratch is the LAST
resort: the canon is ITERAZIONE SU SNAPSHOT (direttiva 6/09) + the TASK-634
post-LS catalog. Fix validation and cure iteration run on catalogued states
(minutes); only adoption gates, end-to-end campaign measures and fresh
checkpoint captures pay a full bench (hours).

The hook BLOCKS a bench/cycle launch unless the SAME command declares WHY the
field run is legitimate, via any of:
  - a replay/catalog surface: post_ls_catalog.py, trio.export=, trio.run with
    PLANNER_TEST_REPLAY, lsvar.ck= (checkpoint replay bench is a measure of
    the races themselves - allowed, it is the campaign-rate instrument),
    capture-run.sh / planner.checkpoint.capture (the capture IS the purpose);
  - an explicit justification marker: SNAPSHOT-FIRST:<reason>.
To pass the gate deliberately, write the reason into the command, e.g.:
  PLANNER_SNAPSHOT_FIRST: gate di adozione, serve la consegna end-to-end
Any of the substrings "SNAPSHOT-FIRST:" or "PLANNER_SNAPSHOT_FIRST:" counts.
"""
import json
import re
import sys

LAUNCH = re.compile(
    r"(-Dlsvar\.run=true|-Dtrio\.run=true"
    r"|solve-staged|run-measure\.sh"
    r"|overnight-benchmark|run-honest-ab)",
    re.IGNORECASE,
)
LEGITIMATE = re.compile(
    r"(post_ls_catalog|trio\.export=|PLANNER_TEST_REPLAY"
    r"|lsvar\.ck=|capture-run\.sh|planner\.checkpoint\.capture"
    r"|SNAPSHOT-FIRST:|PLANNER_SNAPSHOT_FIRST:)",
    re.IGNORECASE,
)


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0
    tool = payload.get("tool_name", "")
    if tool != "Bash":
        return 0
    command = (payload.get("tool_input") or {}).get("command", "")
    if not command or not LAUNCH.search(command):
        return 0
    if LEGITIMATE.search(command):
        return 0
    print(
        "SNAPSHOT-FIRST BENCH GATE (maintainer 21/09): stai per lanciare un bench o un "
        "ciclo di misura DA CAPO. La scala obbligata e': test localizzati -> replay "
        "sugli stati del catalogo (scripts/analyze/post_ls_catalog.py, banco "
        "DeliveryTrioBenchTest con trio.export=) -> warm start su snapshot -> SOLO "
        "il gate di adozione/la misura di campagna paga un bench completo. Se il "
        "lancio e' davvero l'strumento giusto (gate di adozione, misura della "
        "frequenza con n ampio, cattura di snapshot freschi), ripeti il comando "
        "con la motivazione inline: aggiungi 'SNAPSHOT-FIRST: <perche' il campo e' "
        "il misurando giusto>' al comando.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
