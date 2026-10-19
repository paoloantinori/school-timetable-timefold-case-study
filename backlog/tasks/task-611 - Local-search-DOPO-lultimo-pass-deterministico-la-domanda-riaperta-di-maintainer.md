---
id: TASK-611
title: 'Local search DOPO l''ultimo pass deterministico: la domanda riaperta di maintainer'
status: To Do
assignee: []
created_date: '2026-09-13 08:32'
updated_date: '2026-09-14 10:50'
labels:
  - claude-planned
  - ls
  - phase-structure
  - rivisitazione
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DOMANDA RIAPERTA (maintainer, 13/09: 'non sono convinto'): dovremmo far girare una local search DOPO l'ultimo pass deterministico, invece/accanto ai finisher compositi?

EVIDENZA PRO: il confine post-catena oggi e' morto (nessuna metaeuristica sullo stato consegnato: wiring verificato); un'LS con accettazione lasca (tabu/late-acceptance sull'HARD) PUO' attraversare valli che mosse singole strette non attraversano; e' il meccanismo generico (non ha bisogno di enumerare le classi di riparazione una a una come il finisher).

EVIDENZA CONTRO (da superare, pre-registrata): iter-30b ERA una post-repair LS e fu RIMOSSA (verdetto: consegne convergenti senza headroom locale, +1.7k medium per 900s); TASK-551 medium-pura finale resta flag OFF per la stessa ragione; il composito -11->-9 e' una COPPIA di mosse dove la prima e' hard-neutrale/peggiore: serve accettazione che ammetta il peggioramento, col rischio di vagare in hard peggiori contro il vincolo binario 0-hard; wall: 600s+ per tentativo proprio mentre TASK-584 mostra regime all-doomed a 5 tentativi pieni; varianza stocastica su TUTTE le consegne (il finisher e' inerte senza residuo).

QUANDO RIAPRIRE: se il CompositeSwapFinisher (TASK-609) lascia residui ricorrenti che la scansione mirata non chiude; o se dopo l'adozione del finisher il residuo medio resta > 0 su piu' consegne consecutive (misurabile dal residual ledger).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Pre-registrato: la tesi contraria da superare (iter-30b rimosso per no-headroom; valle hard non attraversabile da mosse singole strette; wall aggiuntivo in regime all-doomed; varianza stocastica su consegne)
- [ ] #2 Spike: LS finale a accettazione hard-stretta/medium-lasca (late acceptance sull'HARD solo) su banco snapshot con budget limitato: quante valli attraversa realmente? confronto col finisher deterministico sullo stesso residuo
- [ ] #3 Metrica pre-registrata: (guadagno hard) / (secondi spesi) e varianza di consegna confrontata col finisher
- [ ] #4 Verdetto con predizione scritta PRIMA; nessuna adozione senza gate
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
EVIDENZA NUOVA 14/09 (Fedora, post TASK-608/609/612): la domanda 'LS dopo l'ultimo pass deterministico?' si e' arricchita di un dato strutturale. OGGI il ciclo chiude con una catena tutta deterministica (QFR-2 -> EDF -> harvest -> ... -> corner-b refinement -> CSF -> EDF-ext al delivery): la LS timeslot NON gira MAI sullo stato consegnato (verificato nel wiring, e il TASK-608 lo indica come componente della classe COMPOSITE_MISSING insieme all'assenza di mosse composite). Il banco ha dimostrato che su quello stato esistono catene migliorative (RELAY-k) MA anche che le mosse SINGOLE della LS lì non bastano (nessuna mossa singola migliorativa: il -11 non si chiude con il repertorio attuale). CONSEGUENZA per questo task: una 'LS post-repair' con le mosse attuali avrebbe valore PESSIMO-dimostrato sul residuo fill/boxed (niente mosse composita = stallo); il suo eventuale valore resta (a) sul tier medium (rifinire cio' che i pass deterministici non guardano), (b) mid-cycle sui bacini condannati TASK-584. Se si riapre, va riaperta CONGIUNTAMENTE a TASK-610 (LS composita: custom Move evict+place dentro una LS post-repair e' l'unica combinazione che il census dice potenzialmente in grado di chiudere residui che i pass enumerativi non coprono, es. profondita' >3). RACCOMANDAZIONE: unica decisione congiunta 610+611, gate su banco replay prima di qualsiasi ciclo.
<!-- SECTION:NOTES:END -->
