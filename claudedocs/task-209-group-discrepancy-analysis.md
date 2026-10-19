# TASK-209: Group-Level Hour Discrepancy Analysis

**Date**: 2026-03-09
**Status**: In Progress

## Summary

Comprehensive analysis of all student groups reveals **no critical (>50%) discrepancies**. The 1,040-hour difference mentioned in the initial plan for G11 was incorrect — the actual difference is only **1 hour** (671 vs 672).

## Key Findings

### Overall Statistics
- **Total Groups Analyzed**: 14
- **DataGenerator Total**: 10,812 hours
- **spalmone.csv Total**: 11,397 hours
- **Difference**: +585 hours (+5.4%)

### Discrepancy Levels

| Status | Count | Groups |
|--------|-------|--------|
| ✓ OK (0% diff) | 5 | G03, G02, G01, G07, G06 |
| ~ MINOR (<10%) | 3 | G11, G05, G04 |
| ⚠ WARNING (>10%) | 5 | G10, G09, G12, G08, G14 |
| ❌ CRITICAL (>50%) | 0 | — |

### Warning Groups (>10% discrepancy)

| Group | DataGenerator | spalmone.csv | Diff | % |
|-------|---------------|--------------|------|---|
| G14 | 180h | 132h | -48h | -26.7% |
| G10 | 654h | 812h | +158h | +24.2% |
| G09 | 666h | 826h | +160h | +24.0% |
| G12 | 657h | 800h | +143h | +21.8% |
| G08 | 666h | 791h | +125h | +18.8% |

## Root Cause Analysis

### 1. "Stage" Subject in spalmone.csv (NOT "azienda")

The spalmone.csv includes a "Stage" subject (148h for G10, 148h for G09, 127h for G12, 150h for G08).

**This is NOT the "azienda" stage activities** that are excluded from the solver via H4 constraint. This represents:
- **PCTO** (Percorsi per le Competenze Trasversali e l'Orientamento)
- Alternating school/work activities
- Scheduled as regular curriculum in the human solution

**Question for user**: Should the solver schedule these PCTO "Stage" hours, or are they managed externally?

### 2. Subject Name Mismatches

Same content, different names in DataGenerator vs spalmone.csv:

| DataGenerator | spalmone.csv | Hours Affected |
|---------------|--------------|----------------|
| Lavaggio | Laboratorio acconciatura | 46h (G10) |
| Cosmetologia | Cosmetica | 35h (G10) |
| Contabilità | Contabilita | 15h (G10), 54h (G12) |
| Servizio tecnico e stilistico (A/B) | Servizi tecnici | 126h (G10) |
| Consulenza d'immagine | Immagine | 45h (G10) |
| Relazione con il cliente | Clienti | 40h (G10) |
| Storia | Storia ed educazione civica | 30h (G09) |
| Comportamento sociale | Scienze sociali | 20h (G09) |
| Arredi tessili | Arredamento | 10h (G09) |
| Preparazione all'alternanza | Progettazione visiva | 70h (G12) |

### 3. Additional CSV Subjects

spalmone.csv includes subjects not defined in DataGenerator:
- **Stage**: PCTO activities (varying hours per group)
- **Esame**: Exam periods (10-12h per group)
- **Componenti pratici**: Practical components (20h per group)

## Recommendations

### Option A: Align DataGenerator with spalmone.csv (Authoritative)

If spalmone.csv is the "validated gold standard":
1. Add "Stage" courses to DataGenerator for affected groups
2. Standardize subject names (Cosmetica not Cosmetologia, etc.)
3. Add missing subjects (Esame, Componenti pratici)

**Impact**: ~585 hours need to be added/updated

### Option B: Exclude PCTO/Stage from Solver

If PCTO/Stage should remain external:
1. Exclude "Stage" from CSV comparison (treat like "azienda")
2. Only align core curriculum subjects
3. Document that PCTO is managed separately

**Impact**: ~148h per group excluded, reduces discrepancies to ~10%

### Option C: Hybrid Approach

1. Add PCTO/Stage as non-scheduled metadata in DataGenerator
2. Align subject names for core curriculum
3. Keep Esame/Componenti pratici as optional

**Impact**: Moderate changes, preserves separation

## Questions for User

1. **PCTO "Stage"**: Should the solver schedule the "Stage" subject found in spalmone.csv, or is this managed externally?

2. **Subject names**: Should we standardize names (Cosmetica → Cosmetologia, etc.) in DataGenerator or in the CSV parser?

3. **Esame periods**: Should exam periods be scheduled by the solver, or are they outside the regular curriculum?

4. **Authoritative source**: Is spalmone.csv the complete curriculum (including PCTO), or just the regular lesson schedule?

## Next Steps

Await user decision on:
- How to handle "Stage" subject (PCTO activities)
- Whether to standardize subject names
- Scope of solver scheduling (core curriculum vs. all activities)

After clarification, update DataGenerator.java and/or analysis scripts accordingly.
