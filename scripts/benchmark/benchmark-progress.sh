#!/usr/bin/env bash
#
# Monitor Timefold benchmark progress by polling the report directory.
# Shows which solvers have completed and their best scores.
#

BENCHMARK_DIR="local/benchmarkReport"
POLL_INTERVAL=5  # seconds between polls (can be increased for longer benchmarks)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if benchmark directory exists
if [ ! -d "$BENCHMARK_DIR" ]; then
    echo -e "${RED}Error: Benchmark directory not found: $BENCHMARK_DIR${NC}"
    echo "Start the benchmark first with: ./scripts/benchmark/run-benchmarker.sh"
    exit 1
fi

# Get total solver count from config
TOTAL_SOLVERS=$(grep -c '<name>' /Users/user/planner/src/main/resources/solverBenchmarkConfig.xml 2>/dev/null || echo "0")
TEST_MODE=0
if [ -f "src/main/resources/solverBenchmarkConfigTest.xml" ]; then
    TOTAL_SOLVERS=$(grep -c '<name>' /Users/user/planner/src/main/resources/solverBenchmarkConfigTest.xml 2>/dev/null || echo "3")
    TEST_MODE=1
fi

echo -e "${BLUE}============================================================"
echo "  Benchmark Progress Monitor"
echo "============================================================${NC}"
echo "Directory: $BENCHMARK_DIR"
echo "Total solvers: $TOTAL_SOLVERS"
echo "Poll interval: ${POLL_INTERVAL}s"
echo ""

# Track previously seen solvers to detect new completions
declare -A seen_solvers
declare -A solver_scores
solver_count=0

while true; do
    # Look for benchmark result HTML files
    # Timefold creates these as each solver completes
    if [ -f "$BENCHMARK_DIR/index.html" ]; then
        # Parse HTML for solver results
        # Look for table rows with solver names and scores
        current_count=0

        # Extract solver names and best scores from HTML
        # This is a simple grep-based approach; for more robust parsing, we'd use a proper HTML parser
        while IFS= read -r line; do
            if [[ $line =~ \<td\ class=\"solverName\"\>([^<]+) ]]; then
                solver_name="${BASH_REMATCH[1]}"
                if [ -z "${seen_solvers[$solver_name]}" ]; then
                    seen_solvers[$solver_name]=1
                    ((solver_count++))

                    # Try to extract best score from nearby line
                    score_line=$(grep -A 3 "$solver_name" "$BENCHMARK_DIR/index.html" | grep -o 'class="bestScore"[^>]*>[^<]*<' | head -1)
                    if [[ $score_line =~ \>[^0-9-]*([-0-9]+) ]]; then
                        score="${BASH_REMATCH[1]}"
                        solver_scores[$solver_name]=$score
                        echo -e "${GREEN}[NEW]${NC} $solver_name - Best score: ${BLUE}$score${NC}"
                    else
                        echo -e "${GREEN}[NEW]${NC} $solver_name"
                    fi
                fi
            fi
        done < <(grep "solverName" "$BENCHMARK_DIR/index.html" 2>/dev/null)
    fi

    # Show progress bar
    if [ $solver_count -gt 0 ]; then
        progress=$((solver_count * 50 / TOTAL_SOLVERS))
        printf -v bar "%${progress}s" ""
        bar="${bar// /█}"
        printf -v empty "%$((50 - progress))s" ""
        echo -ne "\r${BLUE}Progress:${NC} [$bar${empty}] $solver_count/$TOTAL_SOLVERS solvers complete"
    fi

    # Check if all solvers completed
    if [ $solver_count -ge $TOTAL_SOLVERS ]; then
        echo ""
        echo -e "${GREEN}✓ Benchmark complete!${NC}"
        echo ""
        echo "Summary:"
        for solver in "${!solver_scores[@]}"; do
            printf "  %-40s %s\n" "$solver" "${solver_scores[$solver]}"
        done
        echo ""
        echo "Open report: open $BENCHMARK_DIR/index.html"
        break
    fi

    sleep $POLL_INTERVAL
done
