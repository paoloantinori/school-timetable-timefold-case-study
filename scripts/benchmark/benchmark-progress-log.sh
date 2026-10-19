#!/usr/bin/env bash
#
# Monitor Timefold benchmark progress by parsing Quarkus log output.
# Shows phase transitions and score improvements in real-time.
# Compatible with bash 3.2 (macOS default).
#

LOG_FILE="target/quarkus.log"
POLL_INTERVAL=2  # seconds between log checks

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Shared function to format score with colors (single sed pass)
format_score() {
    local score="$1"
    echo "$score" | sed -E \
        -e "s/(-?[0-9]+)hard/${RED}\1hard${NC}/g" \
        -e "s/(-?[0-9]+)medium/${YELLOW}\1medium${NC}/g" \
        -e "s/([0-9]+)soft/${CYAN}\1soft${NC}/g"
}

# Check if log file exists
if [ ! -f "$LOG_FILE" ]; then
    echo -e "${RED}Error: Log file not found: $LOG_FILE${NC}"
    echo "Start the benchmark first with: mvn test -Dtest=LocalSearchComparisonBenchmark"
    exit 1
fi

echo -e "${BLUE}============================================================"
echo "  Benchmark Progress Monitor (Log Parser)"
echo "============================================================${NC}"
echo "Log file: $LOG_FILE"
echo "Poll interval: ${POLL_INTERVAL}s"
echo ""

solver_count=0
last_line_count=0
echo -e "${CYAN}Waiting for benchmark to start...${NC}"
echo ""

while true; do
    # Get new lines since last check
    current_line_count=$(wc -l < "$LOG_FILE" 2>/dev/null || echo 0)
    new_lines=$((current_line_count - last_line_count))

    if [ $new_lines -gt 0 ]; then
        # Read only the new lines
        tail -n "$new_lines" "$LOG_FILE" 2>/dev/null | while IFS= read -r line; do
            # Track phase completion
            phase_info=$(echo "$line" | grep -o 'Construction Heuristic phase ([0-9]+) ended: time spent ([0-9]+), best score (\[[^]]+\])' 2>/dev/null)
            if [ -n "$phase_info" ]; then
                phase_num=$(echo "$phase_info" | sed -n 's/.*phase \([0-9]*\).*/\1/p')
                time_ms=$(echo "$phase_info" | sed -n 's/.*time spent \([0-9]*\).*/\1/p')
                score=$(echo "$phase_info" | sed -n 's/.*best score \[\(.*\)\].*/\1/p')
                time_sec=$((time_ms / 1000))

                score_formatted=$(format_score "$score")

                echo -e "${GREEN}[PHASE COMPLETE]${NC} ${BOLD}Phase $phase_num${NC} (Construction Heuristic) | Time: ${BLUE}${time_sec}s${NC} | Score: $score_formatted"
            fi

            # Track Local Search phase completion
            ls_info=$(echo "$line" | grep -o 'Local Search phase ([0-9]+) ended: time spent ([0-9]+), best score (\[[^]]+\])' 2>/dev/null)
            if [ -n "$ls_info" ]; then
                phase_num=$(echo "$ls_info" | sed -n 's/.*phase \([0-9]*\).*/\1/p')
                time_ms=$(echo "$ls_info" | sed -n 's/.*time spent \([0-9]*\).*/\1/p')
                score=$(echo "$ls_info" | sed -n 's/.*best score \[\(.*\)\].*/\1/p')
                time_sec=$((time_ms / 1000))

                score_formatted=$(format_score "$score")

                echo -e "${GREEN}[PHASE COMPLETE]${NC} ${BOLD}Phase $phase_num${NC} (Local Search) | Time: ${BLUE}${time_sec}s${NC} | Score: $score_formatted"
            fi

            # Track solver end
            solver_info=$(echo "$line" | grep -o 'Solving ended.*time spent ([0-9]+), best score (\[[^]]+\])' 2>/dev/null)
            if [ -n "$solver_info" ]; then
                time_ms=$(echo "$solver_info" | sed -n 's/.*time spent \([0-9]*\).*/\1/p')
                score=$(echo "$solver_info" | sed -n 's/.*best score \[\(.*\)\].*/\1/p')
                time_sec=$((time_ms / 1000))

                ((solver_count++))

                score_formatted=$(format_score "$score")

                echo -e "${GREEN}[SOLVER COMPLETE]${NC} Solver #$solver_count | Total time: ${BLUE}${time_sec}s${NC} | Final score: $score_formatted"
            fi
        done

        last_line_count=$current_line_count
    fi

    # Check if benchmark is still running
    if ! pgrep -f "LocalSearchComparisonBenchmark" > /dev/null 2>&1; then
        # Check if log file was recently modified
        if [ -f "$LOG_FILE" ]; then
            current_time=$(date +%s)
            log_time=$(stat -f %m "$LOG_FILE" 2>/dev/null || stat -c %Y "$LOG_FILE" 2>/dev/null)
            log_age=$((current_time - log_time))
            if [ $log_age -gt 5 ]; then
                echo ""
                echo -e "${GREEN}✓ Benchmark complete!${NC}"
                echo -e "Total solvers completed: ${BOLD}$solver_count${NC}"
                break
            fi
        fi
    fi

    sleep $POLL_INTERVAL
done
