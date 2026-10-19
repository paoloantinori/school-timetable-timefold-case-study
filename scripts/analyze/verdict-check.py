#!/usr/bin/env python3
"""verdict-check.py: mechanically applies pre-registered decision criteria to measured numbers.

WHY (precedent 29/08): the v2.1 prebooking gate delivered -12hard (BETTER than the
adoption band -13..-19), yet the verdict said "NON candidata adozione per criterio
pre-registrato". The agent misread its own criteria. This script removes interpretation
from the loop: you give it the bands and the numbers, it tells you the band and the action.

USAGE:
    # Inline (for quick checks):
    python3 scripts/analyze/verdict-check.py --tier -12 --h17-gaps 0 --h34-cells 6 \\
        --criteria-json '{"bands": [{"name": "non-adoptable", "when": "tier <= -20", "action": "no"},
        {"name": "adoption-candidate", "when": "tier >= -19 AND tier <= -13 AND h32_cells <= 4", "action": "ask A/B n>=2"},
        {"name": "gray", "when": "h34_cells > 4", "action": "composition arbitration"}]}'

    # File-based (for gate discipline: write criteria BEFORE the run, apply AFTER):
    python3 scripts/analyze/verdict-check.py --criteria-file criteri-v21.yaml --measured-file misura.yaml

    # From capture (reads analysis.json recount, score.txt as tier-only fallback):
    python3 scripts/analyze/verdict-check.py --criteria-file criteri.yaml --capture-dir task564-results/gate-v21/

RULES:
    - First matching band wins (bands are checked in order).
    - "tier" is the total hard score (negative = violations).
    - Variables must be numeric; unknown variables in conditions cause a LOUD error.
    - The script NEVER interprets: it evaluates the condition and reports the band.

EXIT CODES:
    0 = verdict produced (check stdout for the band)
    1 = no band matched (numbers outside all bands: ambiguous, needs human)
    2 = error (bad criteria, missing values, unparseable)
"""

import argparse
import json
import os
import re
import sys


def eval_condition(cond, variables):
    """Safely evaluates a simple condition like 'tier <= -20' or 'tier >= -19 AND h32_cells <= 4'.
    Only supports: variable names, numbers, comparison operators (<=, >=, <, >, ==, !=), AND/OR.
    """
    # Replace variable names with their values
    expr = cond
    for var, val in variables.items():
        expr = re.sub(r'\b' + re.escape(var) + r'\b', str(val), expr)
    # Check for any remaining identifiers (unknown variables). Case-INSENSITIVE set:
    # a capitalized Python literal (True/False/None) is as unknown as a typo'd
    # variable, and letting it through would let a condition force its band
    # ('tier >= -19 OR True' must die loud, not match).
    leftover = re.findall(r'\b[A-Za-z_][A-Za-z0-9_]*\b', expr.replace('AND', '').replace('OR', ''))
    if leftover:
        print(f"ERROR: unknown variables in condition: {leftover}", file=sys.stderr)
        print(f"  condition: {cond}", file=sys.stderr)
        print(f"  after substitution: {expr}", file=sys.stderr)
        return None
    # Evaluate
    expr = expr.replace('AND', ' and ').replace('OR', ' or ')
    try:
        return bool(eval(expr))
    except Exception as e:
        print(f"ERROR: cannot evaluate condition '{cond}' -> '{expr}': {e}", file=sys.stderr)
        return None


def read_capture_dir(capture_dir, constraint_vars=None):
    """Reads measured values from a canonical capture directory.

    Two sources, in priority order:
    - analysis.json (the DB recount the campaign verdicts use; its medium is the
      authoritative one, score.txt's medium is the known-divergent in-memory
      latestScore, TASK-509)
    - score.txt (fallback for the TIER ONLY; written by api.sh 'score', parsed with
      shared.parse_score; a non-match warns on stderr so the failure points here,
      not at a later 'unknown variable: tier')

    Constraint rows use the drill-down contract (ScheduleResource.getScoreAnalysis):
    key 'constraint' holding the canonical id string, 'matches' an int count. The id
    is matched on its code part (before ' - ') with EQUALITY, so a dot-notation
    sibling (H32.1-style) can never over-match. A constraint ABSENT from the
    drill-down has zero matches (the endpoint skips zero-score constraints), so the
    mapped variables default to 0 whenever a constraints list was read.

    constraint_vars maps variable name -> constraint code (e.g. {'h17_gaps': 'H17'}),
    defaulting to DEFAULT_CONSTRAINT_VARS; the criteria file can declare its own
    'variables:' mapping so a new gate never edits this script.
    """
    if constraint_vars is None:
        constraint_vars = DEFAULT_CONSTRAINT_VARS
    values = {}
    analysis_path = os.path.join(capture_dir, 'analysis.json')
    analysis = None
    if os.path.isfile(analysis_path):
        with open(analysis_path) as f:
            try:
                analysis = json.load(f)
            except ValueError:
                analysis = None
    if analysis is not None and not isinstance(analysis, dict):
        print(f"WARNING: analysis.json is not a JSON object ({type(analysis).__name__}); ignoring it", file=sys.stderr)
        analysis = None
    if analysis is not None:
        # top-level recount totals (capture-run.sh drill-down format)
        for src, dst in (('hard', 'tier'), ('medium', 'medium'), ('soft', 'soft')):
            v = analysis.get(src)
            if isinstance(v, int):
                values[dst] = v
        rows = analysis.get('constraints')
        if rows is None or not isinstance(rows, list):
            if 'constraints' in analysis:
                print(f"WARNING: analysis.json 'constraints' is not a list ({type(rows).__name__}); treating as absent", file=sys.stderr)
        else:
            for c in rows:
                if not isinstance(c, dict):
                    continue
                code = str(c.get('constraint', '')).split(' - ')[0].strip()
                for var, prefix in constraint_vars.items():
                    if code == prefix:
                        values[var] = c.get('matches', 0)
            # A constraint absent from the drill-down has zero matches (the endpoint
            # skips zero-score constraints), so mapped variables default to 0. The
            # NOTE keeps that from being silent: a typo'd mapping code or a capture
            # from a build without the constraint reads identically, and the note
            # is the only thing that distinguishes them from a real zero.
            defaulted = [var for var in constraint_vars if var not in values]
            for var in defaulted:
                values[var] = 0
            if defaulted:
                print(f"NOTE: {sorted(defaulted)} default to 0 (constraint row absent from the "
                      f"drill-down); if that is unexpected, check the variables mapping codes "
                      f"against this build's analysis.json", file=sys.stderr)
    if 'tier' not in values:
        score_path = os.path.join(capture_dir, 'score.txt')
        parsed = None
        if os.path.isfile(score_path):
            sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
            from shared import parse_score
            with open(score_path) as f:
                parsed = parse_score(f.read())
        if parsed is None:
            print(f"WARNING: no tier in analysis.json and score.txt unusable in {capture_dir}; "
                  "a broken capture should fail loud, not fall back to a divergent medium",
                  file=sys.stderr)
        elif parsed == (0, 0, 0):
            # api.sh writes '0hard/0medium/0soft' as its no-score placeholder; a real
            # solve never has medium 0. In a capture already degraded enough to use the
            # fallback, reading the sentinel as tier=0 would hand the best band a
            # fabricated adoption-grade verdict, so it is an error, not a value.
            print("ERROR: score.txt contains the 0hard/0medium/0soft placeholder (no real "
                  "score in this capture); refusing to certify from it", file=sys.stderr)
            sys.exit(2)
        else:
            print("WARNING: no usable tier in analysis.json (absent, unparseable, or non-int "
                  "totals: shape drift?), falling back to score.txt for the TIER only "
                  "(medium/soft left unset: score.txt medium is the in-memory latestScore, "
                  "TASK-509)", file=sys.stderr)
            values['tier'] = parsed[0]
            # stdout, not stderr: logs that capture only stdout+exit code must be able
            # to tell a full verdict from a degraded one
            print("SOURCE: score.txt fallback (tier only)")
    return values


# Which capture variables this tool can extract, by constraint code. The criteria
# file may declare its own 'variables:' mapping (name -> code); this is the default
# when it does not, so existing criteria files keep working.
DEFAULT_CONSTRAINT_VARS = {
    'h17_gaps': 'H17',
    'h32_cells': 'H32',
    'h34_cells': 'H34',
}


def main():
    parser = argparse.ArgumentParser(description='Mechanically apply pre-registered criteria')
    parser.add_argument('--criteria-file', help='YAML file with bands')
    parser.add_argument('--criteria-json', help='Inline JSON with bands')
    parser.add_argument('--measured-file', help='YAML file with measured values')
    parser.add_argument('--capture-dir', help='Capture directory (reads score.txt + analysis.json)')
    # Direct value overrides
    parser.add_argument('--tier', type=int, help='Total hard score')
    parser.add_argument('--medium', type=int, help='Medium score')
    parser.add_argument('--h17-gaps', type=int, dest='h17_gaps')
    parser.add_argument('--h32-cells', type=int, dest='h32_cells')
    parser.add_argument('--h34-cells', type=int, dest='h34_cells')
    parser.add_argument('--set', action='append', default=[], metavar='NAME=VALUE',
                        help='Override any measured variable, including ones declared in the '
                             'criteria variables mapping (e.g. --set h29_rooms=3); ints are parsed')
    args = parser.parse_args()

    # Load criteria FIRST: its 'variables:' mapping (name -> constraint code) drives
    # what the capture reader extracts, so a gate watching new constraints declares
    # them in data instead of editing this script. Any unreadable criteria/measure
    # source is exit 2 (error), never a traceback exit 1 that a wrapper would read
    # as 'ambiguous'.
    if args.criteria_file:
        try:
            import yaml
        except ImportError:
            print("ERROR: yaml module needed for --criteria-file", file=sys.stderr)
            sys.exit(2)
        try:
            with open(args.criteria_file) as f:
                criteria = yaml.safe_load(f)
        except Exception as e:
            print(f"ERROR: cannot read criteria file {args.criteria_file}: {e}", file=sys.stderr)
            sys.exit(2)
    elif args.criteria_json:
        try:
            criteria = json.loads(args.criteria_json)
        except ValueError as e:
            print(f"ERROR: invalid --criteria-json: {e}", file=sys.stderr)
            sys.exit(2)
    else:
        print("ERROR: need --criteria-file or --criteria-json", file=sys.stderr)
        sys.exit(2)
    if not isinstance(criteria, dict):
        criteria = {}
    # Partial declarations EXTEND the defaults: a gate adding h29_rooms must not have
    # to re-declare h17_gaps/h32_cells/h34_cells for the shared bands to keep working.
    constraint_vars = dict(DEFAULT_CONSTRAINT_VARS)
    constraint_vars.update(criteria.get('variables') or {})

    # Collect measured values
    variables = {}
    if args.capture_dir:
        variables.update(read_capture_dir(args.capture_dir, constraint_vars))
    if args.measured_file:
        try:
            import yaml
        except ImportError:
            print("ERROR: yaml module needed for --measured-file (pip install pyyaml)", file=sys.stderr)
            sys.exit(2)
        try:
            with open(args.measured_file) as f:
                measured = yaml.safe_load(f)
        except Exception as e:
            print(f"ERROR: cannot read measured file {args.measured_file}: {e}", file=sys.stderr)
            sys.exit(2)
        if isinstance(measured, dict):
            variables.update(measured)
    for key in ('tier', 'medium', 'h17_gaps', 'h32_cells', 'h34_cells'):
        if getattr(args, key) is not None:
            variables[key] = getattr(args, key)
    # Generic override: the named flags above cover the default variables, --set
    # covers any variable a criteria file declares, so new gates stay data-driven.
    for assignment in args.set:
        if '=' not in assignment:
            print(f"ERROR: --set expects NAME=VALUE, got '{assignment}'", file=sys.stderr)
            sys.exit(2)
        name, _, raw = assignment.partition('=')
        name, raw = name.strip(), raw.strip()
        variables[name] = int(raw) if re.fullmatch(r'-?\d+', raw) else raw

    bands = criteria.get('bands', [])
    if not bands:
        print("ERROR: no bands in criteria", file=sys.stderr)
        sys.exit(2)

    # Apply criteria mechanically
    print(f"Measured: {json.dumps(variables, indent=2)}")
    print(f"Bands ({len(bands)}):")
    for i, band in enumerate(bands):
        cond = band.get('when', '')
        result = eval_condition(cond, variables)
        if result is None:
            sys.exit(2)
        status = "MATCH" if result else "no"
        print(f"  [{i}] {band['name']:25s} when: {cond:50s} -> {status}")
        if result:
            print(f"\nVERDICT: {band['name']}")
            print(f"ACTION:  {band.get('action', 'unspecified')}")
            sys.exit(0)

    print("\nVERDICT: NO BAND MATCHED (ambiguous)")
    print("ACTION:  needs human decision")
    sys.exit(1)


if __name__ == '__main__':
    main()
