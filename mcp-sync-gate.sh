#!/usr/bin/env bash
set -euo pipefail
# MCP sync gate: proves HEAD == upstream MCP + exactly mcp-compat.patch.
#
# The compatible-backport branch keeps the MCP cross-section byte-identical to the
# upstream release tag, except for a single auditable patch (mcp-compat.patch) that
# records the Jackson 3->2 fallback, the Spring Boot 3.5/SDK 2.0 pinning, and the
# compile-closure backports. Any edit that is not recorded in the patch makes this
# gate fail.

SYNC_SRC="${SYNC_SRC:-v2.0.1}"
# The "pure mirror" commit created by Phase B (mcp: mirror MCP module from upstream v2.0.1).
MIRROR_COMMIT="edb77d286"
MCP_PATHS="mcp auto-configurations/mcp \
  starters/spring-ai-starter-mcp-server starters/spring-ai-starter-mcp-server-webmvc \
  starters/spring-ai-starter-mcp-server-webflux starters/spring-ai-starter-mcp-client \
  starters/spring-ai-starter-mcp-client-webflux"

# 1) HEAD must equal the pure mirror + exactly this patch (reversibility check).
git apply -R --check mcp-compat.patch

# 2) The pure mirror's MCP cross-section must be byte-identical to the sync source,
#    which - combined with (1) - proves HEAD's MCP == SYNC_SRC + exactly mcp-compat.patch.
git diff --exit-code "$SYNC_SRC" "$MIRROR_COMMIT" -- $MCP_PATHS

echo "SYNC GATE PASSED: HEAD == $SYNC_SRC + exactly mcp-compat.patch"
