# Research: Timefold compatibility with the human-faithful construction spike (TASK-396)

**Depth:** exhaustive. **Date:** 2026-07-23. **Timefold version in project:** 2.3.0
(docs scraped are `latest` = 2.3.0-rc-1). **For:** the TASK-396 spike design
(`claudedocs/design/human-faithful-construction-spike-2026-07-23.md`).

## Executive summary (verdict first)
**Timefold can host the spike's approach, but the approach goes against the framework's
grain and must be built as a custom phase + a one-line model change — it is not a built-in
algorithm.** Specifically:

1. The spike's "constructive + hard-feasible-at-every-step + box-the-rest + bounded backtrack"
   paradigm is **none of Timefold's built-in algorithms**. Timefold's built-ins are Construction
   Heuristics (greedy, scalable, **not** guaranteed feasible, **no** backtracking) + Local Search
   (metaheuristic repair), plus Exhaustive Search (Branch & Bound = backtracking, but rated
   **Scalable 0/5** and "mostly unusable for a real-world problem").
2. It **is implementable** via `CustomPhaseCommand` (a custom solver phase) — which **this project
   already uses** for `DayClusteredConstructionPhase` and `CompactionPhase`. So a human-faithful
   construction phase is a drop-in replacement for the current custom construction phase.
3. **The "box" requires a model change**: `Lesson.timeslot` is currently `@PlanningVariable(...)`
   **without** `allowsUnassigned` (Lesson.java:89) — so the solver is *forced* to assign every
   lesson a timeslot and has no box, which is the mechanical root cause of the -44,100-hard
   scatter. Adding `allowsUnassigned = true` to `timeslot` (the room variable already has it,
   Lesson.java:94) enables the box; the existing **H0** (`unassignedLesson`, BaseConstraintProvider:209/463)
   then becomes the pressure that keeps the box small. This is a one-line **model** change → Fedora
   decision under the primary directive.
4. **The real risk is Timefold's own verdict on backtracking**: Branch & Bound is documented
   Scalable 0/5 — "Exhaustive Search hits this wall on small datasets already, so in production
   these algorithms are mostly useless." The spike's bounded-backtrack + H0-box + MRV ordering must
   *demonstrably* avoid that wall on the way to 12,301 lessons. That is exactly what the spike
   (slices A→D) is designed to test.

**Bottom line:** compatible, via CustomPhaseCommand + `allowsUnassigned`, but it is a custom build
*against* the framework's CH+LS philosophy — not a configuration tweak. The spike's go/no-go hinges
on whether bounded backtracking scales, and Timefold's docs give that a low prior.

---

## The question, framed for Timefold
The spike wants a solver that (a) places planning entities one at a time into **hard-feasible**
positions only (never a conflict), (b) leaves an entity **unassigned** ("in the box") when no
feasible position exists, and (c) does **bounded chronological backtracking** on stall. For each,
does Timefold help or fight?

## Finding 1 — Timefold's built-in paradigm is "greedy CH + LS repair," explicitly *not* feasible-by-construction
The construction-heuristics doc states verbatim:
> "A construction heuristic builds a pretty good initial solution in a finite length of time. **Its
> solution isn't always feasible, but it finds it fast so metaheuristics can finish the job.**"

And on First Fit:
> "It assigns the planning entity to the best available planning value… It terminates when all
> planning entities have been initialized. **It never changes a planning entity after it has been
> assigned.**"

So built-in CH: assigns **all** entities (no box), picks "best available" (may be a conflict → not
hard-feasible), and **never backtracks**. This is precisely the scatter-and-repair behavior the
spike wants to replace — it is the framework's default, not an accident. (First Fit Decreasing does
order "more difficult entities first" — the MRV idea the spike wants — but still no backtrack, still
assigns all.) Source: [Construction heuristics](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/construction-heuristics).

## Finding 2 — Backtracking *exists* (Branch & Bound) but Timefold rates it 0/5 scalable
The overview's scalability table is decisive:

| Algorithm | Scalable | Optimal | Requires CH? |
|---|---|---|---|
| Brute Force | 0/5 | 5/5 | No |
| **Branch And Bound** | **0/5** | 5/5 | No |
| First Fit / FFD | 5/5 | 1–2/5 | No |
| Late Acceptance / Tabu / SA (LS) | 5/5 | 4/5 | **Yes** |

The exhaustive-search doc:
> "Exhaustive Search will always find the global optimum… That being said, **it doesn't scale (not
> even beyond toy data sets) and is therefore mostly useless.**"
> "Branch And Bound… creates a search tree that explodes exponentially as the problem size
> increases… **mostly unusable for a real-world problem due to time limitations.**"
> "**Exhaustive Search hits this wall on small datasets already, so in production these optimization
> algorithms are mostly useless. Use Construction Heuristics with Local Search instead: those can
> handle thousands of entities easily.** Throwing hardware at these scalability issues has no
> noticeable impact."

B&B *does* support the spike's ordering ideas (`entitySorterManner=DESCENDING`, `DEPTH_FIRST`,
optimistic-bound pruning) — but it still hits the exponential wall. This is the framework's own
evidence that **unbounded backtracking will not reach 12,301 lessons**; the spike's "bounded" +
"box" design is the necessary mitigation, and the docs give the scaling a low prior.
Sources: [Overview](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview),
[Exhaustive search](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/exhaustive-search).

## Finding 3 — The escape hatch is `CustomPhaseCommand`, and the project already uses it
A `CustomPhaseCommand` runs arbitrary Java (`changeWorkingSolution`) as a solver phase — usable as
the first phase to build an initial solution however you like (community pattern: "implementing a
CustomPhaseCommand as the first phase of my solver"; Red Hat's BRP guide documents `customProperties`
for benchmarker-tweakable params). **This project already implements two**: `DayClusteredConstructionPhase`
(the current construction) and `CompactionPhase` (TASK-393). So a human-faithful constructive phase
is an **architecturally consistent, drop-in replacement** for the current custom construction phase —
no new integration pattern to invent. Sources: project code (`solver/*.java`); [SO 76803188](https://stackoverflow.com/questions/76803188);
[Red Hat BRP guide §6](https://docs.redhat.com/en/documentation/red_hat_process_automation_manager/6.4/html/business_resource_planner_guide/optimizationalgorithms).

## Finding 4 — The "box" needs a one-line model change (this is also today's scatter root cause)
Code inspection of `Lesson.java`:
```java
89: @PlanningVariable(valueRangeProviderRefs = {"availableTimeslots"},
90:                   comparatorFactoryClass = TimeslotStrengthComparatorFactory.class)
91: @ManyToOne(fetch = FetchType.EAGER)
92: public Timeslot timeslot;                      // ← NO allowsUnassigned → solver MUST assign
93:
94: @PlanningVariable(valueRangeProviderRefs = {"availableRooms"}, allowsUnassigned = true)
95: @ManyToOne(fetch = FetchType.EAGER)
96: public Room room;                              // ← allowsUnassigned=true (the room can be null)
```
`timeslot` has **no `allowsUnassigned`**, so the solver is compelled to give every lesson a timeslot
— there is no legal "leave it in the box" value, which is *mechanically* why the construction scatters
into conflicts (-44,100 hard) rather than boxing the hard residual. **Enabling the box = add
`allowsUnassigned = true` to `timeslot`.** The existing **H0** constraint (`unassignedLesson`,
BaseConstraintProvider.java:209/463, built on `forEachIncludingUnassigned`) already penalizes the
unassigned state, so with `allowsUnassigned` on, H0 becomes the pressure that keeps the box small —
the solver leaves a lesson unassigned only when every feasible slot is worse than the H0 penalty.
This is a **model change** (the primary directive applies; Fedora decision), not a search-config
change, and it is the single most important enabler for the whole approach.
Sources: project code (`Lesson.java:89-96`, `BaseConstraintProvider.java`); allowsUnassigned
behavior corroborated by [SO 78740030](https://stackoverflow.com/questions/78740030) and the
[modeling building blocks](https://docs.timefold.ai/timefold-solver/latest/domain-modeling/modeling-planning-problems).

## Finding 5 — Inside (custom phase) vs outside (standalone → pins): both viable; one is already proven
- **Inside (custom phase + allowsUnassigned):** the constructive+backtrack solver runs as a Timefold
  phase, reusing the incremental score calculator, the VRP gate (`Lesson.getAvailableTimeslots`),
  constraint streams, and the Benchmarker. This is the path to a full pipeline replacement.
- **Outside (standalone constructive solver → feed pins):** the project's **pre-assignment already
  does exactly this** — `BacktrackingPreAssigner` / `HybridPhase1aService` run a CSP backtracking
  search *outside* the main solve and pin the feasible placements; Timefold then solves only the
  unpinned remainder. This is a proven, framework-friendly pattern (it doesn't fight Timefold at all
  — it just shrinks what Timefold has to solve).
- **Recommendation:** spike the **inside** approach (custom phase + `allowsUnassigned`) because it's
  the only path that replaces scatter-and-repair end-to-end and reuses the score machinery; keep the
  **outside→pins** pattern (HYBRID in TASK-396) as the fallback if bounded backtracking doesn't scale
  — it is strictly less risky because pre-assignment already proves a subset-CSP can work.

## Implications for the spike (TASK-396)
1. **Prerequisite model change (Fedora):** `Lesson.timeslot` → `allowsUnassigned = true`. Verify H0
   weight is set so the box is pressured (not free). Re-run the golden regression: the 100%-pinned
   golden must still score cleanly (pinned lessons have timeslots; H0 unchanged for them).
2. **Build the constructive solver as a `CustomPhaseCommand`** replacing the current construction
   phase, reusing the VRP gate + `canPlaceLesson` feasibility checks + `BacktrackingPreAssigner`'s
   backtrack shape. Hard-feasibility is checked in Java at placement time (not via the score during
   construction — the phase never places into a conflict).
3. **Bounded backtrack is non-negotiable** — Timefold's B&B (0/5 scalable) is the cautionary tale.
   The spike's slices A→D exist precisely to find the bound (`D`, `U`) below which backtracking stays
   sub-linear. If slice C/D shows exponential blow-up, route to the HYBRID (outside→pins) outcome.
4. **Manage expectations on the framework's philosophy:** Timefold's lead (Geoffrey De Smet) states
   the solver deliberately "gives the solution which gets you closest to a feasible solution… the
   least hard constraints broken" rather than failing — i.e., the framework is built *around*
   tolerate-and-repair. Going feasible-by-construction is a deliberate divergence; expect no built-in
   help and rely entirely on the custom phase.

## Confidence
- **High:** Findings 1, 2, 3, 4 (sourced from official Timefold 2.3.0 docs + this project's code,
  which is ground truth for the model/enablers).
- **Medium:** Finding 5's "inside is the better spike path" — that's a recommendation, not a fact;
  the outside→pins fallback is the lower-risk bet and is proven by pre-assignment.
- **The open empirical question (not researchable, must be measured):** whether bounded backtracking
   + the H0 box scales to 12,301 lessons. Timefold's docs put a low prior on backtracking scaling;
   the spike exists to settle it.

## Sources
- [Construction heuristics — Timefold Solver 2.3.0](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/construction-heuristics) — CH not feasible; First Fit no backtrack.
- [Optimization algorithms overview — Timefold Solver 2.3.0](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview) — scalability table (B&B 0/5; CH 5/5; LS requires CH).
- [Exhaustive search — Timefold Solver 2.3.0](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/exhaustive-search) — B&B "mostly unusable," exponential wall.
- [Building blocks / modeling — Timefold Solver 2.3.0](https://docs.timefold.ai/timefold-solver/latest/domain-modeling/modeling-planning-problems) — genuine planning variables, @PlanningVariable.
- Project code (ground truth): `Lesson.java:89-96` (timeslot not allowsUnassigned; room allowsUnassigned), `BaseConstraintProvider.java:209/463` (H0 unassignedLesson, forEachIncludingUnassigned), `solver/DayClusteredConstructionPhase.java` + `solver/CompactionPhase.java` (existing CustomPhaseCommand use), `service/BacktrackingPreAssigner.java` (outside→pins CSP precedent).
- Community/corroboration: [SO 76803188 (CustomPhaseCommand as first phase)](https://stackoverflow.com/questions/76803188), [SO 78740030 (allowsUnassigned behavior)](https://stackoverflow.com/questions/78740030), [Red Hat BRP guide §6 (customProperties)](https://docs.redhat.com/en/documentation/red_hat_process_automation_manager/6.4/html/business_resource_planner_guide/optimizationalgorithms), Geoffrey De Smet (Timefold lead) on the "closest-to-feasible" philosophy.
