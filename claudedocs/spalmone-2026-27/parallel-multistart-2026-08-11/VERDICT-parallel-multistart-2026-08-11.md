# VERDICT parallel multi-start spike - NEGATIVO (non vale la complessita') (zai, 2026-08-11)

Spike di performance (36h laptop grant): externalize il multi-start (N app+DB isolati concorrenti,
1 costruzione ciascuno, pick-best) invece del seriale TASK-483 (1 processo, N tentativi).
L'obiettivo era ~1x wall vs Nx seriale. **Risultato: NON raggiunto. Lo speedup e' marginale
(~1,2x) e il parallelo e' in realta' PIU' LENTO del seriale nel caso comune.**

## Risultato (parallel-multistart.sh N=3)

| metric | valore |
|--------|--------|
| solve wall (all-3-done) | **4266s = ~71 min** |
| TOTAL wall (boot+setup+solve) | 4405s = ~73 min |
| instance 1 (GRASP seed 1) | -10hard (bad seed) |
| instance 2 (GRASP seed 2) | **0hard**/-1310k |
| instance 3 (GRASP seed 3) | **0hard**/-1007k (pick-best) |
| p(0-hard) | 2/3 (pick-best = 0hard) |

Isolamento FUNZIONANTE: 3 container (planner-pms-1/2/3 porte 5433/4/5) + 3 app (8081/2/3) + 3
volumi freschi, nessun conflitto, cleanup in trap OK. Quindi l'architettura e' buildable.

## Perche' NON vale la complessita' (verdetto negativo)

1. **CPU-bound contention mangia il parallelismo.** I 3 solve concorrenti su 12 core sono ~3x
   piu' lenti ciascuno (ogni solve vuole tutti i 12 core per la LS; 3 concorrenti = ~4 core
   ciascuno). Wall parallelo 71 min vs seriale 3x28 = 84 min = **solo ~1,2x speedup** (non ~3x).
   La legge di Amdahl morde: se il solve usa gia' tutti i core, N processi concorrenti non
   accelerano, si dividono i core.
2. **Il seriale ha l'early-stop, il parallelo no.** TASK-483 si FERMA al primo 0-hard (spesso
   1-2 tentativi = ~28-56 min). Il parallelo (run-all-3 + pick-best) corre sempre tutte e 3 =
   71 min. Quindi **nel caso comune (good seed, 0-hard al primo) il seriale e' PIU' VELOCE**
   del parallelo. Il parallelo batte il seriale solo quando il seriale usa tutti e 3 i tentativi
   (bad seed: seriale 84 min, parallelo 71 min), caso in cui la differenza e' ~1,2x.
3. La complessita' (N container/porte/volumi + orchestrazione + cleanup) NON e' giustificata
   da ~1,2x speedup nel solo caso bad-seed.

## Semantica: OK (era il prerequisito)

Il pick-best parallelo TROVA il 0-hard (instance 3, pick-best). Quindi semanticamente il
multi-start parallelo e' equivalente al seriale (N costruzioni, pick-best). Il problema non e'
la correttezza, e' la performance (CPU-bound).

## Verdetto per Fedora

**NON costruire la procedura multi-start parallela per produzione.** La condizione del
multistart-ab.sh ("build the parallel speedup ONLY IF p(0-hard)>0") e' soddisfatta (m-13
approvato, p~1,0), ma la MISURA mostra che lo speedup non c'e' (CPU-bound + serial early-stop).
Il seriale TASK-483 (~28-56 min nel caso comune, stop al primo 0-hard) resta la procedura
migliore. Un vero parallel-speedup servirebbe piu' core (un solve per nodo, non 3 su 12 core
condivisi) o solve non-CPU-bound (non lo sono).

## Cosa conserva lo spike (non sprecato)
- L'harness `parallel-multistart.sh` e' buildable + testato (isolation funzionante): se in
  futuro ci fosse un nodo con molti core liberi (o solve I/O-bound), l'architettura e' pronta.
- La misurazione NEGATIVA e' essa stessa il dato: esclude un vicolo (la performance via
  parallelismo su una singola laptop CPU-bound), risparmiando a Fedora di provarci.
