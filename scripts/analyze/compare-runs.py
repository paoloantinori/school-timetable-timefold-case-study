#!/usr/bin/env python3
"""compare-runs.py - TASK-534: confronto run-vs-run con vettore KPI normalizzato.

Nessuno scalare unico (lo score e' gia' il nostro scalare, e un secondo scalare
pesato ricreerebbe la compensabilita'). Forma: vettore di KPI con POLARITA'
dichiarata per dimensione + dominanza Pareto + dimensione-piu'-grave (max-min,
coerente con la filosofia 0hard-binario: la dimensione peggiore vincola).

Dimensioni e polarita' (dal task):
  hard        target=0 (binario)
  full-day    target: massimo (banda campagna ~87,5)
  ricorrenza-unpinned  target: massimo (~75,3 source=golden)
  buchi H17   target=0
  celle sotto-quota in-scope H32  target=0
Usage: compare-runs.py <runDirA> <runDirB> [--out page.html]
Read-only. Legge i KPI della capture canonica (kpi-*.txt, score.txt, export).
"""
import argparse
import gzip
import html
import json
import os
import re
import sys
import time
from collections import defaultdict

BANDS = {
    # (verde_sopra/sotto, giallo) per dimensioni con polarita' "max"
    "full_day": (87.0, 84.0),
    "ricorrenza_unpinned": (74.0, 70.0),
    # polarita' "min", target 0
    "hard": (0, 50),
    "h17_holes": (0, 5),
    "under_quota": (0, 10),
}


def read_text(path):
    # Single-shot read: the laptop FS flake can hide a path for minutes, and a
    # retry loop turns a missing file into a multi-minute hang. A miss here is
    # reported as None in the vector and the WARN names the path.
    try:
        with open(path) as f:
            return f.read()
    except OSError as e:
        print(f"WARN: unreadable {path}: {e}", file=sys.stderr)
        return ""


def parse_run(d):
    out = {"dir": d}
    txt = read_text(os.path.join(d, "score.txt"))
    m = re.search(r"(-?\d+)hard", txt)
    out["hard"] = int(m.group(1)) if m else None
    fill = read_text(os.path.join(d, "kpi-fill.txt"))
    m = re.search(r"FULL-DAY RATE: \d+/\d+ = ([\d.]+)%", fill)
    out["full_day"] = float(m.group(1)) if m else None
    rem = read_text(os.path.join(d, "kpi-resemblance.txt"))
    m = re.search(r"ricorrenza UNPINNED: \d+/\d+ = ([\d.]+)%", rem)
    out["ricorrenza_unpinned"] = float(m.group(1)) if m else None
    holes = read_text(os.path.join(d, "kpi-holes.txt"))
    m = re.search(r"BUCO-INTERNO: (\d+) giorni", holes)
    out["h17_holes"] = int(m.group(1)) if m or "BUCO-INTERNO" in holes else None
    # under-quota in-scope H32 dall'export
    exp = os.path.join(d, "export.json.gz")
    if os.path.exists(exp):
        try:
            data = json.load(gzip.open(exp))
            quota = load_quota()
            per = defaultdict(int)
            for l in data.get("lessons", []):
                if l.get("timeslot") and (l.get("teacher") or {}).get("name") != "Stage":
                    per[((l.get("studentGroup") or {}).get("name"),
                         l["timeslot"]["date"][:10])] += 1
            under = 0
            for (g, dt), q in quota.items():
                if g in ("G04", "G08", "G14") or dt > "2027-06-10":
                    continue
                if (g, dt) in per and per[(g, dt)] < q - (1 if g == "G09" else 0):
                    under += 1
            out["under_quota"] = under
        except Exception as e:
            out["under_quota"] = None
    else:
        out["under_quota"] = None
    return out


SEED = os.path.join(os.path.dirname(__file__), "..", "..", "src/main/resources/seed")


def load_quota():
    import csv
    quota = {}
    for r in csv.DictReader(open(os.path.join(SEED, "calendario_fc.csv"))):
        h = int(r["aula_hours"]) + int(r["lab_hours"])
        if h > 0:
            quota[(r["group"], r["date"])] = h
    for r in csv.DictReader(open(os.path.join(SEED, "calendario_class_days.csv"))):
        if r["modality"] != "S" and int(r["hours"]) > 0:
            quota.setdefault((r["group"], r["date"]), int(r["hours"]))
    return quota


POLARITY = {
    "hard": "max",  # negative numbers: closer to 0 = better = larger
    "h17_holes": "min", "under_quota": "min",
    "full_day": "max", "ricorrenza_unpinned": "max",
}
LABEL = {
    "hard": "hard residuo (target 0)",
    "h17_holes": "buchi H17 (target 0)",
    "under_quota": "celle sotto-quota H32 in-scope (target 0)",
    "full_day": "full-day %",
    "ricorrenza_unpinned": "ricorrenza unpinned %",
}


def band(key, v):
    lo, hi = BANDS[key]
    pol = POLARITY[key]
    if v is None:
        return "?"
    if key == "hard":
        # hard is a negative-or-zero score: distance from 0 is what matters,
        # so band on the absolute value (0 green, <=50 yellow, worse red).
        a = abs(v)
        if a <= lo: return "verde"
        if a <= hi: return "giallo"
        return "rosso"
    if pol == "max":
        if v >= lo: return "verde"
        if v >= hi: return "giallo"
        return "rosso"
    else:
        if v <= lo: return "verde"
        if v <= hi: return "giallo"
        return "rosso"


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("runA")
    ap.add_argument("runB")
    ap.add_argument("--out")
    a = ap.parse_args()
    A, B = parse_run(a.runA), parse_run(a.runB)
    rows = []
    dominates_a = dominates_b = True
    worst_dim = None
    worst_gap = -1.0
    for k in POLARITY:
        va, vb = A.get(k), B.get(k)
        if va is None or vb is None:
            rows.append((k, va, vb, None, "?"))
            continue
        # polarity applied: positive = B BETTER on this dimension. Uniform
        # arithmetic: B minus A on the RAW value when larger is better (max),
        # A minus B when smaller is better (min). REVIEW FIXES (two sign bugs,
        # caught by hand-checking hard -104->-38 and holes 97->5): (1) hard is
        # a negative number where LESS NEGATIVE is better, i.e. larger-better:
        # treat it as max, not min; (2) the old min branch computed vb-va,
        # which makes B's improvement negative. Now min uses va-vb uniformly.
        delta = (vb - va) if POLARITY[k] == "max" else (va - vb)
        better = delta > 0
        if not better: dominates_a = False
        if delta < 0: dominates_b = False
        rows.append((k, va, vb, delta, better))
        norm = abs(delta) / max(1e-9, abs(va) if va != 0 else 1.0)
        if norm > worst_gap:
            worst_gap, worst_dim = norm, k
    html_out = ["<!doctype html><html><head><meta charset='utf-8'><title>compare-runs</title>",
                "<style>body{font:14px -apple-system,sans-serif;margin:24px}",
                "table{border-collapse:collapse}td,th{padding:6px 14px;border:1px solid #ddd}",
                ".verde{background:#d4edda}.giallo{background:#fff3cd}.rosso{background:#f8d7da}",
                ".better{color:#155724;font-weight:600}.worse{color:#721c24;font-weight:600}</style></head><body>",
                f"<h1>Confronto run-vs-run (vettore KPI, nessuno scalare)</h1>",
                f"<p>A: <code>{html.escape(a.runA)}</code> &middot; B: <code>{html.escape(a.runB)}</code></p>",
                "<table><tr><th>dimensione</th><th>A</th><th>B</th><th>delta (B-A, polarita'-gia'-applicata)</th><th>banda A</th><th>banda B</th></tr>"]
    for k, va, vb, delta, better in rows:
        html_out.append(
            f"<tr><td>{LABEL[k]}</td><td>{va}</td><td>{vb}</td>"
            f"<td class='{'better' if better is True else ('worse' if better is False else '')}'>"
            f"{'' if delta is None else f'{delta:+.1f}'}</td>"
            f"<td class='{band(k, va)}'>{band(k, va)}</td><td class='{band(k, vb)}'>{band(k, vb)}</td></tr>")
    html_out.append("</table>")
    dom = ("A domina B (Pareto)" if dominates_a else
           "B domina A (Pareto)" if dominates_b else "nessuna dominanza Pareto")
    html_out.append(f"<p><b>{dom}</b> &middot; dimensione-piu'-grave (max-min normalizzato): "
                    f"<b>{worst_dim}</b> (gap {worst_gap:.2f})</p>")
    html_out.append("<p style='color:#666;font-size:12px'>delta con polarita' applicata: positivo = B meglio di A su quella dimensione. "
                    "Nessuna media pesata: la comparazione e' il vettore, la sintesi e' Pareto + dimensione peggiore.</p></body></html>")
    page = "\n".join(html_out)
    if a.out:
        open(a.out, "w").write(page)
        print("written", a.out)
    else:
        print(page)


if __name__ == "__main__":
    main()
