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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

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
	 * Resource containing the MCP servers configuration.
	 * <p>
	 * This resource should contain a JSON configuration defining the MCP servers and
	 * their parameters.
	 */
	private @Nullable Resource serversConfiguration;

	/**
	 * Map of MCP stdio connections configurations.
	 * <p>
	 * Each entry represents a named connection with its specific configuration
	 * parameters.
	 */
	private final Map<String, Parameters> connections = new HashMap<>();

	public @Nullable Resource getServersConfiguration() {
		return this.serversConfiguration;
	}

	public void setServersConfiguration(@Nullable Resource stdioConnectionResources) {
		this.serversConfiguration = stdioConnectionResources;
	}

	public Map<String, Parameters> getConnections() {
		return this.connections;
	}

	private Map<String, ServerParameters> resourceToServerParameters() {
		if (this.serversConfiguration == null) {
			return Collections.emptyMap();
		}
		try {
			Map<String, Map<String, Parameters>> stdioConnection = new ObjectMapper()
				.readValue(this.serversConfiguration.getInputStream(), new TypeReference<>() {
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
		serverParameters.putAll(resourceToServerParameters());

		for (Map.Entry<String, Parameters> entry : this.connections.entrySet()) {
			serverParameters.put(entry.getKey(), entry.getValue().toServerParameters());
		}
		return serverParameters;
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
			@JsonProperty("command") @Nullable String command,
			/**
			 * List of command arguments.
			 */
			@JsonProperty("args") @Nullable List<String> args,
			/**
			 * Map of environment variables for the server process.
			 */
			@JsonProperty("env") @Nullable Map<String, String> env) {

		public ServerParameters toServerParameters() {
			return ServerParameters.builder(this.command()).args(this.args()).env(this.env()).build();
		}

	}

}
