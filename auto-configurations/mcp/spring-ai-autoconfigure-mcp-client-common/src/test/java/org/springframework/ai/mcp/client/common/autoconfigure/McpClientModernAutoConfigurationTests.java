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

import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.transport.HttpClientMcpTransport;
import io.modelcontextprotocol.modern.client.transport.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpLogging;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.modern.client.McpModernToolFilter;
import org.springframework.ai.mcp.modern.client.McpModernToolNamePrefixGenerator;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeListener;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeSubscriber;
import org.springframework.ai.mcp.modern.client.SyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallCustomizer;
import org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link McpClientModernAutoConfiguration}. Modern clients do no I/O when
 * built, so no server is needed.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
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
	void queryAndFragmentOfTheUrlStayBehindTheEndpoint() {
		assertThat(endpointUri("http://host/api?tenant=a", null)).isEqualTo(URI.create("http://host/api/mcp?tenant=a"));
		assertThat(endpointUri("http://host/?tenant=a", "custom")).isEqualTo(URI.create("http://host/custom?tenant=a"));
		assertThat(endpointUri("http://host?tenant=a#top", null)).isEqualTo(URI.create("http://host/mcp?tenant=a#top"));
		// An endpoint with a query of its own comes first.
		assertThat(endpointUri("http://host/api?tenant=a", "/mcp?v=2"))
			.isEqualTo(URI.create("http://host/api/mcp?v=2&tenant=a"));
	}

	@Test
	void urlThatAlreadyEndsWithTheEndpointIsReported(CapturedOutput output) {
		// The legacy JDK transport resolves the endpoint against the url, so there the
		// whole address works as the url. Here the endpoint is appended to it.
		assertThat(endpointUri("https://host/mcp", null)).isEqualTo(URI.create("https://host/mcp/mcp"));
		assertThat(output).contains("The url of the MCP connection 'tickets' already ends with the endpoint '/mcp'")
			.contains("its requests go to https://host/mcp/mcp");

		assertThat(endpointUri("https://host/api", null)).isEqualTo(URI.create("https://host/api/mcp"));
		assertThat(output).doesNotContain("its requests go to https://host/api/mcp");
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
	void handlerAnnotationsThatAModernConnectionDoesNotSupportAreReportedAtStartup(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(output).contains("The @McpSampling methods for the MCP connection 'tickets' are not called")
					.contains("The @McpLogging methods for the MCP connection 'tickets' are not called")
					.contains("The @McpToolListChanged methods for the MCP connection 'tickets' are not called")
					.doesNotContain("The @McpElicitation methods")
					.doesNotContain("The @McpProgress methods")
					// The handlers of another connection are no concern of this one.
					.doesNotContain("for the MCP connection 'billing'");
			});
	}

	@Test
	void listChangedMethodsAreCalledForAConnectionThatListens(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				// A connection that does not listen is told so, see the test above.
				assertThat(output).doesNotContain("The @McpToolListChanged methods");
				// The methods hear about a change after the provider, which has dropped
				// its kept listing by then.
				assertThat(context.getBean(SyncModernMcpChangeSubscriber.class)).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.hasSize(2)
					.satisfiesExactly(
							listener -> assertThat(listener).isInstanceOf(SyncModernMcpToolCallbackProvider.class),
							listener -> assertThat(listener).isInstanceOf(ModernListChangedHandlers.class));
			});
	}

	@Test
	void lazyInitializationKeepsTheListenersOfTheListenStreams() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class)
			.withInitializer(
					context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				SyncModernMcpChangeSubscriber subscriber = context.getBean(SyncModernMcpChangeSubscriber.class);
				// The list-changed methods are registered although nothing asks for the
				// bean that registers them.
				assertThat(subscriber).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.singleElement()
					.isInstanceOf(ModernListChangedHandlers.class);

				// The provider is created when it is first asked for. It still hears
				// about a change before the methods.
				context.getBean(SyncMcpToolCallbackProvider.class);
				assertThat(subscriber).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.satisfiesExactly(
							listener -> assertThat(listener).isInstanceOf(SyncModernMcpToolCallbackProvider.class),
							listener -> assertThat(listener).isInstanceOf(ModernListChangedHandlers.class));
			});
	}

	@Test
	void listChangedMethodsAloneKeepAListenStreamOpen(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true",
					"spring.ai.mcp.client.toolcallback.enabled=false")
			.run(context -> {
				assertThat(context.getBean(SyncModernMcpChangeSubscriber.class)).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.singleElement()
					.isInstanceOf(ModernListChangedHandlers.class);
				assertThat(output).doesNotContain("No listen stream is opened");
			});
	}

	@Test
	void noListenStreamIsOpenedIfNeitherAProviderNorAListChangedMethodListens(CapturedOutput output) {
		// The registry of the annotated methods is there, but none of its methods wants
		// to hear about the changes of the connection that listens.
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(UnusableHandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true",
					"spring.ai.mcp.client.toolcallback.enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(SyncModernMcpChangeSubscriber.class)).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.isEmpty();
				assertThat(output).contains("No listen stream is opened to the MCP connections [tickets]");
			});
	}

	@Test
	void listChangedMethodsAreCalledThroughASubscriberOfTheApplication() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class, ApplicationSubscriberConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(SyncModernMcpChangeSubscriber.class)).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.hasAtLeastOneElementOfType(ModernListChangedHandlers.class);
			});
	}

	@Test
	void reactiveListChangedMethodsOfAListeningConnectionAreReportedAtStartup(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(ReactiveListChangedHandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(output)
					.contains("The @McpPromptListChanged methods for the MCP connection 'tickets' are not called: "
							+ "the connection uses the MODERN protocol, which needs a synchronous method");
				// Nothing is listened for on behalf of a method that is not called.
				assertThat(context.getBean(SyncModernMcpChangeSubscriber.class)).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.doesNotHaveAnyElementsOfTypes(ModernListChangedHandlers.class);
			});
	}

	@Test
	void handlerMethodsThatCannotServeAModernConnectionAreReportedAtStartup(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(UnusableHandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(output)
					.contains("The @McpElicitation methods for the MCP connection 'tickets' are not called")
					.contains("The @McpProgress methods for the MCP connection 'tickets' are not called");
			});
	}

	@Test
	void toolCallCustomizerBeansAreSetOnTheModernProvider() {
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(SyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider")
				.extracting("toolCallCustomizer")
				.isNull());

		this.contextRunner.withUserConfiguration(ToolCallCustomizerConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(SyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider")
				.extracting("toolCallCustomizer")
				.isSameAs(context.getBean(SyncModernMcpToolCallCustomizer.class)));
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
	void listenStreamIsKeptOnlyForTheConnectionsThatAskForIt(CapturedOutput output) {
		// No connection listens unless it says so.
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(SyncModernMcpChangeSubscriber.class).connectionNames())
				.isEmpty());

		// Nothing listens on these ports: a server that cannot be reached must not fail
		// the startup.
		this.contextRunner
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true",
					CONNECTIONS + "catalog.url=http://localhost:8082", CONNECTIONS + "catalog.protocol=MODERN",
					// A legacy connection has no listen stream.
					LEGACY_BILLING_URL, CONNECTIONS + "billing.listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				SyncModernMcpChangeSubscriber subscriber = context.getBean(SyncModernMcpChangeSubscriber.class);

				assertThat(subscriber.connectionNames()).containsExactly("tickets");
				assertThat(subscriber.isRunning()).isTrue();
				// The provider hears about the changes, to drop a kept tool listing.
				assertThat(subscriber).extracting("listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.hasSize(1)
					.first()
					.isInstanceOf(SyncModernMcpToolCallbackProvider.class);
				assertThat(output)
					.contains("The MCP connection 'billing' sets listen=true but does not use the MODERN");
			});
	}

	@Test
	void changeListenerBeansHearAboutTheChangesNextToTheProvider() {
		this.contextRunner.withUserConfiguration(ChangeListenerConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true")
			.run(context -> assertThat(context.getBean(SyncModernMcpChangeSubscriber.class)).extracting("listeners")
				.asInstanceOf(InstanceOfAssertFactories.LIST)
				.hasSize(2)
				.contains(context.getBean(SyncModernMcpChangeListener.class))
				.hasAtLeastOneElementOfType(SyncModernMcpToolCallbackProvider.class));
	}

	@Test
	void pausesBeforeAListenStreamIsOpenedAgainGrowUpToTheUnreachableCoolDown() {
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(SyncModernMcpChangeSubscriber.class))
				.extracting("maxReopenDelay")
				.isEqualTo(Duration.ofSeconds(30)));

		this.contextRunner
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL,
					"spring.ai.mcp.client.modern.unreachable-cool-down=0s")
			.run(context -> assertThat(context.getBean(SyncModernMcpChangeSubscriber.class))
				.extracting("maxReopenDelay")
				.isEqualTo(Duration.ofSeconds(1)));
	}

	@Test
	void severalModernToolFiltersFailTheStartupUnlessOneIsPrimary() {
		// Falling back to the default filter would expose all tools.
		this.contextRunner.withUserConfiguration(TwoModernToolFiltersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.isInstanceOf(NoUniqueBeanDefinitionException.class)
				.hasMessageContaining("McpModernToolFilter"));

		this.contextRunner.withUserConfiguration(PrimaryModernToolFilterConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(SyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider")
				.extracting("toolFilter")
				.isSameAs(context.getBean("primaryToolFilter")));
	}

	@Test
	void severalModernToolNameGeneratorsFailTheStartup() {
		this.contextRunner.withUserConfiguration(TwoModernToolNameGeneratorsConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context).getFailure()
				.rootCause()
				.isInstanceOf(NoUniqueBeanDefinitionException.class)
				.hasMessageContaining("McpModernToolNamePrefixGenerator"));
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

	static class Handlers {

		@McpElicitation(clients = "tickets")
		ElicitResult elicitation(ElicitFormRequest request) {
			return ElicitResult.decline();
		}

		@McpProgress(clients = "tickets")
		void progress(ProgressParams progress) {
		}

		@McpSampling(clients = { "tickets", "billing" })
		McpSchema.CreateMessageResult sampling(McpSchema.CreateMessageRequest request) {
			throw new UnsupportedOperationException();
		}

		@McpLogging(clients = "tickets")
		void logging(McpSchema.LoggingMessageNotification notification) {
		}

		@McpToolListChanged(clients = "tickets")
		void toolListChanged(List<McpSchema.Tool> tools) {
		}

	}

	/** Handler methods that name the modern connection but cannot serve it. */
	static class UnusableHandlers {

		// A legacy URL request needs an elicitationId, which the modern protocol lacks.
		@McpElicitation(clients = "tickets")
		McpSchema.ElicitResult legacyUrl(McpSchema.ElicitUrlRequest request) {
			throw new UnsupportedOperationException();
		}

		// The modern client calls synchronous handlers only.
		@McpProgress(clients = "tickets")
		Mono<Void> reactiveProgress(McpSchema.ProgressNotification notification) {
			return Mono.empty();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class UnusableHandlersConfiguration {

		@Bean
		UnusableHandlers unusableHandlers() {
			return new UnusableHandlers();
		}

	}

	/** The application chooses the listening connections with a subscriber of its own. */
	@Configuration(proxyBeanMethods = false)
	static class ApplicationSubscriberConfiguration {

		@Bean
		SyncModernMcpChangeSubscriber applicationSubscriber(List<SyncModernMcpConnection> connections) {
			SyncModernMcpChangeSubscriber.Builder subscriber = SyncModernMcpChangeSubscriber.builder();
			connections.forEach(subscriber::connection);
			return subscriber.build();
		}

	}

	static class ReactiveListChangedHandlers {

		// The modern client calls synchronous handlers only.
		@McpPromptListChanged(clients = "tickets")
		Mono<Void> reactivePrompts(List<McpSchema.Prompt> prompts) {
			return Mono.empty();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ReactiveListChangedHandlersConfiguration {

		@Bean
		ReactiveListChangedHandlers reactiveListChangedHandlers() {
			return new ReactiveListChangedHandlers();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ChangeListenerConfiguration {

		@Bean
		SyncModernMcpChangeListener changeListener() {
			return (connectionInfo, event) -> {
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ToolCallCustomizerConfiguration {

		@Bean
		SyncModernMcpToolCallCustomizer toolCallCustomizer() {
			return (connectionInfo, toolSpec, toolContext) -> toolSpec;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class HandlersConfiguration {

		@Bean
		Handlers handlers() {
			return new Handlers();
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
	static class TwoModernToolFiltersConfiguration {

		@Bean
		McpModernToolFilter noAdminTools() {
			return (connectionInfo, tool) -> !tool.name().startsWith("admin");
		}

		@Bean
		McpModernToolFilter noDebugTools() {
			return (connectionInfo, tool) -> !tool.name().startsWith("debug");
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class PrimaryModernToolFilterConfiguration {

		@Bean
		@Primary
		McpModernToolFilter primaryToolFilter() {
			return (connectionInfo, tool) -> !tool.name().startsWith("admin");
		}

		@Bean
		McpModernToolFilter otherToolFilter() {
			return (connectionInfo, tool) -> !tool.name().startsWith("debug");
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class TwoModernToolNameGeneratorsConfiguration {

		@Bean
		McpModernToolNamePrefixGenerator noPrefix() {
			return McpModernToolNamePrefixGenerator.noPrefix();
		}

		@Bean
		McpModernToolNamePrefixGenerator connectionPrefix() {
			return McpModernToolNamePrefixGenerator.connectionPrefix();
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
