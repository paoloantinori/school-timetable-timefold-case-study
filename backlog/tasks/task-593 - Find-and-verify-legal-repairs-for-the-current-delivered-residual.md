---
id: TASK-593
title: Find and verify legal repairs for the current delivered residual
status: Done
assignee:
  - fable-fedora
created_date: '2026-09-09 10:10'
updated_date: '2026-09-10 21:35'
labels:
  - claude-planned
  - zero-hard-recovery
  - witness-test
milestone: m-14
dependencies:
  - TASK-539.01
references:
  - TASK-574
  - TASK-577
  - TASK-589
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Use the frozen ledger and near-delivery fixtures supplied by TASK-539.01 to test actual remaining H29/H33/H34 defects. This is a current-state discriminator, not a duplicate of the older OneOffFinisher implementation or the closed TASK-577 census. Seek complete legal repairs with existing machinery first. If a local move fails, identify donor/follower/teacher/room dependencies and escalate to a bounded compound witness only where needed. referente clarification is parallel, not a prerequisite to reversible testing. Do not revive historical quasi-hard tolerances.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every current residual has a scoped result: provider-verified witness, proven input contradiction, LOCAL_NO_WITNESS, or TIMEOUT.
- [x] #2 Remaining H29 lessons are checked first for room-only and existing room-tail alternatives.
- [x] #3 Each accepted compound witness preserves donor/blocker days, authoritative pins, per-course/group inventory, and all full-provider hard families.
- [x] #4 Search limits and explored alternatives are recorded; limited failure is never called global infeasibility.
- [x] #5 Witnesses replay on the original fixture and are checked for regressions on the second fixture.
- [x] #6 Report selects the smallest supported remedy or explicitly rejects the tested route; no production finisher is added without supporting evidence.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Plan (drafted 2026-09-10 morning, awaiting maintainer approval; surfaces studied, no code yet)

KEY SUBSTRATE DECISION: the DELIVERED timeslot state is byte-represented by the winner's LAST boundary checkpoint - attempt1/POST_FINAL_SURPLUS_HARVEST.json (1MB, sha c24fc8c5 in the committed manifest): TASK-539.01 verified its placements are IDENTICAL to the export on all 12510 lessons (the post-boundary room refinement moved rooms only). Rooms differ from the final delivery, but H29=0 in this delivery and every residual family (H28/H33/H34/H32/H0) is timeslot-tier: the checkpoint is a FAITHFUL local fixture of the delivered state. This makes the whole witness search bench-local (the StagedCheckpoint/LessonPlacement/Restore/Serializer package already exists for parsing).

### A. Fixtures in-repo + load-verify gate
1. scp from laptop: attempt1/POST_FINAL_SURPLUS_HARVEST.json -> task593-results/fixture-delivery-slotretry.json (fixture A = original); v132's winner final boundary from staged-checkpoints-v132 -> fixture-delivery-v132.json (fixture B = second, per the drift warning: NEVER the live v132 volume).
2. Witness bench (env-gated @QuarkusTest, census-bench pattern): seed -> wire statics -> apply fixture A placements (lessonId->timeslotId; pinned flags from the placement records) -> provider recount. ENTRY GATE: must reproduce [-13/0] with EXACTLY H28=4, H33=3, H34=3, H0=1, H32=1 (a mismatch = harness failure, stop: doc-001 rule). Same gate on fixture B ([-6/0]: H34 3/H33 2/H29 1).
3. Every witness verification = apply move(s) -> provider recount of ALL hard families + pins + per-course/group placed inventory; accept only if the target family improves and NO family regresses (AC#3).

### B. Per-unit witness searches (cheapest first; bounds and alternatives recorded per AC#4)
1. H0 lesson 8244 (G14 Confezione T16): v132 PLACED this same lesson (existence witness) - extract its slot from fixture B as the transplant candidate; PLUS enumerate fixture-A legal slots for (course VRP, teacher H1/H5, group H3/H11, quota). Expected: cheapest closure.
2. H28 T03/T22 A..B..A x2 days (09-24, 10-01): day-local reorder. Enumerate the day's teacher-block rearrangements (6 lessons: B,B,T,T,T,B -> <=60 arrangements), filter conflict-free + VRP-legal + contiguity (H17-safe block swap), recount. Day-local = no donor day touched.
3. G12 2027-04-16 (census: NEVER_ATTEMPTED, slot 13 free): single-placement witness - enumerate legal course lessons placeable at the free slots of that date.
4. H34 June cells (G06 10/06, G09 06-03): compound cross-date witness, bounded BFS (donor hour import from a same-group surplus date + donor repair), depth<=2, node cap + wall cap; the census says the pool accounting blocks greedy, so the witness must be swap-shaped (import + give-back). LOCAL_NO_WITNESS or TIMEOUT at the bound if nothing found - never GLOBAL_UNSAT.
5. H33 G14 Fridays x3 (1h each): bounded donor search within G14 programme; expected classification input-question (TASK-594 pending) unless a legal donor exists - the search decides.
6. G13 2026-11-06 H32 1h: single donor-hour move, bounded.
7. H29: ZERO in this delivery (ledger) - AC#2 vacuously satisfied, documented in the report.

### C. Second-fixture regression (AC#5)
Each ACCEPTED witness replays on fixture B where the corresponding cells/lessons exist (e.g. the H0 transplant IS fixture-B-derived: its replay there is trivially its origin; the H28 pattern: G03 exists in v132's delivery too - check the same reorder harms nothing). Cells absent on B: recorded as not-applicable, not silently skipped.

### D. Report -> TASK-595
Per-unit verdict table (WITNESS with the move + provider evidence / INPUT-QUESTION / LOCAL_NO_WITNESS at bound N / TIMEOUT), bounds + alternatives explored, second-fixture regressions. The report SELECTS the smallest supported remedy per unit or rejects the route explicitly (AC#6); NO production finisher is added by this task.

### E. Gates
/simplify + code-review on the harness diff; battery = witness bench (self-verifying via the entry gates) + test.sh full suite (test-side code); both fixtures committed with sha256 pinned in the report; zero laptop cycles beyond the one-time scp.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
[2026-09-09 sera, post-commit verification] AVVERTIMENTO sul secondo fixture: il VOLUME DB v132 (planner-data-v132-7sep) e' andato in DERIVA rispetto alla propria export: placements intatti (recount a HEAD corrente d' ancora [-6/0], H34 3/H33 2/H29 1) ma pinned=0 nel DB contro i 274 pinned dell'export presa alla capture. I tuoi witness devono replayare dall'EXPORT + dai checkpoint (staged-checkpoints-v132), NON dal volume vivo. Il volume slotretry-9sep invece e' coerente (276 pinned verificati su checkpoint/export/DB). Dettaglio: CONTROL-MANIFEST sezione fixture B caveat + REPRODUCTION-LOG 'Post-commit adversarial verification'.

[2026-09-10, ESECUZIONE COMPLETA] Banco WitnessBenchTest (PLANNER_TEST_WITNESS_BENCH=1, EXIT=0) su DUE fixture CONTENT-ADDRESSED derivate dagli export congelati (id-indipendenti: TASK-588): fixture A = consegna slotretry (12509 placed, la G14 Confezione/T16 unplaced), fixture B = consegna v132 (12510, la stessa lezione PLACED). ENTRY GATES: entrambe riproducono il vettore consegnato ESATTAMENTE. Oracolo: provider recount per mossa su UNDICI famiglie hard timeslot-tier (H28/H33/H34/H0/H32/H29/H17/H1/H3/H11/H31), accettazione = famiglia target migliora E nessuna famiglia regredisce vs il vettore RUNNING.

VERDETTI ONESTI (11 famiglie): H28 x2 WITNESS (rotazione giorno-locale 4 lezioni: B@13->10, T shift; giorno resta pieno = H17-safe per costruzione; H28 4->2->0); H33 G14 2027-01-29 WITNESS (donor-import 1h Competenze Digitali da 02-05; H33 3->2). TUTTI GLI ALTRI LOCAL_NO_WITNESS a depth-1 ai bound registrati: H0 (8 slot-room pairs su 01-28: OGNI placement chiude H0 ma viola H11 - il giovedi' e' al cap giornaliero), H33 02-05/03-05 (6+6), H34 G06 10/06 + G09 06/03 (6+6: il donatore apre il proprio deficit), G12 16/04 (8 negli slot liberi), G13 H32 (6). Vettore finale: -13 -> -8 con TRE mosse legali giorno-locali.

NOTA DI ONESTA' (la lezione del task): con la guardia a 6 famiglie il banco dichiarava H0 + 3 G14 'witness' per -13->-4. La review /simplify (altitude) ha colto il gap della famiglia-set; la guardia a 11 famiglie ha smascherato OGNI placement H0 come violazione H11 e 2 dei 3 G14. I numeri ristretti sono registrati nel report COME racconto cautelativo, esattamente la famiglia di falsi positivi che questo task esiste per prevenire.

FIXTURE + ARTIFATTI: task593-results/ fixture-content-{slotretry,v132}.txt (sha 8172fc60/e1746ba7) + fixture-delivery-{slotretry,v132}.json (i checkpoint winner raw, c24fc8c5/811f3027, provenance) + bench-log-excerpt.txt + WITNESS-REPORT-2026-09-10.md. HARNESS ITERATIONS (5 run, ognuna corretta leggendo i vettori-verdetto): mutazioni invisibili all'oracolo via em.clear (lo stato va threadato come lista, il recount vede gli OGGETTI); baseline fissa che accettava miglioramenti-fantasma (running vector); doppio conteggio recounts che affamava il budget; budget della replay B. SKIP motivati dai review: parseExport al posto del content-fixture (i json commitati sono checkpoint, non export: parseExport non si applica); hoist director per recount (frozen evidence, 41-51 recount totali).

CODE-REVIEW high in volo; suite completa in volo. Al verde: commit + chiusura.

[2026-09-10 notte, TASK INVALIDATO dalla code-review high] IL BANCO PRODUCE EVIDENZA INVALIDA: l'oracolo ripete solo 6 delle ~30 famiglie hard, e il seed del banco salta la classificazione SubjectRegistry (nessun corso e' lab: H-M2/H23/H24 strutturalmente inerti) e non cabla stage-pattern/blackout/co-presenza (H30/H4/H-COPRESENCE inerti). Cross-check fixture-level: 7 degli 8 'WITNESS' registrati CREANO nuove violazioni hard (H0: T16 gia' insegna G01 alle 08:00 + R06 occupata da G09 + G14 gia' alle 08:00 = H1+H2+H3; H28 09-24: T03 a slot 10 gia' occupato dal suo G11 Orientamento = H1; donor-import: stanze trasportate in conflitto + giorni donatore che iniziano alle 09:00 = H31). 'Sei mosse legali' e' VOID: le mosse facili del vicinato sono TUTTE illegali (trovato prezioso per TASK-595 ma in senso opposto).

REWORK (prossima iterazione del banco, nel task): (1) FAMILIES = tutti i prefissi hard (~15, incl. H1/H2/H3/H17/H31/H30/H4/H7/H20/H-M2/H-COPRESENCE); (2) il seed applica la classificazione subjects.csv/subject_overrides (il percorso DataGenerator.generateCourses o equivalente SnapshotFixture); (3) statics stage/blackout/co-presenza cablate come refreshSolverStatics; (4) dominio candidati: celle occupate escluse, R18 e stanze non-assignable escluse; (5) running baseline in OGNI unita' (unit 3 aveva il baseline hardcoded dell'ingresso); (6) asserzione del vettore finale a fine banco; (7) le eccezioni di recount falliscono LUDICAMENTE (niente DIAG+map vuoto); (8) pass sanity per-gruppo obbligatorio su ogni stato witness accettato (CLAUDE.md). Il verdetto VALIDO che sopravvive: il vicinato depth-1 della consegna e' piu' stretto del censimento (ogni mossa facile e' illegale) - che orienta TASK-595 verso mosse composte/swap-shaped.

STATO: task torna efettivamente aperto (In Progress) nonostante la report invalidato committato; il commit porta il banco + le fixture + il report marcati INVALIDATO in testa. Nessuna evidenza avvelenata va a TASK-595.

[2026-09-10 notte] NOTA VALIDAZIONE: la suite completa lanciata sull'albero del banco e' stata UCCISA (killed, probabile OOM dei fork di test - memoria memory oom-fedora-test-forks): ca016519 NON ha una suite verde completa sull'albero esatto. La rielaborazione del banco (oracolo completo ~15 prefissi, SubjectRegistry seed, statics production) dovra' concludersi con suite EXIT 0 prima di qualunque nuova evidenza; controllare swapon/memoria libera prima di rilanciare i fork.

[2026-09-10 pomeriggio, REWORK COMPLETATO - banco oracolo v3 VERDE] Fix applicati da /simplify finale (4 agent: isAssignable accessor, H0Result eliminato - le unita' tornano Map, FDFreePlacement FOLDATA in donorImport ~50 righe, providerCounts Set<String> exact-contains, commento FAMILIES con le 3 esclusioni strutturali) e da code-review high finale (9 finding: (1) problem() passava List.of() come stagePatterns -> H4/H30 MORTI nei recount: FIX passato StagePattern.listAll(); (4) SnapshotFixture.clearTables ora pulisce anche SchoolHoliday/StagePattern/Room (leak nella H2 condivisa + double-seed al re-run); (5) budget DEDICATO di 12 recount per la replay fixture-B (prima strutturalmente affamata tried=0); (6) H0 ora prova TUTTE le stanze assignable per slot (il cap 2-room non raggiungeva mai l'origine v132 R03); (8) RealSeedFixture imposta allowProfessionalizzantiDuringStage=false per G08/G09 (divergenza da DataGenerator); (9) VRP pre-filtro su isSlotLegalFresh (TASK-578) invece della cache mai invalidata).

RUN FINALE (fin2, EXIT=0, 233s, 67 recount = 55 fase-A + 12 replay): ENTRY GATES entrambi esatti. VERDETTI: H28 10-01 WITNESS (rotazione 4 lezioni: H28 4->2, TUTTE le altre 22 famiglie invariate); H28 09-24 LOCAL_NO_WITNESS (T03 a slot 10 insegna gia' G11: H1=1000); H0 LOCAL_NO_WITNESS a 17 slot-room pairs su 3 venerdi' (ogni candidato chiude H0+H33 ma T16 insegna G05 negli stessi slot: H1=1000; l'escalation depth-2 e' 'sposta prima la lezione G05 di T16'); TUTTI gli altri LOCAL_NO_WITNESS a depth-1 ai bound. Replay B eseguita davvero: entrambi i venerdi' v132 LOCAL_NO_WITNESS 6+6. Report rigenerato dal run REALE (il finding #2 della review - log/report hand-editati senza re-run - chiuso: bench-log-excerpt.txt ora e' del run fin2).

SUITE: la prima finale (suite4) aveva 3 errori NoClassDefFound in HumanFaithfulConstructionSpikeTest = target/ stantio della suite uccisa; dopo clean rebuild il test isolato passa 3/3. Suite finale (suite5) in volo sull'albero completo. Al verde: commit + chiusura task.

[2026-09-10 sera, DEPTH-2 esplorato su ordine di maintainer] Fase DEPTH-2 aggiunta al banco (budget 60): H0 compound (blocker-first: 13 blocker al primo slot, 12 composti su 3 venerdi') e June swap (8 composti): TUTTI respinti dall'oracolo 23-famiglie. Il vicinato depth-2 della consegna e' esaurito alle soglie registrate; prossima escalation = depth-3+ o risposte referente. FIX in itinere: Long boxed confrontato per riferimento (blocker sempre 0 sopra id 127), stanza mancante al target. Commit e85cc7bf, pushato. Run EXIT=0, 87 recount. Report e excerpt aggiornati dal run reale.

[2026-09-10 notte, DEPTH-3 su mandato notturno maintainer] Catena occupant-free per H0 (10 catene a 3 mosse su 3 venerdi') e giugno depth-2 ALLARGATO (il cap interno del loop patch era rimasto a 8: corretto a 20, aggiunta G09 06-03): TUTTI LOCAL_NO_WITNESS sotto l'oracolo 23-famiglie (129 recount). Il vicinato legale della consegna e' esausto fino a depth-3 (H0) e depth-2-allargato (giugno). Vie residue: referente (RA/G14 semantics) e depth-4+. Commit 3d0c15bb, pushato. CONTESTO NOTTURNO: ciclo di controllo control-10sep lanciato sul laptop (mandato consegnato alle 22:40 circa, zai in esecuzione: git pull a df331488, rebuild, preflight, staged cycle completo con checkpoint capture, capture canonica, dottore 579.01 dogfood, REPORT.md). Monitoraggio via tick scheduled; il ciclo misera il quadro ratificato corrente (pool-cap-fill ON, golden-monthly rimosso) che nessuna consegna ha ancora misurato.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Witness search executed on the frozen delivery fixtures with a FULL-oracle bench (commit ee94a997, after the first harness was invalidated by its own review). SUBSTRATE: two content-addressed delivery fixtures (id-independent per TASK-588) derived from the frozen exports; entry gates reproduce both delivered hard vectors EXACTLY (slotretry [-13/0] and v132 [-6/0], every clean family at 0). ORACLE v3 (three generations, two invalidated by their own evidence - the task's own cautionary tale): provider recount over all 23 reachable hard families in HARD POINTS (ScoreCompat.hardOf; match counts read compliant groupBy days as violations: H7=3316 on the clean delivery), EXACT ID-token matching (prefix 'H3' absorbed H30-H34), VRP pre-filter via isSlotLegalFresh, SubjectRegistry-classified seed with stage patterns/holidays/co-presenza/statics wired as production, fail-loud exceptions. VERDICTS: ONE witness survives the full oracle - the H28 day rotation on 2026-10-01 (H28 4->2, all 22 other families unchanged); everything else LOCAL_NO_WITNESS at depth 1 with named blockers: H0 (every placement closes H0+H33 together but T16 teaches G05 in those slots, H1=1000; escalation = move the G05 lesson first), H28 09-24 (T03's external G11 commitment at slot 10), June H34 cells (donor opens its own deficit), G12 (all imports trip conflicts), G13 H32 (budget). Cross-fixture replay ran with a dedicated budget: both v132 Fridays also LOCAL_NO_WITNESS. STRUCTURAL FINDING for TASK-595: the delivery's depth-1 legal neighborhood is NEARLY EMPTY (1 of ~70 probed moves) - remedies must be compound depth-2 swap-shaped finishers with the identified blockers; no single-move finisher is supported. Review fixes also hardened shared infrastructure: SnapshotFixture.clearTables now wipes SchoolHoliday/StagePattern/Room; RealSeedFixture sets allowProfessionalizzantiDuringStage per production. Gates: /simplify (4 agents) + code-review high (9 findings, all fixed) + bench EXIT=0 + full suite 1292/0/0 EXIT=0.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /simplify and high-effort code review on any changed witness harness or solver code.
- [x] #2 Run applicable main-session wrapper tests with explicit exit codes.
- [x] #3 Recount results with the provider and independently reconcile inventory and per-cell changes.
<!-- DOD:END -->
