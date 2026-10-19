# TASK-386 Honest Metaheuristic Benchmark — laptop Results & Verdict

**Procedure:** `scripts/benchmark/laptop-RUNBOOK.md` — 6 phase-2 config arms, sequential live A/B
(full fresh-profile cycle each), ~75–127 min per arm, plus 2 stability re-runs of the leading
contenders. Within-laptop comparability only; absolute times are NOT comparable to Fedora runs 12–16.
**Run dates:** 2026-07-21 → 2026-07-22. **Solver:** phase-2 budget pinned to 3600s for every arm.

## Per-run results (single run unless noted)

| arm | phase-2 config | final hard | medium | soft | phase-2 end hard | full-day | rec% | blocks≥2h |
|-----|----------------|-----------:|-------:|-----:|-----------------:|---------:|-----:|----------:|
| A   | tabu 7, acl 1000 (control)        | −1062 | −1,452,018 | +76,594 | −2198 | 78.0% | 37.1 | 2077 |
| B   | **late acceptance 400**, acl 1000 |  −128 | −1,651,738 | −88,555 |  −387 | **66.9%** | 45.4 | 2086 |
| C   | tabu 7, acl 500                   |  −123 | −1,486,294 | +79,494 | −1139 | 78.2% | 37.0 | 2048 |
| D   | tabu 7, acl 2000                  |  −302 | −1,416,817 | +73,823 |  −317 | 78.2% | 37.8 | 2024 |
| E   | tabu 11, acl 1000                 | −1001 | −1,459,726 | +75,796 | −2019 | 78.4% | 36.0 | 2040 |
| F   | tabu 7, acl 1000, **no pillars**  |  −303 | −1,437,493 | +80,377 | −1320 | 77.8% | 39.0 | 2117 |
| C-r2 | tabu 7, acl 500 (re-run)         | −1002 | −1,418,015 | +79,512 | −1024 | 78.6% | 36.2 | 2096 |
| D-r2 | tabu 7, acl 2000 (re-run)        | −1541 | −1,468,260 | +77,316 | −1562 | 78.3% | 38.0 | 2073 |

## Stability re-runs — the decisive evidence

The run-16 finding (Fedora, same config → −426 vs −3902 hard, a 9× spread) demanded re-running
the top contenders before any verdict. The two leaders on single-run hard were re-run:

| config | run 1 final hard | run 2 final hard | swing | run 1 phase-2 | run 2 phase-2 |
|--------|-----------------:|-----------------:|------:|--------------:|--------------:|
| C (acl 500)       |  −123 | −1002 | **879** (8×) | −1139 | −1024 |
| D (acl 2000)      |  −302 | −1541 | **1239** (5×) |  −317 | −1562 |

Both leaders regressed straight back into the A/E cluster (−1000 region) on the second run. The
single-run leaderboard's top four (C −123, B −128, F −303, D −302) was variance-dominated; the
re-runs collapse the two that were tested. This replicates run-16's variance finding on laptop
machine-independently: same-config final-hard spreads of 879 and 1239, same order of magnitude
as Fedora's 3476.

## What is and isn't signal

**Signal (robust across runs / arms):**
1. **Late acceptance (arm B) is disqualified.** It is the *only* config that collapsed a KPI:
   daily-fill 66.9%, well under the ~74% threshold (all tabu arms 77.8–78.6%). It also took the
   longest (127 min) and produced the worst medium/soft. Verdict criterion #4 ("an arm that wins
   on score by shredding the KPIs loses") removes it. The tabu family is confirmed superior to
   late acceptance for this problem.
2. **The phase-2 metaheuristic is NOT the lever.** At n ≤ 2 per config, no tabu variant
   (entityTabuSize 7 vs 11; acceptedCountLimit 500 vs 1000 vs 2000; pillars on vs off) shows a
   reproducible advantage over the control. C's two phase-2 ends were the most self-consistent
   (−1139 / −1024 ≈ −1080) but mediocre; D's were high-variance (−317 / −1562). The single-run
   gaps were noise.

**Not signal (within the noise floor):**
- Any ranking of C/D/E/F/A on final hard. The re-runs show these are statistically
  indistinguishable at this sample size.
- The apparent "C and B tie at ~−125" top of the single-run board — both were lucky/degenerate
  (C a lucky phase-3 basin, B a KPI-shredding degenerate).

## Where the variance lives

C's two phase-2 ends were consistent (−1139 / −1024); its 879-hard final swing came **entirely
from phase-3 ruin-recreate** (run-1 found a −1139→−138 basin; run-2 got −1024→−1024, no such
basin). Phase 3 is identical across all arms (it is not the knob being varied). **The final
score is therefore dominated by a phase-3 lottery that no arm controls**, which is why the
single-run final-hard leaderboard measured luck, not the phase-2 metaheuristic.

## Recommendation

1. **Do not adopt a new phase-2 config on the strength of this benchmark.** The runbook's own
   stability mandate ("prefer an arm that lands ~−800 twice over one that lands −426 once and
   could be −3902 the next time") is decisive: no arm landed reliably low. Keep the canonical
   config (A) as the standing choice.
2. **Late acceptance is ruled out** (KPI collapse). Stay in the tabu family.
3. **Attack performance via a different lever**, not the phase-2 tabu knobs this benchmark
   swept — they carry no reproducible signal. Candidates, in rough promise order:
   - The **phase-3 ruin-recreate** configuration (it dominates the final score; it is the actual
     high-variance lever and the most likely place to reduce spread or improve the typical basin).
   - The **construction heuristic** (phase 1) and the weekly-template stamp — a better starting
     point shrinks the basin the LS has to climb out of.
   - **More samples per config** (n=2 is insufficient to resolve distributions that swing 5–9×;
     a config with a genuinely better *mean* would need ~5 runs to surface above this noise).
4. Per the **primary directive** (CLAUDE.md): this is a search-configuration problem, never a
   reason to touch the logical model. The model (constraints/targets) is unchanged and correct;
   the result above is purely a search-knob finding.

## Run conditions: heap sizing (run-17 reconciliation)

While this laptop benchmark was running, the Fedora session landed TASK-387 / run 17
(commits `761a0468`, `df07bff4`, `bbb8b73e`) and added a "⚠️ Heap sizing — READ
FIRST" section to `laptop-RUNBOOK.md` recommending `JAVA_TOOL_OPTIONS=-Xmx2g` for
every arm. **These 8 runs used the JVM default (~260MB) heap**, because the
benchmark was started from the runbook revision *before* that section existed
(I read `laptop-RUNBOOK.md` at session open; the heap commits arrived mid-run).

This does **not** affect the verdict, for two reasons:
1. **All 8 runs used the same heap** → arm-vs-arm comparability (ground rule #1) is intact.
2. Run 17's own conclusion is that heap "does NOT bias the metaheuristic ranking"
   (total GC overhead ≈2.3% of wall-clock at *both* 260MB and 2GB; hard score stays
   "in variance band"). A 2.3% GC tax is negligible next to the 5–9× run-to-run
   variance measured here. Run 17's independent finding ("hard score in variance
   band") corroborates this benchmark's central conclusion from a separate machine.

Caveat: the reported solve times (74–127 min) were measured at 260MB and are
therefore slightly GC-padded versus a 2GB run — but equally so across all arms,
so no ranking distortion. (Phase terminations are wall-clock `secondsSpentLimit`/
`unimprovedSecondsSpentLimit`; GC pauses count against them, but at a fixed ~2.3%
that is common across arms and heap sizes.)

## Reproducibility / artifacts

Each `arm-*/` folder holds: `score.txt`, `phases.json` (per-phase endScores), `analysis.json`
(constraint breakdown), `lessons.txt` (raw placement dump, KPI source), `kpi-daily-fill.txt`,
`kpi-resemblance.txt`. `SUMMARY.md` is the per-arm narrative log. The phase-1 endScore was
byte-identical (`−57457hard/−2053121medium/−123080soft`) across all 8 runs, confirming the
deterministic construction-heuristic baseline and a clean A/B (every arm started from the same
point; divergence is purely phase-2 onward).
