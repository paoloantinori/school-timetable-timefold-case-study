---
id: TASK-120.02
title: 'TASK-120.2: Rotation Candidate Generator'
status: Done
assignee: []
created_date: '2026-02-25 18:18'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - subtask
dependencies: []
parent_task_id: TASK-120
priority: high
ordinal: 8750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Overview
Create the generator that produces rotation candidates for testing. This class generates all valid combinations of dayOfWeek assignments for lab blocks.

## Dependencies
- TASK-120.1 (RotationCandidate, RotationCandidate.BlockKey)

## Files to Create

### `RotationCandidateGenerator.java`

```java
package com.school.planner.service;

import com.school.planner.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Generates rotation candidates for hybrid Phase 1a.
 * 
 * Strategy:
 * 1. Identify unique (teacher, studentGroup) pairs from LabRotation records
 * 2. For external teachers (T28), only their available day is valid
 * 3. For other teachers, all 5 weekdays are valid
 * 4. Generate Cartesian product of all valid day combinations
 */
@ApplicationScoped
public class RotationCandidateGenerator {
    
    // Priority order for day selection (matches human planner preference)
    private static final List<DayOfWeek> DAY_PRIORITY = List.of(
        DayOfWeek.THURSDAY,   // T28's day (external teacher)
        DayOfWeek.WEDNESDAY,  // T26's preferred day
        DayOfWeek.TUESDAY,    // T18's preferred day
        DayOfWeek.MONDAY,
        DayOfWeek.FRIDAY
    );
    
    @Inject
    LabRotationRepository labRotationRepository;
    
    @Inject
    TeacherAvailabilityRepository availabilityRepository;
    
    /**
     * Generate all valid rotation candidates for the given phase.
     * 
     * @param phase The phase (1=ACCONCIATURA, 2=LABS, 3=PROFESSIONALIZZANTI)
     * @param roomTag Filter by room tag (e.g., "acconciatura")
     * @return Stream of candidates (lazy evaluation for memory efficiency)
     */
    public Stream<RotationCandidate> generateCandidates(int phase, String roomTag) {
        // Implementation
    }
    
    /**
     * Get valid days for a teacher based on availability constraints.
     */
    public Set<DayOfWeek> getValidDaysForTeacher(Teacher teacher) {
        // External teachers (like T28) only have one valid day
        // Other teachers have all weekdays available
    }
    
    /**
     * Extract unique (teacher, studentGroup) pairs from LabRotations.
     */
    public Set<RotationCandidate.BlockKey> extractBlockKeys(String roomTag) {
        // Query LabRotation by roomTag and extract unique pairs
    }
}
```

## Unit Tests
Create `RotationCandidateGeneratorTest.java`:

```java
@QuarkusTest
class RotationCandidateGeneratorTest {
    
    @Inject
    RotationCandidateGenerator generator;
    
    @Test
    void testExternalTeacherOnlyOneValidDay() {
        // T28 should only have THURSDAY as valid day
    }
    
    @Test
    void testRegularTeacherHasFiveValidDays() {
        // Non-external teachers should have all 5 weekdays
    }
    
    @Test
    void testCandidateCountIsCorrect() {
        // 3 fixed pairs + 3 flexible pairs (5^3 = 125) = 125 total
    }
    
    @Test
    void testCandidatesAreUnique() {
        // No duplicate candidates
    }
    
    @Test
    void testLazyEvaluation() {
        // Verify stream is not materialized until terminal operation
    }
}
```

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Generates correct number of candidates (Cartesian product)
- [ ] #2 External teachers constrained to their available days
- [ ] #3 Returns Stream for lazy evaluation (memory efficient)
- [ ] #4 Day priority order matches human planner preference
- [ ] #5 Unit tests pass

## Verification
```bash
mvn compile -DskipTests
mvn test -Dtest=RotationCandidateGeneratorTest
```
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
