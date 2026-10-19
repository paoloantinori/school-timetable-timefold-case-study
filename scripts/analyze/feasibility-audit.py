#!/usr/bin/env python3
"""
feasibility-audit.py — Mathematical lower-bound feasibility audit (TASK-321).

Answers: can the dataset reach 0 hard-constraint violations with 0 unassigned
lessons, or is it infeasible by construction? Computes the MAXIMUM (most generous)
capacity per resource dimension; any dimension where required hours exceed max
capacity is INFEASIBLE, and the gap is a lower bound on unplaceable hours.

Three dimensions (full school year, 2025-09-10 .. 2026-06-10):
  1. Per teacher:  required = sum(course.totalhours); available = slots on non-holiday
                   days the teacher is not on VACATION/SICK (external teachers' day
                   restrictions are encoded as VACATION records, so this captures them).
  2. Per group:    required = sum(course.totalhours); available = slots from the group's
                   start date on non-holiday days, capped per day by maxHoursPerDay (H11).
  3. Per room tag: required = sum(course.totalhours) of lab courses requiring the tag;
                   available = (#rooms with tag) x (total non-holiday slots).

Capacity is the generous maximum (stage blackouts, H1/H2/H3 conflicts, H7 daily cap
beyond the slot count are NOT subtracted) — so a violation here is a true lower bound.

All dates in this script are 'YYYY-MM-DD' ISO strings, which sort lexicographically,
so every date comparison is a plain string compare (no date parsing needed).
DB access goes through the whitelisted wrapper scripts/db/db-query.sh (not raw psql).
Output: claudedocs/research/feasibility-audit-YYYYMMDD.md + the report on stdout.

DO NOT modify DataGenerator.java (data fixes are TASK-337/m-6).
"""
import json
import os
import statistics
import subprocess
import sys
from collections import defaultdict
from datetime import date

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DB_QUERY = os.path.join(PROJECT_ROOT, "scripts", "db", "db-query.sh")
TODAY = date.today().isoformat()
REPORT = os.path.join(PROJECT_ROOT, "claudedocs", "research", f"feasibility-audit-{TODAY}.md")

# School-year bounds as ISO strings — string comparison is correct for YYYY-MM-DD.
# TASK-506: refreshed to the 2026/27 seed (2026-09-14 .. 2027-06-10; the old
# 2025/26 bounds made the whole audit mute on the current data).
YEARS_START = "2026-09-14"
YEARS_END = "2027-06-10"
H7_CAP = 6  # BaseConstraintProvider.MAX_TEACHER_DAILY_HOURS


def q(sql):
    """Run SQL via db-query.sh, wrapping it so we get back a JSON array of row objects."""
    wrapped = f"SELECT coalesce(json_agg(row_to_json(t)), '[]'::json) FROM ({sql}) t"
    res = subprocess.run([DB_QUERY, "-f", "json", wrapped], capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit(f"query failed (rc={res.returncode}):\n{res.stderr}\nSQL:\n{sql}")
    lines = [ln for ln in res.stdout.strip().split("\n") if ln.strip() and ln.strip() != "SET"]
    raw = lines[-1] if lines else "[]"
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        sys.exit(f"could not parse JSON from query:\nSQL:\n{sql}\nGOT:\n{raw!r}")


def in_year(d):
    """d is a 'YYYY-MM-DD' string; ISO dates sort lexicographically."""
    return YEARS_START <= d <= YEARS_END


# ---------------------------------------------------------------------------
# Load reference data
# ---------------------------------------------------------------------------
day_slots = {}                       # 'YYYY-MM-DD' -> slot count that day
for r in q("""SELECT to_char(startdatetime,'YYYY-MM-DD') AS d, count(*)::int AS slots
              FROM timeslot GROUP BY 1"""):
    day_slots[r["d"]] = int(r["slots"])
all_days = sorted(day_slots.keys())
total_slots = sum(day_slots.values())

holidays = set(r["d"] for r in
               q("SELECT to_char(date,'YYYY-MM-DD') AS d FROM schoolholiday"))
school_days = [d for d in all_days if d not in holidays]
school_day_slots = {d: day_slots[d] for d in school_days}
total_school_slots = sum(school_day_slots.values())

courses = q("SELECT id, subject, teacher_id, studentgroup_id, totalhours::int AS totalhours, islab FROM course")
teachers = {t["id"]: t for t in q("SELECT id, name, external FROM teacher WHERE pseudo = false")}
groups = {g["id"]: g for g in
          q("""SELECT id, name, maxhoursperday::int AS maxhoursperday,
                     to_char(startdate,'YYYY-MM-DD') AS sd FROM studentgroup""")}
name_to_gid = {g["name"]: gid for gid, g in groups.items()}

# Teacher VACATION/SICK as date ranges -> set of unavailable dates per teacher.
# startdatetime/enddatetime are timestamps; [:10] yields the 'YYYY-MM-DD' date.
avail = q("""SELECT teacher_id, type, startdatetime, enddatetime
             FROM teacheravailability WHERE type IN ('VACATION','SICK')""")
teacher_unavailable = defaultdict(set)   # teacher_id -> set of 'YYYY-MM-DD'
# TASK-506: T05/T04/T11 carry PART-DAY records (daily 14:30-16:30): a day is
# fully unavailable only when the record spans the whole school slot window (08:00..16:30).
# The old date-only logic read those three teachers as available=0 (false INFEASIBLE).
SCHOOL_OPEN = "08:00"
SCHOOL_CLOSE = "16:30"
for a in avail:
    s, e = str(a["startdatetime"]), str(a["enddatetime"])
    if not s or not e:
        continue
    sd, ed = s[:10], e[:10]
    st, et = s[11:16], e[11:16]
    whole_day = (sd != ed) or (st <= SCHOOL_OPEN and et >= SCHOOL_CLOSE)
    for d in all_days:
        if sd <= d <= ed and whole_day:
            teacher_unavailable[a["teacher_id"]].add(d)

# Room tags -> rooms per tag, and lab hours required per tag
room_tags = q("SELECT room_id, tags AS tag FROM room_tags")
rooms_per_tag = defaultdict(set)
for rt in room_tags:
    rooms_per_tag[rt["tag"]].add(rt["room_id"])
course_tags = q("SELECT course_id, requiredroomtags AS tag FROM course_requiredroomtags")
course_tag_map = defaultdict(set)
for ct in course_tags:
    course_tag_map[ct["course_id"]].add(ct["tag"])


# ---------------------------------------------------------------------------
# Dimension 1: per teacher
# ---------------------------------------------------------------------------
teacher_required = defaultdict(int)
for c in courses:
    if c["teacher_id"] is not None:
        teacher_required[c["teacher_id"]] += c["totalhours"]

teacher_rows = []
for tid, t in teachers.items():
    unavailable = teacher_unavailable.get(tid, set())
    available = sum(slots for d, slots in school_day_slots.items() if d not in unavailable and in_year(d))
    req = teacher_required.get(tid, 0)
    gap = req - available
    teacher_rows.append((t["name"], req, available, gap, bool(t["external"])))
teacher_rows.sort(key=lambda r: r[3], reverse=True)

# ---------------------------------------------------------------------------
# Dimension 2: per student group
# ---------------------------------------------------------------------------
group_required = defaultdict(int)
for c in courses:
    if c["studentgroup_id"] is not None:
        group_required[c["studentgroup_id"]] += c["totalhours"]

group_rows = []
for gid, g in groups.items():
    sd = g["sd"]  # 'YYYY-MM-DD' or None
    cap = g["maxhoursperday"] or H7_CAP
    available = 0
    for d, slots in school_day_slots.items():
        if not in_year(d):
            continue
        if sd and d < sd:
            continue
        available += min(slots, cap)
    req = group_required.get(gid, 0)
    gap = req - available
    group_rows.append((g["name"], req, available, gap, cap))
group_rows.sort(key=lambda r: r[3], reverse=True)

# ---------------------------------------------------------------------------
# Dimension 3: per room tag
# ---------------------------------------------------------------------------
tag_required = defaultdict(int)
for c in courses:
    for tag in course_tag_map.get(c["id"], ()):
        tag_required[tag] += c["totalhours"]

tag_rows = []
for tag in sorted(tag_required):
    n_rooms = len(rooms_per_tag.get(tag, ()))
    available = n_rooms * total_school_slots
    req = tag_required[tag]
    gap = req - available
    tag_rows.append((tag, req, available, gap, n_rooms))
tag_rows.sort(key=lambda r: r[3], reverse=True)

# ---------------------------------------------------------------------------
# TASK-506 dimension 4: quota-vs-window per (group, weekday)  [H20 family]
# The school's own calendar sheets demand N hours on a weekday whose open-slot
# window (GroupCalendarRule) is narrower: INFEASIBLE for that cell.
# ---------------------------------------------------------------------------
quota_rows = q("""SELECT DISTINCT sg.name AS g, to_char(dq.date,'YYYY-MM-DD') AS d,
                         (dq.aulahours + dq.labhours)::int AS h
                  FROM dailyactivityquota dq
                  JOIN studentgroup sg ON sg.id = dq.studentgroup_id
                  WHERE dq.omcquota = true""")
quota_cell = defaultdict(int)
for r in quota_rows:
    quota_cell[(r["g"], r["d"])] = int(r["h"])

gcr = q("""SELECT sg.name AS g, gcr.dayofweek AS wd, gcr.minslot::int AS mn, gcr.maxslot::int AS mx
           FROM groupcalendarrule gcr JOIN studentgroup sg ON sg.id = gcr.studentgroup_id""")
# dayofweek is stored as an int (1=MONDAY..7=SUNDAY, ISO): map to python weekday 0..6
WD_INDEX = {1:0, 2:1, 3:2, 4:3, 5:4, 6:5, 7:6}
win_by_gw = {}
for r in gcr:
    win_by_gw[(r["g"], WD_INDEX.get(int(r["wd"])))] = (int(r["mn"]), int(r["mx"]))

import datetime as _dt
quota_window_rows = []
for (g, d), h in sorted(quota_cell.items()):
    wd = _dt.date.fromisoformat(d).weekday()
    mn, mx = win_by_gw.get((g, wd), (0, 7))
    capacity = max(0, mx - mn + 1)
    if h > capacity:
        quota_window_rows.append((g, d, h, capacity, h - capacity))

# ---------------------------------------------------------------------------
# TASK-506 dimension 5: incarichi-vs-calendario per group  [835-vs-750 family]
# The course pool vs the school's own expected-hours plan: OVERDEMAND when the
# pool exceeds the plan (M20/M33 tension), QUESTION when the plan exceeds the
# pool (the G04 shape: the solver will underfill the calendar).
# ---------------------------------------------------------------------------
plan_hours = defaultdict(int)
for r in q("""SELECT sg.name AS g, (dq.aulahours + dq.labhours)::int AS h
              FROM dailyactivityquota dq JOIN studentgroup sg ON sg.id = dq.studentgroup_id
              WHERE dq.date >= '2026-09-14' AND dq.date <= '2027-06-10'"""):
    plan_hours[r["g"]] += int(r["h"])
pool_vs_plan_rows = []
stage_pool = defaultdict(int)
for c in courses:
    if c["subject"] == "Stage":
        stage_pool[c["studentgroup_id"]] += c["totalhours"]
for gid, g in groups.items():
    pool = group_required.get(gid, 0) - stage_pool.get(gid, 0)
    plan = plan_hours.get(g["name"], 0)
    if pool != plan:
        pool_vs_plan_rows.append((g["name"], pool, plan, pool - plan))
pool_vs_plan_rows.sort(key=lambda r: abs(r[3]), reverse=True)

# ---------------------------------------------------------------------------
# TASK-506 dimension 6: H30 deadline (Sicurezza hours vs pre-stage capacity)
# ---------------------------------------------------------------------------
SICUREZZA = "Sicurezza"
stage_patterns = q("""SELECT sg.name AS g, to_char(sp.startdate,'YYYY-MM-DD') AS sds,
                             to_char(sp.enddate,'YYYY-MM-DD') AS sde
                      FROM stagepattern sp JOIN studentgroup sg ON sg.id = sp.studentgroup_id""")
first_stage_start = {}
for r in stage_patterns:
    k = r["g"]
    if k not in first_stage_start or r["sds"] < first_stage_start[k]:
        first_stage_start[k] = r["sds"]
h30_rows = []
for c in courses:
    if c["subject"] != SICUREZZA:
        continue
    g = groups.get(c["studentgroup_id"])
    if not g:
        continue
    sds = first_stage_start.get(g["name"])
    if not sds:
        continue
    cap = g["maxhoursperday"] or H7_CAP
    available = sum(min(sl, cap) for d, sl in school_day_slots.items()
                    if in_year(d) and d < sds and (not g["sd"] or d >= g["sd"]))
    h30_rows.append((g["name"], c["totalhours"], available, sds,
                     c["totalhours"] - available))
h30_rows.sort(key=lambda r: r[4], reverse=True)

# ---------------------------------------------------------------------------
# TASK-506 dimension 7: H25 (stage hours vs stage-day capacity)
# ---------------------------------------------------------------------------
stage_days = defaultdict(int)      # group -> IN-GRID stage-day count (from the DB)
for r in q("""SELECT sg.name AS g, to_char(dq.date,'YYYY-MM-DD') AS d
              FROM dailyactivityquota dq JOIN studentgroup sg ON sg.id = dq.studentgroup_id
              WHERE dq.stagehours > 0"""):
    stage_days[r["g"]] += 1
# TASK-506 stadio 2 (caso G04): the DB DROPS out-of-horizon quota rows (the DataGenerator
# skips them), so the off-grid DECLARED stage days must come from the school's own sheet
# (calendario_fc.csv): G04 declares 30 stage days, 2 of them (2027-06-11/14, 8h each)
# beyond the grid end. The golden places exactly those 16h there (verified), so they are
# expected-unscheduled, not in-grid demand: subtract them from the H25 demand.
import csv as _csv
from pathlib import Path as _Path
stage_off_hours = defaultdict(int)
_FC_csv = _Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "seed" / "calendario_fc.csv"
with open(_FC_csv, newline="") as _f:
    for _r in _csv.DictReader(_f):
        if int(_r["stage_hours"] or 0) > 0 and _r["date"] > YEARS_END:
            stage_off_hours[_r["group"]] += int(_r["stage_hours"])
stage_days_offgrid = defaultdict(int)
with open(_FC_csv, newline="") as _f:
    for _r in _csv.DictReader(_f):
        if int(_r["stage_hours"] or 0) > 0 and _r["date"] > YEARS_END:
            stage_days_offgrid[_r["group"]] += 1
h25_rows = []
for c in courses:
    if c["subject"] != "Stage":
        continue
    g = groups.get(c["studentgroup_id"])
    if not g:
        continue
    ndays = stage_days.get(g["name"], 0)
    offd = stage_days_offgrid.get(g["name"], 0)
    offh = stage_off_hours.get(g["name"], 0)
    cap = g["maxhoursperday"] or H7_CAP
    capacity = ndays * cap
    demand = c["totalhours"] - offh
    h25_rows.append((g["name"], c["totalhours"], offd, offh, ndays, capacity,
                     demand - capacity))
h25_rows.sort(key=lambda r: r[6], reverse=True)

# ---------------------------------------------------------------------------
# TASK-506 dimension 8: quota-bound lab demand vs lab-quota days  [H23/H24]
# Lab courses (islab + tagged) per group vs the number of days the calendar
# gives that group a lab quota: OVERDEMAND when the demand exceeds the days.
# ---------------------------------------------------------------------------
lab_day_count = defaultdict(int)
for r in q("""SELECT sg.name AS g, to_char(dq.date,'YYYY-MM-DD') AS d, dq.labhours::int AS lh
              FROM dailyactivityquota dq JOIN studentgroup sg ON sg.id = dq.studentgroup_id
              WHERE dq.labhours > 0"""):
    lab_day_count[r["g"]] += 1
quota_lab_rows = []
for gid, g in groups.items():
    lab_hours = sum(c["totalhours"] for c in courses
                    if c["studentgroup_id"] == gid and c["islab"]
                    and course_tag_map.get(c["id"]))
    ndays = lab_day_count.get(g["name"], 0)
    cap = g["maxhoursPerDay".lower()] if False else (g["maxhoursperday"] or H7_CAP)
    capacity = ndays * cap
    if lab_hours > 0:
        if ndays == 0:
            # MODA family: the lab structure arrives as calendario PINS (MODA TUTTI),
            # not as quota L days: the quota-bound bound does not apply.
            quota_lab_rows.append((g["name"], lab_hours, "N/A (pin-based)",
                                   "N/A", "N/A (lab demand already pinned, not quota-bound)"))
        else:
            quota_lab_rows.append((g["name"], lab_hours, ndays, capacity, lab_hours - capacity))
quota_lab_rows.sort(key=lambda r: (str(r[4]).startswith("N/A"), r[4] if isinstance(r[4], int) else -1), reverse=False)

# ---------------------------------------------------------------------------
# TASK-506 dimension 9: tag offer refined per weekday  [mono-room saturation]
# On a weekday, the groups whose calendar gives lab quota that weekday all
# compete for the tag's rooms at the same time: if more groups have a lab day
# than there are rooms of the tag, the weekday is saturated (the grasp-seed-6
# family; exact slot-level packing still decided by the solver).
# ---------------------------------------------------------------------------
lab_weekday_groups = defaultdict(set)   # (tag, weekday) -> groups with lab quota that weekday
tag_by_gid = {}
for c in courses:
    if c["islab"] and course_tag_map.get(c["id"]) and c["studentgroup_id"]:
        gname = groups[c["studentgroup_id"]]["name"]
        for tag in course_tag_map[c["id"]]:
            tag_by_gid.setdefault((tag, gname), True)
for r in q("""SELECT sg.name AS g, to_char(dq.date,'YYYY-MM-DD') AS d, dq.labhours::int AS lh
              FROM dailyactivityquota dq JOIN studentgroup sg ON sg.id = dq.studentgroup_id
              WHERE dq.labhours > 0"""):
    wd = _dt.date.fromisoformat(r["d"]).weekday()
    for (tag, gname) in tag_by_gid:
        if gname == r["g"]:
            lab_weekday_groups[(tag, wd)].add(gname)
tag_weekday_rows = []
for (tag, wd), gset in sorted(lab_weekday_groups.items()):
    n_rooms = len(rooms_per_tag.get(tag, ()))
    if n_rooms and len(gset) > n_rooms:
        tag_weekday_rows.append((tag, ["Lun","Mar","Mer","Gio","Ven"][wd], len(gset), n_rooms,
                                 len(gset) - n_rooms))
tag_weekday_rows.sort(key=lambda r: r[4], reverse=True)

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
infeasible_teachers = [r for r in teacher_rows if r[3] > 0]
infeasible_groups = [r for r in group_rows if r[3] > 0]
infeasible_tags = [r for r in tag_rows if r[3] > 0]
unplaceable_lower_bound = sum(r[3] for r in infeasible_teachers + infeasible_groups + infeasible_tags)
# (note: dimensions overlap — a lab hour counts against its teacher, group, and tag — so this is a loose sum)
any_infeasible = bool(infeasible_teachers or infeasible_groups or infeasible_tags or quota_window_rows)

# ---------------------------------------------------------------------------
# Markdown report
# ---------------------------------------------------------------------------
def fmt_table(header, rows):
    out = ["| " + " | ".join(header) + " |", "|" + "|".join(["---"] * len(header)) + "|"]
    for r in rows:
        out.append("| " + " | ".join(str(x) for x in r) + " |")
    return "\n".join(out)


lines = []
lines.append(f"# Feasibility Audit — {TODAY}")
lines.append("")
lines.append(f"**Profile:** dev  |  **School year:** {YEARS_START} .. {YEARS_END}  |  "
             f"**Total slots:** {total_slots}  |  **School-day slots (non-holiday):** {total_school_slots}  |  "
             f"**Holidays:** {len(holidays)}")
lines.append("")
lines.append("Capacity is the **maximum (most generous)** per resource — stage blackouts and "
             "resource conflicts are NOT subtracted. So any row where **required > available** "
             "is infeasible by construction, and the gap is a lower bound on hours that cannot be placed.")
lines.append("")
lines.append(f"## Verdict: {'INFEASIBLE — 0 hard / 0 unassigned is NOT achievable by construction' if any_infeasible else 'FEASIBLE on these dimensions — no structural lower-bound violation found'}")
lines.append("")
if any_infeasible:
    lines.append(f"Loose lower bound on unplaceable hours (sum across infeasible rows; dimensions overlap): **{unplaceable_lower_bound}**")
else:
    lines.append("No teacher, group, or room-tag dimension exceeds its maximum capacity. "
                 "This does NOT prove a 0-hard solution exists (conflicts, stage rules, and block "
                 "contiguity still bite), but it removes 'infeasible by construction' as an explanation.")
lines.append("")
lines.append("## Per teacher (required hours vs available slots)")
lines.append(fmt_table(["Teacher", "Required (h)", "Available (slots)", "Gap", "External"],
                       [(r[0], r[1], r[2], r[3], r[4]) for r in teacher_rows]))
lines.append("")
lines.append("## Per student group (required hours vs available slots, capped by maxHoursPerDay)")
lines.append(fmt_table(["Group", "Required (h)", "Available (slots)", "Gap", "Cap/day"],
                       [(r[0], r[1], r[2], r[3], r[4]) for r in group_rows]))
lines.append("")
lines.append("## Per room tag (required lab hours vs rooms × slots)")
lines.append(fmt_table(["Tag", "Required (h)", "Available (slots)", "Gap", "# Rooms"],
                       [(r[0], r[1], r[2], r[3], r[4]) for r in tag_rows]))
lines.append("")

# ── TASK-506 new dimensions ─────────────────────────────────────────────
lines.append("## TASK-506 dimension 4: quota vs calendar window per (group, weekday) [H20 family]")
lines.append("INFEASIBLE class: the school's own sheets demand more hours on a weekday than the group's open-slot window.")
lines.append(fmt_table(["Group", "Date", "Quota (h)", "Window (slots)", "Excess"],
                       quota_window_rows if quota_window_rows else [("(none)", "-", "-", "-", "-")]))
lines.append("")
lines.append("## TASK-506 dimension 5: course pool vs calendar plan per group [835-vs-750 family]")
lines.append("QUESTION class (workbook referente): pool > plan means M20/M33 tension (OVERDEMAND note); plan > pool means the solver will underfill the calendar (the G04 shape).")
lines.append(fmt_table(["Group", "Pool (incarichi h)", "Plan (expected h)", "Delta"],
                       pool_vs_plan_rows if pool_vs_plan_rows else [("(all match)", "-", "-", "-")]))
lines.append("")
lines.append("## TASK-506 dimension 6: H30 deadline (Sicurezza vs pre-stage capacity)")
lines.append("INFEASIBLE when the hours cannot fit before the stage starts (generous capacity: no conflicts subtracted).")
lines.append(fmt_table(["Group", "Sicurezza (h)", "Pre-stage capacity", "Stage start", "Gap"],
                       h30_rows if h30_rows else [("(no Sicurezza courses with stage)", "-", "-", "-", "-")]))
lines.append("")
lines.append("## TASK-506 dimension 7: H25 (stage hours vs stage-day capacity, off-grid aware)")
lines.append("OVERDEMAND when IN-GRID demand exceeds in-grid capacity. Off-grid declared stage days (beyond the grid end) are separate: the golden places exactly their hours there (G04: 2 days, 16h, on 2027-06-11/14), expected-unscheduled, not overdemand.")
lines.append(fmt_table(["Group", "Stage (h)", "# Off-grid d", "Off-grid h", "# In-grid d", "In-grid cap", "Gap"],
                       h25_rows if h25_rows else [("(no stage courses)", "-", "-", "-", "-", "-", "-")]))
lines.append("")
lines.append("## TASK-506 dimension 8: lab demand vs lab-quota days [H23/H24]")
lines.append("OVERDEMAND when the group's lab-tagged hours exceed (lab-quota days x cap/day).")
lines.append(fmt_table(["Group", "Lab hours", "# Lab days", "Capacity", "Gap"],
                       quota_lab_rows))
lines.append("")
lines.append("## TASK-506 dimension 9: tag offer per weekday [mono-room saturation]")
lines.append("QUESTION/OVERDEMAND: weekdays where more groups have a lab day than the tag has rooms (grasp-seed-6 family; slot-level packing is the solver's).")
lines.append(fmt_table(["Tag", "Weekday", "# Groups with lab", "# Rooms", "Excess"],
                       tag_weekday_rows if tag_weekday_rows else [("(none saturated)", "-", "-", "-", "-")]))
lines.append("")

# Drill-down: for each infeasible group, show peer median + largest courses
cap_totals = defaultdict(list)
for gid, g in groups.items():
    cap = g["maxhoursperday"] or H7_CAP
    cap_totals[cap].append(group_required.get(gid, 0))

inf_groups = [r for r in group_rows if r[3] > 0]
lines.append("## Drill-down: infeasible groups (excess over peer median + largest courses)")
if not inf_groups:
    lines.append("(none — every group fits its generous capacity)")
else:
    for (name, req, avail, gap, cap) in inf_groups:
        peers = cap_totals.get(cap, [])
        peer_med = int(statistics.median(peers)) if peers else 0
        gid = name_to_gid.get(name)
        courses_for = sorted([c for c in courses if c["studentgroup_id"] == gid],
                             key=lambda c: -c["totalhours"])
        lines.append(f"**{name}** — required {req}h vs available {avail} (gap +{gap}); "
                     f"peer median for cap {cap}/day = {peer_med}h; excess over peer median = "
                     f"**{req - peer_med}h** (an excess over peer median often pinpoints a data discrepancy — TASK-337).")
        for c in courses_for[:10]:
            lines.append(f"  - {c['subject']} — {c['totalhours']}h" + (" (lab)" if c['islab'] else ""))
lines.append("")

# The per-teacher table above already surfaces every infeasible teacher ranked by gap,
# so no named self-test is needed. (Historical note: the previously documented
# "T28/G10 IMPOSSIBLE" case is feasible at current hours — see TASK-321.)
lines.append("_Method: scripts/analyze/feasibility-audit.py via scripts/db/db-query.sh. "
             "Data fixes belong to TASK-337 (m-6), not here._")
lines.append("")

report = "\n".join(lines)
with open(REPORT, "w") as fh:
    fh.write(report)

print(report)
print(f"\n[report written to {REPORT}]", file=sys.stderr)
