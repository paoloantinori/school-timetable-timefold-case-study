---
id: TASK-087
title: Add UI page explaining problem complexity
status: Done
assignee: []
created_date: '2026-02-23 14:54'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - documentation
  - education
  - claude-planned
dependencies: []
priority: low
ordinal: 128000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create an educational/documented page in the UI that explains why timetable scheduling is computationally hard, using real-world analogies and numbers from our specific problem.

## User Story
As a school administrator, I want to understand why automatic scheduling is complex, so I appreciate the solver's work and set realistic expectations.

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Create new page at route `/complexity` linked from DocsPage
- [ ] #2 #2 Fetch problem metrics dynamically via API:
  - Number of lessons
  - Number of timeslots
  - Number of rooms
  - Number of teachers
  - Number of student groups
  - Number of constraints (hard/medium/soft count)
- [ ] #3 #3 Display search space calculation:
  - Theoretical combinations: lessons × timeslots × rooms
  - Comparison to atoms in observable universe (~10^82)
  - Why brute force would take longer than universe age
- [ ] #4 #4 Include real-world comparisons:
  - "More combinations than chess games ever played"
  - "Equivalent to X billion Sudoku puzzles"
- [ ] #5 #5 Add simple visualization (optional: animated search space exploration)
- [ ] #6 #6 Mobile responsive (works on tablet and phone)
- [ ] #7 #7 i18n support: English and Italian
- [ ] #8 #8 Accessible: screen reader friendly, sufficient color contrast

## Edge Cases
- Handle missing metrics gracefully (show "loading" or "N/A")
- Large numbers should be formatted with locale-aware separators
- Dark mode support

## Technical Implementation
- Create `src/main/webui/src/components/ComplexityPage.tsx`
- Add API endpoint `GET /api/schedule/metrics` if not exists (or reuse existing)
- Add translations to `i18n/locales/en/complexity.json` and `it/complexity.json`
- Add route in `App.tsx`

## Dependencies
- None (can use existing schedule data)

## Risks
- Numbers may change with different seed data — ensure they're fetched dynamically

## Definition of Done
- Page renders correctly on desktop and mobile
- All text translated (EN/IT)
- Passes accessibility audit (Lighthouse score > 90)
<!-- SECTION:DESCRIPTION:END -->
<!-- AC:END -->



## Definition of Done
- Page renders correctly on desktop and mobile
- All text translated (EN/IT)
- Passes accessibility audit (Lighthouse score > 90)
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->
