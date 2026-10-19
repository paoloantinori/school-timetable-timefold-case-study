---
id: TASK-664
title: >-
  UX: esperienza di caricamento - skeleton, errore visibile, dedup dei fetch
  analysis/lessons
status: Done
assignee: []
created_date: '2026-09-24 08:46'
labels:
  - claude-planned
  - ux
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding major della UX review 24/09, MISURATO sul sito pubblico (performance entries): GET /api/schedule = 27,4MB decodificati, 6,4s; GET /api/schedule/analysis/lessons chiamato TRE volte al load (12s, 11s, 15,7s; 260KB l'uno); /api/schedule/analysis due volte. Il primo goto del browser e' andato in timeout a 30s: l'esperienza d'ingresso del planner e' ~30s di attesa. A questo si somma il rendering gated su schedule truthy (ScheduleTimeline.tsx:520) senza skeleton/spinner (grep: zero match), e il fallimento fetch che finisce solo in console (ScheduleTimeline.tsx:243-245): pagina bianca indefinita. Nota: il fix @JsonIgnore (5185e055) ha gia' portato il payload da 1,2GB a ~27MB; il residuo e' dedup dei fetch, skeleton e superficie errore.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Spinner/skeleton visibile durante il primo caricamento della schedule (oggi pagina muta)
- [x] #2 Un errore di fetch della schedule e' visibile in pagina con azione 'riprova', non solo in console
- [x] #3 All'apertura della pagina l'endpoint analysis/lessons non viene chiamato piu' di una volta
- [ ] #4 Misura prima/dopo su caricamento a freddo del sito pubblico (timing performance entries)
<!-- AC:END -->
