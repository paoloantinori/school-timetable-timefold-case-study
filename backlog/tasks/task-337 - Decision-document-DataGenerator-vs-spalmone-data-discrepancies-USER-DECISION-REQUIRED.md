---
id: TASK-337
title: >-
  Decision document: DataGenerator vs spalmone data discrepancies (USER DECISION
  REQUIRED)
status: Done
assignee: []
created_date: '2026-07-12 17:48'
updated_date: '2026-07-16 09:41'
labels:
  - claude-planned
  - analysis
  - user-decision
milestone: m-6
dependencies:
  - TASK-321
references:
  - .claude/rules/datagenerator-protection.md
  - claudedocs/insights/5hour-day-groups.md
  - scripts/analyze/verify-datagenerator.py
priority: high
ordinal: 161000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Known data discrepancies can make the whole problem infeasible regardless of solver quality: T28 G10 is documented IMPOSSIBLE (11 blocks needed, 9 slots available - .claude/rules/datagenerator-protection.md); G09 runs +19h/8.8 percent over spalmone; claudedocs/insights/5hour-day-groups.md contains an UNRESOLVED contradiction (analysis says G08 and G13 are the true maxHoursPerDay=5 groups, but implementation notes claim G06 and G10 were set). Absorbs TASK-209 (hour discrepancy analysis) and TASK-210 (G13 Servizi vendita hours). HARD RULE: bootstrap/DataGenerator.java is protected - this task produces a DECISION DOCUMENT for the user, it does NOT modify data.

WHAT:
1. Re-verify current state mechanically: run python3 scripts/analyze/verify-datagenerator.py and the spalmone comparison scripts (scripts/analyze/spalmone-report.py, detailed-subject-breakdown.py); do not trust the March docs - regenerate the numbers.
2. Cross-check against the TASK-321 feasibility audit output.
3. Produce claudedocs/research/data-decision-document-YYYYMMDD.md: one section per discrepancy with (a) current DataGenerator value, (b) spalmone value, (c) feasibility impact (blocks 0hard? by how many hours?), (d) options (keep / align to spalmone / needs school confirmation) with a recommendation each.
4. The maxHoursPerDay contradiction must be settled by reading the CURRENT DataGenerator values and the spalmone distribution, stating which groups are actually configured vs which should be.
5. Present the document to the user and STOP - applying any change to DataGenerator happens only after explicit user approval, as a separate follow-up.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All numbers regenerated mechanically from current code and DB, not copied from March docs
- [ ] #2 Decision document exists with per-discrepancy feasibility impact and recommendation
- [ ] #3 maxHoursPerDay contradiction resolved with evidence
- [ ] #4 DataGenerator.java NOT modified
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERSEDED 2026-07-16: the school delivered the definitive 2026/27 spalmone xlsx and the user decided it COMPLETELY OVERRIDES all previous data. The DataGenerator-vs-old-spalmone discrepancy decision is moot: neither side survives. Replacement work tracked in milestone m-7 (TASK-341..346), analysis in claudedocs/spalmone-2026-27/analysis.md.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-14 14:57
---
USER DECISION (2026-07-14, via feasibility audit TASK-321 + claudedocs/research/feasibility-audit-2026-07-14.md): ACCEPT the ~33h unassigned floor for G04 (1037h vs 1004-slot capacity). The 'Larsa' 47h course is real - it appears 47x in spalmone.csv, is taught by T15, is non-lab, and exists ONLY for G04 (alongside 'Recuperi e approfondimenti' 50h) - so G04 is genuinely over-allocated vs its 990h peers, not a typo. DO NOT modify DataGenerator (protected). Flag the G04 over-allocation to the school separately. Solver work proceeds targeting 0 conflict-violations (H1-H3, H5-H7, H11) + unassigned <= ~33. NOTE: the baseline's -115hard is M22 calendar-compactness (-85) + H4.3/H4 stage (-30), NOT resource conflicts - so the baseline may already satisfy the 0-conflict part of the criterion; verify against the full constraint breakdown. Broader DG-vs-spalmone discrepancies (G13 -14.6% etc.; TASK-209 found -3% overall) remain non-blocking.
---
<!-- COMMENTS:END -->
