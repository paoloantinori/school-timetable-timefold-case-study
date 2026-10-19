---
id: TASK-647
title: >-
  Timefold issue: stabilita' API preview per move filter (churn
  SelectorBasedSwapMove)
status: Done
assignee: []
created_date: '2026-09-23 04:53'
updated_date: '2026-09-23 09:22'
labels:
  - upstream
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Churn subito nell'upgrade rc-1: QuotaPreservingSwapFilter migrata da preview.api.move.builtin.SwapMove a impl.heuristic.selector.move.generic.SelectorBasedSwapMove perche' la preview API e' cambiata. I FILTRI di mossa che vivono nel config non hanno interfaccia stabile. PROPOSTA upstream: interfaccia stabile per move filter (o storia di compatibilita' della preview API per i filtri). Evidenza: il nostro diff di migrazione + il fatto che il filtro deve vedere tipo concreto per leggere le proprieta'. Forma: issue con il caso d'uso.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DIRETTIVA maintainer 23/09 sera (memoria open-core-posture-maintainer): issue upstream accantonata. Azione locale se il churn torna: isolare l'uso della preview API dietro un adapter nostro (come sopra per 646). L'evidenza (la migrazione SelectorBasedSwapMove) resta nel task come documentazione del costo.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
