#!/usr/bin/env python3
"""Teacher-tier pacing KPI: how a run consumes generic vs specialised teachers.

A teacher is GENERIC when they serve many student groups (default: >= 8 of 14) and
SPECIALISED otherwise. The two classes are not interchangeable: a generic teacher is
the flexible resource that can fill a hole in almost any group, a specialised one can
only serve their few groups. The golden timetable consumes the two classes at a
constant rate all year; a run that front-loads the generics ends the year holding a
specialist-heavy residue, which is exactly when horizontal recombination gets hard.

Reported, golden vs run:
  1. mix per course family (an input invariant: it must match, it is a sanity check)
  2. mix per slot of the day (where each class is placed inside the day)
  3. mix per month (the pacing: a drift here is the real finding)
  4. temporal centroid per teacher (median day of their hours), by class

Stage dates are excluded by default (--keep-stage to disable): during a group's stage
window the group has no ordinary lessons, so those dates carry no tier information.

Usage:
  scripts/analyze/teacher-tier-kpi.py RUN_LESSONS.txt [--data-dir DIR] [--generic-min N]

RUN_LESSONS.txt is the canonical dump `group|date|hour|subject|teacher|pinned`.
"""
import argparse
import collections
import csv
import datetime
import os
import statistics
import sys

PSEUDO_TEACHERS = {"Stage", "Esame", "RA", "Totale ore corso", ""}
FAMILIES = ("FB", "FD", "FA", "MODA", "FC", "G13", "G14")
YEAR_START = datetime.date(2026, 9, 14)


def family_of(group):
    for f in FAMILIES:
        if f in group:
            return f
    return group


def read_generic_teachers(data_dir, generic_min):
    """Classify teachers by how many groups they serve, from the incarichi facts."""
    spread = collections.defaultdict(set)
    with open(os.path.join(data_dir, "incarichi.csv")) as fh:
        for row in csv.DictReader(fh):
            teacher = (row.get("teacher") or "").strip()
            if teacher in PSEUDO_TEACHERS:
                continue
            spread[teacher].add(row["group"].strip())
    generic = {t for t, groups in spread.items() if len(groups) >= generic_min}
    return generic, set(spread) - generic, spread


def read_stage_dates(data_dir):
    """Dates on which a group is on stage, from the two authoritative calendar sheets."""
    stage = collections.defaultdict(set)
    with open(os.path.join(data_dir, "calendario_class_days.csv")) as fh:
        for row in csv.DictReader(fh):
            if row["modality"].strip().upper() == "S":
                stage[row["group"].strip()].add(row["date"])
    with open(os.path.join(data_dir, "calendario_fc.csv")) as fh:
        for row in csv.DictReader(fh):
            on_grid = float(row["aula_hours"] or 0) + float(row["lab_hours"] or 0)
            if float(row["stage_hours"] or 0) > 0 and on_grid == 0:
                stage[row["group"].strip()].add(row["date"])
    return stage


def read_golden(data_dir):
    with open(os.path.join(data_dir, "lessons.csv")) as fh:
        for row in csv.DictReader(fh):
            teacher = row["teacher"].strip()
            if teacher in PSEUDO_TEACHERS:
                continue
            yield row["date"], int(row["slot"][:2]) - 8, row["group"].strip(), teacher


def read_run(path):
    with open(path) as fh:
        for line in fh:
            parts = line.strip().split("|")
            if len(parts) < 6:
                continue
            group, date, hour, _subject, teacher, _pinned = parts[:6]
            teacher = teacher.strip()
            if teacher in PSEUDO_TEACHERS:
                continue
            yield date, int(hour) - 8, group.strip(), teacher


def share(rows, key, generic):
    """key(row) -> bucket; returns bucket -> (generic hours, specialised hours)."""
    buckets = collections.defaultdict(lambda: [0, 0])
    for row in rows:
        buckets[key(row)][0 if row[3] in generic else 1] += 1
    return buckets


def print_table(title, gold, run, label_width=9):
    print(f"\n{title}")
    head = f"{'':<{label_width}}{'GOLDEN':>9}{'RUN':>8}{'delta':>8}{'ore G':>8}{'ore R':>8}"
    print(head)
    for bucket in sorted(set(gold) | set(run), key=str):
        g, r = gold.get(bucket, [0, 0]), run.get(bucket, [0, 0])
        pg = g[0] * 100 / sum(g) if sum(g) else float("nan")
        pr = r[0] * 100 / sum(r) if sum(r) else float("nan")
        print(f"{str(bucket):<{label_width}}{pg:>8.1f}%{pr:>7.1f}%{pr - pg:>+8.1f}{sum(g):>8}{sum(r):>8}")


def centroids(rows):
    days = collections.defaultdict(list)
    for date, _slot, _group, teacher in rows:
        days[teacher].append((datetime.date.fromisoformat(date) - YEAR_START).days)
    return {t: statistics.median(v) for t, v in days.items()}


GOLD_COLOR, RUN_COLOR = "#1f6feb", "#d29922"   # validated pair (dataviz check: all PASS, contrast
                                               # WARN on the amber, relieved by direct labels + table)

HTML_HEAD = """<!DOCTYPE html>
<html lang="it"><head><meta charset="utf-8"><title>Ritmo dei tier docenti - golden vs run</title>
<style>
body{margin:0;color-scheme:light;background:#fcfcfb;color:#0b0b0b;font-family:system-ui,sans-serif;padding:20px 24px}
h1{font-size:17px;margin:0 0 2px} h2{font-size:13.5px;margin:22px 0 2px}
.sub{color:#52514e;font-size:12px;margin:0 0 6px;max-width:70ch;line-height:1.45}
.legend{display:flex;gap:16px;align-items:center;font-size:11.5px;color:#52514e;margin:8px 0}
.legend .sw{display:inline-block;width:13px;height:13px;border-radius:3px;vertical-align:-2px;margin-right:5px}
table{border-collapse:collapse;font-size:11.5px;margin-top:8px}
th,td{border:1px solid #dedcd7;padding:3px 8px;text-align:right}
th:first-child,td:first-child{text-align:left}
th{background:#f1f0ee;font-weight:600}
td.neg{color:#b23a10;font-weight:600} td.pos{color:#177245}
svg text{font-family:system-ui,sans-serif}
</style></head><body>
"""


def line_chart(months, gold_pct, run_pct):
    """Monthly generic-share, two series. Change over time, so: lines."""
    w, h, pad_l, pad_b, pad_t = 820, 300, 46, 34, 14
    lo, hi = 30, 55
    def x(i): return pad_l + i * (w - pad_l - 118) / max(1, len(months) - 1)
    def y(v): return pad_t + (hi - v) * (h - pad_t - pad_b) / (hi - lo)
    out = [f'<svg viewBox="0 0 {w} {h}" width="{w}" height="{h}" role="img">']
    for v in range(lo, hi + 1, 5):                              # recessive grid
        out.append(f'<line x1="{pad_l}" y1="{y(v):.1f}" x2="{w - 118}" y2="{y(v):.1f}" stroke="#eceae5"/>'
                   f'<text x="{pad_l - 8}" y="{y(v) + 4:.1f}" text-anchor="end" font-size="10" fill="#8a887f">{v}%</text>')
    for i, m in enumerate(months):
        out.append(f'<text x="{x(i):.1f}" y="{h - pad_b + 16}" text-anchor="middle" font-size="10" fill="#8a887f">{m[5:]}/{m[2:4]}</text>')
    for series, color, name in ((gold_pct, GOLD_COLOR, "golden"), (run_pct, RUN_COLOR, "run")):
        pts = " ".join(f"{x(i):.1f},{y(v):.1f}" for i, v in enumerate(series))
        out.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="2"/>')
        for i, v in enumerate(series):
            out.append(f'<circle cx="{x(i):.1f}" cy="{y(v):.1f}" r="4.5" fill="{color}" stroke="#fcfcfb" stroke-width="2">'
                       f'<title>{name} {months[i]}: {v:.1f}% di ore da generici</title></circle>')
        out.append(f'<text x="{x(len(series) - 1) + 10:.1f}" y="{y(series[-1]) + 4:.1f}" font-size="11.5" '
                   f'font-weight="600" fill="#0b0b0b">{name} {series[-1]:.1f}%</text>')
    out.append("</svg>")
    return "".join(out)


def dumbbell(drift, label):
    """Per-teacher temporal centroid, golden -> run. Identity on the y axis, so: dots."""
    rows = sorted(drift, key=lambda r: r[1])
    w, row_h, pad_l = 700, 20, 132
    h = len(rows) * row_h + 26
    lo, hi = 40, 200
    def x(d): return pad_l + (d - lo) * (w - pad_l - 70) / (hi - lo)
    out = [f'<svg viewBox="0 0 {w} {h}" width="{w}" height="{h}" role="img">']
    for d in range(50, hi + 1, 25):
        out.append(f'<line x1="{x(d):.1f}" y1="14" x2="{x(d):.1f}" y2="{h - 10}" stroke="#eceae5"/>')
    for i, (teacher, delta, cg, cr) in enumerate(rows):
        yy = 24 + i * row_h
        out.append(f'<text x="{pad_l - 10}" y="{yy + 4}" text-anchor="end" font-size="11" fill="#52514e">{teacher}</text>')
        out.append(f'<line x1="{x(cg):.1f}" y1="{yy}" x2="{x(cr):.1f}" y2="{yy}" stroke="#dedcd7" stroke-width="2"/>')
        out.append(f'<circle cx="{x(cg):.1f}" cy="{yy}" r="4.5" fill="{GOLD_COLOR}"><title>golden: giorno {cg:.0f}</title></circle>')
        out.append(f'<circle cx="{x(cr):.1f}" cy="{yy}" r="4.5" fill="{RUN_COLOR}" stroke="#fcfcfb" stroke-width="1.5">'
                   f'<title>run: giorno {cr:.0f} ({delta:+.0f} giorni)</title></circle>')
        out.append(f'<text x="{w - 60}" y="{yy + 4}" font-size="11" fill="{"#b23a10" if delta < -5 else "#52514e"}">{delta:+.0f}g</text>')
    out.append(f'<text x="{pad_l}" y="{h - 1}" font-size="10" fill="#8a887f">{label}, giorni dal 14/09/2026</text></svg>')
    return "".join(out)


def html_table(title, buckets_gold, buckets_run):
    rows = [f"<h2>{title}</h2><table><tr><th></th><th>golden</th><th>run</th><th>delta</th><th>ore G</th><th>ore R</th></tr>"]
    for bucket in sorted(set(buckets_gold) | set(buckets_run), key=str):
        g, r = buckets_gold.get(bucket, [0, 0]), buckets_run.get(bucket, [0, 0])
        pg = g[0] * 100 / sum(g) if sum(g) else 0
        pr = r[0] * 100 / sum(r) if sum(r) else 0
        cls = "neg" if pr - pg <= -2 else ("pos" if pr - pg >= 2 else "")
        rows.append(f"<tr><td>{bucket}</td><td>{pg:.1f}%</td><td>{pr:.1f}%</td>"
                    f'<td class="{cls}">{pr - pg:+.1f}</td><td>{sum(g)}</td><td>{sum(r)}</td></tr>')
    return "".join(rows) + "</table>"


def write_html(path, run_path, by_month_g, by_month_r, by_slot_g, by_slot_r, by_fam_g, by_fam_r, gen_drift, spec_drift):
    months = sorted(set(by_month_g) | set(by_month_r))
    pct = lambda b, k: (b[k][0] * 100 / sum(b[k])) if k in b and sum(b[k]) else 0
    gold_pct = [pct(by_month_g, m) for m in months]
    run_pct = [pct(by_month_r, m) for m in months]
    mean_gen = statistics.mean(d for _, d, _, _ in gen_drift) if gen_drift else 0
    mean_spec = statistics.mean(d for _, d, _, _ in spec_drift) if spec_drift else 0
    legend = (f'<div class="legend"><span><span class="sw" style="background:{GOLD_COLOR}"></span>golden</span>'
              f'<span><span class="sw" style="background:{RUN_COLOR}"></span>run</span></div>')
    with open(path, "w") as fh:
        fh.write(HTML_HEAD)
        fh.write("<h1>Ritmo di consumo dei tier docenti, golden contro run</h1>")
        fh.write(f'<p class="sub">Un docente e\' <b>generico</b> quando serve molti gruppi, quindi puo\' tappare un buco '
                 f'quasi ovunque, <b>specializzato</b> altrimenti. Confronto su <code>{os.path.basename(run_path)}</code>, '
                 f'date di stage escluse. Le percentuali sono la quota di ore erogate da generici.</p>')
        fh.write("<h2>Il ritmo nel corso dell'anno</h2>")
        fh.write('<p class="sub">La scuola tiene la quota piatta da settembre a giugno. Una curva che parte sopra e '
                 'finisce sotto significa che il docente flessibile viene speso presto e a fine anno resta un residuo '
                 'di specializzati, poco sostituibili.</p>')
        fh.write(legend + line_chart(months, gold_pct, run_pct))
        fh.write(html_table("Numeri per mese", by_month_g, by_month_r))
        fh.write("<h2>Dove finiscono nella giornata</h2>")
        fh.write(f'<p class="sub">Slot 0 = 08:00. Uno scarto forte all\'ultima ora del mattino dice che quella fascia '
                 f'viene riempita con la classe sbagliata di docente.</p>{legend}')
        fh.write(html_table("Quota generici per slot", by_slot_g, by_slot_r))
        fh.write(html_table("Controllo di sanita': quota per famiglia (invariante di input, deve coincidere)", by_fam_g, by_fam_r))
        fh.write("<h2>Baricentro temporale per docente</h2>")
        fh.write(f'<p class="sub">Giorno mediano delle ore di ciascun docente. Scarto medio: generici '
                 f'<b>{mean_gen:+.1f}</b> giorni, specializzati <b>{mean_spec:+.1f}</b>.</p>{legend}')
        fh.write(dumbbell(gen_drift, "generici") + dumbbell(spec_drift, "specializzati"))
        fh.write("</body></html>")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--html", metavar="OUT.html", help="write the visual report as well")
    ap.add_argument("run_lessons", help="canonical run dump group|date|hour|subject|teacher|pinned")
    ap.add_argument("--data-dir", default="claudedocs/spalmone-2026-27/data",
                    help="directory holding incarichi.csv, lessons.csv and the calendar sheets")
    ap.add_argument("--generic-min", type=int, default=8,
                    help="minimum number of groups for a teacher to count as generic (default 8)")
    ap.add_argument("--keep-stage", action="store_true", help="do not exclude stage dates")
    args = ap.parse_args()

    generic, specialised, spread = read_generic_teachers(args.data_dir, args.generic_min)
    print(f"generici (>= {args.generic_min} gruppi): {len(generic)}   specializzati: {len(specialised)}")
    print("generici: " + ", ".join(f"{t} ({len(spread[t])})" for t in sorted(generic, key=lambda t: -len(spread[t]))))

    gold = list(read_golden(args.data_dir))
    run = list(read_run(args.run_lessons))

    # A teacher absent from the incarichi facts cannot be classified, so counting their hours
    # would silently land them among the specialised. The golden has one such name, "Fasullo"
    # (Italian for "bogus"): 112 rows, teacher AND subject both "Fasullo", 8 hours x 14 groups
    # on Saturday 2026-09-19, a placeholder block the school's planners left behind. Left in,
    # it moved the golden's September generic share by nearly 6 points. Drop and say so.
    unknown = collections.Counter(t for _, _, _, t in gold + run if t not in spread)
    if unknown:
        print("docenti non classificabili (assenti dagli incarichi), ESCLUSI: "
              + ", ".join(f"{t} ({n} ore)" for t, n in unknown.most_common()))
        gold = [r for r in gold if r[3] in spread]
        run = [r for r in run if r[3] in spread]
    if not args.keep_stage:
        stage = read_stage_dates(args.data_dir)
        before = len(gold), len(run)
        gold = [r for r in gold if r[0] not in stage[r[2]]]
        run = [r for r in run if r[0] not in stage[r[2]]]
        print(f"date di stage escluse: -{before[0] - len(gold)} ore golden, -{before[1] - len(run)} ore run")
    print(f"ore confrontate: golden {len(gold)}, run {len(run)}")

    for title, key, width in (
        ("Quota di ore erogate da docenti GENERICI, per famiglia", lambda r: family_of(r[2]), 9),
        ("Quota generici per slot della giornata (0 = 08:00)", lambda r: r[1], 9),
        ("Quota generici per mese - il ritmo di consumo, qui sta il segnale", lambda r: r[0][:7], 9),
    ):
        print_table(title, share(gold, key, generic), share(run, key, generic), width)

    cg, cr = centroids(gold), centroids(run)
    drifts = {}
    print("\nBaricentro temporale per docente (giorno mediano delle sue ore, 0 = 14/09/2026)")
    for label, group in (("GENERICI", generic), ("SPECIALIZZATI", specialised)):
        drift = [(t, cr[t] - cg[t], cg[t], cr[t]) for t in group if t in cg and t in cr]
        drifts[label] = drift
        if not drift:
            continue
        print(f"  {label:<15} scarto medio run-golden {statistics.mean(d for _, d, _, _ in drift):+5.1f} giorni (n={len(drift)})")
        for teacher, delta, day_g, day_r in sorted(drift, key=lambda r: r[1])[:3]:
            print(f"      {teacher:<15}{day_g:>5.0f} -> {day_r:>5.0f}  {delta:>+6.0f}g")

    if args.html:
        by = lambda rows, key: share(rows, key, generic)
        write_html(args.html, args.run_lessons,
                   by(gold, lambda r: r[0][:7]), by(run, lambda r: r[0][:7]),
                   by(gold, lambda r: r[1]), by(run, lambda r: r[1]),
                   by(gold, lambda r: family_of(r[2])), by(run, lambda r: family_of(r[2])),
                   drifts["GENERICI"], drifts["SPECIALIZZATI"])
        print(f"\nreport visivo: {args.html}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
