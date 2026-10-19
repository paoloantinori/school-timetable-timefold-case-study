# VERDICT: Benchmark harness repair (Timefold 2.3.0), 2026-08-04

**Task:** TASK-284 (benchmark VND variants). This doc covers the harness-repair
prerequisite (unblock the benchmark vehicle). The VND comparison run itself is in
`VERDICT-tabuvnd-benchmark-*.md`.

**Outcome:** the benchmark harness, broken since the Timefold 1.x to 2.3.0 move
(last worked March 2026), is **repaired and proven end-to-end**. DoD complete.
NO ADOPTION of any solver change (this is test infrastructure only).

## What was broken (TWO independent blockers, not one)

The handoff (`handoff-task284-vnd-2026-08-04.md`) censused only the compile blocker.
Running it revealed a second, runtime blocker. Both fixed.

### Blocker 1: compile (censused)
The whole `**/benchmark/**` package was hidden from compilation by
`<testExclude>**/benchmark/**</testExclude>` in `maven-compiler-plugin`. Two causes:
1. **API drift.** All 8 benchmark classes called `PlannerBenchmark.benchmarkAndShowReportInBrowser()`,
   the Timefold 1.x API. In 2.3.0 that method is gone; the only method is
   `benchmark()` returning `java.io.File` (verified `javap` on the .3.0 jar).
2. **`SchoolScheduleSolutionFileIO` extended `ai.timefold.solver.jackson...JacksonSolutionFileIO`**,
   which in 2.3.0 is the "Jackson 3" module (`tools.jackson`). The class was written
   against Jackson 2.x (`com.fasterxml`), so it no longer compiled.

### Blocker 2: runtime JAXB `InternalError` (NOT censused, the deeper one)
After the compile fix, the first benchmark run threw
`java.lang.InternalError: Error occured while invoking reflection on target classes.
... interface jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter` during
`DefaultPlannerBenchmark.<init>`, then `BenchmarkResultIO`, then `GenericJaxbIO`, then
`JAXBContext.newInstance`.

**Root cause (verified on the module pom):** `timefold-solver-benchmark` 2.3.0 has a
**real compile dependency on `timefold-solver-jaxb`** (it provides the JAXB adapters and
context wiring the benchmark report writer needs). The project pom **excluded** it
(comment: "POM isn't cached", true in March, but the network is reachable now).
Excluding a needed dep means JAXB context creation fails. NOT a version skew (production
uses a consistent JAXB 4.0.5/4.0.6), NOT the Quarkus classloader (refuted: removing
`@QuarkusIntegrationTest` did not help).

## The fix (commit 1)

- **8 classes:** `benchmarkAndShowReportInBrowser()` becomes `benchmark()` (forced by the API;
  the old method also opened a browser, the new one just writes the report). Zero
  correctness loss, and correct for a headless/server context.
- **`SchoolScheduleSolutionFileIO`:** `extends JacksonSolutionFileIO` becomes `implements SolutionFileIO`
  (the public SPI in `core.api`, not an internal `impl` class) with a direct Jackson 2.x
  `ObjectMapper`. The old parent was a trivial wrapper (decompiled: `mapper.readValue/writeValue`,
  **no** Timefold planning-entity registration), so the direct impl is equivalent. Jackson 2.x
  is already on the classpath via `quarkus-rest-jackson`; no new dep, no Jackson 3.x migration.
- **pom.xml:**
  - Removed `<testExclude>**/benchmark/**</testExclude>` (compile blocker 1). It was the wrong
    tool anyway (it hid a real compile error).
  - Removed the `timefold-solver-jaxb` exclusion from `timefold-solver-benchmark` (runtime
    blocker 2). Kept the `timefold-solver-jackson` exclusion (we use Jackson 2.x directly).
  - Deleted a stale commented-out dependency block.
  - **Added `<excludes><exclude>**/benchmark/**</exclude></excludes>` to `maven-failsafe-plugin`.**
    Without it, `mvn verify` would auto-run `SchoolScheduleBenchmarkIT` (the only `*IT.java`,
    `@QuarkusIntegrationTest`, needs a live app) and fail. This mirrors the old compile-exclude
    intent at the correct layer (execution, not compilation). `verify.sh -c X` sets `-Dit.test`,
    which **overrides** pom excludes (verified), so on-demand invocation still works.
    surefire (`test.sh`) is safe by naming (`*Benchmark`/`*IT` don't match `*Test`).

## Proof (evidence, not assertion)

- **Compile:** `rebuild --build-only` BUILD SUCCESS; all 9 `.class` files present;
  `javap` confirms `SchoolScheduleSolutionFileIO implements SolutionFileIO<SchoolSchedule>`
  with the generic bridge methods.
- **End-to-end run:** `TerminationVerificationBenchmark` (1 solver, 300s) ran via
  `test.sh -c` and **passed** (`Tests run: 1, Errors: 0`, `PASS: true`), producing a full
  report (`index.html`, `plannerBenchmarkResult.xml`). This proves the harness reads the
  input via the new SolutionFileIO, builds the benchmark, solves, and writes the report.
- **Normal suite unaffected:** `test.sh` (full suite) EXIT 0, `Tests run: 988, Failures: 0,
  Errors: 0, Skipped: 23`. Zero benchmark classes ran in the suite (naming convention
  confirmed empirically).

## DoD

- `/simplify` (4 agents): applied. Removed the redundant `getOutputFileExtension()` override
  (interface default delegates to `getInputFileExtension`), dead `TimeUnit` import
  (TabuVsVNDBenchmark), 3 dead nio imports (Phase3BenchmarkRunner). Skipped (annotated):
  `createObjectMapper()` duplication in `SchoolScheduleBenchmarkIT` (pre-existing, low value,
  out of scope); efficiency note on Timefold module (empirically refuted).
- `/code-review high` (correctness, 30 tool-uses incl. bytecode decompilation): **0 bugs**.
  All 4 concerns resolved with evidence (API forced; old parent was a trivial wrapper so
  direct impl is equivalent; failsafe exclude correct; no swallow/null/leak). One
  theoretical residual (`FailureAnalysis` no no-arg ctor) is pre-existing and moot for the
  benchmark workflow.
- `test.sh` EXIT 0 (above).

## What this unblocks

The benchmark vehicle for the whole campaign: TASK-284 (VND/metaheuristic comparison),
TASK-296 (acceptedCountLimit screen, whose config `solverBenchmarkConfigAcceptedCountLimit.xml`
was already scaffolded but unrunnable), and any future metaheuristic benchmark. All via the
proper `solverBenchmarkConfig*.xml` + Timefold benchmarker report, no longer forced to the
live staged-sweep workaround.

## Known follow-up

The benchmark configs run solverBenchmarks **sequentially** (no `<parallelBenchmarkCount>`),
so the 9-config `TabuVsVND` run takes ~108 min. Adding `<parallelBenchmarkCount>AUTO` makes
it tractable (~one-wave wall). See the companion results VERDICT for the TASK-284 run.
