#!/usr/bin/env bash
# Import a schedule JSON file via the REST API.
# Usage: ./scripts/api/import-schedule.sh <file.json>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

FILE="${1:?Usage: import-schedule.sh <file.json>}"
PORT="${2:-8080}"

if [ ! -f "$FILE" ]; then
  echo "ERROR: File not found: $FILE"
  exit 1
fi

# TASK-579.01 F1: the JSON import path STRUCTURALLY cannot restore a full
# export (curl -d buffers the whole body and the server caps requests at
# quarkus.http.limits.max-body-size = 50MiB, M = 1024^2 in Quarkus
# MemorySizeConverter), so a ~1.2GB export dies with curl OOM or HTTP 413
# before anything useful happens. Refuse at the cap itself and name the
# DEMONSTRATED restore paths instead.
SIZE_BYTES=$(stat -c %s "$FILE" 2>/dev/null || stat -f %z "$FILE" 2>/dev/null)
if ! [ "$SIZE_BYTES" -gt 0 ] 2>/dev/null; then
  echo "ERROR: cannot stat $FILE (it vanished or is unreadable): refusing rather than import blind."
  exit 1
fi
if [ "$SIZE_BYTES" -gt 52428800 ]; then
  echo "ERROR: the JSON import path CANNOT restore a full export (structural, TASK-579.01 F1)."
  echo "$FILE is ${SIZE_BYTES} bytes, over the server's 50MiB body cap (quarkus.http.limits.max-body-size)."
  echo "Demonstrated restore paths:"
  echo "  1. DB volume reload: point the app at the capture's profile volume and recount"
  echo "     (verified 9/09: reproduces the delivery analysis byte-identically)."
  echo "  2. Checkpoint apply: POST /api/schedule/solve-staged-resume?path=<checkpoint.json>"
  echo "     (1MB boundary files, path-based, no body cap)."
  exit 1
fi

echo "Importing $FILE to localhost:$PORT..."

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "http://localhost:$PORT/api/schedule/import" \
  -H "Content-Type: application/json" \
  -d @"$FILE")

if [ "$STATUS" = "200" ]; then
  echo "Import successful (HTTP 200)"
elif [ "$STATUS" = "413" ]; then
  echo "ERROR: Payload too large (HTTP 413). File size: $(ls -lh "$FILE" | awk '{print $5}')"
  exit 1
else
  echo "ERROR: HTTP $STATUS"
  curl -s -X POST "http://localhost:$PORT/api/schedule/import" \
    -H "Content-Type: application/json" \
    -d @"$FILE"
  exit 1
fi
