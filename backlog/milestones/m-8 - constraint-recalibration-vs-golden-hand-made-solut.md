---
id: m-8
title: "Constraint Recalibration vs Golden Hand-Made Solution"
---
## Description

Fase 1+2 della strategia autonoma (2026-07-17, referente in ferie): il golden è VALIDO, quindi (a) le ambiguità si risolvono adottando ciò che fa il golden, (b) ogni vincolo è tarato bene solo se il golden score pulito o con eccezioni documentate — TASK-355 (m-9) pinna questo criterio nei test.

ORDINE: Fase 0 = TASK-374 (default derivati dal golden per Q11-14/Q16). Fase 1 = TASK-371 (incarichi dal golden, risposta Q3), TASK-373 (stage su chiusure), poi il gate TASK-355 in m-9. Fase 2 (ricalibrazioni, tutte sbloccate) = TASK-348 (done), 349 (whitelist nota da Q18), 350, 365 (whitelist: laboratori+Informatica+Disegno, soft), 366 (done), 368 (choice-day dal docx), 369, 370, 372 (pomeriggi solo-stage, risposta Q17).

**DoD OBBLIGATORIA per ogni task** (anche nel config del backlog): /simplify sul diff + /code-review high (verifica Opus, fix dei confermati) + verifica finale con exit code dall'orchestratore. Nessun task è Done senza.

**Exit criteria:** golden audit ri-eseguito live con zero violazioni non spiegate su TUTTI i vincoli ricalibrati; TASK-355 verde in CI.
