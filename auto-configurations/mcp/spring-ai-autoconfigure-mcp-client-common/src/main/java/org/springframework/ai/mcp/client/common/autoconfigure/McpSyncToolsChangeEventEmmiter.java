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

package org.springframework.ai.mcp.client.common.autoconfigure;

import io.modelcontextprotocol.client.McpClient.SyncSpec;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Emits {@link McpToolsChangedEvent} when the MCP Tools have changed for a given MCP
 * connection.
 *
 * @author Christian Tzolov
 */
public class McpSyncToolsChangeEventEmmiter implements McpSyncClientCustomizer {

	private final ApplicationEventPublisher applicationEventPublisher;

	public McpSyncToolsChangeEventEmmiter(ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher, "applicationEventPublisher must not be null");
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void customize(String connectionName, SyncSpec spec) {
		spec.toolsChangeConsumer(
				tools -> this.applicationEventPublisher.publishEvent(new McpToolsChangedEvent(connectionName, tools)));

	}

}
