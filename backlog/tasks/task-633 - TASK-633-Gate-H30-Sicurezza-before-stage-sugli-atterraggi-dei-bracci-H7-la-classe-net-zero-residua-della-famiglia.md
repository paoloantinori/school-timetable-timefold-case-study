---
id: TASK-633
title: >-
  TASK-633 - Gate H30 (Sicurezza before stage) sugli atterraggi dei bracci H7:
  la classe net-zero residua della famiglia
status: Done
assignee: []
created_date: '2026-09-20 10:23'
updated_date: '2026-09-20 11:04'
labels:
  - claude-planned
  - h7-cap
  - code-review-finding
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal code-review high del TASK-632 (reviewer B, 20/09): i tre bracci H7 del HoleRelayFinisher (h7cap TASK-620, h7-depth3 TASK-621ext, h7swap TASK-632) muovono lezioni CROSS-DATE senza alcun gate H30. isSlotLegalFresh copre H6/H5/H4/pinned/H20/quota/stage-confinement/no-session ma NON H30 (Sicurezza before stage, BaseConstraintProvider, promosso hard maintainer 15/08 TASK-472). SCENARIO CONCRETO: il docente overcap porta una lezione di Sicurezza collocata prima della StagePattern.startDate del gruppo; la mossa (export/swap/chain) la sposta a una data >= stage start; l'H30 nuovo (-1 hard) pareggia l'H7 curato (-1 hard), l'oracolo NET-hard del wrapper tiene la coppia a netto zero e la VIOLAZIONE SPEDISCE. Esattamente la classe net-zero-trade che la direttiva bounded-no-op vieta (precedenti: E1 first-hour/H31 e C2 H28 del 629 v4, curati in d75d837a). PRE-ESISTENTE e-family: h7cap e h7d3 sono RATIFICATI con questo gap; il 632 default-OFF aggiunge solo una terza route.

FREQUENZA ATTESA: rara (Sicurezza sono poche ore, serve che il docente overcap ne porti una quel giorno e che la destinazione cada oltre lo stage start del gruppo), ma la classe e' reale e verificata nel meccanismo.

CURA PROPOSTA (da bancare): belt leg per-mossa "Sicurezza lesson non atterra a data >= StagePattern.startDate del suo gruppo" nei tre bracci (o gate pre-apply sulla lezione Sicurezza: subject == SICUREZZA_SUBJECT && landingDate >= stageStart -> census HRF-H7SWAP/-H7CAP/-H7D3-H30). Serve l'accesso a StagePattern nel Live index (plumbing: pubblicare per-gruppo stageStart pre-solve come fa M-CPS con stage-end da DailyActivityQuota, o passare gli StagePattern nel costruttore Live). Priorita': prima di OGNI ratifica del 632; per h7cap/h7d3 gia'-ratificati e' un audit da fare (verificare se il caso e' mai apparso nei census/banchi storici: cercare Sicurezza nei log HRF).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
20/09 GATES COMPLETE (commits 4717951e + fdfd609d + 6c620634): /simplify 2 agent combinati (verdetto ship-as-is dopo 2 cure da un token: isEmpty ridondante, LocalDate::min; costo della gamba <1% della belt; altitudine GIUSTA: la belt post-apply e' l'unico punto che vede le celle finali, i siti pre-move valuterebbero la posizione sbagliata) + code-review 2 reviewer (bug scan PULITO su 4 assi con equivalenza esatta verificata contro il provider: min-start iff esiste-pattern, bordo inclusivo, normalizzazione identica, tutti e 7 i call site post-apply; storia+commenti: javadoc verifyComposite aggiornato, commento freeze corretto in family-strict). AUDIT richiesto: grep Sicurezza su tutti i log HRF nel repo = ZERO occorrenze (nessuna mossa applicata ha mai toccato una Sicurezza: la gamba e' assicurazione pura, mai sparata). DICHIARATA la parita' banco-produzione per contratto sulle 4 copie test-side di verifyComposite senza la gamba (precedente TASK-622), da risincronizzare al prossimo banco che tocca la famiglia. PENDING: banco inerzia 3 basi su 4717951e (laptop in volo, predizione: esiti identici e COMP-H30 assente).
<!-- SECTION:NOTES:END -->
