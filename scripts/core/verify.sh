#!/bin/bash
#
# verify.sh - Maven verify runner for integration tests
#
# Usage: verify.sh [options] [test-class]
#
# Options:
#   -c, --class NAME     Run specific test class (e.g., SchoolScheduleBenchmark)
#   -m, --method NAME    Run specific test method (e.g., phase3CompactnessBenchmark)
#   -v, --verbose        Show detailed test output
#   -h, --help           Show this help
#
# Examples:
#   verify.sh                                    Run all integration tests
#   verify.sh -c SchoolScheduleBenchmark         Run specific test class
#   verify.sh -c SchoolScheduleBenchmark -m phase3CompactnessBenchmark
#
# Security: Wrapper script for mvn verify (whitelisted)
#

set -e

# Colors
YELLOW='\033[1;33m'
NC='\033[0m'

TEST_CLASS=""
TEST_METHOD=""
VERBOSE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--class)
            TEST_CLASS="$2"
            shift 2
            ;;
        -m|--method)
            TEST_METHOD="$2"
            shift 2
            ;;
        -v|--verbose)
            VERBOSE="-X"
            shift
            ;;
        -h|--help)
            grep "^#" "$0" | cut -c 4- | sed 's/^#//' | sed 's/^ //' | head -30
            exit 0
            ;;
        *)
            if [[ -z "$TEST_CLASS" ]]; then
                TEST_CLASS="$1"
            fi
            shift
            ;;
    esac
done

MVN_CMD="mvn verify -DskipUTs"

if [[ -n "$TEST_CLASS" ]]; then
    if [[ -n "$TEST_METHOD" ]]; then
        MVN_CMD="$MVN_CMD -Dit.test=$TEST_CLASS#$TEST_METHOD"
    else
        MVN_CMD="$MVN_CMD -Dit.test=$TEST_CLASS"
    fi
elif [[ "$1" == "--all-it" ]]; then
    # Run all integration tests without filter
    MVN_CMD="mvn verify -DskipUTs"
fi

if [[ -n "$VERBOSE" ]]; then
    MVN_CMD="$MVN_CMD $VERBOSE"
fi

MVN_CMD="$MVN_CMD -Dnet.bytebuddy.experimental=true"

echo -e "${YELLOW}Running:${NC} $MVN_CMD"
eval $MVN_CMD
