#!/usr/bin/env bash
# Hook: Check for background pipe chains before Bash tool execution
#
# This hook prevents the antipattern of running commands with pipes
# in the background, which creates orphaned processes that hang forever.
#
# BLOCKED: Commands with pipes (|), chains (&&, ||), or subshells
#          when run_in_background=true is used
#
# 2026-09-05 fix: the original line-15 regex contained a raw '&' inside the
# ERE (run_in_background.*true|&\s*$) which is a bash SYNTAX ERROR at parse
# time: the hook exited 2 on every invocation, which the harness reads as
# DENY, locking the whole Bash tool. Rewritten as two separate conditions.

# Check if this is a background command: either the harness flag is set,
# or the command line itself ends with '&'
CMD_LINE="${CLAUDE_INVOCATION_COMMAND:-}"
IS_BG=0
case "$CMD_LINE" in
    *run_in_background*true*) IS_BG=1 ;;
esac
# trailing ampersand, optionally followed by spaces
LAST_CHAR="${CMD_LINE: -1}"
if [[ "$LAST_CHAR" == "&" ]]; then
    IS_BG=1
fi

if [[ "$IS_BG" == 1 ]]; then
    # Extract the actual command (remove run_in_background and other flags)
    ACTUAL_CMD="${CLAUDE_INVOCATION_COMMAND#*run_in_background*true}"

    # Check for dangerous patterns in background commands
    if [[ "$ACTUAL_CMD" == *\|* ]] || [[ "$ACTUAL_CMD" == *&&* ]] || [[ "$ACTUAL_CMD" == *||* ]]; then
        echo "X BLOCKED: Background commands with pipes (|) create orphaned processes."
        echo ""
        echo "Problem: The command contains a pipe which will hang if run in background."
        echo ""
        echo "Fix options:"
        echo "  1. Run synchronously (remove run_in_background=true)"
        echo "  2. Use a wrapper script instead"
        echo "  3. Rewrite to avoid pipes"
        echo ""
        echo "See: .claude/rules/no-background-pipes.md"
        exit 2
    fi
fi

# Not a background command, or a safe one: allow
exit 0
