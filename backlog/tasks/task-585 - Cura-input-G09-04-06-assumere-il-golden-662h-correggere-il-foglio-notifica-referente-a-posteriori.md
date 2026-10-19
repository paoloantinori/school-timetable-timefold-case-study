---
id: TASK-585
title: >-
  Cura input G09 04/06: assumere il golden (662h), correggere il foglio,
  notifica referente a posteriori
status: Done
assignee: []
created_date: '2026-09-05 06:03'
labels:
  - claude-planned
  - input
  - golden-first
  - seed
milestone: m-13
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cura input golden-first, gia' decisa nel piano 28/08 FASE 1 (direttiva maintainer 28/08: NON attendere referente, precedente TASK-550 golden-first). Chiude G1 (golden a 0hard sotto il vincolame corrente).

FATTO: il golden pratica 662h su griglia per G09 e lascia VUOTO il 04/06. Il foglio calendario_class_days.csv dichiarava una riga per il 04/06 (quota 4) che il golden non riempie: e' una domanda fantasma (famiglia TASK-550: quando l'input eccede cio' che il golden pratica, si assume il golden, si corregge l'input, si notifica a posteriori, non si chiede al solver di collocare l'eccesso).

DA FARE (SOLO seed CSV + expectedUnscheduledHours, MAI DataGenerator che e' file protetto): (1) drop della dichiarazione delle 4h del 04/06 G09 (o dell'intera riga se quota=4); (2) +4h in Course.expectedUnscheduledHours di G09; (3) re-pin golden: GoldenSolutionRegressionTest H34 atteso 0hard (era -1), H0 atteso 0 (le 4 lezioni ora attese non piazzabili); (4) le due copie del CSV (src/main/resources/seed/ e claudedocs/spalmone-2026-27/data/) editate in modo binario identico: il test del contratto byte-identical resta verde, CRLF preservati.

DERIVA da verificare subito dopo la cura: la v2 pre-booking non riservera' piu' il 04/06 (cella fuori scope) e la conservazione G09 si chiude a 662=662: atteso H32 di G09 piu' pulito, nessun nuovo boxed (le 4 lezioni ora attese).

NOTIFICA a posteriori nel pacchetto referente (la domanda diventa: "abbiamo assunto il pool praticato 662 e corretto il foglio; confermate o le 4h erano reali?"). Se referente risponde "reali": revert della cura (una riga + un numero), nessun altro impatto.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 calendario_class_days.csv corretto (entrambe le copie src/main/resources/seed/ e claudedocs/spalmone-2026-27/data/, byte-identiche, CRLF preservati): le 4h del 04/06 G09 rimosse o spostate a expectedUnscheduledHours
- [ ] #2 +4h in Course.expectedUnscheduledHours di G09 (meccanismo domanda fantasma TASK-550)
- [ ] #3 DataGenerator.java NON toccato (file protetto)
- [ ] #4 GoldenSolutionRegressionTest re-pinnato: H34 atteso 0hard (era -1), con commento della causa
- [ ] #5 Test contratto byte-identical dei seed verde; suite test.sh EXIT 0
- [ ] #6 Notifica a posteriori aggiunta al pacchetto referente (domanda: 'abbiamo assunto il pool praticato 662 e corretto il foglio; confermate o le 4h erano reali?')
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
