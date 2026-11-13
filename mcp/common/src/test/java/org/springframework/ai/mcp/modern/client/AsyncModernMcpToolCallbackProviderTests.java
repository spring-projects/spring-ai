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

package org.springframework.ai.mcp.modern.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpAsyncClient;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.McpTimeoutException;
import io.modelcontextprotocol.modern.client.transport.McpClientTransport;
import io.modelcontextprotocol.modern.client.transport.McpHttpStatusException;
import io.modelcontextprotocol.modern.client.transport.McpTransportRequest;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AsyncModernMcpToolCallbackProvider},
 * {@link AsyncModernMcpToolCallback} and {@link AsyncModernMcpConnection}, with an
 * asynchronous modern client that talks to a modern server in the same JVM. What the
 * provider keeps of the listings is shared with the synchronous provider and is tested
 * there.
 *
 * @author Christian Tzolov
 */
class AsyncModernMcpToolCallbackProviderTests {

	private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of());

	private static final Implementation CLIENT_INFO = Implementation.builder("test-client", "1").build();

	@AfterEach
	void clearReactorContextOfTheChatCall() {
		ToolCallReactiveContextHolder.clearContext();
	}

	@Test
	void listsToolsUnderTheConnectionPrefixAndCallsThem() {
		InMemoryTransport transport = transport(echoTool());
		AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.shared(info("tickets"), McpClient.async(transport).build()))
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tickets_echo");
		assertThat(callbacks[0].getToolDefinition().description()).isEqualTo("Echoes the text");
		assertThat(callbacks[0].call("{\"text\":\"hi\"}")).contains("echo:hi");
		assertThat(transport.methods()).containsExactly(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_CALL);
	}

	@Test
	void structuredContentIsReturnedTogetherWithTheContent() {
		ToolCallback callback = AsyncModernMcpToolCallbackProvider.builder()
			.connection(
					AsyncModernMcpConnection.shared(info("w"), McpClient.async(transport(structuredTool())).build()))
			.build()
			.getToolCallbacks()[0];

		assertThat(callback.call("{}")).contains("\"structuredContent\":{\"temperature\":21}").contains("sunny");
	}

	@Test
	void errorResultBecomesToolExecutionException() {
		ToolCallback callback = AsyncModernMcpToolCallbackProvider.builder()
			.connection(
					AsyncModernMcpConnection.shared(info("shop"), McpClient.async(transport(failingTool())).build()))
			.build()
			.getToolCallbacks()[0];

		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("out of stock");
	}

	@Test
	void protocolErrorIsRethrown() {
		McpAsyncClient client = McpClient.async(transport(echoTool())).build();
		ToolCallback callback = AsyncModernMcpToolCallback.builder()
			.mcpClient(client)
			.tool(Tool.builder("not-on-the-server", EMPTY_SCHEMA).build())
			.build();

		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(McpException.class);
	}

	@Test
	void timeoutBecomesToolExecutionException() {
		// accepts the request and never answers
		McpClientTransport hanging = request -> request.withRoundTimeout(Flux.never());
		McpAsyncClient client = McpClient.async(hanging).roundTimeout(Duration.ofMillis(200)).build();
		ToolCallback callback = AsyncModernMcpToolCallback.builder()
			.mcpClient(client)
			.tool(Tool.builder("slow", EMPTY_SCHEMA).build())
			.build();

		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(ToolExecutionException.class)
			.hasCauseInstanceOf(McpTimeoutException.class);
	}

	@Test
	void toolContextBecomesRequestMetaWithoutTheReservedKeys() {
		AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.shared(info("idp"), McpClient.async(transport(whoAmITool())).build()))
			.build();
		ToolContext toolContext = new ToolContext(Map.of("tenant", "acme", McpSchema.MetaKeys.PROGRESS_TOKEN, "p1",
				McpSchema.MetaKeys.CLIENT_INFO, "spoofed"));

		String response = provider.getToolCallbacks()[0].call("{}", toolContext);

		assertThat(response).contains("\"tenant\":\"acme\"");
		// The client sets these keys itself, so the tool context cannot override them.
		assertThat(response).doesNotContain("\"p1\"").doesNotContain("spoofed");
	}

	@Test
	void toolCallCustomizerIsAppliedToEachCallWithItsToolContext() {
		List<String> calls = new CopyOnWriteArrayList<>();
		AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.shared(info("idp"), McpClient.async(transport(whoAmITool())).build()))
			.toolCallCustomizer((connectionInfo, toolSpec, toolContext) -> {
				calls.add(connectionInfo.name() + ":"
						+ (toolContext != null ? toolContext.getContext().get("user") : "no tool context"));
				// The spec that the customizer returns is the one that is called.
				return toolSpec.meta(Map.of("tenant", "set-by-customizer"));
			})
			.build();
		ToolCallback callback = provider.getToolCallbacks()[0];

		assertThat(callback.call("{}", new ToolContext(Map.of("user", "alice"))))
			.contains("\"tenant\":\"set-by-customizer\"");
		callback.call("{}");

		assertThat(calls).containsExactly("idp:alice", "idp:no tool context");
	}

	@Test
	void callerOfTheRequestGoesWithTheListingAndWithTheToolCallsOfThatRequest() {
		InMemoryTransport transport = transport(whoAmITool());
		AtomicReference<String> currentUser = new AtomicReference<>("alice");
		AtomicInteger clientsCreated = new AtomicInteger();
		// An asynchronous client holds no caller. The connection is asked for the caller
		// inside getToolCallbacks(), on the calling thread, where a web application knows
		// whom it works for.
		AsyncModernMcpConnection connection = AsyncModernMcpConnection.perRequest(info("idp"), () -> {
			clientsCreated.incrementAndGet();
			return McpClient.async(transport).build();
		}).withTransportContext(connectionInfo -> McpTransportContext.create(Map.of("user", currentUser.get())));
		AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
			.connection(connection)
			.build();

		ToolCallback alicesCallback = provider.getToolCallbacks()[0];
		currentUser.set("bob");
		ToolCallback bobsCallback = provider.getToolCallbacks()[0];
		currentUser.set("nobody");

		assertThat(clientsCreated).hasValue(2);
		// The listings went out for the caller of each request.
		assertThat(transport.callers()).containsExactly("alice", "bob");
		// The callbacks keep the caller they were resolved for, on whatever thread and
		// at whatever time the tool runs.
		assertThat(alicesCallback.call("{}")).contains("\"user\":\"alice\"");
		assertThat(bobsCallback.call("{}")).contains("\"user\":\"bob\"");
	}

	@Test
	void callerTheToolsWereResolvedForWinsOverTheReactorContextOfTheChatCall() {
		InMemoryTransport transport = transport(whoAmITool());
		AsyncModernMcpConnection connection = AsyncModernMcpConnection
			.shared(info("idp"), McpClient.async(transport).build())
			.withTransportContext(connectionInfo -> McpTransportContext.create(Map.of("user", "alice")));
		ToolCallback callback = AsyncModernMcpToolCallbackProvider.builder()
			.connection(connection)
			.build()
			.getToolCallbacks()[0];

		// A chat call hands its Reactor context to the tool calls. It may hold a
		// transport context that is not the caller of this server, such as that of the
		// request an MCP server of the same application is serving.
		ToolCallReactiveContextHolder.setContext(Context.of(McpTransportContext.KEY,
				McpTransportContext.create(Map.of("user", "inbound")), "trace", "t-1"));

		// The tool is called for the caller it was listed for.
		assertThat(callback.call("{}")).contains("\"user\":\"alice\"");
	}

	@Test
	void callbackWithoutACallerUsesTheTransportContextOfTheReactorContext() {
		InMemoryTransport transport = transport(whoAmITool());
		ToolCallback callback = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.shared(info("idp"), McpClient.async(transport).build()))
			.build()
			.getToolCallbacks()[0];

		// An application that supplies no caller when the tools are resolved can still
		// put one into the Reactor context of its chat call, as the SDK client expects.
		ToolCallReactiveContextHolder
			.setContext(Context.of(McpTransportContext.KEY, McpTransportContext.create(Map.of("user", "carol"))));

		assertThat(callback.call("{}")).contains("\"user\":\"carol\"");
	}

	@Test
	void toolsCannotBeListedOnAThreadThatMayNotBlock() {
		for (CacheScope cacheScope : CacheScope.values()) {
			InMemoryTransport transport = transport(60_000, cacheScope, echoTool());
			AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
				.connection(
						AsyncModernMcpConnection.perRequest(info("tickets"), () -> McpClient.async(transport).build()))
				.build();

			Object firstAttempt = onANonBlockingThread(provider::getToolCallbacks);

			// The message says what to do, which that of Reactor does not.
			assertThat(firstAttempt).isInstanceOf(IllegalStateException.class);
			assertThat(((IllegalStateException) firstAttempt).getMessage()).contains("MCP connection 'tickets'")
				.contains("non-blocking thread")
				.contains("boundedElastic");
			assertThat(transport.methods()).isEmpty();

			provider.getToolCallbacks();
			Object secondAttempt = onANonBlockingThread(provider::getToolCallbacks);
			if (cacheScope == CacheScope.PUBLIC) {
				// A kept listing needs no request, so nothing blocks.
				assertThat(secondAttempt).isInstanceOf(ToolCallback[].class);
			}
			else {
				assertThat(secondAttempt).isInstanceOf(IllegalStateException.class);
			}
		}
	}

	@Test
	void connectionWithoutATransportContextProviderHasNoCaller() {
		InMemoryTransport transport = transport(whoAmITool());
		ToolCallback callback = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.shared(info("idp"), McpClient.async(transport).build()))
			.build()
			.getToolCallbacks()[0];

		assertThat(callback.call("{}")).contains("\"user\":null");
		// The one listing went out without a caller.
		assertThat(transport.callers()).containsExactly("null");
	}

	@Test
	void privateListingIsKeptByAClientThatOutlivesTheRequestOnly() {
		InMemoryTransport perRequestTransport = transport(60_000, CacheScope.PRIVATE, echoTool());
		AsyncModernMcpToolCallbackProvider perRequest = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.perRequest(info("tickets"),
					() -> McpClient.async(perRequestTransport).build()))
			.build();
		perRequest.getToolCallbacks();
		perRequest.getToolCallbacks();
		assertThat(perRequestTransport.methods()).hasSize(2);
		// The server allows the listing to be kept, which this connection cannot do.
		assertThat(perRequest).extracting("listings.privateListingHints")
			.asInstanceOf(InstanceOfAssertFactories.collection(String.class))
			.containsExactly("tickets");

		InMemoryTransport sharedTransport = transport(60_000, CacheScope.PRIVATE, echoTool());
		AsyncModernMcpToolCallbackProvider shared = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.shared(info("tickets"), McpClient.async(sharedTransport).build()))
			.build();
		shared.getToolCallbacks();
		shared.getToolCallbacks();
		assertThat(sharedTransport.methods()).hasSize(1);
	}

	@Test
	void publicListingIsKeptAndADroppedListingIsListedAgainFromTheServer() {
		for (CacheScope cacheScope : CacheScope.values()) {
			InMemoryTransport transport = transport(60_000, cacheScope, echoTool());
			AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
				.connection(AsyncModernMcpConnection.shared(info("tickets"), McpClient.async(transport).build()))
				.build();

			provider.getToolCallbacks();
			provider.getToolCallbacks();
			assertThat(transport.methods()).as(cacheScope.name()).hasSize(1);

			provider.invalidateToolListing("tickets");
			provider.getToolCallbacks();
			provider.getToolCallbacks();

			// Listed once more after the drop, and kept again from then on.
			assertThat(transport.methods()).as(cacheScope.name()).hasSize(2);
		}
	}

	@Test
	void scopedConnectionKeepsAClientAndItsPrivateListingForEachUser() {
		InMemoryTransport transport = transport(60_000, CacheScope.PRIVATE, whoAmITool());
		AtomicReference<String> currentUser = new AtomicReference<>("alice");
		AtomicInteger clientsCreated = new AtomicInteger();
		Map<String, McpAsyncClient> clients = new ConcurrentHashMap<>();
		AsyncModernMcpConnection connection = AsyncModernMcpConnection.scoped(info("tickets"), () -> {
			clientsCreated.incrementAndGet();
			return McpClient.async(transport).build();
		}, (connectionInfo, clientFactory) -> clients.computeIfAbsent(currentUser.get(), user -> clientFactory.get()))
			.withTransportContext(connectionInfo -> McpTransportContext.create(Map.of("user", currentUser.get())));
		AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
			.connection(connection)
			.build();

		for (String user : List.of("alice", "alice", "bob", "alice")) {
			currentUser.set(user);
			provider.getToolCallbacks();
		}

		assertThat(clientsCreated).hasValue(2);
		assertThat(transport.callers()).containsExactly("alice", "bob");
		// The scope is not asked for the client of the application.
		assertThat(connection.applicationClient()).isNotNull();
		assertThat(clientsCreated).hasValue(3);
	}

	@Test
	void scopeThatReturnsTheClientOfAnotherConnectionIsRejected() {
		InMemoryTransport tickets = transport(echoTool());
		InMemoryTransport billing = transport(structuredTool());
		Map<String, McpAsyncClient> clientsByUser = new ConcurrentHashMap<>();
		AsyncModernMcpClientScope byUserOnly = (connectionInfo, clientFactory) -> clientsByUser.computeIfAbsent("alice",
				user -> clientFactory.get());
		AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.scoped(info("tickets"), () -> McpClient.async(tickets).build(),
					byUserOnly))
			.connection(AsyncModernMcpConnection.scoped(info("billing"), () -> McpClient.async(billing).build(),
					byUserOnly))
			.build();

		assertThatThrownBy(provider::getToolCallbacks).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("was not built for the MCP connection 'billing'");
		assertThat(billing.methods()).isEmpty();
	}

	@Test
	void unreachableServerIsLeftOutForTheCoolDownAndTheOthersStayUsable() {
		InMemoryTransport server = transport(echoTool());
		AtomicReference<RuntimeException> outage = new AtomicReference<>(httpStatus(503));
		AtomicInteger requests = new AtomicInteger();
		McpClientTransport flaky = request -> {
			requests.incrementAndGet();
			return (outage.get() != null) ? Flux.error(outage.get()) : server.exchange(request);
		};
		AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
			.connection(AsyncModernMcpConnection.perRequest(info("down"), () -> McpClient.async(flaky).build()))
			.connection(AsyncModernMcpConnection.shared(info("up"), McpClient.async(transport(echoTool())).build()))
			.unreachableCoolDown(Duration.ofSeconds(30))
			.build();

		assertThat(provider.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
			.containsExactly("up_echo");
		assertThat(provider.getToolCallbacks()).hasSize(1);
		// Not asked again during the cool-down.
		assertThat(requests).hasValue(1);
	}

	@Test
	void otherListingFailuresAreThrown() {
		for (RuntimeException failure : List.of(httpStatus(401), httpStatus(404),
				new IllegalStateException("login required"))) {
			McpClientTransport failing = request -> Flux.error(failure);
			AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
				.connection(AsyncModernMcpConnection.shared(info("idp"), McpClient.async(failing).build()))
				.build();

			assertThatThrownBy(provider::getToolCallbacks).as("after " + failure).isSameAs(failure);
		}
	}

	/**
	 * Runs the call on a thread of Reactor that may not block, and returns its result or
	 * the exception it threw.
	 */
	private static Object onANonBlockingThread(Supplier<Object> call) {
		return Mono.fromCallable(() -> {
			try {
				return call.get();
			}
			catch (RuntimeException ex) {
				return ex;
			}
		}).subscribeOn(Schedulers.parallel()).block();
	}

	private static McpSyncTool echoTool() {
		return McpSyncTool.of(Tool.builder("echo", EMPTY_SCHEMA).description("Echoes the text").build(),
				(ctx, request) -> McpSyncResponse.result(CallToolResult.builder()
					.addContent(TextContent.builder("echo:" + request.arguments().get("text")).build())
					.build()));
	}

	private static McpSyncTool structuredTool() {
		return McpSyncTool.of(Tool.builder("weather", EMPTY_SCHEMA).build(),
				(ctx, request) -> McpSyncResponse.result(CallToolResult.builder()
					.addContent(TextContent.builder("sunny").build())
					.structuredContent(Map.of("temperature", 21))
					.build()));
	}

	private static McpSyncTool failingTool() {
		return McpSyncTool.of(Tool.builder("fail", EMPTY_SCHEMA).build(),
				(ctx, request) -> McpSyncResponse.result(CallToolResult.builder()
					.addContent(TextContent.builder("out of stock").build())
					.isError(true)
					.build()));
	}

	private static McpSyncTool whoAmITool() {
		return McpSyncTool.of(Tool.builder("whoami", EMPTY_SCHEMA).build(), (ctx, request) -> {
			Map<String, Object> answer = new HashMap<>();
			answer.put("user", ctx.transportContext().get("user"));
			answer.put("tenant", ctx.meta().get("tenant"));
			answer.put("progressToken", ctx.meta().get(McpSchema.MetaKeys.PROGRESS_TOKEN));
			answer.put("clientInfo", ctx.meta().get(McpSchema.MetaKeys.CLIENT_INFO));
			return McpSyncResponse.result(CallToolResult.builder().structuredContent(answer).build());
		});
	}

	private static InMemoryTransport transport(McpSyncTool... tools) {
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test-server", "1").build())
			.feature(ToolsFeature.ofSync(InMemorySyncToolRepository.builder().tools(List.of(tools)).build()))
			.build();
		return new InMemoryTransport(server);
	}

	/**
	 * A server whose tool listings carry the given {@code ttlMs} and {@code cacheScope}.
	 */
	private static InMemoryTransport transport(long listTtlMs, CacheScope listCacheScope, McpSyncTool... tools) {
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test-server", "1").build())
			.feature(ToolsFeature.ofSync(InMemorySyncToolRepository.builder().tools(List.of(tools)).build(),
					McpJsonDefaults.getMapper(), McpJsonDefaults.getSchemaValidator(), listTtlMs, listCacheScope))
			.build();
		return new InMemoryTransport(server);
	}

	private static McpModernConnectionInfo info(String name) {
		return new McpModernConnectionInfo(name, CLIENT_INFO);
	}

	private static McpHttpStatusException httpStatus(int statusCode) {
		return new McpHttpStatusException(URI.create("http://localhost/mcp"), statusCode,
				HttpHeaders.of(Map.of(), (name, value) -> true), "");
	}

	/**
	 * Hands each request of a modern client to a modern server in the same JVM, with a
	 * JSON round trip in both directions so that the messages have their wire shape.
	 */
	private static final class InMemoryTransport implements McpClientTransport {

		private final McpServer server;

		private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

		private final List<String> methods = new CopyOnWriteArrayList<>();

		/** The user of each tool listing request, as the transport context names it. */
		private final List<String> callers = new CopyOnWriteArrayList<>();

		private InMemoryTransport(McpServer server) {
			this.server = server;
		}

		@Override
		public Flux<JSONRPCMessage> exchange(McpTransportRequest request) {
			return request.withRoundTimeout(Flux.deferContextual(ctx -> {
				this.methods.add(request.method());
				McpTransportContext context = ctx.getOrDefault(McpTransportContext.KEY, McpTransportContext.EMPTY);
				if (McpSchema.METHOD_TOOLS_LIST.equals(request.method())) {
					this.callers.add(String.valueOf(context.get("user")));
				}
				JsonRpc.JSONRPCRequest received = (JsonRpc.JSONRPCRequest) overTheWire(request.message());
				return this.server.handle(context, received).flatMapMany(response -> {
					if (response instanceof McpTransportResponse.Result result) {
						return Flux.just(result.response());
					}
					if (response instanceof McpTransportResponse.Error error) {
						return Flux.just(error.response());
					}
					return ((McpTransportResponse.Streaming) response).messages();
				}).map(this::overTheWire);
			}));
		}

		/** The MCP methods of the requests sent so far, in order. */
		private List<String> methods() {
			return List.copyOf(this.methods);
		}

		private List<String> callers() {
			return List.copyOf(this.callers);
		}

		private JSONRPCMessage overTheWire(JSONRPCMessage message) {
			try {
				return JsonRpc.deserializeMessage(this.jsonMapper, this.jsonMapper.writeValueAsString(message));
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

}
