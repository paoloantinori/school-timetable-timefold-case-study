---
id: TASK-120.01
title: 'TASK-120.1: Data Classes for Hybrid Phase 1a'
status: Done
assignee: []
created_date: '2026-02-25 18:17'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - subtask
dependencies: []
parent_task_id: TASK-120
priority: high
ordinal: 7750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Overview
Create the foundational data classes needed by the Hybrid Phase 1a implementation. These are simple POJOs/records that define the data structures.

## Files to Create

### 1. `RotationCandidate.java`
A record representing a single rotation candidate (a complete assignment of days to lab blocks).

```java
package com.school.planner.service;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Represents a candidate rotation assignment for lab blocks.
 * Maps (teacherId, studentGroupId) pairs to their assigned dayOfWeek.
 */
public record RotationCandidate(
    Map<BlockKey, DayOfWeek> assignments,
    int candidateNumber
) {
    public record BlockKey(long teacherId, long studentGroupId) {}
}
```

### 2. `HybridPhase1aReport.java`
A record containing the results of running the hybrid phase 1a.

```java
package com.school.planner.service;

import java.time.Duration;
import java.util.List;

/**
 * Report from running the hybrid Phase 1a pre-assignment.
 */
public record HybridPhase1aReport(
    boolean success,
    String phase,  // "1-ACCONCIATURA", "2-LABS", "3-PROFESSIONALIZZANTI", or "FAILED"
    Duration totalDuration,
    int candidatesTried,
    RotationCandidate winningCandidate,
    FailureAnalysis failureAnalysis,
    List<PhaseResult> phaseResults
) {
    public record PhaseResult(
        String phaseName,
        boolean success,
        int candidatesEvaluated,
        Duration duration,
        int entitiesPinned
    ) {}
}
```

### 3. `FailureAnalysis.java`
A record containing detailed analysis of why a phase failed.

```java
package com.school.planner.service;

import java.util.List;
import java.util.Set;

/**
 * Detailed analysis of why no rotation candidate achieved 0hard.
 */
public record FailureAnalysis(
    String phase,
    String reason,
    List<ConflictPattern> conflictPatterns,
    Set<Long> conflictingTeacherIds,
    Set<Long> conflictingGroupIds,
    String suggestion
) {
    public record ConflictPattern(
        String type,  // "TEACHER_DOUBLE_BOOKING", "ROOM_CONFLICT", etc.
        String description,
        int occurrenceCount
    ) {}
}
```

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All three classes compile without errors
- [ ] #2 Each class has proper Javadoc
- [ ] #3 Classes are immutable (records or final fields)
- [ ] #4 Unit tests verify constructors and accessors

## Unit Tests
Create `HybridPhase1aDataClassesTest.java`:
- Test RotationCandidate creation and immutability
- Test HybridPhase1aReport with success/failure scenarios
- Test FailureAnalysis with various conflict patterns

## Verification
```bash
mvn compile -DskipTests
mvn test -Dtest=HybridPhase1aDataClassesTest
```
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->
