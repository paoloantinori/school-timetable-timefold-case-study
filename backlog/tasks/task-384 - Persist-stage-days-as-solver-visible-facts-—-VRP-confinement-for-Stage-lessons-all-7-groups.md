---
id: TASK-384
title: >-
  Persist stage days as solver-visible facts — VRP confinement for Stage lessons
  (all 7 groups)
status: Done
assignee: []
created_date: '2026-07-20 10:07'
updated_date: '2026-07-21 16:49'
labels:
  - claude-planned
dependencies: []
ordinal: 208000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Follow-up of TASK-381 stage-on-the-grid (code-review finding, 2026-07-20). Today the 5 class-days groups' stage days (2/G11, G10, G12, G09) exist only as CSV-shape knowledge inside CalendarioImportService.buildStageAsks (request-scoped) - the solver never sees them. Consequence: unpinned Stage stubs are unconfined (no constraint anchors pseudo lessons; only workflow discipline - import-calendario before solve, now baked into the full-test skill - prevents scattering). FIX SHAPE (per the altitude review): extend DailyActivityQuota (or a sibling persisted fact) to carry all 7 stage groups' stage days; then (a) a VRP filter confining unpinned Stage lessons to their group's stage dates (H4.1 pattern, pseudo-aware - the pseudo exemption in the H4.1 blackout filter landed with TASK-381's review) and (b) an H-tier safety-net twin (H20/H23 pattern) fall out naturally from existing machinery. CAUTION: widening DailyActivityQuota rows to non-FC groups makes them 'quota groups' for H23/H24/M30/M31 and the aula/lab VRP - either use a sibling fact/table for stage-only days, or extend the quota semantics deliberately with golden-derived bound sets per group (that is L2 territory). Coordinate with TASK-381 L2.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Confinamento VRP delle lezioni Stage + safety-net hard H25. TASK-385 aveva gia' persistito il fact (righe DailyActivityQuota stageHours>0 per tutti i 7 gruppi con stage), quindi puro add VRP+vincolo: (1) filtro in Lesson.getAvailableTimeslots che confina le lezioni pseudo Stage NON pinnate alle sole date con stageHours>0 del loro gruppo; (2) H25 hard pinned-exempt come rete di sicurezza (pattern H23). Predicati condivisi isStageConfined/isStageDate (single source of truth VRP+vincolo). Chiude il rischio operativo 'solve-prima-di-import sparpaglia lo stage': ora e' un guard meccanizzato, non solo disciplina di workflow. Golden 0/0 (pinned-exempt), tutti i pin esistenti invariati. 688/688 test exit 0, typecheck/build 0. DoD: /simplify + /code-review high (agente) + /code-review high indipendente dell'orchestratore (nessun bug di correttezza, 1 rilievo di copertura basso gia' mitigato dal test VRP sintetico) + verifica exit-code.
<!-- SECTION:FINAL_SUMMARY:END -->
