---
id: TASK-462
title: >-
  DEFETTO ingestione G14: letto foglio 'G14' (default 990) invece di 'G14
  2' (TOT 120, Au 70, La 50) - split aula/lab perso
status: Done
assignee: []
created_date: '2026-07-30 08:31'
updated_date: '2026-08-14 10:17'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: high
ordinal: 285000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DEFETTO DI INGESTIONE (verifica maintainer 2026-07-30 sul sorgente /home/user/Downloads/Copy of Calendario scolastico 2026_2027.xlsx): la pipeline ha letto il foglio SBAGLIATO per G14. Il workbook ha due fogli: 'G14' (aula=990, stage=0, lab=0, il default non compilato, identico al foglio 'Base') e 'G14 2' (il blocco reale: TOT 120, Au 70, La 50). Abbiamo letto 'G14' (990) invece di 'G14 2' (120). Conseguenze verificate nei nostri dati: calendario_class_days.csv ha 171 giornate G14 identiche al 100% giorno-per-giorno a G01 e G05 (totale 990); le 50h di laboratorio risultano tutte modalita' 'A' (aula), lo split aula/lab e' perso; incarichi G14 reali ~115-120h. L'esenzione isG14 dal fill (che ci portiamo da mesi) e' un CEROTTO su un dato sbagliato, non una proprieta' del dominio: va rimossa quando il dato sara' corretto. FIX: l'importatore (xlsx->CSV o CalendarioImportService) deve leggere 'G14 2' (non 'G14') e portare lo split aula/lab (Au 70 / La 50), non 990h tutte come aula. Collegato a TASK-461 (la falsa 'incoerenza' G14 veniva da qui).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
FIX APPLICATO (Fedora, 2026-07-30 pomeriggio, ordine esplicito di maintainer). (1) Estrattore calendario2027-extract.py: G14 tolto da CLASS_SHEETS, nuova sezione che legge 'G14 2' (blocchi mensili a 5 colonne: giorno, weekday, Au, La, note testuali nella colonna 'St'; celle non numeriche saltate), header-validation estesa. (2) Seed e data CSV rigenerati SOLO per G14: 35 righe / 120h (70 A + 50 L), 5 date con split aula+lab su due righe; ATTENZIONE: la rigenerazione integrale dell'estrattore avrebbe sovrascritto le curatele di calendario_fc.csv (fix stage G04 del TASK-434) e calendario_moda.csv, ripristinate: i CSV committati NON sono output puri dell'estrattore, landmine da censire. (3) DataGenerator.generateExpectedDailyHours aggrega per (gruppo,data) invece di fallire sui duplicati; fatale solo (gruppo,data,modalita') doppio. Registrato in memoria datagenerator-authorized-changes. (4) Test seed aggiornato 1880->1739, G14 70/50/0, exit 0. RESIDUO: l'esenzione isG14 dal fill e' ora un cerotto potenzialmente superfluo, rimozione da misurare separatamente.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
FIX CONFERMATO DAL GOLDEN (Fedora 2026-08-14): il golden colloca esattamente 120 ore reali per G14 (128 righe - 8 Fasullo trappola sabato 19/09 = 120), distribuite SOLO giovedi' (73h) e venerdi' (42h) = 115 collocabili in griglia + 5 di Orientamento sabato 16/01 off-grid. Combacia col seed corretto dal fix del 30/07 (G14 2: TOT 120, 35 righe). Materie golden G14: Servizio stilistico 30 (T26), Confezione 30 (T16), Orientamento 20 (T14), Comportamento Sociale 20 (T13), Competenze Digitali 20 (T06). RESIDUO spin-off: l'esenzione isG14 dal fill ora poggia sulla forma part-time reale gio-ven (non piu' sul dato sbagliato); la sua eventuale rimozione e' TASK-496 (misura separata, priorita' bassa). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
