---
id: TASK-209
title: >-
  Analyze hour discrepancies between DataGenerator and spalmone.csv for all
  student groups
status: Done
assignee: []
created_date: '2026-03-09 12:59'
updated_date: '2026-07-14 05:31'
labels:
  - claude-planned
  - data-verification
  - spalmone-csv
dependencies: []
documentation:
  - /scripts/data/spalmone.csv
  - /src/main/java/com/school/planner/bootstrap/DataGenerator.java
priority: high
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Analyze all student groups' hour discrepancies between DataGenerator and spalmone.csv.

**PHASE 1 COMPLETE**: Group-level analysis scripts created and executed.

**FINAL RESULTS** (G14 excluded per user request):
- **NO critical (>50%) discrepancies found** ✓
- Overall discrepancy: **-323 hours (-3.0%)**
- 12 of 13 groups at OK/MINOR status
- 1 warning group: G13 (-14.6%)

**KEY CORRECTIONS**:
1. G11 discrepancy is only **1 hour** (not 1,040 as initially reported)
2. Added 30+ subject name aliases for CSV-to-DataGenerator mapping
3. Excluded non-curriculum activities (azienda, stage, esame, comp)
4. **G14 excluded** (independent micro-course, manual scheduling)

**G13 DISCREPANCY EXPLAINED**:
- Missing "Servizi vendita (B)" course (100h) in CSV
- DataGenerator: 176h total (A: 76h + B: 100h)
- spalmone.csv: 76h total (only A course)
- **AWAITING USER DECISION**: Which is correct?

**SCRIPTS CREATED**:
- scripts/analyze-group-discrepancies.py — Group-level comparison (G14 excluded)
- scripts/g13-detailed-analysis.py — G13 detailed breakdown
- scripts/detailed-subject-breakdown.py — Per-subject analysis

**REPORTS**:
- claudedocs/task-209-final-report.md — Complete analysis summary
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Parse spalmone.csv and generate group-by-group hour totals
- [x] #2 Calculate DataGenerator totals for all groups
- [x] #3 Generate comprehensive discrepancy report with % difference
- [x] #4 Identify groups with >50% discrepancy for detailed analysis
- [x] #5 Create per-subject breakdown for major discrepancy groups
- [ ] #6 Update DataGenerator with missing/under-reported courses (AWAITING USER DECISION)
- [ ] #7 Verify solver can schedule all lessons without late-year gaps
- [ ] #8 Document all changes in MEMORY.md
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SUPERSEDED by TASK-337. Phase 1 analysis is already complete (artifacts: claudedocs/task-209-final-report.md, scripts/analyze-group-discrepancies.py, scripts/g13-detailed-analysis.py). The only remaining work — the USER DECISION on DataGenerator vs spalmone discrepancies (incl. G13 -14.6%) — is captured by TASK-337 (m-6 data decision gate).
<!-- SECTION:FINAL_SUMMARY:END -->
