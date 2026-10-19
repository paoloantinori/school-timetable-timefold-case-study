---
id: TASK-531
title: >-
  G12 giorni corti: giorni <4h = 0 (golden-compatible) + diagnosi stamp
  venerdi'
status: To Do
assignee: []
created_date: '2026-08-19 14:26'
updated_date: '2026-08-19 14:32'
labels:
  - solver
  - bug
  - model
dependencies: []
priority: high
ordinal: 312000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Distribuzione G12 giorni corti (run H31): 7 giorni <=3h di cui 6 VENERDI' (02/10, 09/10, 23/04 con 2h, 30/04, 07/05, 14/05, 21/05); 22 giorni a 4h vs golden 11; 99 a 5h vs golden 120. Golden G12: minimo 4h/giorno SEMPRE, zero venerdi' sotto 4, venerdi' = 8x4h + 4x5h + 18x7h. REGOLA (maintainer 19/08): il target e' il CONTO DEL GOLDEN (117 pieni + 11 corti sparsi come li sparge lui), non il minimo aritmetico. FASE MECCANICA PRIMA: vincolo/diagnosi che i giorni <4h siano = 0 (golden-compatible, nel golden non esistono). Poi misura dove nascono (template stamp del WeeklyTemplateBuilder sul venerdi', coda aprile-maggio).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 14:32
---
TRACCIAMENTO (Fedora 19/08 sera): questo task e' SUPERSEDATO da TASK-532 (creato da Fedora con la spec definitiva del vincolo quota-fill). Nessun lavoro collegato: la diagnosi e i numeri sono gia' nel TASK-527 e nella spec del 532. Archiviato per evitare doppio tracciamento.
---
<!-- COMMENTS:END -->
