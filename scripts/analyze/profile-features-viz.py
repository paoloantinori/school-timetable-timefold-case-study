#!/usr/bin/env python3
"""TASK-542 iter-26: 2D scatter (holes x teacher-weekday concentration) + heatmap
of the teacher-weekday matrix for golden vs two construction profiles. Self-contained
HTML, same palette conventions as golden-vs-run-viz (color = outcome class)."""
import collections
import datetime
import gzip
import json
import sys

import importlib.util
_dir = __file__.rsplit('/', 1)[0]
_spec = importlib.util.spec_from_file_location(
    'cpf', _dir + '/construction-profile-features.py')
_cpf = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_cpf)
load_export, load_checkpoints = _cpf.load_export, _cpf.load_checkpoints

WDS = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven']


def matrix(lessons, ts, placements, min_hours=10):
    tw = collections.defaultdict(collections.Counter)
    tot = collections.Counter()
    for lid, tsid in placements.items():
        m = lessons.get(lid)
        t = ts.get(tsid)
        if not m or not t or m['pseudo'] or not m['teacher']:
            continue
        wd = datetime.date.fromisoformat(t[0]).weekday()
        tw[m['teacher']][wd] += 1
        tot[m['teacher']] += 1
    rows = []
    for name in sorted(tw, key=lambda n: -(max(tw[n].values()) / tot[n])):
        if tot[name] < min_hours:
            continue
        rows.append((name, [tw[name][wd] for wd in range(5)], tot[name]))
    return rows


def heatmap_html(title, rows):
    tr = ''.join(
        f"<tr><td class=l>{n}</td>" + ''.join(
            f"<td class=c style=\"background:rgba(37,99,235,{max(0.06, h / max(1, max(r))):.2f})\">"
            f"{h}</td>" for h in r)
        + f"<td class=l>{t}h</td></tr>"
        for n, r, t in rows[:18])
    return f"<div class=card><h3>{title}</h3><table>{tr}</table></div>"


def main():
    feats = json.load(open('claudedocs/spalmone-2026-27/task542-results/profile-features.json'))
    # outcome classes (from iterations-542.md): best=-6/-2/-23 deliveries; doomed=discards
    def cls(name):
        if 'GOLDEN' in name: return 'golden'
        if 'attempt1-' in name: return 'deliverable(-6-like, deterministic)'
        if 'attempt15' in name: return 'delivered(-23)'
        if 'gate' in name: return 'doomed(gate -853/-892)'
        if 'winner-FINAL' in name: return 'delivered(-23 FINAL)'
        return 'doomed(discarded)'

    colors = {'golden': '#d97706', 'deliverable(-6-like, deterministic)': '#16a34a',
              'delivered(-23)': '#2563eb', 'delivered(-23 FINAL)': '#2563eb',
              'doomed(gate -853/-892)': '#dc2626', 'doomed(discarded)': '#dc2626'}
    pts = []
    for name, f in feats.items():
        pts.append((f['holes'], f['conc'], colors[cls(name)], name, cls(name)))
    maxh = max(p[0] for p in pts) * 1.08
    svg_pts = ''.join(
        f'<circle cx="{20 + p[0] / maxh * 740:.1f}" cy="{330 - (p[1] - 0.10) / 0.20 * 300:.1f}"'
        f' r="6" fill="{p[2]}" opacity="0.85"><title>{p[3]}: holes={p[0]} conc={p[1]:.2f}</title></circle>'
        for p in pts)
    scatter = (f'<svg viewBox="0 0 800 380" xmlns="http://www.w3.org/2000/svg">'
               f'<line x1="20" y1="330" x2="770" y2="330" stroke="#64748b"/>'
               f'<line x1="20" y1="330" x2="20" y2="30" stroke="#64748b"/>'
               f'<text x="400" y="365" text-anchor="middle" font-size="13">buchi H17 in costruzione</text>'
               f'<text x="12" y="200" font-size="13" transform="rotate(-90 12 200)">concentrazione docente-weekday (frazione >=50%)</text>'
               + svg_pts + '</svg>')
    legend = ''.join(
        f'<span style="margin-right:18px"><span style="color:{c}">&#9679;</span> {k}</span>'
        for k, c in colors.items())

    # heatmaps: golden vs lowest-holes vs highest-holes construction profile
    wl, wts, wp = load_export('/Users/user/planner/claudedocs/spalmone-2026-27/task542-results/night-2308/export-winner.json.gz')
    grids = load_checkpoints(wl, wts, '/Users/user/planner/staged-checkpoints')
    ghours, grows = collections.defaultdict(set), []
    for line in open('src/main/resources/seed/lessons.csv'):
        parts = line.strip().split(',')
        if len(parts) < 7 or parts[0] == 'date':
            continue
        date, _d, slot, _c, g, teach, sub = parts[:7]
        if teach in ('', 'Fasullo'):
            continue
        tok = slot.split()[0][:5]
        try:
            hour = int(tok[:2])
        except ValueError:
            continue
        ghours[date].add(hour)
        grows.append((date, hour, g, teach, sub))
    rank = {dt: {h: i for i, h in enumerate(sorted(hs))} for dt, hs in ghours.items()}
    L, T, P = {}, {}, {}
    i = 0
    for date, hour, g, teach, sub in grows:
        i += 1
        T[i] = (date, rank[date][hour])
        L[i] = {'teacher': teach, 'pseudo': teach == 'Stage', 'group': g}
        P[i] = i
    lo = min(grids, key=lambda n: sum(1 for _ in grids[n]))
    lo = 'attempt1'
    hi = 'attempt9'
    hm = (heatmap_html('GOLDEN (riferimento: 7 docenti concentrati)', matrix(L, T, P))
          + heatmap_html('Run attempt1 CONSTRUCTION (45 buchi, deliverable)', matrix(wl, wts, grids['attempt1']))
          + heatmap_html('Run attempt9 CONSTRUCTION (331 buchi, doomed)', matrix(wl, wts, grids['attempt9'])))
    html = (f'<html><head><meta charset="utf-8"><title>TASK-542 profili di costruzione: buchi x concentrazione</title>'
            f'<style>body{{font-family:system-ui;margin:24px;background:#f8fafc}}'
            f'.card{{background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:12px;margin:12px 0;overflow-x:auto}}'
            f'table{{border-collapse:collapse;font-size:12px}}td{{padding:2px 8px}}td.c{{text-align:center;color:#fff}}'
            f'td.l{{font-weight:600}}h3{{margin:4px 0}}</style></head><body>'
            f'<h1>TASK-542 iter-26: lo spazio dei profili di costruzione (buchi H17 x concentrazione docente-weekday)</h1>'
            f'<div class=card><p>{legend}</p>{scatter}</div>'
            f'<h2>Matrici docente-weekday (ore per weekday, top 18 docenti per concentrazione)</h2>{hm}'
            f'</body></html>')
    out = 'claudedocs/spalmone-2026-27/task542-results/profile-features-viz.html'
    open(out, 'w').write(html)
    print('written', out)


if __name__ == '__main__':
    main()
