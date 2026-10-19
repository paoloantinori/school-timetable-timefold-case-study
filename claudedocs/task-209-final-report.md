# TASK-209: Group-Level Hour Discrepancy Analysis - Final Report

**Date**: 2026-03-09
**Status**: Phase 1 Complete | Awaiting User Decision on G13

## Executive Summary

Comprehensive analysis of **13 student groups** (G14 excluded per user request) revealed **no critical (>50%) discrepancies**. After implementing parser aliases and excluding non-curriculum activities, the total discrepancy is **-323 hours (-3.0%)**.

## Final Results (G14 Excluded)

| Group | DataGenerator | spalmone.csv | Diff | % | Status |
|-------|---------------|--------------|------|---|--------|
| G13 | 683h | 583h | -100h | -14.6% | ⚠ WARNING |
| G08 | 666h | 619h | -47h | -7.1% | ~ MINOR |
| G10 | 654h | 634h | -20h | -3.1% | ~ MINOR |
| G02 | 990h | 960h | -30h | -3.0% | ~ MINOR |
| G06 | 990h | 960h | -30h | -3.0% | ~ MINOR |
| G09 | 666h | 646h | -20h | -3.0% | ~ MINOR |
| G05 | 990h | 962h | -28h | -2.8% | ~ MINOR |
| G04 | 990h | 967h | -23h | -2.3% | ~ MINOR |
| G01 | 990h | 970h | -20h | -2.0% | ~ MINOR |
| G11 | 671h | 660h | -11h | -1.6% | ~ MINOR |
| G12 | 657h | 663h | +6h | +0.9% | ~ MINOR |
| G03 | 990h | 990h | 0h | 0.0% | ✓ OK |
| G07 | 695h | 695h | 0h | 0.0% | ✓ OK |
| **TOTAL** | **10,632h** | **10,309h** | **-323h** | **-3.0%** | |

## G14 Excluded

Per user guidance, G14 has been excluded from this analysis:
- **Independent micro-course** with manual date selection
- **Not subject to main compaction rules**
- **Only needs to avoid** lab/teacher conflicts with main planning
- Will be reintroduced later when more details are available

## G13 Discrepancy: RESOLVED

The **-100 hour discrepancy** for G13 is definitively explained. After comprehensive teacher workload analysis:

### Teacher Workload Analysis Results

| Teacher | DataGenerator | spalmone.csv | Diff | Status |
|---------|---------------|--------------|------|--------|
| **T27** | 100h | **0h** | **-100h** | ❌ NO CSV MATCH |
| T20 | 96h | 96h | 0h | ✓ (includes Servizi vendita) |
| T18 | 105h | 105h | 0h | ✓ |
| T02 | 100h | 100h | 0h | ✓ |
| All others | 282h | 282h | 0h | ✓ |

### Servizi vendita Courses: Definitive Answer

| Course | DataGenerator | spalmone.csv | Status |
|--------|---------------|--------------|--------|
| Servizi vendita (A) - T20 | 76h | **76h as "Vendita e servizi"** | ✓ MATCH (different name) |
| **Servizi vendita (B) - T27** | **100h** | **0h (not found under any name)** | ❌ MISSING |
| **Total** | **176h** | **76h** | **-100h** |

**Analysis performed:**
1. ✅ Checked all G13 teachers' workload in CSV (11 teachers)
2. ✅ Searched for "Servizi vendita" under different names (vend, serv, comm, cli)
3. ✅ Confirmed T20's 76h appears as "Vendita e servizi" in CSV
4. ✅ Confirmed T27's 100h does NOT appear in CSV under any name or subject

### All Other G13 Subjects Match Perfectly

| Subject | DG | CSV | Match? |
|---------|-----|-----|--------|
| Comunicazione | 70h | 70h | ✓ |
| Comunicazione con il cliente | 60h | 60h | ✓ |
| Gestione contabile | 70h | 70h | ✓ |
| Informatica | 45h | 45h | ✓ |
| Lingua inglese | 40h | 40h | ✓ |
| Matematica | 30h | 30h | ✓ |
| Orientamento | 20h | 20h | ✓ |
| Personalizzazione (A+B) | 60h | 60h | ✓ |
| Preparazione all'alternanza | 5h | 5h | ✓ |
| Scienze | 55h | 55h | ✓ |
| Sicurezza | 12h | 12h | ✓ |
| Storia | 40h | 40h | ✓ |

### Definitive Conclusion

**The spalmone.csv is correct and authoritative.** The CSV contains exactly 76h of Servizi vendita taught by T20. The 100h "Servizi vendita (B)" course taught by T27 in DataGenerator **does not exist** in the validated schedule.

### Recommendation for DataGenerator

**Action**: Remove "Servizi vendita (B)" from DataGenerator.java

```java
// REMOVE THIS LINE:
addCourse(teachers, groups, "T27", "Servizi vendita (B)", "G13", 100);
```

**Rationale**:
- spalmone.csv is the validated gold standard (confirmed by user)
- T27 has 0 hours assigned to G13 in the CSV
- No other teacher has 100h that could be reattributed to T27
- The course appears to be an error in DataGenerator, not in CSV

## Improvements Implemented

### 1. Parser Aliases Added (30+ mappings)

Subject name aliases to handle CSV-to-DataGenerator mapping:
- FA: Lavaggio→Laboratorio acconciatura, Cosmetologia→Cosmetica, etc.
- MODA: Arredi tessili→Arredamento, Merceologia→Mercatistica, etc.
- FC: Disegno meccanico→Disegno edile, Lavorazioni meccaniche d'officina→Officina meccanica
- G13: vendser→Vendita e servizi, prepalt→Preparazione all'alternanza
- G14: off→Officina meccanica, lab termo→Laboratorio termoidraulico

### 2. Non-Curriculum Subjects Excluded

Excluded from comparison (not solver-scheduled):
- **azienda**: Stage activities (handled by configurator, H4 constraint)
- **stage**: PCTO alternating school activities
- **esame**: Exam periods
- **comp**: Componenti pratici

## Key Corrections

1. ✅ **G11 discrepancy corrected**: Only 1 hour difference (not 1,040)
2. ✅ **Parser aliases added**: Handles subject name variations
3. ✅ **Non-curriculum excluded**: azienda, stage, esame, comp
4. ✅ **G14 excluded**: Independent micro-course, not subject to main rules

## Scripts Created

1. **scripts/analyze-group-discrepancies.py** — Group-level comparison (G14 excluded)
2. **scripts/g13-detailed-analysis.py** — G13 detailed breakdown
3. **scripts/detailed-subject-breakdown.py** — Per-subject analysis

## Acceptance Criteria Status

- [x] #1 Parse spalmone.csv and generate group-by-group hour totals
- [x] #2 Calculate DataGenerator totals for all groups
- [x] #3 Generate comprehensive discrepancy report with % difference
- [x] #4 Identify groups with >50% discrepancy for detailed analysis
- [x] #5 Create per-subject breakdown for major discrepancy groups
- [x] **#5a G13 detailed analysis**: Completed - identified missing Servizi vendita (B) 100h
- [x] **#5b Teacher workload analysis**: Completed - confirmed 100h does NOT exist in CSV under any name
- [ ] #6 Update DataGenerator with corrections (**READY FOR USER APPROVAL**)
  - Remove: `addCourse(teachers, groups, "T27", "Servizi vendita (B)", "G13", 100);`
  - Result: G13 683h → 583h (matches CSV)
- [ ] #7 Verify solver can schedule all lessons without late-year gaps
- [ ] #8 Document all changes in MEMORY.md

## Next Steps

### Awaiting User Confirmation

**Analysis complete.** Ready to proceed with DataGenerator update.

**Proposed action**: Remove "Servizi vendita (B)" from DataGenerator.java
- File: `src/main/java/com/school/planner/bootstrap/DataGenerator.java`
- Line to remove: `addCourse(teachers, groups, "T27", "Servizi vendita (B)", "G13", 100);`
- Expected result: G13 total reduces from 683h → 583h, matching spalmone.csv

### After Confirmation

1. Update DataGenerator.java (remove T27's Servizi vendita (B) course)
2. Rebuild and restart: `./scripts/rebuild-quick.sh`
3. Regenerate seed data: `yes | ./planner.sh delete <profile> && ./planner.sh start <profile>`
4. Verify lesson counts: `python3 scripts/verify-datagenerator.py`
5. Test solver: `./scripts/api/api.sh solve` then `./scripts/api/api.sh score`
6. Document change in MEMORY.md
