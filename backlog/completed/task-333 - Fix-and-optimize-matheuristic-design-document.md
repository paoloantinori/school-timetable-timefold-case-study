---
id: TASK-333
title: 'Fix-and-optimize matheuristic: design document'
status: Done
assignee: []
created_date: '2026-07-12 17:47'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - design
milestone: m-5
dependencies:
  - TASK-321
references:
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
  - backlog/drafts
priority: high
ordinal: 157000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The winning approach of the ITC-2019 timetabling competition (Mikkelsen and Holm, Journal of Scheduling 2022) is a fix-and-optimize matheuristic: repeatedly FIX most of the solution and re-optimize a small free subset, adapting subset size and diversifying on stagnation. This is implementable on Timefold Community edition with the pinning API plus repeated short solves, it sidesteps the single-run score-trap problem (each sub-solve is a small, tractable problem), and it is philosophically the generalization of this projects pre-assignment pipeline. This is the strongest untried architectural idea. See roadmap doc section 4. Related: DRAFT-003 (embed pre-assignment as custom phase) touches the same machinery - reference it.

WHAT (design only, no production code): write claudedocs/design/fix-and-optimize.md specifying:
1. Outer loop: start from the best available full solution (post-normal-solve). Repeat: select a neighborhood, set pinned=true on ALL lessons except the neighborhood, run a short solve (60-120s termination), accept the result iff the standardized scorecard improves (define the comparison: conflict-hard first, then medium, then soft), restore pins to their original semantic state (lab pins stay pinned ALWAYS - they are school requirements, never unpin them).
2. Neighborhood selectors, at minimum: (a) one student group entire year; (b) one calendar week all groups; (c) one teacher entire year; (d) worst-day - the (group, date) pairs with most violations from the analysis endpoint. Selection strategy: round-robin with bias toward neighborhoods containing violations.
3. Adaptivity: if K consecutive neighborhoods yield no improvement, grow neighborhood size (2 groups, 2 weeks); on further stagnation, random-restart the selector order (diversification).
4. Stop conditions: target scorecard reached (from TASK-321 success criterion), or N rounds without improvement, or wall-clock budget.
5. Integration: REST endpoints (POST /api/schedule/fix-and-optimize with budget parameter, GET status), reuse SolverManager sequentially (never concurrent solves), persistence via existing ScheduleService transaction patterns, progress into PhaseProgressionService, pin manipulation must be transactional and MUST restore correctly on failure (crash-safety section required).
6. Risks section: pin-restore correctness, interaction with H0 (unassigned penalty) during sub-solves, CH re-initialization cost per sub-solve (should be near-zero since only the neighborhood is unpinned and already initialized - verify assumption against Timefold warm-start docs), DB write amplification.
Include pseudocode for the outer loop and the neighborhood selectors. The document will be reviewed by the user before TASK-334 implements it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Design doc exists at claudedocs/design/fix-and-optimize.md covering all 6 sections plus pseudocode
- [ ] #2 Lab-pin invariant (never unpin school-required lab lessons) explicitly specified
- [ ] #3 Crash-safety / pin-restore strategy specified
- [ ] #4 Explicitly marked as awaiting user review before implementation
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
ASSORBITO da TASK-478 ExactFinisher (ratificato DEFAULT ON 2026-08-02): il pattern fix-and-optimize della letteratura matheuristics e' in produzione come completamento esatto del residuo boxed post-repair, 0-hard-additive con improve-or-noop revert. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
