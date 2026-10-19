---
id: DRAFT-001
title: 'Investigation: Compaction requirements not met - Late finish analysis'
status: Draft
assignee: []
created_date: '2026-03-09 06:44'
updated_date: '2026-03-09 08:00'
labels:
  - investigation
  - compaction
  - constraint
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Solver produces 0hard solutions but "Compaction requirements not met" badge appears. Schedule ends June 10 instead of earlier target dates.

## Findings (2026-03-09) - UPDATED

### Root Cause #1: Lessons Generated Correctly ✅
- All curriculum hours ARE generated (Lessons + LabBlocks = Expected)
- Example: G09 has 666 hours (442 lessons + 224 LabBlock hours)

### Root Cause #2: Hours Distribution Problem ❌ **CORRECTED**
- **Earlier analysis was WRONG** - claimed G09 ended Feb 23
- **CORRECT**: G09 ends June 9 (last LabBlock), not Feb 23
- Actual issue: Groups finish LATE, not early
- Hours are front-loaded BEFORE stage period instead of spread evenly

### G09 Corrected Analysis (via Java API):
- Total: 666 hours
- Hours before stage: **586** (87%!)
- Hours during stage: 42
- Hours after stage: 38
- Actual end: June 9, 2026
- Ideal end: Feb 11, 2026
- **118 days LATE** (not early!)

### Root Cause #3: H11 Constraint Inactive ❌
- `StudentGroup.maxHoursPerDay = null` for all groups
- Constraint only applies when field is set (line 169-170 of constraint provider)
- Groups can have unlimited daily hours without penalty

### Root Cause #4: No Per-Student-Group Target End Date ❌
- M12/M13 penalize gaps but don't enforce completion dates
- No constraint says "group must finish by X date"
- Current feasibility check: `mediumScore >= -50000` (aggregate, not per-group)

## Verification Tool Available ✅

**NEW: Student Group Completion API** (implemented 2026-03-09)
```
GET /api/completion                      → All groups' stats
GET /api/completion/{groupName}          → Detailed stats
GET /api/completion/{groupName}/summary  → Lightweight summary
```

Provides verifiable calculation of:
- Actual vs ideal end dates
- Hours distribution (before/during/after stage)
- Days difference per group

**Java Implementation**: `StudentGroupCompletionService.java`
- Replaces error-prone Python scripts
- Type-safe, testable, accessible via REST

## Data

From laptop overnight run (2026-03-09):
- All groups missing target end dates by 20-120 days
- G01: Target May 27 → Actual Jun 10 (14 days late)
- G09: Target Feb 11 → Actual Jun 9 (**118 days late** - corrected)
- G12: Has very long stage period (special case)

## Next Steps

- **CRITICAL**: User needs to review corrected analysis
- Decide: Option A (H17 constraint) vs Option B (activate H11 + tune M12/M13)
- Use `/api/completion` endpoint to verify any fix attempts

## References

- Export: `/home/user/Downloads/0hard-schedule-export-2026-03-09T05_56_44.055Z.json`
- Progression: `/home/user/Downloads/phase-progression-2026-03-09T05_58_16.344Z.json`
- Constraint: `SchoolScheduleConstraintProvider.java` line 145-175 (H11)
- Badge logic: `ScheduleResource.java` line 178 (feasibility check)
- **NEW API**: `StudentGroupCompletionService.java` + `StudentGroupCompletionResource.java`
<!-- SECTION:DESCRIPTION:END -->
