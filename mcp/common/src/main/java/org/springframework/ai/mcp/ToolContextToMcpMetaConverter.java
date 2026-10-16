/*
 * Copyright 2023-present the original author or authors.
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

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.CollectionUtils;

/**
 * Strategy interface for converting a {@link ToolContext} to a map of metadata to be sent
 * as part of an MCP tool call.
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 * @author Dimitar Proynov
 */
public interface ToolContextToMcpMetaConverter {

	/**
	 * Convert the given {@link ToolContext} to a Map<String, Object> as MCP tool call
	 * metadata.
	 * <p>
	 * The default implementation ignores the
	 * {@link McpToolUtils#TOOL_CONTEXT_MCP_EXCHANGE_KEY} entry and any entries with null
	 * values.
	 * @param toolContext the tool context to convert
	 * @return a map of metadata to be sent as part of the MCP tool call
	 */
	Map<String, Object> convert(ToolContext toolContext);

	/**
	 * Convert the given {@link ToolContext} to MCP tool call metadata for a call bound to
	 * a specific MCP server.
	 * <p>
	 * The same {@link ToolContext} is shared across every registered MCP server, so a
	 * converter that forwards sensitive entries (auth tokens, user identifiers, etc.)
	 * without regard to {@code connectionInfo} sends them to every server the application
	 * talks to, not just the ones that need them. Override this method instead of
	 * {@link #convert(ToolContext)} to scope what gets forwarded based on the destination
	 * server, mirroring how {@link McpToolFilter} scopes tool discovery per
	 * {@link McpConnectionInfo}.
	 * <p>
	 * The default implementation ignores {@code connectionInfo} and delegates to
	 * {@link #convert(ToolContext)}, preserving existing behavior for converters that
	 * don't need per-server scoping.
	 * @param toolContext the tool context to convert
	 * @param connectionInfo the MCP server the resulting metadata will be sent to
	 * @return a map of metadata to be sent as part of the MCP tool call
	 * @since 2.0.2
	 */
	default Map<String, Object> convert(ToolContext toolContext, McpConnectionInfo connectionInfo) {
		return convert(toolContext);
	}

	static ToolContextToMcpMetaConverter defaultConverter() {

		return toolContext -> {
			if (toolContext == null || CollectionUtils.isEmpty(toolContext.getContext())) {
				return Map.of();
			}

			return toolContext.getContext()
				.entrySet()
				.stream()
				.filter(entry -> !McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY.equals(entry.getKey())
						&& entry.getValue() != null)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		};
	}

	/**
	 * Static factory method to create a no-op converter that returns an empty map.
	 * @return a no-op converter
	 */
	static ToolContextToMcpMetaConverter noOp() {
		return toolContext -> Map.of();
	}

}
