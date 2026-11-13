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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.modern.client.transport.McpHttpAuthorizationErrorHandler;
import io.modelcontextprotocol.modern.client.transport.McpHttpRequestCustomizer;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientModernProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.modern.client.McpModernConnectionInfo;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration of the MCP client connections that speak the 2026-07-28 ("modern")
 * protocol revision. A connection is modern when its {@code protocol} property is
 * {@code MODERN}, for example
 * {@code spring.ai.mcp.client.streamable-http.connections.tickets.protocol=MODERN}.
 * Connections without that property stay on the legacy protocol, so an application can
 * talk to legacy and modern servers at the same time.
 * <p>
 * It creates two kinds of beans, the modern counterparts of the legacy transports and
 * clients:
 * <ul>
 * <li>a {@link NamedModernClientMcpTransport} for each modern connection under
 * {@code spring.ai.mcp.client.streamable-http.connections}, on the JDK
 * {@code HttpClient};</li>
 * <li>a {@link SyncModernMcpConnection} for each transport, which creates a client for
 * each request, or holds one shared client if the connection sets
 * {@code client-scope=SHARED}.</li>
 * </ul>
 * A tool listing is kept for as long as the server allows when the server marks it as
 * {@code public}. A listing the server marks as {@code private} is kept only by a
 * connection with {@code client-scope=SHARED}, which is meant for an application that
 * calls the server under a single identity.
 * <p>
 * {@link McpToolCallbackAutoConfiguration} adds the tools of these connections to the
 * {@code SyncMcpToolCallbackProvider} bean, next to the tools of the legacy clients, so
 * the application keeps injecting a single {@code ToolCallbackProvider}.
 * <p>
 * Modern clients are created without contacting the server, so nothing is sent at startup
 * and {@code spring.ai.mcp.client.initialized} does not apply to them. Only the
 * synchronous client type is supported: with {@code spring.ai.mcp.client.type=ASYNC} a
 * modern connection fails the startup.
 * <p>
 * Customization points:
 * <ul>
 * <li>{@code McpClientCustomizer<HttpClientMcpTransport.Builder>} beans customize a
 * transport;</li>
 * <li>{@link McpHttpRequestCustomizer} beans and a
 * {@link McpHttpAuthorizationErrorHandler} bean are added to every transport, for example
 * to send an access token;</li>
 * <li>{@code McpClientCustomizer<McpClient.SyncBuilder>} beans customize a client, for
 * example to set its {@code transportContextProvider}. With the default
 * {@code PER_REQUEST} client scope they run for each new client, on the thread that
 * resolves the tools.</li>
 * </ul>
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@AutoConfiguration
@ConditionalOnClass(McpClient.class)
@EnableConfigurationProperties({ McpClientCommonProperties.class, McpClientModernProperties.class,
		McpStreamableHttpClientProperties.class })
@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class McpClientModernAutoConfiguration {

	private static final String DEFAULT_ENDPOINT = "/mcp";

	/**
	 * Creates a transport on the JDK {@code HttpClient} for each modern Streamable HTTP
	 * connection.
	 * @param streamableProperties the configured connections
	 * @param jsonMapperProvider the JSON mapper of the application, if any
	 * @param requestCustomizers the customizers that add headers to each HTTP request
	 * @param authorizationErrorHandler the handler of {@code 401} and {@code 403}
	 * answers, if any
	 * @param transportCustomizers the customizers of the transport builders
	 * @return the named transports
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<NamedModernClientMcpTransport> modernStreamableHttpTransports(
			McpStreamableHttpClientProperties streamableProperties, ObjectProvider<JsonMapper> jsonMapperProvider,
			ObjectProvider<McpHttpRequestCustomizer> requestCustomizers,
			ObjectProvider<McpHttpAuthorizationErrorHandler> authorizationErrorHandler,
			ObjectProvider<McpClientCustomizer<HttpClientMcpTransport.Builder>> transportCustomizers) {

		JsonMapper jsonMapper = jsonMapperProvider.getIfAvailable(JsonMapper::shared);

		List<NamedModernClientMcpTransport> transports = new ArrayList<>();

		for (Map.Entry<String, ConnectionParameters> connection : streamableProperties.getConnections().entrySet()) {

			if (!connection.getValue().isModern()) {
				// Legacy connections are served by the legacy transport
				// auto-configurations.
				continue;
			}

			String name = connection.getKey();

			HttpClientMcpTransport.Builder transportBuilder = HttpClientMcpTransport
				.builder(endpointUri(name, connection.getValue()))
				.jsonMapper(new JacksonMcpJsonMapper(jsonMapper));

			requestCustomizers.orderedStream().forEach(transportBuilder::requestCustomizer);
			authorizationErrorHandler.ifAvailable(transportBuilder::authorizationErrorHandler);

			transportCustomizers.orderedStream().forEach(customizer -> customizer.customize(name, transportBuilder));

			transports.add(new NamedModernClientMcpTransport(name, transportBuilder.build()));
		}

		return transports;
	}

	/**
	 * Creates a connection for each named transport. All clients of a connection share
	 * its transport. The client scope is read from the configured connection of the same
	 * name, so a transport contributed by the application without such a connection gets
	 * a client for each request.
	 * @param transportsProvider the named transports, auto-configured or contributed by
	 * the application
	 * @param streamableProperties the configured connections
	 * @param commonProperties the common MCP client properties
	 * @param modernProperties the modern MCP client properties
	 * @param clientCustomizers the customizers of the client builders
	 * @return the connections
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncModernMcpConnection> modernMcpSyncConnections(
			ObjectProvider<List<NamedModernClientMcpTransport>> transportsProvider,
			McpStreamableHttpClientProperties streamableProperties, McpClientCommonProperties commonProperties,
			McpClientModernProperties modernProperties,
			ObjectProvider<McpClientCustomizer<McpClient.SyncBuilder>> clientCustomizers) {

		Implementation clientInfo = Implementation.builder(commonProperties.getName(), commonProperties.getVersion())
			.build();

		// Resolved once: with a client for each request the customizers run for each
		// request.
		List<McpClientCustomizer<McpClient.SyncBuilder>> customizers = clientCustomizers.orderedStream().toList();

		List<SyncModernMcpConnection> connections = new ArrayList<>();

		for (NamedModernClientMcpTransport namedTransport : transportsProvider.stream()
			.flatMap(List::stream)
			.toList()) {

			McpModernConnectionInfo connectionInfo = new McpModernConnectionInfo(namedTransport.name(), clientInfo);

			ConnectionParameters parameters = streamableProperties.getConnections().get(namedTransport.name());

			if (parameters != null && parameters.isSharedClient()) {
				connections.add(SyncModernMcpConnection.shared(connectionInfo,
						buildClient(namedTransport, clientInfo, commonProperties, modernProperties, customizers)));
			}
			else {
				connections.add(SyncModernMcpConnection.perRequest(connectionInfo, () -> buildClient(namedTransport,
						clientInfo, commonProperties, modernProperties, customizers)));
			}
		}

		return connections;
	}

	private static McpSyncClient buildClient(NamedModernClientMcpTransport namedTransport, Implementation clientInfo,
			McpClientCommonProperties commonProperties, McpClientModernProperties modernProperties,
			List<McpClientCustomizer<McpClient.SyncBuilder>> clientCustomizers) {

		McpClient.SyncBuilder clientBuilder = McpClient.sync(namedTransport.transport())
			.clientInfo(clientInfo)
			.roundTimeout(commonProperties.getRequestTimeout());

		if (modernProperties.getCallTimeout() != null) {
			clientBuilder.callTimeout(modernProperties.getCallTimeout());
		}

		clientCustomizers.forEach(customizer -> customizer.customize(namedTransport.name(), clientBuilder));

		return clientBuilder.build();
	}

	/**
	 * The URI a modern connection sends its requests to: the {@code endpoint} of the
	 * connection ({@code /mcp} when not set) appended to its {@code url}. A path in the
	 * {@code url} is kept, so {@code http://host/api} and {@code /mcp} give
	 * {@code http://host/api/mcp}. The legacy WebClient transport composes the two in the
	 * same way; the legacy JDK {@code HttpClient} transport resolves the endpoint against
	 * the {@code url} instead, which drops such a path.
	 */
	static URI endpointUri(String connectionName, ConnectionParameters parameters) {
		String url = parameters.url();
		if (!StringUtils.hasText(url)) {
			throw new IllegalStateException("The MCP connection '" + connectionName + "' has no url");
		}
		String endpoint = parameters.endpoint();
		if (!StringUtils.hasText(endpoint)) {
			endpoint = DEFAULT_ENDPOINT;
		}
		return URI
			.create(StringUtils.trimTrailingCharacter(url, '/') + (endpoint.startsWith("/") ? "" : "/") + endpoint);
	}

	/**
	 * Fails the startup when a modern connection is configured together with the
	 * asynchronous client type, which the modern protocol does not support yet. Without
	 * this check such a connection would be silently ignored.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "ASYNC")
	static class AsyncClientTypeGuardConfiguration {

		AsyncClientTypeGuardConfiguration(McpStreamableHttpClientProperties streamableProperties) {
			List<String> modernConnections = streamableProperties.getConnections()
				.entrySet()
				.stream()
				.filter(connection -> connection.getValue().isModern())
				.map(Map.Entry::getKey)
				.sorted()
				.toList();
			if (!modernConnections.isEmpty()) {
				throw new IllegalStateException(
						"The MCP connections " + modernConnections + " use the MODERN protocol, which supports only "
								+ McpClientCommonProperties.CONFIG_PREFIX + ".type=SYNC");
			}
		}

	}

}
