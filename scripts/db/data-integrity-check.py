#!/usr/bin/env python3
"""
Data Integrity Check for Planner Schedule

Checks for:
1. Teachers teaching subjects outside their specialty
2. External teachers assigned on wrong days
3. Groups assigned subjects they shouldn't have
4. Inconsistent course data
"""

import sys
import os
import subprocess


def run_query(sql):
    """Run SQL query via docker/podman"""
    cmd = [
        'podman', 'exec', 'planner-db', 'psql',
        '-U', 'planner', '-d', 'planner_dev',
        '-t', '-A', '-F', '.',
        '-c', sql
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    return result.stdout


def main():
    print("=" * 80)
    print("DATA INTEGRITY CHECK")
    print("=" * 80)

    issues = []

    # Check 1: Teachers teaching 3+ different subjects
    print("\n1. Checking teachers with 3+ different subjects...")
    output = run_query("""
        SELECT t.name as teacher_name, COUNT(DISTINCT c.subject) as subject_count,
               STRING_AGG(DISTINCT c.subject, ', ' ORDER BY c.subject) as subjects
        FROM course c
        JOIN teacher t ON c.teacher_id = t.id
        GROUP BY t.name
        HAVING COUNT(DISTINCT c.subject) >= 3
        ORDER BY subject_count DESC;
    """)
    print(output)
    if 'T28' in output or 'T02' in output:
        lines = [l for l in output.split('\n') if l.strip() and not l.startswith('-')]
        if len(lines) > 1:
            issues.append(("Teachers with 3+ subjects", output))

    # Check 2: T28's courses specifically
    print("\n2. Checking T28's course assignments...")
    output = run_query("""
        SELECT c.id, c.subject, t.name as teacher_name, g.name as group_name, c.totalhours
        FROM course c
        JOIN teacher t ON c.teacher_id = t.id
        JOIN studentgroup g ON c.studentgroup_id = g.id
        WHERE t.name = 'T28'
        ORDER BY c.subject, g.name;
    """)
    print(output)

    # Check 3: T28 assigned to wrong subject (History)
    if 'Storia' in output or 'storia' in output.lower():
        issues.append(("T28 teaching History (should only teach Servizio tecnico)", output))

    # Check 4: FD groups with non-acconciatura subjects
    print("\n3. Checking FD groups with non-acconciatura subjects...")
    output = run_query("""
        SELECT c.id, c.subject, t.name as teacher_name, g.name as group_name, c.totalhours
        FROM course c
        JOIN studentgroup g ON c.studentgroup_id = g.id
        JOIN teacher t ON c.teacher_id = t.id
        WHERE g.name = 'G12'
          AND c.subject NOT LIKE '%Servizio tecnico%'
          AND c.subject NOT LIKE '%Consulenza d''immagine%'
          AND c.subject NOT LIKE '%Contabilità%'
          AND c.subject NOT LIKE '%Relazione con il cliente%'
          AND c.subject NOT LIKE '%Cosmetologia%'
          AND c.subject NOT LIKE '%Analisi del capello%'
          AND c.subject NOT LIKE '%Lavaggio%'
          AND c.subject NOT LIKE '%T27%'
        ORDER BY c.subject;
    """)
    print(output)
    if 'Storia' in output or 'storia' in output.lower():
        issues.append(("FD with non-acconciatura subject (Storia)", output))

    # Check 5: FA groups with non-acconciatura subjects
    print("\n4. Checking FA groups with non-acconciatura subjects...")
    output = run_query("""
        SELECT c.id, c.subject, t.name as teacher_name, g.name as group_name, c.totalhours
        FROM course c
        JOIN studentgroup g ON c.studentgroup_id = g.id
        JOIN teacher t ON c.teacher_id = t.id
        WHERE g.name IN ('G02', 'G06', 'G10')
          AND c.subject NOT LIKE '%Servizio tecnico%'
          AND c.subject NOT LIKE '%Consulenza d''immagine%'
          AND c.subject NOT LIKE '%Contabilità%'
          AND c.subject NOT LIKE '%Relazione con il cliente%'
          AND c.subject NOT LIKE '%Cosmetologia%'
          AND c.subject NOT LIKE '%Analisi del capello%'
          AND c.subject NOT LIKE '%Lavaggio%'
          AND c.subject NOT LIKE '%T27%'
        ORDER BY c.subject;
    """)
    print(output)
    if 'Storia' in output or 'storia' in output.lower():
        issues.append(("FA with non-acconciatura subject (Storia)", output))

    # Check 6: Duplicate teacher+group+subject combinations
    print("\n5. Checking for duplicate course assignments...")
    output = run_query("""
        SELECT t.name as teacher_name, c.subject, g.name as group_name,
               COUNT(*) as course_count,
               STRING_AGG(c.id::text, ', ' ORDER BY c.id) as course_ids
        FROM course c
        JOIN teacher t ON c.teacher_id = t.id
        JOIN studentgroup g ON c.studentgroup_id = g.id
        GROUP BY t.name, c.subject, g.name
        HAVING COUNT(*) > 1
        ORDER BY course_count DESC;
    """)
    print(output)
    if 'course_count' in output and '2' in output:
        issues.append(("Duplicate course assignments", output))

    # Check 7: MODA groups without fashion subjects
    print("\n6. Checking MODA groups without proper fashion subjects...")
    output = run_query("""
        SELECT c.id, c.subject, t.name as teacher_name, g.name as group_name, c.totalhours
        FROM course c
        JOIN studentgroup g ON c.studentgroup_id = g.id
        JOIN teacher t ON c.teacher_id = t.id
        WHERE g.name IN ('G01', 'G05', 'G09')
          AND c.subject NOT LIKE '%Storia della moda%'
          AND c.subject NOT LIKE '%Merceologia%'
          AND c.subject NOT LIKE '%Figurino%'
          AND c.subject NOT LIKE '%Modello%'
          AND c.subject NOT LIKE '%Moulage%'
          AND c.subject NOT LIKE '%Confezione%'
          AND c.subject NOT LIKE '%Tecnologia tessile%'
        ORDER BY c.subject;
    """)
    print(output)
    lines = [l for l in output.split('\n') if l.strip() and not l.startswith('-') and not l.startswith('id')]
    if len(lines) > 1:
        issues.append(("MODA with non-fashion subjects", output))

    # Check 8: FB groups without technical drawing subjects
    print("\n7. Checking FB groups without proper technical subjects...")
    output = run_query("""
        SELECT c.id, c.subject, t.name as teacher_name, g.name as group_name, c.totalhours
        FROM course c
        JOIN studentgroup g ON c.studentgroup_id = g.id
        JOIN teacher t ON c.teacher_id = t.id
        WHERE g.name IN ('G03', 'G07', 'G11')
          AND c.subject NOT LIKE '%Disegno edile%'
          AND c.subject NOT LIKE '%Impresa simulata%'
          AND c.subject NOT LIKE '%Progettazione termoidraulica%'
          AND c.subject NOT LIKE '%Laboratorio termoidraulico%'
        ORDER BY c.subject;
    """)
    print(output)
    lines = [l for l in output.split('\n') if l.strip() and not l.startswith('-') and not l.startswith('id')]
    if len(lines) > 1:
        issues.append(("FB with non-technical subjects", output))

    # Summary
    print("\n" + "=" * 80)
    print(f"SUMMARY: Found {len(issues)} potential data integrity issues")
    print("=" * 80)

    if issues:
        print("\n⚠️  ISSUES FOUND:")
        for i, (desc, _) in enumerate(issues, 1):
            print(f"{i}. {desc}")
        print("\n⚠️  ACTION REQUIRED:")
        print("Consider resetting the database with: ./planner.sh delete dev && ./planner.sh start dev")
        return 1
    else:
        print("\n✓ No obvious data integrity issues found")
        return 0


if __name__ == '__main__':
    sys.exit(main())
