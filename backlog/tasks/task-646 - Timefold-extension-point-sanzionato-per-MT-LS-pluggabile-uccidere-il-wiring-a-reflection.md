---
id: TASK-646
title: >-
  Timefold: extension point sanzionato per MT-LS pluggabile (uccidere il wiring
  a reflection)
status: To Do
assignee: []
created_date: '2026-09-23 04:52'
updated_date: '2026-09-23 09:22'
labels:
  - upstream
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Il dolore ricorrente a ogni upgrade (2.3.0 -> rc-1 riverificato a mano): il decider MT-LS e' wired via REFLECTION negli internals Timefold (pinnato, fragile, javadoc del wiring documentato come tale dalla ratifica TASK-480). PROPOSTA upstream: extension point sanzionato per LS multi-thread con decider pluggabile (o esposizione delle internal API che usiamo: move thread lifecycle, barrier contract). Questo e' il pezzo che piu' ci espone a fork-risk. Forma: issue/discussion prima di PR (scelta API loro). Evidenza: la nostra impl funziona da agosto (A/B -25% wall) ma costa riverifica manuale a ogni release.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DIRETTIVA maintainer 23/09 sera (memoria open-core-posture-maintainer): non si propone piu' come issue upstream. La sostanza diventa azione locale: un nostro layer/adapter che isoli il wiring a reflection del decider (un solo punto da toccare a ogni upgrade, test di fumo automatico), cosi' il costo della fragilita' si paga una volta e resta nostro. Priorita' legata al 2.7.0 finale (29/09).
<!-- SECTION:NOTES:END -->
