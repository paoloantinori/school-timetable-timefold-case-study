# DayOfWeek JSON Deserialization Fix

## Problem
When creating LabRotations via REST API with `dayOfWeek: 3`, lessons were being placed on **Thursday** instead of **Wednesday**.

## Root Cause
Jackson deserializes integer values to Java enums using **ordinal** (0-based index), not ISO value (1-based index).

| Day | Jackson Ordinal | ISO Value | Database Value |
|-----|-----------------|-----------|----------------|
| Monday | 0 | 1 | 1 |
| Tuesday | 1 | 2 | 2 |
| **Wednesday** | **2** | **3** | 2 |
| **Thursday** | **3** | **4** | 3 |
| Friday | 4 | 5 | 4 |

When sending `dayOfWeek: 3` in JSON:
- Jackson deserializes as `DayOfWeek.THURSDAY` (ordinal 3)
- Hibernate persists as ordinal 3
- Code uses `date.getDayOfWeek() != day` which compares ISO values

## Solution
Use **Jackson ordinal values** when creating LabRotations via JSON:

```json
{
  "dayOfWeek": 2  // WEDNESDAY (ordinal 2), NOT ISO value 3
}
```

## Correct Mapping

| Desired Day | JSON value |
|-------------|------------|
| Monday | 0 |
| Tuesday | 1 |
| Wednesday | 2 |
| Thursday | 3 |
| Friday | 4 |

## Example: Creating Wednesday LabRotation

```bash
cat > /tmp/labrotation.json << 'EOF'
{
  "teacherId": 6,
  "studentGroupId": 3,
  "dayOfWeek": 2,  // WEDNESDAY (ordinal)
  "slotStart": 0,
  "blockSize": 2,
  "periodStart": "2026-03-16",
  "periodEnd": "2026-05-04",
  "roomTag": "informatica"
}
EOF

cat /tmp/labrotation.json | ./scripts/api/api.sh post "/api/lab-rotations"
```

## Verification
After creation, verify the dayOfWeek is correctly set:

```bash
# API returns "dayOfWeek": "WEDNESDAY"
# Database stores dayofweek = 2 (ordinal)
```

## Related Files
- `LabRotationResource.java` - REST endpoint that deserializes LabRotationRequest
- `LabRotation.java` - Entity with `DayOfWeek dayOfWeek` field (no @Enumerated annotation)
- `HybridPhase1aService.java` - Loads LabRotations and uses `session.labRotation.dayOfWeek` for placement

## Long-term Fix Consideration
Consider adding a custom Jackson deserializer for `DayOfWeek` that uses `DayOfWeek.of(int)` instead of ordinal-based deserialization. This would allow sending ISO values (1-7) instead of ordinals (0-6).

Example:
```java
@JsonDeserialize(using = DayOfWeekDeserializer.class)
public DayOfWeek dayOfWeek;

public static class DayOfWeekDeserializer extends JsonDeserializer<DayOfWeek> {
    @Override
    public DayOfWeek deserialize(JsonParser p, DeserializationContext ctxt) {
        return DayOfWeek.of(p.getIntValue());  // Uses ISO value (1-7)
    }
}
```
