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

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema.Tool;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when the MCP Tools have changed for a given MCP connection.
 *
 * @author Christian Tzolov
 */
public class McpToolsChangedEvent extends ApplicationEvent {

	private final String connectionName;

	private final List<Tool> tools;

	public McpToolsChangedEvent(String connectionName, List<Tool> tools) {
		super(connectionName);
		this.connectionName = connectionName;
		this.tools = tools;
	}

	public String getConnectionName() {
		return this.connectionName;
	}

	public List<Tool> getTools() {
		return this.tools;
	}

}
