---
id: DRAFT-002
title: >-
  Feature: Per-group setting to allow professionalizzanti courses during stage
  periods
status: Draft
assignee: []
created_date: '2026-03-18 13:41'
labels:
  - feature
  - stage-blackout
  - H4
  - admin-ui
  - database
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**User Story:**
As a school administrator, I want to control whether professionalizzanti courses can be scheduled during stage periods for each student group, so that I can match the school's scheduling policies for different group types.

**Problem:**
Currently, H4 (Stage blackout) allows all professionalizzanti courses during stage periods unconditionally. This is incorrect for groups like G09 where professionalizzanti courses should be blocked during stage. The decision should be configurable per group.

**Solution:**
Add a per-group configuration field `allowProfessionalizzantiDuringStage` that controls whether H4 applies to professionalizzanti courses for that group.

**Implementation Requirements:**

1. **Database Schema:**
   - Add `allowProfessionalizzantiDuringStage` boolean column to `studentgroup` table
   - Default value: `false` (block professionalizzanti during stage)
   - Create migration/SQL update

2. **Entity Model:**
   - Add `allowProfessionalizzantiDuringStage` field to `StudentGroup.java` entity
   - JPA annotations: `@Column`, default value

3. **Constraint Logic:**
   - Modify H4 constraint in `BaseConstraintProvider.java`
   - Current: `nonProfLessonStagePairs` filters out all professionalizzanti courses
   - New: Also filter out professionalizzanti courses if group has `allowProfessionalizzantiDuringStage = false`
   - Logic: 
     ```
     if (course.professionalizzanti && !group.allowProfessionalizzantiDuringStage) {
         // Apply stage blackout
     }
     ```

4. **Admin UI:**
   - Add checkbox/toggle in `StudentGroupsAdmin.tsx` form
   - Label: "Allow professionalizzanti during stage"
   - Tooltip: "If enabled, professionalizzanti courses can be scheduled during stage periods. If disabled, all courses are blocked during stage."

5. **DataGenerator:**
   - Set `allowProfessionalizzantiDuringStage = false` for all groups on startup
   - Ensure G09 specifically gets `false`

6. **Testing:**
   - Verify G09 has no lessons on stage days after fix
   - Verify other groups respect their setting
   - Test constraint verifier for H4
   - Test admin UI CRUD operations

**Files to Modify:**
- `src/main/java/com/school/planner/domain/StudentGroup.java`
- `src/main/java/com/school/planner/solver/BaseConstraintProvider.java`
- `src/main/java/com/school/planner/bootstrap/DataGenerator.java`
- `src/main/webui/src/components/admin/StudentGroupsAdmin.tsx`
- Database migration script

**Acceptance Criteria:**
- [ ] StudentGroup entity has `allowProfessionalizzantiDuringStage` field
- [ ] H4 constraint respects the setting (blocks when false, allows when true)
- [ ] Admin UI exposes the setting
- [ ] G09 has `allowProfessionalizzantiDuringStage = false` by default
- [ ] Tests verify the constraint behavior
- [ ] No regression for other groups
<!-- SECTION:DESCRIPTION:END -->
