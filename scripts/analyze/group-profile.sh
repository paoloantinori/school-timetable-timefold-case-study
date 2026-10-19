#!/usr/bin/env bash
# group-profile.sh — one-shot tightness profile for a student group.
#
# Prints, for a given group: its course/teacher/hours breakdown, total demand
# (sum of course hours), its weekly calendar capacity (sum of open slots per
# recurring weekday rule), the resulting demand-vs-capacity tightness, and each
# teacher's cross-group load (how constrained that teacher is school-wide).
#
# Recurrent during the TASK-381/386 "zero-slack template" work (G05 and any
# other tight group): demand ≈ weekly-capacity × teaching-weeks is exactly the
# condition that makes greedy first-fit construction fragile. Read-only.
#
# Usage: scripts/analyze/group-profile.sh "G05"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DBQ="$SCRIPT_DIR/../db/db-query.sh"

GROUP="${1:-}"
if [[ -z "$GROUP" ]]; then
    echo "Usage: $0 \"<group name>\"   e.g. $0 \"G05\"" >&2
    exit 1
fi

# Guard: does the group exist?
exists="$("$DBQ" "SELECT COUNT(*) FROM studentgroup WHERE name='${GROUP//\'/\'\'}'" 2>/dev/null | grep -oE '[0-9]+' | head -1)"
if [[ "${exists:-0}" -eq 0 ]]; then
    echo "No student group named '$GROUP'. Available:" >&2
    "$DBQ" "SELECT name FROM studentgroup ORDER BY name" 2>/dev/null >&2
    exit 1
fi

G="${GROUP//\'/\'\'}"

echo "=== $GROUP — courses (subject | teacher | hours) ==="
"$DBQ" "SELECT c.subject, t.name, c.totalhours
        FROM course c JOIN studentgroup g ON c.studentgroup_id=g.id
        LEFT JOIN teacher t ON c.teacher_id=t.id
        WHERE g.name='$G' ORDER BY c.totalhours DESC, c.subject"

echo
echo "=== demand vs weekly calendar capacity ==="
"$DBQ" "WITH demand AS (
          SELECT COALESCE(SUM(c.totalhours),0) AS h, COUNT(*) AS courses
          FROM course c JOIN studentgroup g ON c.studentgroup_id=g.id WHERE g.name='$G'),
        cap AS (
          SELECT COALESCE(SUM(gcr.maxslot-gcr.minslot+1),0) AS weekly_slots
          FROM groupcalendarrule gcr JOIN studentgroup g ON gcr.studentgroup_id=g.id
          WHERE g.name='$G' AND gcr.effectivefrom IS NULL)
        SELECT d.courses, d.h AS demand_hours, c.weekly_slots AS weekly_capacity,
               ROUND(d.h::numeric / NULLIF(c.weekly_slots,0), 1) AS implied_teaching_weeks
        FROM demand d, cap c"

echo
echo "=== weekly calendar windows (recurring rules) ==="
"$DBQ" "SELECT gcr.dayofweek, gcr.minslot, gcr.maxslot, (gcr.maxslot-gcr.minslot+1) AS open_slots
        FROM groupcalendarrule gcr JOIN studentgroup g ON gcr.studentgroup_id=g.id
        WHERE g.name='$G' AND gcr.effectivefrom IS NULL ORDER BY gcr.dayofweek"

echo
echo "=== this group's teachers, ranked by school-wide load (hours across ALL groups) ==="
"$DBQ" "SELECT t.name, COUNT(DISTINCT c2.studentgroup_id) AS groups_taught,
               SUM(c2.totalhours) AS total_hours_all_groups,
               SUM(CASE WHEN g.name='$G' THEN c2.totalhours ELSE 0 END) AS hours_for_this_group
        FROM teacher t
        JOIN course c2 ON c2.teacher_id=t.id
        JOIN studentgroup g ON c2.studentgroup_id=g.id
        WHERE t.id IN (SELECT DISTINCT c.teacher_id FROM course c
                       JOIN studentgroup g2 ON c.studentgroup_id=g2.id WHERE g2.name='$G')
        GROUP BY t.name ORDER BY total_hours_all_groups DESC"
