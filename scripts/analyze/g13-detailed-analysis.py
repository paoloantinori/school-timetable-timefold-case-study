#!/usr/bin/env python3
"""
G13 Group Detailed Analysis

Shows per-subject breakdown for G13 to identify the 100-hour discrepancy.
"""

import csv
import re
from collections import defaultdict
from pathlib import Path

# Same mappings as analyze-group-discrepancies.py
SUBJECT_MAP = {
    'Sic': 'Sicurezza', 'sci': 'Scienze', 'ss': 'Scienze sociali',
    'ita': 'Lingua italiana', 'or': 'Orientamento',
    'ing': 'Lingua inglese', 'mate': 'Matematica',
    'info': 'Informatica', 'dir': 'Diritto del lavoro',
    'eco': 'Economia', 'edciv': 'Educazione civica',
    'sto': 'Storia', 'stomoda': 'Storia della moda',
    'gest': 'Gestione aziendale', 'comu': 'Comunicazione',
    'pers': 'Personalizzazione', 'persdid': 'Personalizzazione',
    'persvivi': 'Personalizzazione',
    'cad': 'CAD', 'cn': 'Controllo numerico',
    'offm': 'Officina meccanica', 'taglio': 'Taglio metalli',
    'progtermo': 'Progettazione termoidraulica',
    'labtermo': 'Laboratorio termoidraulico',
    'labbasse': 'Laboratorio termoidraulico',
    'dis': 'Disegno edile ed impiantistico',
    'tm': 'Tecnologia meccanica',
    'conf': 'Confezione', 'Modello': 'Modello',
    'Mou': 'Moulage', 'tt': 'Tecnologia tessile',
    'ttborr': 'Tecnologia tessile', 'merc': 'Mercatistica',
    'vendser': 'Vendita e servizi', 'imm': 'Immagine',
    'arredi': 'Arredamento', 'relaz': 'Relazioni',
    'cli': 'Clienti', 'ig': 'Igiene', 'cosme': 'Cosmetica',
    'ana': 'Anatomia', 'lav': 'Laboratorio acconciatura',
    'perspic': 'Personal image', 'conta': 'Contabilita',
    'ravis': 'Rappresentazione visiva', 'stvis': 'Stile visivo',
    'prepvis': 'Progettazione visiva',
    'servtecT28': 'Servizi tecnici', 'ELIA': 'ELIA',
    'ELIA LES': 'ELIA', 'fig': 'Fisica',
    'radid': 'Recupero didattico', 'sciv': 'Scienze',
    # Additional aliases
    'Storia': 'Storia', 'Diritto': 'Diritto del lavoro',
    'R16': 'R16', 'Sart': 'R16',
    't27': 'T27', 'preppic': 'Progettazione visiva',
    'stT28': 'Storia ed educazione civica',
    'compsoc': 'Comportamento sociale',
    'ra': 'Recupero didattico', 'rat': 'Recupero didattico',
    'RAT': 'Recupero didattico', 'raviv': 'Rappresentazione visiva',
    # G13-specific aliases
    'gest': 'Gestione contabile',
    'cli': 'Comunicazione con il cliente',
    'vendser': 'Vendita e servizi',
    'prepalt': 'Preparazione all\'alternanza',
    "prepalt'": 'Preparazione all\'alternanza',
    'perspic': 'Personalizzazione',
    'persvivi': 'Personalizzazione',
    'off': 'Officina meccanica',
    'lab termo': 'Laboratorio termoidraulico',
    'labtermo': 'Laboratorio termoidraulico',
    'ELIA LES': 'ELIA', 'dir': 'Diritto',
    # Non-curriculum (to exclude)
    'stage': 'Stage', 'esame': 'Esame', 'comp': 'Componenti pratici',
}


def parse_datagenerator(java_path: Path) -> dict:
    """Parse DataGenerator.java for G13 courses."""
    courses = defaultdict(int)
    with open(java_path, 'r', encoding='utf-8') as f:
        content = f.read()
    pattern = r'addCourse\s*\(\s*teachers\s*,\s*groups\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*\)'
    for match in re.finditer(pattern, content):
        teacher, subject, group, hours = match.groups()
        if group == 'G13' and int(hours) > 0:
            courses[subject] += int(hours)
    return dict(courses)


def parse_spalmone_csv(csv_path: Path) -> dict:
    """Parse spalmone.csv for G13 lessons."""
    hours = defaultdict(int)
    excluded = {'azienda', 'stage', 'esame', 'comp'}

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
        if any(excl in subject.lower() for excl in excluded):
            continue

        # Normalize using subject map
        normalized = subject
        for key, mapped in SUBJECT_MAP.items():
            if key.lower() == subject.lower():
                normalized = mapped
                break

        hours[normalized] += 1
    return dict(hours)


def print_G13_analysis():
    """Print detailed G13 comparison."""
    java_path = Path('src/main/java/com/school/planner/bootstrap/DataGenerator.java')
    csv_path = Path('scripts/data/spalmone.csv')

    dg_courses = parse_datagenerator(java_path)
    csv_courses = parse_spalmone_csv(csv_path)

    print("=" * 100)
    print("G13 Group: Detailed Subject Analysis")
    print("=" * 100)
    print()

    dg_total = sum(dg_courses.values())
    csv_total = sum(csv_courses.values())
    diff = csv_total - dg_total
    pct = (diff / dg_total * 100) if dg_total > 0 else 0

    print(f"TOTAL: DataGenerator={dg_total}h, spalmone.csv={csv_total}h, Diff={diff:+}h ({pct:+.1f}%)")
    print()

    # Get all subjects
    all_subjects = set(dg_courses.keys()) | set(csv_courses.keys())

    print(f"{'DataGenerator Subject':<40} {'DG':>6} {'CSV':>6} {'CSV Subject':<40} {'Diff':>6}")
    print("-" * 100)

    # Group by suspected mappings
    dg_by_normalized = {}
    for subj in dg_courses:
        for key, mapped in SUBJECT_MAP.items():
            if subj.lower() == key.lower() or subj == mapped:
                dg_by_normalized[mapped] = dg_courses[subj]
                break
        else:
            dg_by_normalized[subj] = dg_courses[subj]

    csv_by_normalized = {}
    for subj in csv_courses:
        for key, mapped in SUBJECT_MAP.items():
            if subj.lower() == key.lower() or subj == mapped:
                csv_by_normalized[mapped] = csv_courses[subj]
                break
        else:
            csv_by_normalized[subj] = csv_courses[subj]

    # Print side-by-side comparison
    for norm_subj in sorted(set(dg_by_normalized.keys()) | set(csv_by_normalized.keys())):
        dg_h = dg_by_normalized.get(norm_subj, 0)
        csv_h = csv_by_normalized.get(norm_subj, 0)
        d = csv_h - dg_h

        # Get original names for display
        dg_orig = [s for s in dg_courses if SUBJECT_MAP.get(s, s) == norm_subj]
        csv_orig = [s for s in csv_courses if SUBJECT_MAP.get(s, s) == norm_subj]

        dg_name = dg_orig[0] if dg_orig else "-"
        csv_name = csv_orig[0] if csv_orig else "-"

        marker = ''
        if dg_h == 0 and csv_h > 0:
            marker = ' ← ONLY IN CSV'
        elif csv_h == 0 and dg_h > 0:
            marker = ' ← ONLY IN DG'
        elif d != 0:
            marker = f' ({d:+}h)'

        print(f"{dg_name:<40} {dg_h:>6} {csv_h:>6} {csv_name:<40}{marker}")

    print("-" * 100)
    print()

    # Identify specific discrepancies
    print("POTENTIAL ISSUES:")
    print()

    # Check Servizi vendita
    dg_vend = sum(h for s, h in dg_courses.items() if 'Servizi vendita' in s)
    csv_vend = sum(h for s, h in csv_courses.items() if 'vend' in s.lower())
    print(f"1. Servizi vendita:")
    print(f"   DataGenerator: {dg_vend}h (Servizi vendita A: 76h + Servizi vendita B: 100h = 176h)")
    print(f"   spalmone.csv: {csv_vend}h (vendser entries)")
    print(f"   Discrepancy: {csv_vend - dg_vend:+}h")
    print()

    # Check Personalizzazione
    dg_pers = sum(h for s, h in dg_courses.items() if 'Personalizzazione' in s)
    csv_pers = sum(h for s, h in csv_courses.items() if 'pers' in s.lower())
    print(f"2. Personalizzazione:")
    print(f"   DataGenerator: {dg_pers}h (A: 30h + B: 30h = 60h)")
    print(f"   spalmone.csv: {csv_pers}h (perspic + persvivi)")
    print(f"   Discrepancy: {csv_pers - dg_pers:+}h")
    print()

    # Check for unique DG subjects
    dg_only = set(dg_courses.keys()) - {s for s in csv_courses.keys()
                                            for norm in SUBJECT_MAP.values()
                                            if norm in SUBJECT_MAP.get(s, s)}
    if dg_only:
        print(f"3. ONLY IN DATAGENERATOR ({len(dg_only)} subjects, {sum(dg_courses[s] for s in dg_only)}h):")
        for s in sorted(dg_only):
            print(f"   - {s}: {dg_courses[s]}h")
        print()

    # Check for unique CSV subjects
    csv_only = set(csv_courses.keys()) - {s for s in dg_courses.keys()
                                            for norm in SUBJECT_MAP.values()
                                            if norm in SUBJECT_MAP.get(s, s)}
    if csv_only:
        print(f"4. ONLY IN CSV ({len(csv_only)} subjects, {sum(csv_courses[s] for s in csv_only)}h):")
        for s in sorted(csv_only):
            print(f"   - {s}: {csv_courses[s]}h")
        print()


if __name__ == '__main__':
    print_G13_analysis()
