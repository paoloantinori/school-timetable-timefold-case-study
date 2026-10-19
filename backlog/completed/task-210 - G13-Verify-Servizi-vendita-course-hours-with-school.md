---
id: TASK-210
title: 'G13: Verify Servizi vendita course hours with school'
status: Done
assignee: []
created_date: '2026-03-09 14:23'
updated_date: '2026-08-14 10:17'
labels:
  - data-verification
  - g13
  - school-confirmation
dependencies: []
documentation:
  - claudedocs/task-209-final-report.md
  - scripts/g13-teacher-workload.py
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

TASK-209 analysis revealed a 100-hour discrepancy for G13 group:
- DataGenerator had: Servizi vendita (A) 76h + (B) 100h = 176h
- spalmone.csv has: Vendita e servizi 76h only
- Removed: "Servizi vendita (B)" from DataGenerator (T27, 100h)

## Verification Questions for School

1. **Course structure**: Does G13 have ONE "Servizi vendita" course (76h) or TWO courses (A + B, 176h total)?

2. **Teacher assignment**: Who teaches Servizi vendita?
   - Current spalmone.csv: T20 (76h)
   - Previous DataGenerator: T20 (76h for A) + T27 (100h for B)
   - Is T27 supposed to teach this course?

3. **Course duration**: Should Servizi vendita be 76h or 176h for G13?

## Resolution Options

**If school confirms 76h is correct:**
- ✅ DataGenerator fix is complete
- Update G13 course documentation

**If school confirms 176h is correct:**
- Investigate why spalmone.csv is missing 100h
- Possible CSV data issue (not captured)
- Re-add Servizi vendita (B) to DataGenerator
- Update spalmone.csv with missing entries

## Files Modified

- `src/main/java/com/school/planner/bootstrap/DataGenerator.java` (line 466 removed)
  - Removed: `addCourse(teachers, groups, "T27", "Servizi vendita (B)", "G13", 100);`
  - G13 total: 683h → 583h

## Reference

See `claudedocs/task-209-final-report.md` for full analysis.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 School confirms correct Servizi vendita hours (76h or 176h)
- [ ] #2 School confirms teacher assignment (T20 only or T20 + T27)
- [ ] #3 If 176h correct: Identify why spalmone.csv is missing 100h
- [ ] #4 Update DataGenerator and/or documentation based on school response
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Code Change Status (2026-03-10)

**Technical Work: ✅ COMPLETE**

The 100-hour "Servizi vendita (B)" course taught by T27 was removed from DataGenerator.

- Committed: `1951217` (2026-03-09) - "feat(data): Remove T27 course from G13 curriculum"

- G13 total: 683h → 583h (now matches spalmone.csv)

**Remaining: School Verification**

The task remains open pending confirmation from the school that 76h is correct.

See claudedocs/task-209-final-report.md for full analysis.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RISOLTO COL GOLDEN COME ORACOLO (direttiva 2026-07-31, Fedora 2026-08-14): NE' 76h NE 176h. Il golden colloca ZERO ore di qualunque materia "vendita" per G13 (grep "vend" sull'intero golden = 0 occorrenze; T20 nel golden insegna solo materie MODA, 524h totali, zero ore G13). Il quadro G13 golden (978 ore: Stage 295, Lavorazioni meccaniche 140, Comunicazione 70, Personalizzazione 60, Economia e Diritto 60, Competenze Digitali 55, Metrologia 50, Scienze 45, Tecnologia 42, Saldatura 40, Matematica 30, Cad 30, Orientamento 20, Inglese 20, Sicurezza 16, Preparazione all'alternanza 5, escluse 8 Fasullo trappola) combacia subject-by-sub subject col seed incarichi corrente (verifica 1:1 del 14/08; il diff TASK-466 conferma 683=683 aula, 0 divergenze). La rimozione del corso T27 (B) 100h era corretta E anche le 76h "Vendita e servizi (T20)" del vecchio spalmone.csv non sono mai state praticate dalla scuola: il seed attuale (nessun corso vendita) e' gia' quello giusto. Nessuna domanda alla scuola necessaria: il golden e' autoritativo sui numeri e il seed e' gia' allineato (nessuna divergenza da notificare a referente). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
