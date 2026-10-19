---
id: TASK-579.01
title: Require complete hard-vector evidence before accepting a capture
status: Done
assignee:
  - fable-fedora
created_date: '2026-09-09 10:00'
updated_date: '2026-09-09 21:17'
labels:
  - claude-planned
  - validation
  - evidence-integrity
milestone: m-14
dependencies: []
references:
  - scripts/benchmark/capture-doctor.sh
  - src/main/java/com/school/planner/solver/ScoreCompat.java
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
parent_task_id: TASK-579
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The 2026-09-09 zero-hard history review verified that capture-doctor.sh extracts only the first Bendable hard coordinate and exits zero on WARN. Therefore its successful exit cannot certify a clean or reproducible delivery. Track a focused follow-up to distinguish integrity checks from feasibility and require explicit evidence for both hard coordinates. No change is implemented by this task.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A mismatch in either hard coordinate is rejected by the delivery validation gate.
- [x] #2 A matching nonzero hard vector is reported as internally consistent but infeasible.
- [x] #3 Missing replay artifacts or required score evidence cannot be presented as a certified delivery.
- [x] #4 Tests cover legacy scores and Bendable scores including zero first coordinate with nonzero second coordinate.
- [x] #5 The runbook distinguishes capture integrity from zero-hard and per-group sanity acceptance.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Plan (drafted 2026-09-09 notte, awaiting maintainer approval; surfaces studied, no code yet)

Scope: the delivery-validation gate = capture-doctor.sh (both hard coordinates, verdict semantics) + the two tooling fixes filed today (F1 import size guard, F3 dead api.sh call) + the runbook distinction (AC#5). All bash/python, no Java changes (ScoreCompat already emits hard+quasi; the doctor must simply read both).

### B. capture-doctor.sh upgrade (AC#1/#2/#3)
1. BOTH coordinates: parse the full hard vector from score.txt (Bendable "[-13/0]hard/..." -> "-13/0"; legacy plain "0hard/..." or "-5hard" -> single coordinate "N") and compare against analysis.json's "hard" AND "quasi" fields (both exist in the endpoint payload).
2. Verdict semantics:
   - hard VECTOR mismatch (either coordinate) -> DOCTOR-FAIL (today: WARN; exit 0). No capture whose two hard sides disagree may be quoted, whatever the divergence family.
   - hard vector matching and NONZERO -> verdict line says internally consistent but INFEASIBLE (e.g. DOCTOR-OK-INFEASIBLE hard=-13/0), never a bare OK that reads as an endorsement (AC#2).
   - hard vector matching and zero -> DOCTOR-OK hard=0/0 (the only capture a zero-hard claim may cite).
   - missing export OR missing score evidence (score.txt/analysis.json) OR missing sanity -> the verdict line carries NOT-DELIVERY-CERTIFIABLE with the reason (AC#3); WARN stays exit 0 (integrity doctor, not a blocker for intermediate captures) but can no longer be phrased as clean.
3. Keep the existing checks (export size/gunzip/lint, sanity-reds count, phases) unchanged.

### C. Script fixes (filed evidence)
- F3: constraint-violations.sh "api.sh raw" -> "api.sh get" (one line, verified broken live today).
- F1: import-schedule.sh explicit size guard: file > ~45MB -> immediate clear error naming the two DEMONSTRATED restore paths (DB volume reload; checkpoint path apply) instead of curl OOM/HTTP 413 (the server cap makes streaming pointless).

### D. Tests (AC#4) - test_capture_doctor.py, check-regression self-test convention
Synthetic capture dirs in tempdir; assert verdict line + exit code for: clean zero-hard OK; matching nonzero -> OK-INFEASIBLE wording; coord-1 mismatch -> FAIL; coord-2 mismatch (the [0/N] quasi case) -> FAIL; legacy plain score parse; missing export/score/sanity -> NOT-DELIVERY-CERTIFIABLE wording; gunzip-corrupt export -> FAIL. Run directly, exit 0/nonzero.

### E. Runbook (AC#5)
claudedocs/scripts.md capture-doctor section rewritten: three SEPARATE acceptances a delivery must show (capture integrity = doctor's domain; zero-hard = BOTH coordinates zero from the provider; per-group sanity = the canonical sanity gate with no reds), plus the narrative-vs-provider discipline (F4: verdict prose is a claim, analysis.json is the count) and the restore-path rule (a certified delivery names its demonstrated restore path: DB volume or checkpoint apply, since the JSON import is structurally capped).

### F. Gates
/simplify + code-review on the diff (scripts + new test); run test_capture_doctor.py + test_check_regression.py with exit codes; full test.sh is NOT needed (no src/ change) - the wrapper battery for scripts is their self-tests; document that in the DoD notes. Verify the upgraded doctor against the REAL committed captures (slotretry-9sep must come out OK-INFEASIBLE hard=-13/0; v132 OK-INFEASIBLE -6/0) as the live smoke.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
m-14: this gate may be implemented in parallel with TASK-539.01. Baseline investigation must manually verify both hard coordinates until the gate is available; adoption requires this task. Project DoD defaults were empty when checked, so required review/test checks are attached explicitly rather than silently assumed.

[2026-09-09, from TASK-539.01 execution] Three new evidence items for this gate, all verified mechanically today (details in claudedocs/spalmone-2026-27/m14-results/REPRODUCTION-LOG.md):

F1 - The canonical export CANNOT round-trip via POST: quarkus.http.limits.max-body-size=50M (application.properties:43) rejects the 1.2GB full export with HTTP 413, and import-schedule.sh's curl -d @file dies with 'out of memory' before that. The working restore paths are the DB volume reload (verified: reload at current HEAD reproduces the delivery analysis byte-identically) and the path-based checkpoint apply (solve-staged-resume?path=). The gate should treat 'certified delivery' as requiring a DEMONSTRATED restore path, and the import script needs the streaming fix or an explicit size guard with a clear error.

F3 - scripts/api/constraint-violations.sh calls the REMOVED 'api.sh raw' and returns 'API error' for every constraint id (verified live on the laptop). Workaround used: 'api.sh get "/api/schedule/analysis/lessons?constraint=<ID>"'. One-line fix, belongs in this task's capture-tooling pass.

F4 - VERDICT narrative vs provider divergences on slotretry-9sep (all three verified against analysis.json + independent recount): 'H32=3 celle (-3)' is wrong (provider: 1 cell, -1; only -1 reconciles the -13 total); third H34 cell is 2027-04-16 not '04/06'; 'niente GRASP' needs the attempt-1 qualifier (GRASP attempts 2-5 ran and lost). Supports this task's AC#5 (runbook must distinguish narrative claims from provider recounts).

[2026-09-09 notte, ESECUZIONE COMPLETA] capture-doctor.sh ricostruito: VETTORE hard completo via shared.parse_hard_vector (NUOVA funzione in scripts/analyze/shared.py: riusa SCORE_BENDABLE_RE, legacy->implicit-/0, last_match_wins per score.txt con riga init sopra la finale; piu' severa del vecchio sed privato: riga troncata senza coda soft = nessuna evidenza, stessa politica di check-regression); analysis.json letto con quasi DEFAULT 0 (le 310/333 capture pre-Bendable su disco confrontano invece di venire bloccate); divergenza su QUALSIASI coordinata = DOCTOR-FAIL exit 1; token DISGIINTI DOCTOR-OK (solo [0/0]) / DOCTOR-CONSISTENT-NONZERO / DOCTOR-WARN NOT-DELIVERY-CERTIFIABLE:<motivi> (no export/score/sanity) / DOCTOR-FAIL; ramo plain export.json ora valida il JSON e applica il warn 99MiB (garbage non certifica piu'); sanity-reds conta i GRUPPI rossi dall'header del report (fallback righe ROSSO).

CODE-REVIEW HIGH: 10 finding TUTTI confermati e corretti. Oltre ai punti sopra: (a) la review ha TROVATO altri 4 siti morti della stessa classe F3 (resume-bench.sh solve-stop x2, resume-bench-room.sh, benchmark-pillar-moves.sh stop-solve -> api.sh stop) e 8 menzioni doc che insegnavano 'api.sh raw' (scripts/README, README-lesson-placement, solver-debugging.md x6): tutti corretti, grep residuo = 0 (regola 'grep ALL dispatch sites'). (b) import-schedule.sh: soglia portata al cap REALE 50MiB=52428800 (era 45MiB con messaggio '50M': un file 46MiB veniva rifiutato pur importabile; M=1024^2 verificato dal bytecode Quarkus) e stat-failure ora RIFIUTA (era '|| echo 0' che disarmava la guardia). (c) run-measure.sh: il doctor FAIL ora FA FALLIRE la catena (era '|| true': i denti esistevano solo se un umano leggeva il log; finalizza comunque gli artifact, poi exit col codice del doctor + riga CAPTURE-DOCTOR-FAILED). (d) laptop-RUNBOOK sez.2 riscritta con la semantica nuova (era ferma al vecchio modello warn-only monocoordinata: il runbook usato al punto di capture). (e) scripts.md: falso 'chiamato anche da capture-run.sh' corretto (l'unico chiamante automatizzato e' run-measure.sh).

BATTERY: test_capture_doctor.py 14/14 PASS EXIT 0 (zero-hard Bendable, CONSISTENT-NONZERO, mismatch coord1, mismatch coord2 con prima-zero [il caso cieco del vecchio doctor], legacy plain, legacy zero, missing export/analysis/sanity, 1-byte export, pre-Bendable senza quasi, riga init sopra finale, plain garbage, sanity-reds=gruppi); test_check_regression.py PASS EXIT 0; shared.parse_hard_vector smoke (multiline/legacy/no-match); bash -n su tutti gli script toccati; smoke live: capture committed = NOT-DELIVERY-CERTIFIABLE no-export (corretto: l'export vive solo sul laptop), capture completa sul laptop = WARN hard=[-13/0] col warn 105MiB.

Skip motivati: block() dual-write (token greppabile + prosa: sommario-dettaglio voluto); --all di constraint-violations.sh itera 3 ID morti (H15/H16/H-D1, innocuo, annotato); il testo del piano nel task conserva i suoi ' - ' (record storico gia' committato, non documento vivo). Cut-tier hyphen dei file vivi corretti (doctor x2, docstring test).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Delivery-validation gate delivered (commit bf97c6ac). capture-doctor.sh now compares the COMPLETE hard vector score.txt-vs-analysis.json through a new shared.parse_hard_vector (single-parser doctrine; legacy normalized to implicit zero quasi; last-match-wins over multi-line score files): divergence on EITHER coordinate is DOCTOR-FAIL exit 1 (the old doctor read only the first coordinate and WARN'd on divergence); verdict tokens are disjoint - DOCTOR-OK only for hard=[0/0], DOCTOR-CONSISTENT-NONZERO for matching nonzero (internally consistent, not zero-hard), DOCTOR-WARN NOT-DELIVERY-CERTIFIABLE:<reasons> when export/score-evidence/sanity is missing. run-measure.sh now fails the chain on a doctor FAIL (the gate has mechanical teeth, not just a log line). The review's sweep also fixed: 4 more dead api.sh call sites of the F3 class + 8 doc mentions teaching the removed 'raw' command; import-schedule.sh guard set to the real 50MiB server cap with fail-loud stat; pre-Bendable captures (310/333 on disk) compare instead of being discarded; plain export.json garbage no longer certifies; sanity-reds counts red GROUPS not detail lines. Runbook (scripts.md + laptop-RUNBOOK sec.2): the three SEPARATE acceptances (capture integrity, zero-hard both-coordinates-from-provider, per-group sanity), the narrative-vs-provider discipline (F4), and the demonstrated-restore-path rule. Battery: test_capture_doctor.py 14/14 EXIT 0 (incl. the zero-first-coordinate case the old doctor was blind to), test_check_regression.py EXIT 0, live smoke on real captures. Gates: /simplify (4 agents, applied or skipped with reasons) + code-review high (10 verified findings, all addressed). Adoption gate TASK-596's dependency on this task is satisfied.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /simplify on the capture-validation diff.
- [x] #2 Run high-effort code review and resolve or explicitly track confirmed findings.
- [x] #3 Run the applicable main-session wrapper or script test battery with exit code and inspect output.
<!-- DOD:END -->
