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
import java.util.concurrent.atomic.AtomicReference;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.transport.McpHttpRequestCustomizer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientModernAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.webflux.autoconfigure.StreamableHttpWebFluxTransportAutoConfiguration;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerJsonMapperAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerModernAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.ModernServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.mcp.server.webflux.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connects the auto-configured MCP client to auto-configured MCP servers over HTTP: to a
 * modern server alone, and to a legacy and a modern server at the same time. In both
 * cases the application sees a single {@link ToolCallbackProvider}.
 *
 * @author Christian Tzolov
 */
class ModernMcpClientServerIT {

	private static final String USER_HEADER = "X-Test-User";

	private static final String CONNECTIONS = "spring.ai.mcp.client.streamable-http.connections.";

	private static final ThreadLocal<String> THREAD_BOUND_USER = new ThreadLocal<>();

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

	// The client application has the legacy and the modern auto-configurations, as an
	// application on the MCP client starter does.
	private final ApplicationContextRunner clientContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class,
				StreamableHttpWebFluxTransportAutoConfiguration.class, McpClientAutoConfiguration.class,
				McpClientModernAutoConfiguration.class, McpToolCallbackAutoConfiguration.class))
		.withUserConfiguration(ClientConfiguration.class);

	// A client application that takes the user from a thread-bound holder, as an
	// application that reads the SecurityContextHolder does.
	private final ApplicationContextRunner threadBoundClientContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class,
				StreamableHttpWebFluxTransportAutoConfiguration.class, McpClientAutoConfiguration.class,
				McpClientModernAutoConfiguration.class, McpToolCallbackAutoConfiguration.class))
		.withUserConfiguration(ThreadBoundClientConfiguration.class);

	@Test
	void publicToolListingIsKeptForTheTimeTheServerAllows() {
		int modernPort = TestSocketUtils.findAvailableTcpPort();

		this.modernServerContextRunner
			.withPropertyValues("spring.ai.mcp.server.modern.list-ttl=5m",
					"spring.ai.mcp.server.modern.list-cache-scope=PUBLIC")
			.run(serverContext -> {
				DisposableServer modernServer = startModernServer(serverContext, modernPort);
				try {
					this.clientContextRunner
						.withPropertyValues(CONNECTIONS + "greeter.url=http://localhost:" + modernPort,
								CONNECTIONS + "greeter.protocol=MODERN")
						.run(clientContext -> {
							SeenUsers seenUsers = serverContext.getBean(SeenUsers.class);
							CurrentUser currentUser = clientContext.getBean(CurrentUser.class);
							ToolCallbackProvider provider = clientContext.getBean(ToolCallbackProvider.class);

							currentUser.name.set("alice");
							assertThat(provider.getToolCallbacks()).hasSize(1);

							currentUser.name.set("bob");
							assertThat(provider.getToolCallbacks()[0].call("{\"name\":\"AI\"}")).contains("Hello AI");

							// tools/list for alice; bob's request uses the kept
							// listing and only sends its tools/call, as bob
							assertThat(seenUsers.names).containsExactly("alice", "bob");
						});
				}
				finally {
					modernServer.disposeNow();
				}
			});
	}

	@Test
	void streamingChatClientCallsAModernToolAsTheUserThatStartedTheRequest() {
		int modernPort = TestSocketUtils.findAvailableTcpPort();

		this.modernServerContextRunner.run(serverContext -> {
			DisposableServer modernServer = startModernServer(serverContext, modernPort);
			try {
				this.threadBoundClientContextRunner
					.withPropertyValues(CONNECTIONS + "greeter.url=http://localhost:" + modernPort,
							CONNECTIONS + "greeter.protocol=MODERN")
					.run(clientContext -> {
						SeenUsers seenUsers = serverContext.getBean(SeenUsers.class);
						ToolCallingChatModel chatModel = new ToolCallingChatModel("greeter_greet",
								"{\"name\":\"Spring\"}");
						ChatClient chatClient = ChatClient.builder(chatModel)
							.defaultToolCallbacks(clientContext.getBean(ToolCallbackProvider.class))
							.build();

						// stream() resolves the tools on this thread, where the user is
						// known. The model and the tool run later, on other threads.
						Flux<String> content;
						THREAD_BOUND_USER.set("alice");
						try {
							content = chatClient.prompt("Greet Spring").stream().content();
						}
						finally {
							THREAD_BOUND_USER.remove();
						}
						String answer = String.join("", content.collectList().block(Duration.ofSeconds(30)));

						assertThat(chatModel.offeredToolNames).containsExactly("greeter_greet");
						assertThat(answer).contains("Hello Spring");
						assertThat(chatModel.toolResultThread).isNotEqualTo(Thread.currentThread().getName());
						// tools/list on this thread and tools/call on another,
						// both as alice
						assertThat(seenUsers.names).containsExactly("alice", "alice");
					});
			}
			finally {
				modernServer.disposeNow();
			}
		});
	}

	@Test
	void legacyAndModernServersAreServedByOneProvider() {
		int modernPort = TestSocketUtils.findAvailableTcpPort();
		int legacyPort = TestSocketUtils.findAvailableTcpPort();

		this.modernServerContextRunner.run(modernContext -> this.legacyServerContextRunner.run(legacyContext -> {
			DisposableServer modernServer = startModernServer(modernContext, modernPort);
			DisposableServer legacyServer = startLegacyServer(legacyContext, legacyPort);
			try {
				this.clientContextRunner
					.withPropertyValues(CONNECTIONS + "greeter.url=http://localhost:" + modernPort,
							CONNECTIONS + "greeter.protocol=MODERN",
							// no protocol: a legacy connection
							CONNECTIONS + "shouter.url=http://localhost:" + legacyPort)
					.run(clientContext -> {
						assertThat(clientContext).hasSingleBean(ToolCallbackProvider.class);
						ToolCallback[] toolCallbacks = clientContext.getBean(ToolCallbackProvider.class)
							.getToolCallbacks();

						assertThat(toolCallbacks).extracting(callback -> callback.getToolDefinition().name())
							.containsExactlyInAnyOrder("shout", "greeter_greet");
						assertThat(named(toolCallbacks, "shout").call("{\"text\":\"legacy\"}")).contains("LEGACY");
						assertThat(named(toolCallbacks, "greeter_greet").call("{\"name\":\"modern\"}"))
							.contains("Hello modern");
					});
			}
			finally {
				legacyServer.disposeNow();
				modernServer.disposeNow();
			}
		}));
	}

	@Test
	void unreachableModernServerDoesNotHideTheLegacyTools() {
		int legacyPort = TestSocketUtils.findAvailableTcpPort();
		// nothing listens on this port
		int modernPort = TestSocketUtils.findAvailableTcpPort();

		this.legacyServerContextRunner.run(legacyContext -> {
			DisposableServer legacyServer = startLegacyServer(legacyContext, legacyPort);
			try {
				this.clientContextRunner
					.withPropertyValues(CONNECTIONS + "greeter.url=http://localhost:" + modernPort,
							CONNECTIONS + "greeter.protocol=MODERN",
							CONNECTIONS + "shouter.url=http://localhost:" + legacyPort)
					.run(clientContext -> {
						ToolCallback[] toolCallbacks = clientContext.getBean(ToolCallbackProvider.class)
							.getToolCallbacks();

						assertThat(toolCallbacks).extracting(callback -> callback.getToolDefinition().name())
							.containsExactly("shout");
						assertThat(toolCallbacks[0].call("{\"text\":\"legacy\"}")).contains("LEGACY");
					});
			}
			finally {
				legacyServer.disposeNow();
			}
		});
	}

	private static ToolCallback named(ToolCallback[] toolCallbacks, String name) {
		return List.of(toolCallbacks)
			.stream()
			.filter(callback -> callback.getToolDefinition().name().equals(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no tool named " + name));
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
			.handle(new ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(router)))
			.bindNow();
	}

	/** Turns the identity of a call into an HTTP header, as a token-adding hook would. */
	private static McpHttpRequestCustomizer userHeader() {
		return (request, headers, context) -> {
			headers.set(USER_HEADER, String.valueOf(context.get("user")));
			return Mono.empty();
		};
	}

	/**
	 * A chat model that asks for one tool call and then answers with the result of that
	 * tool.
	 */
	static class ToolCallingChatModel implements ChatModel {

		private final String toolName;

		private final String toolArguments;

		final List<String> offeredToolNames = new CopyOnWriteArrayList<>();

		volatile String toolResultThread = "";

		ToolCallingChatModel(String toolName, String toolArguments) {
			this.toolName = toolName;
			this.toolArguments = toolArguments;
		}

		@Override
		public ChatOptions getOptions() {
			return DefaultToolCallingChatOptions.builder().build();
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			throw new UnsupportedOperationException("only streaming is used");
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			List<Message> messages = prompt.getInstructions();
			if (messages.get(messages.size() - 1) instanceof ToolResponseMessage toolResponse) {
				this.toolResultThread = Thread.currentThread().getName();
				return Flux.just(chatResponse(
						new AssistantMessage("The tool said: " + toolResponse.getResponses().get(0).responseData())));
			}
			if (prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks() != null) {
				options.getToolCallbacks()
					.forEach(callback -> this.offeredToolNames.add(callback.getToolDefinition().name()));
			}
			AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call-1", "function", this.toolName,
					this.toolArguments);
			return Flux.just(chatResponse(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build()));
		}

		private static ChatResponse chatResponse(AssistantMessage message) {
			return ChatResponse.builder()
				.generations(List.of(new Generation(message)))
				.metadata(ChatResponseMetadata.builder().id("").model("").build())
				.build();
		}

	}

	/** The user on whose behalf the client application currently acts. */
	static class CurrentUser {

		final AtomicReference<String> name = new AtomicReference<>("nobody");

	}

	/** The users the modern server saw, one entry for each request. */
	static class SeenUsers {

		final List<String> names = new CopyOnWriteArrayList<>();

	}

	@Configuration(proxyBeanMethods = false)
	static class ClientConfiguration {

		@Bean
		CurrentUser currentUser() {
			return new CurrentUser();
		}

		/**
		 * Supplies the identity of a call: the transport context is read on the thread
		 * that starts the call.
		 */
		@Bean
		McpClientCustomizer<McpClient.SyncBuilder> identityCustomizer(CurrentUser currentUser) {
			return (name, builder) -> builder
				.transportContextProvider(() -> McpTransportContext.create(Map.of("user", currentUser.name.get())));
		}

		@Bean
		McpHttpRequestCustomizer userHeaderCustomizer() {
			return userHeader();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ThreadBoundClientConfiguration {

		/**
		 * Takes the user from the thread that resolves the tools and binds it to the
		 * client of that request. The customizer runs for each new client, which with the
		 * default client scope is once for each chat request.
		 */
		@Bean
		McpClientCustomizer<McpClient.SyncBuilder> identityCustomizer() {
			return (name, builder) -> {
				McpTransportContext caller = McpTransportContext
					.create(Map.of("user", String.valueOf(THREAD_BOUND_USER.get())));
				builder.transportContextProvider(() -> caller);
			};
		}

		@Bean
		McpHttpRequestCustomizer userHeaderCustomizer() {
			return userHeader();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ModernServerConfiguration {

		@Bean
		SeenUsers seenUsers() {
			return new SeenUsers();
		}

		@Bean
		McpTransportContextExtractor<ServerRequest> userExtractor(SeenUsers seenUsers) {
			return request -> {
				String user = request.headers().firstHeader(USER_HEADER);
				seenUsers.names.add(String.valueOf(user));
				return McpTransportContext.create(Map.of("user", String.valueOf(user)));
			};
		}

		@Bean
		GreetingTools greetingTools() {
			return new GreetingTools();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class LegacyServerConfiguration {

		@Bean
		ShoutingTools shoutingTools() {
			return new ShoutingTools();
		}

	}

	static class GreetingTools {

		@McpTool(name = "greet", description = "Greets")
		public String greet(@McpToolParam(description = "name") String name) {
			return "Hello " + name;
		}

	}

	static class ShoutingTools {

		@McpTool(name = "shout", description = "Shouts")
		public String shout(@McpToolParam(description = "text") String text) {
			return text.toUpperCase();
		}

	}

}
