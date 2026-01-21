/*
 * Copyright 2025-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link McpToolNamePrefixGenerator} that ensures unique tool
 * names for all client/server connections.
 *
 * <p>
 * This implementation ensures that tool names are unique across different MCP clients and
 * servers by tracking existing connections and appending a counter to duplicate tool
 * names.
 *
 * <p>
 * For each unique combination of (client, server, tool), e.g. each connection, the tool
 * name is generated only once. If a tool name has already been used, a prefix with a
 * counter is added to make it unique (e.g., "alt_1_toolName", "alt_2_toolName", etc.).
 *
 * <p>
 * This implementation is thread-safe.
 *
 * @author Christian Tzolov
 */
public class DefaultMcpToolNamePrefixGenerator implements McpToolNamePrefixGenerator {

	private static final Logger logger = LoggerFactory.getLogger(DefaultMcpToolNamePrefixGenerator.class);

	// Idempotency tracking. For a given combination of (client, server, tool) we will
	// generate a unique tool name only once.
	private final Set<ConnectionId> existingConnections = ConcurrentHashMap.newKeySet();

	private final Set<String> allUsedToolNames = ConcurrentHashMap.newKeySet();

	private final AtomicInteger counter = new AtomicInteger(1);

	@Override
	public String prefixedToolName(McpConnectionInfo mcpConnectionInfo, McpSchema.Tool tool) {

		String uniqueToolName = McpToolUtils.format(tool.name());

		if (this.existingConnections
			.add(new ConnectionId(mcpConnectionInfo.clientInfo(), (mcpConnectionInfo.initializeResult() != null)
					? mcpConnectionInfo.initializeResult().serverInfo() : null, tool))) {
			if (!this.allUsedToolNames.add(uniqueToolName)) {
				uniqueToolName = "alt_" + this.counter.getAndIncrement() + "_" + uniqueToolName;
				this.allUsedToolNames.add(uniqueToolName);
				logger.warn("Tool name '{}' already exists. Using unique tool name '{}'", tool.name(), uniqueToolName);
			}
		}

		return uniqueToolName;
	}

	private record ConnectionId(@Nullable Implementation clientInfo, @Nullable Implementation serverInfo, Tool tool) {
	}

}
