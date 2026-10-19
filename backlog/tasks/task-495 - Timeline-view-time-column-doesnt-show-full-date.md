---
id: TASK-495
title: Timeline view time column doesn't show full date
status: Done
assignee:
  - claude
created_date: '2026-03-13 18:05'
updated_date: '2026-03-13 18:07'
labels:
  - ui
  - timeline
  - frontend
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The time column in the timeline view only shows partial date information, making it difficult to identify the exact date at a glance. Need to investigate and improve the date display format.

**Reference Screenshot:**
![Timeline view showing incomplete date display](https://maas-log-prod.cn-wlcb.ufileos.com/anthropic/f38b0706-a6a0-4069-b92f-f59c49d3f256/eaf98589ba294427934be6e8ebdc820a.png?UCloudPublicKey=TOKEN_e15ba47a-d098-4fbd-9afc-a0dcf0e4e621&Expires=1773426944&Signature=vaSLvxqe2MiPB+pPFTlgFTQYJI4=)

**Issue:** The time column shows abbreviated dates (e.g., day of week only) without full context like month/date.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Time column displays full date (day + date + month) visible without scrolling
- [ ] #2 Visual comparison with screenshot confirms improved date visibility
- [ ] #3 No layout regression on mobile/desktop viewports
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
