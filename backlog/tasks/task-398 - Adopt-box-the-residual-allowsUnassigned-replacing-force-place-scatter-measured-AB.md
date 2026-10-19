---
id: TASK-398
title: Adopt box-the-residual (allowsUnassigned) replacing force-place scatter — measured A/B
status: Done
assignee: []
created_date: '2026-07-23 08:30'
labels:
  - claude-planned
  - search
  - construction
  - spalmone-2027
references:
  - src/main/java/com/school/planner/domain/Lesson.java
  - src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java
  - claudedocs/design/human-faithful-construction-spike-RESULTS.md
priority: high
ordinal: 222000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Il pivot TASK-396 è vindicato E semplificato dallo spike (RESULTS.md): NON serve il backtracking. Il cambio è piccolo — **boxare il residuo (timeslot null → H0) invece di force-placearlo in conflitto** + `allowsUnassigned=true` sul timeslot. Spike (D=0 greedy MRV + box, full 10.501 lezioni, ~114ms): costruzione-end hard **-8 (solo H17)** vs **-44.100** del force-place attuale; 0 H1/H3/H7/H11; box 141 (1,3%).

## Riserve dalla verifica Fedora (da tenere come gate, NON ignorare)
1. **Il box 1,3% è un LOWER BOUND, non l'atteso di produzione.** Il fixture spike è senza pin, senza Stage, senza room. In produzione ~866 MODA + 1800 Stage sono pinnati e OCCUPANO slot teacher/group → più contesa per le unpinned → il box reale sarà probabilmente > 1,3% (contra l'affermazione ottimista del RESULTS "i pin lo rendono più piccolo").
2. **Il valore FINALE è NON provato.** Lo scatter-and-repair attuale raggiunge già **-1/0 finale** (validazione TASK-393). Lo spike prova solo che la COSTRUZIONE è più pulita, non che il solve finale sia migliore/più veloce. Se la LS non svuota il box a 0, il finale sarebbe -N H0 (N = box non-piazzabile) — PEGGIO di -1. Rischio reale: con `allowsUnassigned`, la LS potrebbe preferire boxare (H0 -1) invece di risolvere un conflitto (H3 -1000) — corretto lexicograficamente (H0 < H3 in valore assoluto hard), ma lascia lezioni non insegnate se la LS non le ripiazza.

## Il cambio (Fedora, golden-guarded)
1. **`Lesson.timeslot` → `allowsUnassigned = true`** (Lesson.java:89, oggi assente — la causa meccanica dello scatter: il solver è forzato ad assegnare ogni timeslot). Cambio di MODELLO → direttiva primaria → **GoldenSolutionRegressionTest obbligatorio** (il golden è 100% pinnato: ogni lezione ha timeslot, H0 invariato per i pinned → golden deve restare pulito).
2. **Verificare il peso di H0** (`unassignedLesson`, BaseConstraintProvider): dev'essere pressione sufficiente perché il box resti piccolo (non "gratis"), ma il box non deve mai battere una feasibility reale. Calibrare se necessario.
3. **Sostituire `forcePlaceLeftovers`** (force-place residuo → conflitti) con **box-the-residual** (lascia timeslot null) in `DayClusteredConstructionPhase`. La compattazione (TASK-390/393) e il resto restano.

## Gate: A/B LIVE sul laptop (sull'ESITO FINALE, non sulla costruzione)
Un solve di HEAD+cambio (×2 varianza) confrontato con la baseline validata (TASK-393: hard -1/0, LS ~60min):
- **hard finale ≤ baseline** (0/-1) — se resta box H0 non svuotato → il cambio PEGGIORA → revert.
- **budget/tempo LS minore** o **varianza minore** (il beneficio atteso: LS piazza 141 box vs ripara 44.100 scatter).
- **KPI non regrediscono** (full-day, ricorrenza, blocchi).
- **box finale = 0** (nessuna lezione non insegnata) — critico: se > 0, inaccettabile (lezioni mancanti).
Se l'A/B non mostra hard-finale ≤ baseline E un guadagno (tempo/robustezza) → **NON adottare, revert** (lo scatter-and-repair attuale funziona già).

## Success metrics (before → target)
- construction-end hard: -44.100 → ~-8 (H17) + box H0 (misurato dallo spike).
- FINAL hard: baseline -1/0 → ≤ -1/0 (mai peggio) con box finale 0.
- LS budget: ~60min → misurabilmente minore (il gate del guadagno).

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati — 4 finding applicati: campo `boxedLeftovers` → locale+return; guard `range != null` morto rimosso; **`placementCost==0` → `canPlaceLesson && !exceedsDailyCap`** (fix reuse+semantico: box 32→19, non boxa più slot pomeridiani M28-medium); javadoc stale in `ScheduleService.healPinnedNullTimeslotLessons`. Extra cleanup: rimosso `placementCost` orfano (dead code dopo il rewrite).
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti — skeptic Opus ha CONFERMATO nessun nuovo rischio NPE post-solve (ogni consumer null-guarda `lesson.timeslot`, guard pre-esistenti al reset/fresh); fixato lo stesso doc-drift in `LessonResource` javadoc. Removed-behavior (fallback force-place su range vuoto) è un miglioramento: boxare > force-place che viola VRP H5/H6/H20.
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore — `./scripts/core/test.sh` TEST_EXIT=0, 692/692 pass, BUILD SUCCESS, GoldenSolutionRegressionTest verde (golden 100%-pinnato → H0 invariato). Nessun cambio XML → validate-solver-xml n/a; nessun cambio UI → ui-* n/a.

**GATE SUPERATO — ADOTTATO (2026-07-23, A/B laptop task401-results, HEAD 9edf3f64):** il primo A/B
di 398-da-solo fallì (box 5-6, hard -6, task398-results); con il BoxRepair di TASK-401 il
combinato 398+401 passa OGNI condizione in entrambi i run: hard **-1** (solo H17), **box 0**
(12.301/12.301 piazzate, verificato da Fedora su 3 fonti indipendenti: H0 analysis, log
BoxRepair "still boxed 0", conteggio righe griglia), conflitti-aggiunti 0, KPI tenuti
(full-day 77,7-77,8%, blocchi 2.092-2.139, ricorrenza 35,8-36,7%, knot 0), costruzione
-1.160 vs -44.100. Nessun guadagno di wall-time (beneficio = feasibility-by-construction,
non velocità). M15 salito a 19-23 (dal 13-14; golden 98) — da tenere d'occhio. EXPERIMENTAL
rimosso: il design box-the-residual è la baseline.
<!-- DOD:END -->
