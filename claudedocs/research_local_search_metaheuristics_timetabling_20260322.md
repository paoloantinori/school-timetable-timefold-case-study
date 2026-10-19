# Local Search Metaheuristics for School/University Timetabling: Literature Survey

**Date**: 2026-03-22
**Scope**: Academic literature on neighborhood structures, cooling schedules, tabu configurations, LAHC, VNS, hybrid approaches, local optima escape, ITC competition winners, ALNS, and recent advances (2023-2026).
**Sources**: ITC-2007/2011/2019 competition papers, PATAT conferences, Journal of Scheduling, EJOR, Computers & Operations Research, Annals of Operations Research.

---

## 1. Neighborhood Structures for Timetabling

### Standard Move Operators

The foundational neighborhood operators for timetabling, in order of complexity:

| Operator | Description | Complexity | Effectiveness |
|----------|-------------|------------|---------------|
| **Move** (relocate) | Move one event to a different timeslot/room | O(n) | Fast, fundamental |
| **Swap** (exchange) | Swap timeslots/rooms of two events | O(n^2) | Strong for conflict resolution |
| **Kempe Chain** | Swap colors in a connected component of a bipartite graph | O(n) | Excellent for room/timeslot conflicts |
| **Chain Swap** | Multi-event cyclic exchange between k timeslots | O(n^k) | Powerful but expensive |

### Timetabling-Specific Operators

**Torque Operator (Saviniec et al., 2013)**
- Inspired by chain-of-moves idea
- Builds a graph to identify connected components causing conflicting meetings
- Identifies and resolves conflict structures that simple swaps miss
- Very effective for Brazilian high school timetabling
- Used within VNS framework

**Matching Operator (Saviniec & Constantino, 2017)**
- Based on resolution of assignment problems
- Reschedules a set of teachers in a class
- Finds the best local permutation for a given class
- Combined with torque operator in ILS+VNS hybrid (38 best out of 41 instances)

**Cyclic Exchange (Meyers & Orlin, 2006, PATAT)**
- VLSN technique: ordered subset of exams in disjoint timeslots swapped cyclically
- Each exam moves to the timeslot of the exam following it
- Can be searched effectively for improving sets using dynamic programming
- Scales well for examination timetabling

**Double Kempe Chain (Lermo et al., 2008)**
- Two Kempe chain neighborhoods combined
- Applied within Adaptive Tabu Search for CB-CTT
- Produces very large neighborhoods that escape local optima

**Event Block Swap (Brito et al., 2012)**
- Swap entire blocks of events between timeslots
- Part of 6-neighborhood set for ITC2011 high school timetabling

**Resource Swap / Resource Move (Brito et al., 2012)**
- Swap or move room assignments without changing timeslots
- Useful when room conflicts are the bottleneck

### Neighborhood Ordering Recommendation

**Order from smallest to largest** (Hansen & Mladenovic, 2001):
1. Move (single event)
2. Swap (two events)
3. Kempe chain
4. Resource swap/move
5. Block swap / Torque
6. Cyclic exchange / Ejection chain

**Key finding**: In VND, ordering neighborhoods from smallest to largest with "first improvement" strategy is generally preferred. However, when starting from high-quality solutions, "best improvement" may be better (Salhi, 2020).

**For our project (Italian vocational school)**: Given the mix of single Lessons and multi-hour LabBlocks, the most relevant operators are:
- Timeslot move (reassign a lesson to a different timeslot)
- Timeslot swap (swap timeslots between two lessons)
- Room swap (swap rooms between two lessons)
- Kempe chain (for room/timeslot conflicts in a bipartite structure)
- Block move (move an entire LabBlock to a different starting timeslot)

---

## 2. Simulated Annealing Cooling Schedules

### Cooling Schedule Types

| Schedule | Formula | Pros | Cons | Typical Alpha |
|----------|---------|------|------|---------------|
| **Geometric** | T_{k+1} = alpha * T_k | Most common, well-studied | May cool too fast or slow | 0.93 - 0.995 |
| **Linear** | T_{k+1} = T_k - delta_T | Simple, predictable | No theoretical guarantee | delta_T = 0.1-0.2 |
| **Logarithmic** | T_k = T_0 / ln(1+k) | Convergence guarantee | Extremely slow in practice | alpha = 1.0 |
| **Exponential** | T_k = T_0 * exp(-alpha * k/N) | Fast initial cooling | Can miss optima early | alpha < 1.0 |
| **Adaptive** | Based on acceptance rate | Self-tuning | More complex | Varies |

### Concrete Parameter Recommendations from Literature

**Thompson & Dowsland (1995, TISSUE system)**:
- Proposed general cooling schedules for timetabling
- Geometric cooling with alpha calibrated per problem size
- Temperature-dependent neighborhood selection

**Lewis (2008, ITC2007 Track 2 winner)**:
- Two-phase SA approach
- Time-dependent: parameters calculated automatically based on available computation time
- First SA phase: aggressive (higher temperature, broader exploration)
- Second SA phase: fine-tuning (lower temperature, focused exploitation)

**AlHadid et al. (2020, ITC2007-Track3)**:
- Dynamic initial temperature mechanism (avoids too-high or too-low T_0)
- Adaptive cooling schedule that adjusts during search
- Adaptive neighborhood structure: swap with highest-cost timeslot
- Finding: high T_0 wastes time; low T_0 traps in local optima

**Goh et al. (2019, SA with improved reheating)**:
- Two-stage: feasibility first, then soft constraint optimization
- Reheating strategy: when stuck, partially re-raise temperature
- Learning mechanism: records successful moves to guide future exploration

**Abramson et al. (1999, school timetabling)**:
- Adaptive cooling: temperature increases/decreases based on number of successful moves
- Dynamic cooling schedule adjusts to search state
- If acceptance rate too high -> decrease temperature faster
- If acceptance rate too low -> decrease temperature slower

### Recommended SA Configuration for Timetabling

```
Initial temperature: T_0 such that acceptance rate ~80% at start
Cooling schedule: Geometric
Alpha: 0.995 for long runs, 0.95-0.99 for shorter runs
Iterations per temperature: n/10 to n/5 (where n = problem size)
Stopping: T < T_min or no improvement for K*ln(n) iterations
```

**For our project**: Given the 5-phase solver pipeline, SA is most relevant as a local search phase. Recommended:
- Start temperature: calibrated to accept ~80% of worsening moves initially
- Geometric cooling with alpha = 0.997 (longer exploration phase)
- Consider adaptive cooling that adjusts based on acceptance rate
- Reheating: if no improvement for 10,000 iterations, raise T to 50% of T_0

---

## 3. Tabu Search Configurations

### Tabu List Size

| Problem Size | Recommended Tabu Tenure | Source |
|-------------|------------------------|--------|
| Small (100 events) | 7-15 | Chen et al. (2020) |
| Medium (400 events) | 15-30 | Adaptive TS paper |
| Large (8000+ events) | 20-50 | Hertz et al. tutorial |
| General guideline | sqrt(n) to n/10 | Multiple sources |

**Key finding**: Tabu list size should be **adaptive**. Fixed sizes either allow cycling (too short) or over-restrict search (too long).

### Adaptive Tabu List Strategies

**Reactive Tabu Search (Battiti & Tecchiolli)**:
- Increase tabu tenure when cycling detected
- Decrease when search is progressing well
- Track solution revisit frequency

**Chen et al. (2020, adaptive tabu search for timetabling)**:
- Three-phase: initialization (greedy), intensification, diversification
- Intensification and diversification combined adaptively
- Tabu list adjusts based on solution state
- Outperforms traditional TS in most cases

**Probabilistic Aspiration Criterion (BT-CAP)**:
- When a tabu move improves current solution, accept with probability function
- Proven with 99% confidence to outperform default aspiration and objective aspiration
- Tested on ITC2002 benchmark instances (20 instances)

### Aspiration Criteria

Three main types (Alvarez-Valdes et al.):
1. **Default aspiration**: Override tabu if move leads to best-ever solution
2. **Objective aspiration**: Override tabu if move improves on a threshold
3. **Probabilistic aspiration**: Override tabu with probability based on improvement magnitude (best performer)

### Intensification / Diversification

**Intensification**:
- When improving moves found, deepen search in that region
- Reduce tabu tenure to allow backtracking
- Focus on high-constraint-violation areas

**Diversification**:
- When stuck, increase tabu tenure
- Force exploration of unvisited regions
- Penalize frequently-visited solution attributes
- Penalty-guided perturbation: rank lectures by penalty cost, randomly perturb top-k

### Recommended TS Configuration

```
Tabu tenure: Adaptive, starting at sqrt(n), range [7, 50]
Aspiration: Probabilistic (accept improving tabu moves with probability)
Neighborhood: Move + Swap + Kempe chain (3 structures)
Intensification: When improving, reduce tenure to 50%
Diversification: When stuck for n/5 iterations, increase tenure to 150%
Restart: After n*ln(n) iterations without improvement
```

**For our project**: Tabu search is relevant as the local search metaheuristic. Entity-based tabu (track which Lesson/LabBlock entities have been moved) with adaptive tenure is recommended.

---

## 4. Late Acceptance Hill Climbing (LAHC)

### Algorithm Description

LAHC (Burke & Bykov, 2008) maintains a fitness array of length L (history length). A candidate solution is accepted if it is:
1. Better than the current solution (standard HC), OR
2. Better than the solution at position (current_iteration - L) in the history

This means LAHC can accept worsening moves by comparing against older, typically worse solutions.

### Advantages over SA

- **Single parameter**: Only history length L needs tuning (vs. T_0, alpha, iterations-per-temp for SA)
- **No cooling schedule**: Simpler implementation
- **Deterministic acceptance**: More predictable behavior
- **O(1) history update**: Virtual pointer arithmetic (v = I mod L) avoids shifting

### Performance on Timetabling Benchmarks

**Fonseca et al. (2016, ITC2011 high school timetabling)**:
- LAHC **outperformed the ITC2011 winner** (SA-ILS approach)
- Original LAHC slightly better than standalone SA
- LAHC particularly strong on larger instances
- sf-LAHC (scaled fitness LAHC) variant proposed

**Mixed SA-sf-LAHC (Fonseca et al., 2016)**:
- Combined SA and sf-LAHC achieved best known results for ITC2011
- SA phase followed by sf-LAHC refinement
- Best overall results at that time

**Burke & Bykov (2008, exam timetabling)**:
- LAHC competitive with SA and Great Deluge
- History length L = 500-2000 typical
- Longer L = more diversification, shorter L = more intensification

**Parameter-less LAHC (Ochoa et al., 2014)**:
- Automatic history length selection
- Competitive with tuned LAHC
- Useful when no time for parameter tuning

### History Length Recommendations

| Problem Size | Recommended L | Source |
|-------------|--------------|--------|
| Small (100-400 events) | 200-500 | Fonseca et al. |
| Medium (400-2000 events) | 500-2000 | Burke & Bykov |
| Large (2000+ events) | 2000-10000 | Literature survey |
| General rule | 10 * n_moves_per_second * 1_second | Heuristic |

### Recommended LAHC Configuration

```
History length L: Start at 1000, tune between 200 and 5000
Variant: sf-LAHC (scaled fitness) for better results
Stopping: Time limit or L * n iterations without improvement
```

**For our project**: LAHC is an excellent alternative to SA. Its single-parameter nature makes it attractive. With ~8000 lessons, L = 5000-10000 would be appropriate.

---

## 5. Variable Neighborhood Search (VNS)

### VNS Variants for Timetabling

**Basic VNS (Fonseca & Santos, 2014)**:
- 6 neighborhood structures for ITC2011
- Shake function generates random neighbor in k-th neighborhood
- Local search refines the shaken solution
- Outperformed ITC2011 winner on average

**Sequential Variable Neighborhood Descent (SVND)**:
- Deterministic ordering of neighborhoods
- First improvement strategy in each neighborhood
- Reset to first neighborhood on improvement
- Very effective for timetabling (Fonseca & Santos)

**Reduced VNS (RVNS)**:
- No local search, only random sampling from neighborhoods
- Fast, good for initial exploration
- Useful for large instances

**Skewed VNS (SVNS)**:
- Allows acceptance of worse solutions with a penalty
- Skew function encourages exploration of distant regions
- Similar to SA but neighborhood-based

### Neighborhood Ordering Strategy

**General principle**: Order from smallest/simplest to largest/most complex.

**Recommended order for timetabling** (Fonseca & Santos, 2014):
1. Single event move (smallest perturbation)
2. Two-event swap
3. Kempe chain exchange
4. Resource (room) swap
5. Block move / Event block swap
6. Torque / Cyclic exchange (largest perturbation)

**Key insight**: The order matters significantly. Smaller neighborhoods should be explored first because they are faster and more likely to find improvements when the solution is near-optimal. Larger neighborhoods are needed when smaller ones fail to improve (escaping local optima).

### VNS + Mathematical Programming Hybrid

**Fonseca et al. (2016b)**:
- VNS for exploration + IP solver for intensification
- VNS makes thematic changes to neighborhood structures
- IP solver invoked to further improve feasible solutions
- Improved 15 out of 17 best-known solutions for ITC2011

### ILS + VNS Hybrid

**Saviniec & Constantino (2017)**:
- Two operators: matching (MT) and torque (TQ)
- ILS framework: perturbation + VND local search + acceptance
- 38 best solutions out of 41 instances for Brazilian high school TT
- Strongest single-paper result for that benchmark

### Recommended VNS Configuration

```
Neighborhoods: 4-6 structures, ordered small to large
VND strategy: First improvement, reset to k=1 on any improvement
Shake: Random sample from k-th neighborhood
Local search: First improvement VND across all neighborhoods
Stopping: Time limit or unimprovedSecondsSpentLimit
```

**For our project**: VND is already part of our solver pipeline (currently using Timefold's VND). The key improvement would be:
1. Add more neighborhood structures (Kempe chains, block swaps)
2. Order neighborhoods optimally
3. Consider adding shake/perturbation for VNS-style diversification

---

## 6. Hybrid Approaches

### SA + VNS (Brito et al., 2012, ITC2011 finalist)

- Initial solution via KHE (Kerbo High school timetablinG Engine)
- SA phase with probabilistic neighborhood selection (6 neighborhoods)
- VNS phase with shake function
- 8 feasible solutions out of 19 instances (ITC2011 finals)

### VNS + Tabu Search (Vianna et al., 2020)

- **VNS-TS1**: TS as local search method within VNS framework -- **best performer**
- **VNS-TS2**: TS as intensification strategy
- VNS-TS1 obtained best average results for all instances
- 12-15.5% improvement over manual solutions
- In production use at UFF since 2019

### SA + ILS (Fonseca et al., 2012, ITC2011 winner)

- SA phase generates initial solution
- ILS phase with perturbation + local search
- Perturbation: randomly reassign subset of events
- Surprisingly, standalone SA sometimes outperformed SA-ILS

### ILS + VNS (Saviniec & Constantino, 2017)

- ILS framework with VND as local search
- Two specialized operators: matching and torque
- 38/41 best solutions on Brazilian benchmark
- One of the strongest results in literature

### ALNS + GLS + VNS (2023, hybrid for course timetabling)

- Adaptive Large Neighborhood Search for guided destruction/repair
- Guided Local Search with penalty features for diversification
- VNS for multi-neighborhood exploration
- Novel neighborhood structures targeting high-violation lectures
- Instance decomposition and incremental solving

### SA + Great Deluge

**Muller (2008, ITC2007 winner)**:
- Construction phase (iterative forward search)
- Hill Climbing phase
- Great Deluge phase with oscillating bound
- Bound oscillation: B = B * GD_cr, reset when B < GD_lb * S_best
- Counter `at` increases each time lower limit reached, reset on improvement

### Ranking of Hybrid Approaches

Based on literature evidence:
1. **VNS with TS as local search** (VNS-TS1) -- best consistent results
2. **ILS + VNS with domain-specific operators** -- strongest on Brazilian benchmarks
3. **SA + LAHC hybrid** -- best on ITC2011 high school TT
4. **ALNS + GLS + VNS** -- promising for complex instances
5. **SA + ILS** -- ITC2011 winner, robust
6. **SA + Great Deluge** -- ITC2007 winner, well-tested

---

## 7. Escaping Local Optima

### Perturbation Strategies

**Penalty-Guided Perturbation (Lermo et al., 2008)**:
1. Rank all lectures by soft constraint penalty cost (descending)
2. Select subset from top-k most penalized lectures
3. Randomly apply SimpleSwap or KempeSwap moves to these lectures
4. Restart local search from perturbed solution
- **Key insight**: Constrain perturbation to highly-penalized lectures is essential -- these contribute most to violations

**ILS Perturbation Strength Control**:
- Weak perturbation: change 1-3 events (intensification)
- Medium perturbation: change n/100 events (balanced)
- Strong perturbation: change n/20 events (diversification)
- Adaptive: increase strength when stuck, decrease when improving

**Great Deluge with Oscillating Bound (Muller, 2008)**:
- Upper bound: GD_ub * S_best
- Cooling rate: B = B * GD_cr
- When bound reaches lower limit (GD_lb * S_best), reset to upper limit
- Counter increases on each reset (widens search progressively)
- Counter resets when new best found
- Allows systematic escape from deep local minima

### Restart Strategies

**Cold Restart**: Discard current solution, generate new random solution
- Trigger: n * ln(n) iterations without improvement
- Pro: Complete diversification
- Con: Loses all learned information

**Warm Restart**: Perturb current solution moderately
- Trigger: n/5 iterations without improvement
- Pro: Preserves partial solution structure
- Con: May return to same local optimum

**Adaptive Restart (from Krejca et al., 2022)**:
- Theory-driven: restarts are beneficial when basin of attraction of global optimum is large
- Warm restarts work well with guiding information (cost gradient direction)
- Larger search distances (global moves) work well when deceptive regions are present

### Ruin-and-Recreate

**Applied in timetabling** (Sorensen et al., ITC2011):
- Destroy: Remove subset of events from their timeslots
- Recreate: Reinsert removed events optimally
- Related to ALNS destroy/repair operators
- Effective for large-scale perturbation

### Recommended Strategy for Our Project

```
Primary: Great Deluge with oscillating bound (as in Muller's ITC2007 winner)
Secondary: Penalty-guided perturbation (target highest-cost lessons)
Fallback: Warm restart with strong perturbation (n/20 lessons re-randomized)
Trigger: unimprovedSecondsSpentLimit or score stall detection
```

---

## 8. ITC Competition Winners

### ITC-2007 Winners

**Track 1 (Examination Timetabling)**: Tomas Muller (UniTime)
- Algorithm: Hybrid constraint-based approach
- Phases: Iterative Forward Search -> Hill Climbing -> Great Deluge
- Key innovation: Great Deluge with oscillating bound for escaping local optima
- Conflict-based Statistics (CBS) to prevent cycling
- Neighborhoods: Multiple specialized move operators

**Track 2 (Post-Enrollment Course Timetabling)**: Tomas Muller (UniTime)
- Same hybrid approach, different neighborhood operators
- Winner of both Track 1 and Track 2

**Track 3 (Curriculum-Based Course Timetabling)**: Tomas Muller (UniTime)
- Same framework adapted for CB-CTT constraints
- Winner of all three tracks -- unprecedented result

**Notable competitors**:
- Gogos (Greece): GRASP + local search
- Atsuta et al. (Japan): CSP solver with tabu search + ILS
- De Smet (Belgium): Tabu-based approach
- Lewis (UK): 3-phase time-dependent SA

### ITC-2011 Winners (High School Timetabling)

**Winner**: Fonseca et al. (Brazil)
- Algorithm: SA + ILS hybrid
- SA phase for initial solution
- ILS with perturbation for improvement
- Surprisingly, standalone SA sometimes beat SA-ILS

**Finalists**:
- Brito et al.: SA + VNS hybrid (6 neighborhoods)
- Sorensen et al.: ALNS-based approach (Lectio commercial solver)
- KHE-based approaches

### ITC-2019 Winners (University Course Timetabling)

**Winner**: Mikkelsen & Holm (Denmark)
- Algorithm: Parallelized matheuristic (fix-and-optimize)
- Graph-based MIP formulation
- Conflict graphs with reduction algorithm
- Parallel execution on multiple cores
- Solved all instances

**Second Place**: Rappos et al. (Switzerland)
- Mixed Integer Programming approach
- Pre-calculation of instance characteristics
- Constraint aggregation
- Large neighborhood search + metaheuristics

**Third/Best Open Source**: Muller (UniTime)
- Iterative Forward Search + Hill Climbing + Great Deluge
- Constructive student sectioning algorithm
- Default configuration, no parameter tuning
- Best known solutions for 2 late instances

**Other notable approaches**:
- MaxSAT-based (UniCorT): Linear search with clusters algorithm
- Hybrid CP + local search

### Key Takeaway from Competition Results

1. **Matheuristics** (MIP + metaheuristic) are the current state of the art for complex instances
2. **Great Deluge with oscillating bounds** is a proven local search technique
3. **Multi-phase approaches** consistently outperform single-phase methods
4. **Domain-specific neighborhoods** are more important than generic operators
5. **Iterative Forward Search** (Muller's approach) is a robust framework

---

## 9. Adaptive Large Neighborhood Search (ALNS)

### Application to Timetabling

**Sorensen et al. (2012, ITC2011 finalist)**:
- ALNS framework for high school timetabling
- Part of the commercial Lectio solver
- Destroy and repair operators for educational timetabling
- Roulette wheel mechanism for operator selection
- Finalist in ITC2011 round 2

**Hybrid ALNS + GLS + VNS (2023)**:
- Novel neighborhood structures targeting lectures/timeslots with highest violations
- GLS penalty features guide the search toward under-explored regions
- VNS provides multi-neighborhood exploration within the ALNS framework
- Instance decomposition: solve incrementally by curriculum/course group
- Significantly outperforms commercial software on real-world instances

### Destroy Operators for Timetabling

| Operator | Description | Use Case |
|----------|-------------|----------|
| Random removal | Remove N random events | Diversification |
| Worst removal | Remove N highest-penalty events | Targeted improvement |
| Related removal | Remove events sharing teachers/rooms/students | Conflict resolution |
| Day removal | Remove all events on a specific day | Day-level restructuring |
| Teacher removal | Remove all events for a specific teacher | Teacher conflict resolution |
| Curriculum removal | Remove all events for a curriculum | Curriculum-level optimization |

### Repair Operators for Timetabling

| Operator | Description | Complexity |
|----------|-------------|------------|
| Greedy insertion | Insert each removed event at its best position | O(n^2) |
| Regret-based insertion | Insert event with highest "regret" first | O(n^3) |
| Random insertion | Insert at random feasible position | O(n) |
| Constraint-aware insertion | Insert respecting most violated constraints | O(n^2) |

### Adaptive Mechanism

- Roulette wheel selection based on past performance
- Scoring: sigma_1 for new best, sigma_2 for improvement, sigma_3 for acceptance
- Weight update every phi iterations
- Typical values: sigma_1 = 33, sigma_2 = 9, sigma_3 = 3

### ALNS for Our Project

Given our multi-entity model (Lessons + LabBlocks), ALNS destroy/repair could be:
- **Destroy**: Remove all lessons for a specific student group on a specific day
- **Destroy**: Remove all lessons for a specific teacher in a week
- **Repair**: Reinsert using greedy insertion with constraint awareness
- **Repair**: Reinsert using Timefold's CH-like assignment

---

## 10. Recent Advances (2023-2026)

### Matheuristics Dominance

**ITC2019** established matheuristics (MIP + heuristic) as the state of the art:
- Fix-and-optimize approach (Lindahl et al., 2018)
- Parallelized matheuristic (Mikkelsen & Holm, 2022)
- Graph-based MIP formulations
- Integration of CP solvers with local search

### Machine Learning Integration

**Hyper-heuristics with learning**:
- Selection hyper-heuristics using online learning (PATAT 2020)
- Reinforcement learning for operator selection
- Case-based reasoning for algorithm selection
- Neural network-guided destroy/repair in ALNS

**Adaptive mechanisms**:
- Adaptive neighborhood selection based on problem features
- Self-tuning parameters during search
- Ensemble approaches combining multiple metaheuristics

### Decomposition Approaches

**Instance decomposition**:
- Curriculum-based decomposition (solve by curriculum)
- Teacher-based decomposition (solve by teacher)
- Time-based decomposition (solve by week/month)
- Incremental solving (start with hard constraints, add soft constraints)

**Generic analytics-driven framework (2026)**:
- Unified ILS-based constructive search across timetabling variants
- Only domain-specific neighborhood operators need adaptation
- Cross-domain optimization demonstrated

### Constraint Programming + Local Search

- CP for feasibility (hard constraints)
- Local search for optimization (soft constraints)
- Tight integration vs. loose coupling
- CP-based neighborhood exploration

### Two-Stage Multi-Neighborhood SA

- First stage: feasibility (hard constraints only)
- Second stage: multi-neighborhood SA for optimization
- Principled tuning methodology for neighborhood selection
- Effective on real-world benchmarks

### MaxSAT Approaches

- Encode timetabling as MaxSAT problem
- Linear search with clusters algorithm
- Iterative SAT solving with relaxation
- Combined with local search for post-processing

---

## Summary: Concrete Recommendations for Our Project

### Best Metaheuristic Choices (Ranked)

1. **VND with Tabu Search** (VNS-TS1 approach): TS as local search within VNS
   - Proven best in Vianna et al. (2020)
   - Adaptive tabu tenure, probabilistic aspiration
   - Multiple neighborhood structures ordered small to large

2. **Great Deluge with oscillating bound** (Muller's approach)
   - Winner of ITC2007 all three tracks
   - Simple, robust, few parameters
   - Natural fit with Timefold's existing framework

3. **LAHC** (single parameter, competitive with SA)
   - Outperformed ITC2011 winner
   - Simpler than SA to tune
   - History length L = 5000-10000 for our problem size

4. **SA with adaptive cooling**
   - Most studied, well-understood
   - Geometric cooling alpha = 0.995-0.997
   - Dynamic initial temperature

5. **ILS + VNS with domain operators**
   - Strongest benchmark results (Saviniec & Constantino, 38/41)
   - Matching + torque operators
   - Requires problem-specific operator design

### Recommended Solver Pipeline Enhancement

Given our current 5-phase pipeline (CH LabBlocks -> CH Lessons -> LS Compactness -> LS Conflict -> LS Room):

**Option A: Add LAHC as alternative local search phase**
- Replace one LS phase with LAHC
- Only one parameter to tune (history length)
- Low implementation risk

**Option B: Add Great Deluge with oscillating bound**
- Replace or augment current LS phases
- Oscillating bound naturally handles exploration/exploitation
- Proven in ITC2007

**Option C: Multi-neighborhood VND enhancement**
- Add Kempe chain and block swap operators to existing VND
- Order neighborhoods: move -> swap -> Kempe -> block swap
- Add adaptive neighborhood selection

**Option D: Hybrid VNS-TS**
- TS as local search within VNS framework
- Requires entity-based tabu tracking
- Highest potential reward, moderate implementation complexity

### Recommended Starting Point

**VND enhancement (Option C)** is the safest and most practical:
1. Add Kempe chain move operator
2. Add block swap operator (for LabBlocks)
3. Order neighborhoods optimally
4. Test with our existing 5-phase pipeline
5. If results plateau, migrate to Great Deluge or LAHC

---

## References

### Competition Papers
- Muller, T. (2009). ITC2007 solver description: a hybrid approach. *Annals of Operations Research*, 172, 429-446.
- Fonseca, G.H.G. et al. (2016). Late acceptance hill-climbing for high school timetabling. *Journal of Scheduling*, 19(4).
- McCollum, B. et al. (2010). Setting the research agenda in automated timetabling: ITC2007. *INFORMS Journal on Computing*, 22(1), 120-130.
- Mikkelsen, G.S. & Holm, A. (2022). A parallelized matheuristic for ITC2019. *Journal of Scheduling*, 25, 429-452.

### Neighborhood Structures
- Saviniec, L. & Constantino, A. (2017). Hybrid ILS and VNS for Brazilian high school TT. *EJOR*.
- Meyers, C. & Orlin, J.B. (2006). VLSN techniques in timetabling. PATAT 2006.
- Brito, S. et al. (2012). SA-VNS approach for high school TT. *Electronic Notes in Discrete Mathematics*.

### Simulated Annealing
- Lewis, R. (2008). Time-dependent metaheuristic for post enrolment TT. *Annals of Operations Research*, 194.
- Abramson, D. et al. (1999). SA cooling schedules for school TT. *Asia-Pacific JOR*.
- AlHadid, I. et al. (2020). Investigation of SA components for UCTP. *IJOPAM*.

### Tabu Search
- Chen, M. et al. (2020). Large-scale timetabling with adaptive tabu search. *J. Intelligent Systems*.
- Lermo, J. et al. (2008). Hybrid ATS for CB-CTT. *AIMSA 2008*.
- Alvarez-Valdes, R. et al. (1996). TS to schedule university exams.

### LAHC
- Burke, E.K. & Bykov, Y. (2008). Late acceptance strategy in HC for exam TT. PATAT 2008.
- Fonseca, G.H.G. & Santos, H.G. (2014). VNS-based algorithms for high school TT. *Computers & OR*, 52.

### VNS
- Fonseca, G.H.G. & Santos, H.G. (2014). VNS for high school TT. *Computers & OR*, 52, 203-208.
- Hansen, P. & Mladenovic, N. (2001). VNS: Principles and applications. *EJOR*, 130(3).

### Hybrids
- Vianna, D.S. et al. (2020). Hybrid VNS-TS for UCTP. *Brazilian J. of OR & PM*.
- Goh, S.L. et al. (2019). SA with improved reheating for PECTT.

### ALNS
- Sorensen, M. et al. (2012). ALNS for high school TT (ITC2011). PATAT 2012.
- Ropke, S. & Pisinger, D. (2006). ALNS for PDPTW. *Transportation Science*.

### Escaping Local Optima
- Krejca, M. et al. (2022). Escaping local optima with local search: theory-driven discussion. PPSN 2022.

### Recent Advances
- Tan, T. et al. (2021). Survey of optimization methodologies in school TT. *Expert Systems with Applications*.
- Generic analytics-driven constructive search (2026). *Computers & Industrial Research*.
