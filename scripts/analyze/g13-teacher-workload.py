#!/usr/bin/env python3
"""
G13 Teacher Workload Analysis

Counts hours by teacher in spalmone.csv vs DataGenerator expectations
to identify if the missing 100h appears under a different name/teacher.
"""

import csv
import re
from collections import defaultdict
from pathlib import Path

# Teacher name normalization
TEACHER_MAP = {
    'cont': 'Conti',
    'ter': 'T23',
    'bar': 'T02',
    'slob': 'T21',
    'bent': 'T04',
    'caset': 'T08',
    'vail': 'T25',
    'bass': 'T03',
    'bonc': 'T06',
    't29': 'T29',
    'did': 'T11',
    't05': 'T05',
    'fior': 'T12',
    'xhek': 'T31',
    'mous': 'T15',
    'del': 'T10',
    't30': 'T30',
    'garav': 'T13',
    'cel': 'T09',
    't28': 'T28',
    'pici': 'T18',
    'vis': 'T26',
    'murv': 'T16',
    'pecc': 'T17',
    't20': 'T20',
    'borr': 'T07',
    'lavac': 'T14',
    'est': 'External',
}

# Subject aliases for grouping
SUBJECT_MAP = {
    'Sicurezza': ['sic', 'Sicurezza'],
    'Scienze': ['sci', 'sciv', 'Scienze'],
    'Storia': ['sto', 'stT28', 'Storia', 'Storia ed educazione civica'],
    'Lingua italiana': ['ita', 'Lingua italiana'],
    'Orientamento': ['or', 'Orientamento'],
    'Lingua inglese': ['ing', 'Lingua inglese'],
    'Matematica': ['mate', 'Matematica'],
    'Informatica': ['info', 'Informatica'],
    'Gestione contabile': ['gest', 'Gestione contabile'],
    'Comunicazione': ['comu', 'Comunicazione'],
    'Comunicazione con il cliente': ['cli', 'Comunicazione con il cliente'],
    'Personalizzazione': ['pers', 'persdid', 'persvivi', 'perspic', 'Personalizzazione', 'Personal image'],
    'Preparazione all\'alternanza': ['prepalt', 'Preparazione all\'alternanza'],
    'Vendita e servizi': ['vendser', 'Vendita e servizi', 'Servizi vendita'],
    'Diritto': ['dir', 'Diritto', 'Diritto del lavoro'],
    'Educazione civica': ['edciv', 'Educazione civica'],
    'Economia': ['eco', 'Economia'],
    'CAD': ['cad', 'CAD'],
    'Controllo numerico': ['cn', 'Controllo numerico'],
    'Officina meccanica': ['off', 'offm', 'Officina meccanica'],
    'Taglio metalli': ['taglio', 'Taglio metalli'],
    'Progettazione termoidraulica': ['progtermo', 'Progettazione termoidraulica'],
    'Laboratorio termoidraulico': ['labtermo', 'labbasse', 'lab termo', 'Laboratorio termoidraulico'],
    'Disegno edile': ['dis', 'Disegno edile ed impiantistico'],
    'Tecnologia meccanica': ['tm', 'Tecnologia meccanica'],
    'Fisica': ['fig', 'Fisica'],
    'Recupero didattico': ['ra', 'rat', 'radid', 'RAT', 'Recupero didattico'],
    'ELIA': ['ELIA', 'ELIA LES'],
}

# Non-curriculum to exclude
EXCLUDED = {'azienda', 'stage', 'esame', 'comp'}


def normalize_teacher(name: str) -> str:
    """Normalize teacher name from CSV abbreviation."""
    name = name.strip().lower()
    for abbrev, full in TEACHER_MAP.items():
        if name == abbrev.lower() or name in full.lower():
            return full
    return name.title() if name else 'Unknown'


def normalize_subject(subject: str) -> str:
    """Normalize subject name to canonical form."""
    subject = subject.strip().lower()
    for canonical, variants in SUBJECT_MAP.items():
        for variant in variants:
            if subject == variant.lower() or subject in variant.lower():
                return canonical
    return subject.title() if subject else 'Unknown'


def parse_datagenerator(java_path: Path) -> dict:
    """Parse DataGenerator.java for G13 courses by teacher."""
    teachers_data = defaultdict(lambda: defaultdict(int))
    with open(java_path, 'r', encoding='utf-8') as f:
        content = f.read()
    pattern = r'addCourse\s*\(\s*teachers\s*,\s*groups\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*\)'
    for match in re.finditer(pattern, content):
        teacher, subject, group, hours = match.groups()
        if group == 'G13' and int(hours) > 0:
            teachers_data[teacher][subject] += int(hours)
    return dict(teachers_data)


def parse_spalmone_csv(csv_path: Path) -> dict:
    """Parse spalmone.csv for G13 lessons by teacher."""
    teachers_data = defaultdict(lambda: defaultdict(int))

    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        rows = list(reader)

    # G13 is at columns 19-21 (materia, formatore, aula)
    for row in rows[2:]:
        if len(row) < 22:
            continue
        subject = row[19].strip() if 19 < len(row) else ''
        teacher = row[20].strip() if 20 < len(row) else ''
        if not subject or not teacher:
            continue
        if any(excl in subject.lower() for excl in EXCLUDED):
            continue

        norm_teacher = normalize_teacher(teacher)
        norm_subject = normalize_subject(subject)
        teachers_data[norm_teacher][norm_subject] += 1

    return dict(teachers_data)


def print_teacher_workload():
    """Print comprehensive teacher workload comparison."""
    java_path = Path('src/main/java/com/school/planner/bootstrap/DataGenerator.java')
    csv_path = Path('scripts/data/spalmone.csv')

    dg_teachers = parse_datagenerator(java_path)
    csv_teachers = parse_spalmone_csv(csv_path)

    print("=" * 120)
    print("G13 Teacher Workload: DataGenerator vs spalmone.csv")
    print("=" * 120)
    print()

    # Get all teachers
    all_teachers = set(dg_teachers.keys()) | set(csv_teachers.keys())

    dg_total = sum(sum(subjects.values()) for subjects in dg_teachers.values())
    csv_total = sum(sum(subjects.values()) for subjects in csv_teachers.values())

    print(f"TOTAL HOURS: DataGenerator={dg_total}h, spalmone.csv={csv_total}h, Diff={csv_total - dg_total:+}h")
    print()

    # Sort by workload discrepancy
    teacher_diffs = []
    for teacher in all_teachers:
        dg_h = sum(dg_teachers.get(teacher, {}).values())
        csv_h = sum(csv_teachers.get(teacher, {}).values())
        teacher_diffs.append((teacher, dg_h, csv_h, csv_h - dg_h))

    teacher_diffs.sort(key=lambda x: abs(x[3]), reverse=True)

    print(f"{'Teacher':<20} {'DG Total':>10} {'CSV Total':>10} {'Diff':>10} {'%':>8}")
    print("-" * 120)
    for teacher, dg_h, csv_h, diff in teacher_diffs:
        pct = (diff / dg_h * 100) if dg_h > 0 else 0
        marker = ''
        if dg_h == 0 and csv_h > 0:
            marker = ' ← ONLY IN CSV'
        elif csv_h == 0 and dg_h > 0:
            marker = ' ← ONLY IN DG'
        elif abs(diff) > 10:
            marker = ' ⚠ LARGE DIFF'
        print(f"{teacher:<20} {dg_h:>10} {csv_h:>10} {diff:>+10} {pct:>+7.1f}%{marker}")

    print()
    print("=" * 120)
    print()

    # Detailed breakdown for teachers with discrepancies
    print("DETAILED SUBJECT BREAKDOWN (teachers with discrepancies > 0):")
    print("=" * 120)
    print()

    for teacher, dg_h, csv_h, diff in teacher_diffs:
        if diff == 0:
            continue

        dg_subjects = dg_teachers.get(teacher, {})
        csv_subjects = csv_teachers.get(teacher, {})

        print(f"{teacher} ({dg_h}h DG vs {csv_h}h CSV, {diff:+}h):")
        print("-" * 120)

        all_subjects = set(dg_subjects.keys()) | set(csv_subjects.keys())

        print(f"  {'Subject':<40} {'DG':>6} {'CSV':>6} {'Diff':>6}")
        print("  " + "-" * 70)

        for subject in sorted(all_subjects):
            dg_subj_h = dg_subjects.get(subject, 0)
            csv_subj_h = csv_subjects.get(subject, 0)
            subj_diff = csv_subj_h - dg_subj_h

            marker = ''
            if dg_subj_h == 0 and csv_subj_h > 0:
                marker = ' ← ONLY IN CSV'
            elif csv_subj_h == 0 and dg_subj_h > 0:
                marker = ' ← ONLY IN DG'
            elif subj_diff != 0:
                marker = f' ({subj_diff:+}h)'

            print(f"  {subject:<40} {dg_subj_h:>6} {csv_subj_h:>6} {subj_diff:>+6}{marker}")

        print()

    # Summary of missing hours
    print("=" * 120)
    print("SUMMARY OF MISSING 100 HOURS:")
    print("=" * 120)
    print()

    # Find which DG subjects have 0 CSV hours
    missing_in_csv = []
    for teacher, subjects in dg_teachers.items():
        csv_subjects = csv_teachers.get(teacher, {})
        for subject, hours in subjects.items():
            csv_hours = csv_subjects.get(subject, 0)
            if csv_hours == 0 and hours > 0:
                missing_in_csv.append((teacher, subject, hours))

    if missing_in_csv:
        print("Courses in DataGenerator with 0 hours in spalmone.csv:")
        for teacher, subject, hours in sorted(missing_in_csv, key=lambda x: -x[2]):
            print(f"  - {teacher}: {subject} ({hours}h)")
        print()

    # Find which CSV subjects have 0 DG hours
    only_in_csv = []
    for teacher, subjects in csv_teachers.items():
        dg_subjects = dg_teachers.get(teacher, {})
        for subject, hours in subjects.items():
            dg_hours = dg_subjects.get(subject, 0)
            if dg_hours == 0 and hours > 0:
                only_in_csv.append((teacher, subject, hours))

    if only_in_csv:
        print("Courses in spalmone.csv with 0 hours in DataGenerator:")
        for teacher, subject, hours in sorted(only_in_csv, key=lambda x: -x[2]):
            print(f"  - {teacher}: {subject} ({hours}h)")
        print()

    # Check if Servizi vendita appears under different names
    print("=" * 120)
    print("SERVIZI VENDITA ANALYSIS:")
    print("=" * 120)
    print()

    dg_vend = sum(h for s in dg_teachers.values() for subj, h in s.items() if 'servizi' in subj.lower() or 'vendita' in subj.lower())
    csv_vend = sum(h for s in csv_teachers.values() for subj, h in s.items() if 'servizi' in subj.lower() or 'vendita' in subj.lower())

    print(f"DataGenerator: {dg_vend}h total (Servizi vendita A + B)")
    print(f"spalmone.csv: {csv_vend}h total (Vendita e_servi entries)")
    print(f"Discrepancy: {csv_vend - dg_vend:+}h")
    print()

    # Check all CSV subjects that might be related to sales/services
    print("All CSV subjects that might be 'Servizi vendita' under different names:")
    for teacher, subjects in csv_teachers.items():
        for subject, hours in subjects.items():
            if any(kw in subject.lower() for kw in ['vend', 'serv', 'comm', 'cli']):
                dg_h = dg_teachers.get(teacher, {}).get(subject, 0)
                marker = ' ← MATCH' if dg_h > 0 else ' ← NO DG MATCH'
                print(f"  {teacher}: {subject} ({hours}h){marker}")

    print()


if __name__ == '__main__':
    print_teacher_workload()
