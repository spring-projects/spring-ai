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

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(McpServerSseProperties.CONFIG_PREFIX)
public class McpServerSseProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.server";

	/**
	 */
	private String baseUrl = "";

	/**
	 * An SSE endpoint, for clients to establish a connection and receive messages from
	 * the server
	 */
	private String sseEndpoint = "/sse";

	/**
	 * A regular HTTP POST endpoint for clients to send messages to the server.
	 */
	private String sseMessageEndpoint = "/mcp/message";

	/**
	 * The duration to keep the connection alive. Disabled by default.
	 */
	private @Nullable Duration keepAliveInterval;

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		Assert.notNull(baseUrl, "Base URL must not be null");
		this.baseUrl = baseUrl;
	}

	public String getSseEndpoint() {
		return this.sseEndpoint;
	}

	public void setSseEndpoint(String sseEndpoint) {
		Assert.hasText(sseEndpoint, "SSE endpoint must not be empty");
		this.sseEndpoint = sseEndpoint;
	}

	public String getSseMessageEndpoint() {
		return this.sseMessageEndpoint;
	}

	public void setSseMessageEndpoint(String sseMessageEndpoint) {
		Assert.hasText(sseMessageEndpoint, "SSE message endpoint must not be empty");
		this.sseMessageEndpoint = sseMessageEndpoint;
	}

	public @Nullable Duration getKeepAliveInterval() {
		return this.keepAliveInterval;
	}

	public void setKeepAliveInterval(@Nullable Duration keepAliveInterval) {
		this.keepAliveInterval = keepAliveInterval;
	}

}
