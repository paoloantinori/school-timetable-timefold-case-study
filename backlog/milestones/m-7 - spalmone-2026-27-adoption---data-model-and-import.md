---
id: m-7
title: "Spalmone 2026-27 Adoption - Data Model and Import"
---
## Description

Adopt the real 2026/27 spalmone (received 2026-07-16) as the single source of truth. USER DECISION 2026-07-16: this file COMPLETELY OVERRIDES all previous data - DataGenerator edits are authorized for this purpose. Foundation: 8-slot days with lunch break, per-group slot-level calendars, new teachers/courses/stage windows, structured teacher availability, and the hand-made golden solution imported as a reference schedule. Analysis: claudedocs/spalmone-2026-27/analysis.md; extractor: scripts/analyze/spalmone2027-extract.py.

Tasks: TASK-341 (8-slot timeslot model), TASK-342 (per-group calendar), TASK-343 (importer/seed replacement), TASK-344 (Stage/Esame/RA modeling decision), TASK-345 (recurring availability + Vincoli Docenti), TASK-346 (golden solution import), TASK-352 (school open-questions collector), TASK-364 (Calendario-scolastico importer - the school's pre-set calendar document becomes the standard pre-assignment input, reading key in claudedocs/spalmone-2026-27/calendario-scolastico-format.md).

**Exit criteria:** a fresh profile seeds the complete 2026/27 dataset; the golden human timetable loads and renders; every non-modeled constraint from the sheets is either structured or on the school-questions list.
