---
id: TASK-674
title: >-
  Export bloat 200x: il writer serializza la cache VRP per-lezione
  (availableTimeslotsFresh, 500KB→111MB)
status: Done
assignee: []
created_date: '2026-09-28 10:25'
labels: []
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Scoperto nel recupero TASK-672 (28/09): ogni export generato dal ~23/09 in poi serializza per OGNI lezione l'intera cache VRP availableTimeslotsFresh (~100KB/lezione x 12551 = 1.23GB raw, 500KB -> 111MB gzippato; h32b-run1 del 20/08 con lo stesso numero di lezioni pesa 546KB). COSTI MISURATI: export/import 200x piu' lenti e pesanti (capture dirs su disco gonfie, il push GitHub del checkpoint e' stato RIFIUTATO dal limite 100MB), e il workaround e' stato strippare a mano il checkpoint integrato-7 (sha256 in TASK-672). CAUSA: la cache (campo calcolato per il value-range filtering, presumibilmente aggiunta a Lesson tra il 20/08 e il 23/09 per il VRP fresh) e' vista dal serializzatore Jackson dell'export. CURA CANDIDATA: escludere il campo dalla serializzazione (annotazione sul getter/campo o mixin nel writer), tenendo presente che parseExport NON lo legge (il fixture lo ricostruisce via AvailabilityExpansionService) e che anche l'IMPORT di produzione lo ignora: escluderlo e' compatibile con tutti i consumer noti da verificare con grep (ImportService, SnapshotFixture, eventuali driver esterni). Verifica: export di un profilo vivo prima/dopo (dimensione + round-trip import identico), suite, e pin GoldenSolutionRegressionTest. BANCO: round-trip import/export su un profilo di test. NOTA: i 5 export tracked ~88MB (task612/613 ab-gate) sono tutti post-bloat: dopo la cura valutare se riconvertirli in forma stripped (guadagno repo ~440MB, stessa prassi del checkpoint 672).
<!-- SECTION:DESCRIPTION:END -->
