#!/usr/bin/env bash
# Hook: force the snapshot-first question before ANY full solver-cycle launch
# (CLAUDE.md direttiva "ITERAZIONE SU SNAPSHOT", maintainer 6/09: after twice in one
# morning having to remind the orchestrator to use benches/snapshots instead
# of full cycles, the reminder became mechanical).
#
# Fires on Bash commands that look like a full staged-cycle launch or a mandate
# carrying one. DENIES unless the command carries the acknowledgment marker
# SNAPSHOT-OK, which means the operator answered, for THIS launch:
#   - it is the ADOPTION/validation gate (a candidate already won on bench/snapshot), OR
#   - the capture of fresh boundary snapshots is the purpose (flag capture ON), OR
#   - a localized bench genuinely cannot answer this question (say which one in the report).
# Anything else: run the localized bench / snapshot iteration instead.

CMD_LINE="${CLAUDE_INVOCATION_COMMAND:-}"

case "$CMD_LINE" in
    *solve-staged*|*launch-preflight*|*prod-start*|*multistart-ab*|*overnight-benchmark*|*run-benchmarker*)
        case "$CMD_LINE" in
            *SNAPSHOT-OK*)
                exit 0
                ;;
            *)
                echo "X BLOCKED: full-cycle launch without the snapshot-first acknowledgment." >&2
                echo "" >&2
                echo "Direttiva ITERAZIONE SU SNAPSHOT (CLAUDE.md, maintainer 6/09): il ciclo completo e'" >&2
                echo "SOLO il gate di adozione / la cattura di snapshot freschi." >&2
                echo "" >&2
                echo "Prima di rilanciare, rispondi (a te stesso e nel report):" >&2
                echo "  1. Questa misura la puo' dare il banco localizzato (minuti)?" >&2
                echo "  2. La puo' dare l'iterazione su uno snapshot di confine gia' catturato?" >&2
                echo "  3. Se siamo vicini a 0-hard: perche' non warm-start sullo snapshot?" >&2
                echo "  4. Se e' DAVVERO il ciclo di adozione/cattura: -Dplanner.checkpoint.capture.enabled=true c'e'?" >&2
                echo "" >&2
                echo "Rilancia lo STESSO comando con 'SNAPSHOT-OK' aggiunto per confermare" >&2
                echo "(la conferma va motivata nel report del turno)." >&2
                exit 2
                ;;
        esac
        ;;
esac

exit 0
