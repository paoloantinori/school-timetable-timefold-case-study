---
id: TASK-430
title: >-
  Slot-0 teacher balancing in costruzione: chiudere il residuo day-start (90
  giorni, H1 hard cross-gruppo allo slot 0 - anatomia TASK-426)
status: Done
assignee: []
created_date: '2026-07-27 09:08'
updated_date: '2026-07-27 17:27'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 253000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
L'anatomia 426 (claudedocs/spalmone-2026-27/task426-daystart-anatomy-2026-07-27.md) ha dimostrato che il gap day-start (95,9 vs golden 99,86) NON e' chiudibile da leve medium: 88/90 giorni non ancorati sono solver-choice con CAUSA H1 (il docente della prima lezione e' occupato allo slot 0 su un ALTRO gruppo), concentrati su G11 (32) e G04 (21), mercoledi' 37%. La sorgente e' la COSTRUZIONE (82/90 gia' presenti a fine fase 2). LEVA: nel WeeklyTemplateBuilder, il placement gia' usa una teacher-occupancy map condivisa cross-gruppo - aggiungere al ranking delle celle candidate una preferenza slot-0-first che consideri la disponibilita' del docente allo slot 0 SU TUTTI i gruppi (bilanciamento: se il docente X apre il gruppo A, il gruppo B apre con un docente libero), eventualmente con un secondo passaggio di swap slot-0 tra template di gruppi diversi. Checkpoint-testabile al 100%: WeeklyTemplateConstructionRealSeedTest + kpi-daystart a fine costruzione (baseline 82 giorni non ancorati) + guard ricorrenza (>=50 floor del test) + stamp stats invariati su conflitti. Da fare DOPO m-12 (e' construction-level, fuori dal perimetro medium della milestone).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + verified as NON-ADOPTED candidate material (commits 42fc1e96 + 753c510b H11 fix, pushed). WeeklyTemplateBuilder.reserveSlot0Anchors: flag-gated (-Dplanner.construction.slot0-balancing.enabled, default OFF) pre-pass that, before the per-teacher greedy, distributively reserves one slot-0 anchor per (group, weekday) using a teacher free at slot 0 cross-group, hardest-to-anchor groups first; seeds the live occupancy maps so the UNCHANGED main placement respects it. Attacks the TASK-426 root cause (shared teacher monopolised at slot 0 by first-processed group). Validation: WeeklyTemplateConstructionRealSeedTest passes flag-OFF and flag-ON (guards held: recurrence>=50 75.2%/77.5%, full-day>=65, zero conflicts); construction day-start 98.3%->98.8% (34->25 non-anchored, deep +10/+12 offsets eliminated); stamp skipped-conflict/cap 0 both runs (8183->8487 placed). Improvement modest (anatomy-predicted: residual is H1-contended, not fully closeable by a greedy lever). DoD: /simplify done; full test.sh EXIT 0; /code-review high = skeptic agent CONFIRMED H1/H3/H7/quota-kind conflict-free + weeklyTargets accounting + flag-OFF byte-identical (independent empirical verify: flag-ON stampSkipConflict=0), with 1 PLAUSIBLE finding (H11/day-target ceiling not checked) FIXED in 753c510b (mirrors tryPlaceChunk 471-483; behavior-preserving, day-start unchanged 98.8%). ADOPTION = maintainer's call on the next ratification round; default OFF so it does NOT affect solves until ratified.
<!-- SECTION:FINAL_SUMMARY:END -->
