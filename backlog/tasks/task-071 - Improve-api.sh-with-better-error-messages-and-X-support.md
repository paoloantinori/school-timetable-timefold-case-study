---
id: TASK-071
title: Improve api.sh with better error messages and -X support
status: Done
assignee:
  - Claude
created_date: '2026-02-22 16:27'
updated_date: '2026-03-10 05:16'
labels:
  - workflow
  - claude-planned
dependencies: []
priority: medium
ordinal: 35750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The `api.sh` script has full POST/PUT/DELETE support via `post`, `put`, `delete` commands and convenience commands like `pre-assign`. The confusion arises when users try to pass HTTP method flags to commands that don't support them.

**Problem**:
```bash
# User tried this:
./scripts/api/api.sh raw /api/schedule/pre-assign -X POST
# Got: "Unknown option: -X"

# Correct syntax already exists:
./scripts/api/api.sh post /api/schedule/pre-assign
./scripts/api/api.sh pre-assign  # convenience shortcut
```

**Root cause**: The `raw` command is GET-only and doesn't accept `-X` flags. Users don't discover the `post` command easily.

**Improvements needed**:
1. Add `-X METHOD` support to the `raw` command for discoverability
2. Improve "Unknown option" error message to suggest correct command
3. Add `--help` examples showing POST/PUT/DELETE usage
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The `raw` command accepts `-X GET|POST|PUT|DELETE` flag
- [x] #2 Invalid option errors suggest the correct command (e.g., 'Use `api.sh post` for POST requests')
- [x] #3 Help output includes examples of POST/PUT/DELETE with both `-d` flag and stdin
- [x] #4 All existing commands continue to work (backward compatibility)
- [x] #5 Manual test: `./scripts/api/api.sh raw /api/schedule/pre-assign -X POST` triggers pre-assignment
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Approach

### 1. Add -X support to raw command

Modify the option parsing section to accept `-X` or `--method`:

```bash
METHOD="GET"  # default

while [[ $# -gt 0 ]]; do
    case "$1" in
        ...
        -X|--method) METHOD="$2"; shift 2 ;;
        ...
    esac
done
```

Update the `raw` case to use the method:

```bash
raw)
    if [[ -z "$ARGS_STR" ]]; then
        echo "Usage: api.sh raw /api/path [-X GET|POST|PUT|DELETE]" >&2
        exit 2
    fi
    fetch "${ARGS[0]}" "$METHOD" "$BODY" || exit 1
    ;;
```

### 2. Improve error messages

Replace generic "Unknown option" with helpful suggestions:

```bash
-*)
    case "$1" in
        -X|POST|PUT|DELETE)
            echo "Hint: Use 'api.sh post' for POST requests, not 'api.sh raw -X POST'" >&2
            echo "Usage: api.sh post /api/path [-d JSON]" >&2
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Run 'api.sh --help' for available options" >&2
            ;;
    esac
    exit 2
    ;;
```

### 3. Update help examples

Add to the help section:

```
# Examples:
#   api.sh status
#   api.sh solve -t 60
#   api.sh pre-assign
#   api.sh post /api/teachers -d '{"name":"New Teacher"}'
#   echo '{"name":"Test"}' | api.sh post /api/teachers
#   api.sh raw /api/custom -X POST -d '{"key":"value"}'
```

## Testing

```bash
# Should work
./scripts/api/api.sh raw /api/schedule/pre-assign -X POST
./scripts/api/api.sh raw /api/schedule -X GET

# Should show helpful error
./scripts/api/api.sh raw /api/test -X INVALID  # should error with valid methods
```

## Files to Modify

- `scripts/api.sh` — add -X flag support and improve error messages
<!-- SECTION:NOTES:END -->
