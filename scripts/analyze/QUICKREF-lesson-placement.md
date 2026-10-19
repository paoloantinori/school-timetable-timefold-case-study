# Quick Reference: Lesson Placement Analysis

## One-Liner Summary

Checks if lessons are placed after student groups' ideal end dates (overflow).

## Quick Start

```bash
# 1. Start database (if not running)
./planner.sh start <profile>

# 2. Run analysis
python3 scripts/analyze/lesson-placement-analysis.py
```

## Prerequisites

```bash
pip install psycopg2-binary
```

## What to Look For

### ✅ Good Output
```
Group           Total    Assigned  Before  After
G01          666      666       666     0
```
- All lessons assigned before ideal end date
- No overflow

### ⚠️ Bad Output
```
Group           Total    Assigned  Before  After
G01          666      666       651     15  ⚠️
```
- 15 lessons placed after ideal end date
- M12 constraint too weak or insufficient capacity

## Common Findings

| Finding | Meaning | Action |
|---------|---------|--------|
| `After > 0` | Lessons after ideal end date | Increase M12 weight or check capacity |
| `Unassigned > 0` | Lessons not scheduled | Wait for solver or check feasibility |
| `Valid Slots < Total` | Impossible to fit all lessons | Extend ideal end date or reduce hours |

## Next Steps (If Issues Found)

1. **Check capacity**: Compare `Valid Slots` vs `Total`
2. **Check constraints**: `./scripts/api/constraint-violations.sh M12`
3. **View overflow details**: See script output's "DETAILED OVERFLOW ANALYSIS" section
4. **Adjust solver**: Increase M12 weight or extend ideal end date

## Full Documentation

See `README-lesson-placement-analysis.md` for detailed usage and troubleshooting.
