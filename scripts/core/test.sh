#!/bin/bash
#
# test.sh - Maven test runner with common options
#
# Usage: test.sh [options] [test-class]
#
# Options:
#   -u, --unit           Run unit tests only (skip integration)
#   -i, --integration    Run integration tests only (skip unit)
#   -a, --all            Run all tests (unit + integration)
#   -c, --class NAME     Run specific test class (e.g., ExpectedDaysCalculatorTest)
#   -m, --method NAME    Run specific test method (e.g., testSingleDayCourse)
#   -v, --verbose        Show detailed test output
#   -mva, --maven-args   Pass extra Maven args verbatim (e.g. -mva "-Pbyteman")
#   -s, --skip           Skip tests (compile only)
#   -h, --help           Show this help
#
# Examples:
#   test.sh                           Run all tests
#   test.sh -c ExpectedDaysCalculatorTest  Run specific test class
#   test.sh -c ExpectedDaysCalculatorTest -m testSingleDayCourse  Run specific method
#   test.sh -u                        Run unit tests only
#   test.sh -v                        Run tests with verbose output
#
# Security: Wrapper script for mvn test (whitelisted)
#

set -e

# Source sdkman so JAVA_HOME is set for Maven
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
TEST_CLASS=""
TEST_METHOD=""
VERBOSE=""
SKIP_TESTS=""
TEST_ARGS=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--unit)
            TEST_ARGS="$TEST_ARGS -DskipITs"
            shift
            ;;
        -i|--integration)
            TEST_ARGS="$TEST_ARGS -DskipUTs"
            shift
            ;;
        -a|--all)
            # Run all (default)
            shift
            ;;
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
        -mva|--maven-args)
            # TASK-543: passthrough per argomenti maven aggiuntivi (es. -mva "-Pbyteman").
            # Gli argomenti passati qui finiscono TALI E QUALI sulla riga mvn.
            MAVEN_EXTRA_ARGS="${2:-}"
            shift 2 || true
            ;;
        -s|--skip)
            SKIP_TESTS="-DskipTests"
            shift
            ;;
        -h|--help)
            grep "^#" "$0" | cut -c 4- | sed 's/^#//' | sed 's/^ //' | head -40
            exit 0
            ;;
        -*)
            # Detect common Maven-style mistakes and suggest correct syntax
            case "$1" in
                -Dtest=*|-Dtest)
                    echo -e "${RED}Error: Maven property syntax detected${NC}"
                    echo ""
                    echo "You used: $1"
                    echo "This script uses -c for test classes instead."
                    echo ""
                    echo -e "${GREEN}Correct usage:${NC}"
                    echo "  test.sh -c ${1#-Dtest=}"
                    echo "  test.sh -c LocalSearchComparisonBenchmark"
                    echo ""
                    echo "For specific methods:"
                    echo "  test.sh -c ClassName -m methodName"
                    echo ""
                    echo "For other raw Maven arguments (profiles, properties):"
                    echo "  test.sh -mva \"-Pbyteman\""
                    echo ""
                    echo "Use -h for more options"
                    exit 1
                    ;;
                -DskipITs|-DskipUTs|-DskipTests)
                    echo -e "${RED}Error: Maven property detected${NC}"
                    echo ""
                    echo "You used: $1"
                    echo "This script has dedicated flags for these options:"
                    echo "  -u, --unit        Skip integration tests (same as -DskipITs)"
                    echo "  -i, --integration Skip unit tests (same as -DskipUTs)"
                    echo "  -s, --skip        Skip all tests (compile only)"
                    echo ""
                    echo -e "${GREEN}Correct usage:${NC}"
                    echo "  test.sh -u    # Run unit tests only"
                    echo "  test.sh -i    # Run integration tests only"
                    echo "  test.sh -s    # Skip all tests"
                    echo ""
                    echo "Use -h for more options"
                    exit 1
                    ;;
                *)
                    echo -e "${RED}Error: Unknown option $1${NC}"
                    echo ""
                    echo -e "${YELLOW}Common options:${NC}"
                    echo "  -c, --class NAME   Run specific test class"
                    echo "  -m, --method NAME  Run specific test method"
                    echo "  -u, --unit         Run unit tests only"
                    echo "  -v, --verbose      Show detailed output"
                    echo ""
                    echo "Use -h for full help"
                    exit 1
                    ;;
            esac
            ;;
        *)
            # Treat as test class name if not an option
            if [[ -z "$TEST_CLASS" ]]; then
                TEST_CLASS="$1"
            fi
            shift
            ;;
    esac
done

# Build the test command
MVN_CMD="mvn test"

# Add test class if specified
if [[ -n "$TEST_CLASS" ]]; then
    if [[ -n "$TEST_METHOD" ]]; then
        MVN_CMD="$MVN_CMD -Dtest=$TEST_CLASS#$TEST_METHOD"
    else
        MVN_CMD="$MVN_CMD -Dtest=$TEST_CLASS"
    fi
fi

# Add additional test arguments
if [[ -n "$TEST_ARGS" ]]; then
    MVN_CMD="$MVN_CMD $TEST_ARGS"
fi

# Add verbose flag if requested
if [[ -n "$VERBOSE" ]]; then
    MVN_CMD="$MVN_CMD $VERBOSE"
fi

# Add skip tests if requested
if [[ -n "$SKIP_TESTS" ]]; then
    MVN_CMD="mvn compile -DskipTests $TEST_ARGS"
fi

# Add required system property
MVN_CMD="$MVN_CMD -Dnet.bytebuddy.experimental=true"

# Add extra maven args if provided (passthrough -mva)
if [[ -n "${MAVEN_EXTRA_ARGS:-}" ]]; then
    MVN_CMD="$MVN_CMD $MAVEN_EXTRA_ARGS"
fi

# Show what we're running
echo -e "${YELLOW}Running:${NC} $MVN_CMD"

# Run the command
eval $MVN_CMD
