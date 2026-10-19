---
id: TASK-628
title: >-
  TASK-628 - Banco varianza LS: p(0-hard) sulla stessa costruzione con solo la
  race diversa
status: Done
assignee: []
created_date: '2026-09-19 18:28'
labels:
  - claude-planned
  - 0-hard-reliability
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal finding chiave dell'analisi serie: la costruzione e' deterministica (seed=0, sempre identica), quindi la differenza -4 vs 0 nasce a valle. Il banco isola la race della LS multi-threaded come misurando: 8 resume-cycle dal checkpoint POST_CONSTRUCTION di integrato-5, ogni run con la catena repair completa. Predizione pre-registrata: p(0) 30-70% = race e' il collo; >70% = race non determinante; <30% = bacino condannato. Dato preliminare: run 1 = -4 (H32 x2 + H34 x1 + H7 x1), run 2 iter-1 a -4660 vs -4662 del run 1 (diff minime ma reali).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Banco varianza LS completato: N=8 run dalla stessa costruzione deterministica (checkpoint integrato-5 POST_CONSTRUCTION), catena repair completa + corner-b per run, p(0-hard) calcolato
- [ ] #2 #2 VERDICT contro i criteri pre-registrati (p(0) 30-70% = race confermata; >70% = race non determinante; <30% = bacino condannato) con distribuzione dei residui per famiglia
- [ ] #3 #3 Se la race e' confermata: proposta della leva (determinismo LS, GRASP mirato, o repair piu forte) con evidenza
- [ ] #4 #4 Se la race NON e' confermata: identificazione della vera fonte della variabilita' (fase, meccanismo)
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
