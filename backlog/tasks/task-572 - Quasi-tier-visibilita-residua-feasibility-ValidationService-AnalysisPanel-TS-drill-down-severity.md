---
id: TASK-572
title: >-
  Quasi-tier visibilita' residua: /feasibility, ValidationService, AnalysisPanel
  TS, drill-down severity
status: Done
assignee: []
created_date: '2026-08-29 13:32'
updated_date: '2026-09-02 09:00'
labels:
  - claude-planned
  - quasi-tier
  - tech-debt
milestone: m-13
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Superfici che confrontano o espongono lo score restando sul vocabolario pre-BendableScore, emerse dalla /simplify M3-fix (agent Altitude, 29/08). Con flag OFF (produzione) sono innocue (quasi sempre 0); con planner.constraints.quasi-tier.enabled=true nascondono il livello quasi.

1. /feasibility (ScheduleResource ~296): predicate hardOf==0 pure-only + FeasibilityReport senza slot quasi. DECISIONE SEMANTICA pendente (il quasi blocca la fattibilita' operativa?): deciderla all'esito del ciclo TASK-538.
2. ValidationService (~207,233): ValidationResult persiste triple hard/medium/soft; le famiglie quasi sono invisibili al badge e al diff newlyViolated. Fix: campo quasi nel DTO (specchio della /analysis gia' sistemata).
3. AnalysisPanel.tsx (35,119,444): la /analysis espone quasi ma il consumer non lo legge; i constraint solo-quasi appaiono righe zero non classificate.
4. Drill-down per-lezione (ScheduleService ~4638): severity hardOf<0?HARD:MEDIUM e absScore sommano senza quasi.
5. formatFullScore display: quasi invisibile nella stringa compatta (cosmetico).

Riferimento: /simplify 29/08 4 agenti, commit M3-fix seguente. Nessuna urgenza prima dell'esito del ciclo.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PUNTI 2-5 IMPLEMENTATI (1/09 notte, zai, commit 081778d3 verificato Fedora): (2) ValidationResult DTO + ValidationService con campo quasi (top-level e ConstraintCount, specchio /analysis) + repository restore quasi=0 per le righe persisted pre-campo; (4) drill-down severity con QUASI fra HARD e MEDIUM + absScore che include quasiOf; (5) formatFullScore con quasi visibile solo se non-zero (a flag OFF identico a prima); (3) AnalysisPanel ERA GIA' quasi-aware (M3-fix): verificato, non toccato. Punto 1 (/feasibility semantica) resta FUORI come da decisione legata a TASK-538. Gate: ui-typecheck EXIT 0 + ui-build EXIT 0 + suite 1244/0/0/39 EXIT 0. Log f7a/task572/. Il task resta aperto solo per il punto 1.

PUNTO 1 CHIUSO dal verdetto TASK-538 (2/09): /feasibility non deve rappresentare nessun quasi-tier (il tier e' morto col verdetto negativo): il metro pieno resta l'unico segnale e il comportamento attuale e' gia' quello giusto: NIENTE da cambiare. Il task muore con questo punto: punti 2-5 gia' fatti (081778d3). Chiusura documentale.
<!-- SECTION:NOTES:END -->
