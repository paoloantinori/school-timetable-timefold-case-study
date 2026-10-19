# Ideal End Date API

## Endpoint

**GET `/api/completion`** — Get completion statistics for all student groups

## Response Format

```json
{
  "groups": [
    {
      "groupName": "G11",
      "totalCurriculumHours": 648,
      "stageDaysCount": 37,
      "hoursBeforeStage": 130,
      "hoursDuringStage": 0,
      "hoursAfterStage": 102,
      "actualDaysWithLessons": 53,
      "actualEndDate": "2026-06-09",
      "idealEndDate": "2026-05-04",
      "daysDifference": 36
    }
  ],
  "totalGroups": 19
}
```

## Key Fields

| Field | Meaning |
|-------|---------|
| `idealEndDate` | **Target date** when group should finish all curriculum |
| `actualEndDate` | Date of last scheduled lesson |
| `daysDifference` | How late the group finished (positive = late) |

## Per-Group Endpoint

**GET `/api/completion/{groupName}`** — Get stats for specific group
URL-encode spaces: `/api/completion/3%20FB`

## Java Implementation

- **Resource**: `StudentGroupCompletionResource.java`
- **Service**: `StudentGroupCompletionService.java`
- **Calculator**: `TargetEndDateCalculator.computeGroupTargetDate()`

## How to Query (without curl)

The `api.sh` script doesn't have a generic GET command. Use the completion API via:

```bash
# Source the fetch function and call directly
source scripts/api/api.sh
fetch "/api/completion" "GET" | python3 -c "import sys,json; data=json.load(sys.stdin); print(json.dumps([g for g in data['groups'] if 'FB' in g['groupName']], indent=2))"
```

Or add a helper script to `scripts/api/` for completion queries.

## Ideal End Dates (Current Database)

| Group | idealEndDate | maxHoursPerDay |
|-------|--------------|----------------|
| G03 | 2026-06-08 | null |
| G07 | 2026-03-18 | 5 |
| **G11** | **2026-05-04** | **5** |
| G01 | 2026-04-30 | null |
| G05 | 2026-05-05 | null |
| G09 | 2026-05-14 | null |
| G02 | 2026-06-08 | null |
| G06 | 2026-04-21 | 5 |
| G10 | 2026-05-05 | 5 |
| G12 | 2026-05-26 | null |

## Critical Rule for Pre-Assignment

**Never place lessons after `idealEndDate`** — this is a hard requirement for school scheduling.

The pre-assign phase must filter out any timeslot dates that occur after the group's `idealEndDate`.
