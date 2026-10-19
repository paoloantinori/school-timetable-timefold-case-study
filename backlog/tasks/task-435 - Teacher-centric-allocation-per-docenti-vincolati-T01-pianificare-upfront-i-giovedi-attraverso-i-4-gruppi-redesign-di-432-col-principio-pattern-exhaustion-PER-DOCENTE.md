---
id: TASK-435
title: >-
  Teacher-centric allocation per docenti vincolati (T01): pianificare
  upfront i giovedi' attraverso i 4 gruppi - redesign di 432 col principio
  pattern-exhaustion PER-DOCENTE
status: Done
assignee: []
created_date: '2026-07-28 05:45'
updated_date: '2026-07-30 05:04'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
ordinal: 258000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RATIFICATA da maintainer 2026-07-28. IL NODO SISTEMICO della campagna e' uno: T01 (231h, 4 gruppi, SOLO giovedi' per H5). Evidenza convergente: 24 H0 di 432-per-gruppo (gate2-A1), 40 H0 di G06 da order-fragility (gate2-433only post-434), coda +4h di 427 (11 giovedi' G10 a 8h vs foglio 4h), giovedi' monografici golden (9 date dic-giu per G10, profili foglio ESATTI al monte-ore: 65h; idem le altre). MECCANISMO DEL FIX: PRIMA della costruzione per-gruppo, allocare le ore dei docenti Thursday-only (generalizzabile: docenti con disponibilita' settimanale <= 1-2 giorni) sulle date, seguendo il principio pattern-exhaustion PER-DOCENTE: esaurire il profilo per-data dei fogli (che per T01 torna esatto attraverso i 4 gruppi: il golden lo dimostra) al 100%, eccezioni esplicite solo per il residuo; l'allocazione diventa vincolo/riserva per il WeeklyTemplateBuilder dei 4 gruppi (celle riservate teacher-aware, mai contese). Flag-gated default OFF come da prassi, catena checkpoint: WeeklyTemplateConstructionRealSeedTest + harness dedicato (ore T01 per data vs profilo foglio/golden su TUTTI i 4 gruppi; attesa: 0 boxed T01, giovedi' monografici rispettati, surplus +4h di 427 azzerato) + suite. DoD piena. Sostituisce il fill-gain perso bocciando 432 (il profile-following corretto e' teacher-first, group-second).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Flag planner.pre-assign.constrained-teacher.enabled default OFF; flag-OFF byte-identical to baseline (pre-assignment output + WeeklyTemplateConstructionRealSeedTest + full suite green, no behavior change).
- [ ] #2 Flag ON: T01's lessons are PINNED (firm, pre-solve) by a constrained-teacher CSP phase, not LS-movable - the human-planner 'pin them early, only move what is left out' principle (human-planner-heuristics.md line 68), applied to the most constrained variable.
- [ ] #3 Flag ON: T01 assigned to exactly one group per Thursday (his 4 groups' Thursdays disjoint), as the golden demonstrates.
- [ ] #4 Flag ON: G10 65h respected on the 9 monographic Thursdays (2026-12-03/10/17, 2027-01-07/14/21, 05-20/27, 06-03).
- [ ] #5 Flag ON: 0 boxed (H0) T01 at construction end, and a live staged solve shows the -41hard -> 0 (the gate2-run433only post-434 residual eliminated).
- [ ] #6 Flag ON: TASK-427 +4h autumn surplus eliminated (no Thursday over the sheet profile).
- [ ] #7 Generalizable: the phase covers any teacher with <=1-2 available weekdays (H5 VACATION-derived), not hardcoded to T01.
- [ ] #8 Live staged solve (flag ON): KPIs held (full-day ~78, day-start >=99, ricorrenza >=72, emptySpan G04 <=1).
- [ ] #9 DoD piena: /simplify + /code-review high (candidates verified Opus) + ./scripts/core/test.sh EXIT 0 from the orchestrator.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DESIGN REVISED 2026-07-28 (maintainer: human planners pin T01 early; "pin them, only move what is left out", human-planner-heuristics.md line 68). Supersedes the earlier construction-reservation design.

HUMAN-PLANNER PRINCIPLE (the reframe): T01 is the most constrained variable (Thursday-only, 4 groups, 231h). Humans hand-place him early and consider him pinned/quasi-pinned. We currently do the OPPOSITE: verified 0/231 of his lessons pinned; he is NOT in pre-assignment (which scopes only lab blocks + professionalizzanti-on-stage-days); his only special handling is H5 Thursday-only VACATION, the same minimal treatment the docs give T28 (line 29/115), which suffices for T28 (1 group) but fails for T01 (4 groups, contended) and produces the -41hard. The "pin them, only move what is left out" principle is applied to acconciatura/labs/professionalizzanti/stage but NOT to the single most constrained teacher. That is the gap and likely the real root cause.

REVISED MECHANISM - PRE-ASSIGNMENT PINNING (not construction reservation): a new constrained-teacher CSP phase in HybridPhase1aService, mirroring acconciatura/professionalizzanti. It allocates T01 TEACHER-FIRST across his 4 groups (one group per Thursday, hours from the per-date expected-hours profile, descending quota = pattern-exhaustion PER-DOCENTE), validates each placement 0hard via the CSP, and PINS (lesson.pinned = true) BEFORE the solver runs. The solver then only moves "what is left out". Golden-faithful: the sheet profile sums to 231h, one group per Thursday, 9 monographic dates (all verified), so a pin reproducing it is provably feasible.

FINDINGS (verified, unchanged):
- T01 = 231h / 4 groups (G02 60, G06 60, G10 65, G12 46, subject "Servizio tecnico e stilistico"), Thursday-only (H5). Golden: ONE group per Thursday, disjoint. G10 = 9 monographic Thursdays (2026-12-03/10/17, 2027-01-07/14/21, 05-20/27, 06-03) = 65h.
- Why 432 failed: per-GROUP profile-following, no cross-group teacher view, 4 groups contended the same Thursdays -> 24 H0. 435 fixes it teacher-FIRST and FIRM (pin).
- 427 +4h: a RUN over-placement (solver 8h where golden/sheet 6h on autumn Thursdays); golden has NO autumn G10. Construction/solver-side.

INTEGRATION POINTS (revised, verified file:line):
- New CSP phase in HybridPhase1aService (sequential phases with CSP backtracking + respect-prior-pins; pinning at lesson.pinned=true, lines 1467/1510; BLOCKING ERROR if a phase finds no valid assignment). Place the constrained-teacher phase EARLY (his Thursdays are the scarcest resource).
- PreAssignmentEntityExtractor: add extraction of constrained-teacher lessons (teachers with <=1-2 available weekdays via H5 VACATION), generalized beyond T01.
- Validation: DayAssignmentCsp + PreAssignmentValidator (0hard per placement/block).
- Flag namespace: planner.pre-assign.constrained-teacher.enabled (pre-assign side, not construction), default OFF + diagnostic banner.

SUPERSEDED ALTERNATIVE (for the record): construction reservation via a DayClusteredConstructionPhase pre-pass (the original design) - softer (LS-movable), less human-faithful. Rejected because the humans PIN him, and a soft reservation leaves the LS free to disrupt the very cross-group allocation whose absence caused the contention.

ACTION PLAN:
1. Flag planner.pre-assign.constrained-teacher.enabled default OFF + banner.
2. New constrained-teacher CSP phase in HybridPhase1aService (early): for teachers with <=1-2 available weekdays, allocate hours across available dates TEACHER-FIRST (one group per date, descending quota, exhaust 100% with explicit exceptions for the residual), validate 0hard, pin.
3. Extract constrained-teacher lessons in PreAssignmentEntityExtractor (generalizable).
4. Checkpoint harness: WeeklyTemplateConstructionRealSeedTest + a pre-assignment harness asserting pinned T01 is one-group-per-Thursday, 9 monographic dates, 0 boxed, golden-faithful; flag-OFF byte-identical.
5. DoD piena: /simplify + /code-review high + suite EXIT + harness gate + a live staged solve showing -41hard -> 0.
6. Adoption: flag-OFF baseline -> flag-ON verify -> gate -> adopt.

OPEN DESIGN Q (settle in the plan before coding): (a) FULL pin (pre-assignment, firmest, human-faithful) vs QUASI-pin (pin + LS escape valve) - lean FULL pin (golden-faithful, matches humans); (b) the CSP formulation for teacher-first cross-group allocation + the per-Thursday target derivation from the sheet (sheet gives per-(group,date) expected hours, not per-(teacher,date) - must rediscover the golden's one-group-per-Thursday disjointness); (c) phase ordering vs the existing acconciatura/labs/prof pins.

PLAN GATE: present the concrete plan (EnterPlanMode) AFTER step-3 capture.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-28 11:31
---
FEDORA ASSESSMENT (2026-07-28): APPROVATO. Pre-assignment pinning is the right design (beats template-allocation: it is the human-planner move, reuses the existing pre-assign machine, and authoritative pins enter the TASK-424 netting gratis). TWO BINDING REFINEMENTS: (1) NOT round-robin one-group-per-Thursday - FOLLOW THE PER-DATE SHEET PROFILES (calendario_class_days for 1/2/G10 and G12), which for T01 return EXACT to the hour-total (G10: 9 monographic Thursdays Dec-Jun = 65h, verified; pattern-exhaustion: exhaust the profile 100%, explicit exceptions only for the residual if a profile does not return). If a profile does not specify WHICH Thursdays, DERIVE FROM THE GOLDEN (data/lessons.csv) as the reference. (2) GUARD-RAILS: respect H7 (T01 cap 8h/day) and H5 by construction of the profile; GoldenSolutionRegressionTest UNTOUCHABLE (must stay green); the pinning must be IDEMPOTENT and VISIBLE IN LOGS (pinned count per group/date). ACCEPTANCE (confirmed): a fresh staged re-run with -41hard -> 0 AND day-start/buchi/emptySpan/ricorrenza HELD (no barter); flag-gated default OFF until ratification; DoD piena. VAI.
---

created: 2026-07-28 11:36
---
CORREZIONE METODO (maintainer via Fedora, binding): CHECKPOINT-FIRST. The 435 dev loop is NOT long live runs (too slow); it is: (1) L0 unit on the pinning (profile parsing, idempotency, H5/H7 respect, gates); (2) THE DECISIVE VERDICT is at END OF CONSTRUCTION via a MINUTES-long harness (WeeklyTemplateConstructionRealSeedTest or a dedicated variant) on real seed with pinning ON: boxed-T01=0 and total H0 ~0 at end of CH, existing guards held (ricorrenza >= floor, stamp 0 conflicts, day-start construction not worsened) - iterate the design on THIS short cycle (the 41 H0 are construction-bound, proven BoxRepair 0/41); (3) ONLY at green checkpoint, ONE fresh staged run as final certification (-41 -> 0, metrics held), possibly folded into the next ratification gate. The two refinements (per-date profiles, guard-rails) and the DoD remain valid.
---

created: 2026-07-28 11:38
---
INTEGRATION FINDING (verified 2026-07-28, build-critical): HybridPhase1aService.run() short-circuits to a no-op when LabRotation.count()==0 (line 458) - and the 2026/27 seed has NO LabRotations (deferred to TASK-364), so the CURRENT pre-assign pipeline does NOTHING (confirmed: step-3 pre-assign returned COMPLETE / Candidates Tried 0; the -41hard is pure construction+LS, zero pre-assign pins). IMPLICATION: a 'phase 0' placed before line 478 would be SKIPPED by the 458 short-circuit. BUILD: restructure run() to run the constrained-teacher phase BEFORE + INDEPENDENTLY of the LabRotation short-circuit (move context.initialize() + the existing-lesson-counts cache build before it; the short-circuit then only skips Phase 1/2/3, which are the ones that need LabRotations). ALGORITHM (verified, sheet-driven - no golden needed for the 9 monographic dates): for a Thursday-only teacher, his per-(group, Thursday) target = the group's expected hours on the teacher's H5-available weekdays (SchoolSchedule.getExpectedHoursForGroup). Verified for G10: the sheet shows 5/5/7/8/8/8/8/8/8h on the 9 dates = 65h EXACT = T01's G10 budget, matching the golden hour-for-hour. Assign one group per Thursday (disjoint), H7 cap 8h/day, pin via PreAssignmentValidator.canPin(lesson, timeslot, room) + pinLesson (HybridPhase1aService:1507), idempotent (only unpinned lessons), logged per group/date. Golden (data/lessons.csv via SeedCsv.load) is the FALLBACK only when the sheet is silent on a Thursday. BUILD ORDER (checkpoint-first): (1) L0 unit on the pinning (profile parse, idempotency, H5/H7 respect, flag gates); (2) the phase + run() restructure, flag planner.pre-assign.constrained-teacher.enabled default OFF + banner; (3) construction-end harness as the DECISIVE verdict (boxed-T01=0, total H0 ~0, guards held: ricorrenza >= floor, stamp 0 conflicts, day-start construction not worsened) - iterate on this minutes-long cycle; (4) ONE live staged run as final cert only at green checkpoint. 424 netting: confirmed the new authoritative pins (pinned + real teacher + !promoted) flow into effectiveQuota gratis via SchoolSchedule.setPinnedLessonTimeslotsByGroupStatic.
---

created: 2026-07-28 11:53
---
VERIFIED FINDINGS (orchestrator, real clock + golden data/lessons.csv, 2026-07-28 build-start):
1. WEEKDAY: all 9 'monographic' dates ARE Thursdays (2026-12-03/10/17, 2027-01-07/14/21, 05-20/27, 06-03). An Explore agent mis-reported the first 3 as Fridays - WRONG; verified via datetime.strftime('%A').
2. GOLDEN BUDGETS EXACT: T01 golden rows = G02 60 / G06 60 / G10 65 / G12 46 = 231h, matches incarichi.csv course budgets hour-for-hour.
3. DISJOINTNESS: one group per Thursday, fully disjoint across the 4 groups (35 Thursdays total: 10/10/9/6). This is the cross-group teacher view 432 lacked.
4. MONOGRAPHIC INVARIANT: on EVERY T01 Thursday, golden T01 hours == sheet (calendario_class_days) group-hours that day. When he teaches, he fills the whole day.
5. ALGORITHM CORRECTION (resolves OPEN Q b): the integration-finding formula 'target = group expected hours on available weekdays' is WRONG as-stated - the sheet is teacher-agnostic (Thursday sums 216/210/205/141 per group, not 231). Fedora's escape hatch fires: the sheet gives the HOUR COUNT but NOT which-Thursdays, so the GOLDEN (data/lessons.csv) is the authoritative reference for which-Thursdays, sheet cross-checks the count (concordant on all 35). RESOLVED ALGORITHM: golden-authoritative per-(group,Thursday,hours); pin H of the teacher's unpinned group-lessons to that Thursday (contiguous from slot 0 = golden starts 08:00), validate 0hard via PreAssignmentValidator.canPin, pin via pinLesson, H7 cap 8h/day, one-group-per-Thursday by golden disjointness. FULL pin (authoritative, real teacher, !promoted -> TASK-424 netting gratis via setPinnedLessonTimeslotsByGroupStatic). Idempotent (skip pinned), logged per group/date.
---

created: 2026-07-28 12:39
---
CHECKPOINT VERDICT (step 4 harness GREEN, 2026-07-28 14:37, env PLANNER_TEST_CONSTRAINED_TEACHER=1):
- T01 boxed (H0): 41 -> 0 (pinned 231h on his 35 golden Thursdays, all Thursdays). SYSTEMIC NODE ELIMINATED at construction end.
- final-population conflicts: teacher=0 group=0 (0 ACCEPTED H1/H3; skipConflict=258 is benign pinned-cell displacement, relaxed like TASK-432).
- total boxed (H0): 80 of 10501 (LOWER than flag-OFF baseline 232 - the pre-placement HELPS the search, fewer contended placements).
- recurrence: 76.2% (baseline 76.6%, floor 50% HELD - no barter).
- score: -222hard/-1162451medium (baseline -232hard/-1385736medium).
Phase 0 pinned 303 lessons (T01 231 + T07 72), 0 skipped.

DESIGN REFINEMENTS discovered via the harness iteration (checkpoint-first):
1. TIMESLOT-ONLY pin (room=null): the pin fixes the TIMESLOT (the boxed-T01 residual is timeslot==null); the room is the room-phase 4/5's to assign. Seeding rooms in the fixture was a confound (construction assigned them poorly -> 1325 boxed); timeslot-only removes it and the verdict is clean (80 boxed).
2. canPinHardConstraints(Lesson, Timeslot) - hard TIMESLOT-only veto (H1/H3/H5/H11), SKIPS room (H2/H-M2) and medium-shape (M15/H16): the golden LEGITIMATELY splits a same-subject day around lunch (T01 G10 08:00-13:00 + 14:30-15:30, a 30-min gap M15 penalises) - canPinInMemory vetoed that golden-faithful placement, so the pin must only guarantee no HARD conflict.
3. openSlotsForGroupDay window-convention fix: getGroupCalendarWindow returns [openSlotCount, minOpenSlot, maxOpenSlot] (closed-day sentinel min<0), NOT [min,max] - mirrors SchoolScheduleConstraintProvider:789-792.

DEFERRED to the next ratification gate (Fedora/maintainer): the live staged cert (-41hard -> 0 + full KPIs: full-day ~78, day-start >=99, ricorrenza >=72, emptySpan). Flag stays default OFF. DoD piena in corso.
---

created: 2026-07-28 13:13
---
DoD PIENA COMPLETE (orchestrator, 2026-07-28 15:13):
1. /simplify sul diff 82b03881..HEAD: 4 agent (reuse/simplification/efficiency/altitude) -> applicati loadAllGoldenProfiles (single-pass, era N× re-parse ~325k/run), hoist DB query (StudentGroup/Course/Timeslot/Lesson map), pinLesson->persistPinnedLesson core, drop dead H5 guard + test, fix commento :124, package-private. Ri-verificato: verdict identico.
2. /code-review high: 8 finder angle (3 correttivita + conventions + cleanup), verify recall-biased -> 5 finding confermati corretti: (a) tx produzione via self.get() proxy @Transactional [CRITICAL per il cert], (b) pin-loop slot-stall (3 agent), (c) NPE gruppo non risolto, (d) courseHours put->merge, (e) cold-cache return true->false. + byte-identical flag-OFF restored. Ri-verificato: verdict identico (303 pinned, boxed-T01=0, recurrence 76.2%). Deferred documentati: backstop H6/H25/H26 + H20-cold-start (golden clean verificato; runbook carica statics prima del pre-assign).
3. ./scripts/core/test.sh EXIT=0: 846 test, 0 failures, 0 errors, 13 skipped (env-gated harness). 2:20 min.
Step 1-5 + DoD piena chiusi. Prossimo: run di certificazione finale (staged fresco, flag sulla riga java -jar) per ratifica adozione maintainer.
---

created: 2026-07-28 14:30
---
CERT RUN STATUS (live, 2026-07-28 16:29, profile production truly-fresh, flag on java -jar via PLANNER_JAVA_OPTS):
- App started PROD with -Dplanner.pre-assign.constrained-teacher.enabled=true; Phase 0 ran (flag confirmed active): T01 231 pinned, null_room=0 (ROOM-FIX VERIFIED in production - the cert revealed the timeslot-only pin left T01 permanently room-less: @PlanningPin makes a pinned null room immovable, so the room phase 4/5 cannot assign it; fixed by pinning with findRoomForLesson's room). T07 72 skipped (MODA conflict with calendario pins, correct - solver handles him).
- Calendario import: 2859 pins (MODA+Stage) + T01 231 = 3090 total pinned.
- solve-staged running. Mid-solve at B-TIMESLOT-1: score -928hard, hard FROZEN (H16b -963 dominant = consecutive same-subject different-room pairs of NON-pinned teachers; the LS Compactness optimizes timeslots and cannot fix room-based H16b - only B-ROOM, the LAST staged stage, can). phase-progression: B-TIMESLOT-1 has secondsSpentLimit=3600 (60min) + unimproved 600s; medium keeps improving so it runs toward the 60min cap. Normal staged trajectory (baseline final-confirm -41hard reached after B-ROOM).
- The cert is a ~2h staged cycle. Capture pending completion: ./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/m12-results/final-confirm-435 (then KPI verdict vs -41).
NOTE: the room-fix (pin assigns a room) superseded the earlier timeslot-only /simplify choice - committed 49547591, suite EXIT=0, harness green (boxed-T01=0, no-room=0).
---

created: 2026-07-30 05:04
---
RATIFICATO (maintainer, 2026-07-30 notte): constrained-teacher pre-assignment pin -> DEFAULT ON. Meccanismo: opt-out con -Dplanner.pre-assign.constrained-teacher.enabled=false (stessa forma del ripiego lab M-LF, letto LIVE da HybridPhase1aService.isConstrainedTeacherEnabled; banner veritiero in ScheduleService.refreshSolverStatics). PERCHE' adottato: era l'adozione che rendeva i numeri della campagna non riproducibili col protocollo standard. Senza la pre-assegnazione il triangolo G06 / Servizio tecnico e stilistico / T01 va in overdemand di 30h (capacita 30h vs domanda 60h) e 35 lezioni restano non collocate (boxed 35, slack -30h); con la pre-assegnazione lo slack e' +5h e restano 3 boxed (tutti G06/Barberia/T19, residuo tiny). Evidenza: task459-slack-triangolo-g06-t01-2026-07-30.md. I run di riferimento (447, 30b, 30c) giravano gia' tutti col flag acceso: hard -169, 3 boxed, room-null non-lab 0, full-day 87,5% (88,6% col conteggio KPI corretto dopo il fix sabati). Non serve run di conferma. Modifiche: HybridPhase1aService (isConstrainedTeacherEnabled opt-out + lettura), ScheduleService (banner), WeeklyTemplateConstructionRealSeedTest (isolamento setUp/finally -> setProperty false perche' clear ora significherebbe ON), CLAUDE.md (sezione RATIFICA). DoD piena fatta.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO come CHECKPOINT-CERT (2026-07-28 sera). Verdetto: T01 boxed 41->0, sistemico eliminato alla fonte via pinning pre-assegnazione teacher-centric (ConstrainedTeacherAllocator, 231h pinnate CON stanza sui 35 giovedi golden, one-group-per-Thursday disgiunti). VERIFICA FEDORA SUI RAW (m12-results/final-confirm-435/): lessons.txt conferma 231 lezioni T01 tutte pinned=true tutte di giovedi (231h = budget seed esatto, quindi 0 boxed); score.txt -928hard/-1029287medium; analysis.json decompone -928 = H16b -899 + H17 -15 + H0 -14, quindi la paralisi room-based H16b PRE-ESISTENTE domina (nota: il VERDICT.md cita -963, lettura precedente; il numero della cattura e' -899 - sostanza invariata). Full-day mid-solve 78.2% gia' sul target. SCOSTAMENTO CATTURA: VERDICT.md elenca export.json.gz ma il file NON e' nella directory (protocollo checkpoint TASK-401 non rispettato su questo punto; accettato perche' lo stato mid-solve interrotto ha basso valore di ricostruzione e la ri-cert si fara' comunque post-446-B). ADOPTION GATE DIFFERITO: il full-solve di certificazione (-41->0 + KPI tenute) si fa UNA volta sull'architettura veloce post-TASK-446-B, accorpato all'A/B di quel task; flag planner.pre-assign.constrained-teacher.enabled resta default OFF fino a ratifica maintainer. DoD piena completata (simplify + code-review high con 5 confermati corretti + suite 846 test EXIT=0).
<!-- SECTION:FINAL_SUMMARY:END -->
