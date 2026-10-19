---
id: TASK-206
title: 'Option B: Activate H11 and strengthen M12/M13 constraints'
status: To Do
assignee: []
created_date: '2026-03-09 06:44'
updated_date: '2026-07-25 05:55'
labels:
  - constraint
  - compaction
  - medium-constraint
  - tuning
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Option B: Activate H11 and Strengthen M12/M13 Constraints

Less invasive than Option A - work with existing constraint framework.

### Implementation

1. **Activate H11: Max Student Group Daily Hours**
   - Set `maxHoursPerDay = 6` on all StudentGroup entities in DataGenerator
   - Constraint currently inactive (line 169: `if group.maxHoursPerDay == null return false`)
   - After activation, groups cannot exceed 6 hours/day (hard constraint)
   - Prevents front-loading like G09's 586 hours before stage

2. **Strengthen M13: Vertically Compact**
   - Current weight: 500 medium
   - Try increasing to: 1000-2000 medium
   - M13 penalizes gaps between consecutive days with lessons for same group
   - **Goal**: Reduce gaps that push completion dates late

3. **Strengthen M12: Horizontal Compact (Overflow)**
   - Current weight: 1 medium per lesson × days beyond target
   - May need significant increase OR convert to Hard constraint
   - Or implement per-group version instead of aggregate

4. **Review M17: Lab Recurring Pattern**
   - Currently exists for labs
   - Consider extending pattern to all courses (not just labs)

### Pros

- Uses existing constraint framework
- Less invasive than new H17 constraint
- Can tune weights without code changes
- H11 activation is trivial (just set maxHoursPerDay)

### Cons

- M12/M13 are aggregate (whole schedule), not per-group
- May not fully solve "118 days late" problem for G09
- Weight tuning is trial-and-error
- Medium constraints dominated by hard during solving

### Expected Outcome

- H11 prevents >6 hours/day (already exists, just needs activation)
- Stronger M13 reduces gaps between days
- May reduce lateness but unlikely to achieve 118-day improvement for G09
- Might need to combine with Option A for full solution

### Verification

**Use completion API to track improvement:**
```bash
# Baseline (current state)
GET /api/completion/3%20MODA
# Expected: daysDifference = 118 (late)

# After H11 activation
GET /api/completion/3%20MODA
# Should see: more even distribution, less front-loading

# After M12/M13 strengthening
GET /api/completion/3%20MODA
# Should see: daysDifference reduced from 118
```

### Risks

- May not be sufficient alone - 118 days is a huge gap
- Medium constraints still dominated by hard during solving
- Weight tuning requires iterative benchmarking
- User expects on-time completion; this option may not deliver

### Recommendation

**Consider hybrid approach:**
1. Activate H11 (low risk, high benefit)
2. Use `/api/completion` to measure improvement
3. If still insufficient, add H17 (Option A) for enforcement

## References

- **Completion API**: `GET /api/completion/{groupName}` - for verification
- **Constraint Provider**: `SchoolScheduleConstraintProvider.java`
- **H11 Implementation**: lines 169-189
- **M12 Implementation**: lines 145-175
- **M13 Implementation**: lines 191-207
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): tutte e tre le azioni gia' realizzate per altra via: H11 attiva e hard (provider :321-352, seed TASK-343), M13 promossa a H17 (TASK-227), M12 fact-based (TASK-394).
<!-- SECTION:NOTES:END -->
