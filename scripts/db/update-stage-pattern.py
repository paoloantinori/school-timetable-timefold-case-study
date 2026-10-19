#!/usr/bin/env python3
"""Update stage pattern day of week mask for a student group."""

import subprocess
import sys

def run_psql(sql):
    """Run SQL via psql in the planner-pg container."""
    cmd = [
        "podman", "exec", "planner-pg",
        "psql", "-U", "planner", "-d", "planner",
        "-c", sql
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error running SQL: {result.stderr}", file=sys.stderr)
        sys.exit(1)
    return result.stdout

def main():
    # Update G11 (stagepattern_id = 2) from [Tue, Wed, Thu] to [Mon, Tue]
    stagepattern_id = 2

    print(f"Updating stage pattern {stagepattern_id}...")

    # Delete existing days
    delete_sql = f"DELETE FROM stagepattern_dayofweekmask WHERE stagepattern_id = {stagepattern_id};"
    run_psql(delete_sql)
    print(f"Deleted existing day entries")

    # Insert Monday (day=1) and Tuesday (day=2)
    insert_sql = f"""
        INSERT INTO stagepattern_dayofweekmask (stagepattern_id, dayofweekmask)
        VALUES ({stagepattern_id}, 1), ({stagepattern_id}, 2);
    """
    run_psql(insert_sql)
    print("Added Monday (day=1) and Tuesday (day=2)")

    # Verify
    verify_sql = f"SELECT * FROM stagepattern_dayofweekmask WHERE stagepattern_id = {stagepattern_id} ORDER BY dayofweekmask;"
    print("\nStage Pattern Verification:")
    print(run_psql(verify_sql))

    # Fix ProfessionalizzantiDay dayOfWeek from 0 (ordinal) to 1 (MONDAY value)
    print("\nFixing ProfessionalizzantiDay dayOfWeek (0 -> 1 = Monday)...")
    run_psql("UPDATE professionalizzantiday SET dayofweek = 1 WHERE studentgroup_id = (SELECT id FROM studentgroup WHERE name = 'G11');")
    print("Fixed dayOfWeek: 0 -> 1 (Monday)")

    # Verify
    prof_verify = run_psql("SELECT * FROM professionalizzantiday WHERE studentgroup_id = (SELECT id FROM studentgroup WHERE name = 'G11');")
    print("\nProfessionalizzantiDay Verification:")
    print(prof_verify)

    print("\nUpdate successful! G11 now has:")
    print("  - Stage pattern: [Monday, Tuesday]")
    print("  - ProfessionalizzantiDay: Monday")

if __name__ == "__main__":
    main()
