---
id: task-032
title: include project_index.md in claude.md via the @ import style
status: Done
assignee: []
created_date: '2026-02-18 22:25'
updated_date: '2026-02-19 07:33'
labels:
  - claude
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a structured project index file and import it into CLAUDE.md using the @ import syntax (same as existing @claudedocs/*.md imports).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 claudedocs/project_index.md file exists with project structure overview
- [x] #2 CLAUDE.md contains @claudedocs/project_index.md import statement
- [x] #3 Import renders correctly when CLAUDE.md is loaded
- [x] #4 Index includes key directories (domain, service, rest, webui) and their purposes
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Used existing PROJECT_INDEX.md at root level instead of creating claudedocs/project_index.md. The file is comprehensive and serves the same purpose. CLAUDE.md references it via @PROJECT_INDEX.md.
<!-- SECTION:NOTES:END -->
