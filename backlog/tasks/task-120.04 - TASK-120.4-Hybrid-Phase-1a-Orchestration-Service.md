---
id: TASK-120.04
title: 'TASK-120.4: Hybrid Phase 1a Orchestration Service'
status: Done
assignee:
  - '@claude'
created_date: '2026-02-25 18:19'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - subtask
dependencies: []
parent_task_id: TASK-120
priority: high
ordinal: 10750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Overview
Create the main orchestration service that runs the sequential phases with generate + test loops. This is the core of the hybrid approach.

## Dependencies
- TASK-120.1 (HybridPhase1aReport, FailureAnalysis)
- TASK-120.2 (RotationCandidateGenerator)
- TASK-120.3 (RotationTestRunner)

## Files to Create

### `HybridPhase1aService.java`

```java
package com.school.planner.service;

import com.school.planner.domain.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates the hybrid Phase 1a pre-assignment.
 * 
 * Implements sequential phases with generate → test → abort → retry loops:
 * 
 * PHASE 1: Acconciatura
 *   - Generate rotation candidates (T28 fixed Thu, others flexible)
 *   - Test each candidate with early abort
 *   - Pin successful candidate
 *   - Proceed to Phase 2
 * 
 * PHASE 2: Other Labs (meccanica, moda, informatica, termoidraulica)
 *   - For each roomTag:
 *     - Generate candidates
 *     - Test respecting Phase 1 pins
 *     - Pin successful candidate
 *   - Proceed to Phase 3
 * 
 * PHASE 3: Professionalizzanti
 *   - Place on stage days (respecting Phase 1+2 pins)
 *   - Pin successful assignments
 * 
 * BLOCKING ERROR: If any phase fails to find a valid candidate,
 * return failure analysis (no fallback to solver).
 */
@ApplicationScoped
public class HybridPhase1aService {
    
    @Inject
    RotationCandidateGenerator generator;
    
    @Inject
    RotationTestRunner testRunner;
    
    @Inject
    PreAssignmentContext context;
    
    // Room tags in priority order
    private static final List<String> PHASE2_ROOM_TAGS = List.of(
        "acconciatura",    // Already done in Phase 1
        "meccanica",
        "moda",
        "informatica",
        "termoidraulica"
    );
    
    /**
     * Run the full hybrid Phase 1a pipeline.
     * 
     * @return Report with success/failure and analysis
     */
    @Transactional
    public HybridPhase1aReport run() {
        Instant start = Instant.now();
        List<HybridPhase1aReport.PhaseResult> phaseResults = new ArrayList<>();
        
        // PHASE 1: Acconciatura
        var phase1Result = runPhase1();
        phaseResults.add(phase1Result);
        if (!phase1Result.success()) {
            return buildFailureReport(phase1Result, phaseResults, start);
        }
        
        // PHASE 2: Other Labs
        var phase2Result = runPhase2();
        phaseResults.add(phase2Result);
        if (!phase2Result.success()) {
            return buildFailureReport(phase2Result, phaseResults, start);
        }
        
        // PHASE 3: Professionalizzanti
        var phase3Result = runPhase3();
        phaseResults.add(phase3Result);
        if (!phase3Result.success()) {
            return buildFailureReport(phase3Result, phaseResults, start);
        }
        
        return buildSuccessReport(phaseResults, start);
    }
    
    /**
     * Phase 1: Acconciatura lab rotation.
     */
    private HybridPhase1aReport.PhaseResult runPhase1() {
        Log.info("Starting Phase 1: Acconciatura");
        Instant start = Instant.now();
        int candidatesTried = 0;
        
        var candidates = generator.generateCandidates(1, "acconciatura");
        for (var candidate : candidates.collect(Collectors.toList())) {
            candidatesTried++;
            var result = testRunner.testCandidate(candidate, 1);
            
            if (result.success()) {
                pinAssignments(result.successfulAssignments());
                Log.infof("Phase 1 succeeded with candidate #%d", candidatesTried);
                return new HybridPhase1aReport.PhaseResult(
                    "1-ACCONCIATURA", true, candidatesTried,
                    Duration.between(start, Instant.now()),
                    result.successfulAssignments().size()
                );
            }
        }
        
        Log.errorf("Phase 1 failed after %d candidates", candidatesTried);
        return new HybridPhase1aReport.PhaseResult(
            "1-ACCONCIATURA", false, candidatesTried,
            Duration.between(start, Instant.now()), 0
        );
    }
    
    /**
     * Phase 2: Other labs (meccanica, moda, informatica, termoidraulica).
     */
    private HybridPhase1aReport.PhaseResult runPhase2() {
        // Similar structure to Phase 1, but for each roomTag
    }
    
    /**
     * Phase 3: Professionalizzanti on stage days.
     */
    private HybridPhase1aReport.PhaseResult runPhase3() {
        // Place professional subjects on stage days
    }
    
    private void pinAssignments(Map<RotationCandidate.BlockKey, SlotCandidate> assignments) {
        // Persist the assignments with pinned=true
    }
    
    private HybridPhase1aReport buildSuccessReport(...) {}
    private HybridPhase1aReport buildFailureReport(...) {}
}
```

## Unit Tests
Create `HybridPhase1aServiceTest.java`:

```java
@QuarkusTest
class HybridPhase1aServiceTest {
    
    @Inject
    HybridPhase1aService service;
    
    @Test
    void testFullPipelineSucceedsWithValidData() {
        // With valid seed data, should succeed
    }
    
    @Test
    void testPhase1FailsWithConflictingData() {
        // With conflicting data, should fail Phase 1
    }
    
    @Test
    void testPhase2RespectsPhase1Pins() {
        // Phase 2 should not conflict with Phase 1 pins
    }
    
    @Test
    void testPhase3RespectsAllPreviousPins() {
        // Phase 3 should respect Phase 1+2 pins
    }
    
    @Test
    void testReportContainsAccurateTiming() {
        // Report should have accurate duration for each phase
    }
}
```

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Phase 1 runs generate → test → pin loop for Acconciatura
- [ ] #2 Phase 2 runs sequentially for each roomTag
- [ ] #3 Phase 3 places professional subjects on stage days
- [ ] #4 Returns failure analysis when any phase fails (no fallback)
- [ ] #5 Report contains accurate timing and counts
- [ ] #6 Unit tests pass

## Verification
```bash
mvn compile -DskipTests
mvn test -Dtest=HybridPhase1aServiceTest
```
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation complete - HybridPhase1aService.java with sequential phase pipeline (Phase 1: Acconciatura → Phase 2: moda/termoidraulica/meccanica/informatica → Phase 3: Professionalizzanti). 9 unit tests passing. Configurable max-candidates limit via planner.phase1a.max-candidates property.
<!-- SECTION:NOTES:END -->
