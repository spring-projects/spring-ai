#!/usr/bin/env bash
set -euo pipefail

# MCP sync gate — machine proof that:
#     HEAD == upstream MCP cross-section + exactly mcp-compat.patch
# holds for EVERY tracked file of the repository, not just the files the patch
# touches. Any commit that is not recorded in mcp-compat.patch, any edit to a
# file outside the patch, and any uncommitted or untracked leftover under the
# repo (beyond the documented local-only allowlist) makes this gate fail.
#
# Proof strategy:
#   0) Pinning    — the upstream tag must still resolve to the exact tag object
#                   AND peeled commit recorded in MCP_SYNC_SOURCE.txt, and the
#                   pure-mirror commit must exist (a moved tag fails loudly).
#   1) Purity     — the pure-mirror commit's MCP cross-section is byte-identical
#                   to the pinned upstream tag.
#   2) Clean tree — no uncommitted/untracked files anywhere except the three
#                   documented local-only items (.gitignore edit, .worktree/,
#                   plan doc), so the remaining steps describe committed state.
#   3) Exact drift — `git diff <mirror> HEAD` over the WHOLE tree (minus the
#                   three self-referential meta files and the local .gitignore)
#                   is byte-identical to the stored mcp-compat.patch. This is
#                   the step that catches edits to files the patch does NOT
#                   touch — they would appear in the regenerated diff.
#   4) Reversible — the patch reverse-applies cleanly to the working tree.
#
# To record new legitimate drift: make your changes, commit them, regenerate the
# patch with the command in step 3's failure message, and commit the patch.

cd "$(git rev-parse --show-toplevel)"

fail() { echo "SYNC GATE FAILED: $*" >&2; exit 1; }

for f in mcp-compat.patch mcp-sync-gate.sh MCP_SYNC_SOURCE.txt; do
    [ -f "$f" ] || fail "missing meta file $f"
done

key() { grep -E "^$1=" MCP_SYNC_SOURCE.txt | head -1 | cut -d= -f2-; }
SYNC_SRC=$(key SYNC_SRC)
TAG_SHA=$(key SYNC_SRC_TAG)
COMMIT_SHA=$(key SYNC_SRC_COMMIT)
MIRROR_COMMIT=$(key MIRROR_COMMIT)
[ -n "$SYNC_SRC" ] && [ -n "$TAG_SHA" ] && [ -n "$COMMIT_SHA" ] && [ -n "$MIRROR_COMMIT" ] \
    || fail "MCP_SYNC_SOURCE.txt is missing a required KEY=VALUE entry"

# 0) Pin the sync source and the mirror commit.
[ "$(git rev-parse "$SYNC_SRC" 2>/dev/null || true)" = "$TAG_SHA" ] \
    || fail "upstream tag $SYNC_SRC moved or is missing (expected tag object $TAG_SHA)"
[ "$(git rev-parse "$SYNC_SRC^{commit}" 2>/dev/null || true)" = "$COMMIT_SHA" ] \
    || fail "upstream tag $SYNC_SRC no longer peels to $COMMIT_SHA"
git cat-file -e "$MIRROR_COMMIT^{commit}" 2>/dev/null \
    || fail "mirror commit $MIRROR_COMMIT not found"

# 1) Purity of the mirror: MCP cross-section == upstream tag, byte-for-byte.
MCP_PATHS=(mcp auto-configurations/mcp 'starters/spring-ai-starter-mcp-*')
git diff --exit-code "$SYNC_SRC" "$MIRROR_COMMIT" -- "${MCP_PATHS[@]}" \
    || fail "pure-mirror commit $MIRROR_COMMIT deviates from $SYNC_SRC in the MCP cross-section"

# 2) Clean tree: nothing uncommitted/untracked except the documented local items.
BAD=$(git status --porcelain | grep -vE \
    -e '^ M \.gitignore$' \
    -e '^\?\? \.worktree/$' \
    -e '^\?\? spring-ai-mcp-sdk2-backport-plan\.md$' || true)
[ -z "$BAD" ] || fail "uncommitted/untracked files present (commit them or record them in the patch):
$BAD"

# 3) Exact drift: the stored patch must equal the full mirror->HEAD diff.
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
git diff "$MIRROR_COMMIT" HEAD -- . \
    ':(exclude)mcp-compat.patch' ':(exclude)mcp-sync-gate.sh' ':(exclude)MCP_SYNC_SOURCE.txt' \
    ':(exclude).gitignore' > "$TMP"
cmp -s "$TMP" mcp-compat.patch || fail "mcp-compat.patch does not match the actual mirror->HEAD drift.
Regenerate with:
  git diff $MIRROR_COMMIT HEAD -- . ':(exclude)mcp-compat.patch' ':(exclude)mcp-sync-gate.sh' ':(exclude)MCP_SYNC_SOURCE.txt' ':(exclude).gitignore' > mcp-compat.patch"

# 4) The patch reverse-applies cleanly to the (verified-clean) working tree.
git apply -R --check mcp-compat.patch || fail "mcp-compat.patch does not reverse-apply to the working tree"

echo "SYNC GATE PASSED: HEAD == $SYNC_SRC (commit $COMMIT_SHA) MCP cross-section + exactly mcp-compat.patch (whole-tree verified)"
