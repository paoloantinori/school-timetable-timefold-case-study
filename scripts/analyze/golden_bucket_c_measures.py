#!/usr/bin/env python3
"""Read-only golden measures for bucket-C unmodeled rules (coda A, 2026-07-31 notte4-seguito).

Measures 5 written-but-unmodeled rules against the golden practice
(`src/main/resources/seed/lessons.csv`). Golden-first criterion: a rule the golden respects is
a quasi-free model candidate; a rule the golden violates becomes a referente notification line.

Rules:
  (i)   Sicurezza of first-years all in 2 cumulative days        [bucket C1 / T5]
  (ii)  T21 never prime-ore in G08/G12 + mid-day      [bucket C2 / T3]
  (iii) T11 6h post-stage with G08/G11/G10             [bucket C3 / T4]
  (iv)  Laboratorio Termoidraulico in the first 4 hours          [bucket B]
  (v)   Stage G10 no-Monday                                    [bucket C7 / T7]

Traps filtered per CLAUDE.md (Saturdays off-grid, teacher Fasullo/Fasutto placeholder 19/09),
except where a rule is about Stage (off-grid by nature).
Output doc: claudedocs/spalmone-2026-27/misure-golden-bucket-c-2026-07-31.md
"""
import csv, datetime
from collections import defaultdict, Counter

GOLDEN = "src/main/resources/seed/lessons.csv"
SLOT_INDEX = {"08": 0, "09": 1, "10": 2, "11": 3, "12": 4, "13": 5, "14": 6, "15": 7}


def slot_of(slot_field):
    return SLOT_INDEX.get(slot_field[:2], -1)


rows = []
with open(GOLDEN, newline="", encoding="utf-8") as f:
    for r in csv.DictReader(f):
        try:
            d = datetime.date.fromisoformat(r["date"])
        except ValueError:
            continue
        rows.append({"date": d, "wd": d.weekday(), "slot": slot_of(r["slot"]),
                     "group": r["group"], "teacher": r["teacher"], "subject": r["subject"]})


def real(rs):
    return [r for r in rs if r["wd"] != 5 and r["teacher"] not in ("Fasullo", "Fasutto")]


def hr():
    print("=" * 70)

# (i) Sicurezza 1e in 2 giorni cumulativi
hr(); print("(i) Sicurezza 1e in 2 giorni cumulativi (subject == 'Sicurezza')")
for g in ["G03", "G02", "G01", "G04"]:
    s = real([r for r in rows if r["subject"] == "Sicurezza" and r["group"] == g])
    days = sorted({r["date"] for r in s})
    print(f"  {g:8s} ore={len(s):3d}  giorni-distinti={len(days)}  -> "
          f"{'OK <=2' if len(days) <= 2 else 'VIOLA (>2)'}")

# (ii) T21 mai slot0 in G08 / G12 + ore di mezzo
hr(); print("(ii) T21: mai slot0 in G08 / G12 + ore di mezzo")
for g in ["G08", "G12"]:
    s = real([r for r in rows if r["teacher"] == "T21" and r["group"] == g])
    slot0 = [r for r in s if r["slot"] == 0]
    dist = Counter(r["slot"] for r in s)
    print(f"  {g:8s} ore={len(s):3d}  slot0={len(slot0):3d}  -> "
          f"{'OK (no prime ore)' if len(slot0) == 0 else 'VIOLA (prime ore)'}")
    print(f"           distribuzione slot: {dict(sorted(dist.items()))}")

# (iii) T11 6h post-stage per curriculum (G08, G11, G10)
hr(); print("(iii) T11: 6h post-stage per curriculum (G08, G11, G10)")
stage_end = {}
for g in ["G08", "G11", "G10"]:
    dates = [r["date"] for r in rows if r["subject"] == "Stage" and r["group"] == g]
    stage_end[g] = max(dates) if dates else None
    print(f"  stage {g}: end = {stage_end[g]}")
for g in ["G08", "G11", "G10"]:
    se = stage_end[g]
    post = real([r for r in rows if r["teacher"] == "T11" and r["group"] == g and r["date"] > se]) if se else []
    print(f"  T11 {g:8s} post-stage ore={len(post):3d}  by-subject={dict(Counter(r['subject'] for r in post))}")

# (iv) Laboratorio Termoidraulico prime 4 ore (slot 0-3)
hr(); print("(iv) Laboratorio Termoidraulico: prime 4 ore (slot 0-3 = 8-12)")
s = real([r for r in rows if r["subject"] == "Laboratorio Termoidraulico"])
dist = Counter(r["slot"] for r in s)
in04 = sum(c for sl, c in dist.items() if 0 <= sl <= 3)
print(f"  ore totali={len(s)}  in slot0-3={in04}  fuori={len(s) - in04}  -> "
      f"{'OK (prime 4 ore)' if in04 == len(s) else 'VIOLA/partial'}")
print(f"  distribuzione slot: {dict(sorted(dist.items()))}")

# (v) Stage G10 no-lunedi
hr(); print("(v) Stage G10: nessun lunedi (off-grid, sabati non filtrati)")
s = [r for r in rows if r["group"] == "G10" and r["subject"] == "Stage"]
wd_names = ["Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"]
dist = Counter(r["wd"] for r in s)
mon = dist.get(0, 0)
print(f"  Stage G10 ore={len(s)}  lunedi={mon}  -> "
      f"{'OK (no lunedi)' if mon == 0 else 'VIOLA (lunedi presente)'}")
print(f"  distribuzione weekday: {{ {', '.join('%s=%d' % (wd_names[k], dist[k]) for k in sorted(dist))} }}")
