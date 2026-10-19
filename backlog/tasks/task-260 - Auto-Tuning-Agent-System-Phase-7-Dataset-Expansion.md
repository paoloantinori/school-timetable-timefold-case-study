---
id: TASK-260
title: 'Auto-Tuning Agent System - Phase 7: Dataset Expansion'
status: Done
assignee: []
created_date: '2026-03-17 05:29'
updated_date: '2026-03-17 06:43'
labels:
  - autotune
  - datasets
  - benchmark
dependencies: []
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Expand benchmark dataset catalog with diverse test cases for robust tuning.

**Tasks:**
1. Create additional benchmark datasets (small, medium, large, production-scale)
2. Ensure dataset variety (different constraint profiles, entity counts)
3. Create `DatasetCatalog.java` - Registry of available datasets
4. Create `DatasetSplitter.java` - Stratified split for train/validation/test

**Acceptance Criteria:**
- [ ] 4+ datasets: tiny (2 lessons), small (10), medium (50), large (200+)
- [ ] Datasets vary in constraint difficulty (easy, medium, hard profiles)
- [ ] DatasetCatalog provides metadata (entity count, timeslot count, difficulty)
- [ ] DatasetSplitter creates stratified splits preserving difficulty distribution
- [ ] Production-scale dataset reflects real school data

**Key Files:**
- `src/test/java/com/school/planner/benchmark/DatasetCatalog.java`
- `src/test/resources/benchmarks/` - Dataset directory

**Dependencies:** Phase 1 (Configuration Space Definition)
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
**Implementation Complete (2026-03-17)**

- Created 3 Java components: DatasetCatalog, DatasetSplitter, DatasetBuilders

- 10 datasets across 5 size tiers (tiny: 2-10, small: 10-50, medium: 50-200, large: 200-1000, production: 1000+)

- 3 difficulty levels (easy, medium, hard) per size tier

- Stratified split by size and difficulty to ensure representative train/val/test sets

- K-fold cross-validation support (k=2 to n)

- Fixed domain model alignment: Timeslot uses LocalDateTime, StudentGroup uses size field

- TeacherAvailability uses startDateTime/endDateTime pattern
<!-- SECTION:FINAL_SUMMARY:END -->
