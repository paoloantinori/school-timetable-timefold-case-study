---
id: TASK-623
title: >-
  TASK-623 - Celle sanity integrato-3: ordinary-on-stage-day (3 celle
  class_days), cura input G07 09/06 S->A, forma G08 01/04; scope Figurino
  M-BF chiuso dal golden
status: To Do
assignee: []
created_date: '2026-09-17 14:16'
updated_date: '2026-09-17 14:16'
labels:
  - claude-planned
  - sanity
  - golden-first
dependencies: []
references:
  - claudedocs/spalmone-2026-27/integrato-1/golden-oracle-celle-2026-09-17.md
  - claudedocs/spalmone-2026-27/integrato-1/REVISIT-dubbi-17-09.md
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal golden-oracle sulle celle rosse sanity di integrato-3 (doc claudedocs/spalmone-2026-27/integrato-1/golden-oracle-celle-2026-09-17.md, 17/09). Tre fatti meccanici dal golden: (1) G09 22/02, G10 05/03, G10 23/04 sono giorni dichiarati (8,S) che il golden tiene 100% Stage: la nostra consegna ci piazza 1h ordinaria = ordinary-on-stage-day, famiglia NUOVA (i gruppi class_days non hanno il confinamento H23/H24, scoped FCQuota: un giorno aula=lab=0 da foglio non vieta a tier hard le ordinarie); (2) G07 09/06: il foglio dice (5,S) ma il golden fa 5h ORDINARIE (Educazione Civica x3 T23 + Scienze x2 T25 slot 0-4): cura input golden-first modality S->A + notifica referente a posteriori (famiglia TASK-550); (3) G08 01/04: il giorno golden e' pieno (Stage 8-12 + RA 14:30-16:30 contigue = la quota 2+4 del foglio): il nostro difetto e' FORMA (2h sparse con buco 4h) non lunghezza, famiglia compattazione same-day, NON giorni-corti. CONSEGUENZA SCOPE M-BF: il golden pratica Figurino sparso (112 run 1h vs 9 run 2h+): lo scope isLab (Figurino fuori) e' golden-correct, domanda referente RITIRATA, il 0-match golden e' il self-gating voluto.
<!-- SECTION:DESCRIPTION:END -->
