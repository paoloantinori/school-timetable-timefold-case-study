---
id: task-060
title: Audit script documentation completeness in markdown files
status: Done
assignee:
  - workflow-improvement
created_date: '2026-02-20 20:31'
updated_date: '2026-02-21 14:29'
labels:
  - claude-planned
dependencies: []
priority: medium
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

The `scripts/` directory contains utility scripts for development, benchmarking, validation, and git workflows. The file `claudedocs/scripts.md` is supposed to document these scripts, but we need to verify that all scripts are documented and that the documentation is accurate.

## Files to Audit

**Location:** `/home/user/data/repo/personal/planner/scripts/`

**Scripts found (10 total):**
1. `benchmark.sh` - Single-branch benchmark runner
2. `benchmark-all.sh` - Multi-branch benchmark runner
3. `benchmark-experiment.sh` - Experiment branch comparison
4. `capacity-check.py` - Lab room feasibility validator
5. `tag-audit.py` - Tag matching false positive detector
6. `alternanza-check.py` - Stage pattern hours validator
7. `rebuild-ui.sh` - Safe frontend rebuild with restart
8. `rebuild.sh` - Flexible rebuild with mode selection
9. `check-propagation.sh` - Cross-branch commit audit
10. `sync-docs.sh` - Constraint docs synchronization

**Documentation file:** `/home/user/data/repo/personal/planner/claudedocs/scripts.md`

## Current Documentation Coverage

**Documented in scripts.md (9 scripts):**
1. `rebuild-ui.sh` - Build Scripts section ✓
2. `rebuild.sh` - Build Scripts section ✓
3. `capacity-check.py` - Validation Scripts section ✓
4. `tag-audit.py` - Validation Scripts section ✓
5. `alternanza-check.py` - Validation Scripts section ✓
6. `benchmark.sh` - Benchmark Scripts section ✓
7. `benchmark-all.sh` - Benchmark Scripts section ✓
8. `benchmark-experiment.sh` - Benchmark Scripts section ✓
9. `check-propagation.sh` - Git Workflow Scripts section ✓

**NOT documented in scripts.md (1 script):**
- `sync-docs.sh` - Constraint docs synchronization script (MISSING)

## Issues Found

1. **Missing documentation:** `sync-docs.sh` is not documented in `scripts.md`
   - Purpose: Synchronizes frontend constraint docs with Java constraint provider
   - Options: `--ai` (use Claude for descriptions), `--check` (sync verification only)
   - Files modified: `constraints.ts`, `docs.json` (en/it)

## Verification Checklist

For each documented script, verify:
- [ ] Script name and path are correct
- [ ] Description accurately reflects script purpose
- [ ] Usage examples are correct (flags, arguments)
- [ ] Script still exists and is executable
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given all scripts in scripts/ directory, When comparing to scripts.md documentation, Then identify any undocumented scripts
- [x] #2 Given scripts.md content, When reading each documented script, Then verify description matches actual script behavior
- [x] #3 Given the audit is complete, When undocumented scripts are found, Then add documentation for them
- [x] #4 Given documentation is verified, When checking usage examples, Then ensure command syntax is correct
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. **Add sync-docs.sh to scripts.md**
   - Add new section: "Documentation Scripts" or append to existing section
   - Include: purpose, usage, options, files affected

2. **Suggested addition to scripts.md:**

```markdown
## Documentation Scripts

### `scripts/sync-docs.sh [--ai] [--check]`
Synchronize frontend constraint documentation with Java constraint provider. Parses constraint IDs from SchoolScheduleConstraintProvider.java and compares with frontend TypeScript data files.

```bash
./scripts/sync-docs.sh            # Check sync status only
./scripts/sync-docs.sh --ai       # Use Claude to generate descriptions
./scripts/sync-docs.sh --check    # Exit 1 if out of sync (CI use)
```

Options:
- `--ai` - Use Claude CLI to generate human-readable constraint descriptions
- `--check` - Only check if sync is needed, exit 1 if out of sync

Files affected:
- `src/main/webui/src/data/constraints.ts`
- `src/main/webui/src/i18n/locales/en/docs.json`
- `src/main/webui/src/i18n/locales/it/docs.json`
```
<!-- SECTION:NOTES:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Add missing documentation |
| Risk | 1 | Documentation only |
| Testing | 1 | Review |
| Dependencies | 1 | None |
| **Total** | **1** | 30 minutes |

---

## Priority: **MEDIUM** (Quick Fix)

One script missing documentation (sync-docs.sh). Quick audit fix.

## Completion Summary

**Originally estimated:** 10 scripts, 1 missing doc
**Actually found:** 18 scripts, 5 missing docs

**Scripts added to documentation:**
1. `api.sh` - API queries with JSON parsing
2. `json-check.sh` - JSON validation
3. `podman-tools.sh` - Container management
4. `start-and-wait.sh` - App lifecycle automation
5. `sync-docs.sh` - Constraint docs synchronization

**New sections added:**
- Container Management Scripts
- Documentation Scripts
<!-- SECTION:NOTES:END -->
