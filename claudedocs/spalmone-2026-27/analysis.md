# Spalmone 2026/2027 — Analysis of the Real Data + Hand-Made Solution

**Source file:** `/home/user/Downloads/Spalmone 2026_2027 FC DUALE condiviso maintainer.xlsx` (received 2026-07-16)
**Extractor:** `scripts/analyze/spalmone2027-extract.py` → CSVs in `claudedocs/spalmone-2026-27/data/`
**User decision (2026-07-16):** this file COMPLETELY OVERRIDES all previous data (old spalmone.csv, DataGenerator content). No old-vs-new discrepancy reporting is needed. The goal is validating our heuristics and assumptions against it.

## 1. What the file contains

School year **2026-09-14 → 2027-06-10** (FC groups have exam days up to 2027-06-15).

| Sheet | Content | Extracted to |
|---|---|---|
| `Orario in righe` | Full hand-made timetable, 12,551 scheduled lesson-hours (date, weekday, slot, class, teacher, subject) | `lessons.csv` |
| `Incarichi in riga` | Teacher–subject–class hour assignments (296 rows incl. pseudo-rows) | `incarichi.csv` |
| `Docenti` | 26 teachers with full name + email | `teachers.csv` |
| `Elenchi` | 14 groups w/ enrollment + course codes; 16 rooms w/ capacity | `groups.csv`, `rooms.csv` |
| `Parametri` | Year start/end, 29 closure dates | `closures.csv` |
| `Vincoli Classi` | Stage windows, lab days, free-text notes per class | `vincoli_classi.csv` |
| `Vincoli Docenti` | FREE-TEXT teacher availability constraints | (needs structuring, see §5) |
| `Classi` | The school's own reconciliation sheet: "Ore Inc." vs "Ore Prg." + Delta + "A posto"/"Analizzare" flags | — |
| `Orario` | Same timetable in grid form; `NO` cells = per-group slot-level closures | — |

**No rooms are assigned in the hand-made timetable.** The grid holds one cell per (class, slot) — it cannot express room assignments or co-teaching (doppio).

### Pseudo-entities mixed into the data (must be handled explicitly by any importer)
- Teacher **`Stage`** / subject `Stage`: stage periods are *scheduled as lessons* (8h/day) and **count toward the ~990h yearly group total**.
- Teacher **`Esame`**: exam hours assigned in incarichi (24h for 3rd years / G08) but NOT scheduled in the grid (some land after year end: FC 2027-06-11..15).
- Subject **`RA`** (Recuperi ed Approfondimenti): for G08 it is spread over many teachers (T23 19h, T15 6h, T10 5h, …) with no per-teacher assignment in incarichi (assigned to pseudo-teacher `RA`, 50h/40h).
- Row **`Totale ore corso`**: summary row (990h per group, 1000h for G12, 120h G14).

## 2. Calendar structure — BREAKS our current model

- **8 slots/day**: 08–14 (6 morning slots) + lunch 14:00–14:30 + **14:30–15:30, 15:30–16:30 afternoon slots**. Current model: 6 slots/day, `ALL_TIME_PERIODS` hardcoded in frontend, ~1520 timeslots.
- **575 afternoon lessons** across 9 groups (2/G11, 2/G10, G12, G09, 1/G08, G13).
- **Per-group per-weekday opening pattern** (from `NO` cells and occupancy): first-year groups (MODA/FA/FB) never use afternoons and have a 5-slot Friday; G14 runs Thu (5 slots) + Fri (3 slots) only; FC/3rd years use afternoons routinely. First school day (Mon 2026-09-14) starts at 09:00.
- **Group-days can reach 8 hours** (296 group-days with 7–8h; 44 of them are regular teaching, not stage: G06, G10, G12).
- 29 closure dates (bridge days, Christmas 2026-12-23..2027-01-06, Easter 2027-03-25..30, etc.).

## 3. The hand-made solution validates our core hard constraints

Mechanical checks over the 10,542 real-teacher lesson-hours:

| Check | Result | Verdict on our model |
|---|---|---|
| Teacher conflicts (H1) | **0** | ✅ confirmed hard |
| Group double-bookings (H3) | **0** | ✅ confirmed hard (grid makes it structural) |
| Lessons on closure days (H6) | **0** | ✅ confirmed hard |
| Morning gaps in a group's day (H17) | **0 / 2,213 group-days** | ✅ confirmed hard — humans NEVER leave a gap |
| Afternoon lesson with empty 13:00 slot | 2 exceptions only | H17 must treat lunch as non-gap; afternoon contiguity is near-hard |
| Assigned vs scheduled hours | reconciles for ~85% of keys; deltas concentrated in FC-DUALE (in-flux), Stage, Esame | the school's own sheet tracks this — we must too (§6) |

## 4. Findings that INVALIDATE or recalibrate current heuristics

1. **H15 (same-day same-subject must be consecutive) is violated 137 / 7,908 times (1.7%)** in the human solution — e.g. T25 Scienze at slots 3 and 5 with something between. As a HARD constraint our model would declare the human solution infeasible. → demote/parametrize.
2. **H7 (max teacher daily hours = 6?) violated**: T01 teaches 8h in one day (full-day lab with G10, crossing lunch). All other teachers max 6. → per-teacher max.
3. **H11 max group daily hours**: real days reach 8h. Current per-group `maxHoursPerDay` values are stale.
4. **"Labs recur on a fixed weekday" (old M17 / pre-assignment rotation assumption) is FALSE in general**: only 14 of 125 subjects ≥40h sit on a single weekday. BUT **67% of all lesson-hours follow a weekly-recurring template cell (≥10 identical weeks)** — the human builds a weekly grid and patches periods. This favors a *weekly-template* pre-assignment/solving approach over per-date placement.
5. **Block sizes**: histogram {1h: 6746, 2h: 680, 3h: 319, 4h: 83, 5h: 155, 6h: 62}; 27 blocks cross the lunch break (13–14 + 14:30). Lab blocks up to 6h exist.
6. **Stage is not a pure blackout**: groups return to school during their stage window (G11: 4 stage-days/week, lessons on the remaining day — matches our StagePattern dayMask concept ✅). G07 explicitly "rientrano". Stage hours count toward the yearly total.
7. ~~**T28 is GONE**~~ **UPDATE 17/07: Jory = T01** (same person, renamed on the spalmone — confirms her Thursday-heavy 8h days; maxDailyHours=8 seeded in TASK-348). New teachers vs old seed: T01, T07, T10, T15, T19, T26, T18, T17, T14, T13… 26 total. All acconciatura LabRotation seeds referencing T28 are obsolete. ("Jory" appears once in a G12 note — open question for the school.)
8. **Teacher gaps (S1)**: human solution has 1,130 teacher-days with ≥1 gap (of 3,123) — teacher compactness is clearly SOFT for the school.
9. **Sparse days (M20)**: 18 group-days with 1–3h exist (mostly G14 Fri 3h and edge weeks). M20 as medium with exceptions is the right call; hours-per-day histogram: {2:2, 3:16, 4:138, 5:967, 6:794, 7:20, 8:276}.
10. **Doppio (co-teaching)**: the grid format cannot represent it. Whether doppio still exists in 2026/27 is an open question for the school.
11. **57 distinct subjects** incl. new ones (Barberia, Cad, Controllo numerico, Tecniche di saldatura, Lavorazioni meccaniche…), with case inconsistencies ("Lingua italiana" vs "Lingua Italiana", "Educazione Civica" vs "civica", "Comportamento Sociale/sociale") — importer must normalize.

## 5. Teacher availability (Vincoli Docenti) — free text, needs a structured model

Examples that our current `TeacherAvailability` (absolute datetime VACATION rows) cannot express:
- T04: works 8:00–13:00; Friday free.
- T05: Mon/Wed/Thu 8–14 but at most a light day on one of them; Tue 8–14 + 14:30–16:30; Friday free.
- T11: 9–14 always, one day from 8:00.
- T16: max 3 consecutive R16 hours (not 6); 2 mornings free from 11:00.
- T10: Thursday morning free (teaches LES/G14 elsewhere).
- T12: mornings from 10:00 on equipe day; specific dated absences (visite mediche).
→ Requires: recurring weekly availability (day-of-week + slot range), per-subject block-size caps, and dated exceptions.

## 6. The school's own coherence workflow (to replicate in-app)

The `Classi` sheet computes per (teacher, subject, class): assigned hours ("Ore Inc.") vs scheduled hours in the grid ("Ore Prg.") vs Delta, with boolean flags "A posto" / "Analizzare". This is exactly the *continuous reconciliation* the user wants: any edit that makes a number move on one side but not the other must surface immediately.

Real residual deltas found (FC DUALE still in flux at the school): T03↔T22 swap on Lab Termoidraulico (100↔135), Taglio Metalli 60 vs 20, T15 Cad/Lavorazioni/Controllo numerico large deltas, T13 Orientamento G12 10h never scheduled.

## 7. Golden-solution opportunities

The hand-made timetable is a **golden reference**: import it as a fixture and (a) run our ConstraintProvider over it to measure exactly which constraints fire (calibration), (b) keep it as a regression test (constraint drift detection), (c) benchmark our solver's solution against it on identical data (beat-the-human metric).
