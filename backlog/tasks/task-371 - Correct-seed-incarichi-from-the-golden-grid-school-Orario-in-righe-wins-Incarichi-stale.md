---
id: TASK-371
title: >-
  Correct seed incarichi from the golden grid (school: 'Orario in righe wins,
  Incarichi stale')
status: Done
assignee: []
created_date: '2026-07-17 09:50'
updated_date: '2026-07-17 13:46'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-8
dependencies: []
priority: high
ordinal: 195000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: referente's answer to Q3 (17/07): for the FC-DUALE mismatches, 'Orario in righe' is authoritative and 'Incarichi in riga' was NOT updated. Our seed courses (src/main/resources/seed/incarichi.csv) carry the stale incarichi hours, so course totalHours for the affected triples are wrong and the reconciliation dashboard shows false mismatches.

WHAT:
1. For every (teacher, subject, group) where golden scheduled hours != incarichi assigned hours AND the subject is not pseudo (Stage/Esame/RA) — the known set from analysis.md section 6: T03/T22 Laboratorio Termoidraulico swap (grid: T03 135, T22 100), Taglio Metalli G04 (grid 20), T03 RA/Sicurezza deltas, T10 Tecnologia Meccanica (grid 20), T15 Cad/Lavorazioni/Controllo numerico (grid values incl. Cad G13 30, Tecniche di saldatura G13 40, Lavorazioni G04 152/G13 30), T13 Orientamento G12 (grid 0 -> REMOVE row), T14 deltas — derive the corrected hours MECHANICALLY from claudedocs/spalmone-2026-27/data/lessons.csv (count real-teacher rows per triple) and rewrite the affected rows in BOTH copies of incarichi.csv. Drop the stale G04 RA pool row (Q7: only T15's 50h exist). Keep the G08 RA pool row (40h, still a real manual pool per Q9).
2. Ripple: DataGeneratorSeedTest expected counts/totals; GoldenScheduleImportDryRunTest categories (unmatchedNoCourse etc. may shift); reconciliation golden spot-check expectations in TASK-353's notes; design doc section C numbers (1040 anomaly gone once the pool row is dropped).
3. Byte-sync test keeps both copies aligned; document the correction provenance (referente 17/07, Q3) in seed-csv-format.md inline.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Affected incarichi rows corrected mechanically from the golden grid; both CSV copies aligned; provenance documented
- [x] #2 All dependent tests updated and green; dry-run categories recomputed, not hand-tuned
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Seed incarichi corrected mechanically from the golden grid (derive-golden-incarichi-corrections.py, 0 residual mismatches; 14 corrected + 2 real drops + 1 stale RA pool drop). Per-group residuals documented honestly (G04 +240 = DUALE stage without incarichi row, cross-referenced with the design doc's -16 incl-stage metric). code-review high combined with 374: fixes E1-E9 applied. DoD complete (simplify + review high + orchestrator exit-code verification). Tests 514/514.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
