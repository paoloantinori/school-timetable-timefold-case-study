---
id: TASK-428
title: >-
  Pacchetto domande scuola con evidenza quantificata (G14 990vs115, floor 1
  FC, compattare-vs-spalmare, stage-days G08)
status: Done
assignee: []
created_date: '2026-07-27 04:45'
updated_date: '2026-07-29 05:19'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 251000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Le domande vivono in claudedocs/spalmone-2026-27/domande-brevi.md; questo task traccia la PREPARAZIONE E INVIO del pacchetto con i numeri nuovi della campagna: (1) G14: il foglio chiede 990h, incarichi 115h - golden stesso 0% full-day su G14, chiedere quale sia il piano reale (Q gia' abbozzata); (2) G04: floor strutturale >=23h lab-quota, golden lo riempie con Informatica (noi 43% vs golden 32% full: siamo gia' oltre) - conferma della pratica; (3) compattare-vs-spalmare con l'evidenza del trade profondita'/full-day (batteria 26/07 + train2); (4) FYI stage-days G08 2027-04-23/26 contraddetti dal golden (gia' noto). Output: doc pronto per maintainer da inoltrare; nessun blocco della campagna in attesa.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSO. Dossier domande-brevi.md completato con i 4 item: Q23 G14 (990h foglio vs 115h incarichi, golden 0% full-day), Q24 G04 lab-quota floor 23h (riempito con Informatica), Q25 compattare-vs-spalmare (evidenza m-12 quantificata: full-day 78%, sopra 87, burst 45->40%, gap-weeks -103, collegata a Q20), stage-G08 = Q21.1 gia' presente. Doc pronto per maintainer da inoltrare a referente. DoD N/A (puro doc, nessun codice).

Q26 candidata (da anatomia 427, 2026-07-27): G10/T01 - il seed le assegna piu' ore di quante il foglio preveda per i giovedi' G10 (unico giorno in cui puo' insegnare): il solver e' FORZATO a giornate da 8h (4 foglio + 4 T01 pomeriggio) su 11 giovedi'; il golden non le ha. Conferma del monte-ore reale T01 su G10 (discrepanza storica datagenerator-protection: 244 vs 192) e di dove la scuola intende collocarle.

Q26 RITIRATA (~14:10): il seed combacia ESATTAMENTE col golden (T01 60/60/65/46, tot 231; la vecchia nota 244-vs-192 era del seed pre-2026/27). Il golden risponde da solo: giovedi' monografici dic-giu previsti dal foglio. Nessuna domanda alla scuola necessaria - sostituita dalla leva solver TASK-432.

Q27 (da spike G04, 2026-07-27 notte): calendario_fc.csv dichiara stage=0 per G04 su tutte le date, ma il golden schedula 240h di Stage (30 date 2027-04-26..06-14; 6 dei suoi giorni li' sono PURO stage 8h). FYI alla scuola per correzione foglio; operativamente il golden fa fede (fix dati in TASK-434).
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-29 05:19
---
Lista riscritta (Fedora, 29/07) su richiesta maintainer: ordinata per GRADIENTE DI DUBBIO invece che per numero storico. Sezione A = 12 conferme SI/NO (conclusioni gia' derivate dal golden: disponibilita' T10/T12/T13, giorni fissi degli 8 docenti, G14 gio-ven, pomeriggi, spalmare fino a giugno, stage su chiusure e sabati, Orario-wins MODA, residuo G04, piu' le due nuove sui tag aula: Progettazione Termoidraulica e Tecnologia tessile fuori dall'officina). Sezione B = 2 scelte binarie (Taglio Metalli officina o R17; Servizio stilistico pratica o teoria). Sezione C = 4 domande vere (seconda mattina T16, piano ore G14, G08 23-26 aprile, capienza R17). Obiettivo dichiarato: minimizzare lo sforzo cognitivo di chi risponde.
---
<!-- COMMENTS:END -->
