#!/bin/bash
#
# check-lessons.sh - Debug lesson data to understand why completionInfo is empty
#

API_URL="${API_URL:-http://localhost:8080}"

curl -s "$API_URL/api/schedule" | python3 -c "
import sys, json
data = json.load(sys.stdin)

lessons = data.get('lessons', [])
print(f'Total lessons: {len(lessons)}')

# Count lessons with timeslots assigned
assigned = [l for l in lessons if l.get('timeslot')]
print(f'Lessons with timeslots: {len(assigned)}')

# Count lessons per group
from collections import defaultdict
group_lessons = defaultdict(list)
for l in lessons:
    if l.get('course') and l['course'].get('studentGroup'):
        group = l['course']['studentGroup']['name']
        group_lessons[group].append(l)

print('\\nLessons per group (top 5):')
for group, group_lessons_list in sorted(group_lessons.items(), key=lambda x: -len(x[1]))[:5]:
    assigned_count = sum(1 for l in group_lessons_list if l.get('timeslot'))
    print(f'  {group}: {assigned_count}/{len(group_lessons_list)} assigned')

# Check if any lessons exist
if len(lessons) == 0:
    print('\\nERROR: No lessons found! The schedule might not have been solved yet.')
    print('Run: ./scripts/api/api.sh solve')
elif len(assigned) == 0:
    print('\\nWARNING: No lessons have timeslots assigned!')
    print('The schedule might not have been solved yet.')
    print('Run: ./scripts/api/api.sh solve')
"
