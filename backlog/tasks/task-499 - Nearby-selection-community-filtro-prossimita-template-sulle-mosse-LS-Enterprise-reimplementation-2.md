---
id: TASK-499
title: >-
  Nearby selection community - filtro prossimita' template sulle mosse LS
  (Enterprise reimplementation #2)
status: Done
assignee: []
created_date: '2026-08-15 11:37'
labels:
  - claude-planned
  - gated-maintainer
  - enterprise-reimplementation
milestone: m-13
dependencies: []
priority: medium
type: enhancement
ordinal: 311000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Filone Enterprise reimplementation #2 (mandato maintainer 2026-08-15 pomeriggio). Problema: il ChangeMoveSelector genera una mossa per ogni lezione verso OGNI timeslot (1368): la maggior parte e' fuori template del corso, viene VALUTATA e poi scartata da M32: costo di valutazione pagato prima dello scarto. Il LS e' il 99% del wall. DESIGN: filtro a gradini di prossimita' al template (grado 0 = cella template M32, 1 = stesso weekday, 2 = stesso slot altro weekday, 3 = resto filtrato con p_keep 0.20 probabilistico) via SelectionFilter community sul ChangeMoveSelector del path staged. Riferimento Enterprise: OptaPlanner 8.x nearby selection (community pre-fork, divenuta Enterprise nel fork con ordering biased parabolico: la nostra e' una forma filter, dichiarato onestamente). Rischio: mosse di fuga del bad basin: mitigato da p_keep e gate A/B.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Design doc claudedocs/design/nearby-selection-community.md revisionato (fatto: filter a gradini template-proximity, non ordering biased)
- [ ] #2 Prototipo flag planner.ls.nearby-template-filter.enabled DEFAULT OFF + knob keep-probability, wiring solo path staged
- [ ] #3 Filter usa la STESSA isOnTemplateCell di M32 (no divergenza di definizione)
- [ ] #4 A/B appaiato golden default MT-LS ON: hard 0 entrambi, KPI in banda, wall LS + move evaluation speed misurati
- [ ] #5 VERDICT in task499-results/ con stima frazione grado-3 e rischio mosse di fuga discusso
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
