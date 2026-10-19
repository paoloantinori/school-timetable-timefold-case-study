---
id: TASK-518
title: >-
  Round 8: gate aggregato per-pair per-weekday PRIMA del pinning (pin +
  non-enumerata <= cap x giorni-L, ritmo golden) + A/B run8
status: Done
assignee: []
created_date: '2026-08-17 20:15'
updated_date: '2026-08-18 14:08'
labels:
  - claude-planned
  - solver
  - pre-assign
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Conseguenza del ROUND 7 (8eb96310, verificato Fedora): il meccanismo golden-guided cura il bersaglio (RA 0 boxed, convivenza riprodotta) ma presidia il ritmo solo dove il blocco non entra: le 25 ore di code clip diventano boxed e i mercoledi' restano a 174h pinnate. Il round 8 completa il budget: gate aggregato per-pair per-weekday PRIMA del pinning (pin + domanda non-enumerata <= cap x giorni-L). Parentela dichiarata col TASK-517 (placement-parity check): stessa idea vista dai due lati, il gate e' la prevenzione, il check e' la rilevazione.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Gate aggregato per-pair per-weekday applicato PRIMA del pinning: per ogni (docente, gruppo) con domanda lab-bound non enumerata, la somma di pin previsti + domanda non-enumerata deve stare dentro cap x giorni-L, col ritmo golden come target interno (riferimento misurato: T15 4h/giorno nei giorni con RA)
- [x] #2 Il gate decide PRIMA (piano), non durante il greedy: i mercoledi' di T15 non devono restare a 174h pinnate mentre la RA prende le 2h riservate (il difetto del round 7)
- [x] #3 I 25 boxed delle code clip del round 7 devono rientrare (il budget totale li contiene) e i 20 RA devono restare a zero
- [x] #4 Unit test: caso round 7 (code clip) + caso round 3 (RA) + caso round 6 (azzeramento blocco: vietato) tutti coperti
- [ ] #5 A/B run8: control = chain ON senza gate (atteso -42), treatment = chain + gate. Gate di adozione: hard 0 treatment, KPI in banda, wall. Bozza di ratifica se passa, NON applicata
- [x] #6 Nessun default flip: tutto dietro lo stesso flag planner.pre-assign.budget-reservation.enabled
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-18 01:57
---
RUN8 (18/08 notte, ROUND 8 nel VERDICT): GATE SFIORATO NON PASSATO ma risultato qualitativo. Control -42 (quinta replica del bacino). Treatment -3hard: H0 ZERO (RA e code clip tutte collocate, verificato su DB), H29 zero, residuo H7 -2 + H17 -1 (famiglia LS-varianza, non strutturale). Ritmo golden visibile: 13 mercoledi' T15 a 4h come il golden; pin 294h + 232h al solver tutte collocate. KPI: full-day 77,5% (migliore), ricorrenza 68,6% (banda), buchi 1 slot. Wall 150 vs 124 min. Placement-parity TASK-517: 23 -> 20 flag, il flag T15/G04 scomparso.
---

created: 2026-08-18 01:57
---
AC#5: NESSUNA bozza di ratifica (hard 0 binario, -3 non e' 0). Prototipo resta DEFAULT OFF. Proposta per maintainer: round 9 = stesso treatment su seed diverso (o N=2-3) per stabilire se -3 e' pavimento o singolo-run; e la stessa cura estesa alle altre coppie flaggate (T06, T18, T22) come seguito naturale, non nuovo meccanismo.
---

created: 2026-08-18 01:57
---
Nota protocollo: cattura autorevole ab-on-run8-final a quiescenza confermata (driver cap 150min ha catturato a 03:01 durante l'ultimo stadio). Commit implementazione 1bbe4d9a.
---

created: 2026-08-18 14:08
---
ROUND 9 (18/08, CENSORED): cap 150 min del driver taglia il treatment prima della quiescenza su TUTTI e tre i seed (11/12/13). DONE scores = best-so-far in memoria, non finali: seed12/13 -2hard con B-ROOM ancora attivo alla kill (stati persistiti con 10.759/2.859 lezioni senza stanza), seed11 -2118 con timeslot-tier ~-650 (bacino peggiore esiste). Risposta alla domanda 'pavillo o luck': NON RISPENDIBILE con questi dati; compatibile con pavillo -2/-3 ma non provata.
---

created: 2026-08-18 14:08
---
Nota tecnica: plain -Dgrasp.seed normalizzato dal wrapper TASK-483 (banner grasp=false + jcmd proof, archiviato ab-on-run9-seed1-invalid); veicolo corretto force-grasp-seed, MA col seed forzato tutti gli attempt condividono la costruzione e il multi-start perde la diversificazione dei retry (run piu' lunghi).
---

created: 2026-08-18 14:08
---
Review Fedora applicata: findings 1a/1b/2 in 6bbbcbe6 (fallback clamp cap-1, trim mai a zero, skip non silente; test 12/12 suite 1070/0). Differito documentato: asimmetria pair-view plan-sizing vs teacher-view greedy + mappa morta (candidato round 10).
---

created: 2026-08-18 14:08
---
FILONI 2/3 NON avviati (regola: gate fallito = documento e fermati). Follow-up per maintainer: round 9-bis cap 240 min, o decisione sull'evidenza mista (round8 -3 finale pulito + due best-so-far -2).
---
<!-- COMMENTS:END -->
