# Benchmark running instructions (reconstructed)

> 📌 **Reconstructed 2026-07-12** from the surviving auto-memory index (`MEMORY.md` quick-reference), then verified against the live tree. The original `memory/benchmark/running-instructions` file was lost. Script usage and config counts are verified; the "best result" line is from memory and unverified.

## Primary tool: `run-benchmarker.sh`

Runs the Timefold benchmarker with progress tracking and error handling. Continues even if individual benchmarks fail.

```bash
./scripts/benchmark/run-benchmarker.sh [config-file]
```

- `config-file` (optional): path to a solver benchmark config XML. Default: `src/main/resources/solverBenchmarkConfig.xml`.
- **Outputs:** reports → `local/benchmarkReport`; logs → `local/benchmark-logs/benchmarker-<timestamp>.log`; PID file → `local/benchmark-logs/benchmarker.pid`.
- **Env vars:** `MAVEN_OPTS` (JVM options, e.g. `-Xmx8G`), `BENCHMARK_DIR` (override report output dir), `BENCHMARK_SYSTEM_PROPERTIES` (extra `-D` flags spliced into the mvn line; surefire passes them to the forked benchmark JVM, e.g. `-Dplanner.room-tier.override=zero` for the clean room-tier regime; the runner must print the `ROOM-TIER OVERRIDE (benchmark battery): ... ARMED` banner).

## Two standard configs (verified 2026-07-12)

| Config | `<solverBenchmark>` entries | Typical runtime | Command |
|--------|----------------------------|-----------------|---------|
| `solverBenchmarkConfigTest.xml` | **3** | ~45–60 min | `./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfigTest.xml` |
| `solverBenchmarkConfig.xml` | **15** | ~5 hours | `./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfig.xml` |

Entry counts verified via `grep -c '<solverBenchmark>'`. Runtimes are from the original memory note.

## Best known result (from memory, unverified)

Tabu search with **entity tabu size 7** was the best performer in the prior benchmark round. Re-run to confirm against current configs.

## Other benchmark scripts

| Script | Purpose |
|--------|---------|
| `scripts/benchmark/benchmark-branch.sh [branch] [duration-min]` | Benchmark a single branch |
| `scripts/benchmark/run-benchmark.sh` | Alternate runner |
| `scripts/benchmark/benchmark-h5.sh` | H5-constraint benchmark |
| `scripts/benchmark/benchmark-experiment.sh` | Experimental benchmark |
| `scripts/benchmark/overnight-benchmark.sh` | Overnight multi-run |
| `scripts/benchmark/simple-overnight-benchmark.sh` | Simpler overnight variant |
| `scripts/benchmark/benchmark-pillar-moves.sh` | Pillar-moves comparison |

`benchmark-branch.sh` is a good reference for the correct server-startup pattern (it uses `scripts/core/dev-start-and-wait.sh`; see `CLAUDE.md`).

## ✅ `api.sh raw` migration (2026-07-12)

The benchmark helper scripts (`overnight-benchmark.sh`, `simple-overnight-benchmark.sh`, `benchmark-pillar-moves.sh`) and the `full-test` / `laptop-test` skills previously called `api.sh raw ...`, which no longer existed (returned "Unknown command: raw"). They have been migrated to named commands (`reset`, `analysis`, `pre-assign-status`, `-j status`), and a new `phase-progression` command was added to `api.sh` for `/api/phase-progression`, which had no named equivalent. The main `run-benchmarker.sh` path was always unaffected. (A few dated `claudedocs/` reports still mention `api.sh raw` as historical record.)
