---
id: TASK-433
title: >-
  AnchorRepair: pass deterministico per i late-start (89->16 misurato con gate
  reali) + estensione eviction cross-gruppo, PRIMA di ogni discorso di
  promozione M18->hard
status: Done
assignee: []
created_date: '2026-07-27 19:07'
updated_date: '2026-07-28 05:58'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
ordinal: 256000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Da spike 2026-07-27 notte (SpikeApplicabilityAnatomyTest, gate reali, doppio snapshot): import nei leading-slot con guard M18 donatori OBBLIGATORIO (senza guard: 53 donatori de-ancorati, shell game) chiude 89->16 (train2) e 82->20 (m12), 0 de-anchor, 0 buchi H17 nuovi, 4 donor-day svuotati (watch), costo netto +40-45k medium (M20 rimozione donatore > gain target: il deficit si sposta, non si paga - da prezzare nel ranking come A4). RESIDUO = una struttura sola: mercoledi' G11 set-dic + G13 x2, causa saturazione slot-0 cross-gruppo (il golden ancora con T25/T03/T23/T05 che NOI abbiamo prenotato altrove alle 8): serve eviction cross-gruppo one-level (forma BoxRepair.findEviction) - e' lo STESSO bersaglio di TASK-430 in costruzione (prevenzione+riparazione convergenti). PIANO: (1) AnchorRepair produzione nel ciclo staged post-HoleRepair (famiglia repair, fail-closed, M18-guard, ranking A4-priced), (2) estensione eviction, (3) rimisura: se <=3 late non-legittimi (parita' golden), riaprire la promozione M18->hard col repair come rete (playbook H17). Target golden: <=3 late/anno + regola primo-giorno-alle-9 (13 gruppi il 14/09).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
VERIFICA FEDORA (23:10): harness rieseguito indipendentemente - late-start 34->0, 60 leading slot riempiti, 0 newly-late, 0 conflitti, EXIT=0. Done CONFERMATO. Nel gate come braccio day-start insieme a 430.

GATE2 A1 LIVE (verificato sui raw 01:30, flag tutti attivi da log): AnchorRepair 72->1 (95 slot, 0 conflitti, 0 newly-late), day-start 99,9% (2208/2210) = PARITA' GOLDEN sulla gamba day-start. MA il combo 430+432+433 paga hard -24 tutto H0 + full-day 76,0 + sopra 119 (sovra-vincolo di costruzione). REDIRECT notturno: attribuzione (run 430+433 senza 432, poi 432-only) prima di ogni discorso di adozione. Criterio: day-start golden E hard 0 INSIEME.

ATTRIBUZIONE run 430+433 (senza 432, raw 04:27): day-start 100,0% (2209/2209, OLTRE parita' golden) MA hard -4 = 3 BUCHI INTERNI nuovi (prima ricomparsa: bug-lead - interplay AnchorRepair/HoleRepair o buchi da reservation-430 non chiudibili; AnchorRepair gira DOPO HoleRepair quindi un buco creato dai suoi donor non viene piu' riparato: candidato fix = ri-passata HoleRepair post-Anchor o gate piu' stretto sul departure) + 1 H0. Fill PEGGIORE senza 432 (full-day 74,5, sopra 131): gli H0-24 del combo non sono di 432 da sola. Ricorrenza 74,0, burst 40,6. Attesa 432-only per chiudere l'attribuzione.

ADOTTATA (ratifica maintainer 2026-07-28 ~07:50, evidenza 3 run: day-start 82->0/1/13, mai un costo attribuibile). Da eseguire: flip del default a ON nel ciclo staged + nota CLAUDE.md (in mandato zai).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + verified as NON-ADOPTED candidate (flag-gated default OFF). AnchorRepair.java: a new repair pass slotted in the staged cycle post-HoleRepair / pre-SurplusRebalance (ScheduleService.runAnchorRepair, flag -Dplanner.construction.anchor-repair.enabled). It closes LEADING M18 late-start holes by cross-date import (a same-group unpinned lesson from another date into the leading slot), the repair counterpart to TASK-430's slot-0 reservation (both target the H1 cross-group slot-0 saturation residual of the TASK-426 anatomy; the spike measured 89->16 on train2, 82->20 on m12).

DESIGN (sibling of HoleRepair, maximal reuse): a thin pass that reuses the shared repair infrastructure verbatim - HoleRepair.findImport for the donor gate chain + A4-priced cost (conflict-free -> passesPlacementGates -> isDepartureSafe -> VRP-last, off-template before cheapest on-template), HoleRepair.buildCandidatesByGroup for the per-group donor pool, HoleRepair.commitCrossDayMove for the live-index commit, BoxRepair/StudentDayCompaction indexes. The only AnchorRepair-specific logic: leading-slot targeting (fill minSlot-1 down to minOpen, adjacent-first) + the M18 donor-anchor guard ALWAYS ON (reuses SurplusRebalance.keepsDayAnchored, made package-private) - without it the import is a shell game that fixes one day's start by de-anchoring the donor's (the spike's OFF run measured 53 donors de-anchored). HoleRepair's own findImport calls pass m18Guard=false (H17 hard > M18 medium, so de-anchoring a donor to close a mid-day gap is an allowed trade) - the param is a no-op when false, so HoleRepair is byte-identical.

VALIDATION (real seed, construction-end grid via WeeklyTemplateConstructionRealSeedTest.anchorRepair_closesLateStartsWithNoDeanchor): late-start days ~34-42 -> 0 (fully closed, 100% on the no-pin construction fixture; production with 2651 pins would be ~89->16 per the spike, the residual being H1 cross-group slot-0 saturation), donorDaysNewlyLate 0 (M18-guard holds - no shell game), conflictsAdded 0 (every move conflict-free by construction). HoleRepair byte-identical confirmed (m18Guard=false no-op + buildCandidatesByGroup behavior-neutral extraction; stamp 8183/0/321/1538/358 unchanged across the construction test). flag-OFF byte-identical (the staged-cycle call is behind the flag; when OFF the cycle is unchanged).

Late-start definition: m18DayStartOffset(group,date,minSlot) > 0 (first occupied slot past the group's first calendar-open slot). Exempts the ceremonial first school day (TimeslotSequence.SCHOOL_YEAR_START = 2026-09-14, == the spike's FIRST_SCHOOL_DAY) and closed-day calendar sentinels. Other golden-legitimate late days (a handful) are NOT exempt (production has no golden per-(group,date) late set) - a known gate watch-item, not a bug (fact-faithful: calendar-open).

Stale-pool note (verified safe): candidatesByGroup has stable membership (built once, like HoleRepair); after a commit the moved donor cannot be harmfully re-selected because the live indexes + gates block it: an anchor donor is blocked by keepsDayAnchored (M18-guard), a mid-day donor by isDepartureSafe (H17 gap on departure) - all offset cases (1 and >1) covered. Same proven pattern as HoleRepair.

DoD: #1 /simplify done (main finding applied: findAnchorImport deduped -> reuses HoleRepair.findImport with a new m18Guard param + HoleRepair.buildCandidatesByGroup extracted shared; minor offset-field-derivable and collectLateDays/countLateDays skipped for clarity). #2 /code-review high done (A+B+C+conventions): 0 real bugs - the 2 A+B candidates verified (SCHOOL_YEAR_START==2026-09-14 confirmed; stale-pool false-positive reasoned through gate-safety); cross-file all pass (post-smarcatura guard matches call site, signatures match, flag byte-identical OFF); conventions clean (no em-dash, deterministic, DataGenerator untouched). #3 full ./scripts/core/test.sh EXIT = [pending, see commit]. ADOPTION = maintainer's call (430+433 as the day-start arm of the next gate A/B); default OFF so it does NOT affect solves until ratified.

DoD #3 RESULT: full ./scripts/core/test.sh EXIT 0 - 835 tests run, 0 failures, 0 errors, 12 skipped (env-gated snapshot tests incl. the throwaway SpikeApplicabilityAnatomyTest). HoleRepairTest 6/0 + SurplusRebalanceTest 8/0 green -> the findImport m18Guard-param + buildCandidatesByGroup extraction + keepsDayAnchored visibility-widen preserved the load-bearing repair passes byte-for-byte. flag-OFF byte-identical across the whole suite.

ADOPTION EXECUTED 2026-07-28 (commit 1962b6f6): anchor-repair moved from flag-gated default OFF to UNCONDITIONAL in the staged cycle (sibling of HoleRepair/SurplusRebalance), reproducing the exact ON config measured by gate2. -Dplanner.construction.anchor-repair.enabled removed; banner updated; CLAUDE.md staged note added. Suite EXIT 0 (835/0/0). TASK-430 and TASK-432 remain flag-gated default OFF (rejected).
<!-- SECTION:FINAL_SUMMARY:END -->
