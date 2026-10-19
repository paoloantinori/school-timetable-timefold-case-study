---
id: TASK-119
title: Update phase1a scripts to check status and handle async
status: Done
assignee: []
created_date: '2026-02-25 16:01'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - phase1a
  - scripts
dependencies: []
priority: high
ordinal: 121000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
The `phase1a-run.sh` and `phase1a-monitor.sh` scripts don't:
1. Check status before starting (can start duplicate processes)
2. Handle async endpoint responses properly
3. Find logs correctly (rely on hardcoded /tmp/planner.log)

## Solution
- `phase1a-run.sh`: Check status first, refuse if already running
- `phase1a-run.sh`: Handle 202 response from async endpoint
- `phase1a-monitor.sh`: Find actual log location (check running process)
- Add `phase1a-status.sh` helper to check current state

## Files to modify
- `scripts/phase1a-run.sh`
- `scripts/phase1a-monitor.sh`
- `scripts/api.sh` (add status parsing for phase1a/phase1b)

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 `phase1a-run.sh` checks status first, exits with error if already running
- [x] #2 `phase1a-run.sh` handles 202 response correctly
- [x] #3 `phase1a-monitor.sh` finds actual log file from running process
- [x] #4 Scripts work with both old sync and new async endpoints
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
