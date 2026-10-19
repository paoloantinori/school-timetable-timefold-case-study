#!/usr/bin/env bash
# run-benchmark.sh — Run Timefold benchmarks
#
# Usage:
#   ./scripts/benchmark/run-benchmark.sh [--fast] [--full] [--ci] [--report] [--skip-build]
#
# Options:
#   --fast       Run fast mode (30s per config) - default for CI
#   --full       Run quality mode (2min per config) - thorough testing
#   --ci         Enable CI mode (regression detection)
#   --report     Generate HTML report
#   --skip-build Skip Maven build (use existing compiled classes)
#
# Examples:
#   # Quick smoke test
#   ./scripts/benchmark/run-benchmark.sh --fast
#
#   # Full quality benchmark
#   ./scripts/benchmark/run-benchmark.sh --full --report
#
#   # CI regression check
#   ./scripts/benchmark/run-benchmark.sh --fast --ci

set -euo pipefail

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Default configuration
MODE="baseline"
DURATION_SEC="30"
RUN_COUNT="1"
CI_MODE=false
SKIP_BUILD=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --fast)
            MODE="fast"
            DURATION_SEC="30"
            shift
            ;;
        --full)
            MODE="quality"
            DURATION_SEC="120"
            shift
            ;;
        --ci)
            CI_MODE=true
            shift
            ;;
        --report)
            # Report generation is automatic
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--fast] [--full] [--ci] [--report] [--skip-build]"
            echo ""
            echo "Options:"
            echo "  --fast       Run fast mode (30s per config) - default for CI"
            echo "  --full       Run quality mode (2min per config) - thorough testing"
            echo "  --ci         Enable CI mode (regression detection)"
            echo "  --report     Generate HTML report (automatic)"
            echo "  --skip-build Skip Maven build (use existing compiled classes)"
            echo ""
            echo "Examples:"
            echo "  $0 --fast"
            echo "  $0 --full"
            echo "  $0 --fast --ci"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Change to project root
cd "$PROJECT_ROOT"

echo "=== Timefold Benchmark Runner ==="
echo "Mode: $MODE"
echo "Duration: ${DURATION_SEC}s"
echo "Runs: $RUN_COUNT"
echo "CI Mode: $CI_MODE"
echo "Skip Build: $SKIP_BUILD"
echo ""

# Build the project (unless explicitly skipped)
if [[ "$SKIP_BUILD" != "true" ]]; then
    echo "Building project..."
    if ! mvn clean compile test-compile -DskipTests -Dnet.bytebuddy.experimental=true; then
        echo ""
        echo "❌ Build failed!"
        exit 1
    fi
    echo "✓ Build complete"
    echo ""
fi

# Determine which test method to run
TEST_METHOD=""
case "$MODE" in
    fast)
        TEST_METHOD="fastBenchmark"
        ;;
    quality)
        TEST_METHOD="qualityBenchmark"
        ;;
    baseline|*)
        TEST_METHOD="baselineBenchmark"
        ;;
esac

# Run the benchmark
echo "Running benchmark (mode: $MODE)..."
echo ""

if ! mvn test -Dtest=SchoolScheduleBenchmark#"${TEST_METHOD}" \
    -Dbenchmark.mode="${MODE}" \
    -Dbenchmark.seconds="${DURATION_SEC}" \
    -Dbenchmark.runs="${RUN_COUNT}"; then
    echo ""
    echo "❌ Benchmark failed!"
    exit 1
fi

echo ""
echo "=== Benchmark Complete ==="
echo ""
echo "Results summary is printed above."
echo ""

# CI regression detection
if [[ "$CI_MODE" == "true" ]]; then
    echo "Running regression detection..."
    if python3 "$SCRIPT_DIR/check-regression.py"; then
        echo "✓ No regression detected"
    else
        echo "❌ Regression detected!"
        exit 1
    fi
    echo ""
fi

echo "=== Summary ==="
echo "Benchmark mode: $MODE"
echo "Duration: ${DURATION_SEC}s per run"
echo "Runs: $RUN_COUNT"
echo "Status: Complete"
echo ""

exit 0
