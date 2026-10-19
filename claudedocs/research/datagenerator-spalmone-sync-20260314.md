# DataGenerator Sync with Spalmone.csv - 2026-03-14

## Summary

All course hour values in `DataGenerator.java` have been updated to match `spalmone.csv` exactly. The source file now contains the validated spalmone hours.

**Status:** ✅ DataGenerator.java updated with spalmone values

## Changes Made

### 1. MODA Groups - Updated to Match Spalmone

#### G01 (Target: 356h)
| Subject | Teacher | Old | New | Source | Match? |
|---------|---------|-----|-----|--------|--------|
| Confezione | T16 | 142 | **130** | DataGenerator:776 | ✅ |
| Informatica | T06 | 50 | **49** | DataGenerator:769 | ✅ |
| Modello | T17 | 78 | **77** | DataGenerator:774 | ✅ |
| Moulage | T17 | 50 | **48** | DataGenerator:775 | ✅ |
| Tecnologia tessile (A) | T20 | 24 | **23** | DataGenerator:777 | ✅ |
| Tecnologia tessile (B) | T07 | 31 | **23** | DataGenerator:778 | ✅ |

#### G05 (Target: 370h)
| Subject | Teacher | Old | New | Source | Match? |
|---------|---------|-----|-----|--------|--------|
| Confezione | T16 | 120 | **118** | DataGenerator:798 | ✅ |
| Modello | T17 | 80 | **79** | DataGenerator:800 | ✅ |
| Tecnologia tessile (B) | T07 | 24 | **23** | DataGenerator:804 | ✅ |

#### G09 (Target: 216h)
| Subject | Teacher | Old | New | Source | Match? |
|---------|---------|-----|-----|--------|--------|
| Confezione | T16 | 90 | **76** | DataGenerator:817 | ✅ |
| Informatica | T06 | 30 | **27** | DataGenerator:832 | ✅ |
| Tecnologia tessile (T20) | T20 | 15 | **13** | DataGenerator:816 (duplicate removed, value corrected) | ✅ |

### 2. FA Groups - T28 Courses Updated

| Group | Teacher | Old | New | Source | Match? |
|-------|---------|-----|-----|--------|--------|
| G02 | T28 | 60 | **50** | DataGenerator:616 | ✅ |
| G06 | T28 | 60 | **48** | DataGenerator:638 | ✅ |
| G10 | T28 | 32 | **45** | DataGenerator:660 | ✅ |

### 3. FB Thermal Labs - Updated

| Group | Teacher | Old | New | Source | Match? |
|-------|---------|-----|-----|--------|--------|
| G11 | T31 | 70 | **59** | DataGenerator:593 | ✅ |

## FB Thermal Labs - Already Matched (No Changes Needed)

| Group | Subject | Hours | Spalmone | Status |
|-------|---------|-------|----------|--------|
| G03 | Lab T03 (A) | 141 | 141 | ✅ Perfect match |
| G03 | Lab termoidraulico (B) | 141 | 141 | ✅ Perfect match |
| G07 | Lab termoidraulico | 24 | 24 | ✅ Perfect match |

## Database Re-seeding Instructions

To apply the updated DataGenerator values to a fresh database:

```bash
# 1. Kill all running processes
pkill -9 -f quarkus; pkill -9 -f java

# 2. Remove the database volume
podman volume rm planner-data-dev

# 3. Start fresh (will trigger DataGenerator on empty database)
./scripts/start-and-wait.sh dev

# 4. Verify hours match
./scripts/db-query.sh "SELECT sg.name, c.subject, t.name, c.totalHours
FROM course c JOIN StudentGroup sg ON c.studentGroup_id = sg.id
JOIN teacher t ON c.teacher_id = t.id
WHERE sg.name LIKE '%MODA%'
ORDER BY sg.name, c.subject"
```

## Verification Checklist

After database re-seeding, verify:

- [ ] G01 Confezione = 130h (not 142h)
- [ ] G01 Informatica = 49h (not 50h)
- [ ] G01 Tecnologia tessile (A) = 23h (not 24h)
- [ ] G01 Tecnologia tessile (B) = 23h (not 31h)
- [ ] G05 Confezione = 118h (not 120h)
- [ ] G05 Modello = 79h (not 80h)
- [ ] G05 Tecnologia tessile (B) = 23h (not 24h)
- [ ] G09 Confezione = 76h (not 90h)
- [ ] G09 Informatica = 27h (not 30h)
- [ ] G09 Tecnologia tessile = 13h (not 15h)
- [ ] G02 T28 = 50h (not 60h)
- [ ] G06 T28 = 48h (not 60h)
- [ ] G10 T28 = 45h (not 32h)
- [ ] G11 T31 thermal lab = 59h (not 70h)

## Source File Verification

To verify the source file has correct values:

```bash
# Check G01 Confezione
grep "T16.*Confezenie.*G01" src/main/java/com/school/planner/bootstrap/DataGenerator.java
# Should show: addCourse(teachers, groups, "T16", "Confezione", "G01", 130);

# Check G02 T28
grep "T28.*Servizio.*G02" src/main/java/com/school/planner/bootstrap/DataGenerator.java
# Should show: addCourse(teachers, groups, "T28", "Servizio tecnico e stilistico (A)", "G02", 50);

# Check G11 thermal lab
grep "T31.*Laboratorio termoidraulico.*G11" src/main/java/com/school/planner/bootstrap/DataGenerator.java
# Should show: addCourse(teachers, groups, "T31", "Laboratorio termoidraulico", "G11", 59);
```

## Notes

- **File Location:** `src/main/java/com/school/planner/bootstrap/DataGenerator.java`
- **Spalmone Source:** `scripts/data/spalmone.csv`
- **Reference Comparison:** `claudedocs/research/hours-comparison-datagenerator-vs-spalmone-20260314.md`

## Open Issues

There appears to be a caching or build issue where the database is not reflecting the updated DataGenerator values even after a clean rebuild. The source file contains the correct spalmone values, but the database persists with the old values.

**Potential causes:**
1. Quarkus dev mode hot-reload caching old classes
2. Build artifact caching in target/
3. Database initialization happening before code changes are picked up

**Workaround:** Use production JAR mode instead of dev mode, or manually update database values via SQL while investigating the build issue.

## Next Steps

1. Investigate why database values don't match source code
2. Consider creating a verification script to auto-validate hours match spalmone
3. Add unit tests to verify DataGenerator creates courses with correct hours
