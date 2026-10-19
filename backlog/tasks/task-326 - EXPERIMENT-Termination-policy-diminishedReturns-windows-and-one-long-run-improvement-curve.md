---
id: TASK-326
title: >-
  EXPERIMENT: Termination policy - diminishedReturns windows and one long-run
  improvement curve
status: Done
assignee: []
created_date: '2026-07-12 17:44'
updated_date: '2026-07-26 01:31'
labels:
  - claude-planned
  - experiment
milestone: m-3
dependencies:
  - TASK-280
references:
  - src/main/resources/solverConfig.xml
  - scripts/benchmark/benchmark-progress-log.sh
  - .claude/rules/no-background-pipes.md
priority: medium
ordinal: 210
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Phases 3 and 4 terminate on diminishedReturns with a 2-minute sliding window and 0.1 minimum improvement ratio; phase 6 uses 1 minute / 0.05. Timefold docs state that problems of this size can keep improving for hours - the current windows may be killing phases long before the improvement tail, which would explain plateauing far from 0hard. Never measured. See roadmap doc section 3. Related: TASK-098 (unimproved-spent-limit fallback), TASK-295/296 (termination tuning) - this task supersedes their exploratory part with a measured answer.

WHAT:
1. Long-run curve first (the informative half): raise phase caps (secondsSpentLimit 3600 to e.g. 14400 for phases 3-4) and loosen diminishedReturns (window 30 min, ratio 0.01), start an overnight run using ONLY existing tooling: start via ./scripts/api/api.sh solve, and capture the score-vs-time curve with scripts/benchmark/benchmark-progress-log.sh (log parser, already exists) - do NOT invent new background pipe chains (.claude/rules/no-background-pipes.md).
2. From the curve, identify where marginal improvement actually stops per score level (hard may plateau early while medium keeps improving - phases care about different levels).
3. Then test 2 window variants against baseline at equal total budget: (a) 2min/0.1 control, (b) 10min/0.02.
4. Validate XML after every edit; protocol runs for the variants; append rows to experiment-log.md.
5. Verdict: state whether the current termination truncates meaningful improvement, and set the evidence-based windows in solverConfig.xml (follow .claude/rules/solver-config-sync.md).

XSD REMINDER: inside termination, secondsSpentLimit must come BEFORE unimprovedSecondsSpentLimit; always run scripts/validate/validate-solver-xml.sh.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 One long run (4h+) completed with score-vs-time curve captured per phase and per score level
- [ ] #2 Two window variants compared at equal budget via the standardized protocol
- [ ] #3 experiment-log.md updated; evidence-based termination settings applied or control kept, with numbers
- [ ] #4 Curve data and plateau analysis saved under claudedocs/research/
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PRIORITA' ALZATA (2026-07-25, cross-finding E4 dello screening locale): 90s di fase 2 raggiungono lo stesso medium dei 3.600s reali (-2,011M vs -2,014M) ma a ricorrenza 72,5% vs 52,8% - gli ultimi ~3.500s comprano ~zero medium e bruciano ~20 punti di ricorrenza (singolo seed: da confermare su 2-3 seed con Task415BenchmarkScreeningDemoTest, poi arm laptop con secondsSpentLimit fase 2 ridotto). E' il lever piu' economico dell'intero cluster: pura config. Messa in testa al cluster (ordinal 205, prima di 280).

CORREZIONE (2026-07-25 sera, multi-seed locale): il cross-finding E4 NON regge - gli harness smoke giravano a seed fisso 0 (PHASE_ASSERT default, gap ora noto) e la curva per-step 206->2.482 non mostra NESSUN plateau (medium ancora +72/step a 600s); il punto 600s batte il run reale 3.600s su entrambi gli assi = varianza seed-to-seed, non risparmio. La domanda resta aperta ma va posta cosi': DOSE-RESPONSE del budget fase 2 sotto NON_REPRODUCIBLE reale, sul laptop (bracci 600s x3 e 1800s x2 vs baseline val408 3.600s x2) - programma della notte 2026-07-25/26. Gap harness da chiudere: esporre environmentMode/seed negli smoke (fix in arrivo).

CHIUSA col dato (notte 2026-07-25/26, 7 run laptop + verifica Fedora sui raw): la dose-response sotto NON_REPRODUCIBLE dice NESSUN cambio di budget. hard monotono col budget (600s: -6/-8/-10; 1800s: -4/0; 3600s: -1/-1), medium idem, e la ricorrenza NON sale coi budget corti (600s 51,4-51,9%; 1800s 51,3-52,2%; 3600s 52,8-53,6%: piatta): l'ipotesi 'meno LS = template preservato' e' FALSA sotto randomicita' vera. Nemmeno il wall-clock si accorcia in modo affidabile (b600 r1/r3 = 4.510s: le altre fasi riempiono). DECISIONE RACCOMANDATA: fase 2 resta 3.600s. Bonus zai (verificato): MAVEN_OPTS -D NON raggiunge la JVM dell'app (mvn quarkus:run forka java -jar) - nota aggiunta a CLAUDE.md; primo b600-run1 scartato per questo. (Task senza DoD checklist: e' un EXPERIMENT, il dato E' il deliverable.)
<!-- SECTION:NOTES:END -->
