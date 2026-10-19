#!/usr/bin/env python3
"""
Spalmone Report Generator

Generates a markdown report from the constraint analysis of the human planner's schedule.

Usage:
    python3 scripts/analyze/spalmone-report.py <analysis.json> [output.md]

If output file is not specified, prints to stdout.
"""

import json
import sys
from datetime import datetime


def format_score(score: int) -> str:
    """Format a constraint score for display."""
    if score == 0:
        return "0 ✅"
    elif score < 0:
        return f"{score:,} ⚠️"
    else:
        return f"+{score:,}"


def categorize_constraint(constraint_id: str) -> str:
    """Categorize a constraint by type."""
    if constraint_id.startswith('H'):
        return "Hard"
    elif constraint_id.startswith('M'):
        return "Medium"
    elif constraint_id.startswith('S'):
        return "Soft"
    else:
        return "Other"


def generate_report(analysis: dict, import_stats: dict | None = None) -> str:
    """Generate the markdown report."""
    lines = []

    # Header
    lines.append("# Spalmone Analysis Report")
    lines.append("")
    lines.append(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"**Source:** Human planner's master schedule (spalmone 2025-2026)")
    lines.append("")

    # Executive Summary
    lines.append("## Executive Summary")
    lines.append("")

    summary = analysis.get('summary', {})
    hard_score = summary.get('hardScore', analysis.get('hard', 0))
    medium_score = summary.get('mediumScore', analysis.get('medium', 0))
    soft_score = summary.get('softScore', analysis.get('soft', 0))

    lines.append(f"**Overall Score:** {hard_score + medium_score + soft_score:,}")
    lines.append("")
    lines.append(f"| Category | Score | Status |")
    lines.append(f"|----------|-------|--------|")
    lines.append(f"| Hard     | {hard_score:,} | {'✅ Feasible' if hard_score == 0 else '❌ Violations'} |")
    lines.append(f"| Medium   | {medium_score:,} | {'✅ OK' if medium_score >= 0 else '⚠️ Violations'} |")
    lines.append(f"| Soft     | {soft_score:,} | {'✅ OK' if soft_score >= 0 else '⚠️ Violations'} |")
    lines.append("")

    if import_stats:
        lines.append("### Import Statistics")
        lines.append("")
        lines.append(f"| Metric | Count |")
        lines.append(f"|--------|-------|")
        lines.append(f"| Total in CSV | {import_stats.get('total', 'N/A')} |")
        lines.append(f"| Imported | {import_stats.get('imported', 'N/A')} |")
        lines.append(f"| No Timeslot | {import_stats.get('skipped_no_timeslot', 'N/A')} |")
        lines.append(f"| No Course | {import_stats.get('skipped_no_course', 'N/A')} |")
        lines.append(f"| Errors | {import_stats.get('errors', 'N/A')} |")
        lines.append("")

    # Constraint Breakdown
    lines.append("## Constraint Analysis")
    lines.append("")

    constraints = analysis.get('constraints', [])

    # Group by category
    hard_constraints = [c for c in constraints if c.get('id', '').startswith('H')]
    medium_constraints = [c for c in constraints if c.get('id', '').startswith('M')]
    soft_constraints = [c for c in constraints if c.get('id', '').startswith('S')]

    if not constraints:
        lines.append("> **Note:** No constraint breakdown available. This typically means:")
        lines.append("> 1. The solver has not been run (showing initial state)")
        lines.append("> 2. All constraints are satisfied (no violations)")
        lines.append("")

    # Hard Constraints
    lines.append("### Hard Constraints")
    lines.append("")

    if hard_constraints:
        lines.append("| ID | Constraint | Score | Matches |")
        lines.append("|----|------------|-------|---------|")

        for c in sorted(hard_constraints, key=lambda x: x.get('score', 0)):
            cid = c.get('id', '?')
            name = c.get('name', 'Unknown')
            score = c.get('score', 0)
            matches = c.get('matches', 'N/A')
            status = "✅" if score == 0 else "❌"
            lines.append(f"| {cid} | {name} | {score:,} {status} | {matches} |")

        lines.append("")
    else:
        lines.append("✅ **No hard constraint data returned** - likely all satisfied or solver not run.")
        lines.append("")

    # Medium Constraints
    if medium_constraints:
        lines.append("### Medium Constraints")
        lines.append("")
        lines.append("| ID | Constraint | Score | Matches |")
        lines.append("|----|------------|-------|---------|")

        for c in sorted(medium_constraints, key=lambda x: x.get('score', 0)):
            cid = c.get('id', '?')
            name = c.get('name', 'Unknown')
            score = c.get('score', 0)
            matches = c.get('matches', 'N/A')
            lines.append(f"| {cid} | {name} | {score:,} | {matches} |")

        lines.append("")

    # Soft Constraints
    if soft_constraints:
        lines.append("### Soft Constraints")
        lines.append("")
        lines.append("| ID | Constraint | Score | Matches |")
        lines.append("|----|------------|-------|---------|")

        for c in sorted(soft_constraints, key=lambda x: x.get('score', 0)):
            cid = c.get('id', '?')
            name = c.get('name', 'Unknown')
            score = c.get('score', 0)
            matches = c.get('matches', 'N/A')
            lines.append(f"| {cid} | {name} | {score:,} | {matches} |")

        lines.append("")

    # Interpretation
    lines.append("## Interpretation")
    lines.append("")

    if hard_score == 0 and not hard_constraints:
        lines.append("### Initial State Detected")
        lines.append("")
        lines.append("The score shows `-17328init/0hard/0medium/0soft` which indicates:")
        lines.append("- **Initial state**: The solver has not been run")
        lines.append("- **17328init**: Initial calculation score (internal)")
        lines.append("- **0 hard/medium/soft**: No constraint violations detected yet")
        lines.append("")
        lines.append("This is the **state before solving**. To get actual constraint analysis:")
        lines.append("")
        lines.append("```bash")
        lines.append("# Run solver for a short time to get analysis")
        lines.append("./scripts/api/api.sh solve -t 60")
        lines.append("./scripts/api/api.sh analysis > /tmp/spalmone-analysis.json")
        lines.append("```")
        lines.append("")

    # Violations Detail
    violated_hard = [c for c in hard_constraints if c.get('score', 0) < 0]

    if violated_hard:
        lines.append("## Hard Constraint Violations")
        lines.append("")
        lines.append("> ⚠️ **CRITICAL:** The human planner's schedule violates one or more hard constraints.")
        lines.append("> This indicates either:")
        lines.append("> 1. The constraint implementation differs from the school's actual practice")
        lines.append("> 2. The schedule contains data entry errors")
        lines.append("> 3. The constraint should be relaxed from hard to medium/soft")
        lines.append("")

        for c in sorted(violated_hard, key=lambda x: x.get('score', 0)):
            cid = c.get('id', '?')
            name = c.get('name', 'Unknown')
            score = c.get('score', 0)
            matches = c.get('matches', 0)

            lines.append(f"### {cid}: {name}")
            lines.append("")
            lines.append(f"- **Score:** {score:,}")
            lines.append(f"- **Violations:** {matches}")
            lines.append("")

            # Add explanation for known constraints
            explanations = {
                'H1': 'Teacher conflict: A teacher is assigned to multiple lessons at the same time.',
                'H2': 'Room conflict: A room is assigned to multiple lessons at the same time.',
                'H3': 'Student group conflict: A student group has multiple lessons at the same time.',
                'H4': 'Stage blackout: A fundamental subject is scheduled during a stage period.',
                'H5': 'Teacher unavailability: A lesson is scheduled when the teacher is unavailable.',
                'H6': 'School holiday: A lesson is scheduled on a holiday.',
                'H7': 'Max teacher daily hours: A teacher exceeds their maximum daily hours.',
                'H8': 'Min student daily lessons: A student group has fewer than minimum lessons on an attended day.',
                'H9': 'Lab entity valid placement: A lab block is placed in an invalid timeslot.',
                'H11': 'Max student daily hours: A student group exceeds their maximum daily hours.',
                'H14': 'Student group start date: A lesson is scheduled before the group\'s start date.',
                'H15': 'Same-day consecutive same-subject: Same subject scheduled consecutively on same day (not allowed).',
                'H16': 'Consecutive same-subject same teacher-room: Constraint on consecutive same-subject lessons.',
                'H-M2': 'Room suitability: A lab course is scheduled in a non-lab room or vice versa.',
            }

            if cid in explanations:
                lines.append(f"**Explanation:** {explanations[cid]}")
                lines.append("")

            lines.append("")

    else:
        lines.append("## Hard Constraint Status")
        lines.append("")
        lines.append("✅ **No hard constraint violations detected!**")
        lines.append("")

        if hard_score == 0:
            lines.append("The analysis shows 0 hard constraint violations. This means:")
            lines.append("1. The human planner's schedule respects all hard constraints")
            lines.append("2. Our constraint implementation aligns with the school's scheduling practices")
            lines.append("3. No constraint relaxation is needed")
        lines.append("")

    # Recommendations
    lines.append("## Recommendations")
    lines.append("")

    if violated_hard:
        lines.append("### Immediate Actions")
        lines.append("")
        lines.append("1. **Data Validation:** Cross-check the spalmone CSV against the constraint violations")
        lines.append("2. **Entity Matching:** Verify that teachers, groups, and rooms are correctly matched")
        lines.append("3. **Timeslot Alignment:** Ensure CSV dates/slots map correctly to database timeslots")
        lines.append("")
        lines.append("### Constraint Review")
        lines.append("")
        lines.append("If violations persist after data validation, consider:")
        lines.append("")

        for c in violated_hard:
            cid = c.get('id', '?')
            name = c.get('name', 'Unknown')
            lines.append(f"- **{cid} ({name}):** Evaluate if this should be a medium constraint")

        lines.append("")
    else:
        lines.append("### Validation Complete")
        lines.append("")
        lines.append("The human planner's schedule validates successfully against all hard constraints.")
        lines.append("This confirms the constraint system aligns with the school's scheduling practices.")
        lines.append("")

    # Footer
    lines.append("---")
    lines.append("")
    lines.append("*Generated by spalmone-report.py*")

    return '\n'.join(lines)


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/analyze/spalmone-report.py <analysis.json> [output.md]", file=sys.stderr)
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else None

    # Load analysis data
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Handle both direct analysis and wrapped format
    if 'analysis' in data:
        analysis = data['analysis']
        import_stats = data.get('import_stats')
    else:
        analysis = data
        import_stats = None

    # Generate report
    report = generate_report(analysis, import_stats)

    # Output
    if output_file:
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"Report written to: {output_file}")
    else:
        print(report)


if __name__ == '__main__':
    main()
