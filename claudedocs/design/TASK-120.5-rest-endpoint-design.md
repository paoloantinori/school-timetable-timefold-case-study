# Design Plan: TASK-120.5 REST Endpoint for Hybrid Phase 1a

**Created**: 2026-02-26
**Status**: Ready for Implementation
**Depends on**: TASK-120.4 (HybridPhase1aService) ✅ Complete

---

## 1. Summary

Add a REST endpoint to expose the `HybridPhase1aService.run()` method via the API.

**Endpoint**: `POST /api/schedule/pre-assign/two-phase/hybrid`

**Execution Model**: Synchronous (bounded by `planner.phase1a.max-candidates` config)

---

## 2. Architecture Decision

### Target File: `PreAssignmentResource.java`

**Why not ScheduleResource.java?**
- `PreAssignmentResource` already has the `/api/schedule/pre-assign` base path
- Contains all other pre-assignment endpoints including two-phase
- Maintains consistency with existing API structure

### Execution Model: Synchronous

**Why not async like two-phase?**
- Hybrid approach is bounded (maxCandidatesPerPhase = 100,000 by default)
- Typical execution: 2-15 seconds
- Deterministic algorithm, not an open-ended solver search

---

## 3. Implementation

### File: `src/main/java/com/school/planner/rest/PreAssignmentResource.java`

#### 3.1 Add Import and Injection

```java
// Add to imports
import com.school.planner.service.HybridPhase1aService;
import com.school.planner.service.HybridPhase1aReport;

// Add to class (around line 35, with other @Inject fields)
@Inject
HybridPhase1aService hybridPhase1aService;
```

#### 3.2 Add Endpoint (after two-phase endpoints, around line 185)

```java
// ─── Hybrid Phase 1a Endpoint ─────────────────────────────────────

/**
 * Run hybrid Phase 1a pre-assignment (generate → test → abort → retry).
 *
 * <p>This is a synchronous operation that mimics human planner behavior:
 * iterate rotation candidates, test each for 0hard, abort on failure,
 * retry with next candidate.</p>
 *
 * <p>Unlike two-phase (which uses Timefold solver), this approach is
 * deterministic and bounded by planner.phase1a.max-candidates.</p>
 *
 * @return 200 OK with HybridPhase1aReport on success;
 *         409 Conflict with HybridPhase1aReport on failure
 */
@POST
@Path("/two-phase/hybrid")
public Response runHybridPhase1a() {
    HybridPhase1aReport report = hybridPhase1aService.run();

    if (report.success) {
        return Response.ok(report).build();
    } else {
        return Response.status(Response.Status.CONFLICT)
            .entity(report)
            .build();
    }
}
```

---

## 4. API Contract

### Request

```
POST /api/schedule/pre-assign/two-phase/hybrid
Content-Type: application/json (body not required)
```

### Success Response (200 OK)

```json
{
  "success": true,
  "phase": "COMPLETE",
  "totalDuration": "PT2.5S",
  "totalCandidatesTried": 47,
  "winningCandidate": {
    "roomTag": "acconciatura",
    "assignments": [...]
  },
  "failureAnalysis": null,
  "phaseResults": [
    {
      "phaseName": "1-ACCONCIATURA",
      "success": true,
      "candidatesEvaluated": 12,
      "duration": "PT0.8S",
      "entitiesPinned": 18,
      "roomTag": "acconciatura"
    },
    {
      "phaseName": "2-LABS-moda",
      "success": true,
      "candidatesEvaluated": 5,
      "duration": "PT0.3S",
      "entitiesPinned": 24,
      "roomTag": "moda"
    }
  ]
}
```

### Failure Response (409 Conflict)

```json
{
  "success": false,
  "phase": "2-LABS-moda",
  "totalDuration": "PT15.2S",
  "totalCandidatesTried": 100000,
  "winningCandidate": null,
  "failureAnalysis": {
    "phase": "2-LABS-moda",
    "reason": "No valid rotation candidate found within candidate limit",
    "conflictPatterns": [
      {
        "type": "CANDIDATE_LIMIT",
        "description": "Tested 100000 candidates without finding a valid assignment",
        "occurrenceCount": 100000,
        "examples": ["Consider increasing planner.phase1a.max-candidates"]
      }
    ],
    "conflictingTeacherIds": [],
    "conflictingGroupIds": [],
    "suggestion": "Phase 2-LABS-moda failed. Review lab rotation data..."
  },
  "phaseResults": [...]
}
```

---

## 5. Testing

### 5.1 Unit Test (Optional)

Create `src/test/java/com/school/planner/rest/HybridPhase1aResourceTest.java`:

```java
package com.school.planner.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class HybridPhase1aResourceTest {

    @Test
    void testHybridEndpointReturns200OnSuccess() {
        RestAssured.given()
            .when()
            .post("/api/schedule/pre-assign/two-phase/hybrid")
            .then()
            .statusCode(anyOf(is(200), is(409))) // Depends on data state
            .body("success", isA(Boolean.class))
            .body("phase", isA(String.class))
            .body("phaseResults", isA(java.util.List.class));
    }
}
```

### 5.2 Integration Test (Manual)

```bash
# 1. Start app with fresh profile
./planner.sh stop
yes | ./planner.sh delete dev
./scripts/start-and-wait.sh dev

# 2. Run hybrid pre-assignment
./scripts/api/api.sh raw POST /api/schedule/pre-assign/two-phase/hybrid

# Expected: 200 OK with success=true
# Or: 409 Conflict with success=false and failureAnalysis

# 3. Verify score is 0hard (if success)
./scripts/api/api.sh score
# Expected: 0hard/.../...
```

### 5.3 Update api.sh Helper

Add convenience command to `scripts/api.sh`:

```bash
# In the case statement, add:
hybrid)
    shift
    METHOD="POST"
    ENDPOINT="/api/schedule/pre-assign/two-phase/hybrid"
    ;;
```

Then usage becomes: `./scripts/api/api.sh hybrid`

---

## 6. Acceptance Criteria Checklist

| AC # | Criteria | Implementation |
|------|----------|----------------|
| 1 | Endpoint `POST /api/schedule/pre-assign/two-phase/hybrid` exists | `@POST @Path("/two-phase/hybrid")` |
| 2 | Returns 200 OK with report on success | `Response.ok(report).build()` |
| 3 | Returns 409 Conflict with failure analysis on failure | `Response.status(CONFLICT).entity(report)` |
| 4 | Response JSON is properly serialized | HybridPhase1aReport uses public final fields |
| 5 | Unit tests pass | HybridPhase1aResourceTest (optional) |
| 6 | Integration test confirms 0hard score | Manual verification |

---

## 7. Future Enhancements (Out of Scope)

- **Dry-run endpoint**: `GET /two-phase/hybrid/dry-run` - requires `HybridPhase1aService.runDryRun()` method
- **Phase-specific endpoints**: `POST /two-phase/hybrid/phase1`, `POST /two-phase/hybrid/phase2/{roomTag}`
- **Async variant**: For very large candidate spaces, consider async with status polling

---

## 8. Verification Commands

```bash
# Compile check
mvn compile -DskipTests

# Run unit tests (if created)
mvn test -Dtest=HybridPhase1aResourceTest

# Integration test
./scripts/start-and-wait.sh dev
./scripts/api/api.sh raw POST /api/schedule/pre-assign/two-phase/hybrid
```
