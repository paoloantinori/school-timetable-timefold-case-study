---
id: TASK-439
title: >-
  C3 Eccezioni per-gruppo come dati (SPLIT: seeding subito, solver-facing a
  model-freeze)
status: To Do
assignee: []
created_date: '2026-07-28 08:23'
labels:
  - claude-planned
milestone: m-13
dependencies: []
ordinal: 262000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Audit C3. Meta' SEEDING (colonne+admin gia' esistenti): MAX_HOURS_PER_DAY, NO_PROF_DURING_STAGE, GROUP_CALENDAR_PATTERNS->group_calendar.csv, M5_TARGET_GROUPS, EXCLUDED_ROOMS, FC_QUOTA_GROUP_NAMES - fattibile in campagna. Meta' SOLVER-FACING: isG14->flag exemptFromDayFill, M29_RESTRICTED->ProfessionalizzantiDay (entita' e admin GIA' esistenti e inutilizzati), QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP->join table - SOLO a model-freeze.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
