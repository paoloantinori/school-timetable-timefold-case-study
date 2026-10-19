---
id: TASK-429
title: >-
  SurplusRebalance netting-awareness (TASK-424 interaction): isSurplusDay
  ammette giorni at-plan-con-pin come donatori
status: Done
assignee: []
created_date: '2026-07-27 07:11'
updated_date: '2026-07-27 17:27'
labels:
  - claude-planned
  - code-review-finding
milestone: m-12
dependencies: []
references:
  - src/main/java/com/school/planner/solver/SurplusRebalance.java
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
priority: low
ordinal: 252000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding CONFERMATO dal /code-review high di TASK-424 (2026-07-27). SurplusRebalance.isSurplusDay (:545) = `occupancy>0 && m20MarginalRemovalCost==0`. Post-TASK-424 (netting di effectiveQuota), un giorno "at-plan con pin" (N pin autoritativi + 1 ora solver, dailyQuota N+1, effectiveQuota 1) ha m20DayCost(1,N+1)=0 e m20DayCost(0,N)=0 -> marginale 0 -> classificato DONATORE. Pre-424 era marginale<0 (non donatore). Effetto: SurplusRebalance sposta l'ora solver a un giorno deficit; il donor va a N pin + 0 solver (occupancy 0) -> il suo deficit diventa INVISIBILE a M20 (filtro totalHours>0). Asimmetria: un giorno at-plan SENZA pin resta non-donatore (marginale 1000), ma CON pin diventa donatore.

SEVERITA' BASSA: deficit aggregato reale invariato (recipient -1, donor +1 = netto 0; e' ridistribuzione da deficit visibile a invisibile). La KPI daily-fill (metrica di adozione, conta ore reali inclusi pin) e' onesta e lo rileva. Solo l'M20 score del solver sottostima.

FIX CANDIDATO: isSurplusDay dovrebbe richiedere `occupancy > effectiveQuota` (surplus genuino netto) non solo `marginal==0`. effectiveQuota e' package-private in SchoolScheduleConstraintProvider (stesso package). Cambia anche il count surplus in measure() (:573).

DIPENDENZA/CONTESTO: TASK-424 (netting) e' corretto e adottato-implicitly (Fedora push + verifica laptop); questo e' il lato consumer. Il live A/B di ratifica m-12 misurera' se impatta le metriche (full-day, sopra). Se il danno e' misurabile, prioritizzare; altrimenti low priority.

DoD: /simplify + /code-review high + test.sh EXIT reale (come ogni task).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + verified (commit 67617fad, pushed). SurplusRebalance.isSurplusDay now `occupancy > effectiveQuota` (was `m20MarginalRemovalCost==0`), the same net-surplus definition M33 penalizes, MINUS M33's `m20Quota>0` scope gate which stays in measure() so a quota-0/stage day remains a LEGAL donor but never a "surplus day" in the DayFill count. Excludes the at-plan-with-pin false-positive donors (occupancy == effectiveQuota, marginal 0 but no real surplus) that hid their own deficit from M20 when lent. Premise verified via m20DayCost line 1857-1858 (placedTotal>0 guard). countSurplusDaysExcludedByNetting() diagnostic added (prints the deflation in one run; 0 on the minimal DEFAULT_SNAPSHOT which has no authoritative pins on surplus days, would report the ~267 mixed days on a real pinned solve). DoD: /simplify done; full test.sh EXIT 0 (832 tests); /code-review high = 3-angle self-review, no surviving findings. NOTE: the first version over-changed (folded m20Quota>0 into isSurplusDay, mirroring M33 too literally) and broke SurplusRebalanceTest.neverMovesPinnedOrPseudoTeacherHours (quota-0 donor exclusion); the full suite caught it, fixed by keeping the gate in measure() only. Live A/B impact not measured (consumer-side of an already-adopted 424 netting; severity low, VERDICT-m12 shows sopra already dropped).
<!-- SECTION:FINAL_SUMMARY:END -->
