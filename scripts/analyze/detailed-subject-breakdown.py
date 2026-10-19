#!/usr/bin/env python3
"""
Detailed Subject Breakdown for Warning Groups

Shows per-subject analysis for groups with >10% discrepancy.
"""

import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

# Same mappings as analyze-group-discrepancies.py
MONTH_MAP = {
    'gen': 1, 'feb': 2, 'mar': 3, 'apr': 4,
    'mag': 5, 'giu': 6, 'lug': 7, 'ago': 8,
    'set': 9, 'ott': 10, 'nov': 11, 'dic': 12
}

GROUP_COLUMNS = {
    4: 'G03', 7: 'G07', 10: 'G11',
    13: 'G04', 16: 'G08',
    19: 'G13',
    22: 'G02', 25: 'G06', 28: 'G10', 31: 'G12',
    34: 'G01', 37: 'G05', 40: 'G09',
    43: 'G14',
}

TEACHER_MAP = {
    'T23': 'T23', 'T02': 'T02',
    'T04': 'T04', 'T21': 'T21',
    'T08': 'T08', 'T25': 'T25',
    'T03': 'T03', 'T06': 'T06',
    'T29': 'T29', 'T11': 'T11', 'T05': 'T05',
    'T12': 'T12', 'T31': 'T31',
    'T15': 'T15', 'T10': 'T10', 'T30': 'T30',
    'T13': 'T13',
    'T09': 'T09', 'T28': 'T28',
    'T18': 'T18', 'T26': 'T26',
    'T16': 'T16', 'T17': 'T17',
    'T20': 'T20', 'T07': 'T07',
    'T14': 'T14',
    'est': 'External', 'est + Basse': 'T03',
}

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
    # Additional mappings for CSV names not in DataGenerator
    'comp': 'Componenti pratici',
    'esame': 'Esame',
    'stage': 'Stage',  # NOT "azienda" - this is alternating school activities
    'R16': 'R16',
    'Sart': 'R16',
    'Storia': 'Storia',
    'Diritto': 'Diritto del lavoro',
    't27': 'T27',
    'preppic': 'Progettazione visiva',
    'stT28': 'Storia ed educazione civica',
    'compsoc': 'Comportamento sociale',
    'ra': 'Recupero didattico',
    'rat': 'Recupero didattico',
    'RAT': 'Recupero didattico',
    'raviv': 'Rappresentazione visiva',
}


def parse_datagenerator(java_path: Path) -> dict:
    courses = defaultdict(lambda: defaultdict(int))
    with open(java_path, 'r', encoding='utf-8') as f:
        content = f.read()
    pattern = r'addCourse\s*\(\s*teachers\s*,\s*groups\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*\)'
    for match in re.finditer(pattern, content):
        teacher = match.group(1)
        subject = match.group(2)
        group = match.group(3)
        hours = int(match.group(4))
        if hours > 0:
            courses[group][subject] += hours
    return courses


def parse_spalmone_csv(csv_path: Path) -> dict:
    hours = defaultdict(lambda: defaultdict(int))
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        rows = list(reader)
    for row in rows[2:]:
        if len(row) < 4:
            continue
        if not row[1].strip():
            continue
        for col_idx, group_name in GROUP_COLUMNS.items():
            if col_idx + 1 >= len(row):
                continue
            subject_cell = row[col_idx].strip() if col_idx < len(row) else ''
            teacher_cell = row[col_idx + 1].strip() if col_idx + 1 < len(row) else ''
            if not subject_cell or not teacher_cell:
                continue
            if 'azienda' in subject_cell.lower():
                continue
            subject_name = subject_cell
            for key, mapped in SUBJECT_MAP.items():
                if key.lower() == subject_cell.lower():
                    subject_name = mapped
                    break
            hours[group_name][subject_name] += 1
    return hours


def print_subject_breakdown(group: str, dg_subjects: dict, csv_subjects: dict):
    print(f"\n{'=' * 100}")
    print(f"{group}: Subject Breakdown")
    print(f"{'=' * 100}\n")

    dg_total = sum(dg_subjects.values())
    csv_total = sum(csv_subjects.values())
    diff = csv_total - dg_total
    pct = (diff / dg_total * 100) if dg_total > 0 else 0

    print(f"Total: DataGenerator={dg_total}h, spalmone.csv={csv_total}h, Diff={diff:+}h ({pct:+.1f}%)")
    print()

    all_subjects = set(dg_subjects.keys()) | set(csv_subjects.keys())

    print(f"{'Subject':<35} {'DG':>8} {'CSV':>8} {'Diff':>8} {'%':>8}")
    print("-" * 100)

    for subject in sorted(all_subjects):
        dg_h = dg_subjects.get(subject, 0)
        csv_h = csv_subjects.get(subject, 0)
        d = csv_h - dg_h
        p = (d / dg_h * 100) if dg_h > 0 else (100 if csv_h > 0 else 0)

        marker = ''
        if dg_h == 0 and csv_h > 0:
            marker = ' ← MISSING IN DG'
        elif csv_h == 0 and dg_h > 0:
            marker = ' ← MISSING IN CSV'
        elif abs(p) > 20:
            marker = ' ⚠ LARGE DIFF'

        print(f"{subject:<35} {dg_h:>8} {csv_h:>8} {d:>+8} {p:>+7.1f}%{marker}")


def main():
    csv_path = Path('scripts/data/spalmone.csv')
    java_path = Path('src/main/java/com/school/planner/bootstrap/DataGenerator.java')

    dg_hours = parse_datagenerator(java_path)
    csv_hours = parse_spalmone_csv(csv_path)

    # Warning groups: >10% discrepancy
    warning_groups = ['G10', 'G09', 'G12', 'G08', 'G14']

    for group in warning_groups:
        dg_subjects = dg_hours.get(group, {})
        csv_subjects = csv_hours.get(group, {})
        print_subject_breakdown(group, dg_subjects, csv_subjects)


if __name__ == '__main__':
    main()
