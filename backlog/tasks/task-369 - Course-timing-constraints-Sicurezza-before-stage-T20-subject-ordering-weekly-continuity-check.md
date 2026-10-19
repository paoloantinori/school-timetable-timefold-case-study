---
id: TASK-369
title: >-
  Course-timing constraints: Sicurezza before stage, T20 subject ordering,
  weekly continuity check
status: Done
assignee: []
created_date: '2026-07-17 09:03'
updated_date: '2026-07-18 02:21'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies: []
priority: medium
ordinal: 193000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: the 'Vincoli in parole' docx states three course-timing rules not in our model: (1) T03: ALL 'Sicurezza' hours of a class BEFORE that class's stage start; (2) T20: Merceologia + Arredi Tessili start immediately, Tecnologia tessile + Storia della moda start later, Figurino/Consulenza d'immagine/Sviluppo Sostenibile anytime; (3) general: possibilmente >=1h/week per subject outside stage windows (continuità didattica).

WHAT:
1. Subject-before-stage constraint (MEDIUM, weight to calibrate): penalize Sicurezza lessons dated after the group's StagePattern start (groups without stage: no-op). Generic implementation (subject name configurable — a small SubjectTimingRule concept or a constant list; keep it simple: start with a constant for Sicurezza, note the generalization).
2. T20 ordering as SOFT constraints: 'start immediately' = reward early-period lessons / penalize first occurrence after (say) September for the named subjects; 'start later' = penalize occurrences in the first N weeks. Simplest robust shape: penalize when the subject's FIRST lesson date falls outside its preferred window (per-subject window table). Calibrate against the golden solution (T20's actual start dates — derive from lessons.csv and pick windows the golden satisfies).
3. Weekly continuity: read S9's implementation; if it already rewards weekly spread, document the match in the constraint table; if not, add a SOFT 'subject taught every non-stage week' reward and calibrate against golden (the golden was hand-built with this principle).
4. Tests for each; CLAUDE.md constraint table entries.
Ref: claudedocs/spalmone-2026-27/vincoli-in-parole.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Sicurezza-before-stage implemented + tested; golden shows near-zero violations (T03 already did this by hand)
- [x] #2 T20 windows implemented, calibrated so the golden scores clean
- [x] #3 Continuity clause mapped to S9 or implemented; constraint table updated
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
M26 (Sicurezza before stage, inclusive boundary, golden 0/0), M27 (T20 start windows derived from golden with margin: subito<=w3, tardi>=w4; golden 0/0), S12 (weekly subject presence reward, +5,623 golden; explicitly NOT a S9 restoration — opposite bound, documented). Guard pins added; Python cross-checks in derive-golden-availability-checks.py; frontend docs en+it. In-agent DoD (simplify + review high: NPE guard, inclusive-boundary fix, 3 doc corrections). Orchestrator verification 577/577 exit 0.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
