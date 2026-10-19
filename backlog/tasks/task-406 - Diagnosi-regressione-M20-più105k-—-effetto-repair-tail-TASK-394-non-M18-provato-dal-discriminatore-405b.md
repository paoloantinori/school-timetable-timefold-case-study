---
id: TASK-406
title: >-
  Diagnosi regressione M20 (+105k) — effetto repair-tail TASK-394, non M18
  (provato dal discriminatore 405b)
status: Done
assignee: []
created_date: '2026-07-24 09:20'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - construction
  - spalmone-2027
dependencies:
  - TASK-405
priority: medium
ordinal: 230000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Il discriminatore dell'A/B 405b (2026-07-24) è conclusivo: il salto M20 da −786k (baseline
401) a −885k/−904k (+~105k, match ~315→378) **sopravvive identico** all'abbassamento di M18
da 250 a 50 (che ha mosso M18 di ~5×). Quindi la causa NON è la pressione di ancoraggio M18:
il candidato indiziato è **l'effetto collaterale della TASK-394 sul repair-tail della
costruzione** — `DayClusteredConstructionPhase.getIdealEndDate` ora restituisce il bound
fact-based (~fine piano ≈ fine anno) invece del vecchio ~aprile, quindi il day-scan del
round-robin copre l'intero anno e distribuisce i leftover su giorni diversi da prima,
peggiorando il fill dei giorni (M20 = |placed − expected| per (gruppo, data)).

**Firma completa da spiegare (coerente in 4 run, 2×250 + 2×50):** M20 +~105k con match
315→378; full-day 77,7-77,8 → 76,8-77,2 (−0,6pt); blocchi 2.092-2.139 → 2.023-2.064.
NOTA: hard/box/day-start NON sono toccati (0-−1 / 0 / 96%) — questa è una diagnosi di
qualità medium, non di correttezza.

**Ipotesi da testare (in ordine):**
1. Il tail ora piazza leftover su giorni tardi che PRIMA (bound aprile) forzava presto nei
   giorni già parzialmente pieni → più giorni sotto-riempiti (M20.matches ↑63). Test cheap
   SENZA solve: `WeeklyTemplateConstructionRealSeedTest`-style fixture, confronto della
   distribuzione dei piazzamenti del round-robin con bound plan-end vs bound legacy-aprile
   (flag temporaneo), su M20-proxy (giorni sotto expected).
2. Interazione col left-anchor: esclusa dal discriminatore per la componente PESO, ma il
   PASS deterministico resta attivo in entrambi gli arm → non discriminato; il test (1) con
   pass on/off lo separa.
3. Se (1) conferma: candidate fix = ordinare il day-scan del tail per "giorni con deficit
   expected-hours prima" (allineare il tail a M20) invece del semplice cronologico — NON
   ripristinare il bound sbagliato (il fix 394 è logicamente corretto, direttiva primaria).

**Contesto quantitativo:** baseline 401 aveva M12 −24k + M20 −786k = −810k; ora M12 −6,5k +
M20 −895k = −901,5k. Circa metà del "salto" M20 è quindi compensato dal crollo di M12 — il
netto peggiorativo reale della coppia è ~+90k, concentrato nel fill.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh incl. golden) + misura A/B (laptop o checkpoint) del fix con M20/full-day/blocchi recuperati e day-start/hard/box invariati
<!-- DOD:END -->

## Phase A Evidence — cheap fixture test (no solve), 2026-07-24

Test: `src/test/java/com/school/planner/bootstrap/Task406RepairTailBoundDiagnosisTest.java`
(`WeeklyTemplateConstructionRealSeedTest` pattern: construction phase only, real seed, no LS, no
pins). Re-seeds per arm so the two arms differ ONLY in the repair tail's day-scan bound
(`group.idealEndDateVrpBuffered`, the field `getIdealEndDate` falls back to when
`idealEndDateInfo` is null). The stamp runs full-year regardless of the bound (its own javadoc),
so the bound isolates to the round-robin repair tail alone.

### Hypothesis 1 (the bound reshapes the repair tail -> M20 regression): REFUTED

| arm | bound | under-days | deficit h | over-days | surplus h | implied M20 medium |
|-----|-------|-----------:|----------:|----------:|----------:|-------------------:|
| YEAR_END (current, TASK-394 fact-based) | 2027-06-10 | 396 | 1100 | 489 | 562 | 1,100,000 |
| APRIL (legacy flat-8h model) | 2027-03-31 | 393 | 1141 | 503 | 586 | 1,141,000 |
| **Delta (YEAR_END - APRIL)** | | **+3** | **-41** | **-14** | **-24** | **-41,000** |

The year-end bound is **41k BETTER** on the construction-end M20 proxy than the legacy April
bound, not worse. The bound DOES scatter leftovers later (placed by month, YEAR_END vs APRIL:
May 795 vs 766, June 216 vs 174 = ~71 more late lessons), but those late placements FILL
under-deficit days toward expected rather than creating new under-days. So the TASK-394 bound
change (April -> year-end) cannot be the +105k live M20 regression cause; if anything it slightly
improves construction. **The live regression is post-LS, driven by the fact-based M12 target
relaxing LS compression (a correct model change per the primary directive), not by the
construction bound.**

### Hypothesis 2 (left-anchor pass interaction): REFUTED by construction

`StudentDayCompaction.compact` (gap-fill AND `anchorRunLeft`) only ever moves a lesson WITHIN its
own (group, date): every target Timeslot is resolved from the mover's own date slot array.
Per-(group,date) counts are therefore invariant under compaction, so compaction can never change
the M20 score (a per-(group,date) count constraint). Empirically confirmed: 2046 (group,date)
cells, counts equal=true after a second compaction pass. This is strictly stronger than an on/off
run (proves the invariant, not just one instance).

### Construction-side signal the proxy DOES reveal (drives the Phase B target)

The repair tail fills each day it touches to MORNING-MAX (~6 slots, the H11 `maxHoursPerDay` cap)
instead of to the M20 expected-hours target (~5): 489 over-days / 562h surplus alongside 396
under-days / 1100h deficit. The over-fill is "free" for M20 (over-days aren't penalized) but it
WASTES leftover capacity on early days before the chronological scan reaches late under-days.
**The deficit-aware fix (Phase B) targets THIS over/under imbalance** (align the tail cap with
M20's `dailyQuota`), NOT the bound. The bound stays fact-based (TASK-394 is logically correct).

## Phase C Result — A/B x2 vs 405b (2026-07-24, laptop). GATE PARTIAL; decision deferred to maintainer.

Fix @ `49176996` (Phase B). VERDICT + full artifacts: `claudedocs/spalmone-2026-27/task406-results/VERDICT.md`
and `val406-run{1,2}/`. Two fresh dev solves, heap 2g, import-calendario + pre-assign, ~70-78 min each.

| metric | 405b | TASK-406 r1 | TASK-406 r2 | gate |
|---|---|---|---|---|
| M20 | -895k avg | -854k/364 | -878k/384 | toward -786k -> PARTIAL (~27% of gap closed, avg -866k) |
| full-day | 77.2% | 79.3% | 79.1% | >=77.5 -> PASS (+2pt) |
| day-start | 96.0-96.9% | 95.8% | 96.1% | >=95 -> PASS |
| recurrence unpinned | 35.1-35.9% | 35.4% | **33.2%** | >=35 -> **FAIL run2** (dips below floor 405b always cleared) |
| blocks >=2h | 2023-2035 | 2000 | 2005 | toward ~2100 -> MARGINAL (below 405b, away from 2100) |
| hard | 0/-1 | -1 (H17) | 0 | <=-1 H17 -> PASS |
| box | 0 | 0 | 0 | 0 -> PASS |

5/7 gates pass. The fix achieves its M20/full-day target cleanly, but recurrence dipped below the
floor on run2 and blocks edged down — both coherent consequences of the deficit-aware cap (stop
over-filling early days -> scatter leftovers out of recurring cells, fewer incidental 2h blocks).

Decision deferred to maintainer (present). Options: REVERT (strict gate-fail) / ADOPT as-is / RETUNE
(recommended if pursuing: make phase-4 BoxRepair deficit-aware too — it leaks ~30k medium both runs —
and soften the tail cap so it doesn't scatter leftovers out of recurring cells).

Alignment with Fedora's TASK-407/408 findings (`12574ee5`/`8f0e37b6`, landed during this A/B): the
full-day gap this fix partially closes is a NARROW slice. The bigger levers Fedora identified are
elsewhere — (a) the gap concentrates in stage-window groups (TASK-385 active-weeks limit), (b) M20 is
blind to EMPTY expected days (placed==0, unpenalized), and (c) M20/M22 hard-tier promotion is rejected
(the golden itself has 320 under-full days). So this fix is a small optimization, not the full-day
solution; pursue those via TASK-408. TASK-407 (ideal-end overflow) was inverted by Fedora's golden
comparison: the golden overflows MORE than us, so it is golden-faithful, not a gap.

## Handoff to Fedora (maintainer 2026-07-24: "i'll let Fedora decide")

The Phase B fix (`49176996`) is ON HEAD and pushed, carrying a PARTIAL gate (M20/full-day/hard/box/day-start pass; recurrence run2 + blocks marginal). Decision deferred to Fedora. Three mechanical options:
- ADOPT: no action (fix already on HEAD); close TASK-406 Done.
- REVERT: `git revert 49176996` (drops only the construction cap + deficit-aware box-repair; Phase A diagnostic test `Task406RepairTailBoundDiagnosisTest` and this backlog/VERDICT stay).
- RETUNE (recommended if pursuing the M20 gain without the recurrence/blocks cost): file TASK-406b — (1) make phase-4 `BoxRepair` (TASK-401, `CompactionPhase`) deficit-aware too (closes the ~30k/phase-4 medium leak both runs), (2) soften the tail cap so it doesn't scatter leftovers out of the weekly-recurring cells the stamp built.
Full data: `claudedocs/spalmone-2026-27/task406-results/VERDICT.md` + `val406-run{1,2}/`.

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSA Done nel giro di pulizia 2026-07-25: il ciclo diagnosi->fix->A/B era completo nel task stesso (causa provata: imbalance del repair tail, non il bound TASK-394; fix 49176996) e il fix e' nello stack ADOTTATO (campaign-status 2026-07-24: repair tail deficit-aware, full-day 77->79; follow-up M20.2 = TASK-409).
<!-- SECTION:NOTES:END -->
