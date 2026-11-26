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

package org.springframework.ai.mcp.client.common.autoconfigure.properties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.modelcontextprotocol.client.transport.ServerParameters;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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

	/**
	 * The connections of the servers configuration resource, read once.
	 */
	private @Nullable Map<String, Parameters> resourceConnections;

	public @Nullable Resource getServersConfiguration() {
		return this.serversConfiguration;
	}

	public void setServersConfiguration(@Nullable Resource stdioConnectionResources) {
		this.serversConfiguration = stdioConnectionResources;
		this.resourceConnections = null;
	}

	public Map<String, Parameters> getConnections() {
		return this.connections;
	}

	private Map<String, Parameters> resourceToParameters() {
		if (this.serversConfiguration == null) {
			return Collections.emptyMap();
		}
		Map<String, Parameters> fromResource = this.resourceConnections;
		if (fromResource == null) {
			try {
				Map<String, Map<String, Parameters>> stdioConnection = JsonMapper.shared()
					.readValue(this.serversConfiguration.getInputStream(), new TypeReference<>() {
					});

				fromResource = stdioConnection.entrySet().iterator().next().getValue();
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to read stdio connection resource", e);
			}
			this.resourceConnections = fromResource;
		}
		return fromResource;
	}

	/**
	 * Returns the connections of the servers configuration resource and of the
	 * {@code connections} property. A connection of the property replaces a connection of
	 * the resource that has the same name. The resource is read once, until another one
	 * is set.
	 * @return map of connection names to their parameters
	 * @since 2.1.1
	 */
	public Map<String, Parameters> allConnections() {
		Map<String, Parameters> all = new HashMap<>(resourceToParameters());
		all.putAll(this.connections);
		return all;
	}

	public Map<String, ServerParameters> toServerParameters() {
		return allConnections().entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toServerParameters()));
	}

	/**
	 * Record representing the parameters for an MCP server connection.
	 * <p>
	 * Includes the command to execute, command arguments, and environment variables, and
	 * the protocol that the server speaks.
	 *
	 * @param command the command to execute for the MCP server
	 * @param args the command arguments
	 * @param env the environment variables for the server process
	 * @param protocol the MCP protocol revision the server speaks; {@code LEGACY} when
	 * not set
	 * @param clientScope how the clients of a modern connection are created and held;
	 * {@code PER_REQUEST} when not set. It has no effect on a legacy connection.
	 * @param listen whether a listen stream is kept open to the server of a modern
	 * connection, to hear about its changes; {@code false} when not set. It has no effect
	 * on a legacy connection.
	 */
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record Parameters(@JsonProperty("command") @Nullable String command,
			@JsonProperty("args") @Nullable List<String> args, @JsonProperty("env") @Nullable Map<String, String> env,
			@JsonProperty("protocol") McpClientCommonProperties.@Nullable Protocol protocol,
			@JsonProperty("clientScope") McpClientModernProperties.@Nullable ClientScope clientScope,
			@JsonProperty("listen") @Nullable Boolean listen) {

		/**
		 * Creates the parameters of a connection. This is the constructor the
		 * configuration properties are bound with.
		 * @since 2.1.1
		 */
		@ConstructorBinding
		@JsonCreator
		public Parameters {
		}

		/**
		 * Creates the parameters of a connection that speaks the legacy protocol.
		 * @param command the command to execute for the MCP server
		 * @param args the command arguments
		 * @param env the environment variables for the server process
		 */
		public Parameters(@Nullable String command, @Nullable List<String> args, @Nullable Map<String, String> env) {
			this(command, args, env, null, null, null);
		}

		public ServerParameters toServerParameters() {
			return ServerParameters.builder(this.command()).args(this.argsOrEmpty()).env(this.env()).build();
		}

		/**
		 * Whether the connection speaks the 2026-07-28 ("modern") protocol revision.
		 * @return {@code true} for a modern connection, {@code false} for a legacy one
		 * @since 2.1.1
		 */
		@JsonIgnore
		public boolean isModern() {
			return this.protocol == McpClientCommonProperties.Protocol.MODERN;
		}

		/**
		 * Whether one client serves all requests of the connection.
		 * @return {@code true} for the {@code SHARED} client scope, {@code false} for the
		 * other scopes
		 * @since 2.1.1
		 */
		@JsonIgnore
		public boolean isSharedClient() {
			return this.clientScope == McpClientModernProperties.ClientScope.SHARED;
		}

		/**
		 * Whether the client scope bean of the application selects the client of each
		 * request of the connection.
		 * @return {@code true} for the {@code CUSTOM} client scope
		 * @since 2.1.1
		 */
		@JsonIgnore
		public boolean isCustomClientScope() {
			return this.clientScope == McpClientModernProperties.ClientScope.CUSTOM;
		}

		/**
		 * Whether a listen stream is kept open to the server of the connection.
		 * @return {@code true} if the connection listens for the changes of its server
		 * @since 2.1.1
		 */
		@JsonIgnore
		public boolean isListening() {
			return Boolean.TRUE.equals(this.listen);
		}

		private List<String> argsOrEmpty() {
			return this.args() != null ? this.args() : Collections.emptyList();
		}

	}

}
