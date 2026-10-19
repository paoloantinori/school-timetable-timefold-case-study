---
id: m-5
title: "Fix-and-Optimize Matheuristic"
---

## Description

The strongest untried architectural idea: the ITC-2019-winning fix-and-optimize matheuristic (pin most of the solution, re-optimize small neighborhoods, adapt and diversify), implementable on Timefold Community via the pinning API. Rationale: claudedocs/research/critical-assessment-and-roadmap-20260712.md section 4.

Tasks: TASK-333 (design doc - USER REVIEW GATE before implementation), TASK-334 (implement FixAndOptimizeService), TASK-335 (benchmark vs long single run - milestone gate), TASK-336 (rolling-horizon fallback - conditional, only if 335 misses target).

**Exit criteria:** TASK-335 verdict recorded: fix-and-optimize either becomes the recommended production mode (documented in CLAUDE.md) or is rejected with numbers; if rejected, TASK-336 evaluates the rolling-horizon fallback.
