# Research: Compactness Constraint Design for School Timetabling

**Date**: 2026-03-21
**Question**: Should we add a direct "0 wasted hours" constraint, or is the current decomposed approach (H17 + M20 + M22) sufficient?

## Executive Summary

The current three-constraint decomposition (H17 hard, M22 hard, M20 medium@1000) is well-designed and aligns with best practices from the timetabling research community. No additional constraint is needed. The "wasted hours" diagnostic metric is valuable as a monitoring tool, not as a solver objective.

## Evidence Base

### 1. ITC-2007 CB-CTT: The Gold Standard

The International Timetabling Competition 2007 (Curriculum-Based Course Timetabling) defines the standard model. Its soft constraints include:

| Constraint | Weight | Relevance |
|-----------|--------|-----------|
| Room Capacity | 1 | Not relevant |
| Minimum Working Days | 5 | Related to M22 (calendar span) |
| **Curriculum Compactness** | **2** | Per-curriculum adjacency — analogous to our H17 |
| Room Stability | 1 | Not relevant |

**Key finding**: Curriculum compactness (avoiding gaps) is a **SOFT** constraint with weight 2 — far less aggressive than our M20 at weight 1000. The ITC-2007 model prioritizes feasibility over compactness.

Source: [ITC-2007 Track 3 definition](https://icaps07-satellite.icaps-conference.org/workshop4/paper06.pdf), [UniTime solver](https://www.unitime.org/itc2007/)

### 2. Brazilian High School Timetabling: Compactness Formulations Matter

Dorneles et al. (2012) explicitly studied **"The Impact of compactness requirements on the resolution of high school timetabling problems"** (SBPO 2012). Two compactness requirements:

- **Minimize working days** (teacher schedule)
- **Avoid idle timeslots** (teacher schedule)

Key findings:
- Different formulations of idle times constraints **significantly impact solver performance**
- A novel formulation outperformed previous approaches
- Compactness requirements can make the problem harder to solve if too strict

Saviniec et al. (2020) extended this with pattern-based formulations, confirming that compactness formulation design matters more than weight.

Source: [MDPI Comparative Study](https://www.mdpi.com/1999-4893/8/3/723), [Graham Kendall survey](https://www.graham-kendall.com/papers/tgks2021.pdf)

### 3. Timefold's Constraint Hierarchy Design

Timefold's three-tier score system (hard/medium/soft) is explicitly designed for decomposed constraints:

- **Hard**: "Breaking hard constraints results in infeasible plans"
- **Medium**: "Help manage plans when resources are limited" — incentivizes without blocking
- **Soft**: "Help optimize plans based on business goals"

From Timefold's modeling guide:
> "By limiting the value range... you are effectively creating a built-in hard constraint. This... can also take away the freedom of the optimization algorithms to **temporarily break that constraint in order to escape from a local optimum**."

This directly supports M20 being medium: it allows the solver to temporarily create sparse days during local search, which helps escape local optima.

Source: [Timefold Constraints Overview](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/overview), [Timefold Modeling Guide](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems)

### 4. Timefold on Score Traps

Timefold's performance docs warn about "score traps":
> "If the blue process moves from an overloaded computer to an empty computer, the hard score should improve. The trapped score implementation fails to do that."

Score traps occur when the solver can't improve because moving one entity doesn't improve the score. **Multiple granular constraints help avoid score traps** because each provides its own gradient. A single "wasted hours" constraint would provide less gradient information than three separate ones.

Source: [Timefold Performance Tips](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance)

### 5. Decomposition vs Aggregation

The ITC-2007 model uses **separate, granular constraints** (room capacity, min working days, curriculum compactness, room stability) rather than a single aggregate score. This is the dominant pattern in timetabling research:

- Each constraint addresses a **specific failure mode**
- The solver gets more granular gradient information during local search
- The weighted sum allows the decision-maker to express relative importance
- Individual constraint violations are easier to diagnose and explain

## Analysis of Our Current Setup

### Constraint Decomposition

| Constraint | Tier | Weight | Failure Mode Addressed |
|-----------|------|--------|------------------------|
| H17 (Vertical compact) | Hard | -1 per gap | Holes within a day (08:00, 09:00, _11:00_) |
| M22 (Calendar compact) | Hard | -1 per empty day | Completely empty day inside calendar span |
| M20 (Daily quota fill) | Medium | -1000 per missing hour | Day with 1-3 hours instead of 5-6 |

### Why This Works

If all three are satisfied:
1. M22 forces contiguous calendar (no gaps between used days)
2. H17 forces each used day filled top-down (no intra-day holes)
3. M20 forces each used day to meet quota (not sparse)

Result: every day has exactly `maxHoursPerDay` lessons, except the last day which can have 0 to `maxHoursPerDay - 1` hours. The "wasted hours" = `totalHours mod maxHoursPerDay` ∈ [0, 5].

### Why M20 Should Stay Medium

1. **Local search freedom**: During VND/local search, the solver may need to temporarily create a sparse day to escape a local optimum. Hard M20 would block this.

2. **Feasibility**: If teacher/room availability makes it impossible to fill a day to quota (e.g., 3 teachers unavailable), hard M20 would make the problem infeasible. Medium M20 accepts the sparse day with a penalty.

3. **Weight 1000 is effectively "soft hard"**: With HardMediumSoftScore, a -5000 medium penalty (5 sparse hours) is always preferred over -1 hard (conflict). So M20 violations are extremely costly but not blocking.

4. **Last-day remainder is unavoidable**: If totalHours=992 and quota=6, the last day has 2 hours. M20 penalizes this by 4×1000=4000 medium, which the solver correctly accepts as the cost of completing the schedule.

## Recommendations

### 1. Keep the current decomposition (H17 + M20 + M22)

**Rationale**: Three granular constraints provide better solver gradients than a single aggregate. The decomposition aligns with ITC-2007 best practices and Timefold's recommended use of the medium tier.

### 2. Do NOT add a direct "wasted hours" constraint

**Rationale**:
- Redundant with existing constraints (double-counting risk)
- Less granular — loses failure-mode distinction
- Increases score calculation cost
- The diagnostic metric (slotUtilization endpoint) already provides monitoring

### 3. Keep the diagnostic metric as a post-hoc analysis tool

**Rationale**: The "132 wasted slots" metric is valuable for:
- Identifying which groups need attention after a solve
- Monitoring solver convergence (should decrease over time)
- Validating that the three constraints are working as expected
- Benchmarking different solver configurations

### 4. Consider M20 weight tuning only if empirical results show persistent violations

**Rationale**: If after a full solve, groups still show > 5 wasted hours, the issue is likely data feasibility (teacher/room conflicts), not constraint design. Increasing M20 weight beyond 1000 won't help if the problem is infeasible — it would just slow down the solver. Instead, investigate the specific blocking constraints.

## Sources

1. Di Gaspero & Schaerf (2007). "Curriculum-based Course Timetabling (Track 3)". ITC-2007.
2. Dorneles, de Araújo & Buriol (2012). "The Impact of compactness requirements on the resolution of high school timetabling problem." SBPO 2012.
3. Saviniec et al. (2020). "Pattern-based formulations for Brazilian high school timetabling."
4. Timefold Solver Documentation. "Constraints and Score: Overview." v2.0.0-beta-1.
5. Timefold Solver Documentation. "Modeling Planning Problems." Latest.
6. Timefold Solver Documentation. "Performance Tips and Tricks." Latest.
7. Feutrill & Müller (2011). "Lower Bounds for the ITC-2007 Curriculum-Based Course Timetabling."
8. MDPI (2015). "A Comparative Study of Modern Heuristics on the School Timetabling Problem."
