---
id: TASK-618
title: >-
  TASK-618 - Timefold neighborhoods API alla RC: oracolo per-mossa in
  passWithOracle + famiglie composite come LS neighborhood (riapre TASK-610)
status: To Do
assignee: []
created_date: '2026-09-15 20:55'
labels: []
dependencies: []
references:
  - claudedocs/spalmone-2026-27/task610-results/VERDETTO-misura-2026-09-15.md
  - claudedocs/spalmone-2026-27/task610-results/api-moves-doc.md
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla spike TASK-610 (283dbe4f su wt/610-moves-ls, misure VERDICT-misura-2026-09-15.md + doc api-moves-doc.md): la preview Move/Neighborhood API (PR 2610, nel main dopo 2.6.0, attesa in una release candidate) ha due integrazioni candidate misurate. (1) ORACOLO PER-MOSSA: InnerScoreDirector.executeTemporaryMove valuta compositi con scoring incrementale + undo automatico a ~54us/mossa (15.726 compositi esaustivi in 849ms): passWithOracle oggi paga riconteggi pieni, con l'API diventa per-mossa quasi gratis (utile a CSF/EDF/HoleRelay/le leve). (2) NEIGHBORHOOD LS DELLE FAMIGLIE COMPOSITE: dare la catena teacher-aware / compact-swap / import cross-data (viva su A3) come mosse campionabili in B-TIMESLOT/B-ROOM. ATTENDENDO CON ASPETTATIVE GIUSTE: la misura esaustiva dice che NESSUNA mossa singola migliora l'hard sul bacino -3 (0/15.726): il valore LS e' su medium e basin-escape, non su hard diretti. NON adottare prima: l'API e' @Preview, production e' pinned 2.3.0, il 999-SNAPSHOT esiste solo nel ~/.m2 del laptop (incidente pom flip 3507bd41->e07f6cc8). TRIGGER: la RC/stable che porta neighborhoods. Break 2.3.0->main censiti in api-moves-doc.md (SwapMove package, calculateScore->InnerScore, overloads PhaseCommandContext). La spike resta il riferimento con le misure.
<!-- SECTION:DESCRIPTION:END -->
