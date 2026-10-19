---
id: TASK-128
title: Enhance TwoPhaseReport with entity ID lists
status: Done
assignee: []
created_date: '2026-02-26 15:59'
updated_date: '2026-03-03 07:00'
labels:
  - enhancement
  - api
  - reporting
dependencies: []
ordinal: 54000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The TwoPhaseReport from the two-phase pre-assignment endpoint includes counts (entitiesExtracted, validAssignments, violationCount) and violation types, but not the actual list of entity IDs that were:
- Successfully assigned and pinned
- Failed validation (not pinned)

This makes it difficult for API consumers to know exactly which entities were affected.

## Solution

Add two new fields to TwoPhaseReport:
- `assignedEntityIds`: List of entity IDs that were successfully pinned
- `failedEntityIds`: List of entity IDs that had violations and were not pinned

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given two-phase pre-assignment completes, when the response is returned, then `assignedEntityIds` contains all successfully pinned LabBlock and Lesson IDs
- [ ] #2 Given two-phase pre-assignment has violations, when the response is returned, then `failedEntityIds` contains all entity IDs that had hard violations
- [ ] #3 Given dry-run mode, when the response is returned, then the same ID lists are included (even though nothing was persisted)

## Priority

Low - Nice-to-have enhancement, not blocking any functionality
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
