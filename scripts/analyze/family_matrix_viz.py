#!/usr/bin/env python3
"""Family migration matrix: runs x hard families, the 628 insight made visual.

The 628 bench discovered that residual FAMILIES MIGRATE run-to-run (same
construction, different LS race: -1..-12 spread). The verdict table carries
the data; this renders it as the matrix the eye can scan: which families
recur (persistent classes to cure at the root) vs which are one-off (race
noise). maintainer 21/09: the frequency question is answered by this shape.

Usage: family_matrix_viz.py --data series-families.json [--out out.html]
JSON shape: {"runs": [{"name": "628-r1", "families": {"H32": 2, "H34": 1, "H7": 1}}, ...]}
"""
import argparse
import html
import json
from pathlib import Path

FAMILY_COLORS = {
    "H0": "#8b0000", "H7": "#c0392b", "H17": "#d35400", "H28": "#7d3c98",
    "H32": "#2471a3", "H33": "#148f77", "H34": "#117864",
    "H29": "#5d6d7e", "H25": "#a04000",
}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", default="family-matrix.html")
    args = ap.parse_args()

    runs = json.loads(Path(args.data).read_text())["runs"]
    families = []
    for r in runs:
        for f in r["families"]:
            if f not in families:
                families.append(f)
    families.sort()

    header = "".join(
        f"<th style='font-size:11px;color:{FAMILY_COLORS.get(f, '#333')}'>{f}</th>"
        for f in families)
    rows = []
    for r in runs:
        cells = []
        total = 0
        for f in families:
            n = r["families"].get(f, 0)
            total += n
            style = (f"background:{FAMILY_COLORS.get(f, '#ddd')};color:#fff;"
                     if n else "color:#ccc")
            cells.append(f"<td style='{style}text-align:center;font-size:11px'>"
                         f"{n if n else '·'}</td>")
        name = html.escape(r["name"]) + (
            " <span style='color:#999'>(delivered)</span>" if r.get("delivered") else "")
        rows.append(f"<tr><td style='font-size:11px;padding:2px 6px'>{name}</td>"
                    f"<td style='text-align:center;font-size:11px;font-weight:bold'>{r.get('hard', total)}</td>"
                    f"{''.join(cells)}</tr>")
    legend = "".join(
        f"<span style='margin-right:10px'><span style='background:"
        f"{FAMILY_COLORS.get(f, "#333")};color:#fff;padding:1px 4px;font-size:10px'>{f}</span></span>"
        for f in families)
    html_out = (f"<html><head><meta charset='utf-8'></head><body>"
                f"<h3>Family migration matrix (runs x hard families)</h3>"
                f"<table border='1' cellpadding='2' cellspacing='0'>"
                f"<tr><th>run</th><th>hard tot</th>{header}</tr>{''.join(rows)}</table>"
                f"<p style='font-size:11px'>{legend}<br>Recurring columns = persistent"
                f" classes to cure at the root; single dots = race noise (628: the"
                f" families migrate, the race decides the depth).</p></body></html>")
    Path(args.out).write_text(html_out, encoding="utf-8")
    print(f"OK wrote {args.out} ({len(runs)} runs x {len(families)} families)")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
