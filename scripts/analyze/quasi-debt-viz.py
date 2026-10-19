#!/usr/bin/env python3
"""Viz HTML del debito quasi: golden vs baseline(flag OFF) vs run(flag ON) sulle celle oltre-golden.

Regola di casa: valori SULLE celle, niente hover-only. Ogni riga e' una cella
(gruppo, data) del debito oltre-golden con le ore collocate da golden, baseline
v2.1 e il run quasi; il colore marca chi e' sotto il golden. Serve l'arbitraggio
di maintainer: mostra che la differenza fra le due soluzioni e' esattamente la mappa
del debito, non una degradazione diffusa.

Usage:
  quasi-debt-viz.py --run <lessons.txt> [--baseline <lessons.txt>] [--out out.html]
"""
import argparse
import html
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import importlib.util  # noqa: E402

_spec = importlib.util.spec_from_file_location(
    "quasi_debt_map", Path(__file__).with_name("quasi-debt-map.py"))
assert _spec is not None and _spec.loader is not None, "quasi-debt-map.py non trovato"
_qdm = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_qdm)
EXCLUDED_GROUPS, TOLERANCE = _qdm.EXCLUDED_GROUPS, _qdm.TOLERANCE
debt_map, load_declared_quota, placed_non_stage_hours = (
    _qdm.debt_map, _qdm.load_declared_quota, _qdm.placed_non_stage_hours)
from shared import data_dir_for_year, parse_lessons_csv, parse_lessons_pipe  # noqa: E402


def hours_by_cell(rows):
    return placed_non_stage_hours(rows)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--run", required=True)
    ap.add_argument("--baseline", help="lessons.txt della consegna flag OFF (baseline v2.1)")
    ap.add_argument("--golden-ref", action="store_true", default=True)
    ap.add_argument("--year", default="2026-27")
    ap.add_argument("--out", default="quasi-debt-viz.html")
    args = ap.parse_args()

    data_dir = Path(data_dir_for_year(args.year))
    quota = load_declared_quota(data_dir)
    golden_rows = list(parse_lessons_csv(data_dir / "lessons.csv", include_hour=True))
    golden_cells = hours_by_cell(golden_rows)
    golden_debt = debt_map(golden_rows, quota)
    golden_debt_cells = {(g, d) for g, v in golden_debt.items() for d, _x, _y, _z in v["detail"]}

    run_cells = hours_by_cell(list(parse_lessons_pipe(args.run, True)))
    base_cells = hours_by_cell(list(parse_lessons_pipe(args.baseline, True))) if args.baseline else {}

    rows_html = []
    per_group = {}
    for (group, date), declared in sorted(quota.items()):
        if group in EXCLUDED_GROUPS or (group, date) in golden_debt_cells:
            continue
        g = run_cells.get((group, date), 0)
        gold = golden_cells.get((group, date), 0)
        tol = TOLERANCE.get(group, 0)
        deficit = declared - g - tol
        if deficit <= 0:
            continue
        base = base_cells.get((group, date))
        per_group.setdefault(group, []).append((date, declared, gold, base, g))

    def cell(v, ref):
        if v is None:
            return "<td class=na>n/a</td>"
        cls = "bad" if ref is not None and v < ref else ""
        return f"<td class='{cls}'>{v}</td>"

    for group in sorted(per_group):
        rows_html.append(f"<tr class=grp><td colspan=5>{html.escape(group)}</td></tr>")
        for date, declared, gold, base, g in per_group[group]:
            rows_html.append(
                f"<tr><td>{date}</td><td>{declared}</td>"
                f"{cell(gold, None)}{cell(base, gold)}{cell(g, gold)}</tr>")

    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(f"""<!doctype html><html><head><meta charset=utf-8><title>Debito quasi oltre-golden</title>
<style>
body{{font-family:system-ui;margin:24px;background:#141619;color:#dfe3e8}}
table{{border-collapse:collapse;font-size:14px}}
td,th{{border:1px solid #2c313a;padding:4px 12px;text-align:right}}
td:first-child,th:first-child{{text-align:left}}
tr.grp td{{background:#1f242c;font-weight:600;text-align:left}}
td.bad{{background:#5c2b2b;color:#ffd9d9;font-weight:700}}
td.na{{color:#666}}
th{{background:#1a1e24}}
h1{{font-size:18px}} p{{color:#9aa3ad;max-width:70ch}}
</style></head><body>
<h1>Celle di debito oltre-golden: ore collocate per data</h1>
<p>Colonne: dichiarate (foglio), golden, baseline v2.1 (flag OFF), run quasi (flag ON).
Rosso = sotto il golden. Le celle condivise col golden (G13 foglio-fantasma, meta'-giugno) sono escluse:
la tabella e' solo il debito OLTRE la pratica della scuola.</p>
<table><tr><th>data</th><th>dichiarate</th><th>golden</th><th>baseline OFF</th><th>run ON</th></tr>
{''.join(rows_html)}
</table></body></html>""")
    n = sum(len(v) for v in per_group.values())
    print(f"{args.out}: {n} celle oltre-golden, {len(per_group)} gruppi")


if __name__ == "__main__":
    main()
