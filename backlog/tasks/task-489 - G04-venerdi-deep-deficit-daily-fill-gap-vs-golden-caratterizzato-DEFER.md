---
id: TASK-489
title: 'G04 venerdi'' deep-deficit daily-fill gap vs golden (caratterizzato, DEFER)'
status: Done
assignee: []
created_date: '2026-08-04 04:39'
updated_date: '2026-08-04 04:39'
labels:
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
G04 ha deficit profondo di venerdi' (18 giorni attesi 2h, solo 5 ottenuti). Caratterizzato da zai (commit 40ec8a58): run 3.59 vs golden 5.17 (quota 5). DEFER: e' capacity-bound (venerdi' ha meno slot attesi), non un difetto del solver.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 misura daily-fill del venerdi' G04 (run vs golden)
- [ ] #2 #2 caratterizza: pattern (tutti i venerdi') o outlier, capacity-bound o difetto
- [ ] #3 #3 VERDICT con raccomandanda (curare/ignorare/defer)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Caratterizzato da zai (commit 40ec8a58): run 3.59 vs golden 5.17 (quota 5). DEFER: e' capacity-bound (venerdi' ha meno slot attesi), non un difetto del solver. Nessun intervento warranted al momento.
<!-- SECTION:FINAL_SUMMARY:END -->
