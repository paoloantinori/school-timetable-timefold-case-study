---
id: TASK-236
title: Evaluate HashMap iteration order for deterministic solver behavior
status: Done
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-08-04 00:20'
labels:
  - timefold
  - determinism
  - reproducibility
dependencies: []
references:
  - src/main/java/com/school/planner/domain/SchoolSchedule.java
documentation:
  - 'https://timefold.ai/docs/timefold-solver/latest/reproducibility'
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold best practice: "For reproducible solver behavior, use LinkedHashMap instead of HashMap when iteration order affects solver decisions."

Current Issue: SchoolSchedule uses HashMap for several static reference collections (e.g., targetEndDatesStatic, stagePatternsStatic). HashMap iteration order is non-deterministic between JVM runs, which could affect solver reproducibility if these collections are iterated during constraint evaluation or score calculation.

Impact: Non-deterministic iteration can cause:
- Different solver behavior between runs with the same input
- Difficulty reproducing bugs or benchmark results
- Inconsistent constraint evaluation order

Note: Only collections where iteration order affects solver behavior need to be changed. Collections used only for lookup (get/contains) don't need ordering.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Audit all HashMap usage in SchoolSchedule for iteration dependencies
- [x] #2 Identify which HashMaps affect solver behavior vs pure lookup
- [ ] #3 Replace affected HashMaps with LinkedHashMap
- [ ] #4 Verify solver reproducibility with same input produces identical results
- [x] #5 Document which collections require ordered iteration
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
--- Indagine read-only 2026-08-04 (VERDICT-236) ---

VERDETTO: HashMap iteration order NON e' sorgente di varianza run-to-run. IMPATTO BASSO (predizione ex-ante confermata). La varianza e' la LS NON_REPRODUCIBLE di Timefold (REPRODUCIBLE rimosso in Timefold 2.0, non fixable).

Meccanismo (tutto verificato a file:line): (A) entita' hanno hashCode DETERMINISTICO id-based (StudentGroup/Course/Lesson -> id.hashCode(), NON identity), chiavi Long/String/Integer/LocalDate deterministiche, nessun Map<Lesson> identity-keyed; (B) i path di placement order-sensitivi SONO GIA' ordinati esplicitamente (unplacedByCourse = TreeMap by course id DayClusteredConstructionPhase L1089; HoleRepair sort dayKeys per group+date L187 e pool per lesson id L341; layer interni TreeMap<Integer> by slot); (C) costruzione deterministica a seed GRASP fisso (WeeklyTemplateBuilder new Random(graspSeed()) per-build L91); (D) le static-collection di SchoolSchedule (la preoccupazione originale del task) sono LOOKUP-ONLY Long/String-keyed (.get(id)), NON iterate -> no order dependence. I nomi targetEndDatesStatic/stagePatternsStatic del task-2026-03-14 sono STALE (post-TASK-394 sono idealEndDateVrpBufferedStatic/idealEndDateInfoStatic/stageBlackoutDatesStatic, tutte Long-keyed lookup).

AC#1 (audit SchoolSchedule HashMap): FATTO, tutte lookup-only. AC#2 (classifica): FATTO. AC#5 (documenta ordered): FATTO. AC#3 (sostituisci con LinkedHashMap): NON fatto, COSMETICO/INNECESSARIO (lookup non serve ordine; path critici gia' TreeMap/sorted) + GATED maintainer. AC#4 (verifica riproducibilita'): NON fatto, MOOT (la varianza e' LS NON_REPRODUCIBLE non HashMap).

Caveat latente (NON varianza corrente): HashMap order e' praticamente stabile run-to-run (hashCode-id + stessa JDK + stessa inserzione) ma NON garantito da contratto Java; un upgrade JDK potrebbe shiftare i path HashMap-iterati-non-ordinati (es. groupedByStudentGroup.entrySet). Difesa futura (gated maintainer) cosmetica oggi.

Doc: claudedocs/spalmone-2026-27/task236-results/VERDICT-236-2026-08-04.md. Cross-ref: TASK-453 VERDICT (environment NON_REPRODUCIBLE).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
