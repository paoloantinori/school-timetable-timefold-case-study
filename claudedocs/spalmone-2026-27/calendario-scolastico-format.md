# Calendario Scolastico 2026/27 — Reading Key (INPUT format for pre-set solutions)

**Source:** `/home/user/Downloads/Copy of Calendario scolastico 2026_2027.xlsx` (received 2026-07-16)
**User decision (2026-07-16):** this document is the SCHOOL'S REAL PROCESS for pre-placing the hard-to-place lab lessons: the coordinator builds partial empty calendars per class, hands them to the lab teachers, and each teacher starts their own recurring preference calendar. **Our pre-set-solution import phase must consume THIS format** — it replaces the heuristic pre-assignment (LabRotation CSP) as the source of pinned lab placements.
**Authoritative sheets:** `DEF FC TUTTI` (definitive FC), `FC TUTTI`, `MODA TUTTI`. Discarded variants: `MODA NON USARE`, `FC duale piano B`, the various `Copia di …`.

Interpretation below was cross-validated against the spalmone golden solution (see §4).

## 1. Per-class sheets (`G03`, `G07`, … 45 cols)

Month-block layout, 4 columns per month starting at col 1 (settembre) with stride 4:
`(day-of-month, weekday, hours-or-holiday-name, modality)`.

- **hours**: numeric = teaching hours that day (5/6/8); text = holiday name (Natale, Ponte, Immacolata…); empty = weekend/no school.
- **modality letter** (legend in `Istruzioni`): **A = Aula, S = Stage, L = Laboratorio**.
- Rows 2–5 hold monthly + yearly totals per modality (Ore / STAGE / Lab. → Totale 990).
- Example verified: `G07` has Thursday `5 L` in March (lab day) and April Tue–Fri `8 S` with Monday `6 A` (returns to school during stage) — matches the spalmone exactly.
- `Calendario base` is the all-Aula template (990h capacity, monthly totals 74/127/122/81/93/104/111/127/116/35).

This is the **per-class day-capacity calendar**: date → (hours, modality). It encodes what our GroupCalendar + StagePattern + lab-day data must represent.

## 2. `FC TUTTI` / `DEF FC TUTTI` (226 cols)

Month blocks with stride 22: two date columns (cols 7–8 in block 1), then 3+2 group blocks of 4 columns each: `G04`, `G08`, `G13` (+2 unnamed spare blocks), each `TOT | Au | La | St`.

- Per (date, group): total hours split into **Au**la / **La**boratorio (officina) / **St**age hours for that day. E.g. `2026-10-01, G04: TOT 6 = Au 2 + La 4`.
- Cols A–F rows 5–8: yearly totals per group and modality (DEF: G04 990 = Au 745 + La 245; G08 990 = Au 584 + La 106 + St 300; G13 990 = Au 485 + La 210 + St 295).
- Row 4 of each month block: monthly totals per group/modality.
- `DEF FC TUTTI` is the definitive scenario; `FC TUTTI` and `FC duale piano B` are alternatives with different Au/La/St splits.

## 3. `MODA TUTTI` (28 cols) — the shift convention

Single date column layout (rows 4+, one row per date). Per group (`G01` cols 3–9, `G05` 10–16, `G09` 17–23): `TOT | ORE | Au | PE | MU | BO | St`; G14 cols 24–27 as `TOT | Au | La | St`.

- **PE = T17** (Modello/Moulage), **MU = T16** (Confezione), **BO = T07** (verified: all 72 predicted BO slots are T07 in the golden solution; 72 = her exact yearly total).
- Cell value = lab hours that teacher teaches that group that day. **Sign convention: negative = FIRST shift (first 3 hours of the day, slots 08–11); positive = SECOND shift (the following hours, slots 11–14).** Values seen: ±3 (typical), 2, ±5, 6, 1.
- Weekly pattern (Sep): Mon MU 1M(+3)/3M(−3); Tue PE 1M/3M paired shifts; Wed PE 2M(+3), BO 3M(−3); Thu PE 2M(+3), MU 2M(−3); Fri MU 1M(−3), MU 3M(+2).
- `ORE` = yearly 990 reference; `TOT` = total pre-placed hours for the group (G01 347 = PE 143 + MU 180 + BO 24).

## 4. Cross-validation against the spalmone golden solution

- **MODA TUTTI**, 225 teacher-day predictions vs golden timetable: **185 exact (group + shift + hours), 32 mirror swaps** (the teacher keeps both predicted shifts that day but the two groups traded shifts — coordinator-level equivalent), **8 true divergences** (all T16 G05 "mixed" placements in Apr–Jun 2027). Interpretation confirmed at teacher-day level ≈96%.
  **313 vs 225, two different metrics (TASK-364 code review K4):** `calendario_moda.csv` has 313 rows — every nonzero PE/MU/BO teacher-day cell across all of 1/2/G09, for the full 2026/27 school year. The 225 figure here is the smaller subset this cross-validation pass actually compared against the golden spalmone timetable (185 + 32 + 8 = 225 golden-comparison classes); it is not a count of rows dropped or miscounted, just a narrower validation sample than the full extracted set. Both numbers are correct for what they measure.
- **Per-class sheets** match spalmone stage windows and lab days (G07/G11 verified).
- Implication: the calendario is the INPUT template; the spalmone is the OUTPUT after final adjustments. Small divergences are expected and are exactly what the reconciliation dashboard must surface.

## 5. Consequences for the planner (workflow evolution)

The school's process evolved into: **(1) coordinator pre-places lab/stage day-structure per class → (2) lab teachers fill their recurring preference calendars → (3) the rest of the hours are placed around them.** This replaces what our heuristic pre-assignment (LabRotation + HybridPhase1a CSP) tried to compute. Our pipeline becomes: import this document as pinned/pre-set structure → solver fills the remaining ~65–70% of hours. Tracked in backlog: TASK-364 (importer for this format), TASK-351 (template research pivoted to consuming this input), TASK-360 (wizard support).
