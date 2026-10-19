---
id: TASK-262
title: 'Auto-Tuning Agent System - Phase 6: Claude Code Agent Skills'
status: Done
assignee: []
created_date: '2026-03-17 05:29'
updated_date: '2026-03-17 08:01'
labels:
  - autotune
  - agents
  - claude-code
dependencies: []
documentation:
  - CLAUDE.md
  - .claude/rules/
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create Claude Code agent skills for "improve solver" command and related operations.

**Tasks:**
1. Create `.claude/skills/autotune/improve-solver.md` - Main agent for "improve solver" command
2. Create `.claude/skills/autotune/autotune-experiment.md` - Agent for running experiments
3. Create `.claude/skills/autotune/autotune-analyze.md` - Agent for analyzing results
4. Create `.claude/skills/autotune/autotune-apply.md` - Agent for applying recommendations
5. Create skill orchestration workflow for multi-agent coordination

**Acceptance Criteria:**
- [ ] "improve solver" triggers full optimization pipeline
- [ ] Each agent skill has clear trigger patterns and scope
- [ ] Agents coordinate properly (experiment → analyze → recommend → apply)
- [ ] Skills include progress reporting and error handling
- [ ] User can interrupt at any stage with approval checkpoints

**Key Files:**
- `.claude/skills/autotune/improve-solver.md`
- `.claude/skills/autotune/autotune-experiment.md`
- `.claude/skills/autotune/autotune-analyze.md`
- `.claude/skills/autotune/apply.md`

**Dependencies:** Phase 5 (Recommendation Engine)
<!-- SECTION:DESCRIPTION:END -->
