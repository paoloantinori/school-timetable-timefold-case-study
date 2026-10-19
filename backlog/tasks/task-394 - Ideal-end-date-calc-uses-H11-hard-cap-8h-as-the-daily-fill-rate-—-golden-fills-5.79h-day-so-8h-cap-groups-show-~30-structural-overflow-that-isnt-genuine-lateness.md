---
id: TASK-394
title: >-
  Ideal-end-date calc uses H11 hard cap (8h) as the daily fill rate — golden
  fills 5.79h/day, so 8h-cap groups show ~30% structural "overflow" that isn't
  genuine lateness
status: Done
assignee: []
created_date: '2026-07-22 21:51'
labels:
  - claude-planned
  - spalmone-2027
  - ideal-end-date
dependencies: []
references:
  - src/main/java/com/school/planner/service/TargetEndDateCalculator.java
  - src/main/java/com/school/planner/domain/StudentGroup.java
  - claudedocs/spalmone-2026-27/data/lessons.csv
documentation:
  - CLAUDE.md (M12 entry; TargetEndDateCalculator / IdealEndDateInfo)
  - TASK-277
  - TASK-344
  - TASK-378 (VRP horizon removal)
priority: high
ordinal: 218000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Finding (laptop validation run-1, 2026-07-22, HEAD 62f10f8e; user-flagged)
`TargetEndDateCalculator.computeGroupTargetDate` computes a group's ideal end date by summing ALL course hours (incl. Stage — intentional per TASK-344) and scanning forward over weekdays, packing `min(SLOTS_PER_DAY=8, group.maxHoursPerDay)` slots/day until cumulative >= totalHours, with BUFFER_WEEKS=0. It uses the group's `maxHoursPerDay` — the **H11 hard cap** — as the assumed **daily fill rate**.

The golden actually fills **5.79 h/day** (G10: 978h / 169 teaching days). So for groups whose cap is 8, the target is computed ~38% too early, and both the golden and the solver "overflow" it by ~30% — not because they're late, but because the target is unachievable. This is the documented M12 "29.3% golden overflow", now root-caused to the fill-rate assumption (not to genuine lateness, not to bad input).

## Evidence (measured against claudedocs/spalmone-2026-27/data/lessons.csv)
| group | maxHoursPerDay | computed ideal | golden overflow past ideal |
|---|---|---|---|
| G02 / G01 / G03 / G05 | 6 (~ real 5.8) | 2027-05-27 | ~4% (G02: 40h/998) |
| G06 | 8 | 2027-03-31 | 272h (~28%) |
| G10 | 8 | 2027-03-23 | 302h/978 (~31%; 172h of it Stage) |

The overflow tracks `(maxHoursPerDay - 5.79)` — nothing G10-specific. Input hours are correct (G10 seeded 970h, golden 978h). Note: G10 has NO StagePattern row (stage handled via DailyActivityQuota + pins per TASK-384), so its stage days are not excluded; the stale comment at TargetEndDateCalculator.java:182 still calls G10/4 FA "5-hour groups" but both are reseeded to 8 (task-343).

## Impact (contained — low risk)
Since TASK-378 removed the VRP horizon filter, the ideal end date drives ONLY M12 (medium) + the UI marker — it no longer restricts solver timeslot choices. So this is scoring/cosmetic, not feasibility. M12 is correctly medium (golden violates the tight target). The deficiency: (a) the UI ideal-end marker is misleading (G10 "should" finish by March, actually finishes in June); (b) M12 fires heavily on normal behavior rather than on genuine lateness.

## Decision needed (primary directive: judge on logical correctness / golden-practice reflection)
The tight target is a DELIBERATE compactness lever (BUFFER_WEEKS=0, "force aggressive horizontal compactness"). Options:
(a) Keep tight, relabel as "theoretical compactness target" + add a separate realistic-completion marker (measured-fill-derived); M12 unchanged.
(b) Make the ideal-end fill rate realistic (measured golden fill per group, or a configurable rate < cap); UI marker accurate, M12 fires only on genuine lateness — but compaction pressure weakens (already weak: solver overflows the tight target today via M12-medium).
(c) Keep as-is and document ~30% overflow on 8h-cap groups as expected structural noise.
Recommend (a) or (b); validate against the golden (a realistic target should drop 8h-group overflow from ~30% toward the ~4% the 6h groups show).

## Riverifica indipendente G06 (Fedora 2026-07-23, user-flagged di nuovo dal timeline marker)

Ricomputazione Python dai seed: conferma il 31/03 al giorno esatto e aggiunge i modelli di capacità.
- Modello del calcolatore (8h flat/giorno): 990h / 8h = 124 giorni scuola dal 14/09 → **2027-03-31 esatto** (la data che il marker UI mostra).
- Capacità calendario reale H20 (pattern G06: Lun 6, Mar 6, Mer 8, Gio 6, Ven 5 = 31 slot/settimana): 990h raggiunte il **2027-05-20**; capacità annua 1.067h → slack solo 77h.
- H20 meno pomeriggi (M28 li riserva allo stage): 990h raggiunte il **2027-06-09**; capacità annua 997h → **slack 7h**, praticamente zero: G06 ha strutturalmente bisogno dell'intero anno, il che spiega anche le 32 ore pomeridiane del suo golden (valvola di sfogo, TASK-379).
- Piano per-data della scuola (calendario_class_days.csv): 990h su 171 giorni, ultima il **2027-06-10**, con **272h dopo il 31/03**.
- Golden effettivo (lessons.csv): 990 lesson-hours, ultima lezione **2027-06-10**, **272h (27,5%) dopo il 31/03** — identico al piano. Le "tante lezioni dopo il 31/03" della soluzione sono comportamento CORRETTO; è il marker a essere il numero sbagliato.

## Touchpoint sul SOLVER, quantificati (2026-07-23, domanda di maintainer "non influenza il solver?")

La data sbagliata entra nel solver da DUE punti, entrambi verificati sul codice e sui run reali:

1. **M12 (medium).** Filtro e peso usano `idealEndDateVrpBuffered` (per G06 ≈ 21 aprile:
   `ensureEnoughSlots` estende il 31/03 col 10% di buffer contando le righe Timeslot GREZZE,
   8/giorno, anch'esso cieco al calendario H20). Contributo misurato nel run reale
   (val398-run2): **−23.912 su −1.409.982 medium = 1,7%** del medium totale. Il gradiente
   spinge nella direzione sbagliata (anticipare vs lo spalmone della scuola) ma è dominato
   da M20 (57%, expected-hours-targeted dal TASK-385) + M22 (38%), che spingono verso il
   piano per-data della scuola, cioè l'obiettivo giusto. NOTA STORICA: la versione GRAVE di
   questo effetto è già stata trovata e rimossa: a peso 1000/giorno M12 era l'80% del medium
   del run 9 e "bendava l'intera ricerca verso un obiettivo anti-golden" (ricalibrato a
   ONE_MEDIUM/giorno il 2026-07-19, vedi javadoc di overflowBeyondTargetEndDate); e il
   filtro-orizzonte VRP che TAGLIAVA la capacità sotto la domanda è stato rimosso da
   TASK-378 (i forensics di allora citano proprio "G06 capped at Apr 21").

2. **Round-robin repair tail della costruzione** (`DayClusteredConstructionPhase:485/503,
   getIdealEndDate → idealEndDateVrpBuffered`): il day-scan del tail si ferma ~21 aprile per
   G06, quindi i leftover che starebbero a maggio/giugno non possono essere piazzati dal
   round-robin e finiscono nel box. Effetto reale ma assorbito a valle: lo STAMP (che fa
   ~8.400 piazzamenti su ~10.500) copre l'anno INTERO senza cap, e il box (52 a fine
   costruzione, tutti i gruppi) viene svuotato da LS+BoxRepair che non hanno alcun orizzonte
   (getAvailableTimeslots senza filtro data dal TASK-378). Il costo è inefficienza di
   costruzione + un lieve bias anticipatorio del tail, non infattibilità.

Il pre-assignment (`buildIdealEndDatesMap`, "mai oltre l'ideal end") è moot nel 2026/27
(LabRotation vuote, pre-assign no-op). Priorità alzata a HIGH: il fix è economico e rimuove
un gradiente sbagliato residuo (1,7% medium), il bound del tail e il marker UI fuorviante.
Da eseguire SUBITO DOPO il gate 401 (cambia gli score medium → romperebbe la comparabilità
dell'A/B in corso).

**Raccomandazione affinata (supera il "measured golden fill" dell'opzione b):** dal TASK-385 esistono i fatti expected-hours per-data per TUTTI i 14 gruppi (`DailyActivityQuota` via `SchoolSchedule.getExpectedHours`). Il fill realistico non va stimato: si cumulano i fatti expected-hours del gruppo fino a totalHours — per G06 dà letteralmente il 10 giugno, il piano della scuola stessa. Fallback al modello attuale solo per gruppi senza fatti (nessuno nel 2026/27). È la stessa mossa del retarget M20 di TASK-385, applicata al calcolatore; riusa infrastruttura esistente e rende marker UI e M12 fedeli all'input autoritativo (direttiva primaria).

## Related work (do not duplicate)
- TASK-277 (open refactor of this same calc: compute buffered FROM ideal + rename). Land alongside/after.
- TASK-344 (Stage counts toward yearly totals — Stage-in-totalHours is intentional; do NOT exclude Stage without reconciling). Sub-question for groups WITH a StagePattern: stage hours are in totalHours but stage days are excluded — a regular-capacity double-count worth examining there, not by excluding stage.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Decision documented (option a/b/c) with the compactness-vs-accuracy trade-off explicitly weighed against golden practice.
- [ ] #2 Golden per-group overflow measured BEFORE and AFTER the change (a scripts/analyze/ script, sibling to daily-fill-kpi.py / moda-knot-count.py); 8h-cap groups drop from ~30% toward the ~4% the 6h-cap groups show.
- [ ] #3 UI ideal-end marker reflects the chosen semantics; M12 behavior matches the decision.
- [ ] #4 GoldenSolutionRegressionTest updated; golden HARD score unchanged (M12 is medium). Full DoD (/simplify, /code-review high, test.sh exit code).
<!-- AC:END -->

## IMPLEMENTATO (Fedora 2026-07-23, autonomo post-gate-401) — opzione (b) via fatti expected-hours

`TargetEndDateCalculator.computeFactBasedIdealEnd`: dove il gruppo ha righe DailyActivityQuota
(tutti i 14 gruppi nel 2026/27) l'ideal end CUMULA il piano per-data della scuola
(aula+lab+stage) fino a totalHours; modello legacy solo come fallback. VERIFICATO LIVE
sull'app locale (profilo dev, JAR fresco): G06 ideal = 2027-06-10 (era 31/03), tutti i
gruppi plan-fedeli (G04 09/04 con domanda 750h, G14 12/10 con la nota questione 115h).
Il buffered segue il fact-based → M12 ora penalizza solo il ritardo GENUINO oltre il piano,
e il bound del repair-tail di costruzione si sposta a fine piano (di fatto sbloccato).
Unit test: TargetEndDateCalculatorFactBasedTest (fact-based vs fallback legacy).

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati — commento esplicativo su Set.of() stageDates (deliberato, non copy-paste: il piano cumulato include già lo stage)
- [x] #2 Skill /code-review high eseguita — coperta dal pass combinato 394+405 (agente simplify quadruplo-angolo + skeptic Opus); golden guard verde (M12 esente-pinned, invariato)
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore — test.sh EXIT=0 (700/700) + verifica LIVE del marker sull'app (G06 10/06)
<!-- DOD:END -->
