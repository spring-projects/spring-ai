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

import java.lang.annotation.Annotation;
import java.net.URI;
import java.time.Duration;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.spring.ClientMcpSyncHandlersRegistry;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientModernProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.modern.client.McpModernConnectionInfo;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeListener;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeSubscriber;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration of the MCP client connections that speak the 2026-07-28 ("modern")
 * protocol revision. A connection is modern when its {@code protocol} property is
 * {@code MODERN}, for example
 * {@code spring.ai.mcp.client.streamable-http.connections.tickets.protocol=MODERN}.
 * Connections without that property stay on the legacy protocol, so an application can
 * talk to legacy and modern servers at the same time.
 * <p>
 * It creates three kinds of beans, the first two being the modern counterparts of the
 * legacy transports and clients:
 * <ul>
 * <li>a {@link NamedModernClientMcpTransport} for each modern connection under
 * {@code spring.ai.mcp.client.streamable-http.connections}, on the JDK
 * {@code HttpClient};</li>
 * <li>a {@link SyncModernMcpConnection} for each transport, which creates a client for
 * each request, or holds one shared client if the connection sets
 * {@code client-scope=SHARED}.</li>
 * <li>a {@link SyncModernMcpChangeSubscriber}, which keeps a listen stream open to the
 * server of each connection that sets {@code listen=true}, so that a kept tool listing is
 * dropped as soon as the server reports that its tools changed.</li>
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
 * The {@code @McpElicitation} and {@code @McpProgress} methods that name a modern
 * connection in their {@code clients} attribute answer the elicitation requests and
 * receive the progress of that connection, as they do for a legacy connection. A method
 * may take the schema types of either protocol, and a {@code ToolContext} to receive the
 * tool context of the tool call it serves. The {@code @McpToolListChanged},
 * {@code @McpPromptListChanged} and {@code @McpResourceListChanged} methods are called
 * for a connection that sets {@code listen=true}. {@code @McpSampling} and
 * {@code @McpLogging} are not supported for modern connections, and a warning is logged
 * for them.
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
 * resolves the tools. A handler set by a customizer is used for the connections that no
 * annotated handler method names; for the tool calls of the others the annotated methods
 * take precedence.</li>
 * <li>{@link SyncModernMcpChangeListener} beans hear about the changes that the servers
 * of the listening connections report.</li>
 * <li>{@code SyncModernMcpToolCallCustomizer} beans customize each tool call, with the
 * {@code ToolContext} of that call. They run after the annotated handlers are set for the
 * call, so they can replace them.</li>
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

	private static final Log logger = LogFactory.getLog(McpClientModernAutoConfiguration.class);

	private static final String DEFAULT_ENDPOINT = "/mcp";

	/** The pauses before a listen stream is opened again grow to at least this. */
	private static final Duration MIN_LISTEN_REOPEN_DELAY = Duration.ofSeconds(1);

	/**
	 * The client annotations whose handler methods are not called for a modern
	 * connection: sampling and logging do not exist in the modern client.
	 */
	private static final List<Class<? extends Annotation>> UNSUPPORTED_HANDLER_ANNOTATIONS = List.of(McpSampling.class,
			McpLogging.class);

	/**
	 * The client annotations whose handler methods are called for a modern connection
	 * only if it keeps a listen stream open, on which the server reports its changes.
	 */
	private static final List<Class<? extends Annotation>> LIST_CHANGED_HANDLER_ANNOTATIONS = List
		.of(McpToolListChanged.class, McpPromptListChanged.class, McpResourceListChanged.class);

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
	 * @param handlersRegistry the registry of the annotated client handler methods, if
	 * the annotation scanner is enabled
	 * @return the connections
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public List<SyncModernMcpConnection> modernMcpSyncConnections(
			ObjectProvider<List<NamedModernClientMcpTransport>> transportsProvider,
			McpStreamableHttpClientProperties streamableProperties, McpClientCommonProperties commonProperties,
			McpClientModernProperties modernProperties,
			ObjectProvider<McpClientCustomizer<McpClient.SyncBuilder>> clientCustomizers,
			ObjectProvider<ClientMcpSyncHandlersRegistry> handlersRegistry) {

		ClientMcpSyncHandlersRegistry handlers = handlersRegistry.getIfAvailable();

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

			if (handlers != null) {
				warnAboutUnsupportedHandlers(namedTransport.name(), handlers,
						parameters != null && parameters.isListening());
			}

			if (parameters != null && parameters.isSharedClient()) {
				connections.add(SyncModernMcpConnection.shared(connectionInfo, buildClient(namedTransport, clientInfo,
						commonProperties, modernProperties, handlers, customizers)));
			}
			else {
				connections.add(SyncModernMcpConnection.perRequest(connectionInfo, () -> buildClient(namedTransport,
						clientInfo, commonProperties, modernProperties, handlers, customizers)));
			}
		}

		return connections;
	}

	/**
	 * Creates the subscriber that keeps a listen stream open to the server of each modern
	 * connection that sets {@code listen=true}. It opens the streams when the application
	 * has started, without waiting for the servers. A stream that ended is opened again
	 * after one second, and after pauses that double up to the unreachable cool-down time
	 * for as long as that fails. A stream is opened by a client of its own, outside of
	 * any request, so it carries the identity of the application and not that of a
	 * caller.
	 * @param connectionsProvider the modern connections
	 * @param streamableProperties the configured connections
	 * @param modernProperties the modern MCP client properties
	 * @param changeListeners the listeners of the application, which hear about what the
	 * servers report next to the tool callback provider
	 * @param handlersRegistry the registry of the annotated client handler methods, if
	 * the annotation scanner is enabled; a stream also listens for the changes of the
	 * prompts and resources if a list-changed method wants to hear about them
	 * @return the subscriber, which listens to no server if no connection asks for it
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	public SyncModernMcpChangeSubscriber modernMcpChangeSubscriber(
			ObjectProvider<List<SyncModernMcpConnection>> connectionsProvider,
			McpStreamableHttpClientProperties streamableProperties, McpClientModernProperties modernProperties,
			ObjectProvider<SyncModernMcpChangeListener> changeListeners,
			ObjectProvider<ClientMcpSyncHandlersRegistry> handlersRegistry) {

		ClientMcpSyncHandlersRegistry handlers = handlersRegistry.getIfAvailable();

		Duration coolDown = modernProperties.getUnreachableCoolDown();
		SyncModernMcpChangeSubscriber.Builder subscriber = SyncModernMcpChangeSubscriber.builder()
			.maxReopenDelay(coolDown.compareTo(MIN_LISTEN_REOPEN_DELAY) < 0 ? MIN_LISTEN_REOPEN_DELAY : coolDown);

		connectionsProvider.stream().flatMap(List::stream).forEach(connection -> {
			String name = connection.info().name();
			ConnectionParameters parameters = streamableProperties.getConnections().get(name);
			if (parameters != null && parameters.isListening()) {
				// The tools are always listened for. The prompts and the resources only
				// if an annotated method wants to hear about them.
				subscriber.connection(connection,
						handlers != null && handlers.handlesModernListChanged(name, McpPromptListChanged.class),
						handlers != null && handlers.handlesModernListChanged(name, McpResourceListChanged.class));
			}
		});

		// Only a connection that uses the modern protocol has a listen stream.
		streamableProperties.getConnections().forEach((name, parameters) -> {
			if (parameters.isListening() && !parameters.isModern()) {
				logger.warn("The MCP connection '" + name + "' sets listen=true but does not use the MODERN "
						+ "protocol; no listen stream is opened for it.");
			}
		});

		changeListeners.orderedStream().forEach(subscriber::listener);

		return subscriber.build();
	}

	/**
	 * Has the {@code @McpToolListChanged}, {@code @McpPromptListChanged} and
	 * {@code @McpResourceListChanged} methods called for the changes that the servers of
	 * the listening connections report. The listener for them is added to the subscriber,
	 * whether auto-configured or defined by the application, once all beans are created.
	 * That is after the tool callback provider was added, so that a method that asks the
	 * provider for its tools gets the new ones. Nothing is added if no such method names
	 * a listening connection. The bean is never lazy, as nothing would ask for it.
	 * @param changeSubscriber the subscriber of the listen streams
	 * @param connectionsProvider the modern connections
	 * @param handlersRegistry the registry of the annotated client handler methods, if
	 * the annotation scanner is enabled
	 * @return the callback that adds the listener
	 */
	@Bean
	@ConditionalOnProperty(prefix = McpClientCommonProperties.CONFIG_PREFIX, name = "type", havingValue = "SYNC",
			matchIfMissing = true)
	@Lazy(false)
	SmartInitializingSingleton modernListChangedHandlersRegistrar(
			ObjectProvider<SyncModernMcpChangeSubscriber> changeSubscriber,
			ObjectProvider<List<SyncModernMcpConnection>> connectionsProvider,
			ObjectProvider<ClientMcpSyncHandlersRegistry> handlersRegistry) {
		return () -> {
			SyncModernMcpChangeSubscriber subscriber = changeSubscriber.getIfAvailable();
			ClientMcpSyncHandlersRegistry handlers = handlersRegistry.getIfAvailable();
			if (subscriber == null || handlers == null) {
				return;
			}
			List<String> listening = subscriber.connectionNames();
			ModernListChangedHandlers listChangedHandlers = ModernListChangedHandlers
				.forConnections(connectionsProvider.stream()
					.flatMap(List::stream)
					.filter(connection -> listening.contains(connection.info().name()))
					.toList(), handlers);
			if (listChangedHandlers != null) {
				subscriber.addListener(listChangedHandlers);
			}
		};
	}

	private static McpSyncClient buildClient(NamedModernClientMcpTransport namedTransport, Implementation clientInfo,
			McpClientCommonProperties commonProperties, McpClientModernProperties modernProperties,
			@Nullable ClientMcpSyncHandlersRegistry handlers,
			List<McpClientCustomizer<McpClient.SyncBuilder>> clientCustomizers) {

		McpClient.SyncBuilder clientBuilder = McpClient.sync(namedTransport.transport())
			.clientInfo(clientInfo)
			.roundTimeout(commonProperties.getRequestTimeout());

		if (modernProperties.getCallTimeout() != null) {
			clientBuilder.callTimeout(modernProperties.getCallTimeout());
		}

		if (handlers != null) {
			applyAnnotatedHandlers(namedTransport.name(), clientBuilder, handlers);
		}

		clientCustomizers.forEach(customizer -> customizer.customize(namedTransport.name(), clientBuilder));

		return clientBuilder.build();
	}

	/**
	 * Sets the {@code @McpElicitation} and {@code @McpProgress} methods that name the
	 * connection as the handlers of its client. A handler is only set when such a method
	 * exists, because a client declares the elicitation modes it has a handler for and
	 * asks the server for progress only when it has a listener.
	 */
	private static void applyAnnotatedHandlers(String connectionName, McpClient.SyncBuilder clientBuilder,
			ClientMcpSyncHandlersRegistry handlers) {
		if (handlers.handlesModernFormElicitation(connectionName)) {
			clientBuilder.formElicitation(request -> handlers.handleModernElicitation(connectionName, request));
		}
		if (handlers.handlesModernUrlElicitation(connectionName)) {
			clientBuilder.urlElicitation(request -> handlers.handleModernElicitation(connectionName, request));
		}
		if (handlers.handlesModernProgress(connectionName)) {
			clientBuilder.progress(progress -> handlers.handleModernProgress(connectionName, progress));
		}
	}

	/**
	 * Sets the {@code @McpElicitation} and {@code @McpProgress} methods that name a
	 * connection as the handlers of each of its tool calls, in place of those set on the
	 * client. A handler set for a call knows the {@code ToolContext} of that call, which
	 * the methods receive if they declare a {@code ToolContext} parameter.
	 * @param handlers the registry of the annotated client handler methods
	 * @return the customizer of the tool calls of the modern connections
	 */
	static SyncModernMcpToolCallCustomizer annotatedHandlersToolCallCustomizer(ClientMcpSyncHandlersRegistry handlers) {
		return (connectionInfo, toolSpec, toolContext) -> {
			String connectionName = connectionInfo.name();
			McpSyncClient.ToolSpec callSpec = toolSpec;
			if (handlers.handlesModernFormElicitation(connectionName)) {
				callSpec = callSpec
					.formElicitation(request -> handlers.handleModernElicitation(connectionName, request, toolContext));
			}
			if (handlers.handlesModernUrlElicitation(connectionName)) {
				callSpec = callSpec
					.urlElicitation(request -> handlers.handleModernElicitation(connectionName, request, toolContext));
			}
			if (handlers.handlesModernProgress(connectionName)) {
				callSpec = callSpec
					.progress(progress -> handlers.handleModernProgress(connectionName, progress, toolContext));
			}
			return callSpec;
		};
	}

	private static void warnAboutUnsupportedHandlers(String connectionName, ClientMcpSyncHandlersRegistry handlers,
			boolean listening) {
		for (Class<? extends Annotation> annotationType : UNSUPPORTED_HANDLER_ANNOTATIONS) {
			if (handlers.hasHandlers(connectionName, annotationType)) {
				logger.warn("The @" + annotationType.getSimpleName() + " methods for the MCP connection '"
						+ connectionName + "' are not called: the connection uses the MODERN protocol, which has "
						+ "neither sampling nor logging.");
			}
		}
		for (Class<? extends Annotation> annotationType : LIST_CHANGED_HANDLER_ANNOTATIONS) {
			if (!handlers.hasHandlers(connectionName, annotationType)) {
				continue;
			}
			if (!listening) {
				logger.warn("The @" + annotationType.getSimpleName() + " methods for the MCP connection '"
						+ connectionName + "' are not called: the connection uses the MODERN protocol and does not "
						+ "set listen=true, so it does not hear about the changes of its server.");
			}
			else if (!handlers.handlesModernListChanged(connectionName, annotationType)) {
				logger.warn("The @" + annotationType.getSimpleName() + " methods for the MCP connection '"
						+ connectionName + "' are not called: the connection uses the MODERN protocol, which needs "
						+ "a synchronous method that returns void.");
			}
		}
		if (handlers.hasHandlers(connectionName, McpElicitation.class)
				&& !handlers.handlesModernFormElicitation(connectionName)
				&& !handlers.handlesModernUrlElicitation(connectionName)) {
			logger.warn("The @McpElicitation methods for the MCP connection '" + connectionName
					+ "' are not called: the connection uses the MODERN protocol, which needs a synchronous method "
					+ "that takes a form request or the modern ElicitUrlRequest.");
		}
		if (handlers.hasHandlers(connectionName, McpProgress.class)
				&& !handlers.handlesModernProgress(connectionName)) {
			logger.warn("The @McpProgress methods for the MCP connection '" + connectionName
					+ "' are not called: the connection uses the MODERN protocol, which needs a synchronous method "
					+ "that returns void.");
		}
	}

	/**
	 * The URI a modern connection sends its requests to: the {@code endpoint} of the
	 * connection ({@code /mcp} when not set) appended to its {@code url}. A path in the
	 * {@code url} is kept, so {@code http://host/api} and {@code /mcp} give
	 * {@code http://host/api/mcp}. The legacy WebClient transport composes the two in the
	 * same way; the legacy JDK {@code HttpClient} transport resolves the endpoint against
	 * the {@code url} instead, which drops such a path. A query or a fragment of the
	 * {@code url} stays at the end, after the endpoint. A warning is logged for a
	 * {@code url} that already ends with the endpoint, as it does when a connection of
	 * the legacy JDK transport that gave the whole address as its {@code url} is changed
	 * to the modern protocol.
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
		if (!endpoint.startsWith("/")) {
			endpoint = "/" + endpoint;
		}
		// The endpoint belongs to the path, before a query or a fragment of the url.
		int pathEnd = url.length();
		for (char delimiter : new char[] { '?', '#' }) {
			int index = url.indexOf(delimiter);
			if (index >= 0 && index < pathEnd) {
				pathEnd = index;
			}
		}
		String base = StringUtils.trimTrailingCharacter(url.substring(0, pathEnd), '/');
		String rest = url.substring(pathEnd);
		if (rest.startsWith("?") && endpoint.contains("?")) {
			rest = "&" + rest.substring(1);
		}
		if (base.endsWith(endpoint)) {
			logger.warn("The url of the MCP connection '" + connectionName + "' already ends with the endpoint '"
					+ endpoint + "', which is appended to it: its requests go to " + base + endpoint
					+ ". Remove the endpoint from the url if the server listens at " + base + ".");
		}
		return URI.create(base + endpoint + rest);
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
