---
id: TASK-510
title: >-
  Probe di piazzamento esatto per i 20 boxed (perche' ExactFinisher non trova
  cella per la RA di T15 nei lun/ven con quota-spare)
status: Done
assignee: []
created_date: '2026-08-17 05:28'
updated_date: '2026-08-17 06:02'
labels:
  - claude-planned
  - solver
  - diagnosis
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Conseguenza diretta della notte 16-17/08 (verdict ROUND 5-PREVIEW b0f5a32f): la famiglia dei guard aggregati e' confutata (annuale zero-declini, per-weekday onesta 345>=50, ablation acconciatura negativa) e il verdict indica come unico passo sensato il probe sul PIAZZAMENTO ESATTO. I 20 boxed sono G04 / RA / T15, bloccati dai pin meccanica/informatica/termoidraulica dello stesso docente sui giorni-L di 1/G08 e G13. La domanda aperta: perche' ExactFinisher depth<=1 non trova cella per la RA nei lun/ven liberi dove G04 HA quota-spare? Approvato da maintainer 17/08 mattina (opzione i prima di b/c).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Meccanismo ESATTO del blocco dei 20 boxed (G04 / RA / T15) stabilito: per ogni candidato cella nei lun/ven dove G04 ha quota-spare, quale vincolo la esclude (VRP getAvailableTimeslots? finestra? conflitto docente residuo? M20/H7 indiretto?), tabellato cella per cella
- [x] #2 Probe metodologicamente onesto: replica le condizioni di fine costruzione del run4 (pin della chain in posizione), non uno stato idealizzato
- [x] #3 Risposta esplicita alla domanda: il blocco e' strutturale (nessuna cella esiste in nessun orario chain-compatibile) o di copertura (la cella esiste ma la costruzione/ExactFinisher non la trova)?
- [x] #4 La forma del round 4 derivata dal meccanismo trovato, con la raccomandazione scritta (e le alternative scartate col perche')
- [x] #5 Nessun nuovo full-solve richiesto se il probe e' offline (test/checkpoint-based); se serve un run, uno solo
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-17 06:02
---
ESITO (laptop, 17/08 mattina, sezione ROUND 4-PROBE nel VERDICT ab-chain-2026-08-16): AC#1-AC#4 chiusi.
---

created: 2026-08-17 06:02
---
AC#1/AC#3: STRUTTURALE su TUTTE le assi. La RA di G04 e' materia LAB quota-bound (QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP: Recuperi ed Approfondimenti nel bound-set di G04): il VRP permits() richiede labHours>0, le righe lun/ven sono (aula=6, lab=0), ZERO celle VRP su lun/ven (il presupposto della domanda era falso: lo spare lun/ven e' spare AULA). Giovedi': residuo gruppo 0. Mercoledi': 34 celle slot-libere (14:30/15:30) MA T15 e' a esattamente 6h (cap H7) su OGNI mercoledi' (174h PINNATE mec/CN/Cad + 30h RA gia' collocate): ogni cella e' H7-illegale. EF placed 0 of 20 e' CORRETTO, non un bug di copertura. Causa a monte: il deriver/enumerator pinna mec/CN/Cad riempiendo tutto il budget H7 dei giorni-L del docente, zero headroom per la RA (stesso docente, altra quota-bound).
---

created: 2026-08-17 06:02
---
AC#4 raccomandazione: budget-reservation lato deriver (riservare quotaBoundLabDemandNonEnumerated(teacher,group)/labDays ore per giorno-L prima di pinnare). Scartate: guard aggregati (refutati dai run, asse sbagliato), slegare RA dal bound-set (modello contro input: golden pratica RA solo su giorni-L), accettare -42 (fallback: H0 -40 binario blocca m-13), piu' tentativi/EF depth>1 (bacino deterministico, nessuna cella legale).
---

created: 2026-08-17 06:02
---
AC#5: un solo run, fermato a fine pre-assign (profilo probe510: deriver 15 righe, enumerator 2/3 FEASIBLE, Phase 1 332 + Phase 2 402, COMPLETE). Test di supporto BoxedRaPlacementProbeTest aggiunto (variante H2 senza seed reale, documentata non eseguita).
---
<!-- COMMENTS:END -->
