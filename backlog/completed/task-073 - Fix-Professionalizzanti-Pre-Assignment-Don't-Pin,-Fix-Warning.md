---
id: task-073
title: 'Fix Professionalizzanti Pre-Assignment: Don''t Pin, Fix Warning'
status: Done
assignee: []
created_date: '2026-02-22 18:14'
updated_date: '2026-02-22 18:24'
labels:
  - bug
  - pre-assignment
  - solver
  - professionalizzanti
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The ProfessionalizzantiPreAssigner incorrectly:
1. **Pins** professionalizzanti lessons to stage days (solver can't move them)
2. **Warns** "insufficient stage day slots" when professionalizzanti > stage days

This is backwards - professionalizzanti CAN go anywhere (H4 exempts them), so having more professionalizzanti than stage slots is normal and expected.

## Correct Behavior

- Professionalizzanti CAN be on stage days (students on stage earn equivalent credits)
- Professionalizzanti CAN ALSO be on non-stage days (no restriction)
- Pre-assigner should place them on stage days but NOT pin them
- Warning should be informational, not an error

## Solution

1. Change `pinned = true` to `pinned = false` in ProfessionalizzantiPreAssigner
2. Fix warning message to: "Placed X on stage days, Y available for solver to place elsewhere"

## Why This Works

- Pre-assigner gives solver a good starting point (professionalizzanti on stage days)
- Solver can rearrange if a better arrangement exists
- Stage days are naturally attractive (fundamentals blocked there by H4)
- Clear intent documented in code
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Professionalizzanti lessons are assigned to timeslots but NOT pinned (pinned=false)
- [x] #2 Warning message changed to informational format
- [x] #3 Pre-assignment places professionalizzanti on stage days as starting point
- [x] #4 Solver can freely move professionalizzanti lessons
- [x] #5 No 'insufficient stage day slots' error for having more professionalizzanti than stage slots
<!-- AC:END -->
