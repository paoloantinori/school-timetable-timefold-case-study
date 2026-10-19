#!/usr/bin/env bash
#
# check-propagation.sh — Cross-branch commit propagation audit
#
# Finds commits on the base branch (fastest: feature/cartesian-block-entity) after
# the common divergence point, then checks whether each commit was cherry-picked
# to all other active feature/* branches (excludes archive/*).
# Prints a matrix of ✓/✗ per branch and exits non-zero if any gaps exist.
#
# Active branches: feature/cartesian-block-entity, feature/cartesian-shadow-lab-blocks,
#                  feature/shadow-lab-blocks
#
# Usage: ./scripts/validate/check-propagation.sh
#
# Requirements: git
#

set -euo pipefail

# Use the fastest branch as the propagation source
BASE_BRANCH="feature/cartesian-block-entity"

# --- Collect active feature branches (exclude archived) ---
# Only branches matching feature/* and NOT starting with archive/
mapfile -t BRANCHES < <(git branch --format='%(refname:short)' | grep '^feature/' | grep -v '^archive/' | sort)

if [ "${#BRANCHES[@]}" -eq 0 ]; then
  echo "No feature/* branches found."
  exit 0
fi

# --- Find the earliest divergence point across all active branches ---
EARLIEST_ANCESTOR=""
for branch in "${BRANCHES[@]}"; do
  ancestor=$(git merge-base "$BASE_BRANCH" "$branch" 2>/dev/null || true)
  if [ -z "$ancestor" ]; then
    continue
  fi
  if [ -z "$EARLIEST_ANCESTOR" ]; then
    EARLIEST_ANCESTOR="$ancestor"
  else
    # Keep the older (earlier) ancestor
    if git merge-base --is-ancestor "$ancestor" "$EARLIEST_ANCESTOR" 2>/dev/null; then
      EARLIEST_ANCESTOR="$ancestor"
    fi
  fi
done

if [ -z "$EARLIEST_ANCESTOR" ]; then
  echo "Could not find common ancestor between $BASE_BRANCH and feature branches."
  exit 1
fi

# --- Get base branch commits after the earliest divergence ---
mapfile -t BASE_COMMITS < <(git log --oneline --reverse "${EARLIEST_ANCESTOR}..${BASE_BRANCH}" --format="%H")

if [ "${#BASE_COMMITS[@]}" -eq 0 ]; then
  echo "No commits on $BASE_BRANCH after divergence point. Nothing to audit."
  exit 0
fi

# --- Build header ---
# Calculate column widths
MSG_WIDTH=50
COL_WIDTH=12

# Exclude the base branch from the check list (commits are already there)
declare -a CHECK_BRANCHES
for branch in "${BRANCHES[@]}"; do
  if [ "$branch" != "$BASE_BRANCH" ]; then
    CHECK_BRANCHES+=("$branch")
  fi
done

# Build short names for check branches only
declare -a CHECK_SHORT_NAMES
for branch in "${CHECK_BRANCHES[@]}"; do
  name="${branch#feature/}"
  CHECK_SHORT_NAMES+=("$name")
done

# Print header
printf "%-7s  %-${MSG_WIDTH}s" "COMMIT" "MESSAGE"
for name in "${CHECK_SHORT_NAMES[@]}"; do
  printf "  %-${COL_WIDTH}s" "$name"
done
echo ""

# Separator
TOTAL_WIDTH=$((7 + 2 + MSG_WIDTH + (COL_WIDTH + 2) * ${#CHECK_BRANCHES[@]}))
printf '%*s\n' "$TOTAL_WIDTH" '' | tr ' ' '-'

# --- Check each base branch commit against other active branches ---
GAPS=0

for commit in "${BASE_COMMITS[@]}"; do
  SHORT_HASH="${commit:0:7}"
  MSG=$(git log --format="%s" -1 "$commit" | cut -c1-${MSG_WIDTH})

  printf "%-7s  %-${MSG_WIDTH}s" "$SHORT_HASH" "$MSG"

  for branch in "${CHECK_BRANCHES[@]}"; do
    # Check if this exact commit is an ancestor of the branch
    if git merge-base --is-ancestor "$commit" "$branch" 2>/dev/null; then
      printf "  %-${COL_WIDTH}s" "✓"
      continue
    fi

    # Fuzzy match: search by commit message subject in the branch log
    COMMIT_MSG=$(git log --format="%s" -1 "$commit")
    FOUND=$(git log "$branch" --oneline --grep="$COMMIT_MSG" --fixed-strings | head -1 || true)

    if [ -n "$FOUND" ]; then
      printf "  %-${COL_WIDTH}s" "✓ (cherry)"
    else
      # Try stripping constraint number prefix (e.g. "Add H9:" → search for "minimum student group...")
      # Extract the part after "H\d+:|S\d+:|M\d+:" to match regardless of numbering
      CORE_MSG=$(echo "$COMMIT_MSG" | sed -E 's/.*[HSM][0-9]+: //')
      if [ "$CORE_MSG" != "$COMMIT_MSG" ] && [ -n "$CORE_MSG" ]; then
        FOUND=$(git log "$branch" --oneline --grep="$CORE_MSG" --fixed-strings | head -1 || true)
      fi
      if [ -n "$FOUND" ]; then
        printf "  %-${COL_WIDTH}s" "✓ (renum)"
      else
        printf "  %-${COL_WIDTH}s" "✗"
        GAPS=$((GAPS + 1))
      fi
    fi
  done
  echo ""
done

# --- Summary ---
echo ""
if [ "$GAPS" -gt 0 ]; then
  echo "RESULT: $GAPS gap(s) found — some commits have not been propagated to all active branches."
  echo ""
  echo "To fix: cherry-pick missing commits to the affected branches."
  echo "  git checkout <branch>"
  echo "  git cherry-pick <commit-hash>"
  exit 1
else
  echo "RESULT: All $BASE_BRANCH commits are present in all active branches. No gaps."
  exit 0
fi
