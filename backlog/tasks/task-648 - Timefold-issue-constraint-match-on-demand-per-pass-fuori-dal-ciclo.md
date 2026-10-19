---
id: TASK-648
title: 'Timefold issue: constraint match on-demand per pass fuori dal ciclo'
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
Ogni nostro pass deterministico fuori dal ciclo (CSF, EDF, HRF, 638) ricostruisce un director con ConstraintMatchPolicy.ENABLED per leggere i match: oggi la policy e' per-fase intera (costo su tutto il solve se accesa). PROPOSTA upstream: API on-demand (calcolo match al volo su richiesta per un pass post-fase). Evidenza: 5+ consumer nel nostro repo, ognuno con il proprio snapshot director. Forma: issue/discussion, puo' unirsi alla discussione flagship (stesso tema: consumare le violazioni).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DIRETTIVA maintainer 23/09 sera (memoria open-core-posture-maintainer): issue upstream accantonata. Il nostro consumo di constraint match via impl funziona ed e' NOSTRO (la via del director freddo fuori dal loop, validata su rc-1); se un domani l'accesso venisse gated, la reimplementazione propria e' la via (TASK-653 registro). Niente azione ora.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
