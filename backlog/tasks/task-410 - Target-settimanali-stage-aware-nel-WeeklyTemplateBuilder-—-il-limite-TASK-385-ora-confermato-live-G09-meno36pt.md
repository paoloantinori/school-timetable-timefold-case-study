---
id: TASK-410
title: >-
  Target settimanali stage-aware nel WeeklyTemplateBuilder — il limite TASK-385
  ora confermato live (G09 −36pt)
status: Done
assignee: []
created_date: '2026-07-24 11:40'
updated_date: '2026-07-24 14:09'
labels:
  - claude-planned
  - construction
  - spalmone-2027
dependencies: []
priority: high
ordinal: 234000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Il gap full-day residuo è CONCENTRATO nei gruppi con finestra stage (misura 408, per-gruppo
golden vs val406-run1): G09 59% vs 95% golden (−36pt), G13 74 vs 89, G10 82 vs 94,
G07 94 vs 100. È la firma del limite già documentato nelle note TASK-385:
`DayClusteredConstructionPhase.countActiveWeeks`/`buildWeeklyTargets` spalma le ore del corso
su TUTTE le settimane dell'anno invece che sulle sole settimane NON-stage del gruppo → target
settimanale strutturalmente basso → giorni sotto-riempiti per costruzione.

WHAT: rendere il calcolo delle settimane attive stage-aware — per i gruppi con fatti stage
(`DailyActivityQuota.stageHours>0` / groupHasStageFacts, TASK-384) escludere dal conteggio le
settimane interamente-stage (e pro-rare le settimane miste), così `remainingUnpinnedHours /
activeWeeks` produce il target reale delle settimane di lezione. Riusare le facts esistenti,
nessuna nuova persistenza. Attenzione al fixture: `WeeklyTemplateConstructionRealSeedTest`
non seeda Stage (limite documentato) — il suo full-day SCOPED dovrebbe salire verso il 65%
originariamente atteso dal brief 385; aggiornare le soglie misurando, non gonfiando.

Validazione: real-seed test (recurrence ≥ soglia, conflitti 0, full-day scoped ↑), golden
guard invariato, A/B laptop con daily-fill-kpi + confronto visivo (le strisce dei 4 gruppi
stage devono avvicinarsi al golden).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh) + A/B laptop + riverifica visiva
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-24 11:31
---
PART 1 implemented (2026-07-24, laptop). countActiveWeeks now stage-aware (double return): non-stage groups unchanged integer ISO-week count; stage groups (groupHasStageFacts) pro-rate each ISO week by teaching-day fraction (fully-stage week weight 0, mixed week fractional), keyed off DailyActivityQuota aula+lab>0 vs stage-only. allocateLargestRemainder activeWeeks int->double (backward-compatible: existing int-literal unit tests widen fine). isOpenSchoolDay helper factored to prevent drift. DoD: /simplify (5 fixes applied: redundant cast, weekTally naming, javadoc trim, stale micro-course comment, change-log comment), /code-review high (4 finders: 0 bugs, 0 violations), test.sh EXIT 0 (708/708, GoldenSolutionRegressionTest green - golden pins unmoved). Construction fixture SCOPED full-day 83.5%->85.3% (at golden 85.4%), deficit 590->568h, G08 +17pp. NOTE: the mission's cited 9-45%/54.2% construction baseline was STALE (TASK-385-era); current HEAD already showed stage groups 71-95%/83.5% due to TASK-390/398/401/405/406 compaction+repair-tail work, NOT stage-awareness. The FAST SIGNAL was already green; TASK-410's construction delta is modest (+1.7pp) because the deficit-aware repair tail masks the weekly-target miscalc in the fixture. Real validation = PART 2 live solve. Modal weeklyTargetTotal checked healthy (25-26, not depressed) so no modal change needed. Pro-rate validated against ground truth (G07 = 38 raw weeks x 134/172 = 29.6 active).
---

created: 2026-07-24 14:09
---
PART 2 A/B x2 done (2026-07-24, laptop). Both fresh dev solves: run1 -10hard/-1.783M, run2 -12hard/-1.798M (full 6-phase, ~67-70min each). Systematic, not variance (both in same band). Cumulative gate PARTIAL: fails hard(box 9/11), M20(-938/-958k toward -786k), M20.2(32/31 empty days toward ~0), marginal full-day(78.3/77.6%)/day-start(94.0/94.8%); PASSES recurrence(36.5-36.8% >35 floor), blocks(~2150-2167), G05 knot(0/0). PER-LEVER DIAGNOSTIC EXONERATES TASK-410: M20.2 empty days dominated by G04 (23/18) - a quota group with NO Stage course (410 does not touch it; pre-existing structural floor); box dominated by G06 (10) + G01 (1) - non-stage (410 does not touch). TASK-410 stage groups have few empty days (1-6 each). Teacher fingerprint confirms Q22 working (T01 100% Thu, T07 100% Wed - match golden). Worse-than-406 numbers physiological: M20.2 new measurement (+167-177k), Q22 stricter-correct golden-derived. Per primary directive: model changes logically correct, residuals are search-domain (TASK-408 M22 -576k, BoxRepair tuning, G04 capacity question for school). VERDICT: ADOPT TASK-410 (clean, exonerated), cumulative gate PARTIAL/no reverts. Artifacts + confronto HTMLs in task410-results/.
---
<!-- COMMENTS:END -->
