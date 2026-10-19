---
id: TASK-663
title: >-
  UX: guardie su Importa (sovrascrive l'orario) e Risolvi; Export/Import JSON
  fuori dalla barra quotidiana
status: Done
assignee: []
created_date: '2026-09-24 08:46'
labels:
  - claude-planned
  - ux
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding major della UX review 24/09: 'Importa' sovrascrive l'intero orario da file JSON SENZA conferma (handleImport UnifiedSolverControls.tsx:626-649, verificato: nessun confirm), col tooltip 'per test A/B' (intento dev) ma posizionato nella barra quotidiana accanto a Solve: un planner che legge 'Importa' pensando ad 'importare i dati della scuola' (come il CSV Import in Admin, che significa altro) cancella la consegna in un click. Backend: ImportService cancella anche le LabRotation (trappola documentata in CLAUDE.md). 'Risolvi' fire-and-forget senza conferma (handleSolve UnifiedSolverControls.tsx:491-506): i pin sopravvivono (hand-placed = intoccabile) ma ogni ora non pinnata accettata dal planner viene rimescolata da un run di mezz'ora. Contrastare con Reset che e' doppiamente confermato: il livello di guardia e' invertito rispetto al rischio. Storia: Export/Import JSON sono tool di campagna (A/B, checkpoint) nati per gli agent, non funzioni prodotto.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Import chiede conferma esplicita con spiegazione ('sostituira' TUTTO l'orario corrente') prima di api.importSchedule
- [x] #2 Risolvi chiede conferma quando esiste gia' una consegna (le ore non pinnate verranno rimescolate)
- [x] #3 Export/Import non stanno nella barra dei controlli quotidiana accanto a Solve: spostati in area separata/dev
- [x] #4 Il toltip/copy non dice 'per test A/B' su una superficie scuola
<!-- AC:END -->
