# Critical Assessment & Experimental Roadmap — 2026-07-12

**Scope:** full review of claudedocs/research (21 docs), claudedocs/insights (13 docs), git history across all 14 branches, current solver code, and external research (Timefold releases, ITC-2019 state of the art). This document is the rationale behind backlog milestones m-2..m-6 (tasks 319+). Task executors: read the section referenced by your task before starting.

---

## 1. Where the project actually stands (verified 2026-07-12)

- **Branch reality:** `feature/cartesian-block-entity` is the ONLY active branch (all 13 others dormant since Feb/Mar 2026). Despite the name, **LabBlock no longer exists** — TASK-228 denormalized lab blocks into pinned `Lesson` rows and TASK-267 (commit 0a91e97, 2026-03-17) deleted the entity. Labs = pinned Lessons with `isLabHour=true`.
- **Stack:** Timefold **2.1.0** community edition, Java 21, Quarkus. The 1.31.0 IdentityHashSet patch is obsolete (fixed upstream in 1.32.0, PR #2140).
- **Pipeline (solverConfig.xml, 6 phases):** DayClustered custom CH → fallback CH → LS Compactness (tabu 7, change/swap/pillar on timeslot) → LS RuinRecreate (ruin **2–5**, tabu 7) → CH room-only → LS room refinement. All phases `diminishedReturns`-terminated (2 min / 0.1), global cap 8 h. Single-threaded (multithread/nearby selection/partitioned search are **Enterprise-only** — not available to us).
- **No current baseline exists.** Every documented benchmark predates the May–Jul changes (6-phase pipeline, RuinRecreate, diminishedReturns, Timefold 2.x). The best documented number is CH-only from March: **-2002 hard / -1.95 M medium / -190 K soft**, with M20 (sparse days) contributing -1.944 M of the medium score. No clean 0hard has ever been recorded.
- **The core unsolved problem is compaction**, in two coupled forms: (a) hard score dominated by H15/H17-class violations, (b) medium score dominated by M20 sparse days. Best group densities after the day-first CH rewrite: G01 4.83 lessons/day vs human target 5.53 (88%); **G09 stuck at 3.66/day, cause never diagnosed**.
- **The dataset itself may be infeasible:** T28 / G10 is documented "IMPOSSIBLE" (11 blocks needed, 9 slots available) in `.claude/rules/datagenerator-protection.md`; several DataGenerator vs spalmone hour discrepancies remain. **If input data is infeasible, no solver tuning will ever reach 0hard.** This has never been settled.

## 2. Critical evaluation of the research so far

**What was done well:** root-causing the CH scatter problem (subject-first vs day-first, solver-config-analysis-20260313), the toList() → toConsecutiveSequences incremental-scoring fix, the LabBlock→pinned-Lesson denormalization (correctly implemented the labblock-vs-individual-lessons recommendation), abandoning the two-level DayAssignmentCsp (correct: day-level granularity cannot model the T06 informatica bottleneck).

**Methodological weaknesses that explain why no conclusive result exists:**
1. **No standard experiment protocol.** Runs used 90 s, 120 s, 6 min, different branches, different datasets. Nothing is comparable to anything else. The pillar-moves benchmark (2026-03-17) was confounded (baseline stalled in Phase 3 from an unrelated bug) yet its recommendation was merged anyway.
2. **Key decisions never re-validated after architecture changes.** M13→H17 promotion (hard compaction) contradicts the project's own 2026-03-07 benchmark (hard compaction = 40% worse hard score, +76 s CH); that trade-off was never re-measured after LabBlock removal, R&R, or Timefold 2.x. Tabu size 7 comes from an "unverified prior benchmark round".
3. **Projected impacts treated as results.** The full canPin() validator ("~88% H15 reduction") was specced with code but never measured.
4. **Feasibility never audited mathematically.** Success criterion (strict 0hard vs. 0-conflicts-with-minimal-unassigned) was never defined against a lower-bound analysis of the data.
5. **Docs drifted badly** (CLAUDE.md still says Timefold 1.31/LabBlock family/28 teachers; branch-families.md claims 3 active branches and archive/* branches that do not exist; orphaned preAssignmentPhase1a/1b XMLs reference the deleted LabBlock class; GapPreservingMoveFilter and UnpinnedLessonFilter are dead code — the former is a stub returning null). Any agent trusting the docs will make wrong decisions.

## 3. Structural risks in the current design (hypotheses to test)

- **R&R too timid:** ruinedCount 2–5 vs Timefold default 5–20; official guidance says raise it for large datasets. Ruining 2–5 lessons cannot restructure a day of 6 slots, let alone a week — likely useless against compaction traps. (High-leverage, cheap experiment.)
- **Score trap / hump crossing:** with H17+H15 hard, many improving trajectories require temporarily worsening hard score. Tabu 7 cannot cross humps; Late Acceptance / Simulated Annealing are the textbook acceptors for this (Timefold discussion #951). Never tried here.
- **Room-deferred pipeline may be unrepairable:** phases 1–4 optimize timeslots with rooms unassigned; phase 5 CH assigns rooms; phase 6 can only move rooms. If room assignment creates conflicts that need *timeslot* moves, nothing downstream can fix them. Never verified.
- **DiminishedReturns windows (2 min / 0.1) may terminate phases long before the improvement tail** — Timefold docs say large problems keep improving for hours/days. Never tested with long windows or an overnight run.
- **Timefold 2.3.0 (released 2026-07-10)** explicitly claims: "significantly improved underlying metaheuristics so that in those few cases where the solver would previously get stuck, it no longer does" + an R&R unassigned-values fix (#2448). We are on 2.1.0. Cheapest high-potential experiment available.

## 4. State of the art (external, verified)

- **ITC-2019 winner** (Mikkelsen & Holm, J. of Scheduling 2022): parallel **fix-and-optimize matheuristic** — fix most of the solution, re-optimize a small free subset, adapt subset size, diversify on stagnation. Directly implementable on community Timefold via pinning + repeated short solves. This is the strongest architectural idea we have not tried; our pre-assignment pipeline is philosophically the "fix" half already.
- **Timefold official patterns:** union coarse-grained moves (pillar/R&R) at low probability with fine-grained ones; increase penalty granularity to remove flat score plateaus; "continuous planning" (rolling window with locked prefix) is the documented decomposition for long horizons.
- **Community edition boundaries:** moveThreadCount, nearby selection, partitioned search = Enterprise. Do not plan around them.

## 5. Experimental method (mandatory for all m-3/m-4/m-5/m-6 experiment tasks)

1. All experiments run through the standard protocol script (task 319) on the same DB profile, same seed data, fixed termination; scorecards saved to `local/experiments/`.
2. Every experiment is compared against the m-2 baseline (task 320) — 3 repetitions minimum where variance matters; report mean and per-run values.
3. Record results in the task Final Summary AND append one row to `claudedocs/research/experiment-log.md` (create on first use): date, task, config delta, hard/medium/soft, unassigned, avg lessons/day, wall-clock, verdict.
4. One variable at a time. If a change helps, it becomes the new baseline only after a confirming re-run.
5. A negative result is a valid deliverable: document it and close the task. Do NOT stack a second workaround on a failed idea — go back to the hypothesis.

## 6. Recommended execution order (milestones m-2..m-6, tasks 319-340)

Dependencies are also encoded in the task frontmatter; this is the intended sequence for an executor picking the next task.

**Phase 0 — Foundation (blocking, do first, in this order):**
1. TASK-319 — standardized experiment protocol + scorecard script (everything else depends on it)
2. TASK-320 — current-state baseline x3 (the reference for every A/B)
3. TASK-321 — feasibility audit (defines the success criterion; may reveal the target itself must change)
4. TASK-322 — docs truth pass + dead code removal (can run in parallel with 320/321)

**Phase 1 — Quick wins (m-3):**
5. TASK-323 — Timefold 2.1.0 → 2.3.0 upgrade + A/B (do FIRST in m-3: all later experiments should run on the upgraded solver)
6. TASK-324 — RuinRecreate ruined-count sweep (highest expected leverage on the stuck-solver symptom)
7. TASK-325 — acceptor sweep (tabu vs Late Acceptance vs SA — the other half of the score-trap hypothesis)
8. TASK-326 — termination policy + overnight improvement curve
9. TASK-327 — constraint-stream throughput pass

**Phase 2 — Structural diagnostics and experiments (m-4):**
10. TASK-328 — room-deferral diagnostic (cheap, answers a structural question)
11. TASK-330 — H17 hard vs heavy-medium re-benchmark (the biggest open score-landscape question)
12. TASK-331 — progressive constraint tightening two-stage solve (builds on 330 findings)
13. TASK-329 — mixed repair Phase 7 (ONLY if TASK-328 verdict is YES)
14. TASK-332 — pillar moves clean A/B

**Phase 3 — Architectural bet (m-5):**
15. TASK-333 — fix-and-optimize design doc → **USER REVIEW GATE**
16. TASK-334 — implement FixAndOptimizeService
17. TASK-335 — benchmark vs long single run (milestone gate)
18. TASK-336 — rolling-horizon fallback (ONLY if TASK-335 misses the success criterion)

**Phase 4 — Data and pre-assignment (m-6; 337 can start any time after 321):**
19. TASK-337 — DataGenerator vs spalmone decision document → **USER DECISION GATE**
20. TASK-338 — MODA LabRotation proposal → **USER DECISION GATE**
21. TASK-339 — pinned-lesson violation measurement (+ canPin extension if warranted)
22. TASK-340 — solve WITHOUT pre-assignment A/B

**Standing rules for executors:** never run two experiments concurrently on the same profile; one variable at a time; every result (positive or negative) gets a row in claudedocs/research/experiment-log.md; a winner replaces the baseline only after a confirming re-run; the three gates above stop for the user — do not proceed past them autonomously.

## 7. Success criterion (to be finalized by task 321)

Target, pending feasibility audit: **0 hard-conflict violations (H1–H3, H5–H7, H11)** with **minimal unassigned lessons (H0)** if the audit proves full assignment infeasible, **avg lessons/day ≥ 90% of spalmone target for every group**, within ≤ 2 h wall-clock, reproducibly (3/3 runs). The audit may legitimately conclude that some hours must be dropped/negotiated with the school — that is a data decision, not a solver failure.

### Finalized by TASK-321 (2026-07-14 feasibility audit)

The audit (`claudedocs/research/feasibility-audit-2026-07-14.md`, via `scripts/analyze/feasibility-audit.py`) finds the dataset **INFEASIBLE by construction on exactly one dimension**: student group **G04 requires 1037h against 1004 available slots (+33h deficit)**. Every other group, every teacher, and every room tag fits its generous maximum capacity. Two long-standing assumptions are overturned:

- **T28 / G10 "IMPOSSIBLE" is stale.** T28 is feasible at current hours (198h vs 210 Thursday slots; the old 244h was reduced). The script correctly reports it feasible — the documented infeasibility was period-scoped or pre-audit.
- **G09's "stuck at 3.66/day" is NOT a capacity ceiling** (643h vs 1004 available). It is a solver-behavior problem, so TASK-330-style hard/medium re-benchmarks address it; capacity is not the constraint.

The G04 deficit (+33h over capacity, **+47h over the 990h peer median**) pinpoints a specific data discrepancy: the **"Larsa" course (47h)** accounts exactly for the excess over peers. This is a **TASK-337 data decision**, not a solver target.

**Audited success criterion:** 0 hard-conflict violations (H1–H3, H5–H7, H11) with **unassigned ≤ the audited floor** — currently **≈33h on G04**, dropping to ~0 only if TASK-337 resolves the "Larsa" discrepancy; plus avg lessons/day ≥ 90% of spalmone target, ≤ 2h wall-clock, 3/3 reproducible. **Implication: resolve the G04 / "Larsa" data question (TASK-337) before further solver tuning — it is the single structural blocker to 0 unassigned, and it is independent of the baseline's -115hard (which is M22 calendar-compactness + H4/H4.3 stage, not a capacity issue).**
