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

package org.springframework.ai.mcp.modern.client;

import io.modelcontextprotocol.modern.client.McpSyncClient;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.Assert;

/**
 * Customizes a single call of a modern MCP tool, with the {@link ToolContext} of that
 * call at hand.
 * <p>
 * {@link SyncModernMcpToolCallback} applies it to the request spec of each call, after
 * the arguments and the {@code _meta} entries are set. It can set anything a
 * {@link McpSyncClient.ToolSpec} offers for one call, in particular the elicitation
 * handlers and the progress listener, which replace those of the client for that call. A
 * handler set here can read the {@code ToolContext}, for example to find the user it has
 * to ask, which a handler set on the client cannot.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see SyncModernMcpToolCallbackProvider.Builder#toolCallCustomizer
 */
@FunctionalInterface
public interface SyncModernMcpToolCallCustomizer {

	/**
	 * Customizes the request spec of a tool call.
	 * @param connectionInfo the connection the tool belongs to
	 * @param toolSpec the request spec of the call; specs are immutable, so every setter
	 * returns a new one
	 * @param toolContext the tool context of the call, or {@code null} if the tool is
	 * called without one
	 * @return the spec to call the tool with
	 */
	McpSyncClient.ToolSpec customize(McpModernConnectionInfo connectionInfo, McpSyncClient.ToolSpec toolSpec,
			@Nullable ToolContext toolContext);

	/**
	 * Returns a customizer that applies this customizer and then the given one to the
	 * spec this one returned. What the second one sets replaces what the first one set.
	 * @param next the customizer to apply after this one
	 * @return the composed customizer
	 */
	default SyncModernMcpToolCallCustomizer andThen(SyncModernMcpToolCallCustomizer next) {
		Assert.notNull(next, "next must not be null");
		return (connectionInfo, toolSpec, toolContext) -> next.customize(connectionInfo,
				this.customize(connectionInfo, toolSpec, toolContext), toolContext);
	}

}
