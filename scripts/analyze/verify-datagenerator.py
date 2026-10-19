#!/usr/bin/env python3
"""
DataGenerator Verification Script

Parses spalmone.csv (the human planner's master schedule) and DataGenerator.java
to comprehensively compare hours per teacher/group/subject.

This script helps identify data discrepancies that cause CSP infeasibility.

Usage:
    python3 scripts/analyze/verify-datagenerator.py [--csv scripts/data/spalmone.csv] [--java src/main/java/.../DataGenerator.java]

Output:
    - Full comparison table of hours per teacher/group/subject
    - Discrepancy analysis with recommendations
    - Exit code 1 if critical discrepancies found

Exit codes:
    0 - No discrepancies or only minor differences (<5%)
    1 - Critical discrepancies found (>10h or >20% difference)
    2 - Error (file not found, parse error, etc.)
"""

import argparse
import csv
import re
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path
from typing import Optional, Dict, Set, Tuple


# Italian month abbreviations to month numbers
MONTH_MAP = {
    'gen': 1, 'feb': 2, 'mar': 3, 'apr': 4,
    'mag': 5, 'giu': 6, 'lug': 7, 'ago': 8,
    'set': 9, 'ott': 10, 'nov': 11, 'dic': 12
}

# CSV column index to student group name mapping
# Columns 0-3: week#, date, day_of_week, time_slot
# Then 14 groups x 3 columns = 42 columns
GROUP_COLUMNS = {
    4: 'G03',
    7: 'G07',
    10: 'G11',
    13: 'G04',
    16: 'G08',
    19: 'G13',
    22: 'G02',
    25: 'G06',
    28: 'G10',
    31: 'G12',
    34: 'G01',
    37: 'G05',
    40: 'G09',
    43: 'G14',
}

# Teacher name normalization (CSV abbreviation -> canonical name)
TEACHER_MAP = {
    # FB teachers
    'T23': 'T23',
    'T02': 'T02',
    'T04': 'T04',
    'T21': 'T21',
    'T08': 'T08',
    'T25': 'T25',
    'T03': 'T03',
    'T06': 'T06',
    'T29': 'T29',
    'T11': 'T11',
    'T05': 'T05',
    'T12': 'T12',
    'T31': 'T31',
    'T15': 'T15',
    'T10': 'T10',
    'T30': 'T30',
    # FC teachers
    'T13': 'T13',
    # FA teachers
    'T09': 'T09',
    'T28': 'T28',
    'T18': 'T18',
    'T26': 'T26',
    # MODA teachers
    'T16': 'T16',
    'T17': 'T17',
    'T20': 'T20',
    'T07': 'T07',
    'T14': 'T14',
    # External/special
    'est': 'External',
    'est + Basse': 'T03',
}

# Subject normalization (CSV abbreviations -> canonical names)
SUBJECT_MAP = {
    # General subjects
    'Sic': 'Sicurezza',
    'sci': 'Scienze',
    'ss': 'Scienze sociali',
    'ita': 'Lingua italiana',
    'or': 'Orientamento',
    'ing': 'Lingua inglese',
    'mate': 'Matematica',
    'info': 'Informatica',
    'dir': 'Diritto del lavoro',
    'eco': 'Economia',
    'edciv': 'Educazione civica',
    'sto': 'Storia ed educazione civica',
    'gest': 'Gestione aziendale',
    'comu': 'Comunicazione',
    'pers': 'Personalizzazione',
    'persdid': 'Personalizzazione',
    'persvivi': 'Personalizzazione',
    'ss': 'Sviluppo sostenibile',
    # Technical subjects - FB
    'cad': 'CAD',
    'cn': 'Controllo numerico',
    'offm': 'Officina meccanica',
    'taglio': 'Taglio metalli',
    'progtermo': 'Progettazione termoidraulica',
    'labtermo': 'Laboratorio termoidraulico',
    'labbasse': 'Laboratorio termoidraulico',
    'dis': 'Disegno edile ed impiantistico',
    'tm': 'Tecnologia meccanica',
    # Technical subjects - FA
    'conf': 'Confezione',
    'Modello': 'Modello',
    'Mou': 'Moulage',
    'tt': 'Tecnologia tessile',
    'ttborr': 'Tecnologia tessile',
    'merc': 'Mercatistica',
    'vendser': 'Vendita e servizi',
    'imm': 'Immagine',
    'arredi': 'Arredamento',
    'relaz': 'Relazioni',
    'cli': 'Clienti',
    'ig': 'Igiene',
    'cosme': 'Cosmetica',
    'ana': 'Anatomia',
    'lav': 'Laboratorio acconciatura',
    'perspic': 'Personal image',
    'conta': 'Contabilita',
    'ravis': 'Rappresentazione visiva',
    'stvis': 'Stile visivo',
    'prepvis': 'Progettazione visiva',
    'stomoda': 'Storia della moda',
    'servtecT28': 'Servizi tecnici',
    'ELIA': 'ELIA',
    'ELIA LES': 'ELIA',
    'fig': 'Fisica',
    'radid': 'Recupero didattico',
}


def parse_date(date_str: str, year: int = 2025) -> Optional[date]:
    """Parse Italian date format like '8 set' or '01 ott'."""
    match = re.match(r'(\d+)\s+([a-z]{3})', date_str.strip().lower())
    if not match:
        return None

    day = int(match.group(1))
    month_str = match.group(2)

    if month_str not in MONTH_MAP:
        return None

    return date(year, MONTH_MAP[month_str], day)


def parse_datagenerator(java_path: Path) -> Dict[str, Dict[str, Dict[str, int]]]:
    """
    Parse DataGenerator.java to extract course hours.

    Returns:
        dict: {teacher: {group: {subject: hours}}}
    """
    courses = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))

    with open(java_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Match addCourse calls: addCourse(teachers, groups, "Teacher", "Subject", "Group", hours);
    pattern = r'addCourse\s*\(\s*teachers\s*,\s*groups\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*\)'

    for match in re.finditer(pattern, content):
        teacher = match.group(1)
        subject = match.group(2)
        group = match.group(3)
        hours = int(match.group(4))

        if hours > 0:  # Skip 0-hour courses (impresa simulata)
            courses[teacher][group][subject] += hours

    return courses


def parse_spalmone_csv(csv_path: Path) -> Dict[str, Dict[str, Dict[str, int]]]:
    """
    Parse spalmone.csv to extract lesson hours.

    Returns:
        dict: {teacher: {group: {subject: hours}}}
    """
    hours = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))

    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        rows = list(reader)

    # Skip header rows (first 2 rows)
    for row in rows[2:]:
        if len(row) < 4:
            continue

        date_str = row[1]
        if not date_str.strip():
            continue

        # Process each group column
        for col_idx, group_name in GROUP_COLUMNS.items():
            if col_idx + 1 >= len(row):
                continue

            subject_cell = row[col_idx].strip() if col_idx < len(row) else ''
            teacher_cell = row[col_idx + 1].strip() if col_idx + 1 < len(row) else ''

            if not subject_cell or not teacher_cell:
                continue

            # Normalize teacher name
            teacher_name = None
            for key, mapped in TEACHER_MAP.items():
                if key.lower() in teacher_cell.lower():
                    teacher_name = mapped
                    break

            if not teacher_name or teacher_name == 'External':
                continue

            # Normalize subject
            subject_name = subject_cell
            for key, mapped in SUBJECT_MAP.items():
                if key.lower() == subject_cell.lower():
                    subject_name = mapped
                    break

            # Count 1 hour per cell
            hours[teacher_name][group_name][subject_name] += 1

    return hours


def compare_sources(dg_hours: dict, csv_hours: dict) -> Tuple[dict, list]:
    """
    Compare DataGenerator hours with CSV hours.

    Returns:
        tuple: (comparison_dict, issues_list)
    """
    comparison = defaultdict(lambda: defaultdict(dict))
    issues = []

    # Get all teacher/group combinations from both sources
    all_teachers = set(dg_hours.keys()) | set(csv_hours.keys())

    for teacher in sorted(all_teachers):
        dg_groups = set(dg_hours.get(teacher, {}).keys())
        csv_groups = set(csv_hours.get(teacher, {}).keys())
        all_groups = dg_groups | csv_groups

        for group in sorted(all_groups):
            dg_subjects = dg_hours.get(teacher, {}).get(group, {})
            csv_subjects = csv_hours.get(teacher, {}).get(group, {})

            # Aggregate by teacher/group (not subject) for comparison
            dg_total = sum(dg_subjects.values())
            csv_total = sum(csv_subjects.values())

            diff = csv_total - dg_total
            pct_diff = (diff / dg_total * 100) if dg_total > 0 else (100 if csv_total > 0 else 0)

            comparison[teacher][group] = {
                'csv': csv_total,
                'dg': dg_total,
                'diff': diff,
                'pct_diff': pct_diff,
                'csv_subjects': csv_subjects,
                'dg_subjects': dg_subjects,
            }

            # Detect issues
            if dg_total > 0 and abs(diff) > 10:
                issues.append({
                    'teacher': teacher,
                    'group': group,
                    'type': 'DISCREPANCY',
                    'csv': csv_total,
                    'dg': dg_total,
                    'diff': diff,
                    'pct_diff': pct_diff,
                    'description': f"CSV shows {csv_total}h, DataGenerator has {dg_total}h ({abs(diff)}h difference)"
                })

            # Check for missing in either source
            if dg_total > 0 and csv_total == 0:
                issues.append({
                    'teacher': teacher,
                    'group': group,
                    'type': 'MISSING_IN_CSV',
                    'csv': 0,
                    'dg': dg_total,
                    'diff': -dg_total,
                    'pct_diff': -100,
                    'description': f"DataGenerator has {dg_total}h but CSV has no entries"
                })

            if csv_total > 0 and dg_total == 0:
                issues.append({
                    'teacher': teacher,
                    'group': group,
                    'type': 'MISSING_IN_DG',
                    'csv': csv_total,
                    'dg': 0,
                    'diff': csv_total,
                    'pct_diff': 100,
                    'description': f"CSV has {csv_total}h but DataGenerator has no entries"
                })

    return comparison, issues


def analyze_feasibility(comparison: dict, issues: list) -> list:
    """
    Analyze whether hours are feasible given constraints.

    Returns:
        list of additional issues
    """
    feasibility_issues = []

    # Known constraints:
    # - T28: Thursday only (external)
    # - Period 3 (Apr-Jun): ~9-10 Thursdays
    # - Block size for labs: 6 hours

    for teacher, groups in comparison.items():
        for group, data in groups.items():
            dg_hours = data['dg']

            # Check T28's feasibility (external, Thursday only)
            if teacher == 'T28' and group in ['G10', 'G12']:
                blocks_needed = dg_hours / 6
                # Period 3 has ~9 Thursdays (Apr 7 - Jun 1)
                available_thursdays = 9

                if blocks_needed > available_thursdays:
                    feasibility_issues.append({
                        'teacher': teacher,
                        'group': group,
                        'type': 'INFEASIBLE',
                        'csv': data['csv'],
                        'dg': dg_hours,
                        'diff': data['diff'],
                        'pct_diff': data['pct_diff'],
                        'description': f"INFEASIBLE: {dg_hours}h = {blocks_needed:.1f} blocks needed, "
                                      f"but only ~{available_thursdays} Thursdays available in Period 3"
                    })

    return feasibility_issues


def print_report(comparison: dict, issues: list, show_subjects: bool = False):
    """Print a formatted comparison report."""
    print("=" * 100)
    print("DataGenerator Verification Report")
    print("=" * 100)
    print()

    # Summary statistics
    total_teachers = len(comparison)
    total_combos = sum(len(groups) for groups in comparison.values())
    total_csv = sum(data['csv'] for teacher in comparison.values() for data in teacher.values())
    total_dg = sum(data['dg'] for teacher in comparison.values() for data in teacher.values())

    print(f"Summary: {total_teachers} teachers, {total_combos} teacher/group combinations")
    print(f"Total hours: CSV={total_csv}, DataGenerator={total_dg}, Diff={total_csv - total_dg}")
    print()

    # Print comparison table
    print("Hours Comparison: CSV vs DataGenerator (by Teacher/Group)")
    print("-" * 100)
    print(f"{'Teacher':<15} {'Group':<10} {'CSV':>8} {'DG':>8} {'Diff':>8} {'%':>8} {'Status':<15}")
    print("-" * 100)

    for teacher in sorted(comparison.keys()):
        for group in sorted(comparison[teacher].keys()):
            data = comparison[teacher][group]

            if data['csv'] == 0 and data['dg'] == 0:
                continue

            # Determine status
            if data['diff'] == 0:
                status = "✓ OK"
            elif abs(data['diff']) <= 5:
                status = "~ Minor"
            elif abs(data['diff']) <= 10:
                status = "⚠ Check"
            else:
                status = "❌ MISMATCH"

            print(f"{teacher:<15} {group:<10} {data['csv']:>8} {data['dg']:>8} "
                  f"{data['diff']:>+8} {data['pct_diff']:>+7.1f}% {status:<15}")

    print("-" * 100)
    print(f"{'TOTAL':<15} {'':<10} {total_csv:>8} {total_dg:>8} {total_csv - total_dg:>+8}")
    print()

    # Print issues
    if issues:
        print("=" * 100)
        print("ISSUES DETECTED")
        print("=" * 100)

        # Group by type
        by_type = defaultdict(list)
        for issue in issues:
            by_type[issue['type']].append(issue)

        for issue_type, type_issues in sorted(by_type.items()):
            print(f"\n### {issue_type} ({len(type_issues)} issues)")
            print("-" * 80)
            for issue in sorted(type_issues, key=lambda x: (x['teacher'], x['group'])):
                print(f"  [{issue['teacher']} / {issue['group']}]")
                print(f"    {issue['description']}")
                if show_subjects and 'csv_subjects' in issue:
                    print(f"    CSV subjects: {dict(issue.get('csv_subjects', {}))}")
                    print(f"    DG subjects:  {dict(issue.get('dg_subjects', {}))}")
                print()

        print("-" * 80)
        print("RECOMMENDATION:")
        print("  1. Verify course hours in spalmone.csv (the validated human solution)")
        print("  2. Cross-reference with the school's official course catalog")
        print("  3. Update DataGenerator.java with correct hours AFTER user confirmation")
        print("  4. DO NOT modify DataGenerator without explicit user permission")
        print("-" * 80)
    else:
        print("✓ No critical discrepancies found!")

    print()


def main():
    parser = argparse.ArgumentParser(description='Verify DataGenerator hours against spalmone.csv')
    parser.add_argument('--csv', type=str, default='scripts/data/spalmone.csv',
                        help='Path to spalmone.csv')
    parser.add_argument('--java', type=str,
                        default='src/main/java/com/school/planner/bootstrap/DataGenerator.java',
                        help='Path to DataGenerator.java')
    parser.add_argument('--json', action='store_true', help='Output as JSON')
    parser.add_argument('--subjects', action='store_true', help='Show subject breakdown')
    parser.add_argument('--all', action='store_true', help='Show all entries (not just discrepancies)')
    args = parser.parse_args()

    csv_path = Path(args.csv)
    java_path = Path(args.java)

    if not csv_path.exists():
        print(f"ERROR: CSV file not found: {csv_path}", file=sys.stderr)
        sys.exit(2)

    if not java_path.exists():
        print(f"ERROR: Java file not found: {java_path}", file=sys.stderr)
        sys.exit(2)

    try:
        # Parse both sources
        print(f"Parsing DataGenerator.java: {java_path}", file=sys.stderr)
        dg_hours = parse_datagenerator(java_path)
        print(f"  Found {len(dg_hours)} teachers", file=sys.stderr)

        print(f"Parsing spalmone.csv: {csv_path}", file=sys.stderr)
        csv_hours = parse_spalmone_csv(csv_path)
        print(f"  Found {len(csv_hours)} teachers", file=sys.stderr)
        print(file=sys.stderr)

        # Compare
        comparison, issues = compare_sources(dg_hours, csv_hours)

        # Analyze feasibility
        feasibility_issues = analyze_feasibility(comparison, issues)
        all_issues = issues + feasibility_issues

        # Output
        if args.json:
            import json
            print(json.dumps({
                'summary': {
                    'total_teachers': len(comparison),
                    'total_combinations': sum(len(g) for g in comparison.values()),
                    'total_csv_hours': sum(d['csv'] for t in comparison.values() for d in t.values()),
                    'total_dg_hours': sum(d['dg'] for t in comparison.values() for d in t.values()),
                },
                'comparison': {t: {g: {k: v for k, v in d.items() if k not in ['csv_subjects', 'dg_subjects']}
                                   for g, d in groups.items()}
                              for t, groups in comparison.items()},
                'issues': all_issues
            }, indent=2))
        else:
            print_report(comparison, all_issues, show_subjects=args.subjects)

        # Exit with error code if critical issues found
        critical_issues = [i for i in all_issues if i['type'] in ('INFEASIBLE', 'DISCREPANCY')]
        sys.exit(1 if critical_issues else 0)

    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(2)


if __name__ == '__main__':
    main()
