# Phase-2 smoke screen: B-TIMESLOT-1 metaheuristic arms (notte2, 2026-07-31)

5 armi, 90s ciascuna sul default snapshot (`task410-results/val410-run2`, stato FINALE).
`Phase2SmokeSnapshotTest` con `PLANNER_TEST_PH2SMOKE_ACCEPTOR`/`_PARAM` (parametrizzato stanotte).
Identica partenza per tutte: `hard=-9870 medium=-2165974 soft=59421 recurrence=46.1%`.

| arm | acceptor | hard delta | medium delta | recurrence end |
|-----|----------|-------|----------|----------------|
| A | entityTabu 7 (control) | +259 | +523916 | 43.0% |
| E | entityTabu 11 | +259 | +523916 | 43.0% |
| H | entityTabu 3 | +253 | +503498 | 42.8% |
| B | lateAcceptance 400 | +17 | +17352 | 46.0% |
| G | lateAcceptance 1000 | +20 | +19752 | 46.0% |

## Verdetto screen: PASSATO (nessuna arm esplode)
Tutte e 5 migliorano o tengono hard; nessuna diverge. Procedono tutte ai run staged.

## CAVEAT: non predittivo del B-TIMESLOT-1
- Lo snapshot e' FINALE (stato gia' buono): misura "come l'accettatore TENDE uno stato buono", non
  come cerca partendo dalla costruzione. Su uno stato buono tabu e' aggressivo (migliora ma erode
  ricorrenza 46->43%), LA e' conservativo (tiene, ricorrenza 46% immobile).
- B-TIMESLOT-1 parte dalla COSTRUZIONE (stato peggiore): li' LA potrebbe esplorare meglio.
  Quindi il ranking smoke (tabu >> LA) NON va preso come predizione del verdetto staged.
- Lo smoke serve solo a escludere divergenze prima di bruciare ~46min per braccio. Compito assolto.

## Nota tecnica
- A (tabu7) ed E (tabu11) sono byte-identici nel delta: su 90s e stesso snapshot la memoria tabu
  7 vs 11 converge ugualmente. H (tabu3) leggermente sotto. La famiglia tabu e' cluster compatto;
  la famiglia LA e' un cluster separato e conservativo. Segnale da confermare sul run reale.
