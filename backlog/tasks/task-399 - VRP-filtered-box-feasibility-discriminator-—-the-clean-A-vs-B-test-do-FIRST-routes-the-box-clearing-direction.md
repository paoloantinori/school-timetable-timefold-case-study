---
id: TASK-399
title: >-
  VRP-filtered box-feasibility discriminator — the clean A-vs-B test (do FIRST;
  routes the box-clearing direction)
status: Done
assignee: []
created_date: '2026-07-23 11:03'
labels:
  - claude-planned
  - search
  - construction
  - spalmone-2027
dependencies: []
priority: high
ordinal: 223000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The TASK-398 A/B (HEAD ea35ad83) left a non-empty box at final solve in BOTH runs (run #1: 5 un-taught; run #2: 6 un-taught) → REVERT. The open question is WHY the box doesn't clear: are the boxed lessons genuinely unplaceable (no feasible slot exists) or LS-missed (a feasible slot exists, LS didn't take it)?

The laptop-collection partial check (scripts/analyze/box-feasibility.py, committed) tests only H1/H3/H7/H11 (placement conflicts) — it is INCONCLUSIVE: all 6 run-2 boxed lessons have 109-126 placement-conflict-free cells (lean A), but it does NOT include the VRP gate (calendar H20 / quota H23-H24 / availability H5 / holiday H6 / stage H4,H25), which lives in Java Lesson.getAvailableTimeslots.

WHAT (Fedora, small): for each final-box lesson, check whether ANY of its getAvailableTimeslots() slots is conflict-free (canPlaceLesson + exceedsDailyCap) given the final placements. This is the exact check the spike's HumanFaithfulConstruction.pickFeasibleSlot already does — wire it to run against a live-solved schedule (a small test or a diagnostic endpoint).
ROUTING: if boxed lessons HAVE a VRP-legal conflict-free slot → hypothesis A (LS-coverage miss; a single-hour-targeted repair clears the box). If NONE → hypothesis B (the placements/template deadlock them; relax recurrence rigidity).
Evidence: run-2 box = G03×2, G05×2, G02×2 (T02/Storia×2, T04, T11, T13, T14); all 6 lean A at the H1/H3/H7/H11 level.
<!-- SECTION:DESCRIPTION:END -->

## RISULTATO (Fedora, 2026-07-23) — **VERDETTO: A, ipotesi confermata → TASK-401**

Harness: `BoxVrpFeasibilityDiscriminatorTest` (com.school.planner.bootstrap) — applica il VRP REALE
(`Lesson.getAvailableTimeslots()`: H20 calendario, H5 disponibilità, H6 festività, H4 stage, quote)
+ i check di piazzamento (occupazione docente/gruppo, cap H7/H11 con `getEffectiveMaxDailyHours`
reale, che il Python trattava come illimitato) contro la griglia finale run-2 (artifact statici
committati → deterministico per sempre; ~5s, fixture minimale via `RealSeedFixture` senza generare
le 10k lezioni).

| lezione boxata | VRP-legali | libere | giorni |
|---|---|---|---|
| G03 T04 / Lingua Italiana | 820 | **3** | 3 |
| G03 T11 / Personalizzazione | 995 | **5** | 5 |
| G05 T13 / Educazione Civica | 995 | **7** | 5 |
| G05 T14 / Lingua Italiana | 995 | **4** | 4 |
| G02 T02 / Storia (×2) | 995 | **6** | 3 |

Tutte e 6 hanno celle VRP-legali conflict-free → **A (LS coverage miss)**, e la scarsità (3-7 su
~1.000 VRP-legali, tutte tardo-anno feb-giu 2027) spiega PERCHÉ la LS stocastica le manca: regime
1-su-~200, esattamente il caso che un repair mirato per enumerazione risolve banalmente. Matching
simultaneo di tutte e 6 verificato a mano (es. T04 06-07@13, T11 02-15@8, T13
06-04@12, T14 06-08@13, T02 05-28@12 + 06-04@8 — giorni/gruppi indipendenti).
Corretto anche l'errore del VERDICT.md laptop (box di costruzione = 52 lezioni, non ~1.160: quello era
lo score hard di fine ph1; il caveat "coi pin il box cresce" era invertito — 52 < 141 dello spike).

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati — riuso: seeding docenti+disponibilità estratto in `RealSeedFixture.seedTeachersAndAvailabilityRules` (condiviso con lo spike test invece di terza copia); fixture minimale (niente LessonGenerator, ~5s vs ~80s)
- [x] #2 Skill /code-review high eseguita — fedeltà del fixture argomentata nel javadoc della classe (pin-VRP ⊂ filtro occupazione; gruppi boxati senza StagePattern/quote; festività strutturalmente assenti); predizione A dichiarata prima del run e confermata; asserzioni pinnate su artifact statici
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore — `test.sh -c BoxVrpFeasibilityDiscriminatorTest` EXIT=0; suite completa EXIT=0 (693 test, include il refactor dello spike test)
<!-- DOD:END -->
