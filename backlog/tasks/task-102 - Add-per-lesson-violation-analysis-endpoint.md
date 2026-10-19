---
id: TASK-102
title: Add per-lesson violation analysis endpoint
status: Done
assignee: []
created_date: '2026-02-23 19:47'
updated_date: '2026-03-09 19:50'
labels:
  - backend
  - api
  - analysis
  - claude-planned
dependencies: []
priority: medium
ordinal: 65000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add a REST endpoint that returns constraint violations per individual lesson/lab-block entity, using Timefold's SolutionManager.explain() and Indictment API. Currently our analysis endpoint (GET /api/schedule/analysis) only shows aggregate constraint scores — it doesn't identify WHICH specific lessons are involved in violations.

Inspired by dmon-scheduler's per-session analysis endpoint.

**New endpoint:** `GET /api/schedule/analysis/lessons`

**Response format:**
```json
[
  {
    "lessonId": 42,
    "entityType": "Lesson",
    "teacher": "T06",
    "subject": "Informatica",
    "group": "G01",
    "timeslot": "2026-02-23T08:00",
    "room": "Lab Info 1",
    "hasViolations": true,
    "totalScore": "-2hard/0medium/0soft",
    "violations": [
      {"constraint": "H1.1", "level": "HARD", "score": -1, "message": "Teacher conflict with lesson #43"}
    ]
  }
]
```

**Key files:**
- `rest/ScheduleResource.java` (add endpoint)
- `service/ScheduleService.java` (add analysis logic)
- New DTO class for response

**Frontend integration (separate task):** Timeline cells could show red/yellow violation borders based on this data.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GET /api/schedule/analysis/lessons returns per-entity violation data after solving
- [x] #2 Response includes lesson metadata (teacher, subject, group, timeslot, room)
- [x] #3 Each violation entry includes constraint ID, level (HARD/MEDIUM/SOFT), and score impact
- [x] #4 Both Lesson and LabBlock entities are covered
- [x] #5 Endpoint returns empty array when no violations exist
- [x] #6 Endpoint returns 409 or similar when no solution is available
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
# Implementation Plan: Per-Lesson Violation Analysis Endpoint

## Overview

Add `GET /api/schedule/analysis/lessons` endpoint that returns per-entity constraint violations using Timefold's Indictment API.

## Architecture

```
ScheduleResource.getLessonAnalysis()
    └── ScheduleService.analyzeLessonViolations()
            └── SolutionManager.explain(schedule)
                    └── ScoreExplanation.getIndictmentMap()
```

## Key Files to Modify

| File | Changes |
|------|---------|
| `rest/ScheduleResource.java` | Add new endpoint `GET /analysis/lessons` |
| `service/ScheduleService.java` | Add `analyzeLessonViolations()` method |

## Implementation Steps

### Step 1: Create Response DTO (new file)

**File:** `rest/dto/LessonViolationAnalysis.java`

```java
public record LessonViolationAnalysis(
    Long lessonId,
    String entityType,          // "Lesson" or "LabBlock"
    String teacher,
    String subject,
    String studentGroup,
    String timeslot,            // ISO datetime or null
    String room,
    boolean hasViolations,
    String totalScore,          // e.g., "-2hard/0medium/0soft"
    List<ViolationDetail> violations
) {
    public record ViolationDetail(
        String constraint,      // e.g., "H1.1"
        String level,           // "HARD", "MEDIUM", "SOFT"
        int score,              // negative value
        String message          // human-readable (optional)
    ) {}
}
```

### Step 2: Add Service Method

**File:** `service/ScheduleService.java`

Add method after `analyzeScore()`:

```java
@Transactional
public List<LessonViolationAnalysis> analyzeLessonViolations() {
    SchoolSchedule schedule = loadSchedule();
    solutionManager.update(schedule);
    
    ScoreExplanation<SchoolSchedule, HardMediumSoftScore> explanation = 
        solutionManager.explain(schedule);
    
    Map<Object, Indictment<HardMediumSoftScore>> indictmentMap = 
        explanation.getIndictmentMap();
    
    List<LessonViolationAnalysis> results = new ArrayList<>();
    
    // Process Lessons
    for (Lesson lesson : schedule.getLessons()) {
        Indictment<HardMediumSoftScore> indictment = indictmentMap.get(lesson);
        if (indictment != null) {
            results.add(buildAnalysis(lesson, indictment));
        }
    }
    
    // Process LabBlocks
    for (LabBlock block : schedule.getLabBlocks()) {
        Indictment<HardMediumSoftScore> indictment = indictmentMap.get(block);
        if (indictment != null) {
            results.add(buildAnalysis(block, indictment));
        }
    }
    
    return results;
}

private LessonViolationAnalysis buildAnalysis(Lesson lesson, Indictment<HardMediumSoftScore> indictment) {
    List<ViolationDetail> violations = indictment.getConstraintMatchSet().stream()
        .map(cm -> new ViolationDetail(
            extractConstraintName(cm.getConstraintRef()),
            cm.getScore().hardScore() < 0 ? "HARD" : 
                cm.getScore().mediumScore() < 0 ? "MEDIUM" : "SOFT",
            Math.abs(cm.getScore().hardScore() 
                + cm.getScore().mediumScore() 
                + cm.getScore().softScore()),
            null  // message - could be derived from constraint ID
        ))
        .toList();
    
    return new LessonViolationAnalysis(
        lesson.id,
        "Lesson",
        lesson.getTeacher()?.name,
        lesson.getSubject(),
        lesson.getStudentGroup()?.name,
        lesson.timeslot?.startDateTime?.toString(),
        lesson.room?.name,
        true,
        indictment.getScore().toString(),
        violations
    );
}
```

### Step 3: Add REST Endpoint

**File:** `rest/ScheduleResource.java`

Add after `getScoreAnalysis()`:

```java
@GET
@Path("/analysis/lessons")
public Response getLessonAnalysis() {
    if (scheduleService.getStatus() == SolverStatus.SOLVING_ACTIVE) {
        return Response.status(Response.Status.CONFLICT)
            .entity(Map.of("error", "Cannot analyze while solving"))
            .build();
    }
    
    List<LessonViolationAnalysis> analysis = scheduleService.analyzeLessonViolations();
    return Response.ok(analysis).build();
}
```

## Dependencies

- Timefold's `ScoreExplanation` and `Indictment` are already available via `solutionManager.explain()`
- No new Maven dependencies required

## Edge Cases

| Case | Handling |
|------|----------|
| No violations | Return empty list `[]` |
| Solver running | Return 409 CONFLICT |
| No solution yet | Return 404 or empty list |
| Entity with no timeslot | Include with `timeslot: null` |

## Response Example

```json
[
  {
    "lessonId": 42,
    "entityType": "Lesson",
    "teacher": "T06",
    "subject": "Informatica",
    "studentGroup": "G01",
    "timeslot": "2026-02-23T08:00",
    "room": "Lab Info 1",
    "hasViolations": true,
    "totalScore": "-2hard/0medium/0soft",
    "violations": [
      {"constraint": "H1.1", "level": "HARD", "score": 1, "message": null},
      {"constraint": "H2.1", "level": "HARD", "score": 1, "message": null}
    ]
  }
]
```

## Testing

1. **Unit test**: Mock `SolutionManager.explain()` with known indictment map
2. **Integration test**: Run solver, call endpoint, verify violations match score analysis

## Estimated Effort

- DTO + Service method: 30 min
- REST endpoint: 15 min
- Testing: 30 min
- **Total: ~1.5 hours**
<!-- SECTION:PLAN:END -->
