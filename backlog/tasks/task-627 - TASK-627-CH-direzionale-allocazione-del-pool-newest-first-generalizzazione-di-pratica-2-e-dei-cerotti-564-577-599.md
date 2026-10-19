---
id: TASK-627
title: >-
  TASK-627 - CH direzionale: allocazione del pool newest-first (generalizzazione
  di pratica-2 e dei cerotti 564/577/599)
status: To Do
assignee: []
created_date: '2026-09-18 10:16'
updated_date: '2026-09-19 08:08'
labels:
  - claude-planned
  - m-15
milestone: m-15
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla discussione maintainer 18/09 (m-15). Il builder e' greedy-forward + 4 cerotti in coda (pre-booking 564, pool-cap-fill 577, spread 599, pratica-2 in arrivo), tutti che spingono nella STESSA direzione: riempire partendo dalla fine. Il golden alloca l'ANNO: sparge le ore piccole fino a giugno (forma {1h:90 giorni} per G04) e due solutori indipendenti (golden + blind puzzle 18/09, verdetto 87-92% legali) convergono sulla stessa classe di filler. Ipotesi forte: pratica-2 non e' "un filler in piu'" ma il caso particolare della DIREZIONE generale "alloca il pool newest-first invece che forward-greedy". Questo task formalizza e misura la direzione generale: se il banco la conferma, alcuni cerotti diventano ridondanti e la CH si semplifica invece di accumulare patch.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Generalizzazione formalizzata: dal filler small-pool in coda (pratica-2 banca) all'allocazione del pool per-anno newest-first nel builder, confrontata col metro del golden (spr delle ore piccole fino a giugno, forma {1h:90 giorni} di G04)
- [ ] #2 #2 Banco differenziale a semi multipli: allocazione forward-greedy (corrente) vs newest-first su firma di costruzione (giorni vuoti di coda, full-day, ricorrenza, BLOCK-form)
- [ ] #3 #3 Le tre ratifiche-cerotto (564 prebooking, 577 pool-cap-fill, 599 spread) rilette come casi particolari della direzione newest-first: dichiarare quali diventano ridondanti se la direzione generale vince
- [ ] #4 #4 Se il banco vince: gate end-to-end (integrato-6 o successivo) con A/B appaiato e ratifica maintainer prima di qualunque flip
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
19/09 mattina (laptop, banco completato, wt/pratica-2 b17a3034 pushato): INERTE sul deterministico, VIVO su GRASP. Banco onesto = replay su checkpoint POST_CONSTRUCTION: det no-op (il fenomeno del cieco e' gia' stato assorbito da 564/577/599+cure; le 35 celle tail residue sono senza pool o H1/VRP-bloccate = strutturali). GRASP s101: 10/10 celle vuote G08 chiuse (20h, hard netto +20, zero nuove violazioni). Criterio pre-registrato NON centrato (bacino-dipendente, produzione consegna solo il det). AC#3: cerotti NON ridondanti (NO). Gate end-to-end NON scatta. Prototipo DEFAULT OFF come building block verificato (DoD piena: simplify 7 fix, code-review 10 finding curati, suite 1362/0 EXIT 0). Valore live condizionato alla domanda sospesa predictor/GRASP (TASK-624). DOMANDA maintainer PENDENTE: dormire come assicurazione o ritirare? Raccomandazione Fedora: tenere dormiente (precedente CSF TASK-609).

19/09 (maintainer: "Seguo i tuoi suggerimenti"): prototipo RESTA DORMIENTE come assicurazione legata a TASK-624 (precedente CSF TASK-609). TASK-627 CHIUSO: AC#1/#2 soddisfatti (banco differenziale det+GRASP), AC#3 risposto NO (cerotti non ridondanti), AC#4 non scatta (banco non vince sul det). laptop informato, sessione chiusa.
<!-- SECTION:NOTES:END -->
