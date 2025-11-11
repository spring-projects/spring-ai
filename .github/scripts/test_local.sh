#!/bin/bash

# Local testing script to simulate GitHub Actions maintenance-fast.yml workflow
# Usage: ./test_local.sh [branch_name]

set -e

BRANCH_NAME=${1:-"1.0.x"}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Local GitHub Actions Simulation ==="
echo "Branch: $BRANCH_NAME"
echo "Script dir: $SCRIPT_DIR"
echo ""

# Simulate GitHub Actions environment variables
export GITHUB_REF_NAME="$BRANCH_NAME"
export GITHUB_REF="refs/heads/$BRANCH_NAME"

echo "=== Step 1: Show commit range ==="
echo "Base ref: origin/$GITHUB_REF_NAME"
if git rev-parse "origin/$GITHUB_REF_NAME" >/dev/null 2>&1; then
    git log --oneline "origin/$GITHUB_REF_NAME...HEAD" | head -5
else
    echo "WARNING: origin/$GITHUB_REF_NAME not found, using HEAD~1"
    git log --oneline HEAD~1..HEAD
fi
echo ""

echo "=== Step 2: Compute impacted modules ==="
cd "$SCRIPT_DIR/../.."
echo "Working directory: $(pwd)"

# Test different git diff strategies locally
echo "--- Testing git diff strategies ---"

echo "Strategy 1: origin/$GITHUB_REF_NAME...HEAD (three-dot)"
FILES_3DOT=$(git diff --name-only "origin/$GITHUB_REF_NAME...HEAD" 2>/dev/null || echo "")
echo "Files found: $(echo "$FILES_3DOT" | wc -l)"
echo "$FILES_3DOT" | head -3

echo ""
echo "Strategy 2: origin/$GITHUB_REF_NAME..HEAD (two-dot)"
FILES_2DOT=$(git diff --name-only "origin/$GITHUB_REF_NAME..HEAD" 2>/dev/null || echo "")
echo "Files found: $(echo "$FILES_2DOT" | wc -l)"
echo "$FILES_2DOT" | head -3

echo ""
echo "Strategy 3: HEAD~1..HEAD (single commit)"
FILES_1COMMIT=$(git diff --name-only "HEAD~1..HEAD" 2>/dev/null || echo "")
echo "Files found: $(echo "$FILES_1COMMIT" | wc -l)"
echo "$FILES_1COMMIT" | head -3

echo ""
echo "--- Running test_discovery.py ---"
MODS=$(python3 .github/scripts/test_discovery.py modules-from-diff --base "origin/$GITHUB_REF_NAME" --verbose 2>&1)
MODULE_LIST=$(echo "$MODS" | tail -1)

echo "Script output:"
echo "$MODS"
echo ""
echo "Final modules: '$MODULE_LIST'"

echo ""
echo "=== Step 3: Test build logic ==="
if [ -z "$MODULE_LIST" ]; then 
    echo "ERROR: No modules detected - git diff failed to find changes"
    echo "This likely indicates a problem with the git diff strategy"
    echo "Failing fast to avoid wasted resources and investigate the issue"
    echo "Check the 'Compute impacted modules' step output for debugging info"
    exit 1
else
    echo "SUCCESS: Would build modules: $MODULE_LIST"
    echo "Build command would be:"
    echo "./mvnw -B -T 1C -Pintegration-tests -DfailIfNoTests=false -pl \"$MODULE_LIST\" -amd verify"
fi

echo ""
echo "=== Local test complete ==="