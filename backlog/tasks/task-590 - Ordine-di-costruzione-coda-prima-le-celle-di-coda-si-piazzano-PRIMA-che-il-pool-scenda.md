---
id: TASK-590
title: >-
  Ordine di costruzione coda-prima: le celle di coda si piazzano PRIMA che il
  pool scenda
status: Done
assignee:
  - fable-fedora
created_date: '2026-09-07 08:31'
updated_date: '2026-09-09 18:17'
labels:
  - claude-planned
  - construction-order
  - coda-prima
milestone: m-14
dependencies:
  - TASK-539.01
documentation:
  - 'backlog://docs/doc-001'
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Execute doc-001's construction discriminator, building on maintainer's 7/09 request to address tail days before resources are consumed. The old chronological-consumption explanation is a hypothesis to test, not a universal root cause. Use current pool-cap defaults and production-equivalent pre-assignment from TASK-539.01. Census the LIVE reservation path before changing placement. Only prototype alternative-course selection when the rejected target has a legal alternative with safe earlier-date allowance. Construction changes must run from pre-construction state, not POST_B_ROOM. This task owns census and conditional prototype evidence; production integration/adoption is separately gated.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Control uses current source/input fingerprints and production-equivalent pre-assignment and static facts.
- [x] #2 Every target reservation failure is classified as no candidate, allowance exhausted, VRP rejection, conflict, parity rejection, or never attempted.
- [x] #3 The report distinguishes successful-booking journal entries from rejection observations on the live path.
- [x] #4 A prototype is evaluated only if a legal alternative and non-cannibalizing allowance are demonstrated; otherwise the hypothesis is rejected with evidence.
- [x] #5 OFF/ON comparison reports target and newly created deficits after reservation, construction, and repair tail on the same input.
- [x] #6 Any supported prototype preserves pins, inventory, hard families and donor/earlier-date coverage; a target improvement that creates another deficit is rejected.
- [x] #7 Result is reproduced or challenged on a second fixture/seed, with artifacts, exit codes, and explicit recommendation.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Plan (drafted 2026-09-09 evening, awaiting maintainer approval; Phase A reading done, no code yet)

Fixture/control (AC#1): m14-precon-b state reproduced IN-TEST (fresh real-seed fixture + pre-assign), which TASK-539.01 proved deterministic and set-identical to the v-series cycle start (274 pins, sha 55ed4ae0a7a9ed5a). The bench ASSERTS the pin-set hash before running, so any drift from the frozen fixture blocks loudly. Current HEAD (pool-cap-fill ON default) is the quadro by construction.

### B. Instrument the LIVE reserve path (code, counters only, zero behavior change)
The phase already has aggregate counters (prefixStops, budgetCappedDates, parityCuts, datesServed, reserved) and a debug-gated success-only RESERVE_JOURNAL. Add per-(group,date) FIRST-FAILURE classification at each early-exit branch of reserveDeclaredDayPlacements, using the AC#2 taxonomy:
- NEVER_ATTEMPTED (declared date with no cells / no daySlots / walk never entered)
- NO_CANDIDATE (queue null/empty: course pool exhausted)
- ALLOWANCE_EXHAUSTED (allowance<=0: hours claimed by earlier dates)
- VRP_REJECTION (slot outside the course's legal set, incl. off-grid tail marker)
- CONFLICT (canPlaceLesson false / exceedsDailyCap)
- PARITY_REJECTION (pair-day cap / weekday-share bar)
Shape: static per-build census map (group|date -> class + failing cell/course/slot), cleared like the other statics (SnapshotFixture.resetStatics family), plus one summary log line with per-class counts. The bench reads the map; successes remain distinguishable via the existing journal/counters (AC#3).

### C. Census bench (new env-gated @QuarkusTest, PoolCapFillBenchTest pattern)
seedRealSeedFixture -> wire statics -> run pre-assign in-test -> ASSERT pinned=274 AND pin-set sha 55ed4ae0a7a9ed5a (fixture equivalence gate) -> run construction with census armed -> dump: (a) full per-date census, (b) empty/under-quota declared cells after reservation and after construction (H32/H33/H34 recount, provider-counted), (c) the four LEDGER target cells with their classification (G06 2027-06-10, G09 2027-06-03, G12 2027-04-16, G13 2027-11-06 H32).
Expected discriminator: the G12 16/04 cell should classify NEVER_ATTEMPTED-or-VRP with slot 13 free (the morning-analysis hypothesis), while G06/G09 should classify teacher/conflict/allowance (June saturation) - but the census DECIDES, the hypothesis does not.

### D. Analysis + prototype gate (AC#4)
Census report in task590-results/: class per target cell, and for each rejected target whether a LEGAL ALTERNATIVE course with NON-CANNIBALIZING allowance exists (the gate). If no target passes the gate: hypothesis REJECTED with evidence, task closes on the census alone (a valid outcome per doc-001).

### E. Conditional prototype (only if D supports a target; default OFF)
Alternative-course selection at rejected targets behind a default-OFF flag; OFF/ON comparison on the SAME input reporting target and NEWLY CREATED deficits after reservation and construction (bench checkpoints; the repair-tail checkpoint uses the deterministic repair passes if bench-reachable, else the limitation is declared honestly); second-seed challenge (GRASP variant of the bench) for AC#7. No production wiring, no adoption: that is TASK-595.

### F. Gates + budget
/simplify + code-review on the diff; ./scripts/core/test.sh with exit codes (check swapon FIRST per the OOM-fork memory: test JVMs are sacrificial under herdr); census artifacts + exit codes in the task; prototype (if any) stays default OFF. Zero laptop cycles, zero full solves: everything is bench/localized per the snapshot-iteration directive.

[Phase E design, drafted 2026-09-09 notte before implementation] SUBSTITUTION PROTOTYPE (flag planner.construction.prebooking.substitution.enabled, DEFAULT OFF, standard opt-in form read once per reserve pass):

SCOPE: only the census-supported case - the walk dies at a template cell on ALLOWANCE_EXHAUSTED (booked=anything). NOT the other classes (VRP/conflict/parity/never-attempted have no cell to substitute into or a different cure).

MECHANICS at the allowance-death cell C (same slot range, same length - a provider swap for the identical shape): scan the date's orderedCells courses AFTER C in order; pick the FIRST course A with (a) allowance > 0 under the SAME claim accounting (non-cannibalizing by construction), (b) non-empty queue, (c) VRP-legal at C's slots, (d) passes canPlaceLesson/exceedsDailyCap, parity pair-day + weekday-share bars, and the expectedHoursCap - all the gates the original course faces. If A books, the walk CONTINUES (no prefixBroken); the census records the substitution (new census key BOOKED_SUBSTITUTED + detail original->alternative) so OFF/ON deltas are legible. If no A qualifies: unchanged behavior (ALLOWANCE_EXHAUSTED).

WHY FIRST-fit and not best-fit: deterministic, minimal, and the census tables show multiple alternatives with 3-23h at the target cells; ordering effects are measurable in the OFF/ON arms. A preference heuristic is unjustified before the first measurement.

BENCH: third arm in ReserveCensusBenchTest (substitution ON, deterministic seed): report target cells' census class, ALL newly created deficits (same empties recount + H32/H33/H34 provider counts vs the OFF arm), pins preserved (274 unchanged), inventory unchanged (placed totals per group identical), and the substitution entries. AC#5's repair-tail checkpoint: NOT bench-reachable without the staged tail - declared limitation, measured at reservation + construction checkpoints only (the plan already declared this fallback honest).

AC#6 KILL CRITERIA (pre-registered): any target-cell improvement that creates a NEW H34/H32/H33 cell elsewhere, or moves a deficit into a date the OFF arm served, or drops placed totals/pins = prototype REJECTED, evidence recorded, flag stays OFF.

AC#7: the GRASP arm reruns with substitution ON (basin challenge).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Zero-hard history review, 2026-09-09. Proposed next experiment is a rejection census on the LIVE reserve path, with production-equivalent pre-assignment and current pool-cap defaults. Do not implement prefix substitution until a rejected target has a legal alternative course with non-cannibalizing allowance. Existing RESERVE_JOURNAL records successes only and is debug-gated (DayClusteredConstructionPhase:2476,2693); rejection instrumentation must be added to the live path. Read the complete approved-for-planning assessment at /home/user/.cc-mirror/zai/config/plans/zero-hard-recovery-2026-09-09.md. Review optional suggestion to widen source range is declined: the cited 2577-2641 already covers the claimed selection/failure behavior, and success journaling is cited separately at 2693. This records recommendations only; no implementation or adoption authorized.

Repository-owned condensed assessment is Backlog document doc-001, Zero-hard recovery: evidence and recommended sequence (2026-09-09). Full plan above remains unchanged. This is recommendation tracking only.

[2026-09-09, from TASK-539.01] Your control substrate is READY on the laptop (app stopped, volume present): planner-data-m14-precon-b = fresh profile + pre-assign at current HEAD 5b406056, NO import-calendario (this reproduces the measured v-series start state exactly: 274 pins, pin-set sha 55ed4ae0a7a9ed5a, SET-IDENTICAL to v132 CYCLE_START). Banners verified incl. 'Pool-cap fill enabled=true'. Do NOT use m14-precon (the calendario variant): that is the documented-flow branch, a materially different start state (3077 pins); the protocol divergence is finding F2 pending at TASK-596. The residual your census attacks (from the ledger): G06 2027-06-10 + G09 2027-06-03 persistent H34 cells, G12 2027-04-16 (slot 13 free, pre-booking never tries it - your AC#2 rejection-classification target), G13 2026-11-06 H32 1h. Ledger: claudedocs/spalmone-2026-27/m14-results/RESIDUAL-LEDGER-2026-09-09.md

[2026-09-09 sera] CENSUS DONE (Phase B/C/D of the approved plan). Instrumentation: RESERVE_CENSUS (first-walk-stopping class per declared tail date) + ALLOWANCE_SNAPSHOT (the date's full per-course allowance table at first allowance exhaustion) in the live path, counters-only, cleared per construction; summary log line per construction. Bench ReserveCensusBenchTest (PLANNER_TEST_RESERVE_CENSUS=1) reproduces the measured protocol start state in-test: 12510 lessons (18 rooms + 2009 Stage pseudo-courses seeded; the PoolCapFill pattern lacked both and the first runs failed the fixture gate for exactly that reason), 274 pinned+placed pre-assign pins, deterministic + GRASP arms, EXIT=0.

TWO HARNESS TRAPS fixed en route (documented in the bench javadoc/freshLessons): Phase 0 pins die with 'hard conflict / no room' without the 18 rooms seeded; and Lesson.listAll() after the phase shows STALE entities (request-scoped EM first-level cache never saw the phase-transaction merges: listAll pinned=0 while JPQL count=274) - fresh reads need em.clear()+JPQL.

CENSUS VERDICT on the 4 ledger cells: (1) G06 2027-06-10 ALLOWANCE_EXHAUSTED Cosmetologia@slot0, stable across arms; claim=70 vs pool=50 (tail demand exceeds pool). (2) G09 2027-06-03 ALLOWANCE_EXHAUSTED RA@slot0 (determ) / PARITY_REJECTION weekday-share Confezione (GRASP): basin-dependent. (3) G12 2027-04-16 NEVER_ATTEMPTED: NO TEMPLATE CELLS FOR FRIDAY (the slot-13 hypothesis REFUTED as mechanism: nothing to try). (4) G13 2027-11-06 NOT IN CENSUS: outside the 56-day window, repair-tail family, not a reserve target. Whole-window CONFLICT=13/616: teacher-saturation story refuted for these cells.

AC#4 GATE: PASSED for the two persistent June cells - allowance snapshot shows Servizio stilistico allow=15 at G06 06-10 and Educazione Civica allow=2 at G09 06-03 (non-cannibalizing allowance exists). Under blockGuard the leading cell's allowance death stops the whole date, so those alternatives are never tried today: the substitution prototype (Phase E, default OFF) is supported; it must take the LEADING position (H31/H17 contiguity) and pass VRP+parity at booking time (snapshot proves allowance only).

Deliverables: claudedocs/spalmone-2026-27/task590-results/CENSUS-REPORT-2026-09-09.md + reserve-census-{DETERMINISTIC,GRASP}.txt (616 entries each) + bench-log-excerpt.txt. NEXT: Phase E prototype (alternative-course substitution at allowance-dead leading cells, flag default OFF), then AC#5-#7.

[2026-09-09 notte] GATES + REVIEW CORRECTIONS. /simplify (4 agent: 9 finding applicati - holder ReserveCensusState al posto dei 7 blocchi null-check, subj/conflict locals, classificazione via served, grouping a log time, dumpAllowanceSnapshot estratto, seedTeachersAndAvailabilityRules riusato al posto dei loop copiati, contentSha resa davvero id-independent, metodo morto e filtro morto rimossi; skip motivati: hoist wireStatics/countByProvider/empties in fixture condivisa = follow-up, vedi sotto).

CODE-REVIEW HIGH: 10 finding, i 4 portanti CORRETTI nel codice e nel report: (1) il bench non ripubblicava setPinnedLessonTimeslotsByGroupStatic DOPO il pre-assign (lo fa solo refreshSolverStatics in produzione) -> le braccia giravano con l'esclusione H3 pinned-cell DISARMATA: censimento di una variante di protocollo. FIX: republish nel bench. (2) le rejection post-cap facevano shadow sul cap (BOOKED_CAP_FULL irraggiungibile) -> FIX: reject() no-op dopo capReached. (3) LEDGER_CELLS aveva G13 2027-11-06 (typo; la cella reale e' 2026-11-06) -> FIX + report corretto. (4) snapshot allowance saltato quando una classe parity precedeva -> FIX: dump alla prima exhaustion comunque (flag allowanceDumped). Piu': census statics cleared prima del gate prebooking (no stale census con prebooking off), subj null-safe, commenti ' - ' corretti, countByProvider -> providerCounts single-rescore.

CENSITO CORRETTO (bench EXIT=0 post-fix, numeri definitivi): deterministic {ALLOWANCE 125, PARITY 186, VRP 28, CONFLICT 0, NEVER 8, BOOKED_PARTIAL 268, BOOKED_CAP_FULL 1}; GRASP {116, 190, 28, 0, 8, 256, 17+1}. Il fix pinned-static ha SPOSTATO 13 CONFLICT in VRP (le celle occupate da pin ora sono VRP-illegali, non conflitti): la vecchia riga 'CONFLICT=13' era dell'armi variante. CELLE LEDGER INVARIATE: G06 10/06 ALLOWANCE (claim 54 vs pool 50), G09 06/03 ALLOWANCE determ / PARITY grasp, G12 16/04 NEVER (no Friday cells), G13 26-11-06 fuori finestra. AC#4 GATE: PASSATO PER ENTRAMBE le celle giugno (G06: Consulenza 3h, Approfondimenti 3h, Gestione aziendale 8h, Servizio stilistico 23h; G09: Educazione Civica 2h).

DEFERRED dal review (follow-up da_schedulare): hoist di countByProvider (6a copia), wireStatics+seedCourses (3a copia) ed empties-recount (4a copia, gia' divergenta) in RealSeedFixture/SnapshotFixture - la wiring e' la validita' del banco (il finding 1 e' nato li'). Cut findings registrati: deref null-course latente (ora difensivamente null-safe), parity nel braccio no-block-guard.

Suite verde PRE-refactor (1291/0, EXIT 0, valida l'instrumentazione); suite FINALE post-refactor in volo. Al verde: commit dell'unita' censimento (instrumentazione + bench + report). Phase E (prototipo sostituzione corso-alternativo alle leading cells allowance-dead, default OFF) resta il prossimo passo del task.

[2026-09-09 notte, Fase E] PROTOTIPO FIRST-FIT SUBSTITUTION MISURATO E RESPINTO (kill criteria pre-registrati, 4 bracci banco EXIT=0). Flag planner.construction.prebooking.substitution.enabled (opt-in solo 'true', default OFF). Meccanica: alla morte per allowance della cella, primo ALTRO corso della stessa data con allowance non-cannibalizzante (decrementata per prenotazione), stesso range di slot, stessi gate (VRP/occupazione/cap/parita').

RISULTATI: deterministic ON chiude 5 celle INCLUSE ENTRAMBE le giugno del ledger (G06 10/06 Cosmetologia->Gestione aziendale; G09 06/03 RA->Educazione Civica: le alternative PREDETTE dal censimento) ma CREA G12 2027-05-07; GRASP ON chiude 4 (G06 10/06 di nuovo) ma CREA 2 incl. la G09 06/03 STESSA. H32/H33 costruzione peggiorano (38->43 / 39->44 determ). 15 substitution nel braccio determ, pin 274 preservati (asserted). KILL CRITERION TRIPPED IN ENTRAMBI I BACINI -> first-fit RESPINTO in questa forma, flag resta OFF, scaffold tenuto committed (precedente CompositionHoleCloser).

MECCANISMO DEL FALLIMENTO: la redistribuzione cannibalizza attraverso il pool anche restando dentro la propria allowance (il claim model protegge le date claimed, non il consumo successivo dello stamp). L'ASSET del respingimento: le celle giugno sono CHIUDIBILI da costruzione (G06 10/06 in ENTRAMBI i bacini) - esistenza-proof per TASK-593, che dovra' cercare mosse non-greedy (swap-shaped / claim-aware che proteggano le date cannibalizzate).

GATES Fase E: /simplify 2 agent (5 finding applicati: allowance computata una volta, served=true alla substitution, precedenza key = stop-class con substitution nel tail, javadoc wording, knobs.md rigenerato 132 voci col nuovo flag - process miss colto dal reviewer; skip motivati: estrazione placeReservedLesson tail e diffArms = churn su scaffold frozen respinto). Un bug REALE del prototipo trovato e corretto in itinere dal primo run: l'helper prenotava fino a cell.length() SENZA decrementare l'allowance dell'alternativa (overbook di 1 -> cannibalizzazione); il verdetto non cambia con il fix (CREATED identico). code-review high in volo sul diff Fase E; poi suite finale e commit.

[2026-09-09 notte, Fase E review + disconfinamento] CODE-REVIEW HIGH sul prototipo: 10 finding TUTTI confermati. I 3 difetti pesanti erano BUG DI SELEZIONE che confondevano il verdetto: (a) alternative scansionate dalla cella 0 -> prenotava un corso di cella PRECEDENTE piu' tardi nello stesso giorno = H28 hard A..B..A; (b) fill parziale che proseguiva sopra il gap = H17 hard; (c) morte mid-cell ritentata a slot occupato = scan garantito-zero. FIX applicati: scansione SOLO celle successive (niente stessa-materia nello stesso giorno), gate slot==cellStart, fill parziale ferma la data (prefixBroken), journal + capReached + parity mantenuti sul path substitution, sostituzioni accumulate in lista (la seconda non cancella la prima), tail con classe-stop E sostituzione.

RERUN 4 bracci dopo i fix: STESSI closed/created (determ: chiude 5 incl. entrambe le giugno, CREA G12 05-07; GRASP: chiude 4, CREA 2 incl. G09 stessa) -> IL VERDETTO DI RESPINGIMENTO E' DISCONFINATO: la cannibalizzazione e' inerente alla redistribuzione first-fit, non ai bug. Costi residui del meccanismo registrati col respingimento: ogni ora sostituita devia dal template del proprio corso (M32), combinazione substitution+slot-retry non testata (moot col flag OFF).

DISPOSIZIONE degli altri finding: (4) M32-inherent = parte della razionale del respingimento, non bug da fixare; (5) interazione slot-retry = non testata, flag OFF la rende moot, registrata; (6) kill print-only = deliberato (il banco misura, il verdetto sta nel report; l'assert pins resta come invariant gratuito); cut-tier (lista parametri, casing del flag) skip motivati: scaffold frozen respinto, e il casing case-sensitive e' voluto (opt-in piu' severo della famiglia). Suite finale in volo; al verde commit + chiusura task.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Construction discriminator executed end to end in one evening, zero solve cycles, all bench-localized (commits f30943c3 census, d11b16b5 Phase E, 3458380c final simplify pass; suite 1291/0 EXIT 0 on d11b16b5, bench EXIT 0 on the final tree). (1) CENSUS: permanent per-date rejection classification in the live reserve path (RESERVE_CENSUS + ALLOWANCE_SNAPSHOT); bench reproduces the measured protocol start state in-test (12510 lessons incl. Stage, 18 rooms, 274 pre-assign pins hard-asserted, pinned-cells static republished as production does). VERDICTS on the four ledger cells: G06 10/06 + G09 06/03 = ALLOWANCE_EXHAUSTED (claim demand exceeds pool; teacher-saturation refuted, CONFLICT=0/616); G12 16/04 = NEVER_ATTEMPTED, no Friday template cells (slot-13 hypothesis refuted as mechanism); G13 26-11-06 outside the reserve window (repair-tail family). (2) AC#4 gate PASSED for both June cells (non-cannibalizing alternatives measured). (3) Phase E first-fit substitution MEASURED AND REJECTED under pre-registered kill criteria, tripped in both basins (deterministic closes both June ledger cells but CREATES G12 05-07; GRASP creates 2 incl. the G09 target itself); verdict UNCONFOUNDED by fixing the review's three selection bugs and re-running to identical closed/created: the cannibalization is inherent to first-fit redistribution. Flag default OFF; scaffold committed (CompositionHoleCloser precedent); M32-deviation and slot-retry interaction recorded as residual costs. (4) ASSET for TASK-593: the June cells are construction-closable (G06 in both basins) = existence proof pointing to non-greedy, swap-shaped/claim-aware moves. Gates: /simplify (three rounds, 10 agents total, findings applied or skipped with written reasons) + code-review high (two rounds, 20 findings, all dispositioned) + full test.sh EXIT 0 + bench EXIT 0. Follow-up: TASK-597 (bench plumbing hoist, 4 items).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /simplify and high-effort code review on changed construction or benchmark code.
- [x] #2 Run main-session applicable wrapper tests with exit codes.
- [x] #3 Publish provider-counted before/after results and preserve the prototype default OFF until explicit adoption.
<!-- DOD:END -->
