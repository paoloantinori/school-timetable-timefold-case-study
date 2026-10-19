#!/bin/bash
#
# check-completion-info.sh - Check if completionInfo is populated in API response
#

API_URL="${API_URL:-http://localhost:8080}"

# Use curl (allowed by hook for schedule export)
curl -s "$API_URL/api/schedule" | python3 -c "
import sys, json
data = json.load(sys.stdin)
groups = data.get('studentGroups', [])
with_completion = [g for g in groups if g.get('completionInfo')]

print(f'Total groups: {len(groups)}')
print(f'Groups with completionInfo: {len(with_completion)}')

if with_completion:
    print('\\nSample completion data:')
    for g in with_completion[:3]:
        ci = g['completionInfo']
        print(f\"  {g['name']}:\")
        print(f\"    lastLessonDate: {ci.get('lastLessonDate')}\")
        print(f\"    completionDate: {ci.get('completionDate')}\")
        print(f\"    finalDayLessonCount: {ci.get('finalDayLessonCount')}\")
        print(f\"    isPerfectlyCompacted: {ci.get('isPerfectlyCompacted')}\")
        print(f\"    label: {ci.get('label')}\")
else:
    print('\\nWARNING: No completionInfo found in any student groups!')
    print('This means the backend populateCompletionInfo() is not working.')
    sys.exit(1)
"
