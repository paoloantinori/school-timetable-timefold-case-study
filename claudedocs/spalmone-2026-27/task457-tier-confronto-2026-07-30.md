# Task 457 - ritmo tier docenti: confronto run 2026-07-29b (TASK-456) vs 447 (riferimento)

Misura read-only (`scripts/analyze/teacher-tier-kpi.py`) per capire se la deriva
dei generici e' cambiata col nuovo stato (TASK-456 room-tier).

## Risposta: NON e' cambiata (coerente, TASK-456 e' room-tier, il tier-pacing e' timeslot-tier)

| metrica | 447 (riferimento) | 29b (TASK-456) |
|---|---|---|
| baricentro temporale generici (run-golden) | -6,6 giorni | **-7,5 giorni** |
| mix generici per mese (pattern) | front-load ott-feb +3%, crollo mag-giu -5/-6% | identico (delta <1%/mese) |

Il front-loading dei generici (~7g di anticipo vs golden, +3% ott-feb, crollo -7,8%
a maggio) e' STRUTTURALE del modello di schedule, preesiste a TASK-456, identico al
run 447. Lascia un residuo specializzato a fine anno (esattamente quando la
ricombinazione orizzontale del task 451 si fa difficile): non e' un effetto di
TASK-456, e' il segnale che motivava il task 451 stesso.

I docenti piu' front-loaded (29b): T06 -49g, T13 -34g, T23 -27g.
Specializzati stabili (+0,2g). Generici identificati (>=8 gruppi): 9 su 26.

Output grezzi: tier-kpi su lessons.txt del run 29b e del task447-results/cert.
