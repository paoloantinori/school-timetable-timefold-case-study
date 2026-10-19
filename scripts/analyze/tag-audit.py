#!/usr/bin/env python3
"""
Tag-to-course matching audit for the school planner.

Parses DataGenerator.java to:
  1. Extract all course subject names
  2. Apply the startsWith() tag matching logic (same as getRoomTags)
  3. Report which courses match which tags
  4. Flag potential false positives (subjects that accidentally match a prefix)

Usage:
    python3 scripts/analyze/tag-audit.py
    python3 scripts/analyze/tag-audit.py path/to/DataGenerator.java
"""

import re
import sys
import os

DEFAULT_GENERATOR = "src/main/java/com/school/planner/bootstrap/DataGenerator.java"


def find_generator(path=None):
    """Find DataGenerator.java from explicit path or auto-detect."""
    if path and os.path.isfile(path):
        return path
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(script_dir)
    candidate = os.path.join(repo_root, DEFAULT_GENERATOR)
    if os.path.isfile(candidate):
        return candidate
    candidate = os.path.join(os.getcwd(), DEFAULT_GENERATOR)
    if os.path.isfile(candidate):
        return candidate
    print(f"ERROR: Cannot find DataGenerator.java. Pass the path as argument.")
    sys.exit(1)


def parse_room_tags(content):
    """Extract the getRoomTags mapping: prefix -> tag."""
    tag_map = {}
    match = re.search(
        r'private List<String> getRoomTags\(String subject\)\s*\{(.*?)\n    \}',
        content, re.DOTALL
    )
    if not match:
        print("WARNING: Could not parse getRoomTags method")
        return tag_map

    body = match.group(1)

    for if_match in re.finditer(
        r'if\s*\((.*?)\)\s*return\s+List\.of\("([^"]*)"\)',
        body, re.DOTALL
    ):
        condition = if_match.group(1)
        tag = if_match.group(2)
        for prefix in re.findall(r'subject\.startsWith\("([^"]*)"\)', condition):
            tag_map[prefix] = tag

    return tag_map


def parse_courses(content):
    """Extract all addCourse calls: teacher, subject, group, hours."""
    pattern = r'addCourse\(teachers,\s*groups,\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*(\d+)\)'
    courses = []
    for m in re.finditer(pattern, content):
        courses.append({
            "teacher": m.group(1),
            "subject": m.group(2),
            "group": m.group(3),
            "hours": int(m.group(4)),
        })
    return courses


def get_matching_tag(subject, tag_map):
    """Apply the same startsWith() logic as DataGenerator.getRoomTags()."""
    for prefix, tag in tag_map.items():
        if subject.startswith(prefix):
            return tag, prefix
    return None, None


# Known-good matches: subjects that are expected to match their prefix.
# Subjects NOT in this list that match a prefix are flagged as potential false positives.
KNOWN_GOOD = {
    # termoidraulica
    "Laboratorio termoidraulico",
    "Laboratorio termoidraulico (A)",
    "Laboratorio termoidraulico (B)",
    # meccanica
    "Lavorazioni meccaniche d'officina",
    "Taglio metalli",
    # informatica
    "Informatica",
    "Cad",
    "Controllo numerico",
    # moda
    "Confezione",
    "Operazioni di taglio",
    "Modello",
    "Moulage",
    "Tecnologia tessile",
    "Tecnologia tessile (A)",
    "Tecnologia tessile (B)",
    # acconciatura
    "Lavaggio",
    "Servizio tecnico e stilistico (A)",
    "Servizio tecnico e stilistico (B)",
    "Servizio tecnico (consulente d'immagine)",
    "T27",
}


def run_audit(generator_path):
    with open(generator_path) as f:
        content = f.read()

    tag_map = parse_room_tags(content)
    courses = parse_courses(content)

    if not tag_map:
        print("ERROR: No tag mappings found in getRoomTags()")
        return False

    print("TAG-TO-COURSE MATCHING AUDIT")
    print("=" * 70)
    print(f"Source: {generator_path}")
    print()

    # Show the tag mapping
    print("PREFIX -> TAG MAPPING (from getRoomTags)")
    print("-" * 70)
    for prefix in sorted(tag_map.keys()):
        print(f"  \"{prefix}\" -> \"{tag_map[prefix]}\"")
    print()

    # Collect all unique subjects
    unique_subjects = sorted(set(c["subject"] for c in courses))

    # Categorize matches
    matched = {}  # tag -> list of (subject, prefix, groups)
    unmatched = []
    false_positives = []

    for subj in unique_subjects:
        tag, prefix = get_matching_tag(subj, tag_map)
        if tag:
            if tag not in matched:
                matched[tag] = []
            groups = sorted(set(c["group"] for c in courses if c["subject"] == subj))
            matched[tag].append({"subject": subj, "prefix": prefix, "groups": groups})

            # Check if this is a known-good match
            if subj not in KNOWN_GOOD:
                groups_str = ", ".join(groups)
                false_positives.append(
                    f"  \"{subj}\" matches prefix \"{prefix}\" -> tag \"{tag}\" (groups: {groups_str})"
                )
        else:
            unmatched.append(subj)

    # Report matches by tag
    print("MATCHED SUBJECTS BY TAG")
    print("-" * 70)
    for tag in sorted(matched.keys()):
        print(f"\n  Tag '{tag}':")
        for entry in matched[tag]:
            groups_str = ", ".join(entry["groups"])
            indicator = "" if entry["subject"] in KNOWN_GOOD else " *** REVIEW ***"
            print(f"    {entry['subject']:50s} (prefix: \"{entry['prefix']}\") [{groups_str}]{indicator}")

    # Report unmatched (regular classroom subjects)
    print(f"\n\nUNMATCHED SUBJECTS (regular classroom)")
    print("-" * 70)
    for subj in unmatched:
        groups = sorted(set(c["group"] for c in courses if c["subject"] == subj))
        print(f"  {subj:50s} [{', '.join(groups)}]")

    # Report false positives
    print(f"\n\nPOTENTIAL FALSE POSITIVES")
    print("-" * 70)
    if false_positives:
        print("  The following subjects match a tag prefix but are NOT in the known-good list.")
        print("  Verify these are intentionally tagged (i.e., they need a lab room):")
        print()
        for fp in false_positives:
            print(fp)
        print()
        print(f"  Total: {len(false_positives)} subject(s) to review")
    else:
        print("  No false positives detected. All matched subjects are in the known-good list.")

    print(f"\n\nSUMMARY")
    print("=" * 70)
    print(f"  Unique subjects: {len(unique_subjects)}")
    print(f"  Tagged (lab): {sum(len(v) for v in matched.values())}")
    print(f"  Untagged (regular): {len(unmatched)}")
    print(f"  Potential false positives: {len(false_positives)}")

    if false_positives:
        print("\n  RESULT: REVIEW NEEDED - potential false positives found")
        return False
    else:
        print("\n  RESULT: PASS - all tag matches are expected")
        return True


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else None
    generator = find_generator(path)
    ok = run_audit(generator)
    sys.exit(0 if ok else 1)
