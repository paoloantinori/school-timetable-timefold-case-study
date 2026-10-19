---
id: TASK-158
title: Evaluate Incremental Score Calculator for top constraints
status: Done
assignee: []
created_date: '2026-03-03 20:48'
updated_date: '2026-08-04 01:29'
labels:
  - claude-planned
  - performance
  - solver
  - research
milestone: Solver Performance
dependencies: []
references:
  - claudedocs/research-timefold-optimizations-2026-03-03.md
  - 'src/main/java/com/school/planner/solver/BaseConstraintProvider.java:129-221'
documentation:
  - 'https://blog.dotsandlines.ai/17x-faster-timefold-part1-f6d54d2a4094'
  - >-
    https://blog.dotsandlines.ai/how-we-made-our-clients-timefold-solver-over-17x-faster-074524485b39
  - >-
    https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance
priority: low
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Investigate replacing Constraint Streams with an Incremental Score Calculator for the most expensive constraints. Dots & Lines achieved 17x speedup on Timefold's course timetabling example using this approach.

**The strategy (from Dots & Lines blog):**
1. Implement BOTH Constraint Streams and Incremental in parallel
2. Use Constraint Streams for testing and score explanation
3. Use Incremental for production solving
4. Validate correctness by comparing scores from both implementations

**Phase 1 (POC) — Convert top 3 most expensive constraints:**
- H1 (Teacher conflict): L-L, L-B, B-B — 3 constraints
- H2 (Room conflict): L-L, L-B, B-B — 3 constraints
- H3 (Student group conflict): L-L, L-B, B-B — 3 constraints

These 9 conflict constraints are evaluated on every move and involve the most join/filter operations.

**Key optimization techniques from the blog:**
- Use arrays instead of HashMaps for frequency tracking (28-40x faster for basic ops)
- Pre-index entities by teacher ID, room ID, student group ID
- Delta-based updates: on `beforeVariableChanged`/`afterVariableChanged`, only update affected indices

**Assessment:**
- Our problem has ~30 constraints across 2 entity types (Lesson + LabBlock)
- Full conversion is a major engineering effort
- Starting with the 9 conflict constraints (H1-H3) provides the best cost/benefit ratio
- If POC shows significant speedup (>3x), consider converting remaining constraints

**This is a large research+implementation task. Start with benchmarking to quantify how much time is spent in H1-H3 vs other constraints.**
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Benchmark profiling identifies % of score calculation time in H1-H3 vs other constraints
- [ ] #2 POC IncrementalScoreCalculator implements H1.1-H1.3, H2.1-H2.3, H3.1-H3.3
- [ ] #3 Remaining constraints still use Constraint Streams (hybrid approach)
- [ ] #4 Score comparison validates identical results between Incremental and Streams
- [ ] #5 Benchmark shows measurable speedup (target: >3x on move evaluation speed)
- [ ] #6 Decision document: proceed with full conversion or stay with Constraint Streams
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
VERDICT-158 (2026-08-04, read-only): DIFFERIRE. Non procedere ora. (1) Piano POC stale: il task pianifica 9 vincoli (H1.1-H3.3, split L-L/L-B/B-B) ma oggi esistono 3 vincoli singoli su 1 entita' (LabFollower/LabBlock rimossi TASK-228/267); anche il research doc sez 5/6a e' stale ('2 entity types', H1.2/H2.2/occupiesTimeslot). (2) H1/H2/H3 sono gia' ottimizzati con joiner indicizzati Joiners.equal(key)+equal(LESSON_DATE)+lessThan(id), non sono O(n^2); il commento H1 L336-341 e' esplicito. (3) Il 17x del blog Dots&Lines (letto direttamente) e' sul toy course-timetabling a 8 vincoli, NON real-world; si scompone in ~4x Streams->Incremental x ~4x trick array (Part 2); costo 1 mese/sviluppatore per 20 vincoli + 4x maintenance permanente. Gate mancante: AC#1 (profilare % score-calc in H1-H3) non fatto. Stay with Constraint Streams; riprendere solo se profilazione mostra H1-H3 dominanti E search-config esaurito. Doc: claudedocs/spalmone-2026-27/task158-results/VERDICT-158-2026-08-04.md
<!-- SECTION:FINAL_SUMMARY:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
