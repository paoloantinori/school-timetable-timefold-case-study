#!/usr/bin/env python3
"""
Spalmone CSV Parser

Parses the human planner's master schedule CSV ("spalmone") and produces structured
lesson data as JSON. The CSV is a cross-tabular format: 14 student groups × 3 columns
(materia/formatore/aula) per timeslot row.

Handles Italian date format ("8 set"), subject abbreviation normalization,
teacher name mapping, and co-teaching patterns.

Usage:
    python3 scripts/analyze/spalmone-parser.py <csv_path> [--output /tmp/spalmone-parsed.json]

Output JSON:
    {
      "total_lessons": N,
      "lessons": [{date, slot_index, day_of_week, student_group, subject, teacher, room, ...}],
      "unique_groups": [...],
      "unique_teachers": [...],
      "unique_subjects": [...]
    }
"""

import argparse
import csv
import json
import re
import sys
from datetime import date
from typing import Optional

from shared import OFF_CAMPUS_TEACHERS


# Italian month abbreviations to month numbers
MONTH_MAP = {
    'gen': 1, 'feb': 2, 'mar': 3, 'apr': 4,
    'mag': 5, 'giu': 6, 'lug': 7, 'ago': 8,
    'set': 9, 'ott': 10, 'nov': 11, 'dic': 12
}

# CSV column index to student group name mapping
# Based on header row analysis: 14 groups, 3 columns each (subject, teacher, room)
# Columns 0-3: week#, date, day_of_week, time_slot
# Then 14 groups x 3 columns = 42 columns
GROUP_COLUMNS = {
    4: 'G03',    # 1oit
    7: 'G07',    # 2oit
    10: 'G11',   # 3oit
    13: 'G04',    # g04
    16: 'G08',    # g08
    19: 'G13',     # g13
    22: 'G02',    # g02
    25: 'G06',    # g06
    28: 'G10',    # g10 (note space in CSV)
    31: 'G12',   # 4fa (different naming in DB)
    34: 'G01',   # g01
    37: 'G05',   # g05
    40: 'G09',   # g09
    43: 'G14',    # G14
}

# Teacher name normalization (CSV -> DB name)
TEACHER_MAP = {
    'T03': 'T03',
    'T06': 'T06',
    'T28': 'T28',
    'T18': 'T18',
    'T26': 'T26',
    'T25': 'T25',
    'T04': 'T04',
    'T23': 'T23',
    'T15': 'T15',
    'T09': 'T09',
    'T02': 'T02',
    'T13': 'T13',
    'T12': 'T12',
    'T30': 'T30',
    'T14': 'T14',
    'T10': 'T10',
    'T21': 'T21',
    'T17': 'T17',
    'T16': 'T16',
    'T20': 'T20',
    'T07': 'T07',
    'T29': 'T29',
    'T11': 'T11',
    'T05': 'T05',
    'T08': 'T08',
    # Special mappings for edge cases
    'stT28': 'T28',         # T28's services code maps to T28
    'servtecT28': 'T28',    # Servizi tecnici T28 maps to T28
    'labtermo': 'T03',   # Lab termoidraulico maps to T03 (default lab teacher)
    't27': 'T27',       # T27 teacher (capitalized for DB match)
    # Multi-teacher patterns
    'est + Basse': 'T03',  # External + T03 -> T03 as primary
    'est': None,  # External only - needs special handling
}

# Subject normalization (CSV abbreviations -> DB subject names)
SUBJECT_MAP = {
    'Sic': 'Sicurezza',
    'sci': 'Scienze',
    'ss': 'Scienze sociali',
    'ita': 'Italiano',
    'or': 'Orientamento',
    'ing': 'Inglese',
    'mate': 'Matematica',
    'info': 'Informatica',
    'dir': 'Diritto',
    'eco': 'Economia',
    'edciv': 'Educazione civica',
    'sto': 'Storia',
    'gest': 'Gestione',
    'comu': 'Comunicazione',
    'pers': 'Professionalizzazione',
    'persdid': 'Professionalizzazione didattica',
    'cad': 'CAD',
    'cn': 'Controllo numerico',
    'offm': 'Officina meccanica',
    'taglio': 'Taglio',
    'progtermo': 'Progettazione termoidraulica',
    'labtermo': 'Laboratorio termoidraulico',
    'labbasse': 'Laboratorio T03',
    'dis': 'Disegno',
    'tm': 'Tecnologia meccanica',
    'conf': 'Confezione',
    'Modello': 'Modello',
    'Mou': 'Moulage',
    'tt': 'Tecnologia tessile',
    'ttborr': 'Tecnologia tessile (T07)',
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
    'servtecT28': 'Servizi tecnici (T28)',
    'ELIA LES': 'ELIA',  # Special project
    'fig': 'Fisica',
    'radid': 'Recupero didattico',
}


def parse_date(date_str: str, year: int = 2025) -> Optional[date]:
    """Parse Italian date format like '8 set' or '01 ott'."""
    date_str = date_str.strip()
    if not date_str:
        return None

    match = re.match(r'(\d{1,2})\s+([a-z]{3})', date_str.lower())
    if not match:
        return None

    day = int(match.group(1))
    month_abbr = match.group(2)

    if month_abbr not in MONTH_MAP:
        print(f"WARNING: Unknown month abbreviation: {month_abbr}", file=sys.stderr)
        return None

    month = MONTH_MAP[month_abbr]

    # Handle year transition (Sept-Dec is 2025, Jan-June is 2026)
    if month >= 9:
        year = 2025
    else:
        year = 2026

    try:
        return date(year, month, day)
    except ValueError as e:
        print(f"WARNING: Invalid date {date_str}: {e}", file=sys.stderr)
        return None


def parse_time_slot(slot_str: str) -> Optional[int]:
    """Convert time slot like '8-9' to 0-based slot index."""
    slot_str = slot_str.strip()
    if not slot_str:
        return None

    match = re.match(r'(\d+)-(\d+)', slot_str)
    if not match:
        return None

    start_hour = int(match.group(1))
    # Map 8-9 -> 0, 9-10 -> 1, 10-11 -> 2, 11-12 -> 3, 12-13 -> 4, 13-14 -> 5
    if 8 <= start_hour <= 13:
        return start_hour - 8
    return None


def parse_teacher(teacher_str: str) -> tuple[Optional[str], Optional[str]]:
    """
    Parse teacher field, handling co-teaching and special patterns.
    Returns (primary_teacher, doppio_teacher).
    Returns (None, None) for off-campus entries that should be skipped.
    """
    if not teacher_str or not teacher_str.strip():
        return None, None

    teacher_str = teacher_str.strip()

    # Skip off-campus entries (these are NOT lessons)
    if teacher_str in OFF_CAMPUS_TEACHERS:
        return None, None

    # Handle "est + Basse" pattern (external + T03)
    if 'est + Basse' in teacher_str:
        return 'T03', None

    # Handle comma-separated teachers (co-teaching)
    if ',' in teacher_str:
        parts = [p.strip() for p in teacher_str.split(',')]
        primary = None
        doppio = None
        for part in parts:
            normalized = TEACHER_MAP.get(part, part)
            if normalized:
                if primary is None:
                    primary = normalized
                else:
                    doppio = normalized
                    break
        return primary, doppio

    # Single teacher
    normalized = TEACHER_MAP.get(teacher_str, teacher_str)
    return normalized, None


def parse_subject(subject_str: str) -> Optional[str]:
    """Normalize subject abbreviation to full name."""
    if not subject_str or not subject_str.strip():
        return None

    subject_str = subject_str.strip()
    return SUBJECT_MAP.get(subject_str, subject_str)


def parse_room(room_str: str) -> Optional[str]:
    """Parse room field (often empty in CSV)."""
    if not room_str or not room_str.strip():
        return None
    return room_str.strip() or None


def is_summary_row(row: list[str]) -> bool:
    """Check if this is a summary row (hour counts, not lessons)."""
    for val in row[-20:]:
        if val.strip() and val.strip().isdigit():
            return True
    return False


def parse_csv(filepath: str) -> list[dict]:
    """Parse the spalmone CSV and return list of lesson records."""
    lessons = []
    line_num = 0

    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)

        for row in reader:
            line_num += 1

            # Skip header rows
            if line_num <= 2:
                continue

            # Skip empty rows
            if not any(cell.strip() for cell in row):
                continue

            # Skip summary rows
            if is_summary_row(row):
                continue

            # Ensure row has enough columns
            if len(row) < 4:
                continue

            # Parse date and time slot
            date_str = row[1].strip()
            day_of_week = row[2].strip()
            slot_str = row[3].strip()

            # Skip weekends
            if day_of_week in ('Sabato', 'Domenica'):
                continue

            parsed_date = parse_date(date_str)
            if not parsed_date:
                continue

            slot_index = parse_time_slot(slot_str)
            if slot_index is None:
                continue

            # Process each student group
            for col_idx, group_name in GROUP_COLUMNS.items():
                if col_idx + 2 >= len(row):
                    continue

                subject_str = row[col_idx].strip() if col_idx < len(row) else ''
                teacher_str = row[col_idx + 1].strip() if col_idx + 1 < len(row) else ''
                room_str = row[col_idx + 2].strip() if col_idx + 2 < len(row) else ''

                # Skip empty lessons
                if not subject_str and not teacher_str:
                    continue

                # Skip off-campus entries silently (these are NOT lessons)
                if teacher_str in OFF_CAMPUS_TEACHERS:
                    continue

                subject = parse_subject(subject_str)
                primary_teacher, doppio_teacher = parse_teacher(teacher_str)
                room = parse_room(room_str)

                if not primary_teacher:
                    print(f"WARNING: Could not parse teacher '{teacher_str}' "
                          f"at line {line_num}, group {group_name}", file=sys.stderr)
                    continue

                lesson = {
                    'date': parsed_date.isoformat(),
                    'slot_index': slot_index,
                    'day_of_week': day_of_week,
                    'student_group': group_name,
                    'subject': subject or subject_str,
                    'teacher': primary_teacher,
                    'doppio_teacher': doppio_teacher,
                    'room': room,
                    'source_line': line_num,
                    'raw_subject': subject_str,
                    'raw_teacher': teacher_str,
                }
                lessons.append(lesson)

    return lessons


def main():
    parser = argparse.ArgumentParser(
        description='Parse spalmone CSV into structured JSON')
    parser.add_argument('csv_path', help='Path to the spalmone CSV file')
    parser.add_argument('--output', '-o', default=None,
                        help='Output file path (default: stdout)')
    args = parser.parse_args()

    lessons = parse_csv(args.csv_path)

    output = {
        'total_lessons': len(lessons),
        'lessons': lessons,
        'unique_groups': sorted(set(l['student_group'] for l in lessons)),
        'unique_teachers': sorted(set(l['teacher'] for l in lessons if l['teacher'])),
        'unique_subjects': sorted(set(l['subject'] for l in lessons if l['subject'])),
    }

    json_str = json.dumps(output, indent=2, ensure_ascii=False)

    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(json_str)
        print(f"Parsed {len(lessons)} lessons -> {args.output}", file=sys.stderr)
    else:
        print(json_str)


if __name__ == '__main__':
    main()
