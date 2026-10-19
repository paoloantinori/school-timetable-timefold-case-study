#!/usr/bin/env python3
"""
Group-Level Hour Discrepancy Analysis

Parses spalmone.csv and DataGenerator.java to compare total hours per student group.
This is the Phase 1 implementation for TASK-209.

Usage:
    python3 scripts/analyze-group-discrepancies.py

Output:
    - Group-by-group comparison table
    - Critical discrepancies (>50%) highlighted
    - Per-subject breakdown for problematic groups
"""

import csv
import re
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path
from typing import Optional, Dict, Set, Tuple, List

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
    # G14: Excluded from analysis
    # G14 is an independent micro-course with manual date selection.
    # It's not subject to main compaction rules and should be handled separately.
    # 43: 'G14',  # Excluded
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
    'sto': 'Storia',  # Maps to "Storia" in DataGenerator
    'stomoda': 'Storia della moda',
    'gest': 'Gestione aziendale',
    'comu': 'Comunicazione',
    'pers': 'Personalizzazione',
    'persdid': 'Personalizzazione',
    'persvivi': 'Personalizzazione',
    # G13-specific aliases
    'gest': 'Gestione contabile',  # Maps to Gestione contabile
    'cli': 'Comunicazione con il cliente',  # Maps to Comunicazione con il cliente
    'vendser': 'Vendita e servizi',  # Maps to Servizi vendita
    'prepalt': 'Preparazione all\'alternanza',  # Abbreviation
    "prepalt'": 'Preparazione all\'alternanza',  # Accent variant
    'perspic': 'Personalizzazione',  # Maps to Personalizzazione (A)
    'persvivi': 'Personalizzazione',  # Maps to Personalizzazione (B)
    # Technical subjects - FB
    'cad': 'CAD',
    'cn': 'Controllo numerico',
    'offm': 'Officina meccanica',
    'off': 'Officina meccanica',  # Abbreviation (used in G14)
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
    'servtecT28': 'Servizi tecnici',
    'ELIA': 'ELIA',
    'ELIA LES': 'ELIA',
    'fig': 'Fisica',
    'radid': 'Recupero didattico',
    'sciv': 'Scienze',
    # Aliases for CSV names that differ from DataGenerator
    'Storia': 'Storia',  # Direct alias
    'Diritto': 'Diritto del lavoro',  # Direct alias
    'R16': 'R16',  # Direct alias
    'Sart': 'R16',  # Abbreviation
    't27': 'T27',  # Direct alias
    'preppic': 'Progettazione visiva',  # Abbreviation
    'stT28': 'Storia ed educazione civica',  # Abbreviation
    'compsoc': 'Comportamento sociale',  # Abbreviation
    'comportamento sociale': 'Comportamento sociale',  # Direct
    'ra': 'Recupero didattico',  # Abbreviation
    'rat': 'Recupero didattico',  # Abbreviation
    'RAT': 'Recupero didattico',  # Uppercase
    'raviv': 'Rappresentazione visiva',  # Abbreviation
    # FA-specific aliases
    'Lavaggio': 'Laboratorio acconciatura',  # Different name for same subject
    'Cosmetologia': 'Cosmetica',  # Different name for same subject
    'Contabilità': 'Contabilita',  # Accent character
    "Contabilita'": 'Contabilita',  # Accent character variant
    'Consulenza d\'immagine': 'Immagine',  # Related subject, maps to Immagine
    'Relazione con il cliente': 'Clienti',  # Related subject, maps to Clienti
    # MODA-specific aliases
    'Arredi tessili': 'Arredamento',  # Maps to Arredamento
    'Merceologia': 'Mercatistica',  # Maps to Mercatistica
    'Economia e diritto del lavoro': 'Economia',  # Maps to Economia
    'Figurino': 'Fisica',  # Maps to Fisica (fig)
    'Taglio': 'Taglio metalli',  # MODA taglio maps to Taglio metalli
    'Tecnologia dei tessuti': 'Tecnologia tessile',  # Different name
    # FC-specific aliases
    'Disegno meccanico': 'Disegno edile ed impiantistico',  # Maps to dis
    'Lavorazioni meccaniche d\'officina': 'Officina meccanica',  # Maps to offm
    "Lavorazioni meccaniche d'officina": 'Officina meccanica',  # Accent variant
    'Economia e diritto del lavoro': 'Economia',  # Maps to Economia
    # G14-specific aliases
    'off': 'Officina meccanica',  # Abbreviation for G14
    'lab termo': 'Laboratorio termoidraulico',  # Abbreviation
    'labtermo': 'Laboratorio termoidraulico',  # Abbreviation
    'ELIA LES': 'ELIA',  # ELIA variant (G14 specific)
    'dir': 'Diritto',  # Maps to Diritto in DataGenerator
    # Non-curriculum subjects (to be excluded from comparison)
    'stage': 'Stage',  # PCTO activities (not solver-scheduled)
    'esame': 'Esame',  # Exam periods (not solver-scheduled)
    'comp': 'Componenti pratici',  # Practical components (not solver-scheduled)
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


def parse_datagenerator(java_path: Path) -> Dict[str, Dict[str, int]]:
    """
    Parse DataGenerator.java to extract course hours by student group.

    Returns:
        dict: {group: {subject: hours}}
    """
    courses = defaultdict(lambda: defaultdict(int))

    with open(java_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Match addCourse calls: addCourse(teachers, groups, "Teacher", "Subject", "Group", hours);
    pattern = r'addCourse\s*\(\s*teachers\s*,\s*groups\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*\)'

    for match in re.finditer(pattern, content):
        teacher = match.group(1)
        subject = match.group(2)
        group = match.group(3)
        hours = int(match.group(4))

        # Skip G14 (excluded from analysis)
        if group == 'G14':
            continue

        if hours > 0:  # Skip 0-hour courses (impresa simulata)
            courses[group][subject] += hours

    return courses


def parse_spalmone_csv(csv_path: Path) -> Dict[str, Dict[str, int]]:
    """
    Parse spalmone.csv to extract lesson hours by student group.

    Excludes:
    - "azienda" (stage activities handled by configurator)
    - "stage" (PCTO activities not solver-scheduled)
    - "esame" (exam periods not solver-scheduled)
    - "comp"/"Componenti pratici" (practical components not solver-scheduled)
    - G14 (independent micro-course, manually scheduled)

    Returns:
        dict: {group: {subject: hours}}
    """
    hours = defaultdict(lambda: defaultdict(int))

    # Subjects to exclude from comparison (not solver-scheduled)
    EXCLUDED_SUBJECTS = {'azienda', 'stage', 'esame', 'comp', 'Componenti pratici'}

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
            # Skip G14 (excluded from analysis)
            if group_name == 'G14':
                continue

            if col_idx + 1 >= len(row):
                continue

            subject_cell = row[col_idx].strip() if col_idx < len(row) else ''
            teacher_cell = row[col_idx + 1].strip() if col_idx + 1 < len(row) else ''

            if not subject_cell or not teacher_cell:
                continue

            # Skip excluded subjects (non-curriculum activities)
            if subject_cell.lower() in EXCLUDED_SUBJECTS:
                continue
            if any(excl in subject_cell.lower() for excl in EXCLUDED_SUBJECTS):
                continue

            # Normalize subject using alias map
            subject_name = subject_cell
            for key, mapped in SUBJECT_MAP.items():
                if key.lower() == subject_cell.lower():
                    subject_name = mapped
                    break

            # Count 1 hour per cell
            hours[group_name][subject_name] += 1

    return hours


def compare_by_group(
    dg_hours: Dict[str, Dict[str, int]],
    csv_hours: Dict[str, Dict[str, int]]
) -> List[Dict]:
    """
    Compare DataGenerator hours with CSV hours by student group.

    Returns:
        list: Comparison records sorted by discrepancy percentage
    """
    comparison = []

    # Get all groups from both sources
    all_groups = set(dg_hours.keys()) | set(csv_hours.keys())

    for group in sorted(all_groups):
        dg_subjects = dg_hours.get(group, {})
        csv_subjects = csv_hours.get(group, {})

        dg_total = sum(dg_subjects.values())
        csv_total = sum(csv_subjects.values())

        diff = csv_total - dg_total
        pct_diff = (diff / dg_total * 100) if dg_total > 0 else (100 if csv_total > 0 else 0)

        # Determine status
        if dg_total == 0 and csv_total == 0:
            status = "EMPTY"
        elif abs(diff) == 0:
            status = "OK"
        elif abs(pct_diff) > 50:
            status = "CRITICAL"
        elif abs(pct_diff) > 10:
            status = "WARNING"
        else:
            status = "MINOR"

        comparison.append({
            'group': group,
            'dg_total': dg_total,
            'csv_total': csv_total,
            'diff': diff,
            'pct_diff': pct_diff,
            'status': status,
            'dg_subjects': dg_subjects,
            'csv_subjects': csv_subjects,
        })

    # Sort by absolute percentage difference (descending)
    comparison.sort(key=lambda x: abs(x['pct_diff']), reverse=True)

    return comparison


def analyze_subject_breakdown(
    dg_subjects: Dict[str, int],
    csv_subjects: Dict[str, int],
    group: str
) -> List[Dict]:
    """
    Analyze subject-level breakdown for a group.

    Returns:
        list: Subject comparison records
    """
    breakdown = []

    # Get all subjects from both sources
    all_subjects = set(dg_subjects.keys()) | set(csv_subjects.keys())

    for subject in sorted(all_subjects):
        dg_hours = dg_subjects.get(subject, 0)
        csv_hours = csv_subjects.get(subject, 0)

        diff = csv_hours - dg_hours
        pct_diff = (diff / dg_hours * 100) if dg_hours > 0 else (100 if csv_hours > 0 else 0)

        breakdown.append({
            'subject': subject,
            'dg_hours': dg_hours,
            'csv_hours': csv_hours,
            'diff': diff,
            'pct_diff': pct_diff,
        })

    # Sort by absolute difference (descending)
    breakdown.sort(key=lambda x: abs(x['diff']), reverse=True)

    return breakdown


def print_group_comparison(comparison: List[Dict]):
    """Print the group-by-group comparison table."""
    print("=" * 110)
    print("Group-Level Hour Discrepancy Analysis")
    print("=" * 110)
    print()

    # Summary statistics
    total_dg = sum(c['dg_total'] for c in comparison)
    total_csv = sum(c['csv_total'] for c in comparison)
    critical_count = sum(1 for c in comparison if c['status'] == 'CRITICAL')
    warning_count = sum(1 for c in comparison if c['status'] == 'WARNING')

    print(f"Summary: {len(comparison)} student groups")
    print(f"Total hours: DataGenerator={total_dg}, spalmone.csv={total_csv}, Diff={total_csv - total_dg}")
    print(f"Critical discrepancies (>50%): {critical_count} groups")
    print(f"Warning discrepancies (>10%): {warning_count} groups")
    print()

    # Print comparison table
    print("Hours Comparison: DataGenerator vs spalmone.csv (by Student Group)")
    print("-" * 110)
    print(f"{'Group':<12} {'DataGenerator':>15} {'spalmone.csv':>15} {'Diff':>10} {'% Diff':>10} {'Status':<10}")
    print("-" * 110)

    for record in comparison:
        if record['dg_total'] == 0 and record['csv_total'] == 0:
            continue

        status_symbol = {
            'OK': '✓',
            'MINOR': '~',
            'WARNING': '⚠',
            'CRITICAL': '❌',
            'EMPTY': '-',
        }.get(record['status'], '?')

        print(f"{record['group']:<12} {record['dg_total']:>15} {record['csv_total']:>15} "
              f"{record['diff']:>+10} {record['pct_diff']:>+9.1f}% {status_symbol} {record['status']}")

    print("-" * 110)
    print(f"{'TOTAL':<12} {total_dg:>15} {total_csv:>15} {total_csv - total_dg:+>10}")
    print()


def print_critical_analysis(comparison: List[Dict]):
    """Print detailed subject breakdown for critical groups."""
    critical_groups = [c for c in comparison if c['status'] == 'CRITICAL']

    if not critical_groups:
        print("✓ No critical discrepancies (>50%) found!")
        return

    print("=" * 110)
    print(f"CRITICAL DISCREPANCIES ({len(critical_groups)} groups)")
    print("=" * 110)
    print()

    for record in critical_groups:
        print(f"### {record['group']}: {record['dg_total']}h → {record['csv_total']}h "
              f"({record['diff']:+}h, {record['pct_diff']:+.1f}%)")
        print("-" * 80)

        # Subject breakdown
        breakdown = analyze_subject_breakdown(
            record['dg_subjects'],
            record['csv_subjects'],
            record['group']
        )

        print(f"{'Subject':<30} {'DataGenerator':>12} {'spalmone.csv':>12} {'Diff':>8} {'%':>8}")
        print("-" * 80)

        for subject_record in breakdown:
            if subject_record['dg_hours'] == 0 and subject_record['csv_hours'] == 0:
                continue

            # Highlight missing subjects
            marker = ''
            if subject_record['dg_hours'] == 0:
                marker = ' ← MISSING IN DATAGENERATOR'
            elif subject_record['csv_hours'] == 0:
                marker = ' ← MISSING IN CSV'

            print(f"{subject_record['subject']:<30} {subject_record['dg_hours']:>12} "
                  f"{subject_record['csv_hours']:>12} {subject_record['diff']:>+8} "
                  f"{subject_record['pct_diff']:>+7.1f}%{marker}")

        print()


def print_recommendations(comparison: List[Dict]):
    """Print prioritized recommendations."""
    critical_groups = [c for c in comparison if c['status'] == 'CRITICAL']
    warning_groups = [c for c in comparison if c['status'] == 'WARNING']

    if not critical_groups and not warning_groups:
        return

    print("=" * 110)
    print("RECOMMENDATIONS")
    print("=" * 110)
    print()

    if critical_groups:
        print("### PRIORITY 1: CRITICAL (>50% difference)")
        print()
        for record in critical_groups:
            action = "ADD" if record['csv_total'] > record['dg_total'] else "REDUCE"
            print(f"  [{record['group']}] {action} ~{abs(record['diff'])} hours")
            print(f"    DataGenerator: {record['dg_total']}h, spalmone.csv: {record['csv_total']}h")
        print()

    if warning_groups:
        print("### PRIORITY 2: VERIFY (>10% difference)")
        print()
        for record in warning_groups:
            print(f"  [{record['group']}] Verify hour counts")
            print(f"    DataGenerator: {record['dg_total']}h, spalmone.csv: {record['csv_total']}h ({record['diff']:+}h)")
        print()

    print("-" * 110)
    print("IMPORTANT: Do NOT modify DataGenerator.java without user confirmation!")
    print("-" * 110)
    print()


def main():
    csv_path = Path('scripts/data/spalmone.csv')
    java_path = Path('src/main/java/com/school/planner/bootstrap/DataGenerator.java')

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
        print(f"  Found {len(dg_hours)} student groups", file=sys.stderr)

        print(f"Parsing spalmone.csv: {csv_path}", file=sys.stderr)
        csv_hours = parse_spalmone_csv(csv_path)
        print(f"  Found {len(csv_hours)} student groups", file=sys.stderr)
        print(file=sys.stderr)

        # Compare by group
        comparison = compare_by_group(dg_hours, csv_hours)

        # Print reports
        print_group_comparison(comparison)
        print_critical_analysis(comparison)
        print_recommendations(comparison)

        # Exit with error code if critical issues found
        critical_count = sum(1 for c in comparison if c['status'] == 'CRITICAL')
        sys.exit(1 if critical_count > 0 else 0)

    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(2)


if __name__ == '__main__':
    main()
