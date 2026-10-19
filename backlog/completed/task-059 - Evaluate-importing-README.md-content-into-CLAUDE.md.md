---
id: task-059
title: Evaluate importing README.md content into CLAUDE.md
status: Done
assignee: []
created_date: '2026-02-20 20:31'
updated_date: '2026-02-21 08:39'
labels:
  - claude-planned
dependencies: []
priority: low
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

CLAUDE.md currently contains detailed project-specific instructions for the Planner project, while README.md contains general project overview, setup instructions, and architecture diagrams. There is potential overlap between these files, and the question is whether README.md content should be imported/merged into CLAUDE.md to provide Claude with more comprehensive project context.

## Decision: Selective Extraction (NOT wholesale import)

After analysis, the recommendation is **NOT to wholesale import README.md**, but to **extract specific high-value content** that's missing from CLAUDE.md.

### What was extracted and added to CLAUDE.md:

1. **Production Deployment Commands** - `java -jar target/quarkus-app/quarkus-run.jar` with environment variable configuration (QUARKUS_DATASOURCE_*)
2. **Seed Data Volumes** - Quantities for context: 28 teachers, 14 student groups, ~1,520 timeslots, 33 holidays, 70+ courses, ~7-8.7k lessons

### What was NOT imported (and why):

| Content | Reason to Skip |
|---------|----------------|
| Screenshots | Visual, not useful for AI |
| Prerequisites (Java 17+, etc.) | Inferable from pom.xml |
| Getting Started narrative | Claude doesn't need hand-holding |
| UI workflow (pin, drag-drop) | User features, not dev-focused |
| REST API table | Already documented in @rest-api.md |
| Constraint overview | Already documented in CLAUDE.md |

## Rationale

The two files serve different audiences:
- **README.md** = Human developers (setup, screenshots, getting started)
- **CLAUDE.md** = Claude (detailed paths, workflows, conventions)

Wholesale importing would bloat CLAUDE.md without adding proportional value. Selective extraction of specific operational details (deployment commands, data volumes) provides the missing pieces without the noise.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given CLAUDE.md and README.md exist, When comparing their content, Then identify overlap vs unique content
- [x] #2 Given the analysis is complete, When evaluating import value, Then decide whether to import, extract, or leave as-is
- [x] #3 Given a decision is made, When documenting the outcome, Then update this task with the final recommendation
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Completed: 2026-02-21

**Outcome:** Selective extraction, not wholesale import.

**Changes made to CLAUDE.md:**
1. Added "Production Deployment" section with JAR execution and env vars
2. Added "Seed Data Volumes" subsection with quantities

**Files modified:**
- `/home/user/data/repo/personal/planner/CLAUDE.md`

**Conclusion:** Files remain separate with targeted additions to CLAUDE.md. Task complete.
<!-- SECTION:NOTES:END -->
