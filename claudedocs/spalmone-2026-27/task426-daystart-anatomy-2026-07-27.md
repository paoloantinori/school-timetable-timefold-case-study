# TASK-426 Day-Start Anatomy (val419-train2-run1)

**Date**: 2026-07-27 | **Run**: `val419-train2-run1` (feature/cartesian-block-entity) | **Type**: READ-ONLY analysis, no code changes
**Final score**: `0hard/-2301305medium/12677soft` (fully feasible; non-anchoring is MEDIUM-tier only)
**Headline**: 95.9% anchored (2126/2216) vs golden 99.86% (2210/2213). **90 non-anchored group-days**, of which **88 are pure solver-choice** (all-unpinned days, slot 0 calendar-open, expected hours > 0), **0 structural**. Source is **construction** (cross-group teacher contention at slot 0), not LS/HoleRepair/SurplusRebalance. The gap is **addressable by a lever**.

---

## 1. Total + per-group (confirmed)

Total non-anchored group-days: **90** (matches `kpi-daystart.txt` exactly). First-hour histogram (from KPI): `{8: 2114, 9: 71, 10: 23, 11: 5, 12: 3}`. The "anchored" definition (per `scripts/analyze/day-start-kpi.py`): earliest placed lesson at hour 8 (slot 0), except the 2026-09-14 ramp day where the anchor is hour 9 (slot 1, the only date the school starts an hour late).

| Group | Non-anchored | Total days | %  | Biggest weekday |
|-------|-------------:|-----------:|---:|-----------------|
| G11   | 32 | 169 | 18.9% | Wed (20), Thu (10) |
| G04    | 21 | 162 | 13.0% | Tue (8), Mon (6), Wed (6) |
| G09   | 10 | 167 |  6.0% | Mon (7) |
| G13     |  9 | 171 |  5.3% | Tue (3) |
| G14    |  6 |  45 | 13.3% | Thu (6, all) |
| G10    |  5 | 159 |  3.1% | Wed (5, all) |
| G08    |  4 | 168 |  2.4% | Thu (2) |
| G07   |  2 | 170 |  1.2% | Wed (1), Thu (1) |
| G12   |  1 | 150 |  0.7% | Fri (1) |
| G01 / G02 / G03 / G05 / G06 | 0 | - | 0% | - |

G11 alone is 36% of the gap (32/90); G11 + G04 = 59%.

---

## 2. Weekday pattern

| Weekday | Count | Share |
|---------|------:|------:|
| Wednesday | 33 | 36.7% |
| Thursday  | 22 | 24.4% |
| Monday    | 15 | 16.7% |
| Tuesday   | 11 | 12.2% |
| Friday    |  9 | 10.0% |

**Wednesday + Thursday = 61%** of the gap. The Wednesday spike is driven by G11 (20 days) + G04 (6) + G10 (5). The Thursday spike is G11 (10) + G14 (6, all Thursdays).

**No calendar explanation**: every group's `GROUP_CALENDAR_PATTERNS` (DataGenerator.java:759-775) opens slot 0 on every open weekday. The only calendar-narrowed weekday in the whole seed is G14 (Mon/Tue/Wed fully closed; only Thu 0-4 and Fri 0-2), and G14 shows up here (6 Thursday) precisely because Thursday is one of its only two open weekdays, not because Thursday opens late. **M18 (TASK-405) applies to every (group, date)** via the widened gate, so no group is exempt by rule. There is no weekday where M18 "doesn't apply".

---

## 3. Structural vs solver (the core split)

Cross-referenced each non-anchored (group, date) against `calendario_class_days.csv` (11 non-FC groups) and `calendario_fc.csv` (3 FC groups), and the pinned flag in `lessons.txt`:

| Category | Count | Meaning |
|----------|------:|---------|
| Structural (no expected-hours fact / no-session day, H26) | **0** | None. Every non-anchored day has an expected-hours row. |
| Structural (expected hours = 0) | **0** | None. |
| All-pinned (structurally forced late by golden pins) | **0** | None. |
| Mixed (has pinned + unpinned on the same day) | **2** | Both G09 (2027-05-21 Fri, 2027-05-28 Fri) where a Confezione pinned block sits at slot 2-3 alongside unpinned lessons. |
| **All-unpinned (pure solver choice)** | **88** | Slot 0 is calendar-open, expected hours > 0, no pin forcing the late start. The solver freely chose to start later. |

**100% of the gap is solver-addressable**, not structural. The 2 mixed G09 cases still have unpinned lessons the solver could have placed at slot 0 (the pinned Confezione block sits at hour 11-12, not blocking slot 0).

### Offset distribution (how late)

| Offset (slots vs expected anchor) | Count |
|----------------------------------|------:|
| +1 (hour 9, or hour 10 on ramp)  | 59 |
| +2 (hour 10)                     | 24 |
| +3 (hour 11)                     |  4 |
| +4 (hour 12)                     |  3 |

92% of the gap is "start one or two slots late". The deep offsets (+3/+4) concentrate in G09 Mondays (Recuperi/Lingua Italiana starting at hour 11) and G13 (a few afternoon-only days at hour 12).

---

## 4. Source attribution: template/construction vs LS vs repair

### Phase-transition evolution

Day-start anchored rate across the phase-transition dumps in `phase-transitions/`:

| Snapshot | Anchored | Rate |
|----------|---------:|-----:|
| phase-2-2 (end LS Compactness #1, earliest dump) | 2135/2217 | 96.3% |
| phase-3-3 (end RuinRecreate #1) | 2128/2216 | 96.0% |
| phase-4-4 (end Compaction #1) | 2128/2216 | 96.0% |
| phase-5-5 (end Room CH) | 2126/2216 | 95.9% |
| phase-6-6 (end Room LS = FINAL) | 2126/2216 | 95.9% |
| `lessons.txt` (final) | 2126/2216 | 95.9% |

Non-anchored set deltas:
- At phase-2 end (earliest): **82** non-anchored.
- At phase-6 end (final): **90**.
- Introduced during LS+compaction (phase2 -> phase6): **14**.
- Resolved during LS+compaction: **6**.
- Net drift across ALL LS phases: +8 (82 -> 90).
- Room phases (phase 5-6): **0 introduced, 0 resolved** (90 -> 90, identical set).

**The non-anchoring is established before phase 2 even runs.** The 82 at the earliest dump is already post-construction + 1 hour of LS compactness; the residual drift during the remaining 2.5 hours of LS/RuinRecreate/Compaction is tiny (+8 net, and bidirectional: 14 in, 6 out, confirming LS is churning near an equilibrium it cannot escape). Room phases touch nothing.

The phase-1 (construction-end) dump is not in `phase-transitions/`, but the `phase-markers-log.txt` confirms the construction shape directly:

```
Phase 1 CH:  "Weekly-template stamp ... placed 7165, skipped conflict/cap 163"
             "TASK-390/405 compaction ... left-anchored 41"  (CH's own compaction anchored 41 days)
Phase 4 (cycle 1): "TASK-393/405 final compaction ... left-anchored 54"
Phase 4 (cycle 2): "TASK-393/405 final compaction ... left-anchored 14"
```

So construction (template stamp + round-robin + CH compaction) is actively trying to anchor days but leaves a large residual. The two dedicated left-anchor passes inside the construction/compaction phases collectively anchored 41+54+14 = 109 day-start fixes, yet 90 remain.

### Confirming the task note's upstream claim

The task note says HoleRepair logs 0 DE-ANCHORS and SurplusRebalance's M18 veto protects anchors, so the source should be upstream. **Confirmed**: the `phase-markers-log.txt` shows the final HoleRepair run (`H17 hole slots 2 -> 0, conflicts added 0`) and the final state has day-start identical to pre-HoleRepair. The gap is **template stamp + LS residual**, not repair.

### Why LS cannot close it (the M18 weight vs H1 conflict)

M18 (`SchoolScheduleConstraintProvider.java:472`) weighs day-start offset at `HardMediumSoftScore.ofMedium(50)` per slot (TASK-405 retuned 250 -> 50). A +1-slot day costs 50 medium. The 90 non-anchored days contribute roughly 90 x ~1.4 x 50 = ~6,300 medium to the -2.30M medium total, a rounding error in the landscape dominated by M20/M22 (1000/hour).

The reason LS doesn't move these days is **not** that M18 is too weak to "want" the fix. It is that the fix requires dislodging a HARD teacher conflict (H1): every candidate slot-0 placement for the non-anchored group collides with another group already holding that teacher at slot 0 (see mechanism below). The solver would need a multi-entity swap chain that single-move LS selectors don't discover, and even cartesian swap moves don't target this shape directly. The hard tier blocks the move regardless of M18's weight. **Strengthening M18 alone would not close the gap**; the block is at the hard tier.

---

## 5. The mechanism: cross-group teacher contention at slot 0 (root cause)

The dominant case, G11 Wednesday (20 days, 22% of the entire gap), makes the mechanism fully visible. For G11's Wednesday teachers across the run, every one is booked at slot 0 (hour 8) for a DIFFERENT group on the same Wednesday:

| Teacher (G11 Wed subject) | Slot-0 group (hour 8) | Conflicting group |
|------------------------------|----------------------|-------------------|
| T25 (Sviluppo Sostenibile) | G11 at hour 9 | **G03** at hour 8 |
| T04 (Lingua Italiana) | G11 at hour 10/12 | **G02** at hour 8 |
| T05 (Progettazione Termoidraulica / Prep. alternanza) | G11 at hour 10/11 | **G07** at hour 8 |
| T08 (Matematica) | G11 at hour 12/14 | **G06** at hour 8 |
| T23 (Educazione Civica) | G11 at hour 10/13 | **G13** at hour 8 |
| T06 (Informatica) | G11 at hour 11 | **G10** at hour 8 |

Hour-8 occupancy check (sample Wednesday 2026-09-16): 12 groups have a lesson at hour 8 (`G01, G02, G03, G04, G05, G06, G07, G08, G09, G10, G12, G13`). **G11 is the only group NOT in that set.** The first-year groups (G03, G02, G06) and G07 win the slot-0 teacher assignments; G11, which shares its entire Wednesday teacher pool with those groups, is systematically the odd group out.

The golden resolves this differently. For the same Wednesdays, `data/lessons.csv` places:
- 2026-09-16: T25/Sviluppo sostenibile at slot 0 **for G11** (the run gives T25 to G03 instead).
- 2026-10-14 onward: T03/Orientamento at slot 0 for G11 (a teacher the run never assigns to G11 Wednesday slot 0).

So the golden distributes shared teachers across groups so every group gets anchored; the solver's construction stamp assigns slot-0 teachers greedily per-group (in `WeeklyTemplateBuilder`'s "fewest open weekly cells first" order), and G11 loses the contention because it is processed after the groups that grabbed T25/T04/T05/T08/T23 at slot 0.

The same pattern (less starkly) explains the other high-count groups:
- **G04 (21)**: Tuesday/Wednesday/Monday. G04's late starts cluster on days where its aula teachers (T04/Lingua Italiana, T08/Matematica) are contended with the same first-year groups.
- **G14 (6, all Thursday)**: G14's only teachers (Servizio stilistico, Confezione, Competenze Digitali) are shared with the MODA groups; the MODA template wins slot 0.
- **G09 (7 Monday)**: G09's Monday teachers (T20 subjects, Lingua Italiana) are contended with G01/G05 at slot 0.

---

## 6. Golden comparison

Golden day-start: **99.86%** (2210/2213), only **3 non-anchored days** total, all G08 at hour 9 (Tue/Thu/Fri, one each). These 3 are likely the documented FC afternoon-lab shape (FC labs run into the afternoon; a single 9-am start is tolerable).

For our 90 non-anchored (group, date) days:
- Golden **anchors** the same day at slot 0: **84** (genuine gap vs golden).
- Golden has **no lessons** that day: **6** (closure / beyond-horizon edge cases; these are G11 late-June dates where the golden grid is empty, so no anchor is even definable).
- Golden also non-anchored: **0** (no overlap with the golden's own 3 exceptions).

So **84 group-days are a genuine, addressable delta vs golden practice**. The other 6 are edge cases with no golden reference. Every group with non-zero tardivi in our run is at 0% non-anchored in the golden (G04 0/174, G09 0/166, G10 0/159, G11 0/169, G13 0/171, G14 0/29). The gap is not concentrated in groups the golden itself leaves late; it is spread across groups the golden anchors.

---

## Anatomy summary

Of the 90 non-anchored group-days, **0 are structural** (no calendario/no-session/pinned-late reason; all 90 have slot 0 calendar-open and expected hours > 0) and **88 are pure solver-choice** (all-unpinned days; the remaining 2 are mixed-pin edge cases the solver could still anchor). The dominant source is **construction**: 82 of the 90 are already non-anchored at the earliest phase dump (post-construction + first LS), LS/RuinRecreate/Compaction drift the set by only +8 net across 2.5 hours of optimization, and the room phases change nothing. HoleRepair and SurplusRebalance are exonerated (the task note's upstream hypothesis is confirmed). The mechanism is **cross-group teacher contention at slot 0**: in the dominant case (G11 Wednesday, 20 of 90), every one of G11's Wednesday teachers is booked at hour 8 for a different group (G03, G02, G07, G06, G13, G10), leaving G11 as the odd group out; the golden anchors the same days by distributing the shared teachers differently. 84 of the 90 are days the golden anchors, so the gap is genuine and **a lever is warranted**: the block is at the HARD tier (H1 teacher conflict), not the M18 medium tier, so a pure M18 weight change will not move the needle; the lever must act at construction (template slot-0 teacher balancing) or give LS a move that can break the cross-group slot-0 contention.
