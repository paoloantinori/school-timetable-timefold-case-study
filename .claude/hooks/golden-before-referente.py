#!/usr/bin/env python3
"""Golden-before-referente gate (maintainer, 21/09: "te lo devo sempre dire io").

Every time an artifact OPENS a new question for referente (the phrasings below)
without carrying the golden analysis in the same payload, the tool call is
BLOCKED. The golden is the authority on the data (direttiva golden-first):
a referente question filed without having asked the golden first is a question
filed too early. To pass the gate, the same payload must reference the
golden analysis it rests on (any mention of "golden" travels with it).

Historical quotes ("conferma referente a posteriori", the ratification ledger)
do NOT match the trigger pattern: only new-question phrasings do.
"""
import json
import re
import sys

referente_QUESTION = re.compile(
    r"(domand[ae] (per|a) referente|domanda da (porre|fare) a referente"
    r"|da chiedere a referente|chiedere a referente|chieder[oa] a referente"
    r"|porre la domanda a referente|question for referente|ask referente)",
    re.IGNORECASE,
)
GOLDEN_EVIDENCE = re.compile(r"golden", re.IGNORECASE)


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0  # not our business: never break on malformed input
    blob = json.dumps(payload, ensure_ascii=False)
    if referente_QUESTION.search(blob) and not GOLDEN_EVIDENCE.search(blob):
        print(
            "⛔ golden-before-referente: stai aprendo una domanda per referente senza"
            " l'analisi golden nello stesso artefatto. La direttiva golden-first"
            " (e la lezione G06 06-09 del 21/09, arrivata solo quando maintainer"
            " ha detto 'guarda il golden') esige che il golden venga interrogato"
            " PRIMA: verifica cosa pratica il golden su quella cella/numero e"
            " includi l'evidenza nel testo, oppure dichiara perche' il golden"
            " non e' consultabile su questa domanda.",
            file=sys.stderr,
        )
        return 2  # block
    return 0


if __name__ == "__main__":
    sys.exit(main())
