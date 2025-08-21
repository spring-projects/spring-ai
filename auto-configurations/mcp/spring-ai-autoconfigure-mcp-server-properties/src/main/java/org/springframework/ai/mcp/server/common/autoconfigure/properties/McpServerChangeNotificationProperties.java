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

package org.springframework.ai.mcp.server.common.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(McpServerChangeNotificationProperties.CONFIG_PREFIX)
public class McpServerChangeNotificationProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.server";

	/**
	 * Enable/disable notifications for resource changes. Only relevant for MCP servers
	 * with resource capabilities.
	 * <p>
	 * When enabled, the server will notify clients when resources are added, updated, or
	 * removed.
	 */
	private boolean resourceChangeNotification = true;

	/**
	 * Enable/disable notifications for tool changes. Only relevant for MCP servers with
	 * tool capabilities.
	 * <p>
	 * When enabled, the server will notify clients when tools are registered or
	 * unregistered.
	 */
	private boolean toolChangeNotification = true;

	/**
	 * Enable/disable notifications for prompt changes. Only relevant for MCP servers with
	 * prompt capabilities.
	 * <p>
	 * When enabled, the server will notify clients when prompt templates are modified.
	 */
	private boolean promptChangeNotification = true;

	public boolean isResourceChangeNotification() {
		return this.resourceChangeNotification;
	}

	public void setResourceChangeNotification(boolean resourceChangeNotification) {
		this.resourceChangeNotification = resourceChangeNotification;
	}

	public boolean isToolChangeNotification() {
		return this.toolChangeNotification;
	}

	public void setToolChangeNotification(boolean toolChangeNotification) {
		this.toolChangeNotification = toolChangeNotification;
	}

	public boolean isPromptChangeNotification() {
		return this.promptChangeNotification;
	}

	public void setPromptChangeNotification(boolean promptChangeNotification) {
		this.promptChangeNotification = promptChangeNotification;
	}

}
