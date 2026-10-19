# Research: Determining Feasibility When Semantically-Hard Constraints Are Implemented as Soft

**Date**: 2026-03-07
**Context**: School schedule optimization - compaction (student group gap minimization) is semantically hard but implemented as soft (S5 weight=500)

---

## Executive Summary

When semantically-hard constraints are implemented as soft constraints, traditional score-based feasibility detection (0hard = feasible) breaks down. The literature offers several approaches:

| Approach | Description | Applicability |
|----------|-------------|---------------|
| **Hierarchical CSP** | Multiple constraint levels with lexicographic ordering | High - already using hard/medium/soft |
| **Partial CSP (PCSP)** | Accept solutions within a distance metric from ideal | Medium - requires defining acceptable violation |
| **Score Analysis + Thresholds** | Break down scores per constraint, set acceptability thresholds | High - Timefold native support |
| **Post-hoc Feasibility Testing** | Pin solution, run constraint analysis on "unassigned" entities | Medium - requires extra solve step |
| **Constraint Justification** | Explain which constraints caused violations | High - Timefold native support |

---

## The Problem

In our planner, **compaction** (S5: minimize student group gaps) is:
- **Semantically hard**: The school requires contiguous lesson blocks
- **Implemented as soft**: Weight 500, not hard, because a hard constraint caused solver to get stuck in local optima

This creates a fundamental issue:
```
Score: 0hard/-52Msoft

Is this solution usable?
- We know 0hard means no hard constraint violations
- But -52Msoft aggregates ALL soft constraints
- We can't tell if compaction (S5) is satisfied or not
```

### Why Not Just Make It Hard?

From our benchmarks (6-minute runs on cartesian-shadow-lab-blocks, 8,693 lessons):

| Metric | Hard (H10 no-gaps) | Soft (S5=500) |
|--------|-------------------|---------------|
| CH time | 231s | 155s |
| Final hard score | -1,975k | -1,185k |
| Final soft score | -4.3M | -52.1M |

**The hard constraint competes with other hard constraints for solver attention**, reducing overall solution quality. The soft approach won: 40% better hard score and 76s faster CH.

---

## Approaches in Literature

### 1. Hierarchical Constraint Satisfaction Problems (HCSP)

**Source**: Borning, Freeman-Benson, Wilson (1992); O'Sullivan & Feldman (2009)

HCSP introduces **constraint hierarchies** with labeled strengths:

```
H0 = required (hard) - must be satisfied
H1 = strong - more important than H2
H2 = weak - preferred but least important
```

**Key insight**: A solution is **optimal** if no better solution exists at any level, where "better" is determined lexicographically.

**Lexicographic comparison**:
```
Score1 = 0hard/-1000medium/-50soft
Score2 = 0hard/-500medium/-100soft

Compare: hard first → equal
Compare: medium next → Score2 wins (-500 > -1000)
Soft level is ignored
```

**Application to our problem**:
- H0 (hard): Teacher conflicts, room conflicts, etc.
- H1 (medium): **Compaction** (our semantically-hard S5)
- H2 (soft): Preferences, distribution

This is EXACTLY what Timefold's HardMediumSoftScore provides!

**Timefold documentation**:
> "Medium constraints usually incentivise Timefold to assign as many entities as possible. They are used by Timefold to allow for overconstrained planning."

**Recommendation**: Move compaction (S5) to **medium** level, not soft. This creates a clear feasibility threshold:
```
Feasible = 0hard AND medium score within acceptable threshold
```

---

### 2. Partial Constraint Satisfaction Problems (PCSP)

**Source**: Freuder & Wallace (1989, 1992)

PCSP formalizes finding "the best partial solution" when full satisfaction is impossible:

**Components**:
```
<(P,U), (PS,≤), (M,(N,S))>

P = original CSP
U = universes (potential values)
PS = problem space (weakened versions of P)
M = distance metric
(N,S) = necessary and sufficient bounds
```

**Distance functions**:
1. **Solution subset**: Number of solutions added by relaxation
2. **Constraint augmentation**: Number of constraint values changed
3. **Max-CSP**: Number of constraints satisfied

**Application to our problem**:
- Define a **metric** for compaction violation (e.g., total gap minutes)
- Set a **threshold** N = maximum acceptable gap minutes
- Solution is acceptable if `gap_minutes < N`

**This is essentially what S5 weight=500 does** - it penalizes gap minutes. The question is: what threshold makes a solution "usable"?

---

### 3. Weighted CSP (WCSP) / Valued CSP (VCSP)

**Source**: Schiex, Fargier, Verfaillie (1995); Cooper et al. (2010)

WCSP extends CSP with **cost functions** on constraints:

```
Instance = (X, D, C, K)
X = variables
D = domains
C = cost functions c_i : scope → {0, ..., K}
K = maximum cost (∞ = hard constraint)
```

**Key concepts**:
- **Valuation structure**: Costs are combined and compared
- **Lower bounds**: Used in branch-and-bound (FDAC, EDAC)
- **Upper bounds**: Best known solution cost

**Application to our problem**:
- Treat S5 violation cost as part of the optimization objective
- Track **lower bound** on S5 violations during solving
- Compare final S5 cost to a **threshold**

**Bound computation** (from Dlask, Werner, de Givry 2021):
```
B(f) = Σ max(t∈U) f_t

This provides an upper bound on the optimal WCSP value.
If B(f) for compaction alone is below threshold → feasible.
```

---

### 4. Lexicographic Optimization

**Source**: Isermann (1982); Multi-objective optimization literature

**Lexicographic method**:
```
Minimize f_1(x)  → find optimal value v_1*
Minimize f_2(x) subject to f_1(x) ≤ v_1*
Minimize f_3(x) subject to f_1(x) ≤ v_1*, f_2(x) ≤ v_2*
...
```

**With thresholds** (from Tercan 2024 thesis):
```
Minimize f_k(x) subject to f_i(x) - f_i* ≤ ε_i for all i < k
```

Where ε_i is the **acceptable deviation** from optimal at level i.

**Application to our problem**:
1. Solve with hard constraints only → get optimal hard score (0)
2. Solve with hard + medium (compaction) → get optimal medium score M*
3. Accept solutions where medium score ≤ M* + ε_medium

---

### 5. Timefold/OptaPlanner Overconstrained Planning

**Source**: Timefold documentation; GitHub discussions #1617

**The overconstrained planning pattern**:

1. **Use 3 score levels**: HardMediumSoftScore
2. **Medium level penalizes unassigned entities**
3. **Hard constraints are checked but not "broken"**

```java
// Medium constraint for unassigned
.forEachIncludingUnassigned(Lesson.class)
.filter(lesson -> lesson.getTimeslot() == null)
.penalize(HardMediumSoftScore.ONE_MEDIUM)
.asConstraint("Unassigned lesson");
```

**Key insight from Timefold docs**:
> "If there is no feasible solution, this means the best solution will be infeasible. To instead leave some of the planning entities unassigned, apply overconstrained planning."

**For analyzing WHY entities are unassigned** (from discussion #1617):
> "Take that PlanningSolution, take all assigned entities and one of the unassigned, pin them all, and then run the solve then analyze the score."

**Application to our problem**:
- We're not dealing with unassigned entities, but the same principle applies
- If compaction is in medium level, we can analyze:
  1. What is the medium score?
  2. Which constraints contribute to it?
  3. Is compaction satisfied within threshold?

---

### 6. Score Analysis and Constraint Justification

**Source**: Timefold documentation; "Visualize the score and the constraints" blog

**Score breakdown per constraint type**:
```
| Constraint type    | Score impact |
|---------------------|--------------|
| Room capacity       | -100soft     |
| Room stability      | -40soft      |
| Curriculum compact. | -3soft       |
| Total               | -143soft     |
```

**Indictment heat map**:
- Shows score impact per planning entity
- Identifies "hot" entities causing violations

**Timefold API**:
```java
SolutionManager<VehicleRoutePlan, HardSoftLongScore> solutionManager =
    SolutionManager.create(solverFactory);
ScoreAnalysis<HardSoftLongScore> scoreAnalysis =
    solutionManager.analyze(vehicleRoutePlan);
System.out.println(scoreAnalysis.summarize());
```

**JSON serialization** for frontend:
```java
// ScoreAnalysis is JSON-friendly
// Can be sent to frontend for visualization
```

---

## Practical Solutions

### Solution 1: Three-Level Score with Compaction as Medium

**Implementation**:
```java
// In constraint provider
public Constraint studentGroupCompactness(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
        // ... join logic ...
        .penalize(HardMediumSoftScore.ONE_MEDIUM,
                  (l1, l2) -> calculateGapMinutes(l1, l2))
        .asConstraint("S5: Student group gaps");
}
```

**Feasibility check**:
```java
public boolean isUsable(Schedule solution) {
    HardMediumSoftScore score = solution.getScore();

    // Must satisfy all hard constraints
    if (score.hardScore() < 0) return false;

    // Medium (compaction) must be within threshold
    // Threshold = max acceptable gap minutes per student group
    int maxAcceptableGap = calculateMaxAcceptableGap(solution);
    int actualGap = -score.mediumScore(); // negative to positive

    return actualGap <= maxAcceptableGap;
}
```

**Advantages**:
- Native Timefold support
- Clear separation: hard → medium → soft
- Can set per-instance thresholds

---

### Solution 2: Score Analysis with Constraint-Specific Thresholds

**Implementation**:
```java
public FeasibilityReport analyzeFeasibility(Schedule solution) {
    SolutionManager<Schedule, HardMediumSoftLongScore> sm =
        SolutionManager.create(solverFactory);
    ScoreAnalysis<HardMediumSoftLongScore> analysis = sm.analyze(solution);

    // Extract constraint-specific scores
    long s5Score = analysis.getConstraintAnalysis("S5: Student group gaps")
                           .getScore().softScore();

    // Define thresholds per constraint
    Map<String, Long> thresholds = Map.of(
        "S5: Student group gaps", 10000L,  // max 10000 gap-minutes
        "S1: Teacher gaps", 50000L,
        "S2: Subject distribution", 100000L
    );

    boolean feasible = true;
    List<String> violations = new ArrayList<>();

    for (var entry : thresholds.entrySet()) {
        long score = getScore(analysis, entry.getKey());
        if (score < -entry.getValue()) {
            feasible = false;
            violations.add(entry.getKey() + ": " + score +
                          " (threshold: " + -entry.getValue() + ")");
        }
    }

    return new FeasibilityReport(feasible, violations);
}
```

---

### Solution 3: Post-Hoc Feasibility Testing

**For critical constraints, run a verification solve**:

```java
public boolean isCompactionFeasible(Schedule solution) {
    // Pin all assignments
    for (Lesson l : solution.getLessons()) {
        l.setPinned(true);
    }

    // Create a new problem with ONLY compaction as hard constraint
    Schedule verification = clone(solution);

    // Solve with special constraint provider that has:
    // - All assignments pinned
    // - Compaction as HARD constraint
    // - 0 seconds time limit (just score calculation)

    SolverFactory<Schedule> factory = SolverFactory.create(
        new SolverConfig()
            .withSolutionClass(Schedule.class)
            .withEntityClasses(Lesson.class)
            .withConstraintProvider(VerificationConstraintProvider::new)
            .withTerminationSpentLimit(Duration.ofSeconds(0))
    );

    Solver<Schedule> solver = factory.buildSolver();
    Schedule result = solver.solve(verification);

    return result.getScore().hardScore() == 0;
}
```

**VerificationConstraintProvider**:
```java
// Only includes compaction as HARD, everything else ignored
public class VerificationConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            // Compaction as HARD
            factory.forEach(Lesson.class)
                .join(Lesson.class,
                    equal(Lesson::getStudentGroup),
                    lessThan(Lesson::getTimeslot))
                .filter((l1, l2) -> hasGap(l1, l2))
                .penalizeLong(HardSoftLongScore.ONE_HARD,
                             (l1, l2) -> calculateGapMinutes(l1, l2))
                .asConstraint("COMPACTNESS_HARD")
        };
    }
}
```

---

### Solution 4: Dual-Pass Solving

**Two-phase approach**:

1. **Phase 1**: Solve with compaction as SOFT (current approach)
   - Get best solution with 0hard/Nsoft

2. **Phase 2**: Re-solve with compaction as HARD, using Phase 1 as starting point
   - If solver finds 0hard, compaction is achievable
   - If solver fails (negative hard), compaction is infeasible

```java
public Schedule solveWithFeasibilityCheck(Schedule problem) {
    // Phase 1: Soft compaction
    Solver<Schedule> softSolver = createSolver(SoftCompactionConfig.class);
    Schedule softSolution = softSolver.solve(problem);

    // Phase 2: Hard compaction
    Solver<Schedule> hardSolver = createSolver(HardCompactionConfig.class);
    Schedule hardSolution = hardSolver.solve(softSolution);

    if (hardSolution.getScore().hardScore() == 0) {
        // Compaction is achievable - use hard solution
        return hardSolution;
    } else {
        // Compaction infeasible - use soft solution with warning
        log.warn("Compaction infeasible, using soft solution");
        return softSolution;
    }
}
```

---

## Recommended Approach

For our school timetabling problem:

### Immediate: Score Analysis with Constraint-Specific Thresholds

1. **Implement `ScoreAnalysis` service** to break down scores by constraint
2. **Define acceptability thresholds** for semantically-hard soft constraints
3. **Expose via REST API** for frontend visualization

### Medium-term: Three-Level Score (HardMediumSoft)

1. **Move compaction (S5) to medium level**
2. **Set medium threshold** based on human planner benchmarks
3. **Use lexicographic comparison**: 0hard first, then medium score

### Long-term: Post-Hoc Verification

1. **For critical solutions**, run verification solve with compaction as hard
2. **Store feasibility metadata** with each solution
3. **Provide "explain why infeasible"** feature using indictment analysis

---

## References

### Academic Papers
- Borning, A., Freeman-Benson, B., Wilson, M. (1992). "Constraint Hierarchies"
- Freuder, E., Wallace, R. (1992). "Partial Constraint Satisfaction" - AI Journal 58
- Schiex, T., Fargier, H., Verfaillie, G. (1995). "Valued Constraint Satisfaction Problems"
- Cooper, M., et al. (2010). "Soft Constraints: A Survey"

### Timefold/OptaPlanner Documentation
- [Constraints and Score: Overview](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/overview)
- [Responding to Change: Overconstrained Planning](https://docs.timefold.ai/timefold-solver/latest/responding-to-change/responding-to-change)
- [Understanding the Score](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/understanding-the-score)
- [Visualize the score and the constraints](https://timefold.ai/blog/visualize-the-score-and-the-constraints)

### GitHub Discussions
- [Score Analysis in an Over Constrained Planning Problem #1617](https://github.com/TimefoldAI/timefold-solver/discussions/1617)

### Books
- Handbook of Constraint Programming (Rossi, van Beek, Walsh, 2006) - Chapter 9: Soft Constraints
