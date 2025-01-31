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

package org.springframework.ai.autoconfigure.mcp.client.stdio;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Configuration properties for the Model Context Protocol (MCP) stdio client.
 * <p>
 * This class manages configuration settings for MCP stdio client connections, including
 * server parameters, timeouts, and connection details. It supports both direct
 * configuration through properties and configuration through external resource files.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(McpStdioClientProperties.CONFIG_PREFIX)
public class McpStdioClientProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mcp.client.stdio";

	/**
	 * Enable/disable the MCP client.
	 * <p>
	 * When set to false, the MCP client and all its components will not be initialized.
	 */
	private boolean enabled = false;

	/**
	 * The version of the MCP client instance.
	 * <p>
	 * This version is reported to clients and used for compatibility checks.
	 */
	private String version = "1.0.0";

	/**
	 * The timeout duration for MCP client requests.
	 * <p>
	 * Defaults to 20 seconds.
	 */
	private Duration requestTimeout = Duration.ofSeconds(20);

	/**
	 * Flag to enable/disable root change notifications.
	 * <p>
	 * When enabled, the client will be notified of changes to the root configuration.
	 * Defaults to true.
	 */
	private boolean rootChangeNotification = true;

	/**
	 * Resource containing the MCP servers configuration.
	 * <p>
	 * This resource should contain a JSON configuration defining the MCP servers and
	 * their parameters.
	 */
	private Resource serversConfiguration;

	/**
	 * Map of MCP stdio connections configurations.
	 * <p>
	 * Each entry represents a named connection with its specific configuration
	 * parameters.
	 */
	private final Map<String, McpStdioConnection> stdioConnections = new HashMap<>();

	/**
	 * Flag to indicate if the MCP client has to be initialized.
	 */
	private boolean initialize = true;

	public Resource getServersConfiguration() {
		return this.serversConfiguration;
	}

	public void setServersConfiguration(Resource stdioConnectionResources) {
		this.serversConfiguration = stdioConnectionResources;
	}

	public Map<String, McpStdioConnection> getStdioConnections() {
		return this.stdioConnections;
	}

	public boolean isRootChangeNotification() {
		return this.rootChangeNotification;
	}

	public void setRootChangeNotification(boolean rootChangeNotification) {
		this.rootChangeNotification = rootChangeNotification;
	}

	public Duration getRequestTimeout() {
		return this.requestTimeout;
	}

	public void setRequestTimeout(Duration requestTimeout) {
		Assert.notNull(requestTimeout, "Request timeout must not be null");
		this.requestTimeout = requestTimeout;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		Assert.hasText(version, "Version must not be empty");
		this.version = version;
	}

	public boolean isInitialize() {
		return this.initialize;
	}

	public void setInitialize(boolean initialize) {
		this.initialize = initialize;
	}

	/**
	 * Record representing the parameters for an MCP server connection.
	 * <p>
	 * Includes the command to execute, command arguments, and environment variables.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record Parameters(
			/**
			 * The command to execute for the MCP server.
			 */
			@JsonProperty("command") String command,
			/**
			 * List of command arguments.
			 */
			@JsonProperty("args") List<String> args,
			/**
			 * Map of environment variables for the server process.
			 */
			@JsonProperty("env") Map<String, String> env) {
	}

	private Map<String, ServerParameters> resourceToServerParameters() {
		try {
			Map<String, Map<String, Parameters>> stdioConnection = new ObjectMapper().readValue(
					this.serversConfiguration.getInputStream(),
					new TypeReference<Map<String, Map<String, Parameters>>>() {
					});

			Map<String, Parameters> mcpServerJsonConfig = stdioConnection.entrySet().iterator().next().getValue();

			return mcpServerJsonConfig.entrySet().stream().collect(Collectors.toMap(kv -> kv.getKey(), kv -> {
				Parameters parameters = kv.getValue();
				return ServerParameters.builder(parameters.command())
					.args(parameters.args())
					.env(parameters.env())
					.build();
			}));
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to read stdio connection resource", e);
		}
	}

	public Map<String, ServerParameters> toServerParameters() {
		Map<String, ServerParameters> serverParameters = new HashMap<>();
		if (this.serversConfiguration != null) {
			serverParameters.putAll(resourceToServerParameters());
		}

		for (Map.Entry<String, McpStdioConnection> entry : this.stdioConnections.entrySet()) {
			serverParameters.put(entry.getKey(), entry.getValue().toServerParameters());
		}
		return serverParameters;
	}

}
