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

package org.springframework.ai.mcp.server.common.autoconfigure.properties;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @author Armen Petrosyan
 */
@ConfigurationProperties(McpServerStreamableHttpProperties.CONFIG_PREFIX)
public class McpServerStreamableHttpProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.server.streamable-http";

	/**
	 */
	private String mcpEndpoint = "/mcp";

	/**
	 * The duration to keep the connection alive.
	 */
	private @Nullable Duration keepAliveInterval;

	private boolean disallowDelete;

	/**
	 * Maximum number of concurrent sessions. Requests to initialize a new session are
	 * rejected once this limit is reached.
	 */
	private long maxSessions = 100_000;

	/**
	 * Duration of inactivity after which a session is evicted and closed. Must be greater
	 * than the keep-alive interval when both are set. When not set, idle sessions are
	 * never evicted.
	 */
	private @Nullable Duration sessionIdleTimeout;

	public String getMcpEndpoint() {
		return this.mcpEndpoint;
	}

	public void setMcpEndpoint(String mcpEndpoint) {
		Assert.hasText(mcpEndpoint, "MCP endpoint must not be empty");
		this.mcpEndpoint = mcpEndpoint;
	}

	public void setKeepAliveInterval(@Nullable Duration keepAliveInterval) {
		Assert.notNull(keepAliveInterval, "Keep-alive interval must not be null");
		this.keepAliveInterval = keepAliveInterval;
	}

	public @Nullable Duration getKeepAliveInterval() {
		return this.keepAliveInterval;
	}

	public boolean isDisallowDelete() {
		return this.disallowDelete;
	}

	public void setDisallowDelete(boolean disallowDelete) {
		this.disallowDelete = disallowDelete;
	}

	/**
	 * @since 2.1.0
	 */
	public long getMaxSessions() {
		return this.maxSessions;
	}

	/**
	 * @since 2.1.0
	 */
	public void setMaxSessions(long maxSessions) {
		this.maxSessions = maxSessions;
	}

	/**
	 * @since 2.1.0
	 */
	public @Nullable Duration getSessionIdleTimeout() {
		return this.sessionIdleTimeout;
	}

	/**
	 * @since 2.1.0
	 */
	public void setSessionIdleTimeout(@Nullable Duration sessionIdleTimeout) {
		this.sessionIdleTimeout = sessionIdleTimeout;
	}

}
