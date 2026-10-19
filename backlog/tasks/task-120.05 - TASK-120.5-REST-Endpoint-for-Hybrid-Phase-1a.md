---
id: TASK-120.05
title: 'TASK-120.5: REST Endpoint for Hybrid Phase 1a'
status: Done
assignee:
  - claude
created_date: '2026-02-25 18:20'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - subtask
  - rest-api
dependencies: []
parent_task_id: TASK-120
priority: high
ordinal: 11750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Overview
Create the REST endpoint that exposes the hybrid Phase 1a service via the API.

## Dependencies
- TASK-120.4 (HybridPhase1aService)
- Existing: `ScheduleResource.java`

## Files to Modify

### `ScheduleResource.java` - Add endpoint

```java
/**
 * Run hybrid Phase 1a pre-assignment (generate → test → abort → retry).
 * 
 * @return HybridPhase1aReport with success/failure and analysis
 */
@POST
@Path("/pre-assign/two-phase/hybrid")
@Produces(MediaType.APPLICATION_JSON)
public Response runHybridPhase1a() {
    HybridPhase1aReport report = hybridPhase1aService.run();
    
    if (report.success()) {
        return Response.ok(report).build();
    } else {
        return Response.status(Response.Status.CONFLICT)
            .entity(report)
            .build();
    }
}
```

## Files to Create

### `HybridPhase1aResource.java` (optional, if prefer separate resource)

```java
package com.school.planner.rest;

import com.school.planner.service.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/schedule/pre-assign")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HybridPhase1aResource {
    
    @Inject
    HybridPhase1aService hybridPhase1aService;
    
    @POST
    @Path("/two-phase/hybrid")
    public Response runHybridPhase1a() {
        HybridPhase1aReport report = hybridPhase1aService.run();
        
        if (report.success()) {
            return Response.ok(report).build();
        } else {
            return Response.status(Response.Status.CONFLICT)
                .entity(report)
                .build();
        }
    }
}
```

## Unit Tests
Create `HybridPhase1aResourceTest.java`:

```java
@QuarkusTest
class HybridPhase1aResourceTest {
    
    @TestHTTPResource
    URL url;
    
    @Test
    void testHybridEndpointReturns200OnSuccess() {
        // Mock service to return success
        // Call POST /api/schedule/pre-assign/two-phase/hybrid
        // Expect 200 OK with report body
    }
    
    @Test
    void testHybridEndpointReturns409OnFailure() {
        // Mock service to return failure
        // Call POST /api/schedule/pre-assign/two-phase/hybrid
        // Expect 409 Conflict with failure analysis
    }
    
    @Test
    void testReportSerialization() {
        // Verify JSON structure matches expected format
    }
}
```

## Integration Test
```bash
# Start app
./scripts/start-and-wait.sh dev

# Run hybrid pre-assignment
./scripts/api/api.sh raw POST /api/schedule/pre-assign/two-phase/hybrid

# Verify score
./scripts/api/api.sh score  # Should show 0hard
```

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Endpoint `POST /api/schedule/pre-assign/two-phase/hybrid` exists
- [x] #2 Returns 200 OK with report on success
- [x] #3 Returns 409 Conflict with failure analysis on failure
- [x] #4 Response JSON is properly serialized
- [x] #5 Unit tests pass
- [x] #6 Integration test confirms 0hard score

## Verification
```bash
mvn compile -DskipTests
mvn test -Dtest=HybridPhase1aResourceTest
```
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Design Plan

See `claudedocs/design/TASK-120.5-rest-endpoint-design.md` for full design.

### Key Decisions

1. **Target file**: `PreAssignmentResource.java` (not ScheduleResource.java) - matches existing pre-assignment endpoint pattern
2. **Execution model**: Synchronous (bounded by maxCandidatesPerPhase, typically 2-15s)
3. **Response pattern**: 200 OK on success, 409 Conflict on failure

### Implementation (Single File Change)

Add to `PreAssignmentResource.java`:
- Import: `HybridPhase1aService`, `HybridPhase1aReport`
- Inject: `@Inject HybridPhase1aService hybridPhase1aService`
- Endpoint: `@POST @Path("/two-phase/hybrid") public Response runHybridPhase1a()`

### API Contract

```
POST /api/schedule/pre-assign/two-phase/hybrid

200 OK:
{
  "success": true,
  "phase": "COMPLETE",
  "totalDuration": "PT2.5S",
  "totalCandidatesTried": 47,
  "phaseResults": [...]
}

409 Conflict:
{
  "success": false,
  "phase": "2-LABS-moda",
  "failureAnalysis": {...}
}
```
<!-- SECTION:NOTES:END -->
