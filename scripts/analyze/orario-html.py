#!/usr/bin/env python3
"""Orario HTML per gruppo (maintainer 2026-07-30, "fammi vedere un orario").

Genera un artefatto HTML leggibile PER GRUPPO da un export.json.gz di un run,
la cosa che un preside apre e capisce in dieci secondi: una griglia settimanale
(giorni in colonna Lun-Ven, ore in riga 08:00-15:30), con in ogni cella
materia, docente e aula. Niente cruscotti KPI, niente confronto col golden:
solo l'orario come lo vede una scuola.

Scelte di fedelta' (verificate sul run 30d, vedi CLAUDE.md):
- L'aula mostrata e' il valore di piano `room` dell'export. E' la variabile
  genuina: None significa davvero "nessuna aula assegnata a questa lezione".
- Una cella e' flaggata "SENZA AULA" (giallo) SOLO se la lezione e' collocata,
  senza aula, NON pinned e NON Stage. Sono i soli deficit reali di assegnazione
  stanza (H29): 167 nel run 30d. I pinned senza aula (lab MODA pre-assegnati,
  Stage off-site) non sono fallimenti del solver: la stanza e' implicita o
  assente per natura, quindi si mostrano senza allarme (punto faint al posto
  dell'aula).
- Stage e' off-site (albergo/azienda): cella grigia distinta, nessuna aula,
  nessun allarme.
- Le celle vuote (slot atteso ma senza lezione) restano visibilmente vuote,
  con un fondo rosa leggero per distinguerle dagli slot non pertinenti. Non si
  inventano riempimenti.

Navigazione: 36 settimane ISO, un selettore (e bottoni prec/succ); ogni settimana
e' pre-renderizzata e mostrata/nascosta da JS puramente DOM (niente parsing
runtime, funziona anche da file://).

Usage:
  python3 scripts/analyze/orario-html.py <export.json.gz> -o <outdir>
  python3 scripts/analyze/orario-html.py \\
      claudedocs/spalmone-2026-27/certification-2026-07-30d/export.json.gz \\
      -o claudedocs/spalmone-2026-27/orario
"""
import argparse
import gzip
import html
import json
import os
import sys
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

# Reuse the package's authoritative constants (mirrored off the Java
# TimeslotSequence source of truth) instead of redefining them here.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shared import SLOT_START_TIMES, WEEKDAY_ORDER  # noqa: E402

WEEKDAYS = WEEKDAY_ORDER
SLOT_TIMES = SLOT_START_TIMES
# Italian weekday abbreviations for the grid headers (local to this report).
WD_IT = {"MONDAY": "Lun", "TUESDAY": "Mar", "WEDNESDAY": "Mer",
         "THURSDAY": "Gio", "FRIDAY": "Ven"}


def load_export(path):
    """Read export.json or export.json.gz into a dict."""
    op = Path(path)
    raw = op.read_bytes()
    if str(path).endswith(".gz"):
        raw = gzip.decompress(raw)
    return json.loads(raw)


def iso_week_key(d):
    """(iso_year, iso_week) so weeks sort correctly across the year boundary."""
    return d.isocalendar()[0], d.isocalendar()[1]


def monday_of(iso_year, iso_week):
    return date.fromisocalendar(iso_year, iso_week, 1)


def group_lessons_by_cell(data):
    """Return {gid: {(date, slot_time): [lesson, ...]}} for placed, on-grid lessons."""
    by_group = defaultdict(lambda: defaultdict(list))
    for l in data["lessons"]:
        ts = l.get("timeslot")
        if ts is None:
            continue  # unplaced (boxed) -> not on the grid
        g = l["studentGroup"]
        gid = g["id"] if isinstance(g, dict) else g
        slot_time = ts["startDateTime"][11:16]
        d = date.fromisoformat(ts["date"])
        if slot_time not in SLOT_TIMES:
            continue  # off-grid time (shouldn't happen, defensive)
        by_group[gid][(d, slot_time)].append(l)
    return by_group


def _gid(v):
    """Coerce a nested {id:..} export entity (group/teacher/room) to its id."""
    return v["id"] if isinstance(v, dict) else v


def _is_stage(l):
    """A lesson belongs to the off-site Stage pseudo-entity.

    Keyed off the pseudo-teacher flag (the solver's own exemption notion), not
    the display subject string, so a rename or a second pseudo entity keeps
    working. Falls back to the subject name if the flag is not serialized.
    """
    t = l.get("teacher")
    if isinstance(t, dict) and t.get("pseudo"):
        return True
    return l.get("subject") == "Stage"


def compute_all_stats(data):
    """Every group's headline numbers in a single pass over lessons.

    attese = the school's in-grid plan (DailyActivityQuota aula+lab), capped by
    the courses' own total demand: a plan exceeding what the incarichi actually
    ask for is a data artifact (G14, whose quota sheet is decoupled from its
    courses), so the course total wins and the page prints no bogus gap.
    """
    quota = defaultdict(int)
    for q in data.get("dailyActivityQuotas", []):
        quota[_gid(q["studentGroup"])] += q["aulaHours"] + q["labHours"]
    course_total = defaultdict(int)
    for c in data["courses"]:
        course_total[_gid(c["studentGroup"])] += c["totalHours"]

    placed_nostage = defaultdict(int)
    stage = defaultdict(int)
    senza_aula = defaultdict(int)
    unplaced = defaultdict(int)
    for l in data["lessons"]:
        gid = _gid(l["studentGroup"])
        if l.get("timeslot") is None:
            unplaced[gid] += 1
            continue
        if _is_stage(l):
            stage[gid] += 1
            continue
        placed_nostage[gid] += 1
        if l.get("room") is None and not l.get("pinned"):
            senza_aula[gid] += 1

    out = {}
    for g in data["studentGroups"]:
        gid = g["id"]
        q = quota.get(gid, 0)
        ct = course_total.get(gid, 0)
        attese = min(q, ct) if (q and ct) else (q or ct)
        out[gid] = {
            "attese": attese,
            "collocate": placed_nostage.get(gid, 0),
            "senza_aula": senza_aula.get(gid, 0),
            "stage": stage.get(gid, 0),
            "unplaced": unplaced.get(gid, 0),
        }
    return out


def weeks_for_group(by_cell, gid):
    """Ordered list of (iso_year, iso_week, monday_date) with >=1 lesson for the group."""
    weeks = set()
    for (d, _t) in by_cell[gid].keys():
        weeks.add(iso_week_key(d))
    out = []
    for (iy, iw) in sorted(weeks):
        out.append((iy, iw, monday_of(iy, iw)))
    return out


def render_cell(lessons):
    """Build the inner HTML for one (date, slot) cell given its lessons (usually 1)."""
    if not lessons:
        return '<td class="cell empty" title="Slot vuoto"></td>'
    parts = []
    for l in lessons:
        subj = l.get("subject") or "-"
        teacher = l.get("teacher")
        tname = teacher["name"] if isinstance(teacher, dict) else (teacher or "-")
        room = l.get("room")
        rname = room["name"] if isinstance(room, dict) else None
        pinned = bool(l.get("pinned"))
        is_stage = _is_stage(l)

        if is_stage:
            parts.append(
                '<div class="lesson stage"><span class="subj">Stage</span>'
                '<span class="tch">off-site</span></div>')
            continue

        room_html = ""
        cls = "lesson"
        if rname:
            room_html = f'<span class="room">{html.escape(rname)}</span>'
        elif not pinned:
            # genuine room deficit (H29): flag it
            cls += " noroom"
            room_html = '<span class="room noroom-flag">SENZA AULA</span>'
        else:
            # pre-assigned (pinned) without a stored room: implicit, no alarm
            room_html = '<span class="room implicit">&middot;</span>'
        parts.append(
            f'<div class="{cls}"><span class="subj">{html.escape(subj)}</span>'
            f'<span class="tch">{html.escape(tname)}</span>{room_html}</div>')
    return f'<td class="cell">{"".join(parts)}</td>'


def render_week_table(iy, iw, monday, by_cell, gid, holidays):
    """One week: 5 day-columns x 8 slot-rows. Days as columns, hours as rows."""
    dates = [monday + timedelta(days=i) for i in range(5)]  # Mon..Fri

    head = ['<tr><th class="corner">ora</th>']
    for d in dates:
        wd = WEEKDAYS[d.weekday()]
        is_hol = d in holidays
        hol_cls = " hol" if is_hol else ""
        head.append(
            f'<th class="day{hol_cls}">{WD_IT[wd]}<span class="dnum">'
            f'{d.day}/{d.month}</span></th>')
    head.append("</tr>")

    rows = []
    for st in SLOT_TIMES:
        row = [f'<tr><th class="slot">{st}</th>']
        for d in dates:
            cell_lessons = by_cell[gid].get((d, st), [])
            if d in holidays and not cell_lessons:
                row.append(
                    '<td class="cell holiday" title="Giorno di chiusura">vacanza</td>')
            else:
                row.append(render_cell(cell_lessons))
        row.append("</tr>")
        rows.append("".join(row))
    week_label = (f"Settimana {iw}: {dates[0].strftime('%d/%m')} "
                  f"al {dates[4].strftime('%d/%m')}")
    return (
        f'<div class="week" id="week-{iy}-{iw}">'
        f'<div class="week-label">{html.escape(week_label)}</div>'
        f'<table class="grid"><thead>{"".join(head)}</thead>'
        f'<tbody>{"".join(rows)}</tbody></table>'
        f'</div>'
    )


PAGE_CSS = """
* { box-sizing: border-box; }
body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
       margin: 0; background: #f8fafc; color: #0f172a; }
header.top { background: #0f172a; color: #f8fafc; padding: 14px 20px; }
header.top h1 { margin: 0 0 4px 0; font-size: 20px; }
header.top a { color: #93c5fd; text-decoration: none; }
.sub { font-size: 13px; color: #cbd5e1; }
.stats { display: flex; gap: 10px; flex-wrap: wrap; padding: 12px 20px; background: #fff;
         border-bottom: 1px solid #e2e8f0; }
.stat { background: #f1f5f9; border-radius: 8px; padding: 8px 14px; min-width: 120px; }
.stat .k { font-size: 11px; text-transform: uppercase; letter-spacing: .04em; color: #64748b; }
.stat .v { font-size: 20px; font-weight: 700; }
.stat.warn .v { color: #b45309; }
.stat.stage .v { color: #475569; }
.stat.gap .v { color: #b91c1c; }
.controls { display: flex; align-items: center; gap: 8px; padding: 10px 20px; background: #fff;
            border-bottom: 1px solid #e2e8f0; }
.controls button { padding: 6px 12px; border: 1px solid #cbd5e1; background: #fff; border-radius: 6px;
                   cursor: pointer; font-size: 14px; }
.controls button:hover { background: #f1f5f9; }
.controls select { padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 14px; }
.legend { display: flex; gap: 16px; flex-wrap: wrap; padding: 10px 20px; font-size: 12px; color: #475569;
          background: #fff; border-bottom: 1px solid #e2e8f0; }
.legend .sw { display: inline-block; width: 14px; height: 14px; border-radius: 3px; margin-right: 5px;
              vertical-align: middle; border: 1px solid #cbd5e1; }
main { padding: 16px 20px 60px; }
.week { display: none; }
.week.active { display: block; }
.week-label { font-size: 15px; font-weight: 600; margin: 4px 0 10px; color: #334155; }
table.grid { border-collapse: collapse; width: 100%; background: #fff; }
table.grid th, table.grid td { border: 1px solid #e2e8f0; }
th.corner, th.slot { background: #f1f5f9; font-weight: 600; color: #334155; }
th.corner { width: 56px; }
th.slot { width: 56px; padding: 6px; font-size: 12px; text-align: center; }
th.day { padding: 6px; text-align: center; font-size: 13px; min-width: 150px; }
th.day .dnum { display: block; font-weight: 400; color: #64748b; font-size: 11px; }
th.day.hol { background: #fee2e2; }
td.cell { vertical-align: top; padding: 4px 5px; height: 58px; }
td.cell.empty { background: #fff1f2; }
td.cell.holiday { background: #f8fafc; color: #94a3b8; font-size: 11px; text-align: center;
                  font-style: italic; }
.lesson { display: flex; flex-direction: column; line-height: 1.15; }
.lesson .subj { font-weight: 600; font-size: 12.5px; color: #0f172a; }
.lesson .tch { font-size: 11px; color: #64748b; }
.lesson .room { font-size: 11px; color: #0369a1; }
.lesson.noroom { background: #fef9c3; border-radius: 4px; padding: 2px 3px; margin: -2px -3px; }
.lesson.noroom .noroom-flag { color: #b45309; font-weight: 700; }
.lesson .room.implicit { color: #cbd5e1; }
.lesson.stage { background: #e2e8f0; border-radius: 4px; padding: 2px 3px; margin: -2px -3px; }
.lesson.stage .subj { color: #475569; }
.lesson.stage .tch { color: #94a3b8; font-style: italic; }
ul.groups { list-style: none; padding: 0; margin: 0; display: grid;
            grid-template-columns: repeat(auto-fill, minmax(220px,1fr)); gap: 10px; }
ul.groups li a { display: block; padding: 14px; background: #fff; border: 1px solid #e2e8f0;
                 border-radius: 8px; text-decoration: none; color: #0f172a; }
ul.groups li a:hover { border-color: #93c5fd; background: #f8fafc; }
ul.groups li .g { font-weight: 700; font-size: 16px; }
ul.groups li .m { font-size: 12px; color: #64748b; margin-top: 4px; }
.note { padding: 10px 20px; font-size: 12px; color: #64748b; background: #fff;
        border-bottom: 1px solid #e2e8f0; }
"""

NAV_JS = """
function initWeekNav(){
  var weeks = Array.prototype.slice.call(document.querySelectorAll('.week'));
  if(!weeks.length) return;
  var sel = document.getElementById('week-select');
  function show(id){
    weeks.forEach(function(w){ w.classList.toggle('active', w.id===id); });
    if(sel) sel.value = id;
    var idx = weeks.findIndex(function(w){return w.id===id;});
    document.getElementById('btn-prev').disabled = idx<=0;
    document.getElementById('btn-next').disabled = idx>=weeks.length-1;
  }
  weeks.forEach(function(w){ w.classList.remove('active'); });
  show(weeks[0].id);
  if(sel) sel.addEventListener('change', function(){ show(sel.value); });
  document.getElementById('btn-prev').addEventListener('click', function(){
    var cur = weeks.find(function(w){return w.classList.contains('active');});
    var idx = weeks.indexOf(cur); if(idx>0) show(weeks[idx-1].id);
  });
  document.getElementById('btn-next').addEventListener('click', function(){
    var cur = weeks.find(function(w){return w.classList.contains('active');});
    var idx = weeks.indexOf(cur); if(idx<weeks.length-1) show(weeks[idx+1].id);
  });
}
document.addEventListener('DFContentLoaded', initWeekNav);
"""


def group_page(data, group, stats, by_cell, holidays, run_id):
    gid = group["id"]
    name = group["name"]
    weeks = weeks_for_group(by_cell, gid)
    gap = stats["attese"] - stats["collocate"]

    week_blocks = "\n".join(
        render_week_table(iy, iw, mon, by_cell, gid, holidays) for (iy, iw, mon) in weeks)
    options = "\n".join(
        f'<option value="week-{iy}-{iw}">Settimana {iw}: '
        f'{mon.strftime("%d/%m")} al {(mon+timedelta(days=4)).strftime("%d/%m")}</option>'
        for (iy, iw, mon) in weeks)

    gap_stat = (
        '<div class="stat gap"><div class="k">Gap attese</div>'
        f'<div class="v">{gap:+d}</div></div>') if gap != 0 else ""

    unplaced_stat = (
        '<div class="stat warn"><div class="k">Non collocate</div>'
        f'<div class="v">{stats["unplaced"]}</div></div>') if stats["unplaced"] else ""

    return f"""<!doctype html>
<html lang="it"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Orario {html.escape(name)} ({html.escape(run_id)})</title>
<style>{PAGE_CSS}</style></head>
<body>
<header class="top">
  <h1>Orario: {html.escape(name)}</h1>
  <div class="sub">Run {html.escape(run_id)} &middot; <a href="index.html">&larr; tutti i gruppi</a></div>
</header>
<div class="stats">
  <div class="stat"><div class="k">Ore attese</div><div class="v">{stats["attese"]}</div></div>
  <div class="stat"><div class="k">Ore collocate</div><div class="v">{stats["collocate"]}</div></div>
  <div class="stat warn"><div class="k">Senza aula</div><div class="v">{stats["senza_aula"]}</div></div>
  {gap_stat}
  <div class="stat stage"><div class="k">Ore di stage</div><div class="v">{stats["stage"]}</div></div>
  {unplaced_stat}
</div>
<div class="note">"Senza aula" = lezione collocata ma senza stanza assegnata (deficit reale).
Le celle rosa sono slot vuoti attesi. Stage = off-site. Lezioni pinned senza aula (lab
pre-assegnati) mostrano un punto, senza allarme.</div>
<div class="controls">
  <button id="btn-prev">&#9664; prec</button>
  <select id="week-select">{options}</select>
  <button id="btn-next">succ &#9654;</button>
  <span style="margin-left:auto;color:#94a3b8;font-size:12px">{len(weeks)} settimane</span>
</div>
<div class="legend">
  <span><span class="sw" style="background:#fff1f2"></span>slot vuoto</span>
  <span><span class="sw" style="background:#fef9c3"></span>senza aula</span>
  <span><span class="sw" style="background:#e2e8f0"></span>stage</span>
  <span><span class="sw" style="background:#fff"></span>lezione</span>
</div>
<main>
{week_blocks}
</main>
<script>{NAV_JS}</script>
</body></html>"""


def index_page(data, group_stats, run_id, src):
    items = []
    for g in sorted(data["studentGroups"], key=lambda x: x["name"]):
        s = group_stats[g["id"]]
        items.append(
            f'<li><a href="{slug(g["name"])}.html">'
            f'<div class="g">{html.escape(g["name"])}</div>'
            f'<div class="m">attese {s["attese"]} &middot; collocate {s["collocate"]} '
            f'&middot; senza aula {s["senza_aula"]} &middot; stage {s["stage"]}'
            f'{(" &middot; " + str(s["unplaced"]) + " non collocate") if s["unplaced"] else ""}</div>'
            f'</a></li>')
    return f"""<!doctype html>
<html lang="it"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Orari scolastici ({html.escape(run_id)})</title>
<style>{PAGE_CSS}</style></head>
<body>
<header class="top">
  <h1>Orari scolastici ({html.escape(run_id)})</h1>
  <div class="sub">Seleziona un gruppo &middot; fonte: {html.escape(src)}</div>
</header>
<div class="note">Griglia settimanale (giorni in colonna, ore in riga) con materia, docente e aula
per ogni cella. "Senza aula" = deficit reale di stanza. Totale senza aula su tutti i gruppi:
<b>{sum(s["senza_aula"] for s in group_stats.values())}</b> ore.</div>
<main style="padding:16px 20px">
<ul class="groups">
{''.join(items)}
</ul>
</main>
</body></html>"""


def slug(name):
    return "".join(c if c.isalnum() or c in "-_" else "_" for c in name)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("export", help="export.json[.gz] of the run")
    ap.add_argument("-o", "--outdir", required=True, help="output directory")
    ap.add_argument("--run-id", default=None,
                    help="label for the run (default: parent dir name)")
    args = ap.parse_args()

    data = load_export(args.export)
    run_id = args.run_id or Path(args.export).parent.name
    out = Path(args.outdir)
    out.mkdir(parents=True, exist_ok=True)

    holidays = {date.fromisoformat(h["date"]) for h in data.get("schoolHolidays", [])}
    by_cell = group_lessons_by_cell(data)
    group_stats = compute_all_stats(data)

    for g in data["studentGroups"]:
        page = group_page(data, g, group_stats[g["id"]], by_cell, holidays, run_id)
        (out / f"{slug(g['name'])}.html").write_text(page, encoding="utf-8")

    (out / "index.html").write_text(
        index_page(data, group_stats, run_id, str(args.export)), encoding="utf-8")

    tot_senza = sum(s["senza_aula"] for s in group_stats.values())
    print(f"Wrote {len(data['studentGroups'])} group pages + index to {out}")
    print(f"Total 'senza aula' across groups: {tot_senza} (run 30d baseline: 167)")
    for g in sorted(data["studentGroups"], key=lambda x: x["name"]):
        s = group_stats[g["id"]]
        print(f"  {g['name']:8} attese={s['attese']:4} collocate={s['collocate']:4} "
              f"senza_aula={s['senza_aula']:3} stage={s['stage']:4} unplaced={s['unplaced']}")


if __name__ == "__main__":
    main()
