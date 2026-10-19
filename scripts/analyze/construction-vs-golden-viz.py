#!/usr/bin/env python3
"""construction-vs-golden-viz.py - the "colpo d'occhio" made mechanical (maintainer, 9/09).

Compares the CONSTRUCTION checkpoint (before LS) against the golden, per-group
per-month, producing an HTML heatmap that immediately shows whether the
construction has the right distribution shape. maintainer's thesis: from the
construction checkpoint we should be able to verify the similarity index
between golden and candidate, BEFORE spending hours of LS refining.

Usage:
    construction-vs-golden-viz.py <checkpoint.json> [--golden <lessons.csv>]

Inputs:
    checkpoint.json: a TASK-542 staged checkpoint (POST_CONSTRUCTION or any boundary)
    golden: default seed/lessons.csv
"""
import argparse
import csv
import json
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

def parse_args():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("checkpoint", help="TASK-542 checkpoint JSON file")
    p.add_argument("--golden", default="src/main/resources/seed/lessons.csv")
    p.add_argument("--meta", help="lesson metadata TSV (id|group|pseudo) from the laptop DB")
    p.add_argument("--tsmap", help="timeslot map TSV (id|startdatetime) from the laptop DB")
    p.add_argument("--output", default=None, help="output HTML (default: stdout summary + HTML file)")
    return p.parse_args()

def load_golden(path):
    """Returns {group: {month: hours}}"""
    dist = defaultdict(lambda: defaultdict(int))
    with open(path) as f:
        for r in csv.DictReader(f):
            if r['teacher'] in ('Stage', 'Fasullo'): continue
            m = date.fromisoformat(r['date']).strftime('%Y-%m')
            dist[r['group']][m] += 1
    return dist

def load_checkpoint(path, meta_path, tsmap_path):
    """Returns {group: {month: hours}} from checkpoint JSON."""
    meta = {}
    for line in open(meta_path):
        line = line.strip()
        if '|' not in line: continue
        parts = [p.strip() for p in line.split('|')]
        if not parts[0].isdigit(): continue
        meta[int(parts[0])] = (parts[1], parts[2] == 'true')
    tsmap = {}
    for line in open(tsmap_path):
        line = line.strip()
        if '|' not in line: continue
        parts = [p.strip() for p in line.split('|')]
        if not parts[0].isdigit(): continue
        stamp = parts[1]
        tsmap[int(parts[0])] = stamp.split(' ')[0]
    dist = defaultdict(lambda: defaultdict(int))
    d = json.load(open(path))
    for p in d['placements']:
        if p['timeslotId'] is None: continue
        g, pseudo = meta[p['lessonId']]
        if pseudo: continue
        dt = tsmap[p['timeslotId']]
        m = date.fromisoformat(dt).strftime('%Y-%m')
        dist[g][m] += 1
    return dist

MONTHS = ['2026-09','2026-10','2026-11','2026-12','2027-01','2027-02','2027-03','2027-04','2027-05','2027-06']
MONTH_LABELS = ['Sep','Oct','Nov','Dec','Jan','Feb','Mar','Apr','May','Jun']

def compute_similarity(golden, run):
    """Per-group cosine similarity of monthly distributions (0=orthogonal, 1=identical)."""
    groups = sorted(set(golden) | set(run))
    scores = {}
    for g in groups:
        gv = [golden.get(g, {}).get(m, 0) for m in MONTHS]
        rv = [run.get(g, {}).get(m, 0) for m in MONTHS]
        dot = sum(a*b for a, b in zip(gv, rv))
        ng = sum(a*a for a in gv) ** 0.5
        nr = sum(a*a for a in rv) ** 0.5
        scores[g] = dot / (ng * nr) if ng > 0 and nr > 0 else 0.0
    return scores

def generate_html(golden, run, scores, checkpoint_path):
    groups = sorted(set(golden) | set(run))
    avg_sim = sum(scores.values()) / len(scores) if scores else 0
    css = """
    body{font-family:monospace;background:#1a1a2e;color:#e0e0e0;margin:20px}
    h1{color:#00b4d8;font-size:1.2rem}
    .summary{background:#16213e;padding:10px;border-radius:5px;margin:10px 0}
    .summary .avg{font-size:1.5rem;color:#4ecca3 if avg>0.8 else #e94560}
    table{border-collapse:collapse;margin:10px 0}
    td,th{border:1px solid #333;padding:4px 8px;text-align:center;font-size:0.85rem}
    th{background:#16213e;color:#00b4d8}
    .gold{color:#4ecca3}
    .run{color:#e94560}
    .diff{font-size:0.75rem;color:#888}
    .sim{font-weight:bold}
    .sim-hi{color:#4ecca3}.sim-md{color:#f9c74f}.sim-lo{color:#e94560}
    """
    rows = ""
    for g in groups:
        sim = scores.get(g, 0)
        sim_cls = 'sim-hi' if sim > 0.85 else ('sim-md' if sim > 0.6 else 'sim-lo')
        cells_gold = "".join(f"<td class='gold'>{golden.get(g,{}).get(m,0)}</td>" for m in MONTHS)
        cells_run = "".join(f"<td class='run'>{run.get(g,{}).get(m,0)}</td>" for m in MONTHS)
        cells_diff = "".join(
            f"<td class='diff'>{run.get(g,{}).get(m,0) - golden.get(g,{}).get(m,0):+d}</td>"
            for m in MONTHS)
        rows += f"""<tr><td rowspan=3><b>{g}</b><br><span class='sim {sim_cls}'>{sim:.2f}</span></td>
<td class='diff'>golden</td>{cells_gold}</tr>
<tr><td class='diff'>run</td>{cells_run}</tr>
<tr><td class='diff'>diff</td>{cells_diff}</tr>"""
    html = f"""<!DOCTYPE html><html><head><style>{css}</style></head><body>
<h1>Construction vs Golden: monthly distribution</h1>
<div class='summary'>
<p>Checkpoint: <code>{checkpoint_path}</code></p>
<p>Average similarity: <span class='sim {"sim-hi" if avg_sim > 0.85 else "sim-md" if avg_sim > 0.6 else "sim-lo"}'>{avg_sim:.3f}</span></p>
<p>maintainer's thesis (9/09): if the construction doesn't have the golden's shape, the LS spends hours fixing what shouldn't be broken.</p>
</div>
<table>
<tr><th>Group</th><th></th>{"".join(f"<th>{l}</th>" for l in MONTH_LABELS)}</tr>
{rows}
</table>
</body></html>"""
    return html

def main():
    args = parse_args()
    golden = load_golden(args.golden)
    run = load_checkpoint(args.checkpoint, args.meta, args.tsmap)
    scores = compute_similarity(golden, run)
    avg = sum(scores.values()) / len(scores) if scores else 0
    # Console summary
    print(f"Construction vs Golden similarity: avg={avg:.3f}")
    for g in sorted(scores):
        s = scores[g]
        flag = "OK" if s > 0.85 else ("WARN" if s > 0.6 else "LOW")
        print(f"  {g:12s} {s:.3f} [{flag}]")
    # HTML
    html = generate_html(golden, run, scores, args.checkpoint)
    out = args.output or str(Path(args.checkpoint).with_suffix('.vs-golden.html'))
    Path(out).write_text(html)
    print(f"\nHTML: {out}")

if __name__ == '__main__':
    main()
