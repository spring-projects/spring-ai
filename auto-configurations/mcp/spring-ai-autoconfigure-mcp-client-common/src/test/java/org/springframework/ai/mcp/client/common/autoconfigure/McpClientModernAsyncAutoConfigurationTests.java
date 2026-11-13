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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.modern.client.McpAsyncClient;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpChangeListener;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpChangeSubscriber;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpClientScope;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpToolCallCustomizer;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpToolCallbackProvider;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpTransportContextProvider;
import org.springframework.ai.mcp.modern.client.McpModernConnectionInfo;
import org.springframework.ai.mcp.modern.client.McpModernToolFilter;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeSubscriber;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the modern connections of {@link McpClientModernAutoConfiguration} and
 * {@link McpToolCallbackAutoConfiguration} with the asynchronous client type.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class McpClientModernAsyncAutoConfigurationTests {

	private static final String CONNECTIONS = "spring.ai.mcp.client.streamable-http.connections.";

	private static final String MODERN_TICKETS_URL = CONNECTIONS + "tickets.url=http://localhost:8080";

	private static final String MODERN_TICKETS_PROTOCOL = CONNECTIONS + "tickets.protocol=MODERN";

	private static final String LEGACY_BILLING_URL = CONNECTIONS + "billing.url=http://localhost:8081";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpClientAutoConfiguration.class,
				McpToolCallbackAutoConfiguration.class, McpClientModernAutoConfiguration.class))
		.withPropertyValues("spring.ai.mcp.client.type=ASYNC");

	@Test
	void asyncClientTypeServesModernConnectionsThroughTheAsyncProviderBean() {
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(connections(context.getBean("modernMcpAsyncConnections")))
				.extracting(connection -> connection.info().name())
				.containsExactly("tickets");
			// The synchronous connections belong to the synchronous client type.
			assertThat(context).doesNotHaveBean("modernMcpSyncConnections");

			assertThat(context).hasSingleBean(ToolCallbackProvider.class);
			assertThat(context.getBean(AsyncMcpToolCallbackProvider.class)).extracting("modernToolCallbackProvider")
				.isInstanceOf(AsyncModernMcpToolCallbackProvider.class);
		});
	}

	@Test
	void providerHasNoModernPartWithoutModernConnections() {
		this.contextRunner.withPropertyValues(CONNECTIONS + "billing.url=http://localhost:8081").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AsyncMcpToolCallbackProvider.class)).extracting("modernToolCallbackProvider")
				.isNull();
		});
	}

	@Test
	void perRequestScopeIsTheDefaultAndBuildsAndCustomizesAClientForEachRequest() {
		this.contextRunner.withUserConfiguration(RecordingCustomizerConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				AsyncModernMcpConnection connection = connections(context.getBean("modernMcpAsyncConnections")).get(0);
				RecordingCustomizer customizer = context.getBean(RecordingCustomizer.class);

				// Nothing is built at startup.
				assertThat(customizer.customized).isEmpty();

				assertThat(connection.client()).isNotSameAs(connection.client());
				assertThat(connection.clientPerRequest()).isTrue();
				assertThat(customizer.customized).containsExactly("tickets", "tickets");
			});
	}

	@Test
	void sharedScopeBuildsOneClientForTheConnection() {
		this.contextRunner.withUserConfiguration(RecordingCustomizerConfiguration.class)
			.withPropertyValues(CONNECTIONS + "tickets.client-scope=SHARED", MODERN_TICKETS_URL,
					MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				AsyncModernMcpConnection connection = connections(context.getBean("modernMcpAsyncConnections")).get(0);

				assertThat(connection.client()).isSameAs(connection.client());
				assertThat(context.getBean(RecordingCustomizer.class).customized).containsExactly("tickets");
			});
	}

	@Test
	void customScopeLetsTheBeanOfTheApplicationSelectTheClients() {
		this.contextRunner.withUserConfiguration(ClientScopeConfiguration.class)
			.withPropertyValues(CONNECTIONS + "tickets.client-scope=CUSTOM", MODERN_TICKETS_URL,
					MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				AsyncModernMcpConnection connection = connections(context.getBean("modernMcpAsyncConnections")).get(0);

				assertThat(connection.client()).isSameAs(connection.client());
				assertThat(context.getBean(KeepingClientScope.class).asked).containsExactly("tickets", "tickets");
				// The client of the application does not come from the scope.
				assertThat(connection.applicationClient()).isNotSameAs(connection.client());
			});
	}

	@Test
	void customScopeWithoutAScopeBeanFailsTheStartup() {
		this.contextRunner
			.withPropertyValues(CONNECTIONS + "tickets.client-scope=CUSTOM", MODERN_TICKETS_URL,
					MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("The MCP connection 'tickets' sets client-scope=CUSTOM")
				.hasMessageContaining("no AsyncModernMcpClientScope bean is defined"));
	}

	@Test
	void transportContextProviderBeanSuppliesTheCallerOfARequest() {
		this.contextRunner.withUserConfiguration(CallerConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				AsyncModernMcpConnection connection = connections(context.getBean("modernMcpAsyncConnections")).get(0);

				assertThat(connection.transportContext().get("user")).isEqualTo("alice of tickets");
			});

		// Without the bean a request has no caller.
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(
					connections(context.getBean("modernMcpAsyncConnections")).get(0).transportContext())
				.isSameAs(McpTransportContext.EMPTY));
	}

	@Test
	void sharedClientWithACallerForEachRequestIsReported(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(CallerConfiguration.class)
			.withPropertyValues(CONNECTIONS + "tickets.client-scope=SHARED", MODERN_TICKETS_URL,
					MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				// The shared client would keep the private listing of the first caller.
				assertThat(output).contains("The MCP connection 'tickets' sets client-scope=SHARED, and an "
						+ "AsyncModernMcpTransportContextProvider bean supplies the caller of each request");
			});

		// A client for each request keeps nothing, so there is nothing to report.
		this.contextRunner.withUserConfiguration(CallerConfiguration.class)
			.withPropertyValues(CONNECTIONS + "billing.url=http://localhost:8082",
					CONNECTIONS + "billing.protocol=MODERN")
			.run(context -> assertThat(output).doesNotContain("The MCP connection 'billing' sets client-scope=SHARED"));
	}

	@Test
	void toolCallCustomizerBeansAreSetOnTheModernProvider() {
		this.contextRunner.withUserConfiguration(ToolCallCustomizerConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context.getBean(AsyncMcpToolCallbackProvider.class))
				.extracting("modernToolCallbackProvider.toolCallCustomizer")
				.isSameAs(context.getBean(AsyncModernMcpToolCallCustomizer.class)));
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

		this.contextRunner
			.withUserConfiguration(LegacyToolFilterConfiguration.class, ModernToolFilterConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void reactiveHandlerMethodsServeTheModernConnectionAndTheOthersAreReported(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				// The annotated handlers are set for each tool call.
				assertThat(context.getBean(AsyncMcpToolCallbackProvider.class))
					.extracting("modernToolCallbackProvider.toolCallCustomizer")
					.isNotNull();
				assertThat(output).contains("The @McpSampling methods for the MCP connection 'tickets' are not called")
					.contains("The @McpToolListChanged methods for the MCP connection 'tickets' are not called")
					.doesNotContain("The @McpElicitation methods")
					.doesNotContain("The @McpProgress methods");
			});
	}

	@Test
	void handlerMethodsThatAnAsyncModernConnectionCannotCallAreReported(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(UnusableHandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(output)
					.contains("The @McpElicitation methods for the MCP connection 'tickets' are not called")
					.contains("needs a method that returns a Mono and takes a form request");
			});
	}

	@Test
	void listenStreamIsKeptOnlyForTheConnectionsThatAskForIt(CapturedOutput output) {
		// No connection listens unless it says so.
		this.contextRunner.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL).run(context -> {
			assertThat(context.getBean(AsyncModernMcpChangeSubscriber.class).connectionNames()).isEmpty();
			// The subscriber of the synchronous client type is not created.
			assertThat(context).doesNotHaveBean(SyncModernMcpChangeSubscriber.class);
		});

		// Nothing listens on these ports: a server that cannot be reached must not fail
		// the startup.
		this.contextRunner
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true",
					CONNECTIONS + "catalog.url=http://localhost:8082", CONNECTIONS + "catalog.protocol=MODERN",
					// A legacy connection has no listen stream.
					LEGACY_BILLING_URL, CONNECTIONS + "billing.listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				AsyncModernMcpChangeSubscriber subscriber = context.getBean(AsyncModernMcpChangeSubscriber.class);

				assertThat(subscriber.connectionNames()).containsExactly("tickets");
				assertThat(subscriber.isRunning()).isTrue();
				// The provider hears about the changes, to drop a kept tool listing.
				assertThat(subscriber).extracting("streams.listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.hasSize(1)
					.first()
					.isInstanceOf(AsyncModernMcpToolCallbackProvider.class);
				assertThat(output).contains("The MCP connection 'billing' sets listen=true but does not use the MODERN")
					.doesNotContain("not supported");
			});
	}

	@Test
	void reactiveListChangedMethodsAreCalledForAConnectionThatListens(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				// A connection that does not listen is told so, see the test above.
				assertThat(output).doesNotContain("The @McpToolListChanged methods");
				// The methods hear about a change after the provider, which has dropped
				// its kept listing by then.
				assertThat(context.getBean(AsyncModernMcpChangeSubscriber.class)).extracting("streams.listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.satisfiesExactly(
							listener -> assertThat(listener).isInstanceOf(AsyncModernMcpToolCallbackProvider.class),
							listener -> assertThat(listener).isInstanceOf(AsyncModernListChangedHandlers.class));
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
				AsyncModernMcpChangeSubscriber subscriber = context.getBean(AsyncModernMcpChangeSubscriber.class);
				// The list-changed methods are registered although nothing asks for the
				// bean that registers them.
				assertThat(subscriber).extracting("streams.listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.singleElement()
					.isInstanceOf(AsyncModernListChangedHandlers.class);

				// The provider is created when it is first asked for. It still hears
				// about a change before the methods.
				context.getBean(AsyncMcpToolCallbackProvider.class);
				assertThat(subscriber).extracting("streams.listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.satisfiesExactly(
							listener -> assertThat(listener).isInstanceOf(AsyncModernMcpToolCallbackProvider.class),
							listener -> assertThat(listener).isInstanceOf(AsyncModernListChangedHandlers.class));
			});
	}

	@Test
	void synchronousListChangedMethodsOfAListeningConnectionAreReportedAtStartup(CapturedOutput output) {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(SynchronousListChangedHandlersConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(output)
					.contains("The @McpPromptListChanged methods for the MCP connection 'tickets' are not called: "
							+ "the connection uses the MODERN protocol with the ASYNC client type, which needs a "
							+ "method that returns a Mono<Void>");
				// Nothing is listened for on behalf of a method that is not called.
				assertThat(context.getBean(AsyncModernMcpChangeSubscriber.class)).extracting("streams.listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.doesNotHaveAnyElementsOfTypes(AsyncModernListChangedHandlers.class);
			});
	}

	@Test
	void changeListenerBeansHearAboutTheChangesNextToTheProvider() {
		this.contextRunner.withUserConfiguration(ChangeListenerConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL, CONNECTIONS + "tickets.listen=true")
			.run(context -> assertThat(context.getBean(AsyncModernMcpChangeSubscriber.class))
				.extracting("streams.listeners")
				.asInstanceOf(InstanceOfAssertFactories.LIST)
				.hasSize(2)
				.contains(context.getBean(AsyncModernMcpChangeListener.class))
				.hasAtLeastOneElementOfType(AsyncModernMcpToolCallbackProvider.class));
	}

	@Test
	void subscriberOfTheApplicationReplacesTheAutoConfiguredOne() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(McpClientAnnotationScannerAutoConfiguration.class))
			.withUserConfiguration(HandlersConfiguration.class, ApplicationSubscriberConfiguration.class)
			.withPropertyValues(MODERN_TICKETS_URL, MODERN_TICKETS_PROTOCOL)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(AsyncModernMcpChangeSubscriber.class)).extracting("streams.listeners")
					.asInstanceOf(InstanceOfAssertFactories.LIST)
					.hasAtLeastOneElementOfType(AsyncModernMcpToolCallbackProvider.class)
					.hasAtLeastOneElementOfType(AsyncModernListChangedHandlers.class);
			});
	}

	@SuppressWarnings("unchecked")
	private static List<AsyncModernMcpConnection> connections(Object bean) {
		return (List<AsyncModernMcpConnection>) bean;
	}

	static class RecordingCustomizer implements McpClientCustomizer<McpClient.AsyncBuilder> {

		final List<String> customized = new ArrayList<>();

		@Override
		public void customize(String name, McpClient.AsyncBuilder builder) {
			this.customized.add(name);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class RecordingCustomizerConfiguration {

		@Bean
		RecordingCustomizer recordingCustomizer() {
			return new RecordingCustomizer();
		}

	}

	/** Keeps the first client it is given for each connection. */
	static class KeepingClientScope implements AsyncModernMcpClientScope {

		final List<String> asked = new ArrayList<>();

		private final Map<String, McpAsyncClient> clients = new HashMap<>();

		@Override
		public McpAsyncClient client(McpModernConnectionInfo connectionInfo, Supplier<McpAsyncClient> clientFactory) {
			this.asked.add(connectionInfo.name());
			return this.clients.computeIfAbsent(connectionInfo.name(), name -> clientFactory.get());
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ClientScopeConfiguration {

		@Bean
		KeepingClientScope clientScope() {
			return new KeepingClientScope();
		}

	}

	static class Handlers {

		@McpElicitation(clients = "tickets")
		Mono<ElicitResult> elicitation(ElicitFormRequest request) {
			return Mono.just(ElicitResult.decline());
		}

		@McpProgress(clients = "tickets")
		Mono<Void> progress(ProgressParams progress) {
			return Mono.empty();
		}

		@McpSampling(clients = "tickets")
		Mono<McpSchema.CreateMessageResult> sampling(McpSchema.CreateMessageRequest request) {
			return Mono.error(new UnsupportedOperationException());
		}

		@McpToolListChanged(clients = "tickets")
		Mono<Void> toolListChanged(List<McpSchema.Tool> tools) {
			return Mono.empty();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class HandlersConfiguration {

		@Bean
		Handlers handlers() {
			return new Handlers();
		}

	}

	/** A handler method that names the modern connection but cannot serve it. */
	static class UnusableHandlers {

		// A legacy URL request needs an elicitationId, which the modern protocol lacks.
		@McpElicitation(clients = "tickets")
		Mono<McpSchema.ElicitResult> legacyUrl(McpSchema.ElicitUrlRequest request) {
			return Mono.error(new UnsupportedOperationException());
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class UnusableHandlersConfiguration {

		@Bean
		UnusableHandlers unusableHandlers() {
			return new UnusableHandlers();
		}

	}

	/** A synchronous method, which an asynchronous modern connection does not call. */
	static class SynchronousListChangedHandlers {

		@McpPromptListChanged(clients = "tickets")
		void promptListChanged(List<McpSchema.Prompt> prompts) {
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class SynchronousListChangedHandlersConfiguration {

		@Bean
		SynchronousListChangedHandlers synchronousListChangedHandlers() {
			return new SynchronousListChangedHandlers();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ChangeListenerConfiguration {

		@Bean
		AsyncModernMcpChangeListener changeListener() {
			return (connectionInfo, event) -> Mono.empty();
		}

	}

	/** A subscriber of the application, for the connection of the test. */
	@Configuration(proxyBeanMethods = false)
	static class ApplicationSubscriberConfiguration {

		@Bean
		AsyncModernMcpChangeSubscriber applicationSubscriber(List<AsyncModernMcpConnection> connections) {
			AsyncModernMcpChangeSubscriber.Builder subscriber = AsyncModernMcpChangeSubscriber.builder();
			connections.forEach(subscriber::connection);
			return subscriber.build();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CallerConfiguration {

		@Bean
		AsyncModernMcpTransportContextProvider callerProvider() {
			return connectionInfo -> McpTransportContext.create(Map.of("user", "alice of " + connectionInfo.name()));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ToolCallCustomizerConfiguration {

		@Bean
		AsyncModernMcpToolCallCustomizer toolCallCustomizer() {
			return (connectionInfo, toolSpec, toolContext) -> toolSpec;
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
			return McpModernToolFilter.acceptAll();
		}

	}

}
