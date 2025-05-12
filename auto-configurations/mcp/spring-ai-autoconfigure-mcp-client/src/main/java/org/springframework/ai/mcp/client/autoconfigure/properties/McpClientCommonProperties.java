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

package org.springframework.ai.mcp.client.autoconfigure.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Common Configuration properties for the Model Context Protocol (MCP) clients shared for
 * all transport types.
 *
 * @author Christian Tzolov
 * @author Yangki Zhang
 * @since 1.0.0
 */
@ConfigurationProperties(McpClientCommonProperties.CONFIG_PREFIX)
public class McpClientCommonProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.client";

	/**
	 * Enable/disable the MCP client.
	 * <p>
	 * When set to false, the MCP client and all its components will not be initialized.
	 */
	private boolean enabled = true;

	/**
	 * The name of the MCP client instance.
	 * <p>
	 * This name is reported to clients and used for compatibility checks.
	 */
	private String name = "spring-ai-mcp-client";

	/**
	 * The version of the MCP client instance.
	 * <p>
	 * This version is reported to clients and used for compatibility checks.
	 */
	private String version = "1.0.0";

	/**
	 * Flag to indicate if the MCP client has to be initialized.
	 */
	private boolean initialized = true;

	/**
	 * The timeout duration for MCP client requests.
	 * <p>
	 * Defaults to 20 seconds.
	 */
	private Duration requestTimeout = Duration.ofSeconds(20);

	/**
	 * The type of client to use for MCP client communication.
	 * <p>
	 * Supported types are:
	 * <ul>
	 * <li>SYNC - Standard synchronous client (default)</li>
	 * <li>ASYNC - Asynchronous client</li>
	 * </ul>
	 */
	private ClientType type = ClientType.SYNC;

	/**
	 * Client types supported by the MCP client.
	 */
	public enum ClientType {

		/**
		 * Synchronous (McpSyncClient) client
		 */
		SYNC,

		/**
		 * Asynchronous (McpAsyncClient) client
		 */
		ASYNC

	}

	/**
	 * Flag to enable/disable root change notifications.
	 * <p>
	 * When enabled, the client will be notified of changes to the root configuration.
	 * Defaults to true.
	 */
	private boolean rootChangeNotification = true;

	/**
	 * Tool callback configuration.
	 * <p>
	 * This configuration is used to enable or disable tool callbacks in the MCP client.
	 */
	private Toolcallback toolcallback = new Toolcallback();

	/**
	 * Represents a callback configuration for tools.
	 * <p>
	 * This record is used to encapsulate the configuration for enabling or disabling tool
	 * callbacks in the MCP client.
	 *
	 * @param enabled A boolean flag indicating whether the tool callback is enabled. If
	 * true, the tool callback is active; otherwise, it is disabled.
	 */
	public static class Toolcallback {

		/**
		 * A boolean flag indicating whether the tool callback is enabled. If true, the
		 * tool callback is active; otherwise, it is disabled.
		 */
		private boolean enabled = true;

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean isInitialized() {
		return this.initialized;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public Duration getRequestTimeout() {
		return this.requestTimeout;
	}

	public void setRequestTimeout(Duration requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public ClientType getType() {
		return this.type;
	}

	public void setType(ClientType type) {
		this.type = type;
	}

	public boolean isRootChangeNotification() {
		return this.rootChangeNotification;
	}

	public void setRootChangeNotification(boolean rootChangeNotification) {
		this.rootChangeNotification = rootChangeNotification;
	}

	public Toolcallback getToolcallback() {
		return toolcallback;
	}

	public void setToolcallback(Toolcallback toolcallback) {
		this.toolcallback = toolcallback;
	}

}
