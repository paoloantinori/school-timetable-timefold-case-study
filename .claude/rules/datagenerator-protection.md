# DataGenerator Protection Rule

**NEVER modify `bootstrap/DataGenerator.java` without explicit user permission.**

## Why?

This file contains real school data (teachers, groups, courses, hours) as provided by the school. It represents the authoritative source of truth for the scheduling domain.

## Known Data Discrepancies

The discrepancies once flagged against the validated CSV (spalmone.csv) are **both RESOLVED** on the
current seed (DataGenerator is built from `incarichi.csv`, which TASK-371 normalized to the golden).
The live authority is the mechanical diff script, not this table:

```
python3 scripts/analyze/derive-golden-incarichi-corrections.py   # (docente, materia, gruppo) -> 0 divergenze
```

| Group | Old flag (DataGenerator vs CSV) | Current status | Resolution |
|-------|--------------------------------|----------------|------------|
| G09 | 666h vs 640h (+26h, "Pending verification") | RESOLVED (2026-07-31, TASK-466) | 666h = 666h on the current seed; 640h was a prior sheet. |
| T01/T28 "Servizio tecnico e stilistico" | 244h vs 192h (+52h, "IMPOSSIBLE") | RESOLVED (2026-07-31, TASK-466) | 231h = 231h (G02 60, G06 60, G10 65, G12 46); the old 244/192 figures were a prior mismatch, not an impossible placement. |

Full evidence and reproducibility: `claudedocs/spalmone-2026-27/task466-incarichi-vs-golden-diff-2026-07-31.md`.
The one residual numeric delta (G08 pool "Recuperi ed Approfondimenti" 40h vs 41h, a 1h sheet
rounding) is a referente-notification item, not a seed error: `claudedocs/spalmone-2026-27/referente-notifica-divergenze-input-vs-golden-2026-07-31.md`.

## What To Do Instead

If you suspect a data discrepancy:

1. Run verification: `python3 scripts/verify-datagenerator.py`
2. Report findings to the user
3. **Wait for confirmation** before making any changes
4. Document any approved changes in MEMORY.md

## Rationale

- School data changes require human verification
- Some discrepancies may be intentional (e.g., curriculum updates)
- Incorrect data breaks solver feasibility (e.g., T28's impossible 64h requirement)
