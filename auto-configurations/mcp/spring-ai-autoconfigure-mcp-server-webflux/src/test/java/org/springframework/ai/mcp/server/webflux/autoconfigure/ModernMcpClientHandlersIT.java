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

package org.springframework.ai.mcp.server.webflux.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.modern.McpSchema.InputRequiredResult;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.client.McpMissingClientCapabilityException;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import io.modelcontextprotocol.modern.server.McpSyncNotifier;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.spec.McpSchema;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpClientCapability;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpModernSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientModernAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.mcp.modern.client.McpModernConnectionInfo;
import org.springframework.ai.mcp.modern.client.SyncModernMcpChangeListener;
import org.springframework.ai.mcp.modern.client.SyncModernMcpToolCallCustomizer;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ModernServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Lets {@code @McpElicitation} and {@code @McpProgress} methods of the auto-configured
 * MCP client serve a legacy and a modern server at the same time, over HTTP. One handler
 * method answers both servers, whether it is written against the schema types of the
 * legacy protocol ({@code McpSchema} in this class) or against those of the modern
 * revision.
 *
 * @author Christian Tzolov
 */
class ModernMcpClientHandlersIT {

	private static final String CONNECTIONS = "spring.ai.mcp.client.streamable-http.connections.";

	private final ApplicationContextRunner modernServerContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=MODERN")
		.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class,
				McpServerModernAutoConfiguration.class, McpServerModernWebFluxAutoConfiguration.class,
				McpServerAnnotationScannerAutoConfiguration.class,
				ModernServerSpecificationFactoryAutoConfiguration.class))
		.withUserConfiguration(ModernServerConfiguration.class);

	private final ApplicationContextRunner legacyServerContextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE", "spring.ai.mcp.server.type=SYNC")
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				McpServerJsonMapperAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class,
				McpServerStreamableHttpWebFluxAutoConfiguration.class,
				McpServerAnnotationScannerAutoConfiguration.class,
				McpServerSpecificationFactoryAutoConfiguration.class))
		.withUserConfiguration(LegacyServerConfiguration.class);

	// The client application has the legacy and the modern auto-configurations and the
	// annotation scanner, as an application on the MCP client starter does.
	/** The connections to the HTTP servers of the tests. */
	private static final ChannelGroup OPEN_CONNECTIONS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

	private volatile ApplicationContext modernServerContext;

	/** The HTTP server of the modern MCP server, which a test may stop and start. */
	private volatile DisposableServer modernServer;

	private final ApplicationContextRunner clientContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class,
				StreamableHttpWebFluxTransportAutoConfiguration.class,
				McpClientAnnotationScannerAutoConfiguration.class, McpClientAutoConfiguration.class,
				McpClientModernAutoConfiguration.class, McpToolCallbackAutoConfiguration.class));

	@Test
	void handlerWithLegacyTypesAnswersTheLegacyAndTheModernServer() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withUserConfiguration(LegacyTypedHandlersConfiguration.class)
			.withPropertyValues(connections(legacyPort, modernPort))
			.run(clientContext -> {
				LegacyTypedHandlers handlers = clientContext.getBean(LegacyTypedHandlers.class);
				ToolCallback[] toolCallbacks = clientContext.getBean(ToolCallbackProvider.class).getToolCallbacks();

				assertThat(named(toolCallbacks, "delete_ticket").call("{\"id\":\"t-1\"}")).contains("Deleted t-1");
				assertThat(named(toolCallbacks, "modern_delete_ticket").call("{\"id\":\"t-2\"}"))
					.contains("Deleted t-2");

				assertThat(handlers.questions).containsExactly("Delete ticket t-1?", "Delete ticket t-2?");
				// The progress of the modern server, received by a method that takes the
				// modern type.
				assertThat(handlers.progress).containsExactly("asking for confirmation", "deleting");
				// And by a method that takes its values: a modern client numbers its
				// progress tokens, which the method receives as a string.
				assertThat(handlers.progressValues).hasSize(2).allMatch(values -> values.matches("\\d+ 100\\.0"));
			}));
	}

	@Test
	void handlerWithModernTypesAnswersTheLegacyAndTheModernServer() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withUserConfiguration(ModernTypedHandlersConfiguration.class)
			.withPropertyValues(connections(legacyPort, modernPort))
			.run(clientContext -> {
				ModernTypedHandlers handlers = clientContext.getBean(ModernTypedHandlers.class);
				ToolCallback[] toolCallbacks = clientContext.getBean(ToolCallbackProvider.class).getToolCallbacks();

				assertThat(named(toolCallbacks, "delete_ticket").call("{\"id\":\"t-1\"}")).contains("Deleted t-1");
				assertThat(named(toolCallbacks, "modern_delete_ticket").call("{\"id\":\"t-2\"}"))
					.contains("Deleted t-2");

				assertThat(handlers.questions).containsExactly("Delete ticket t-1?", "Delete ticket t-2?");
				// The progress of the modern server, received by a method that takes the
				// legacy type.
				assertThat(handlers.progress).containsExactly("asking for confirmation", "deleting");
			}));
	}

	@Test
	void oneMethodAnswersTheFormRequestsAndAnotherTheUrlRequestsOfAConnection() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withUserConfiguration(FormAndUrlHandlersConfiguration.class)
			.withPropertyValues(connections(legacyPort, modernPort))
			.run(clientContext -> {
				FormAndUrlHandlers handlers = clientContext.getBean(FormAndUrlHandlers.class);
				ToolCallback[] toolCallbacks = clientContext.getBean(ToolCallbackProvider.class).getToolCallbacks();

				assertThat(named(toolCallbacks, "modern_delete_ticket").call("{\"id\":\"t-1\"}"))
					.contains("Deleted t-1");
				assertThat(named(toolCallbacks, "modern_connect_account").call("{}")).contains("Connected");
				assertThat(named(toolCallbacks, "delete_ticket").call("{\"id\":\"t-2\"}")).contains("Deleted t-2");

				assertThat(handlers.questions).containsExactly("Delete ticket t-1?", "Delete ticket t-2?");
				assertThat(handlers.urls).containsExactly("https://example.com/login");
			}));
	}

	@Test
	void handlerReceivesTheToolContextOfAModernToolCall() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withUserConfiguration(ToolContextHandlersConfiguration.class)
			.withPropertyValues(connections(legacyPort, modernPort))
			.run(clientContext -> {
				ToolContextHandlers handlers = clientContext.getBean(ToolContextHandlers.class);
				ToolCallback[] toolCallbacks = clientContext.getBean(ToolCallbackProvider.class).getToolCallbacks();
				ToolCallback modernTool = named(toolCallbacks, "modern_delete_ticket");

				// One set of callbacks, two calls with their own tool context.
				assertThat(modernTool.call("{\"id\":\"t-1\"}", new ToolContext(Map.of("user", "alice"))))
					.contains("Deleted t-1");
				assertThat(modernTool.call("{\"id\":\"t-2\"}", new ToolContext(Map.of("user", "bob"))))
					.contains("Deleted t-2");
				// The elicitation of a legacy server is not tied to a tool call.
				assertThat(named(toolCallbacks, "delete_ticket").call("{\"id\":\"t-3\"}",
						new ToolContext(Map.of("user", "carol"))))
					.contains("Deleted t-3");

				assertThat(handlers.questions).containsExactly("alice: Delete ticket t-1?", "bob: Delete ticket t-2?",
						"nobody: Delete ticket t-3?");
				assertThat(handlers.progress).containsExactly("alice: asking for confirmation", "alice: deleting",
						"bob: asking for confirmation", "bob: deleting");
			}));
	}

	@Test
	void toolCallCustomizerBeanReplacesTheAnnotatedHandlerForACall() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withUserConfiguration(LegacyTypedHandlersConfiguration.class, DecliningCustomizerConfiguration.class)
			.withPropertyValues(connections(legacyPort, modernPort))
			.run(clientContext -> {
				LegacyTypedHandlers handlers = clientContext.getBean(LegacyTypedHandlers.class);
				ToolCallback modernTool = named(clientContext.getBean(ToolCallbackProvider.class).getToolCallbacks(),
						"modern_delete_ticket");

				// The customizer runs after the annotated handler is set for the call.
				assertThat(modernTool.call("{\"id\":\"t-1\"}", new ToolContext(Map.of("readOnly", true))))
					.contains("Kept t-1");
				assertThat(modernTool.call("{\"id\":\"t-2\"}", new ToolContext(Map.of("readOnly", false))))
					.contains("Deleted t-2");

				assertThat(handlers.questions).containsExactly("Delete ticket t-2?");
			}));
	}

	@Test
	void listChangedMethodsHearAboutTheToolChangesOfAConnectionThatListens() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withUserConfiguration(ListChangedHandlersConfiguration.class)
			.withPropertyValues(CONNECTIONS + "modern.url=http://localhost:" + modernPort,
					CONNECTIONS + "modern.protocol=MODERN", CONNECTIONS + "modern.listen=true")
			.run(clientContext -> {
				ListChangedHandlers handlers = clientContext.getBean(ListChangedHandlers.class);
				// A change is only reported to a stream that is established.
				await().atMost(Duration.ofSeconds(10)).until(() -> !handlers.established.isEmpty());

				this.modernServerContext.getBean(InMemorySyncToolRepository.class).add(addedTool());

				// One method takes the tools in the legacy schema type and one in the
				// modern one; both get the tools as the server lists them now.
				await().atMost(Duration.ofSeconds(10))
					.untilAsserted(() -> assertThat(handlers.seen).containsExactlyInAnyOrder(
							"legacy [added, connect_account, delete_ticket]",
							"modern [added, connect_account, delete_ticket]"));
			}));
	}

	@Test
	void listChangedMethodsHearAboutAServerThatComesBackWithOtherTools() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withUserConfiguration(ListChangedHandlersConfiguration.class)
			.withPropertyValues(CONNECTIONS + "modern.url=http://localhost:" + modernPort,
					CONNECTIONS + "modern.protocol=MODERN", CONNECTIONS + "modern.listen=true")
			.run(clientContext -> {
				ListChangedHandlers handlers = clientContext.getBean(ListChangedHandlers.class);
				await().atMost(Duration.ofSeconds(10)).until(() -> handlers.established.size() == 1);
				// The first stream calls no method: nothing was known before it.
				assertThat(handlers.seen).isEmpty();

				// The server goes down and gets a tool while no stream is there to
				// report it on.
				this.modernServer.disposeNow();
				OPEN_CONNECTIONS.close().awaitUninterruptibly();
				this.modernServerContext.getBean(InMemorySyncToolRepository.class).add(addedTool());
				this.modernServer = startModernServer(this.modernServerContext, modernPort);

				// The stream is established again, and the methods get the tools of
				// the server as they are now.
				await().atMost(Duration.ofSeconds(30))
					.untilAsserted(() -> assertThat(handlers.seen).containsExactlyInAnyOrder(
							"legacy [added, connect_account, delete_ticket]",
							"modern [added, connect_account, delete_ticket]"));
				assertThat(handlers.established).hasSize(2);
			}));
	}

	@Test
	void withoutAHandlerTheModernClientDeclaresNoElicitationAndTheServerRefusesTheTool() {
		withBothServers((legacyPort, modernPort) -> this.clientContextRunner
			.withPropertyValues(CONNECTIONS + "modern.url=http://localhost:" + modernPort,
					CONNECTIONS + "modern.protocol=MODERN")
			.run(clientContext -> {
				ToolCallback[] toolCallbacks = clientContext.getBean(ToolCallbackProvider.class).getToolCallbacks();

				assertThatThrownBy(() -> named(toolCallbacks, "modern_delete_ticket").call("{\"id\":\"t-2\"}"))
					.isInstanceOf(McpMissingClientCapabilityException.class);
			}));
	}

	private void withBothServers(ClientRun clientRun) {
		int modernPort = TestSocketUtils.findAvailableTcpPort();
		int legacyPort = TestSocketUtils.findAvailableTcpPort();

		this.modernServerContextRunner.run(modernContext -> this.legacyServerContextRunner.run(legacyContext -> {
			this.modernServerContext = modernContext;
			this.modernServer = startModernServer(modernContext, modernPort);
			DisposableServer legacyServer = startLegacyServer(legacyContext, legacyPort);
			try {
				clientRun.run(legacyPort, modernPort);
			}
			finally {
				legacyServer.disposeNow();
				this.modernServer.disposeNow();
			}
		}));
	}

	private static String[] connections(int legacyPort, int modernPort) {
		return new String[] {
				// no protocol: a legacy connection
				CONNECTIONS + "legacy.url=http://localhost:" + legacyPort,
				CONNECTIONS + "modern.url=http://localhost:" + modernPort, CONNECTIONS + "modern.protocol=MODERN" };
	}

	private static ToolCallback named(ToolCallback[] toolCallbacks, String name) {
		return List.of(toolCallbacks)
			.stream()
			.filter(callback -> callback.getToolDefinition().name().equals(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no tool named " + name));
	}

	private static McpSyncTool addedTool() {
		return McpSyncTool.of(
				io.modelcontextprotocol.modern.McpSchema.Tool
					.builder("added", Map.of("type", "object", "properties", Map.of()))
					.description("Added at runtime")
					.build(),
				(ctx, request) -> McpSyncResponse
					.result(CallToolResult.builder().addContent(TextContent.builder("added").build()).build()));
	}

	private static DisposableServer startModernServer(ApplicationContext serverContext, int port) {
		RouterFunction<?> router = serverContext.getBean("webFluxModernServerRouterFunction", RouterFunction.class);
		return startHttpServer(router, port);
	}

	private static DisposableServer startLegacyServer(ApplicationContext serverContext, int port) {
		RouterFunction<?> router = serverContext.getBean(WebFluxStreamableServerTransportProvider.class)
			.getRouterFunction();
		return startHttpServer(router, port);
	}

	private static DisposableServer startHttpServer(RouterFunction<?> router, int port) {
		return HttpServer.create()
			.port(port)
			// Stopping the server does not close the connections that are open, such
			// as a listen stream. They are kept so that a test can close them.
			.doOnConnection(connection -> OPEN_CONNECTIONS.add(connection.channel()))
			.handle(new ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(router)))
			.bindNow();
	}

	@FunctionalInterface
	interface ClientRun {

		void run(int legacyPort, int modernPort);

	}

	record Confirmation(boolean confirmed) {
	}

	/** Handler methods written against the schema types of the legacy protocol. */
	static class LegacyTypedHandlers {

		final List<String> questions = new CopyOnWriteArrayList<>();

		final List<String> progress = new CopyOnWriteArrayList<>();

		final List<String> progressValues = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = { "legacy", "modern" })
		StructuredElicitResult<Confirmation> confirm(McpSchema.ElicitRequest request) {
			this.questions.add(request.message());
			return new StructuredElicitResult<>(McpSchema.ElicitResult.Action.ACCEPT, new Confirmation(true), null);
		}

		@McpProgress(clients = "modern")
		void progress(ProgressParams progress) {
			this.progress.add(progress.message());
		}

		@McpProgress(clients = "modern")
		void progressValues(Double progress, String progressToken, String total) {
			this.progressValues.add(progressToken + " " + total);
		}

	}

	/** One method for the form requests and one for the URL requests of a connection. */
	static class FormAndUrlHandlers {

		final List<String> questions = new CopyOnWriteArrayList<>();

		final List<String> urls = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = { "legacy", "modern" })
		StructuredElicitResult<Confirmation> confirm(McpSchema.ElicitFormRequest request) {
			this.questions.add(request.message());
			return new StructuredElicitResult<>(McpSchema.ElicitResult.Action.ACCEPT, new Confirmation(true), null);
		}

		@McpElicitation(clients = "modern")
		ElicitResult openUrl(ElicitUrlRequest request) {
			this.urls.add(request.url());
			return ElicitResult.accept(null);
		}

	}

	static class FormAndUrlHandlersConfiguration {

		@Bean
		FormAndUrlHandlers formAndUrlHandlers() {
			return new FormAndUrlHandlers();
		}

	}

	/** Handler methods written against the schema types of the modern revision. */
	static class ModernTypedHandlers {

		final List<String> questions = new CopyOnWriteArrayList<>();

		final List<String> progress = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = { "legacy", "modern" })
		ElicitResult confirm(ElicitFormRequest request) {
			this.questions.add(request.message());
			return ElicitResult.accept(Map.of("confirmed", true));
		}

		@McpProgress(clients = "modern")
		void progress(McpSchema.ProgressNotification notification) {
			this.progress.add(notification.message());
		}

	}

	/** Handler methods that take the tool context of the tool call they serve. */
	static class ToolContextHandlers {

		final List<String> questions = new CopyOnWriteArrayList<>();

		final List<String> progress = new CopyOnWriteArrayList<>();

		@McpElicitation(clients = { "legacy", "modern" })
		ElicitResult confirm(ElicitFormRequest request, ToolContext toolContext) {
			this.questions.add(user(toolContext) + ": " + request.message());
			return ElicitResult.accept(Map.of("confirmed", true));
		}

		@McpProgress(clients = "modern")
		void progress(ProgressParams progress, ToolContext toolContext) {
			this.progress.add(user(toolContext) + ": " + progress.message());
		}

		private static Object user(ToolContext toolContext) {
			return toolContext != null ? toolContext.getContext().get("user") : "nobody";
		}

	}

	static class ToolContextHandlersConfiguration {

		@Bean
		ToolContextHandlers toolContextHandlers() {
			return new ToolContextHandlers();
		}

	}

	/** Declines every elicitation of a tool call whose tool context is read-only. */
	static class DecliningCustomizerConfiguration {

		@Bean
		SyncModernMcpToolCallCustomizer readOnlyCallsDecline() {
			return (connectionInfo, toolSpec,
					toolContext) -> toolContext != null && Boolean.TRUE.equals(toolContext.getContext().get("readOnly"))
							? toolSpec.formElicitation(request -> ElicitResult.decline()) : toolSpec;
		}

	}

	/**
	 * List-changed methods for the modern connection, and a listener that tells when its
	 * listen stream is established.
	 */
	static class ListChangedHandlers implements SyncModernMcpChangeListener {

		final List<String> seen = new CopyOnWriteArrayList<>();

		final List<String> established = new CopyOnWriteArrayList<>();

		@McpToolListChanged(clients = "modern")
		void legacyTools(List<McpSchema.Tool> tools) {
			this.seen.add("legacy " + tools.stream().map(McpSchema.Tool::name).sorted().toList());
		}

		@McpToolListChanged(clients = "modern")
		void modernTools(List<io.modelcontextprotocol.modern.McpSchema.Tool> tools) {
			this.seen.add("modern "
					+ tools.stream().map(io.modelcontextprotocol.modern.McpSchema.Tool::name).sorted().toList());
		}

		@Override
		public void changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event) {
		}

		@Override
		public void streamEstablished(McpModernConnectionInfo connectionInfo) {
			this.established.add(connectionInfo.name());
		}

	}

	static class ListChangedHandlersConfiguration {

		@Bean
		ListChangedHandlers listChangedHandlers() {
			return new ListChangedHandlers();
		}

	}

	static class LegacyTypedHandlersConfiguration {

		@Bean
		LegacyTypedHandlers legacyTypedHandlers() {
			return new LegacyTypedHandlers();
		}

	}

	static class ModernTypedHandlersConfiguration {

		@Bean
		ModernTypedHandlers modernTypedHandlers() {
			return new ModernTypedHandlers();
		}

	}

	static class ModernServerConfiguration {

		@Bean
		ModernTicketTools modernTicketTools() {
			return new ModernTicketTools();
		}

	}

	static class LegacyServerConfiguration {

		@Bean
		LegacyTicketTools legacyTicketTools() {
			return new LegacyTicketTools();
		}

	}

	/** A modern tool that asks for confirmation in a multi round-trip request. */
	static class ModernTicketTools {

		private static final Map<String, Object> CONFIRM_SCHEMA = Map.of("type", "object", "properties",
				Map.of("confirmed", Map.of("type", "boolean")), "required", List.of("confirmed"));

		@McpTool(name = "delete_ticket", description = "Deletes a ticket after asking the user to confirm",
				requiredCapabilities = McpClientCapability.ELICITATION_FORM)
		public CallToolOutcome deleteTicket(McpSyncNotifier notifier, McpModernSyncRequestContext ctx,
				@McpToolParam(description = "Ticket id") String id) {
			ElicitAnswer answer = ctx.inputResponse("confirm", ElicitAnswer.class);
			if (answer == null) {
				notifier.progress(10, 100.0, "asking for confirmation");
				return InputRequiredResult.builder()
					.elicit("confirm", ElicitFormRequest.builder("Delete ticket " + id + "?", CONFIRM_SCHEMA).build())
					.build();
			}
			boolean confirmed = "accept".equals(answer.action()) && answer.content() != null
					&& Boolean.TRUE.equals(answer.content().get("confirmed"));
			notifier.progress(100, 100.0, "deleting");
			return CallToolResult.builder()
				.addContent(TextContent.builder(confirmed ? "Deleted " + id : "Kept " + id).build())
				.build();
		}

		@McpTool(name = "connect_account", description = "Connects an account after the user logged in",
				requiredCapabilities = McpClientCapability.ELICITATION_URL)
		public CallToolOutcome connectAccount(McpModernSyncRequestContext ctx) {
			ElicitAnswer answer = ctx.inputResponse("login", ElicitAnswer.class);
			if (answer == null) {
				return InputRequiredResult.builder()
					.elicitUrl("login", "Log in to connect the account", "https://example.com/login")
					.build();
			}
			return CallToolResult.builder()
				.addContent(
						TextContent.builder("accept".equals(answer.action()) ? "Connected" : "Not connected").build())
				.build();
		}

		record ElicitAnswer(String action, Map<String, Object> content) {
		}

	}

	/** A legacy tool that asks for confirmation with a request to the client. */
	static class LegacyTicketTools {

		@McpTool(name = "delete_ticket", description = "Deletes a ticket after asking the user to confirm")
		public String deleteTicket(McpSyncRequestContext ctx, @McpToolParam(description = "Ticket id") String id) {
			StructuredElicitResult<Confirmation> answer = ctx.elicit(e -> e.message("Delete ticket " + id + "?"),
					Confirmation.class);
			boolean confirmed = answer.action() == McpSchema.ElicitResult.Action.ACCEPT
					&& answer.structuredContent() != null && answer.structuredContent().confirmed();
			return confirmed ? "Deleted " + id : "Kept " + id;
		}

	}

}
