#!/bin/bash
# Ensures Timefold Solver is patched for the IdentityHashSet bug
# See: claudedocs/timefold-vrp-identity-bug-fix-20260306.md
#
# Usage: ./scripts/ensure-timefold-patch.sh [--force]
#
# The patch fixes ValueRangeCache to use HashSet instead of IdentityHashSet
# for user-defined value types (JPA entities like Room, Timeslot).
#
# NOTE (TASK-305): VERIFIED FIXED in Timefold 1.33.0. ValueRangeCache now uses
# CollectionUtils.newHashSet() instead of newIdentityHashSet(). This script is
# kept for historical reference but is NO LONGER NEEDED. Do not run it against
# 1.33.0+. If downgrading to 1.31.0, the patch is still required.

set -e

TIMEFOLD_VERSION="1.31.0"
TIMEFOLD_GROUP="ai/timefold/solver"
TIMEFOLD_ARTIFACT="timefold-solver-core"
M2_REPO="$HOME/.m2/repository"
JAR_PATH="$M2_REPO/$TIMEFOLD_GROUP/$TIMEFOLD_ARTIFACT/$TIMEFOLD_VERSION/${TIMEFOLD_ARTIFACT}-${TIMEFOLD_VERSION}.jar"
CLASS_FILE="ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.class"
PATCH_MARKER="$M2_REPO/$TIMEFOLD_GROUP/$TIMEFOLD_ARTIFACT/$TIMEFOLD_VERSION/.patched-identity-hashset"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_patched() {
    # Check if patch marker exists
    if [[ -f "$PATCH_MARKER" ]]; then
        return 0
    fi

    # Check if JAR exists and contains patched class
    if [[ -f "$JAR_PATH" ]]; then
        # Extract and decompile the class to check for HashSet vs IdentityHashSet
        # We check by looking at the string constants in the class file
        local tmp_dir
        tmp_dir=$(mktemp -d)
        unzip -q -o "$JAR_PATH" "$CLASS_FILE" -d "$tmp_dir" 2>/dev/null || true

        if [[ -f "$tmp_dir/$CLASS_FILE" ]]; then
            # Check bytecode for HashSet reference (patched) vs IdentityHashSet (unpatched)
            if javap -c "$tmp_dir/$CLASS_FILE" 2>/dev/null | grep -q "java/util/HashSet"; then
                # Found HashSet - likely patched, create marker
                touch "$PATCH_MARKER"
                rm -rf "$tmp_dir"
                return 0
            fi
        fi
        rm -rf "$tmp_dir"
    fi

    return 1
}

apply_patch() {
    log_info "Applying Timefold $TIMEFOLD_VERSION IdentityHashSet patch..."

    local work_dir
    work_dir=$(mktemp -d)
    local src_dir="$work_dir/timefold-solver"
    local target_file="$src_dir/core/src/main/java/ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.java"

    log_info "Cloning Timefold Solver (tag v$TIMEFOLD_VERSION)..."
    git clone --depth 1 --branch "v$TIMEFOLD_VERSION" https://github.com/TimefoldAI/timefold-solver.git "$src_dir" 2>&1 | sed 's/^/  /'

    if [[ ! -f "$target_file" ]]; then
        log_error "Source file not found: $target_file"
        exit 1
    fi

    log_info "Patching ValueRangeCache.java..."
    # Replace newIdentityHashSet with newHashSet (multiple occurrences with different arguments)
    # macOS sed requires -i '' (empty backup suffix)
    sed -i '' 's/CollectionUtils\.newIdentityHashSet(/CollectionUtils.newHashSet(/g' "$target_file"

    # Verify the patch was applied
    if grep -q "newIdentityHashSet" "$target_file"; then
        log_error "Patch failed - newIdentityHashSet still present"
        exit 1
    fi

    log_info "Building patched JAR (this may take a few minutes)..."
    cd "$src_dir"
    ./mvnw clean install -DskipTests -Dquickly -pl core -am 2>&1 | tail -20

    # Find the built JAR
    local built_jar="$src_dir/core/target/${TIMEFOLD_ARTIFACT}-${TIMEFOLD_VERSION}.jar"
    if [[ ! -f "$built_jar" ]]; then
        log_error "Built JAR not found: $built_jar"
        exit 1
    fi

    log_info "Installing patched JAR to local Maven repository..."
    mkdir -p "$(dirname "$JAR_PATH")"
    cp "$built_jar" "$JAR_PATH"

    # Also copy POM and sources if available
    cp "$src_dir/core/target/${TIMEFOLD_ARTIFACT}-${TIMEFOLD_VERSION}.pom" \
       "$M2_REPO/$TIMEFOLD_GROUP/$TIMEFOLD_ARTIFACT/$TIMEFOLD_VERSION/" 2>/dev/null || true
    cp "$src_dir/core/target/${TIMEFOLD_ARTIFACT}-${TIMEFOLD_VERSION}-sources.jar" \
       "$M2_REPO/$TIMEFOLD_GROUP/$TIMEFOLD_ARTIFACT/$TIMEFOLD_VERSION/" 2>/dev/null || true

    # Create patch marker
    touch "$PATCH_MARKER"

    # Cleanup
    cd - > /dev/null
    rm -rf "$work_dir"

    log_info "Patch applied successfully!"
}

force=false
if [[ "$1" == "--force" ]]; then
    force=true
fi

if $force; then
    log_warn "Force mode - reapplying patch..."
    rm -f "$PATCH_MARKER"
    apply_patch
elif check_patched; then
    log_info "Timefold $TIMEFOLD_VERSION is already patched ✓"
    exit 0
else
    log_warn "Timefold $TIMEFOLD_VERSION is not patched. Applying patch..."
    apply_patch
fi
