---
id: TASK-279
title: Professionalizzanti day-by-day rotation in pre-assignment
status: Done
assignee: []
created_date: '2026-03-20 20:20'
updated_date: '2026-07-15 19:33'
labels:
  - pre-assignment
  - professionalizzanti
  - bug
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Problem**: Phase 3 pre-assignment currently processes professionalizzanti courses greedily by course, filling all available stage day slots with one subject before moving to the next. This results in days with only "Disegno edile ed impiantistico" instead of variety.

**Requirement**: Each professionalizzanti day should have 1 lesson from each subject (up to 5 subjects for a 5-hour day), rotating through subjects in a recurring pattern.

**Example for G07** (6 professionalizzanti subjects, 5-hour days):
- Week 1 Wednesday: Disegno, Gestione, Laboratorio, Personalizzazione, Progettazione (5 different subjects)
- Week 2 Wednesday: Sicurezza, Disegno, Gestione, Laboratorio, Personalizzazione (rotate pattern)
- Continue until all lessons are assigned

**Files to modify**:
- `src/main/java/com/school/planner/service/HybridPhase1aService.java` - Phase 3 logic (runPhase3 method around line 625)

**Current behavior** (course-by-course greedy):
```java
for (Map.Entry<Long, List<Lesson>> entry : lessonsByCourse.entrySet()) {
    // Place ALL lessons of this course before moving to next course
    for (Lesson lesson : courseLessons) {
        findStageDaySlot(...); // Fills first available slot
    }
}
```

**Required behavior** (day-by-day round-robin):
1. Iterate through stage days chronologically
2. For each day, assign up to N lessons (N = maxHoursPerDay, typically 5)
3. Each day gets at most 1 lesson from each course
4. Rotate through courses to ensure variety
5. Skip days that would exceed H11 maxHoursPerDay constraint

**Data context**:
- G07 professionalizzandi subjects: Disegno edile (80h), Gestione (30h), Laboratorio termoidraulico (24h), Personalizzazione (60h), Progettazione termoidraulica (64h), Sicurezza (12h)
- Total: 270 hours across 6 subjects
- ProfessionalizzantiDay entity defines which day of week (e.g., Wednesday) is for professional lessons
- Stage period: typically ~10 weeks of 1 day/week = ~10 stage days available
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): Phase 3 uses date-first round-robin (HybridPhase1aService:724). Groups by (group,course), enforces max 1/course/day, iterates chronologically.
<!-- SECTION:FINAL_SUMMARY:END -->
