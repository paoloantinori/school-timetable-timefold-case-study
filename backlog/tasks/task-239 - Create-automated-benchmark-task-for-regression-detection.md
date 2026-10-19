---
id: TASK-239
title: Create automated benchmark task for regression detection
status: Done
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-08-04 04:54'
labels:
  - timefold
  - benchmarking
  - ci-cd
  - claude-planned
  - read-only-spike
dependencies: []
references:
  - scripts/benchmark/benchmark-branch.sh
  - scripts/benchmark/benchmark-all.sh
documentation:
  - >-
    https://timefold.ai/docs/timefold-solver/latest/configuration#benchmarkConfiguration
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
REFRAMED (maintainer, 2026-08-04): read-only SPIKE — design doc + prototype regression-checker. NOT CI adoption (CI integration stays maintainer's call).

Original AC #1 (CI/CD nightly) and #4 (publish results) are OUT OF SCOPE per maintainer's reframe. Scope = design + skeleton prototype + VERDICT-239, full DoD, commit+push, no adoption.

Deliverables:
1. DESIGN doc — what regression-detection means here: which KPIs to safeguard, what the baseline is, how to compare run-to-run given multi-start variance (p(0-hard)=0.75/single construction), where results save. Built on existing capture-run.sh / KPI scripts (no reinvention).
2. PROTOTYPE — scripts/benchmark/regression-check.{sh,py} skeleton: compares a candidate capture vs a saved baseline on hard-0, full-day, ricorrenza, medium score. Config-gated, exit-code semantics, read-only (never launches solves).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CI/CD pipeline configured to run nightly benchmarks
- [ ] #2 Benchmark results stored with timestamp for historical tracking
- [ ] #3 Performance regression alerts configured (e.g., score degradation > 5%)
- [ ] #4 Benchmark results published or accessible for review
- [ ] #5 Documentation on benchmark schedule and regression thresholds
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SPIKE READ-ONLY COMPLETATO (reframe maintainer 2026-08-04). DoD tutta verificata.

Deliverable:
- DESIGN: claudedocs/spalmone-2026-27/task239-regression-benchmark-design.md (cosa significa regression detection qui, KPI di guardia, baseline come capture dir + spec di regole, gestione varianza multi-start, exit codes, dove salva).
- PROTOTIPO: scripts/benchmark/check-regression.py (rewrite del placeholder; read-only, confronta 2 capture-run.sh dir su 6 KPI di guardia; single vs aggregate; exit 0/1/2/3).
- SELF-TEST: scripts/benchmark/test_check_regression.py (12 scenari).
- VERDICT: claudedocs/spalmone-2026-27/task239-results/VERDICT-239-2026-08-04.md.

DoD: /simplify (4 agent; applicato altitude#1 footgun silent-drop + simplification #1-9; saltati onesti) + /code-review high (skeptic Opus: C1/C2/C3/C5/C6 CONFIRMED, C4 REFUTED = bug reale falso-PASS trovato e corretto con root-cause + test regressione) + test.sh EXIT=0 (988/0/0).

Risposta alla domanda di design: LS NON_REPRODUCIBLE -> single run hard<0 = INCONCLUSIVE (varianza, p(0-hard)=0.75), aggregate best-of-N hard<0 = REGRESSION; KPI continui a banda warn/fail, single-run = indicativo.

NESSUNA ADOZIONE: non cablato in CI (AC originali #1/#4 out per reframe). Adozione = decisione maintainer. TASK-candidate differiti (non creati, nel VERDICT): baseline aggregate ratificata, breakdown hard per-famiglia via analysis.json, trend store storico, promuovere parse_score in shared.py (dup 4x), gate CI notturno.
<!-- SECTION:FINAL_SUMMARY:END -->
