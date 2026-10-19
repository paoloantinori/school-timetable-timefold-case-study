---
id: TASK-448
title: >-
  Vincolo no-return del docente nella (gruppo, materia, giornata): chi cede il
  blocco non rientra (invariante golden verificato)
status: Done
assignee: []
created_date: '2026-07-28 20:11'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 271000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Ipotesi maintainer verificata da Fedora sul golden (sera 2026-07-28): nei 15 blocchi contigui same-subject multi-docente del golden la sequenza compressa e' SEMPRE A->B (un solo handoff), zero casi A..B..A; identico esito a livello di giornata intera (gruppo, materia, data). Anche il run gate-l5-m36 e' pulito (5 subject-day multi-docente, 0 ritorni): il vincolo e' un GUARD a delta zero sul comportamento attuale che cristallizza l'invariante. Direttiva maintainer: 'se e' vera, dobbiamo modellarla'. MODELLAZIONE: penalizza, dentro una (gruppo, materia, data), la coppia di lezioni dello STESSO docente tra le quali esiste (ifExists) una lezione di docente DIVERSO in slot strettamente intermedio. Golden-first: 0 violazioni nel golden -> candidabile hard; nessun repair necessario (niente da riparare oggi). Complementare a S16 (premio same-teacher adiacente) e M15 (contiguita' same-subject). Implementazione bundlata col treno fix room del 28/07 sera (demozione H16b + tag-capacity).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 L0 ConstraintVerifier: penalize su A-B-A, silence su A-B e su giorni mono-docente
- [ ] #2 GoldenSolutionRegressionTest resta verde (golden 0 violazioni)
- [ ] #3 Re-gate: 0 match del vincolo nel run finale
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
OBSOLETO/ASSORBITO: ratificato come H28 (golden-first guard a delta zero, TASK-448) il 2026-07-28, stringa "H28 - No teacher return within group subject day" verificata in SchoolScheduleConstraintProvider il 2026-08-14. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
