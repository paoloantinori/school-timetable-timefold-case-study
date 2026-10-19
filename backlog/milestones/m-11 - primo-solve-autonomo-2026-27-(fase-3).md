---
id: m-11
title: "Primo Solve Autonomo 2026-27 (Fase 3)"
---
## Description

Fase 3 della strategia autonoma: il primo solve completo del 2026/27 e il confronto con l'orario umano. Prerequisiti: m-8 ricalibrazioni + TASK-355 verdi.

Pipeline: import-calendario pinna i laboratori MODA (+ eventuale re-import quando la scuola manda i fogli riorganizzati) → solve completo → TASK-363 benchmark solver-vs-umano su dati e vincoli identici (protocollo 2 run, experiment-log). DECISIONE TASK-351 (2026-07-17): NO-GO sul template-first (41.5% ricorrenza stretta per segmento, 72% tetto modale, ~16 griglie/gruppo — claudedocs/design/weekly-template-redesign-2026-07-17.md). Strategia: pipeline per-data attuale + pin calendario + TASK-375 (vincolo soft di stabilità settimanale). L'artefatto finale — il nostro orario affiancato a quello della scuola, vincolo per vincolo — è la demo per il rientro di referente.

**DoD OBBLIGATORIA per ogni task**: /simplify + /code-review high (verifica Opus) + verifica finale con exit code dall'orchestratore.

**Exit criteria:** un orario 2026/27 generato dal solver, feasibility 0hard sulle lezioni non pinnate, confronto per-vincolo col golden pubblicato in claudedocs.
