#!/usr/bin/env python3
"""TASK (prassi referente 30/08): stabilita della coppia (docente, gruppo).

La prassi descritta da referente: le celle (weekday, slot) di una coppia docente-gruppo
restano fisse tutto l'anno e le MATERIE si alternano a blocchi (prima si esaurisce
un corso, poi il prossimo parte negli stessi slot). Il golden la pratica su circa
un quarto delle coppie (i docenti di laboratorio: T16, T10, T26), i
teorici (T09, T20) intrecciano.

Metriche per coppia (soglia --min-lessons):
  wd_conc   concentrazione weekday (max share);
  switches  cambi di materia nella timeline (sequenza a blocchi => pochi);
  cell_stab intersezione/unione delle celle (weekday, slot) sulle settimane attive.

Usage:
  pair-stability-kpi.py --run <lessons.txt|golden csv> [--compare <altro dump>]
                        [--html OUT.html] [--top 16]
La via pipe (dump run) e il golden CSV si distinguono da soli.
"""
import argparse
import datetime
import html
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from shared import parse_lessons_csv, parse_lessons_pipe  # noqa: E402

# il golden porta ore muro (8..15 dove 14=14:30), i dump portano slotIndex 0..7
WALL_TO_ORD = {8: 0, 9: 1, 10: 2, 11: 3, 12: 4, 13: 5, 14: 6, 15: 7}


def load_rows(path):
    text = Path(path).read_text(encoding='utf-8-sig')
    if '|' in text.splitlines()[1] if len(text.splitlines()) > 1 else False:
        rows = parse_lessons_pipe(path, include_hour=True)
        return [(r[0], r[1], r[2], r[3], r[4]) for r in rows]
    rows = parse_lessons_csv(path, include_hour=True)
    return [(r[0], r[1], r[2], r[3], r[4]) for r in rows]


def pair_metrics(rows, min_lessons):
    by_pair = defaultdict(list)
    for g, d, hour, subj, t in rows:
        if t in ('Stage', 'Fasullo'):
            continue
        by_pair[(t, g)].append((d, hour, subj))
    out = {}
    for tg, lst in by_pair.items():
        if len(lst) < min_lessons:
            continue
        lst.sort(key=lambda x: (x[0], x[1]))
        # weekday concentration
        wds = defaultdict(int)
        for d, h, s in lst:
            wds[d.weekday()] += 1
        wd_conc = max(wds.values()) / len(lst)
        # subject switches
        switches = 0
        prev = None
        for d, h, s in lst:
            if prev and s != prev:
                switches += 1
            prev = s
        n_subj = len({s for _, _, s in lst})
        # cell stability across active weeks
        cells_by_week = defaultdict(set)
        for d, h, s in lst:
            ord_ = h if isinstance(h, int) and h <= 7 else WALL_TO_ORD.get(int(str(h).split(':')[0]), -1)
            if ord_ < 0:
                continue
            cells_by_week[d.isocalendar()[1]].add((d.weekday(), ord_))
        shapes = [set(v) for v in cells_by_week.values() if v]
        if shapes:
            inter = set.intersection(*shapes)
            union = set.union(*shapes)
            cell_stab = len(inter) / len(union) if union else 0.0
        else:
            cell_stab = 0.0
        out[tg] = {'hours': len(lst), 'wd_conc': wd_conc, 'switches': switches,
                   'n_subj': n_subj, 'cell_stab': cell_stab}
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--run', required=True, help='golden CSV o dump pipe del run')
    ap.add_argument('--compare', help='secondo dump (es. il run quando --run e il golden)')
    ap.add_argument('--min-lessons', type=int, default=20)
    ap.add_argument('--top', type=int, default=16, help='coppie nel HTML, per ore decrescenti')
    ap.add_argument('--html', help='output HTML (piccoli multipli per coppia)')
    args = ap.parse_args()

    m1 = pair_metrics(load_rows(args.run), args.min_lessons)
    m2 = pair_metrics(load_rows(args.compare), args.min_lessons) if args.compare else {}

    print(f'coppie con >= {args.min_lessons} lezioni: {len(m1)}')
    stab1 = [tg for tg, v in m1.items() if v['cell_stab'] >= 0.8 and v['switches'] <= v['n_subj'] + 2]
    print(f'  PRASSI PIENA (celle stabili >=80% e sequenza a blocchi): {len(stab1)} -> {sorted(stab1)[:8]}')
    print(f"{'coppia':34s} {'ore':>4s} {'wd%':>5s} {'sw':>3s} {'mat':>3s} {'cell%':>5s}"
          + ('  | RUN wd% sw cell%' if m2 else ''))
    for tg, v in sorted(m1.items(), key=lambda x: -x[1]['hours'])[:24]:
        r = m2.get(tg)
        cmp_line = (f"  | {r['wd_conc']*100:4.0f}% {r['switches']:3d} {r['cell_stab']*100:4.0f}%"
                    if r else '  | ---') if m2 else ''
        print(f"{tg[0] + ' x ' + tg[1]:34s} {v['hours']:4d} {v['wd_conc']*100:4.0f}%"
              f" {v['switches']:3d} {v['n_subj']:3d} {v['cell_stab']*100:4.0f}%{cmp_line}")

    if args.html:
        render_html(args.run, args.compare, m1, m2, args.top, args.min_lessons, args.html)
        print(f'HTML: {args.html}')


SUBJ_COLORS = ['#3b82f6', '#ef4444', '#22c55e', '#eab308', '#a855f7',
               '#ec4899', '#14b8a6', '#f97316', '#64748b', '#8b5cf6']


def render_html(run_path, compare_path, m1, m2, top, min_lessons, out):
    rows1 = load_rows(run_path)
    rows2 = load_rows(compare_path) if compare_path else []
    top_pairs = [tg for tg, _ in sorted(m1.items(), key=lambda x: -x[1]['hours'])[:top]]

    def grid_html(rows, tg):
        cells = defaultdict(dict)  # week -> (wd, ord) -> subject
        weeks = set()
        for g, d, hour, subj, t in rows:
            if (t, g) != tg:
                continue
            ord_ = hour if isinstance(hour, int) and hour <= 7 else WALL_TO_ORD.get(
                int(str(hour).split(':')[0]), -1)
            if ord_ < 0:
                continue
            w = d.isocalendar()[1]
            cells[w][(d.weekday(), ord_)] = subj
            weeks.add(w)
        if not weeks:
            return '<div class=miss>no data</div>'
        lo, hi = min(weeks), max(weeks)
        subjects = sorted({s for w in cells.values() for s in w.values()})
        color = {s: SUBJ_COLORS[i % len(SUBJ_COLORS)] for i, s in enumerate(subjects)}
        legend = ' '.join(
            f'<span style="color:{color[s]}">&#9632;</span>{html.escape(s[:16])}' for s in subjects)
        h = [f'<table class=hm><tr><th></th>' + ''.join(
            f'<th>{w}</th>' for w in range(lo, hi + 1)) + '</tr>']
        for wd in range(5):
            for ord_ in range(8):
                label = 'LMMGV'[wd] + str(ord_ + 1)
                h.append(f'<tr><th>{label}</th>')
                for w in range(lo, hi + 1):
                    subj = cells.get(w, {}).get((wd, ord_))
                    if subj:
                        c = color[subj]
                        h.append(f'<td style="background:{c}" title="{html.escape(subj)}"></td>')
                    else:
                        h.append('<td></td>')
                h.append('</tr>')
        h.append('</table>')
        return f'<div class=cell><div class=leg>{legend}</div>{"".join(h)}</div>'

    parts = ['''<!doctype html><html><head><meta charset=utf-8><title>Stabilita' coppia docente-gruppo</title>
<style>
body{font-family:system-ui;background:#141619;color:#dfe3e8;margin:20px}
h1{font-size:18px} h2{font-size:14px;margin:18px 0 4px}
.hm{border-collapse:collapse}.hm td,.hm th{border:1px solid #2c313a;padding:0;width:10px;height:10px}
.hm th{color:#9aa3ad;font-weight:400;font-size:9px;padding:0 3px}
.pair{display:inline-block;vertical-align:top;margin:8px;padding:6px;background:#1a1e24}
.pair .name{font-weight:600;font-size:12px}
.pair .stats{font-size:10px;color:#9aa3ad}
.miss{color:#666;font-size:11px}
.leg{font-size:9px;color:#9aa3ad;margin:2px 0}
</style></head><body>
<h1>Stabilita' (docente, gruppo): celle fisse, materie a blocchi (prassi referente, verificata nel golden)</h1>
<p>Colonne = settimane ISO; righe = (giorno, slot) L1..V8; colore = materia.
Bande orizzontali solide = la prassi (celle fisse, materia esaurita poi la prossima); strisce = intreccio.</p>''']
    for tg in top_pairs:
        v = m1[tg]
        r = m2.get(tg)
        stats = (f"ore {v['hours']} | wd {v['wd_conc']*100:.0f}% | switch {v['switches']}"
                 f" | celle {v['cell_stab']*100:.0f}%")
        if r:
            stats += (f" &nbsp;||&nbsp; RUN: wd {r['wd_conc']*100:.0f}% | switch {r['switches']}"
                      f" | celle {r['cell_stab']*100:.0f}%")
        parts.append(f'<div class=pair><div class=name>{html.escape(tg[0] + " x " + tg[1])}</div>'
                     f'<div class=stats>{stats}</div>')
        parts.append(grid_html(rows1, tg))
        if rows2:
            parts.append(grid_html(rows2, tg))
        parts.append('</div>')
    parts.append('</body></html>')
    Path(out).write_text(''.join(parts), encoding='utf-8')


if __name__ == '__main__':
    main()
