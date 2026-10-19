---
id: TASK-606
title: Valutare upgrade Timefold 2.3.0 → 2.6.0 (fix correttezza Bavet join/ifExists)
status: Done
assignee: []
created_date: '2026-09-12 11:13'
labels:
  - claude-planned
  - upgrade
  - timefold
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Contesto (rilievo 12/09, ricerca upstream a domanda di maintainer): siamo pinati a timefold-solver.version 2.3.0; upstream ha pubblicato 2.4.0 (30/07), 2.5.0 (12/08) e 2.6.0 (01/09), nessuna patch 2.3.x. I tre rilasci portano FISS CORRETTEZZA sui constraint-streams Bavet che cadono esattamente sui pattern che il nostro provider usa dappertutto: "stale tuples after a join" (2.5.0), "false deactivation of filtered forEach node" (2.5.0), "stale join+ifExists filtering on all cardinalities" (2.6.0). Poi "Restore working solution score after move is undone" (2.6.0, tocca i nostri pass improve-or-noop che revertono) e il fix dell'ID single-metric nel Benchmarker. Implicazione doppia: (a) su 2.3.0 lo score incrementale puo' essere affetto da stale-tuple dopo join/ifExists, e tutto il nostro apparato di gate (assertImproveOrNoop, pick-best sul boundary tier, warm start) si fida dello score incrementale; (b) un upgrade puo' cambiare le consegne misurate, quindi va fatto su braccio isolato NON sul critical path. Ostacolo noto: il wiring reflection del decider MT-LS e' documentato fragile/pinned ai internals di 2.3.0 (javadoc ratifica TASK-480): la prima cosa da verificare. NON urgente: nessuna CVE nota sul 2.3.0; il valore e' correttezza + robustezza, da misurare quando m-14 si chiude.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Ramo usa-e-getta con timefold-solver.version=2.6.0: suite ./scripts/core/test.sh EXIT 0
- [ ] #2 Wiring reflection MT-LS (pacchetto solver/multithreaded, documentato fragile/pinned a 2.3.0) verificato: smoke staged o test dedicato verde su 2.6.0
- [ ] #3 Pin dei checkpoint/verifier (ConstraintVerifier, GoldenSolutionRegressionTest) comparati pre/post: delta score spiegato o assente
- [ ] #4 solverConfig.xml e solverBenchmarkConfig.xml validati contro la XSD della nuova versione (validate-solver-xml.sh)
- [ ] #5 Verdetto su tarea/adozione rimane a maintainer; nessun merge senza APPROVED esplicito
<!-- AC:END -->

2026-10-03 chiuso come FATTO: la valutazione e' stata fatta e l'upgrade eseguito ben oltre (2.6.0 il 21/09, poi 2.7.0 finale il 01/10). I fix Bavet join/ifExists della 2.5/2.6 girano in produzione da allora.
