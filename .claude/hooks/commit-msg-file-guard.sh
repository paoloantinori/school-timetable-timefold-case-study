#!/bin/bash
set -euo pipefail

# Commit-msg file guard (maintainer, 9/09: "hai bisogno di hooks?"): `git commit -F
# <file>` does NOT refresh .git/COMMIT_EDITMSG before the pre-commit gate runs,
# so the gate greps a STALE message. Two failure directions, both real: a stale
# message WITHOUT gate markers blocks a legit commit (happened 9/09, three
# wasted minutes diagnosing), and worse, a stale message WITH the markers would
# let a gateless commit PASS silently. This hook blocks any `git commit -F`
# (or --file=) that does not, in the SAME command, sync the message file to
# .git/COMMIT_EDITMSG first. The compliant form:
#   cp <msgfile> .git/COMMIT_EDITMSG && git commit -F <msgfile>

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

if echo "$command" | grep -qE 'git +commit +(-[a-zA-Z]*F[a-zA-Z]* |--file=)' \
        && ! echo "$command" | grep -q 'COMMIT_EDITMSG'; then
  echo "BLOCKED: git commit -F SENZA il sync di .git/COMMIT_EDITMSG." >&2
  echo "Il pre-commit gate legge .git/COMMIT_EDITMSG, che -F da solo NON aggiorna:" >&2
  echo "legge un messaggio STANTIO (falso blocco oggi, falso PASS domani)." >&2
  echo "Forma corretta:" >&2
  echo "  cp <msgfile> .git/COMMIT_EDITMSG && git commit -F <msgfile>" >&2
  exit 2
fi

exit 0
