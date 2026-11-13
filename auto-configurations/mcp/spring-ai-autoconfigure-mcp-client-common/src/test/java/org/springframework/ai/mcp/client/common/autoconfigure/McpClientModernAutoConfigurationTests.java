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
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.modern.client.transport.McpClientTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.modern.client.McpModernToolFilter;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link McpClientModernAutoConfiguration}. Modern clients do no I/O when
 * built, so no server is needed.
 *
 * @author Christian Tzolov
 */
class McpClientModernAutoConfigurationTests {

	private static final String CONNECTIONS = "spring.ai.mcp.client.streamable-http.connections.";

	private static final String MODERN_TICKETS_URL = CONNECTIONS + "tickets.url=http://localhost:8080";

	private static final String MODERN_TICKETS_PROTOCOL = CONNECTIONS + "tickets.protocol=MODERN";

	private static final String LEGACY_BILLING_URL = CONNECTIONS + "billing.url=http://localhost:8081";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpClientAutoConfiguration.class,
				McpToolCallbackAutoConfiguration.class, McpClientModernAutoConfiguration.class));

	@Test
	void onlyConnectionsWithTheModernProtocolBecomeModernConnections() {
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, LEGACY_BILLING_URL)
			.run(context -> {
				List<SyncModernMcpConnection> connections = connections(context.getBean("modernMcpSyncConnections"));

				assertThat(connections).hasSize(1);
				assertThat(connections.get(0).info().name()).isEqualTo("tickets");
				assertThat(connections.get(0).info().clientInfo().name()).isEqualTo("spring-ai-mcp-client");
			});
	}

	@Test
	void legacyAndModernToolsAreServedByOneProviderBean() {
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, LEGACY_BILLING_URL)
			.run(context -> {
				// A single provider bean keeps injection by the ToolCallbackProvider type
				// working, whatever the mix of legacy and modern connections.
				assertThat(context).hasSingleBean(ToolCallbackProvider.class);
				assertThat(context).hasSingleBean(SyncMcpToolCallbackProvider.class);
				assertThat(context.getBean(SyncMcpToolCallbackProvider.class)).extracting("modernToolCallbackProvider")
					.isInstanceOf(SyncModernMcpToolCallbackProvider.class);
			});
	}

	@Test
	void providerHasNoModernPartWithoutModernConnections() {
		this.contextRunner.withPropertyValues(LEGACY_BILLING_URL)
			.run(context -> assertThat(context.getBean(SyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider")
				.isNull());
	}

	@Test
	void modernConnectionWithTheAsyncClientTypeFailsTheStartup() {
		this.contextRunner
			.withPropertyValues("spring.ai.mcp.client.type=ASYNC", MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("tickets")
				.hasMessageContaining("spring.ai.mcp.client.type=SYNC"));

		this.contextRunner.withPropertyValues("spring.ai.mcp.client.type=ASYNC", LEGACY_BILLING_URL)
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void sharedScopeBuildsOneClientForTheConnection() {
		this.contextRunner.withUserConfiguration(RecordingCustomizerConfiguration.class)
			.withPropertyValues(CONNECTIONS + "tickets.client-scope=SHARED", MODERN_TICKETS_URL,
					MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				SyncModernMcpConnection connection = connections(context.getBean("modernMcpSyncConnections")).get(0);

				assertThat(connection.client()).isSameAs(connection.client());
				assertThat(context.getBean(RecordingCustomizer.class).customized).containsExactly("tickets");
			});
	}

	@Test
	void perRequestScopeIsTheDefaultAndBuildsAndCustomizesAClientForEachRequest() {
		this.contextRunner.withUserConfiguration(RecordingCustomizerConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				SyncModernMcpConnection connection = connections(context.getBean("modernMcpSyncConnections")).get(0);
				RecordingCustomizer customizer = context.getBean(RecordingCustomizer.class);

				// Nothing is built at startup.
				assertThat(customizer.customized).isEmpty();

				assertThat(connection.client()).isNotSameAs(connection.client());
				assertThat(customizer.customized).containsExactly("tickets", "tickets");
			});
	}

	@Test
	void clientScopeIsSetForEachConnection() {
		this.contextRunner
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL,
					CONNECTIONS + "catalog.url=http://localhost:8082", CONNECTIONS + "catalog.protocol=MODERN",
					CONNECTIONS + "catalog.client-scope=SHARED")
			.run(context -> {
				List<SyncModernMcpConnection> connections = connections(context.getBean("modernMcpSyncConnections"));
				SyncModernMcpConnection catalog = named(connections, "catalog");
				SyncModernMcpConnection tickets = named(connections, "tickets");

				assertThat(catalog.client()).isSameAs(catalog.client());
				assertThat(tickets.client()).isNotSameAs(tickets.client());
			});
	}

	@Test
	void unreachableCoolDownIsThirtySecondsUnlessConfigured() {
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(SyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider")
				.extracting("unreachableCoolDownNanos")
				.isEqualTo(Duration.ofSeconds(30).toNanos()));

		this.contextRunner
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL,
					"spring.ai.mcp.client.modern.unreachable-cool-down=5s")
			.run(context -> assertThat(context.getBean(SyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider")
				.extracting("unreachableCoolDownNanos")
				.isEqualTo(Duration.ofSeconds(5).toNanos()));
	}

	@Test
	void endpointIsAppendedToTheUrlAndAPathInTheUrlIsKept() {
		assertThat(endpointUri("http://host", null)).isEqualTo(URI.create("http://host/mcp"));
		assertThat(endpointUri("http://host/", null)).isEqualTo(URI.create("http://host/mcp"));
		assertThat(endpointUri("http://host", "/custom")).isEqualTo(URI.create("http://host/custom"));
		assertThat(endpointUri("http://host", "custom")).isEqualTo(URI.create("http://host/custom"));

		// The path of the url is kept, with or without slashes between the two parts.
		assertThat(endpointUri("http://host/api", null)).isEqualTo(URI.create("http://host/api/mcp"));
		assertThat(endpointUri("http://host/api", "/mcp")).isEqualTo(URI.create("http://host/api/mcp"));
		assertThat(endpointUri("http://host/api/", "/mcp")).isEqualTo(URI.create("http://host/api/mcp"));
		assertThat(endpointUri("http://host/api/", "mcp")).isEqualTo(URI.create("http://host/api/mcp"));
		assertThat(endpointUri("http://host/api", "mcp")).isEqualTo(URI.create("http://host/api/mcp"));

		assertThatThrownBy(() -> endpointUri(null, "/mcp")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("tickets");
	}

	@Test
	void transportCustomizerSetsTheConnectTimeoutOfAConnection() {
		this.contextRunner.withUserConfiguration(ConnectTimeoutConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				List<NamedModernClientMcpTransport> transports = transports(
						context.getBean("modernStreamableHttpTransports"));

				assertThat(transports).hasSize(1);
				assertThat(transports.get(0).transport()).extracting("httpClient")
					.isInstanceOfSatisfying(HttpClient.class,
							httpClient -> assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(2)));
			});
	}

	@Test
	void transportDefinedByTheApplicationBecomesAConnectionWithAClientForEachRequest() {
		this.contextRunner.withUserConfiguration(ApplicationTransportConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				List<SyncModernMcpConnection> connections = connections(context.getBean("modernMcpSyncConnections"));

				assertThat(connections).extracting(connection -> connection.info().name())
					.containsExactlyInAnyOrder("tickets", "files");

				SyncModernMcpConnection files = named(connections, "files");
				assertThat(files.client()).isNotSameAs(files.client());
			});
	}

	@Test
	void legacyToolFilterAloneFailsTheStartupWhenThereAreModernConnections() {
		this.contextRunner.withUserConfiguration(LegacyToolFilterConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("McpModernToolFilter")
				.hasMessageContaining("tickets"));
	}

	@Test
	void legacyToolFilterIsEnoughWithoutModernConnections() {
		this.contextRunner.withUserConfiguration(LegacyToolFilterConfiguration.class)
			.withPropertyValues(LEGACY_BILLING_URL)
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void legacyAndModernToolFiltersTogetherAreAccepted() {
		this.contextRunner
			.withUserConfiguration(LegacyToolFilterConfiguration.class, ModernToolFilterConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(SyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider")
				.extracting("toolFilter")
				.isSameAs(context.getBean(McpModernToolFilter.class)));
	}

	private static URI endpointUri(String url, String endpoint) {
		return McpClientModernAutoConfiguration.endpointUri("tickets", new ConnectionParameters(url, endpoint));
	}

	@SuppressWarnings("unchecked")
	private static List<NamedModernClientMcpTransport> transports(Object bean) {
		return (List<NamedModernClientMcpTransport>) bean;
	}

	@SuppressWarnings("unchecked")
	private static List<SyncModernMcpConnection> connections(Object bean) {
		return (List<SyncModernMcpConnection>) bean;
	}

	private static SyncModernMcpConnection named(List<SyncModernMcpConnection> connections, String name) {
		return connections.stream()
			.filter(connection -> connection.info().name().equals(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no connection named " + name));
	}

	static class RecordingCustomizer implements McpClientCustomizer<McpClient.SyncBuilder> {

		final List<String> customized = new ArrayList<>();

		@Override
		public void customize(String name, McpClient.SyncBuilder builder) {
			this.customized.add(name);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class LegacyToolFilterConfiguration {

		@Bean
		McpToolFilter legacyToolFilter() {
			return (connectionInfo, tool) -> true;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ModernToolFilterConfiguration {

		@Bean
		McpModernToolFilter modernToolFilter() {
			return (connectionInfo, tool) -> !tool.name().startsWith("admin");
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ConnectTimeoutConfiguration {

		@Bean
		McpClientCustomizer<HttpClientMcpTransport.Builder> connectTimeoutCustomizer() {
			return (name, builder) -> builder.connectTimeout(Duration.ofSeconds(2));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ApplicationTransportConfiguration {

		@Bean
		List<NamedModernClientMcpTransport> applicationTransports() {
			// A modern client does no I/O when built, so the transport is never used.
			McpClientTransport transport = request -> Flux.empty();
			return List.of(new NamedModernClientMcpTransport("files", transport));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class RecordingCustomizerConfiguration {

		@Bean
		RecordingCustomizer recordingCustomizer() {
			return new RecordingCustomizer();
		}

	}

}
