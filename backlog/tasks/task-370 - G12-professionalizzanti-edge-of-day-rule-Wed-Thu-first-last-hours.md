---
id: TASK-370
title: G12 professionalizzanti edge-of-day rule (Wed/Thu first/last hours)
status: Done
assignee: []
created_date: '2026-07-17 09:03'
updated_date: '2026-07-18 02:55'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies: []
priority: medium
ordinal: 194000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: the 'Vincoli in parole' docx gives the precise G12 rule: professionalizzanti only on Friday, OR on Wed/Thu ONLY at the day's edges — first hour, last hour, first two, or last two; if hour 2 is prof so must hour 1; if hour 5 is prof so must hour 6. Our TASK-343 seed encodes a coarser Thu+Fri blackout mask (and the docx ALLOWS Wednesday, which our mask forbids).

WHAT:
1. Revise G12's StagePattern/professionalizzanti mask: allow Wed as well per the docx (verify against the golden data: does the golden place G12 prof lessons on Wed? derive from lessons.csv using the professionalizzanti subject set).
2. New constraint (HARD, G12-scoped initially but implement group-generic with a rule table): professionalizzanti lessons on the restricted weekdays must satisfy the edge conditions: the set of prof slot-indexes that day must be a prefix of {0,1} or a suffix of {4,5}... careful — G12's Wed/Thu calendar has open slots 0-4 (from GROUP_CALENDAR_PATTERNS) so 'last hour' = the day's LAST OPEN slot per the group calendar, not literal slot 5: anchor the rule on GroupCalendarRule's effective range. Implement: prof lessons must occupy a contiguous run touching the first or last open slot, max length 2.
3. ConstraintVerifier tests: prefix-of-2 ok, suffix-of-2 ok, middle-hour violation, hour-2-without-hour-1 violation; golden measurement (expect near-zero — the school built it by hand).
Ref: claudedocs/spalmone-2026-27/vincoli-in-parole.md; golden-audit.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Rule implemented anchored on the group's effective open range; tests cover all four docx cases
- [x] #2 Golden measurement recorded; G12 Wed allowance reconciled with the seed mask
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
H22 shipped: group-generic edge-of-day rule (one table row: G12 Wed+Thu; Friday free), contiguous run <=2 anchored on the effective calendar window (Thu is 0-7, corrected in-agent), lab-excluded, day-level all-pinned exemption. Golden Wed prof usage mechanically confirmed; mask reconciliation REFUSED with reasoning (would inflate H4 by ~dozens on 56 real non-prof Wed hours) — documented in DataGenerator comment + CLAUDE.md. Golden pin 0/0 structural. In-agent DoD (2 correctness catches: Thu window, lab exclusion). Orchestrator verification 591/591 exit 0.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
