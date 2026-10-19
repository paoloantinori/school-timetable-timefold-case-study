#!/usr/bin/env python3
"""Day-start ANATOMY: where the late-start days sit (maintainer 2026-07-28).

day-start-kpi.py answers "how many (group, date) days start after the group's
first open slot" as ONE global rate. That rate hides the shape that matters to
a human reading the timetable: 15 late days scattered over 14 groups and 171
dates are noise, but 15 late days that are all the SAME group on the SAME
weekday are a broken weekly pattern - and 99.2% "anchored" reads fine either
way. This script breaks the same population down by (group, weekday), compares
it cell-by-cell against the golden, and dumps the offending dates with the
golden's own first lesson beside them.

Usage:
  python3 scripts/analyze/day-start-anatomy.py <run-lessons.txt> [--html out.html]

<run-lessons.txt> is the usual group|date|hour|subject|teacher|pinned dump (or
any golden-format CSV); the golden reference is data/lessons.csv.

Late = the day's first REAL lesson (KPI_EXCLUDED_PSEUDO dropped, like every KPI
in this family) sits later than BASE_HOUR, except on the first-Monday ramp date
where the calendar itself opens at 9 - day-start-kpi.py's convention, kept
identical here so the two scripts can never disagree on the population.
"""
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from shared import (KPI_EXCLUDED_PSEUDO, load_lesson_rows,  # noqa: E402
                    LESSONS_CSV, RAMP_DATE, add_kpi_args)

GOLDEN = LESSONS_CSV  # golden reference; overridden from --lessons-path in __main__
BASE_HOUR = 8
WEEKDAYS = ['lunedi', 'martedi', 'mercoledi', 'giovedi', 'venerdi']


def first_real_hours(rows):
    """(group, date) -> hour of the day's first real lesson."""
    first = {}
    for g, d, h, s, t in rows:
        if s in KPI_EXCLUDED_PSEUDO or t in KPI_EXCLUDED_PSEUDO:
            continue
        k = (g, d)
        if k not in first or h < first[k]:
            first[k] = h
    return first


def is_late(d, hour):
    return hour > (BASE_HOUR + 1 if d == RAMP_DATE else BASE_HOUR)


def matrix(first):
    """(group, weekday index) -> [late days, total days]."""
    m = defaultdict(lambda: [0, 0])
    for (g, d), h in first.items():
        if d.weekday() > 4:
            continue
        cell = m[(g, d.weekday())]
        cell[1] += 1
        if is_late(d, h):
            cell[0] += 1
    return m


def main(run_path, html_path=None):
    run_first = first_real_hours(load_lesson_rows(run_path, strip_fields=True))
    gold_first = first_real_hours(load_lesson_rows(str(GOLDEN), strip_fields=True))
    run_m, gold_m = matrix(run_first), matrix(gold_first)
    groups = sorted({g for g, _ in run_m} | {g for g, _ in gold_m})

    run_late = sum(1 for (_, d), h in run_first.items() if is_late(d, h))
    gold_late = sum(1 for (_, d), h in gold_first.items() if is_late(d, h))
    print(f"GIORNATE IN RITARDO (prima lezione reale dopo le {BASE_HOUR}):")
    print(f"  run    {run_late} su {len(run_first)} giorni-gruppo")
    print(f"  golden {gold_late} su {len(gold_first)} giorni-gruppo")

    print("\nMATRICE gruppo x giorno-settimana (run | golden), celle con ritardi:")
    hot = []
    for g in groups:
        for wd in range(5):
            r, go = run_m[(g, wd)][0], gold_m[(g, wd)][0]
            if r or go:
                print(f"  {g:8} {WEEKDAYS[wd]:10} run {r:3} | golden {go:3}"
                      f"{'   <<< SCARTO' if r - go >= 3 else ''}")
                if r - go >= 3:
                    hot.append((g, wd, r, go))

    for g, wd, r, go in hot:
        print(f"\nDETTAGLIO {g} / {WEEKDAYS[wd]} (run {r} ritardi, golden {go}):")
        for (gg, d), h in sorted(run_first.items()):
            if gg != g or d.weekday() != wd or not is_late(d, h):
                continue
            gh = gold_first.get((gg, d))
            print(f"  {d}  run inizia alle {h}"
                  f"   golden {'inizia alle ' + str(gh) if gh else 'nessuna lezione'}")

    if html_path:
        write_html(html_path, groups, run_m, gold_m, run_first, gold_first, hot)
        print(f"\nHTML: {html_path}")


def cell_style(late, tot):
    """Sequential blue ramp (palette 100..700), light = near zero."""
    if not tot:
        return '--surface-2', ''
    ramp = ['#f0efec', '#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#104281']
    idx = 0 if not late else min(len(ramp) - 1, 1 + int(late / max(tot, 1) * 12))
    ink = '#ffffff' if idx >= 4 else '#0b0b0b'
    return ramp[idx], ink


def write_html(path, groups, run_m, gold_m, run_first, gold_first, hot):
    def grid(m, title):
        out = [f'<h3>{title}</h3><table class="hm"><tr><th></th>'
               + ''.join(f'<th>{w[:3]}</th>' for w in WEEKDAYS) + '</tr>']
        for g in groups:
            out.append(f'<tr><th class="rowh">{g}</th>')
            for wd in range(5):
                late, tot = m[(g, wd)]
                bg, ink = cell_style(late, tot)
                lbl = str(late) if late else ('.' if tot else '')
                out.append(f'<td style="background:{bg};color:{ink}" '
                           f'data-tip="{g} / {WEEKDAYS[wd]}: {late} giornate in ritardo su {tot}">'
                           f'{lbl}</td>')
            out.append('</tr>')
        return ''.join(out) + '</table>'

    dist_r, dist_g = defaultdict(int), defaultdict(int)
    for h in run_first.values():
        dist_r[h] += 1
    for h in gold_first.values():
        dist_g[h] += 1
    hours = sorted(set(dist_r) | set(dist_g))
    mx = max(list(dist_r.values()) + list(dist_g.values()))
    bars = ''.join(
        f'<div class="bgrp"><span class="bl">{h}:00</span><span class="bstack">'
        f'<span class="brow"><span class="bar run" style="width:{100*dist_r[h]/mx:.1f}%"></span>'
        f'<span class="bv">{dist_r[h]} run</span></span>'
        f'<span class="brow"><span class="bar gold" style="width:{100*dist_g[h]/mx:.1f}%"></span>'
        f'<span class="bv">{dist_g[h]} golden</span></span></span></div>'
        for h in hours)

    detail = ''
    for g, wd, r, go in hot:
        rows = []
        for (gg, d), h in sorted(run_first.items()):
            if gg != g or d.weekday() != wd or not is_late(d, h):
                continue
            gh = gold_first.get((gg, d))
            cells = ''.join(
                f'<span class="slot {"miss" if hh < h else "fill"}">{hh}</span>' for hh in range(8, 14))
            gcells = ''.join(
                f'<span class="slot {"fill" if gh is not None and hh >= gh else "miss"}">{hh}</span>'
                for hh in range(8, 14))
            rows.append(f'<tr><td>{d}</td><td>{cells}</td><td>{gcells}</td></tr>')
        detail += (f'<h3>{g} / {WEEKDAYS[wd]}: run {r} giornate in ritardo, golden {go}</h3>'
                   '<table class="det"><tr><th>data</th><th>run (ore occupate)</th>'
                   '<th>golden</th></tr>' + ''.join(rows) + '</table>')

    run_late = sum(1 for (_, d), h in run_first.items() if is_late(d, h))
    gold_late = sum(1 for (_, d), h in gold_first.items() if is_late(d, h))
    Path(path).write_text(f"""<!doctype html><html lang="it"><meta charset="utf-8">
<title>Anatomia degli inizi in ritardo</title><style>
body{{margin:0;padding:28px;font:14px/1.5 system-ui,sans-serif;background:#fcfcfb;color:#0b0b0b}}
h1{{font-size:20px;margin:0 0 4px}} h2{{font-size:15px;margin:28px 0 8px}} h3{{font-size:13px;margin:16px 0 6px;color:#52514e;font-weight:600}}
.sub{{color:#52514e;margin:0 0 20px}}
.tiles{{display:flex;gap:14px;flex-wrap:wrap;margin-bottom:8px}}
.tile{{border:1px solid #e3e2dd;border-radius:10px;padding:12px 16px;min-width:150px;background:#fff}}
.tile b{{display:block;font-size:26px;line-height:1.1}} .tile span{{color:#52514e;font-size:12px}}
.two{{display:flex;gap:34px;flex-wrap:wrap}}
table.hm{{border-collapse:separate;border-spacing:2px}}
table.hm th{{font-weight:500;color:#52514e;font-size:11px;padding:2px 4px}}
table.hm th.rowh{{text-align:right}}
table.hm td{{width:34px;height:26px;text-align:center;border-radius:4px;font-size:12px;font-variant-numeric:tabular-nums}}
.bgrp{{display:flex;align-items:center;gap:10px;margin:10px 0}}
.bstack{{flex:1;display:flex;flex-direction:column;gap:2px}}
.brow{{display:flex;align-items:center;gap:8px}}
.bl{{width:46px;color:#52514e;font-size:12px;text-align:right}}
.bar{{height:11px;border-radius:0 4px 4px 0;min-width:2px}} .bar.run{{background:#2a78d6}} .bar.gold{{background:#eb6834}}
.bv{{font-size:12px;color:#52514e;font-variant-numeric:tabular-nums;white-space:nowrap}}
.leg{{display:flex;gap:16px;margin:6px 0 0;font-size:12px;color:#52514e}}
.sw{{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:5px}}
table.det{{border-collapse:collapse;font-size:12px}} table.det th{{text-align:left;color:#52514e;font-weight:500;padding:3px 10px 3px 0}}
table.det td{{padding:2px 10px 2px 0;font-variant-numeric:tabular-nums}}
.slot{{display:inline-block;width:20px;text-align:center;margin-right:2px;border-radius:3px;font-size:10px;padding:1px 0}}
.slot.fill{{background:#cde2fb;color:#104281}} .slot.miss{{background:#fff;color:#d03b3b;border:1px dashed #d03b3b}}
#tip{{position:fixed;pointer-events:none;background:#0b0b0b;color:#fff;padding:5px 8px;border-radius:5px;font-size:12px;opacity:0;transition:opacity .1s}}
</style>
<h1>Anatomia degli inizi in ritardo, run contro golden</h1>
<p class="sub">Giornata "in ritardo" = la prima lezione reale del gruppo e' dopo le 8 (esclusa la data-rampa 14/09, che apre alle 9). Stessa popolazione di day-start-kpi.py e del vincolo M18.</p>
<div class="tiles">
  <div class="tile"><b>{run_late}</b><span>giornate in ritardo, run</span></div>
  <div class="tile"><b>{gold_late}</b><span>giornate in ritardo, golden</span></div>
  <div class="tile"><b>{sum(r for _, _, r, _ in hot)}</b><span>concentrate nelle celle anomale</span></div>
</div>
<h2>Dove stanno: gruppo per giorno della settimana</h2>
<div class="two"><div>{grid(run_m, 'RUN')}</div><div>{grid(gold_m, 'GOLDEN')}</div></div>
<p class="leg"><span>Il colore cresce con la quota di giornate in ritardo della cella; il punto indica una cella senza ritardi.</span></p>
<h2>Ora della prima lezione</h2>
{bars}
<p class="leg"><span><span class="sw" style="background:#2a78d6"></span>run</span><span><span class="sw" style="background:#eb6834"></span>golden</span></p>
<h2>Dettaglio delle celle anomale</h2>
{detail}
<div id="tip"></div><script>
const tip=document.getElementById('tip');
document.querySelectorAll('[data-tip]').forEach(el=>{{
  el.addEventListener('mousemove',e=>{{tip.textContent=el.dataset.tip;tip.style.opacity=1;
    tip.style.left=(e.clientX+12)+'px';tip.style.top=(e.clientY+12)+'px';}});
  el.addEventListener('mouseleave',()=>tip.style.opacity=0);}});
</script></html>""", encoding='utf-8')


if __name__ == '__main__':
    import argparse
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('run', help='run lessons dump (group|date|hour|subject|teacher|pinned) or golden CSV')
    ap.add_argument('--html', help='write HTML report to this path')
    add_kpi_args(ap)  # --ramp/--lessons-path (+ --year/--year-start/--year-end/--data-dir/--closures-path)
    args = ap.parse_args()
    if args.lessons_path:
        GOLDEN = args.lessons_path
    RAMP_DATE = date.fromisoformat(args.ramp)
    main(args.run, args.html)
