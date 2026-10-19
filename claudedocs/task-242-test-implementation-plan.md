# TASK-242: ConstraintVerifier Unit Tests Implementation Plan

## Executive Summary

**Status:** Ready to implement
**Current Tests:** 71 tests in `SchoolScheduleConstraintProviderTest.java`
**Estimated Gap:** 4-6 constraint test suites missing
**Effort:** 2-3 hours for remaining tests + verification

---

## Current State Analysis

### Test Coverage Audit (2026-03-15)

| Constraint | Tests Exist | Notes |
|-----------|-------------|-------|
| **Hard Constraints** |||
| H1: Teacher conflict | ✅ (2 tests) | teacherConflict_penalized, teacherConflict_notPenalized |
| H2: Room conflict | ✅ (2 tests) | roomConflict_penalized, roomConflict_notPenalized_differentRoom |
| H3: Student group conflict | ✅ (1 test) | studentGroupConflict_penalized |
| H4: Stage blackout | ✅ (2 tests) | stageBlackout_penalized, stageBlackout_notPenalized_exemptProfessionalizzanti |
| H5: Teacher unavailability | ✅ (1 test) | teacherUnavailability_penalized |
| **H6: School holiday** | ❌ **MISSING** | Constraint exists, no tests |
| H7: Max teacher daily hours | ✅ (3 tests) | penalized_7lessons, notPenalized_6lessons, notPenalized_differentDays |
| H11: Max student group daily hours | ✅ (3 tests) | penalized_exceedsLimit, notPenalized_withinLimit, notPenalized_nullLimit |
| H14: Student group start date | ✅ (2 tests) | penalized, notPenalized_afterStartDate |
| H15: Same-day same-subject consecutive | ✅ (2 tests) | penalized (60min gap), notPenalized_consecutive |
| H16a: Consecutive same-subject same teacher | ✅ (2 tests) | penalized (different room), notPenalized_sameRoom |
| **H16b: Consecutive same-subject same teacher-room** | ⚠️ **UNCLEAR** | May be covered by H16a tests |
| H-M2: Room suitability | ✅ (2 tests) | penalized, notPenalized |
| **Medium Constraints** |||
| M-LA: Lab assistant conflict | ❌ **MISSING** | Constraint exists, no tests |
| M3: Room capacity | ✅ (4 tests) | penalized, notPenalized_fits, notPenalized_exact, notPenalized_nullRoom |
| M5: Reward Informatica after stage | ✅ (2 tests) | rewards, noReward_duringStage |
| **M12: Overflow beyond target end date** | ❌ **MISSING** | Constraint exists, no tests |
| M13: Vertically compact | ✅ (2 tests) | penalized (1 day gap), notPenalized_gracePeriod |
| M20: Sparse day (horizontal compact) | ✅ (5 tests) | penalized (1/2/3h), notPenalized_4h, notPenalized_pinned, notPenalized_G14 |
| M21: Max 1h per subject per day | ✅ (5 tests) | penalized (2/3h), notPenalized_lab, notPenalized_1h, notPenalized_differentDays |
| **Soft Constraints** |||
| S1: Minimize teacher gaps | ✅ (4 tests) | penalized_gap, penalized_multipleGaps, notPenalized_consecutive, penalized_structuralBreakExcluded |
| S2: Subject distribution | ✅ (4 tests) | penalized_pairOfLessons, penalized_threeLessons, notPenalized_differentGroups, notPenalized_differentDays |
| S3: Teacher preference | ✅ (3 tests) | penalized, notPenalized_vacationType, notPenalized_noOverlap |
| S7: Reward consecutive student lessons | ✅ (4 tests) | rewards_adjacent, rewards_30minGap, noReward_notAdjacent, rewards_threeLessons |
| S8: Isolated student lesson | ✅ (3 tests) | penalized_singleLesson, notPenalized_twoAdjacent, penalized_oneIsolated |
| S9: Dilute hours over year | ✅ (3 tests) | penalized_twoLessonsSameWeek, penalized_threeLessonsSameWeek, notPenalized_pinned |
| S10: Discourage generic lessons in labs | ✅ (4 tests) | penalized_genericInLab, notPenalized_courseRequiresLab, notPenalized_genericInRegularRoom, notPenalized_courseRequiresDifferentTag |
| S11: Student group room affinity | ✅ (5 tests) | penalized_twoRooms, penalized_threeRooms, notPenalized_oneRoom, notPenalized_labLessons, notPenalized_pinned |

### Summary

- **Total constraints with tests:** ~25 constraints
- **Constraints missing tests:** 4 (H6, H16b, M-LA, M12)
- **Test count:** 71 tests across 25+ constraint methods

---

## Gap Analysis

### 1. H6: School Holiday Constraint ⚠️ HIGH PRIORITY

**Why Missing:** Simple oversight - constraint exists but no tests

**Constraint Behavior:**
- Penalizes lessons scheduled on school holidays
- Hard constraint (ONE_HARD per violation)
- Requires `SchoolHoliday` entity + `Lesson` matching date

**Test Cases Needed:**
1. ✅ Lesson on holiday date → penalized
2. ✅ Lesson on regular date → not penalized
3. ✅ Null holiday list → no penalty

**Estimated Effort:** 10 minutes

### 2. H16b: Consecutive Same-Subject Same Teacher-Room

**Status:** Possibly covered by H16a tests

**Analysis Needed:** Verify if H16a tests cover both room-change scenarios

**Test Cases Needed (if not covered):**
1. ✅ Same teacher, different room → penalized
2. ✅ Same teacher, same room → not penalized

**Estimated Effort:** 5 minutes (if H16a covers it, else 10 minutes)

### 3. M-LA: Lab Assistant Conflict ⚠️ MEDIUM PRIORITY

**Why Missing:** Medium constraint, may have been deprioritized

**Constraint Behavior:**
- Penalizes when lab assistant is assigned to concurrent lab sessions
- Medium constraint (100 MEDIUM per violation)
- Requires `LabAssistant` entity + `Lesson` matching room/time

**Test Cases Needed:**
1. ✅ Lab assistant double-booked → penalized
2. ✅ Lab assistant has gap → not penalized
3. ✅ No lab assistant assigned → not penalized
4. ✅ Non-lab lessons → no penalty

**Estimated Effort:** 15 minutes

### 4. M12: Overflow Beyond Target End Date ⚠️ MEDIUM PRIORITY

**Why Missing:** Medium constraint, complex to test

**Constraint Behavior:**
- Penalizes lessons scheduled after target end date
- Medium constraint (10 MEDIUM per day over)
- Requires `Course.targetEndDate` calculation

**Test Cases Needed:**
1. ✅ Lesson after target date → penalized
2. ✅ Lesson on or before target → not penalized
3. ✅ Pinned lessons → exempt from penalty
4. ✅ Course with no target date → no penalty

**Estimated Effort:** 20 minutes

---

## Implementation Plan

### Phase 1: Add H6 School Holiday Tests (10 min)

**File:** `src/test/java/com/school/planner/solver/SchoolConstraintProviderTest.java`

```java
// ─── H6: School Holiday (L) ─────────────────────────────────

@Test
void schoolHoliday_penalized() {
    Course course = course(1L, "Math", TEACHER1, GROUP1);
    Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

    SchoolHoliday holiday = new SchoolHoliday();
    holiday.date = MON; // 2025-10-06

    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::schoolHoliday)
            .given(lesson, holiday)
            .penalizesBy(1);
}

@Test
void schoolHoliday_notPenalized_regularDay() {
    Course course = course(1L, "Math", TEACHER1, GROUP1);
    Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

    SchoolHoliday holiday = new SchoolHoliday();
    holiday.date = TUE; // Different day

    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::schoolHoliday)
            .given(lesson, holiday)
            .penalizesBy(0);
}

@Test
void schoolHoliday_notPenalized_noHolidays() {
    Course course = course(1L, "Math", TEACHER1, GROUP1);
    Lesson lesson = lesson(1L, course, TS_MON_08, ROOM_A);

    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::schoolHoliday)
            .given(lesson)
            .penalizesBy(0);
}
```

### Phase 2: Verify H16b Coverage (5 min)

**Action:** Check if existing H16a tests cover room-change scenarios

**If H16b is NOT covered:** Add tests

```java
// ─── H16b: Consecutive Same-Subject Same Teacher-Room ────────────

@Test
void consecutiveSameSubjectSameTeacherRoom_penalized_differentRoom() {
    Course course = course(1L, "Math", TEACHER1, GROUP1);
    Lesson l1 = lesson(1L, course, TS_MON_08, ROOM_A);
    Lesson l2 = lesson(2L, course, TS_MON_09, ROOM_B); // Different room

    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::consecutiveSameSubjectSameTeacherSameRoom)
            .given(l1, l2)
            .penalizesBy(1);
}
```

### Phase 3: Add M-LA Lab Assistant Conflict Tests (15 min)

**Prerequisites:** Need to add lab assistant setup to test data

```java
// ─── M-LA: Lab Assistant Conflict ─────────────────────────────────

@Test
void labAssistantConflict_penalized_concurrentLabs() {
    LabAssistant assistant = new LabAssistant();
    assistant.teacher = TEACHER1;
    assistant.roomTag = "LAB";

    Lesson l1 = lesson(1L, course(1L, "Lab Work", TEACHER1, GROUP1), TS_MON_08, ROOM_LAB);
    Lesson l2 = lesson(2L, course(2L, "Lab Work", TEACHER2, GROUP2), TS_MON_08, ROOM_LAB);

    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::labAssistantConflict)
            .given(l1, l2, assistant)
            .penalizesBy(1);
}

@Test
void labAssistantConflict_notPenalized_differentTimes() {
    // Same lab assistant, different times → no conflict
}

@Test
void labAssistantConflict_notPenalized_differentRooms() {
    // Same time, different rooms → no conflict
}
```

### Phase 4: Add M12 Overflow Tests (20 min)

```java
// ─── M12: Overflow Beyond Target End Date (M) ────────────────────

@Test
void overflowBeyondTarget_penalized_lessonAfterTarget() {
    Course course = course(1L, "Math", TEACHER1, GROUP1);
    course.targetEndDate = LocalDate.of(2025, 10, 31); // Target

    Lesson lesson = lesson(1L, course, TS_TUE_08, ROOM_A); // Nov 7 > Oct 31

    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::overflowBeyondTarget)
            .given(lesson)
            .penalizesBy(7); // 7 days over target = 70 MEDIUM (10 per day)
}

@Test
void overflowBeyondTarget_notPenalized_beforeTarget() {
    // Lesson on or before target → no penalty
}

@Test
void overflowBeyondTarget_notPenalized_pinned() {
    // Pinned lessons are exempt
}
```

---

## Test Templates

### Hard Constraint Template

```java
// ─── HX: [Constraint Name] ─────────────────────────────────────

@Test
void [constraintName]_penalized() {
    // Arrange: Create conflicting scenario
    // Act & Assert: Verify penalty
    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::[constraintMethod])
            .given(/* test data */)
            .penalizesBy(expectedPenalty);
}

@Test
void [constraintName]_notPenalized() {
    // Arrange: Create non-conflicting scenario
    // Act & Assert: Verify no penalty
    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::[constraintMethod])
            .given(/* test data */)
            .penalizesBy(0);
}
```

### Aggregate Constraint Template (H7, H11, M20)

```java
@Test
void [constraintName]_penalized_exceedsLimit() {
    // Arrange: Create entity exceeding limit (e.g., 7 lessons for 6-hour max)
    // Act & Assert: Verify penalty
}

@Test
void [constraintName]_notPenalized_withinLimit() {
    // Arrange: Create entity within limit
    // Act & Assert: Verify no penalty
}

@Test
void [constraintName]_notPenalized_differentDays() {
    // Arrange: Create lessons on different days
    // Act & Assert: Verify no penalty (limits are per-day)
}
```

---

## Verification Checklist

After implementation:

- [ ] All new tests compile without errors
- [ ] `./scripts/core/test.sh` passes (202/202 tests)
- [ ] Each constraint has at least 2 test cases (penalized + notPenalized)
- [ ] Edge cases covered: null values, empty collections, boundary conditions
- [ ] Test names follow convention: `[constraintName]_[scenario]`
- [ ] TestDataBuilder used for test data creation (where applicable)

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| H16b already covered by H16a | Medium | Verify before adding duplicate tests |
| M-LA requires LabAssistant setup | Low | Use existing test data patterns |
| M12 targetEndDate calculation complex | Low | Reuse existing course creation logic |
| Test execution time increase | Low | 4-6 new tests = ~1-2s added |

---

## Dependencies

- **TestDataBuilder:** Already exists, provides `teacher()`, `group()`, `lesson()`, etc.
- **JustificationTest:** Separate test class for justification validation (TASK-244)
- **QuarkusTest:** Provides `@Inject ConstraintVerifier` CDI injection

---

## Success Metrics

- **Test Count:** Target 75-80 tests (from current 71)
- **Coverage:** All hard constraints tested
- **Quality:** All edge cases covered
- **Execution Time:** Target <25s for full test suite

---

## References

- **Timefold Testing Docs:** https://docs.timefold.ai/timefold-solver/latest/testing/
- **ConstraintVerifier API:** https://docs.timefold.ai/timefold-solver/latest constraint-streams/#testing-constraints-with-the-constraintverifier
- **Existing Tests:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java` (71 tests)
- **Test Data Builder:** `src/test/java/com/school/planner/TestDataBuilder.java`
