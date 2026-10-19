#!/usr/bin/env python3
"""score-stream-separation.py - hard-score trajectory analysis: when do bad runs leave the good band?

Reads the per-run score streams (app logs preserved as score-stream.txt by the census/
multistart drivers, commit a1f8728b+) of a set of runs, samples each solver's HARD score
timeline, and answers: at which point in the staged cycle does each run's hard score stop
looking like a good run's?

Background (task452-results dossier, 2026-08-15): the hypothesis from the Fedora T-minus
analysis is that bad constructions are already distinguishable at the post-RELEASE
boundary (entry to the room phase), where good runs sit at exactly the room-null baseline
(-9404 on the 2026/27 seed: every lesson placed, no rooms yet), while 5/6 bad signatures
are already more negative. This script makes that check repeatable on any set of streams,
and flags where the data disagrees with the hypothesis.

What a stream contains (per staged solve): the staged-solve boundary lines
("stage A (construction only) finished", "stage B-TIMESLOT finished", "stage B-ROOM
finished", "staged multi-start attempt N done"), each with an explicit score tuple, plus
the Timefold per-step best-score lines in between. Multi-start OFF streams (census mode)
contain one attempt only.

Usage:
  ./scripts/analyze/score-stream-separation.py <dir-with-run-subdirs> [more dirs...]
    Each subdir must contain score-stream.txt (or app.log). The good reference defaults
    to the first run whose final hard is 0 (override with --good <run-name>).

Output (stdout): per-run first-divergence table (which stage boundary the run's hard
first leaves the good reference's band) and the final hard. Exit 0 always (analysis
tool); interpret the table, don't parse the exit code.
"""
import argparse
import os
import re
import sys

# Log line shapes we parse (Quarkus timestamps + the staged-solve boundary lines):
#   staged solve: stage B-TIMESLOT finished in 565s - score=-1842hard/-865478medium/22031soft
BOUNDARY_RE = re.compile(
    r"staged solve: stage ([A-Za-z0-9-]+(?: \([^)]*\))?) finished in (\d+)s - score=(-?\d+)hard/(-?\d+)medium")
ATTEMPT_RE = re.compile(r"staged multi-start attempt (\d+) done:.*?score=(-?\d+)hard/(-?\d+)medium")

ROOM_NULL_BASELINE = -9404   # 2026/27 seed: every lesson placed, no rooms yet (T-minus datum)


def parse_stream(path):
    """Return (boundaries, final): boundaries = [(name, secs, hard)] in stream order,
    final = the last hard seen (attempt-done line wins over boundary lines)."""
    boundaries, final = [], None
    with open(path, errors="replace") as f:
        for line in f:
            m = BOUNDARY_RE.search(line)
            if m:
                boundaries.append((m.group(1), int(m.group(2)), int(m.group(3))))
                final = int(m.group(3))
                continue
            m = ATTEMPT_RE.search(line)
            if m:
                final = int(m.group(2))
    return boundaries, final


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dirs", nargs="+", help="run directories (each with score-stream.txt or app.log)")
    ap.add_argument("--good", help="run name treated as the good reference (default: first run ending 0hard)")
    ap.add_argument("--tolerance", type=int, default=1,
                    help="hard delta from the good reference counted as divergence beyond this (default 1)")
    args = ap.parse_args()

    runs = {}
    for d in args.dirs:
        name = os.path.basename(os.path.normpath(d))
        for cand in ("score-stream.txt", "app.log"):
            p = os.path.join(d, cand)
            if os.path.isfile(p):
                runs[name] = parse_stream(p)
                break
    if not runs:
        sys.exit("no score streams found (looked for score-stream.txt / app.log in each dir)")

    good = args.good
    if good is None:
        for name, (_, final) in runs.items():
            if final == 0:
                good = name
                break
    if good is None or good not in runs:
        sys.exit("no good run found/given (pass --good): have " + ", ".join(runs))

    gb, gf = runs[good]

    print(f"good reference: {good} (final hard {gf or 0})")
    print(f"room-null baseline (2026/27 seed): {ROOM_NULL_BASELINE}hard")
    print()
    hdr = f"{'run':22s} {'final hard':>10}  first divergence from good (boundary, delta)  [class]"
    print(hdr)
    print("-" * len(hdr))
    for name, (b, final) in sorted(runs.items()):
        # Compare POSITIONALLY: the good stream's i-th boundary vs this run's i-th.
        # Boundary NAMES repeat across multi-start attempts (each attempt logs A,
        # B-TIMESLOT, B-ROOM again), so name-keying would compare attempt 1 of one run
        # against attempt 2 of the good run. Census-mode streams (multi-start OFF) have
        # one attempt = one triple; a multi-start stream aligns only while both runs
        # keep attempting (a run that stops at attempt 1 vs one that retries diverges
        # "never" past the shorter prefix: reported honestly below).
        first = None
        for i, (bname, _, hard) in enumerate(b):
            if i >= len(gb):
                break
            gname, _, ghard = gb[i]
            if abs(hard - ghard) > args.tolerance:
                first = (f"{bname}[{i}]", hard - ghard)
                break
        tag = "GOOD-ref" if name == good else ("good" if final == 0 else "BAD")
        div = f"{first[0]} ({first[1]:+d})" if first else "never (all boundaries in band)"
        print(f"{name:22s} {final or 0:>10}  {div:42s}  [{tag}]")



    # RELEASE-boundary view (the T-minus check): the LAST B-TIMESLOT boundary before
    # B-ROOM is where a doomed attempt is already visible (its timeslot-tier signature
    # is in; rooms cannot fix it). Separating there is the skip-B-ROOM gate (TASK-498).
    print()
    print("RELEASE-boundary hard (last B-TIMESLOT before B-ROOM) and final hard:")
    rel = {}
    for name, (b, final) in sorted(runs.items()):
        bt = [h for n, _, h in b if n.startswith("B-TIMESLOT")]
        if bt:
            rel[name] = bt[-1]
            print(f"  {name:22s} RELEASE {bt[-1]:>6}  final {final or 0:>6}")
    if rel:
        good_vals = [v for n, v in rel.items() if runs[n][1] == 0]
        bad_vals = [v for n, v in rel.items() if runs[n][1] != 0]
        if good_vals and bad_vals:
            # Hard scores are NEGATIVE: a run is "more doomed" the SMALLER (more
            # negative) its hard. Separated = every good value is strictly greater
            # (less negative) than every bad value.
            sep = min(good_vals) > max(bad_vals)
            print(f"  good RELEASE range [{min(good_vals)}, {max(good_vals)}], "
                  f"bad [{min(bad_vals)}, {max(bad_vals)}]: "
                  + ("SEPARATED (tight margin " + str(min(good_vals) - max(bad_vals))
                     + " = worst good vs best bad)" if sep
                     else "OVERLAPPING (fixed threshold unsafe - use the band, not a point)"))

    print()
    print("notes: boundaries absent from the good stream are skipped; a BAD run diverging")
    print("only at B-ROOM is room-tier (like the H29 seed-6 signature); boundary names here")
    print("are coarse (stage granularity). Per-step dense timelines: grep the streams with")
    print("'best score' and plot; this table answers WHO diverges WHERE at stage level.")


if __name__ == "__main__":
    main()