---
id: TASK-444
title: >-
  C8 Script KPI parametrici (anno/ramp/chiusure/paths via CLI; via la terza
  copia delle chiusure)
status: Done
assignee: []
created_date: '2026-07-28 08:23'
updated_date: '2026-08-04 05:43'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: low
ordinal: 267000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
C8+H: rendere parametrici gli KPI script (scripts/analyze/) via CLI: --year, --ramp, --closures-path, --lessons-path. Eliminare la terza copia hardcoded delle chiusure (golden-vs-run-viz + shared.py); fixare letterali anno 2025/26 e denominatore 1176; via i default /home/user/Downloads.

BACKWARD-COMPATIBILE: i default CLI devono riprodurre il comportamento attuale (non rompere i run esistenti). Read-only tooling (non tocca il solver). Prepara l'anno-senza-golden (direttiva solo-input, TASK-441 policy).

DoD: /simplify + /code-review high + test.sh EXIT=0, commit+push. NESSUNA adozione.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
C8+H DONE (2026-08-04). KPI script (scripts/analyze/) parametrici via CLI/env: --year, --ramp, --closures-path, --lessons-path, --year-start, --year-end, --data-dir (+ PLANNER_KPI_* env). Backward-compatible (default = 2026/27).

Cosa fatto:
- shared.py: config layer (YEAR/DATA_DIR/LESSONS_CSV/CLOSURES_CSV/SCHOOL_YEAR_START/END/RAMP_DATE env-driven, default=2026/27), load_closures(), KpiConfig, add_kpi_args(), resolve_config(), data_dir_for_year() (fonte unica della derivazione data-dir).
- closures.csv: fix typo 2026-01-22 -> 2027-01-22 (TASK-408 aveva fissato Java ma non il CSV). Ora 29 date = DataGenerator.CLOSURES_2026_27 autoritativo.
- 5 script battery parametrici: day-start-kpi, daily-fill, hole-topology, day-start-anatomy, golden-vs-run-viz (quest'ultimo: eliminata inline 28-date closures copy -> load_closures fonte unica).
- cleanup: golden-tag-evidence (home paths -> ROOT-relative + shared.LESSONS_CSV); spalmone2027-extract + calendario2027-extract (via ~/Downloads default -> argv required); capacity-check (1176 -> env PLANNER_CAPACITY_TIMESLOTS).

DoD: /simplify (3 agent; applicato F1 alias CEREMONIAL_LATE_DAY, F2+F3 helper data_dir_for_year, A1 docstring onesto, A3 latent-trap default-param; saltati onesti E1/F4/F5/F6) + /code-review high (skeptic Opus: 6/6 claim CONFIRMED indipendentemente) + test.sh EXIT=0 (988/0/0).

Backward-compat verificata byte-identical (day-start/daily-fill/hole-topology vs saved baselines; anatomy KPI-body identico). UNICA variazione di comportamento: golden-vs-run-viz school_days 172->171 (ora esclude il venerdi chiuso 2027-01-22), che allinea al conteggio autoritato documentato in CLAUDE.md. Correzione, documentata.

DEFER (follow-up onesti, non creati): feasibility-audit + sparse-calendar (letterali 2025/26 di intento ambiguo, non-battery); sources.py workbook (4a copia closures, gia corretta a 29, sottosistema separato); 5 script untouched (teacher-tier YEAR_START ecc., fuori scope - prendono file path non year config); docstring daily-fill rebinding module-scope (F4, mantenibilita'). NESSUNA adozione (tooling read-only, non tocca il solver).
<!-- SECTION:FINAL_SUMMARY:END -->
