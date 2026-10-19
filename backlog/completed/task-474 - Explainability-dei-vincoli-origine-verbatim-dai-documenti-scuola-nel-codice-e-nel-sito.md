---
id: TASK-474
title: >-
  Explainability dei vincoli - origine verbatim dai documenti scuola nel codice
  e nel sito
status: Done
assignee: []
created_date: '2026-08-01 08:25'
updated_date: '2026-08-01 12:23'
labels:
  - claude-planned
dependencies: []
documentation:
  - claudedocs/spalmone-2026-27/task474-execution-plan-2026-08-01.md
  - claudedocs/spalmone-2026-27/vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt
ordinal: 297000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Richiesta maintainer (01/08): per OGNI vincolo (tabelle canoniche H/M/S di CLAUDE.md), aggiungere (a) nel CODICE (javadoc del metodo in SchoolScheduleConstraintProvider/BaseConstraintProvider) e (b) nella DESCRIZIONE DEL SITO (src/main/webui/src/data/constraints.ts + locales i18n en/it) un blocco ORIGINE che quoti VERBATIM la fonte nei documenti di input ricevuti dalla scuola, con file, tab, pagina o sezione, in modo che l'explainability sia facilmente ritrovabile.

Inventario delle fonti da citare (con il loro identificatore preciso):
- 'Spalmone 2026_2027 FC DUALE condiviso maintainer.xlsx', tab 'Vincoli Classi' / 'Vincoli Docenti' / 'Vincoli DA ANALIZZARE' (estrazione verbatim gia' in claudedocs/spalmone-2026-27/vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt: riusarla, citando tab e riga).
- 'Copy of Calendario scolastico 2026_2027.xlsx', tab per-gruppo (righe Totali Ore/STAGE/Lab.) -> seed calendario_class_days.csv / calendario_fc.csv.
- Il docx dei vincoli docenti (fonte dei TASK-368/369/370) con sezione/riga.
- seed/incarichi.csv (monte-ore), risposte di referente del 29/07 (doc di riferimento), decisioni maintainer datate (CLAUDE.md ratifiche).
- Dove l'origine e' una PRATICA DEL GOLDEN e non un documento: citare seed/lessons.csv + il doc di misura che la fonda (es. golden-cadence-reference.json per M34/M35, misure bucket B/C, task471 per il set teoria).
- Dove NON esiste alcun documento (vincoli fisici/strutturali: H1/H2/H3 conflitti, H29 aula, ecc.): dichiararlo esplicitamente ('vincolo strutturale, nessun documento scuola; introdotto TASK-nnn').

Regole: citazioni VERBATIM tra virgolette (esenti dalla regola no-em-dash), il resto in prosa; nessun cambiamento di comportamento (solo commenti/descrizioni); il formato del blocco Origine deve essere UNIFORME (es. 'Origine: <quote> - <file>, <tab/sezione>' su una riga o poche). Sul sito la stringa origine va nelle locales en/it (traduzione della cornice, quote invariato).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Ogni vincolo registrato (.asConstraint) ha il blocco Origine nel javadoc, uniforme, con quote verbatim o dichiarazione onesta dell'assenza di documento
- [x] #2 constraints.ts + locales en/it espongono l'origine sul DocsPage per ogni vincolo
- [x] #3 Nessun cambiamento di comportamento: test.sh EXIT 0 + ui-typecheck + ui-build verdi
- [x] #4 Spot-check: 5 vincoli a campione verificati contro la fonte citata (quote davvero presente nel file/tab indicato)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
VERDETTO VALUTAZIONE (zai, 2026-08-01): SESSIONE FRESCA. Volume ~150-200 edit (50 vincoli registrati, alcuni split ~55-60 blocchi logici, x 3-4 artifact javadoc+constraints.ts+locale en/it), formato Origine UNIFORME da disegnare una volta, mappatura fonte-per-vincolo judgment non mechanical, DoD pesante (simplify+code-review+test+ui-typecheck+ui-build). Le quote verbatim sono GIA' estratte (vincoli-tabs-golden-xlsx-estratto, 110 righe) quindi la sessione fresca e' mappa+scrivi non research+scrivi.

FORMATO Origine uniforme (3 casi): (1) documento scuola verbatim 'Origine: "<quote>" - xlsx, tab <T>, riga <n>'; (2) pratica golden 'Origine: pratica golden, no doc scuola; <measure doc>'; (3) strutturale 'Origine: vincolo strutturale, no doc scuola; introdotto TASK-nnn'.

MAPPA prep (4 categorie, fatta): A documento-verbatim (H7,M24,M25,M26,M27,TASK-469,TASK-472,TASK-475,M-TR,availability - quote pronte nell'extract); B calendario xlsx (M12,M20,M28,M30,M31 e quota-derived); C pratica golden (M32,M34,M35,M15,S17,M21,S13,H28); D strutturale (H0,H1,H2,H3,H5,H6,H11,H17,H29,H-M2,M3,S1,S7,S8,S10,S11,S16).

CROSS-FINDING dalla prep: l'extract (riga 96-97) conferma TASK-475 A1 (T14+T20 entrambi doppio moda, budget 100/100/85) - non piu' assunzione pendente. La riga 50 e' la fonte verbatim esatta di TASK-469 (6h post-stage curriculum, anche 2 consecutive).

Piano completo + workflow sessione fresca: task474-execution-plan-2026-08-01.md.
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Explainability completa: ogni vincolo registrato (62 .asConstraint) ha ora il blocco Origine uniforme (javadoc IT sul metodo builder + originKey in constraints.ts + campo origin in en/it docs.json). Copertura: AC#1 62/62 vincoli con javadoc Origine (formato spike H7); AC#2 59 voci constraints.ts con originKey + 7 nuove card (H-COPRESENCE, M34, M35, M-LF, M-TC, M-TR, M-CPS) + render dal commit spike d3195956; AC#3 test.sh 947/0/0/16 EXIT 0 + ui-typecheck EXIT 0 + ui-build EXIT 0 (0 righe di codice Java modificate, verificato con comment-strip residue diff da skeptic Opus); AC#4 spot-check 5/5 quote verbatim verificati nell'extract. Origini classificate onestamente in 4 categorie (A workbook verbatim, B calendario per-gruppo, C pratica golden con doc di misura, D strutturale). DoD: /simplify 4 agent (1 finding H5 workbook-name fissato); /code-review high 2 skeptic Opus (behavior CONFIRMED, content REFUTED -> 3 bug verbatim reali fissati T10/T20/T05 + 1 falso positivo M-TR dismissato e rito-verificato). 5 commit (c5cab783 hard, 97b0fbab medium, 610188b8 soft, a3adfab5 fix H5, ffc326f6 fix verbatim). Mappa origine (spec + spot-check) in claudedocs/spalmone-2026-27/task474-origin-map-2026-08-01.md.
<!-- SECTION:FINAL_SUMMARY:END -->
