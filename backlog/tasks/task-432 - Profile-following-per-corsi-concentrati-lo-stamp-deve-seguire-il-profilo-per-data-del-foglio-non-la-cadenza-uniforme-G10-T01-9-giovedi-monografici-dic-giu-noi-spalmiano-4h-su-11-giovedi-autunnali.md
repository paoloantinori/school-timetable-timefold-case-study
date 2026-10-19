---
id: TASK-432
title: >-
  Profile-following per corsi concentrati: lo stamp deve seguire il profilo
  per-data del foglio, non la cadenza uniforme (G10/T01: 9 giovedi'
  monografici dic-giu, noi spalmiano 4h su 11 giovedi' autunnali)
status: Done
assignee: []
created_date: '2026-07-27 15:39'
updated_date: '2026-07-27 23:29'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
ordinal: 255000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
INFERENZA GOLDEN (2026-07-27, 'se il golden lo fa possiamo farlo anche noi'): per G10/Servizio tecnico e stilistico il golden usa 9 giovedi' INTERI (5/7/8h) dic-giu, ESATTAMENTE dove calendario_class_days prevede 5/7/8h - la scuola codifica i giorni monografici nel foglio. Il nostro WeeklyTemplateBuilder/stamp alloca cronologicamente con cap per-data: brucia le 65h sui giovedi' autunnali (4h attese) e lascia in deficit i giovedi' invernali ad alta attesa -> +4h surplus x11 autunno + deficit inverno = grossa fetta del gap G10 (56 giorni full mancanti, 42% del residuo). Lo SCORE gia' preferisce la forma golden (giorno invernale vuoto = m20DeficitCost fino a 22.000 vs surplus M33 400): il blocco e' costruzione+ricerca (servirebbero mosse bulk cross-data). LEVA (construction-level): nella pianificazione del template/stamp, per i pair (gruppo,materia) il cui profilo atteso per-data e' NON-uniforme (varianza alta della quota nei giorni compatibili col docente), allocare le ore seguendo il PROFILO (prima i giorni ad attesa alta, in ordine di attesa decrescente poi cronologico) invece della cadenza uniforme; il caso generale: quota del giorno molto sopra la media del weekday = giorno monografico inteso dalla scuola. Checkpoint chain: WeeklyTemplateConstructionRealSeedTest + misura dedicata del profilo G10 giovedi' (ore T01 per data vs attese; target: surplus autunno ->0, giovedi' dic-giu pieni), daily-fill G10, ricorrenza guard, stamp stats. Famiglia costruzione come TASK-430; candidato stesso giro.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PRINCIPIO GUIDA (maintainer, ~14:20, vale oltre questo task): i pattern 'hard umani' sono best-effort - si trattano come hard finche' i limiti matematici non impongono di gestire il residuo come ECCEZIONI che possono rompere il pattern; ma in quei casi si ESAURISCE il pattern regolare al 100% e si gestisce SOLO il rimanente come extra - mai usare l'esistenza delle eccezioni per DILUIRE il consumo del pattern originario. Per questo task: consumare il profilo per-data del foglio integralmente (i 9 giovedi' monografici al 100%: e infatti 5+5+7+8x6=65 ESATTE, resto zero - il foglio e' il piano ora-per-ora), e solo un eventuale eccesso oltre il profilo diventa eccezione esplicita fuori pattern. Implementazione: allocazione a due fasi (1: exhaust profilo in ordine attesa-decrescente; 2: remainder come extra marcato), MAI proporzionale-diluita.

GATE2 A1: sospetto principale per gli H0 24 e il fill degradato del combo (profile-following devia ore verso giorni ad alta attesa che la costruzione poi non riempie?). Attribuzione in corso stanotte (run 432-only). Non adottare prima dell'esito.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + verified as NON-ADOPTED candidate (flag-gated default OFF). DayClusteredConstructionPhase.reserveProfileFollowingPlacements: -Dplanner.construction.profile-following.enabled pre-pass inside stampWeeklyTemplate, after unplacedByCourse/availableTimeslotsByCourse, before the chronological stamp loop. For each "must-concentrate" course (per-date expected-hours profile non-uniform: max-modal>=2, AND teacher single-weekday via VRP candidate dates), it sorts the VRP-legal dates by descending expectedHoursCapFor (chronological tiebreak) and fills each to its expected-hours quota (gated on canPlaceLesson H1/H2/H3 + exceedsDailyCap H7/H11 + expectedHoursCap pseudo-netted, exactly like the stamp), polling the per-course queue so the unchanged chronological stamp skips the consumed lessons.

KEY DESIGN: the single-weekday filter is the scoping mechanism. Without it, ALL of a group's courses compete for the same group-total (aula+lab) high-quota days and whichever sorts first alphabetically grabs them, starving the concentrated subject (measured: T01 got 0h on monographic Thursdays, 65h on autumn, before the filter). The deeper abstraction would be subject-kind-aware quotas (labHours for lab via Course.isLab, aulaHours for aula), but getRoomTags/isLabSubject are private in the protected DataGenerator and isLab is unset in the construction fixture, so single-weekday approximates "the subject the school concentrates" (canonical case: T01 Thursday-only via VACATION).

VALIDATION (real seed, construction-only): flag-ON concentrates G10 / Servizio tecnico e stilistico (T01, 65h) onto the 9 monographic Thursdays (12-03/10 5L, 12-17 7L, 01-07/14/21 8L, 05-20/27 8L, 06-03 8L = 5+5+7+8x6 = 65h EXACT) and leaves the autumn 4h Thursdays free: monographic=65h, other=0h (baseline flag-OFF: 24h monographic / 41h other). Recurrence holds 74.7% (== flag-OFF; the monographic days cluster weekly-consistently Dec/Jan/May-Jun). Zero accepted H1/H3 conflicts (final-population check). stampSkipConflict 84 (template cells of multi-day courses displaced on the filled days - expected, repaired by the round-robin tail), down from 665 before the single-weekday filter. flag-OFF byte-identical (stamp 8183/0/321/1538/358, recurrence 74.7%, full-day 86.3% == pre-change baseline measured alone).

M21 note (verified moot): the 8h monographic days are a single subject, which M21 (max 2h/subject/day) would penalize - but T01's "Servizio tecnico e stilistico" is a LAB course (getRoomTags -> "acconciatura" -> isLab=true in production), and M21 exempts lab lessons, so no M21 penalty in production. The construction fixture (construction-only, no score) doesn't evaluate M21 regardless.

DoD: #1 /simplify done (4 fixes applied + verified: single-weekday check moved early for efficiency; modalKey deleted -> reuse WeeklyTemplatePlanner.mostFrequentKey made package-private; assertNoTeacherGroupConflicts extracted from both test methods; isLab-proxy javadoc added). #2 /code-review high done (4 finder angles: line-by-line, removed-behavior, cross-file, conventions + verify): 1 finding FIXED (flag isolation - System.clearProperty added to clean() belt-and-suspenders with the try/finally), M21 concern REFUTED (lab-exempt), 2 findings REFUTED (mostFrequentKey documented precondition all-callers-compliant; double-execution single-call-site). #3 full ./scripts/core/test.sh EXIT = [pending, see commit]. ADOPTION = maintainer's call on the next ratification round (430-ON + 432 + optional M35=100 in one A/B x2); default OFF so it does NOT affect solves until ratified.

DoD #3 RESULT: full ./scripts/core/test.sh EXIT 0 - 833 tests run, 0 failures, 0 errors, 11 skipped (BUILD SUCCESS, 2:07 min). flag-OFF byte-identical confirmed across the whole suite (no regression from the WeeklyTemplatePlanner.mostFrequentKey visibility widen or the flag-gated pre-pass).
<!-- SECTION:FINAL_SUMMARY:END -->

## REVISIT SPIKE 21/09 (workflow zero-hard, reevaluate-past-rejections)

La bocciatura del 28/07 (harness vecchio: 24 H0 T01, -full-day) merita
ri-valutazione: il paesaggio e' cambiato (636 ha introdotto la pressione
golden-aligned in costruzione; il catalogo 634 permette misure senza cicli).
SPIKE BANCO (test dedicato + intera firma di costruzione, flag
planner.construction.profile-following.enabled=true): il meccanismo e' VIVO e
SANO su codice attuale: T01/G10 concentra 65h in giovedi' monografici
(>=5h) vs baseline ~24h monografiche + ~37h sparse; ricorrenza e guardie del
test verde; M-TR e giugno-Servizio INVARIATI (2696 celle, 8h: nessuna
interazione negativa col 636). La vecchia infeasibilita' era a livello di
consegna (promozione a pin): il verdetto richiede comunque un A/B ciclo
completo, MA la priorita' passa dietro al 636 (stesso obiettivo di
concentrazione, perimetro piu' generale data-driven) per evitare due cure
sulla stessa famiglia. Da riaprire solo se il 636 da solo non chiude la forma.
