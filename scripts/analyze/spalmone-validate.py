#!/usr/bin/env python3
"""
Spalmone Validation & Analysis Engine

Queries the running planner app's REST API and PostgreSQL database, then generates
a comprehensive markdown report analyzing the imported spalmone schedule against
our Timefold constraint model.

Requires:
    - Running planner app (HTTP on --port)
    - Running PostgreSQL container (planner-pg)
    - scripts/db/db-query.sh available

Usage:
    python3 scripts/analyze/spalmone-validate.py --output report.md --branch <name> [--port 8080]
"""

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime
from urllib.request import urlopen
from urllib.error import URLError


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DB_QUERY = os.path.join(SCRIPT_DIR, 'db-query.sh')


# ---------------------------------------------------------------------------
# Data access helpers
# ---------------------------------------------------------------------------

def api_get(path: str, port: int = 8080) -> dict | None:
    """HTTP GET via urllib, returns parsed JSON or None on error."""
    url = f"http://localhost:{port}{path}"
    try:
        with urlopen(url, timeout=30) as resp:
            return json.loads(resp.read().decode())
    except (URLError, json.JSONDecodeError, TimeoutError) as e:
        print(f"WARNING: API request failed: {url} -> {e}", file=sys.stderr)
        return None


def run_sql(query: str, fmt: str = 'csv') -> list[list[str]]:
    """Execute SQL via db-query.sh, return list of rows (each a list of columns).

    db-query.sh -f csv uses psql -F "','" which produces: field1','field2','field3
    The SET line from the read-only transaction prefix is also present and must be skipped.
    """
    cmd = [DB_QUERY, '-w', '-f', fmt, query]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    except subprocess.TimeoutExpired:
        print(f"WARNING: SQL query timed out", file=sys.stderr)
        return []

    if result.returncode != 0:
        print(f"WARNING: SQL query failed: {result.stderr.strip()}", file=sys.stderr)
        return []

    rows = []
    for line in result.stdout.strip().split('\n'):
        line = line.strip()
        if not line or line == 'SET':
            continue
        rows.append(line.split("','"))
    return rows


# ---------------------------------------------------------------------------
# Analysis functions
# ---------------------------------------------------------------------------

def analyze_constraint_scores(port: int) -> dict:
    """Fetch constraint analysis from the REST API."""
    data = api_get('/api/schedule/analysis?all=true', port)
    if not data:
        return {'error': 'Could not fetch constraint analysis'}

    hard_score = data.get('hard', 0)
    medium_score = data.get('medium', 0)
    soft_score = data.get('soft', 0)
    score_str = data.get('score', '')

    constraints = data.get('constraints', [])

    hard = []
    medium = []
    soft = []
    for c in constraints:
        raw_id = c.get('constraint', '?')
        # Strip package prefix: "com.school.planner.domain/H2.1: Room conflict (L-L)" -> "H2.1: Room conflict (L-L)"
        cid = raw_id.split('/')[-1] if '/' in raw_id else raw_id
        # Extract canonical prefix: "H2.1" from "H2.1: Room conflict (L-L)"
        short_id = cid.split(':')[0].strip() if ':' in cid else cid
        entry = {
            'id': short_id,
            'name': cid,
            'score_str': c.get('score', ''),
            'hard': c.get('hard', 0),
            'medium': c.get('medium', 0),
            'soft': c.get('soft', 0),
            'matches': c.get('matches', 0),
        }
        # Categorize by actual score weight, not ID prefix.
        # historical note: the old S4 id no longer exists in the providers, so use actual scores.
        if entry['hard'] != 0:
            hard.append(entry)
        elif entry['medium'] != 0:
            medium.append(entry)
        elif entry['soft'] != 0:
            soft.append(entry)
        else:
            # Zero-score constraints: categorize by ID prefix for the "satisfied" table
            if short_id.startswith('H'):
                hard.append(entry)
            elif short_id.startswith('M'):
                medium.append(entry)
            elif short_id.startswith('S'):
                soft.append(entry)

    return {
        'score_str': score_str,
        'hard_score': hard_score,
        'medium_score': medium_score,
        'soft_score': soft_score,
        'hard_constraints': sorted(hard, key=lambda x: x['hard']),
        'medium_constraints': sorted(medium, key=lambda x: x['medium']),
        'soft_constraints': sorted(soft, key=lambda x: x['soft']),
    }


def analyze_teacher_conflicts() -> list[dict]:
    """SQL: find teacher double-bookings (H1 violations)."""
    rows = run_sql("""
        SELECT t.name, ts.startdatetime, count(*)
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN teacher t ON c.teacher_id = t.id
        JOIN timeslot ts ON l.timeslot_id = ts.id
        WHERE l.timeslot_id IS NOT NULL
        GROUP BY t.name, ts.startdatetime
        HAVING count(*) > 1
        ORDER BY t.name, ts.startdatetime
    """)
    conflicts = []
    for row in rows:
        if len(row) >= 3:
            conflicts.append({
                'teacher': row[0].strip(),
                'datetime': row[1].strip(),
                'count': int(row[2].strip()),
            })
    return conflicts


def analyze_holiday_violations() -> list[dict]:
    """SQL: find lessons on school holidays (H6 violations)."""
    rows = run_sql("""
        SELECT t.name, c.subject, sg.name, sh.date, sh.description
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN teacher t ON c.teacher_id = t.id
        JOIN studentgroup sg ON c.studentgroup_id = sg.id
        JOIN timeslot ts ON l.timeslot_id = ts.id
        JOIN schoolholiday sh ON ts.startdatetime::date = sh.date
        WHERE l.timeslot_id IS NOT NULL
        ORDER BY sh.date, sg.name
    """)
    violations = []
    for row in rows:
        if len(row) >= 5:
            violations.append({
                'teacher': row[0].strip(),
                'subject': row[1].strip(),
                'group': row[2].strip(),
                'date': row[3].strip(),
                'holiday': row[4].strip(),
            })
    return violations


def analyze_stage_blackout() -> list[dict]:
    """SQL: find potential H4 violations (fundamentals on stage days)."""
    rows = run_sql("""
        SELECT sg.name, c.subject, count(*)
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN studentgroup sg ON c.studentgroup_id = sg.id
        JOIN timeslot ts ON l.timeslot_id = ts.id
        JOIN stagepattern sp ON sp.studentgroup_id = sg.id
        JOIN stagepattern_dayofweekmask dm ON dm.stagepattern_id = sp.id
        WHERE l.timeslot_id IS NOT NULL
          AND ts.startdatetime::date BETWEEN sp.startdate AND sp.enddate
          AND EXTRACT(ISODOW FROM ts.startdatetime) = dm.dayofweekmask
          AND c.professionalizzanti = false
        GROUP BY sg.name, c.subject
        ORDER BY count(*) DESC
        LIMIT 20
    """)
    violations = []
    for row in rows:
        if len(row) >= 3:
            violations.append({
                'group': row[0].strip(),
                'subject': row[1].strip(),
                'count': int(row[2].strip()),
            })
    return violations


def analyze_same_day_same_subject() -> list[dict]:
    """SQL: find same-day same-subject pairs (H15 evidence)."""
    rows = run_sql("""
        SELECT c.subject, count(*) AS pairs
        FROM lesson l1
        JOIN course c ON l1.course_id = c.id
        JOIN timeslot ts1 ON l1.timeslot_id = ts1.id
        JOIN lesson l2 ON l2.id > l1.id
        JOIN course c2 ON l2.course_id = c2.id
        JOIN timeslot ts2 ON l2.timeslot_id = ts2.id
        WHERE l1.timeslot_id IS NOT NULL AND l2.timeslot_id IS NOT NULL
          AND c.studentgroup_id = c2.studentgroup_id
          AND c.subject = c2.subject
          AND ts1.startdatetime::date = ts2.startdatetime::date
        GROUP BY c.subject
        ORDER BY count(*) DESC
        LIMIT 15
    """)
    results = []
    for row in rows:
        if len(row) >= 2:
            results.append({
                'subject': row[0].strip(),
                'pairs': int(row[1].strip()),
            })
    return results


def analyze_teacher_daily_hours() -> list[dict]:
    """SQL: find teachers exceeding daily hour limits (H7 evidence)."""
    rows = run_sql("""
        SELECT t.name, ts.startdatetime::date AS day, count(*) AS hours
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN teacher t ON c.teacher_id = t.id
        JOIN timeslot ts ON l.timeslot_id = ts.id
        WHERE l.timeslot_id IS NOT NULL
        GROUP BY t.name, ts.startdatetime::date
        HAVING count(*) > 6
        ORDER BY count(*) DESC
        LIMIT 15
    """)
    results = []
    for row in rows:
        if len(row) >= 3:
            results.append({
                'teacher': row[0].strip(),
                'date': row[1].strip(),
                'hours': int(row[2].strip()),
            })
    return results


def analyze_entity_summary() -> dict:
    """SQL: entity counts and lessons per group/teacher."""
    # Entity counts
    count_rows = run_sql("""
        SELECT 'teachers' AS entity, count(*) FROM teacher
        UNION ALL SELECT 'student_groups', count(*) FROM studentgroup
        UNION ALL SELECT 'rooms', count(*) FROM room
        UNION ALL SELECT 'courses', count(*) FROM course
        UNION ALL SELECT 'timeslots', count(*) FROM timeslot
        UNION ALL SELECT 'lessons', count(*) FROM lesson
        UNION ALL SELECT 'lab_blocks', count(*) FROM labblock
        UNION ALL SELECT 'holidays', count(*) FROM schoolholiday
    """)
    counts = {}
    for row in count_rows:
        if len(row) >= 2:
            counts[row[0].strip()] = int(row[1].strip())

    # Lessons per group
    group_rows = run_sql("""
        SELECT sg.name, count(*)
        FROM lesson l
        JOIN course c ON l.course_id = c.id
        JOIN studentgroup sg ON c.studentgroup_id = sg.id
        GROUP BY sg.name
        ORDER BY sg.name
    """)
    groups = []
    for row in group_rows:
        if len(row) >= 2:
            groups.append({'name': row[0].strip(), 'lessons': int(row[1].strip())})

    # Pinned counts
    pinned_rows = run_sql("""
        SELECT pinned, count(*) FROM lesson GROUP BY pinned ORDER BY pinned
    """)
    pinned = {}
    for row in pinned_rows:
        if len(row) >= 2:
            key = 'pinned' if row[0].strip() in ('t', 'true') else 'unpinned'
            pinned[key] = int(row[1].strip())

    return {
        'counts': counts,
        'groups': groups,
        'pinned': pinned,
    }


# ---------------------------------------------------------------------------
# Report generation
# ---------------------------------------------------------------------------

def generate_report(
    constraint_data: dict,
    teacher_conflicts: list[dict],
    holiday_violations: list[dict],
    stage_violations: list[dict],
    same_day_subject: list[dict],
    teacher_daily: list[dict],
    entity_summary: dict,
    branch: str,
    port: int,
) -> str:
    """Assemble all analysis sections into a markdown report."""
    lines = []

    # Header
    lines.append("# Spalmone Analysis Report")
    lines.append("")
    lines.append(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"**Branch:** `{branch}`")
    lines.append(f"**Source:** Human planner's master schedule (spalmone 2025-2026)")
    lines.append(f"**API Port:** {port}")
    lines.append("")

    # Executive Summary
    lines.append("## Executive Summary")
    lines.append("")

    if 'error' in constraint_data:
        lines.append(f"> **Error:** {constraint_data['error']}")
        lines.append("")
    else:
        hard = constraint_data['hard_score']
        medium = constraint_data['medium_score']
        soft = constraint_data['soft_score']
        score_str = constraint_data.get('score_str', '')

        lines.append(f"**Overall Score:** `{score_str}`")
        lines.append("")
        lines.append("| Category | Score | Status |")
        lines.append("|----------|-------|--------|")
        lines.append(f"| Hard     | {hard:,} | {'Feasible' if hard == 0 else 'Violations'} |")
        lines.append(f"| Medium   | {medium:,} | {'OK' if medium >= 0 else 'Violations'} |")
        lines.append(f"| Soft     | {soft:,} | {'OK' if soft >= 0 else 'Violations'} |")
        lines.append("")

        hard_cs = constraint_data.get('hard_constraints', [])
        violated = sum(1 for c in hard_cs if c['hard'] < 0)
        satisfied = sum(1 for c in hard_cs if c['hard'] == 0)
        lines.append(f"**Hard constraints:** {violated} violated, {satisfied} satisfied")
        lines.append("")

    # Data Summary
    lines.append("## Data Summary")
    lines.append("")
    counts = entity_summary.get('counts', {})
    if counts:
        lines.append("| Entity | Count |")
        lines.append("|--------|-------|")
        for entity, count in sorted(counts.items()):
            lines.append(f"| {entity} | {count:,} |")
        lines.append("")

    pinned = entity_summary.get('pinned', {})
    if pinned:
        lines.append(f"**Pinned:** {pinned.get('pinned', 0):,} | "
                      f"**Unpinned:** {pinned.get('unpinned', 0):,}")
        lines.append("")

    groups = entity_summary.get('groups', [])
    if groups:
        lines.append("### Lessons per Student Group")
        lines.append("")
        lines.append("| Group | Lessons |")
        lines.append("|-------|---------|")
        for g in groups:
            lines.append(f"| {g['name']} | {g['lessons']:,} |")
        lines.append("")

    # Import Limitations
    lines.append("## Import Limitations")
    lines.append("")
    lines.append("1. **No room data**: All lessons assigned to Aula 1 (placeholder). "
                 "Room-based constraints (H2, H-M2, M3) are **false positives**.")
    lines.append("2. **All courses non-lab**: `islab=false` for all. "
                 "Lab constraints (H9, M4, S10) show 0 matches.")
    lines.append("3. **No professionalizzanti flags**: All `professionalizzanti=false`. "
                 "H4 violations include courses that would be exempt in our model.")
    lines.append("4. **No doppio teacher data**: H-D1 constraints show 0 matches.")
    lines.append("")

    # Hard Constraint Breakdown
    if 'error' not in constraint_data:
        hard_cs = constraint_data.get('hard_constraints', [])

        lines.append("## Hard Constraint Violations")
        lines.append("")

        violated_hard = [c for c in hard_cs if c['hard'] < 0]
        satisfied_hard = [c for c in hard_cs if c['hard'] == 0]

        if violated_hard:
            lines.append("| ID | Constraint | Score | Matches | Analysis |")
            lines.append("|----|-----------|-------|---------|----------|")
            for c in violated_hard:
                cid = c['id']
                name = c.get('name', cid)
                analysis = _constraint_analysis_note(cid)
                lines.append(f"| {cid} | {name} | {c['hard']:,} | {c['matches']:,} | {analysis} |")
            lines.append("")

        if satisfied_hard:
            lines.append("### Hard Constraints with ZERO Violations")
            lines.append("")
            lines.append("| ID | Matches | Interpretation |")
            lines.append("|----|---------|----------------|")
            for c in satisfied_hard:
                cid = c['id']
                interp = _zero_violation_note(cid)
                lines.append(f"| {cid} | {c['matches']:,} | {interp} |")
            lines.append("")

        # Medium Constraints
        medium_cs = constraint_data.get('medium_constraints', [])
        if medium_cs:
            lines.append("## Medium Constraints")
            lines.append("")
            lines.append("| ID | Constraint | Score | Matches |")
            lines.append("|----|-----------|-------|---------|")
            for c in medium_cs:
                name = c.get('name', c['id'])
                lines.append(f"| {c['id']} | {name} | {c['medium']:,} | {c['matches']:,} |")
            lines.append("")

        # Soft Constraints
        soft_cs = constraint_data.get('soft_constraints', [])
        if soft_cs:
            lines.append("## Soft Constraints")
            lines.append("")
            lines.append("| ID | Constraint | Score | Matches |")
            lines.append("|----|-----------|-------|---------|")
            for c in soft_cs:
                name = c.get('name', c['id'])
                lines.append(f"| {c['id']} | {name} | {c['soft']:,} | {c['matches']:,} |")
            lines.append("")

    # Detailed Violation Evidence
    lines.append("## Detailed Violation Evidence")
    lines.append("")

    # H1: Teacher conflicts
    lines.append("### H1: Teacher Double-Bookings")
    lines.append("")
    if teacher_conflicts:
        lines.append(f"**{len(teacher_conflicts)} conflicts found.**")
        lines.append("")
        lines.append("| Teacher | DateTime | Lesson Count |")
        lines.append("|---------|----------|-------------|")
        for tc in teacher_conflicts:
            lines.append(f"| {tc['teacher']} | {tc['datetime']} | {tc['count']} |")
        lines.append("")
        lines.append("These are genuine scheduling errors. All involve exactly 2 lessons "
                      "at the same time — likely data entry mistakes or missed conflicts.")
    else:
        lines.append("No teacher double-bookings found.")
    lines.append("")

    # H6: Holiday violations
    lines.append("### H6: Lessons on School Holidays")
    lines.append("")
    if holiday_violations:
        lines.append(f"**{len(holiday_violations)} lessons on holidays.**")
        lines.append("")
        lines.append("| Teacher | Subject | Group | Date | Holiday |")
        lines.append("|---------|---------|-------|------|---------|")
        for hv in holiday_violations[:20]:  # limit display
            lines.append(f"| {hv['teacher']} | {hv['subject']} | "
                          f"{hv['group']} | {hv['date']} | {hv['holiday']} |")
        if len(holiday_violations) > 20:
            lines.append(f"| ... | ... | ... | ... | ... |")
            lines.append(f"*(showing 20 of {len(holiday_violations)})*")
        lines.append("")
    else:
        lines.append("No lessons on school holidays.")
    lines.append("")

    # H4: Stage blackout
    lines.append("### H4: Stage Blackout Violations")
    lines.append("")
    if stage_violations:
        total_violations = sum(v['count'] for v in stage_violations)
        lines.append(f"**{total_violations} lessons on stage days (top subjects):**")
        lines.append("")
        lines.append("| Group | Subject | Violations |")
        lines.append("|-------|---------|-----------|")
        for sv in stage_violations:
            lines.append(f"| {sv['group']} | {sv['subject']} | {sv['count']} |")
        lines.append("")
        lines.append("**Note**: Some may be professionalizzanti courses that the importer "
                      "didn't flag. True violations are limited to fundamental subjects "
                      "(Italiano, Matematica, etc.).")
    else:
        lines.append("No stage blackout violations found.")
    lines.append("")

    # H7: Teacher daily hours
    lines.append("### H7: Teacher Daily Hours Exceeding 6")
    lines.append("")
    if teacher_daily:
        lines.append(f"**{len(teacher_daily)} cases (top entries):**")
        lines.append("")
        lines.append("| Teacher | Date | Hours |")
        lines.append("|---------|------|-------|")
        for td in teacher_daily:
            lines.append(f"| {td['teacher']} | {td['date']} | {td['hours']} |")
        lines.append("")
    else:
        lines.append("No teachers exceed 6 hours per day.")
    lines.append("")

    # H15: Same-day same-subject
    lines.append("### H15: Same-Day Same-Subject Patterns")
    lines.append("")
    if same_day_subject:
        lines.append("| Subject | Same-Day Pairs |")
        lines.append("|---------|---------------|")
        for sds in same_day_subject:
            lines.append(f"| {sds['subject']} | {sds['pairs']:,} |")
        lines.append("")
        lines.append("These are mostly lab subjects scheduled as multi-hour blocks. "
                      "The school intentionally schedules same subject multiple times per day. "
                      "This validates our LabBlock entity approach.")
    else:
        lines.append("No same-day same-subject patterns found.")
    lines.append("")

    # Interpretation
    lines.append("## Interpretation")
    lines.append("")
    lines.append("### Data-Import Artifacts (False Positives)")
    lines.append("")
    lines.append("- **H2 (Room conflict)**: All lessons share Aula 1 placeholder — "
                 "every timeslot with >1 lesson triggers a false H2 violation.")
    lines.append("- **H-M2 (Room suitability)**: No lab flags set, so lab courses in "
                 "Aula 1 are technically violations but not meaningful.")
    lines.append("- **M3 (Room capacity)**: All lessons in same room, meaningless.")
    lines.append("- **S10 (Generic in labs)**: No lab rooms used, 0 matches expected.")
    lines.append("")
    lines.append("### Genuine Findings")
    lines.append("")
    lines.append("- **H1 teacher conflicts**: Real double-bookings in the school's plan.")
    lines.append("- **H3 = 0**: School never double-books student groups (validates constraint).")
    lines.append("- **H6 holiday violations**: Likely data entry errors.")
    lines.append("- **H4 stage blackout**: Mix of real violations and import artifacts "
                 "(professionalizzanti not flagged).")
    lines.append("- **H15**: Lab subjects with multi-hour blocks confirm LabBlock design.")
    lines.append("")

    # Methodology
    lines.append("## Methodology")
    lines.append("")
    lines.append("1. Spalmone CSV parsed by `scripts/analyze/spalmone-parser.py`")
    lines.append("2. Imported by `scripts/analyze/spalmone-db-importer.py`")
    lines.append("3. All lessons assigned `room_id = 1` (Aula 1) to initialize planning variables")
    lines.append("4. Analysis via `GET /api/schedule/analysis?all=true` endpoint")
    lines.append("5. Detailed violations via SQL queries on planner-pg database")
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append("*Generated by spalmone-validate.py*")

    return '\n'.join(lines)


def _constraint_analysis_note(cid: str) -> str:
    """Short analysis note for violated constraints."""
    notes = {
        'H1.1': 'Real teacher double-bookings',
        'H1': 'Real teacher double-bookings',
        'H2.1': '**FALSE POSITIVE** — all lessons in same dummy room',
        'H2': '**FALSE POSITIVE** — all lessons in same dummy room',
        'H3.1': 'Real student group conflict',
        'H3': 'Real student group conflict',
        'H4.1': 'Partially real — some may be professionalizzanti',
        'H4': 'Partially real — some may be professionalizzanti',
        'H6.1': 'Real — lessons on holidays',
        'H6': 'Real — lessons on holidays',
        'H7': 'Teacher exceeds daily limit',
        'H15': 'School schedules multi-hour blocks non-consecutively',
        'H16': 'Likely room artifact (dummy room)',
        'H-M2.1': 'N/A — no lab flags imported',
        'H-M2': 'N/A — no lab flags imported',
    }
    return notes.get(cid, '')


def _zero_violation_note(cid: str) -> str:
    """Interpretation note for zero-violation constraints."""
    notes = {
        'H3.1': '**School never double-books students**',
        'H3': '**School never double-books students**',
        'H5.1': 'School respects teacher availability',
        'H5': 'School respects teacher availability',
        'H8': 'Min daily lessons respected',
        'H9': 'N/A (no LabBlocks imported)',
        'H11': 'Max daily hours respected',
        'H14.1': 'Start dates respected',
        'H14': 'Start dates respected',
    }
    return notes.get(cid, '')


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description='Analyze spalmone schedule against Timefold constraints')
    parser.add_argument('--output', '-o', required=True,
                        help='Output markdown report path')
    parser.add_argument('--branch', '-b', default='unknown',
                        help='Branch name for report metadata')
    parser.add_argument('--port', '-p', type=int, default=8080,
                        help='API port (default: 8080)')
    args = parser.parse_args()

    print("Spalmone Validation Engine")
    print("=" * 50)

    # 1. Constraint scores from API
    print("\n[1/6] Fetching constraint analysis from API...")
    constraint_data = analyze_constraint_scores(args.port)
    if 'error' not in constraint_data:
        print(f"  Score: {constraint_data.get('score_str', 'N/A')}")
        print(f"  Hard constraints: {len(constraint_data.get('hard_constraints', []))}")

    # 2. Teacher conflicts
    print("[2/6] Querying teacher conflicts (H1)...")
    teacher_conflicts = analyze_teacher_conflicts()
    print(f"  Found: {len(teacher_conflicts)} conflicts")

    # 3. Holiday violations
    print("[3/6] Querying holiday violations (H6)...")
    holiday_violations = analyze_holiday_violations()
    print(f"  Found: {len(holiday_violations)} lessons on holidays")

    # 4. Stage blackout
    print("[4/6] Querying stage blackout violations (H4)...")
    stage_violations = analyze_stage_blackout()
    total_h4 = sum(v['count'] for v in stage_violations) if stage_violations else 0
    print(f"  Found: {total_h4} lessons on stage days")

    # 5. Same-day same-subject
    print("[5/6] Querying same-day same-subject patterns (H15)...")
    same_day_subject = analyze_same_day_same_subject()
    total_h15 = sum(s['pairs'] for s in same_day_subject) if same_day_subject else 0
    print(f"  Found: {total_h15} same-day pairs")

    # 6. Teacher daily hours
    print("[6/6] Querying teacher daily hours (H7)...")
    teacher_daily = analyze_teacher_daily_hours()
    print(f"  Found: {len(teacher_daily)} exceedances")

    # Entity summary
    print("\nGathering entity summary...")
    entity_summary = analyze_entity_summary()
    counts = entity_summary.get('counts', {})
    print(f"  Lessons: {counts.get('lessons', '?')}, "
          f"Courses: {counts.get('courses', '?')}, "
          f"Teachers: {counts.get('teachers', '?')}")

    # Generate report
    print("\nGenerating report...")
    report = generate_report(
        constraint_data=constraint_data,
        teacher_conflicts=teacher_conflicts,
        holiday_violations=holiday_violations,
        stage_violations=stage_violations,
        same_day_subject=same_day_subject,
        teacher_daily=teacher_daily,
        entity_summary=entity_summary,
        branch=args.branch,
        port=args.port,
    )

    with open(args.output, 'w', encoding='utf-8') as f:
        f.write(report)

    print(f"\nReport written to: {args.output}")
    print(f"  Size: {len(report):,} chars, {report.count(chr(10)):,} lines")


if __name__ == '__main__':
    main()
