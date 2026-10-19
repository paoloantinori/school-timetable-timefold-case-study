# Quadro del mattino, 2026-07-28 (~07:30 CEST) - notte gate2 + attribuzione

Tutto verificato sui raw (Fedora) run per run durante la notte; VERDICT-gate2 di zai
fedele. Cronologia completa: VERDICT-gate2.md + backlog 430/432/433/434.

## Esiti della notte (4 run live + 434)

| Run | Config | hard | day-start | full-day | emptySpan G04 | esito |
|---|---|---|---|---|---|---|
| gate2-runA1 | 430+432+433 | -24 (H0, tutti T01) | 99,9% | 76,0 | 13 | infeasible (432) |
| gate2-run430-433 | 430+433 | -4 (3 H17 + 1 H0) | 100,0% | 74,5 | 11 | sporco (430) |
| gate2-run433only | 433, baseline post-434 | **-56 (H0)** | 99,4% (13) | 78,5 | **1** | vedi sotto |
| m-12 baseline | nessuna leva | 0 | 96,1-96,3 | 78,2-78,5 | 9 | riferimento |

- **TASK-433 AnchorRepair: PROMOSSA dall'evidenza** - in tre run ha portato il day-start
  da 82 violazioni a 0/1/13 senza MAI un costo attribuibile a lei (0 conflitti, 0
  newly-late, guard M18 tiene). Candidata adozione.
- **TASK-432: bocciata** (24 H0 tutti T01: il profile per-GRUPPO sovra-prenota i
  giovedi' contesi tra i suoi 4 gruppi). **TASK-430: bocciata lean** (3 buchi H17 +
  full-day -3,7pp; il day-start lo copre gia' 433). Restano flag OFF coi lead scritti.
- **TASK-434 (stage G04 dai golden): FATTA e FUNZIONA** (emptySpan 9->1) con DUE
  refinement emersi dal run di conferma: (a) 16 ore stage oltre l'orizzonte 06-10 da
  contare off-grid (expectedUnscheduledHours) - fix banale; (b) il rimescolamento id
  del seed ha esposto la fragilita' d'ordine su T01: 40 H0 su G06.
- **IL NODO SISTEMICO E' UNO: T01** (432-H0, coda 427, 40 H0 di stanotte, giovedi'
  monografici golden). Cura = pattern-exhaustion PER-DOCENTE: pianificare upfront
  l'allocazione dei suoi giovedi' attraverso i 4 gruppi (i profili del foglio tornano
  esatti: 231h, il golden lo dimostra). E' il redesign corretto di 432 -> proposta
  TASK-435 (high).

## RATIFICHE PENDENTI (maintainer, proposta gia' presentata a voce ~07:15)

1. Adozione TASK-433 (AnchorRepair) - evidenza a 3 run.
2. Fix-16 off-grid su 434 (banale, pronto a partire).
3. TASK-435 teacher-centric allocation (high) - il redesign di 432 che scioglie il nodo.
4. 430/432 restano OFF.

GIA' ADOTTATI ieri (ratifica sera): staged+treno-2, 424 pin-netting, 425 M34/M35.
Tripla di applicabilita' (criterio maintainer): buchi 0 FATTO; day-start a parita' golden
DIMOSTRATO (433); vuoti-in-span 9->1 (resta il fix-16 + 1 residuo con donatori legali).

## In esecuzione / prossimo

zai idle dopo la notte; in attesa della ratifica di maintainer per: fix-16, TASK-435,
eventuale run di conferma finale post-fix (433 ON, hard 0 atteso VERO stavolta).

## RATIFICATO (maintainer, 2026-07-28 ~07:50)

Tutti e 4 i punti: (1) TASK-433 ADOTTATA (flip default ON in esecuzione); (2) fix-16
off-grid su 434 in esecuzione; (3) TASK-435 teacher-centric allocation creata (high,
m-12); (4) 430/432 restano OFF coi lead documentati. Prossimo: run di conferma finale
(433 ON, baseline fixata) con attesa hard 0 + tripla INSIEME.
