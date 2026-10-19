---
id: TASK-280
title: Switch production solver from Tabu Search to Variable Neighborhood Descent
status: Done
assignee: []
created_date: '2026-03-21 09:05'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - solver
milestone: solver-optimization
dependencies:
  - TASK-408
priority: high
ordinal: 200
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The full 23-config benchmark (2026-03-20) showed VND as the clear winner across all dimensions:

**VND vs Tabu-Entity-Size7 (previous best):**
- Hard: -11 vs -16 (5 fewer violations)
- Medium: -35,154 vs -117,057 (82k less penalty)
- Soft: 361,602 vs 332,384 (+29k better)
- Combined med+soft: 326,448 vs 215,327 (+111k)
- Calc speed: 43k/s vs 33k/s (+30% faster)

**Action:** Replace Tabu Search local search in `solverConfig.xml` with VND's `<variableNeighborhoodDescent>` configuration. Update all dependent artifacts per solver-config-sync rule (SolverConfigParser, solverPhases.ts, documentation).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 solverConfig.xml uses VND instead of Tabu Search
- [ ] #2 SolverConfigParser updated with VND phase names
- [ ] #3 solverPhases.ts updated to match XML config
- [ ] #4 solver-phases.md documentation updated
- [ ] #5 Solver validates successfully (validate-solver-xml.sh)
- [ ] #6 Rebuild succeeds and solver produces equal or better scores than Tabu
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CODA 2026-07-25: testa del cluster benchmark metaeuristiche (284/326/332/223/224/291/292/281 accodati dietro con dep su questo). Bersaglio PRECISO dai dump per-fase val408: l'erosione ricorrenza avviene TUTTA in fase 2 (74,3->52,8) e la fase 3 e' inerte per la terza volta - il benchmark va puntato su mosse/accettore di fase 2 template-rispettosi. ALTERNATIVA/COMPLEMENTO di TASK-419 (solve a stadi) sullo stesso gap: decidere quale dei due filoni parte dopo il verdetto 408, non eseguirli alla cieca entrambi. Pre-screen obbligatorio di ogni variante con Phase2SmokeSnapshotTest (90s) prima di qualunque giro laptop.

2026-07-25 E4: metrica erosione-per-medium operativa (Task415BenchmarkScreeningDemoTest, 90s/arm): tabu7 vs tabu11 indistinguibili a questo budget/seed (15,2 vs 15,1 medium-k/pt). Il cross-finding sposta la priorita' su TASK-326 (terminazione fase 2) come primo arm del cluster.

2026-07-25 sera: NOTA METODOLOGICA per tutto il cluster - gli harness di screening giravano a seed fisso 0 (default PHASE_ASSERT di un SolverConfig senza environmentMode): ogni confronto di varianti fatto finora in locale era sulla STESSA traiettoria. Fix in arrivo (env var per environmentMode/seed negli harness); rifare gli screening dopo. Il ranking tabu7/tabu11 di E4 e' da considerare nullo.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RULED-OUT dalla campagna metaheuristic (synthesis 2026-08-05, indagine ESAURITA): VND-FI e' INERTE sul ciclo staged (verificato); il ciclo staged ha già tabu entityTabuSize 7 + pillar + forager ACL 1000 attivi e la LS lascia comunque il buco H17. La direttiva di correttezza-logica vieta di annacquare il modello per costo computazionale; la performance e' stata risolta dal multi-threaded LS ratificato (TASK-480, -25% wall). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
