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

package org.springframework.ai.mcp.toolbox.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.google.cloud.mcp.ProtocolVersion;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration properties for Google Cloud MCP Toolbox.
 *
 * @author Stenal P Jolly
 * @since 2.1.0
 */
@ConfigurationProperties(prefix = McpToolboxProperties.CONFIG_PREFIX)
public class McpToolboxProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.toolbox";

	/**
	 * Whether Google Cloud MCP Toolbox auto-configuration is enabled.
	 */
	private boolean enabled = true;

	/**
	 * Base URL of the MCP Toolbox server (must use http:// or https://).
	 */
	private String url = "http://localhost:5000";

	/**
	 * Optional API key for authenticating with the MCP Toolbox server.
	 */
	@Nullable private String apiKey;

	/**
	 * Client application name sent in telemetry headers to the MCP Toolbox server.
	 */
	private String clientName = "spring-ai-mcp-toolbox";

	/**
	 * Client application version sent in telemetry headers to the MCP Toolbox server.
	 */
	private String clientVersion = "1.0.0";

	/**
	 * Optional preferred MCP protocol version (for example, VERSION_2025_11_25).
	 */
	@Nullable private ProtocolVersion protocolVersion;

	/**
	 * Maximum timeout for MCP Toolbox tool discovery and invocation requests.
	 */
	private Duration timeout = Duration.ofSeconds(30);

	/**
	 * Additional HTTP headers to include in requests to the MCP Toolbox server.
	 */
	private Map<String, String> headers = Map.of();

	/**
	 * Optional list of toolset names to load automatically into the ToolCallbackProvider.
	 */
	private List<String> toolsets = List.of();

	/**
	 * Optional list of specific tool names to load automatically into the
	 * ToolCallbackProvider.
	 */
	private List<String> tools = List.of();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		Assert.hasText(url, "MCP Toolbox URL must not be empty");
		String normalized = url.strip();
		Assert.isTrue(normalized.startsWith("http://") || normalized.startsWith("https://"),
				"MCP Toolbox URL must start with http:// or https://");
		this.url = normalized;
	}

	@Nullable public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public String getClientName() {
		return this.clientName;
	}

	public void setClientName(String clientName) {
		Assert.hasText(clientName, "Client name must not be empty");
		this.clientName = clientName;
	}

	public String getClientVersion() {
		return this.clientVersion;
	}

	public void setClientVersion(String clientVersion) {
		Assert.hasText(clientVersion, "Client version must not be empty");
		this.clientVersion = clientVersion;
	}

	@Nullable public ProtocolVersion getProtocolVersion() {
		return this.protocolVersion;
	}

	public void setProtocolVersion(@Nullable ProtocolVersion protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	public Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(Duration timeout) {
		Assert.notNull(timeout, "Timeout must not be null");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "Timeout must be positive");
		this.timeout = timeout;
	}

	public Map<String, String> getHeaders() {
		return this.headers;
	}

	public void setHeaders(@Nullable Map<String, String> headers) {
		this.headers = headers != null ? Map.copyOf(headers) : Map.of();
	}

	public List<String> getToolsets() {
		return this.toolsets;
	}

	public void setToolsets(@Nullable List<String> toolsets) {
		this.toolsets = toolsets != null ? List.copyOf(toolsets) : List.of();
	}

	public List<String> getTools() {
		return this.tools;
	}

	public void setTools(@Nullable List<String> tools) {
		this.tools = tools != null ? List.copyOf(tools) : List.of();
	}

	@Override
	public String toString() {
		return "McpToolboxProperties{" + "enabled=" + this.enabled + ", url='" + this.url + '\'' + ", apiKey='"
				+ (this.apiKey != null ? "***" : "null") + '\'' + ", clientName='" + this.clientName + '\''
				+ ", clientVersion='" + this.clientVersion + '\'' + ", protocolVersion=" + this.protocolVersion
				+ ", timeout=" + this.timeout + ", headers=" + (this.headers.isEmpty() ? "{}" : "[REDACTED]")
				+ ", toolsets=" + this.toolsets + ", tools=" + this.tools + '}';
	}

}
