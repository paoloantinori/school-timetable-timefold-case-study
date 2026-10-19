---
id: TASK-598
title: >-
  Statics-pollution fra GoldenSolutionRegressionTest e
  SchoolScheduleConstraintProviderTest nello stesso JVM (M20 atteso 1000 /
  effettivo 0)
status: Done
assignee: []
created_date: '2026-09-10 23:03'
updated_date: '2026-09-10 23:54'
labels:
  - test-hygiene
  - statics-pollution
  - code-review-finding
dependencies: []
references:
  - TASK-595
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding dalla code-review TASK-595 (11/09, agente verificatore): eseguendo `GoldenSolutionRegressionTest` e `SchoolScheduleConstraintProviderTest` NELLA STESSA JVM di surefire, il metodo `horizontallyCompact_penalized_calendarCappedQuota_shortOfCalendarQuota` (M20, "Expected 1000 / Actual 0") fallisce; il metodo passa da solo. RIPRODOTTO IDENTICO su worktree PULITO senza il diff TASK-595: inquinamento di statiche condivise, NON introdotto da TASK-595.

CAUSA RADICE (identificata e misurata 11/09 notte): l'app boot di un @QuarkusTest precedente pubblica le statiche solver (ratio supply-cap TASK-461 per TUTTI i gruppi reali + targets pool-cap TASK-497 col flag armed catturato). Il test sintetico M20 usa group id 4L "G03" (stesso id del gruppo reale): la sua data sintetica 2025-10-10 senza riga quota cade nel fallback calendar-capped (5), ma capSupply scala 5 per il RATIO REALE del gruppo 4 sotto le 4 ore piazzate -> M20 fuori scope -> penalta 0. Il @BeforeEach del test puliva solo le statiche dei pin (TASK-424); quote/regole si pulivano solo DOPO.

CURA: clearSolverStatics() condivisa chiamata PRIMA e DOPO ogni test (precedente TASK-424 esteso all'intero set), inclusa clearM20CapPoolStatics() (targets pool-cap + flag armed + null della ratio map). Il test e' ora ORDINE-INDIPENDENTE: piu' forte di aggiungere la combo a CI (verificata manualmente 2x verde: 316/316 combo, 315/315 solo).

Scope residuo (futuro, non bloccante): la classe di rischio generale (qualsiasi plain test che legge statiche solver dopo un boot Quarkus) resta; oggi nessun'altra vittima in suite (1303/0).
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso con commit fa3d014f (pushato). Causa radice misurata: l'app boot di un @QuarkusTest precedente pubblica ratio supply-cap TASK-461 + targets pool-cap TASK-497 (flag armed catturato); il test sintetico M20 (group id 4L = id del 'G03' reale) eredita il ratio reale, la sua data 2025-10-10 fallback calendar-capped 5 viene scalata sotto le 4 ore piazzate, M20 fuori scope = 0. Cura: clearSolverStatics() condivisa PRIMA e DOPO ogni test (precedente TASK-424 esteso), con clearM20CapPoolStatics(). Ordine-indipendenza verificata: combo 316/316, solo 315/315, suite completa 1303/0 EXIT 0. gate-exempt trivial (solo test lifecycle, zero codice produzione).
<!-- SECTION:FINAL_SUMMARY:END -->
