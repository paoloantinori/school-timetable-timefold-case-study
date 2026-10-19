# Constraint Justification Pattern

## Overview

Custom constraint justifications provide structured data about constraint violations, enabling rich UI feedback and better debugging. This document explains the pattern for adding justifications to new constraints.

## Architecture

### Three-Layer Design

```
┌─────────────────────────────────────────────────────────────────────┐
│ Layer 1: Constraint Justification (domain/constraints/)              │
│   - Sealed interface with record implementations                   │
│   - Jackson polymorphic serialization (@JsonTypeInfo)               │
│   - Stores entity IDs (not strings) for performance                 │
├─────────────────────────────────────────────────────────────────────┤
│ Layer 2: Constraint Provider (solver/SchoolScheduleConstraintProvider) │
│   - .justifyWith() or .indictWith() calls in constraint streams    │
│   - Creates justification records with entity IDs                   │
├─────────────────────────────────────────────────────────────────────┤
│ Layer 3: Message Formatter (service/ConstraintMessageFormatter)    │
│   - ResourceBundle i18n (messages_en.properties, messages_it.properties) │
│   - Resolves entity IDs → names for human-readable messages       │
│   - Called by REST API endpoint                                   │
└─────────────────────────────────────────────────────────────────────┘
```

## When to Use Justifications

### Use `justifyWith()` For Conflict Constraints

**Best for:** Constraints involving **two entities** conflicting with each other.

- **H1**: Teacher conflict (Lesson vs Lesson, Lesson vs LabBlock, etc.)
- **H2**: Room conflict
- **H3**: Student group conflict

**Pattern:**
```java
.penalize(HardMediumSoftScore.ONE_HARD)
.justifyWith((lesson1, lesson2) ->
    new TeacherConflictJustification(
        lesson1.getTeacher().id,
        lesson1.id,
        lesson2.id
    ));
```

**Why:** Shows both sides of the conflict (which two entities are conflicting).

---

### Use `indictWith()` For Aggregate Constraints

**Best for:** Constraints that track violations at a **group level** rather than per-entity.

- **H7**: Max teacher daily hours (indicts teacher + date, not individual lessons)
- **H11**: Max student group daily hours (indicts group + date)
- **M20**: Sparse day penalty (indicts group + date)

**Pattern:**
```java
.penalize("Teacher {0} has {1} hours on {2} (max 6 allowed)",
    HardMediumSoftScore.ONE_HARD)
.indictWith(teacher, date);
```

**Why:** Prevents Timefold from showing every individual lesson in the UI as a separate violation. Instead, one violation per teacher-date pair.

---

### No Justification Needed For Simple Constraints

**Best for:** Constraints that are self-explanatory or don't involve entity relationships.

- **H4**: Stage blackout (lesson is during internship period)
- **H5**: Teacher unavailability (scheduled during vacation)
- **H6**: School holiday (scheduled on holiday)
- **H14**: Student group start date (scheduled before group start date)

**Pattern:**
```java
.penalize(HardMediumSoftScore.ONE_HARD)
// No justification needed - violation message explains everything
```

---

## Step-by-Step: Adding a New Justification

### Step 1: Create the Justification Record

**File:** `src/main/java/com/school/planner/domain/constraints/YourJustification.java`

```java
package com.school.planner.domain.constraints;

/**
 * Justification for YOUR_CONSTRAINT violations.
 * Stores entity IDs for efficient serialization.
 */
public record YourJustification(
    long entityId,           // Primary entity involved
    LocalDate date,           // When the violation occurred
    int actualValue,          // Actual value (e.g., hours, count)
    int maxValue             // Threshold or limit
) implements Justification {}
```

### Step 2: Register in Sealed Interface

**File:** `src/main/java/com/school/planner/domain/constraints/Justification.java`

```java
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes({
    // ... existing types ...
    @JsonSubTypes.Type(value = YourJustification.class, name = "YourJustification")
})
public sealed interface Justification permits
        TeacherConflictJustification,
        // ... existing types ...
        YourJustification {
}
```

### Step 3: Use in Constraint Stream

**File:** `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java`

```java
import static com.school.planner.domain.constraints.YourJustification.yourJustification;

// In your constraint method:
.penalize(HardMediumSoftScore.ONE_SOFT)
.justifyWith(() -> yourJustification(
    entity.getId(),
    date,
    actual,
    limit
));
```

### Step 4: Add i18n Messages

**File:** `src/main/resources/messages_en.properties`

```properties
# If your justification is used in a penalize() with message template:
constraint.YOUR_ID=Entity {0} has {1} on {2} (max {3})

# For constraint stream match messages:
constraint.YOUR_ID.description=Your constraint description
```

**File:** `src/main/resources/messages_it.properties`

```properties
constraint.YOUR_ID=L'entità {0} ha {1} il {2} (max {3})
constraint.YOUR_ID.description=Descrizione del vincolo
```

### Step 5: Add Frontend Type (Optional)

**File:** `src/main/webui/src/types/domain.ts`

```typescript
/** YOUR_CONSTRAINT justification */
export interface YourJustification extends Justification {
  '@type': 'YourJustification';
  entityId: number;
  date: string;
  actualValue: number;
  maxValue: number;
}

// Update AnyJustification union type:
export type AnyJustification =
  | TeacherConflictJustification
  | // ... existing types ...
  | YourJustification;
```

### Step 6: Update Frontend Rendering (Optional)

**File:** `src/main/webui/src/utils/justificationUtils.ts`

```typescript
export function renderJustification(justification: AnyJustification): string {
  switch (justification['@type']) {
    // ... existing cases ...
    case 'YourJustification':
      return `Entity ${justification.entityId}: ${justification.actualValue}/${justification.maxValue} on ${justification.date}`;
    default:
      const unknown = justification as Record<string, unknown>;
      console.warn('[Justification] Unknown type:', unknown['@type'], unknown);
      return `[Unknown: ${unknown['@type']}]`;
  }
}
```

---

## Performance Considerations

### Why Store IDs, Not Names?

1. **Solver hot-path performance**: Creating string objects during solving is expensive
2. **Memory efficiency**: Entity IDs are primitives (long = 8 bytes), names are strings
3. **Translation support**: IDs can be resolved to localized names on demand

### When NOT to Use Justifications

| Scenario | Reason |
|----------|--------|
| Very high-volume violations (10,000+) | Serialization overhead may exceed benefit |
| Temporary debugging | Use `ConstraintMatch` inspection instead |
| Self-evident violations | Message alone is sufficient |

---

## Examples

### Example 1: Teacher Conflict (H1)

**Justification:**
```java
public record TeacherConflictJustification(
    long teacherId,
    long lesson1Id,
    long lesson2Id
) implements Justification {}
```

**Constraint:**
```java
.penalize(HardMediumSoftScore.ONE_HARD)
.justifyWith(TeacherConflictJustification::
    new);
```

**i18n:**
```properties
constraint.H1=Teacher {0} is double-booked: {1} at {2} with {3} and {4} at {5} with {6}
```

---

### Example 2: Daily Hours Exceeded (H7)

**Justification:**
```java
public record TeacherDailyHoursJustification(
    long teacherId,
    LocalDate date,
    int lessonCount,
    int maxHours,
    int excessHours
) implements Justification {}
```

**Constraint:**
```java
.penalize("Teacher {0} has {1} hours on {2} (max 6 allowed)",
    HardMediumSoftScore.ONE_HARD)
.indictWith(teacher, date);
```

**Note:** H7 uses `indictWith()` because the violation is at the (teacher, date) level, not per-lesson.

---

## Testing

### Unit Tests with ConstraintVerifier

**File:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

```java
@Test
void yourConstraintWithJustification() {
    tester.verifyThat(YOUR_CONSTRAINT)
        .givenSummary()
        .penalizesBy(1);

    // Verify justification structure
    YourJustification justification = tester.getConstraintVerifier()
        .getJustificationList()
        .stream()
        .filter(YourJustification.class::isInstance)
        .map(YourJustification.class::cast)
        .findFirst()
        .orElseThrow();

    assertThat(justification.entityId()).isEqualTo(expectedId);
}
```

---

## Checklist

Before committing a new justification:

- [ ] Record implements `Justification` interface
- [ ] Added to `Justification` sealed interface permits list
- [ ] Added `@JsonSubTypes.Type` entry with unique name
- [ ] Used in constraint stream (`.justifyWith()` or `.indictWith()`)
- [ ] Added i18n messages to `messages_en.properties` and `messages_it.properties`
- [ ] Added TypeScript type to `domain.ts`
- [ ] Added rendering case to `justificationUtils.ts`
- [ ] Added `ConstraintVerifier` test
- [ ] Verified JSON serialization produces `@type` discriminator

---

## References

- **Timefold Docs**: https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/constraint-streams#customizing-the-output-of-a-constraint-stream
- **Jackson Polymorphism**: https://fasterxml.github.io/jackson-databind/docs/javadoc-2.13/com/fasterxml/jackson/annotation/JsonTypeInfo.html
- **ConstraintVerifier**: https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/constraint-streams#testing-constraints-with-the-constraintverifier
