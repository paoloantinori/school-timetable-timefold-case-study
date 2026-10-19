#!/usr/bin/env python3
"""Sanity stoplight: the per-group sanity gate as a visual strip.

The text table is complete but a red row hides in 14 lines of green; the
stoplight makes the non-conformity state readable in one glance (the gate
stays the TEXT one: this is the CONSUMPTION layer on the same file).

Usage: sanity_stoplight_viz.py <sanity-report.txt> [--out out.html]
"""
import html
import re
import sys
from pathlib import Path

ROW = re.compile(
    r"^\s*(.+?)\s+(\d+-\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$")


def parse(path: Path):
    groups = []
    for line in path.read_text().splitlines():
        m = ROW.match(line)
        if m:
            g, grange, runmin, under, buchi, nostanza = m.groups()
            groups.append({
                "group": g, "goldenRange": grange,
                "runMin": int(runmin), "under": int(under),
                "buchi": int(buchi), "nostanza": int(nostanza),
                "detail": [], "busta": False,
            })
            continue
        if "ROSSO" in line and groups:
            groups[-1]["detail"].append(html.escape(line.strip()))
        if "BUSTA GOLDEN" in line and groups:
            groups[-1]["busta"] = True
    return groups


def light(cond: bool, label: str):
    color = "#b94a48" if cond else "#468847"
    text = html.escape(label) if cond else ""
    return (f"<td style='background:{color};color:#fff;text-align:center;"
            f"font-size:11px;padding:2px 6px'>{text}</td>")


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "sanity-report.txt")
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "sanity-stoplight.html")
    groups = parse(path)
    if not groups:
        print(f"ERR no sanity rows parsed from {path}")
        return 1
    rows = []
    for g in groups:
        bad = g["under"] or g["buchi"] or g["nostanza"]
        detail = "<br>".join(g["detail"])
        rows.append(
            f"<tr><td style='font-size:11px;padding:2px 6px'>{html.escape(g['group'])}</td>"
            f"<td style='font-size:11px;text-align:center'>{g['runMin']}</td>"
            f"<td style='font-size:11px;text-align:center;color:#666'>{g['goldenRange']}</td>"
            + light(bool(g["under"]), str(g["under"]))
            + light(bool(g["buchi"]), str(g["buchi"]))
            + light(bool(g["nostanza"]), str(g["nostanza"]))
            + (f"<td style='font-size:10px;color:#a94442'>{detail}</td>"
               if detail else "<td></td>")
            + "</tr>")
    title = "NON CONFORME" if any(g["under"] or g["buchi"] or g["nostanza"]
                                  for g in groups) else "CONFORME"
    color = "#b94a48" if title != "CONFORME" else "#468847"
    html_out = (f"<html><head><meta charset='utf-8'></head><body>"
                f"<h3 style='color:{color}'>Sanity: {html.escape(title)}</h3>"
                f"<table border='1' cellpadding='2' cellspacing='0'>"
                f"<tr><th>gruppo</th><th>run-min</th><th>golden range</th>"
                f"<th>sotto-min</th><th>buchi 1h</th><th>no stanza</th><th>dettaglio</th></tr>"
                f"{''.join(rows)}</table>"
                f"<p style='font-size:11px'>Generated from {html.escape(str(path))} "
                f"(0hard is binary: the text gate stays authoritative).</p></body></html>")
    out.write_text(html_out, encoding="utf-8")
    print(f"OK wrote {out} ({len(groups)} groups)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
