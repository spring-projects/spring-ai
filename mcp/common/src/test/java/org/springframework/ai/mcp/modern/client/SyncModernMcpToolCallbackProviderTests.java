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
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.McpResponseLostException;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.McpTimeoutException;
import io.modelcontextprotocol.modern.client.transport.McpClientTransport;
import io.modelcontextprotocol.modern.client.transport.McpHttpStatusException;
import io.modelcontextprotocol.modern.client.transport.McpTransportRequest;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives {@link SyncModernMcpToolCallbackProvider} and {@link SyncModernMcpToolCallback}
 * through a real modern client and server, connected by an in-memory transport.
 *
 * @author Christian Tzolov
 */
class SyncModernMcpToolCallbackProviderTests {

	private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of());

	private static final Implementation CLIENT_INFO = Implementation.builder("test-client", "1").build();

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

	/** Answers with the caller's identity and the request's {@code _meta} entries. */
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

	private static ToolCallback callback(ToolCallback[] callbacks, String name) {
		return List.of(callbacks)
			.stream()
			.filter(callback -> callback.getToolDefinition().name().equals(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no tool named " + name));
	}

	@Test
	void listsToolsUnderTheConnectionPrefixAndCallsThem() {
		McpSyncClient client = McpClient.sync(transport(echoTool())).clientInfo(CLIENT_INFO).build();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("my-tickets"), client))
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("my_tickets_echo");
		assertThat(callbacks[0].getToolDefinition().description()).isEqualTo("Echoes the text");
		assertThat(((SyncModernMcpToolCallback) callbacks[0]).getOriginalToolName()).isEqualTo("echo");
		assertThat(callbacks[0].call("{\"text\":\"hi\"}")).contains("echo:hi");
	}

	@Test
	void structuredContentIsReturnedTogetherWithTheContent() {
		McpSyncClient client = McpClient.sync(transport(structuredTool())).build();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("meteo"), client))
			.build();

		String response = provider.getToolCallbacks()[0].call("{}");

		assertThat(response).contains("\"structuredContent\"").contains("\"temperature\":21").contains("sunny");
	}

	@Test
	void errorResultBecomesToolExecutionException() {
		McpSyncClient client = McpClient.sync(transport(failingTool())).build();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("shop"), client))
			.build();
		ToolCallback callback = provider.getToolCallbacks()[0];

		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("out of stock");
	}

	@Test
	void protocolErrorIsRethrown() {
		McpSyncClient client = McpClient.sync(transport(echoTool())).build();
		ToolCallback callback = SyncModernMcpToolCallback.builder()
			.mcpClient(client)
			.tool(Tool.builder("not-on-the-server", EMPTY_SCHEMA).build())
			.build();

		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(McpException.class);
	}

	@Test
	void timeoutBecomesToolExecutionException() {
		// accepts the request and never answers
		McpClientTransport hanging = request -> request.withRoundTimeout(Flux.never());
		McpSyncClient client = McpClient.sync(hanging).roundTimeout(Duration.ofMillis(200)).build();
		ToolCallback callback = SyncModernMcpToolCallback.builder()
			.mcpClient(client)
			.tool(Tool.builder("slow", EMPTY_SCHEMA).build())
			.build();

		// A failure in the client or on the way to the server is left to the tool error
		// handling of the application, as the timeout of a legacy client is. Only a
		// protocol error of the server is rethrown as it is.
		assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(ToolExecutionException.class)
			.hasCauseInstanceOf(McpTimeoutException.class);
	}

	@Test
	void toolContextBecomesRequestMetaWithoutTheReservedKeys() {
		McpSyncClient client = McpClient.sync(transport(whoAmITool())).build();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("idp"), client))
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
		McpSyncClient client = McpClient.sync(transport(whoAmITool())).build();
		List<String> calls = new CopyOnWriteArrayList<>();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("idp"), client))
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
	void composedToolCallCustomizersAreAppliedInOrder() {
		McpSyncClient client = McpClient.sync(transport(whoAmITool())).build();
		SyncModernMcpToolCallCustomizer first = (connectionInfo, toolSpec, toolContext) -> toolSpec
			.meta(Map.of("tenant", "set-by-first"));
		SyncModernMcpToolCallCustomizer second = (connectionInfo, toolSpec, toolContext) -> toolSpec
			.meta(Map.of("tenant", "set-by-second"));
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("idp"), client))
			.toolCallCustomizer(first.andThen(second))
			.build();

		// What the later customizer sets replaces what the earlier one set.
		assertThat(provider.getToolCallbacks()[0].call("{}")).contains("\"tenant\":\"set-by-second\"");
	}

	@Test
	void failingToolCallCustomizerBecomesToolExecutionException() {
		McpSyncClient client = McpClient.sync(transport(echoTool())).build();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("demo"), client))
			.toolCallCustomizer((connectionInfo, toolSpec, toolContext) -> {
				throw new IllegalStateException("no handler for this user");
			})
			.build();
		ToolCallback callback = provider.getToolCallbacks()[0];

		assertThatThrownBy(() -> callback.call("{\"text\":\"hi\"}")).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("no handler for this user");
	}

	@Test
	void perRequestConnectionBindsCallbacksToTheCallerOfThatRequest() {
		InMemoryTransport transport = transport(whoAmITool());
		AtomicReference<String> currentUser = new AtomicReference<>("alice");
		AtomicInteger clientsCreated = new AtomicInteger();

		// The factory runs inside getToolCallbacks(), on the calling thread. It captures
		// the caller there and builds a client that sends every request on that caller's
		// behalf, on whatever thread the tool later runs.
		SyncModernMcpConnection connection = SyncModernMcpConnection.perRequest(info("idp"), () -> {
			clientsCreated.incrementAndGet();
			McpTransportContext caller = McpTransportContext.create(Map.of("user", currentUser.get()));
			return McpClient.sync(transport).transportContextProvider(() -> caller).build();
		});
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(connection)
			.build();

		ToolCallback alicesCallback = provider.getToolCallbacks()[0];
		currentUser.set("bob");
		ToolCallback bobsCallback = provider.getToolCallbacks()[0];

		assertThat(clientsCreated).hasValue(2);
		assertThat(alicesCallback.call("{}")).contains("\"user\":\"alice\"");
		assertThat(bobsCallback.call("{}")).contains("\"user\":\"bob\"");
	}

	@Test
	void publicListingIsKeptForItsTtlAndCallsStillRunAsTheCallerOfTheRequest() {
		InMemoryTransport transport = transport(60_000, CacheScope.PUBLIC, whoAmITool());
		AtomicReference<String> currentUser = new AtomicReference<>("alice");
		AtomicLong nanos = new AtomicLong();
		SyncModernMcpConnection connection = SyncModernMcpConnection.perRequest(info("idp"), () -> {
			McpTransportContext caller = McpTransportContext.create(Map.of("user", currentUser.get()));
			return McpClient.sync(transport).transportContextProvider(() -> caller).build();
		});
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(connection)
			.nanoClock(nanos::get)
			.build();

		ToolCallback alicesCallback = provider.getToolCallbacks()[0];
		currentUser.set("bob");
		nanos.addAndGet(TimeUnit.SECONDS.toNanos(59));
		ToolCallback bobsCallback = provider.getToolCallbacks()[0];

		// Bob's request uses the listing kept from Alice's request.
		assertThat(transport.methods()).containsExactly(McpSchema.METHOD_TOOLS_LIST);
		// The kept listing only saves the listing: each call runs as its own caller.
		assertThat(alicesCallback.call("{}")).contains("\"user\":\"alice\"");
		assertThat(bobsCallback.call("{}")).contains("\"user\":\"bob\"");

		nanos.addAndGet(TimeUnit.SECONDS.toNanos(1));
		provider.getToolCallbacks();

		// The ttlMs has passed, so the tools are listed again.
		assertThat(transport.methods()).containsExactly(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_CALL,
				McpSchema.METHOD_TOOLS_CALL, McpSchema.METHOD_TOOLS_LIST);
	}

	@Test
	void privateListingIsNotKeptForAConnectionWithAClientForEachRequest() {
		InMemoryTransport transport = transport(60_000, CacheScope.PRIVATE, echoTool());
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.perRequest(info("tickets"), () -> McpClient.sync(transport).build()))
			.build();

		provider.getToolCallbacks();
		provider.getToolCallbacks();

		// A private listing may differ from caller to caller, whatever its ttlMs.
		assertThat(transport.methods()).containsExactly(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_LIST);
	}

	@Test
	void privateListingIsKeptByTheClientOfASharedConnection() {
		InMemoryTransport transport = transport(60_000, CacheScope.PRIVATE, echoTool());
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("tickets"), McpClient.sync(transport).build()))
			.build();

		provider.getToolCallbacks();
		provider.getToolCallbacks();

		// A shared client stands for one identity and keeps its own listing.
		assertThat(transport.methods()).containsExactly(McpSchema.METHOD_TOOLS_LIST);
	}

	@Test
	void publicListingWithoutTtlIsNotKept() {
		InMemoryTransport transport = transport(0, CacheScope.PUBLIC, echoTool());
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.perRequest(info("tickets"), () -> McpClient.sync(transport).build()))
			.build();

		provider.getToolCallbacks();
		provider.getToolCallbacks();

		assertThat(transport.methods()).containsExactly(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_LIST);
	}

	@Test
	void toolFilterDecidesWhichToolsAreExposed() {
		McpSyncClient client = McpClient.sync(transport(echoTool(), structuredTool())).build();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("mixed"), client))
			.toolFilter((connectionInfo, tool) -> connectionInfo.name().equals("mixed") && tool.name().equals("echo"))
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(1);
		assertThat(callback(callbacks, "mixed_echo")).isNotNull();
	}

	@Test
	void sameToolNameOnTwoConnectionsCollidesOnlyWithoutPrefix() {
		McpSyncClient first = McpClient.sync(transport(echoTool())).build();
		McpSyncClient second = McpClient.sync(transport(echoTool())).build();
		List<SyncModernMcpConnection> connections = List.of(SyncModernMcpConnection.shared(info("first"), first),
				SyncModernMcpConnection.shared(info("second"), second));

		ToolCallback[] prefixed = SyncModernMcpToolCallbackProvider.builder()
			.connections(connections)
			.build()
			.getToolCallbacks();
		assertThat(prefixed).extracting(callback -> callback.getToolDefinition().name())
			.containsExactly("first_echo", "second_echo");

		SyncModernMcpToolCallbackProvider unprefixed = SyncModernMcpToolCallbackProvider.builder()
			.connections(connections)
			.toolNamePrefixGenerator(McpModernToolNamePrefixGenerator.noPrefix())
			.build();
		assertThatThrownBy(unprefixed::getToolCallbacks).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("echo");
	}

	@Test
	void unreachableServerIsLeftOutAndTheOtherConnectionsStayUsable() {
		McpSyncClient reachable = McpClient.sync(transport(echoTool())).build();
		List<RuntimeException> failures = List.of(
				// a refused connection, as the synchronous client reports it
				Exceptions.propagate(new ConnectException("Connection refused")),
				new McpResponseLostException(McpSchema.METHOD_TOOLS_LIST, new IOException("Connection reset")),
				httpStatus(503));

		for (RuntimeException failure : failures) {
			McpSyncClient unreachable = McpClient.sync(failingWith(failure)).build();
			SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
				.connection(SyncModernMcpConnection.shared(info("down"), unreachable))
				.connection(SyncModernMcpConnection.shared(info("up"), reachable))
				.build();

			ToolCallback[] callbacks = provider.getToolCallbacks();

			assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name())
				.as("after " + failure)
				.containsExactly("up_echo");
		}
	}

	@Test
	void otherListingFailuresAreThrown() {
		List<RuntimeException> failures = List.of(
				// not authorized: the application must be able to ask for a new login
				httpStatus(401), httpStatus(403),
				// thrown by a transport hook, for example when a token cannot be obtained
				new IllegalStateException("login required"),
				// a wrong endpoint is a configuration error, not an outage
				httpStatus(404));

		for (RuntimeException failure : failures) {
			McpSyncClient client = McpClient.sync(failingWith(failure)).build();
			SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
				.connection(SyncModernMcpConnection.shared(info("idp"), client))
				.build();

			assertThatThrownBy(provider::getToolCallbacks).as("after " + failure).isSameAs(failure);
		}
	}

	@Test
	void unreachableServerIsNotAskedAgainUntilTheCoolDownHasPassed() {
		InMemoryTransport server = transport(echoTool());
		AtomicReference<RuntimeException> outage = new AtomicReference<>(httpStatus(503));
		AtomicInteger requests = new AtomicInteger();
		AtomicInteger clientsCreated = new AtomicInteger();
		McpClientTransport flaky = request -> {
			requests.incrementAndGet();
			return (outage.get() != null) ? Flux.error(outage.get()) : server.exchange(request);
		};
		AtomicLong nanos = new AtomicLong();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.perRequest(info("flaky"), () -> {
				clientsCreated.incrementAndGet();
				return McpClient.sync(flaky).build();
			}))
			.unreachableCoolDown(Duration.ofSeconds(30))
			.nanoClock(nanos::get)
			.build();

		assertThat(provider.getToolCallbacks()).isEmpty();
		assertThat(requests).hasValue(1);

		// The server is back, but it is left alone during the cool-down.
		outage.set(null);
		nanos.addAndGet(TimeUnit.SECONDS.toNanos(29));
		assertThat(provider.getToolCallbacks()).isEmpty();
		assertThat(requests).hasValue(1);
		assertThat(clientsCreated).hasValue(1);

		nanos.addAndGet(TimeUnit.SECONDS.toNanos(1));
		assertThat(provider.getToolCallbacks()).hasSize(1);
		assertThat(requests).hasValue(2);
	}

	@Test
	void serverThatDoesNotAnswerHoldsUpOnlyTheFirstCall() {
		AtomicInteger requests = new AtomicInteger();
		// accepts the request and never answers
		McpClientTransport hanging = request -> request.withRoundTimeout(Flux.defer(() -> {
			requests.incrementAndGet();
			return Flux.never();
		}));
		McpSyncClient client = McpClient.sync(hanging).roundTimeout(Duration.ofMillis(200)).build();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("hanging"), client))
			.connection(SyncModernMcpConnection.shared(info("up"), McpClient.sync(transport(echoTool())).build()))
			.build();

		// The first call waits for the timeout of the listing.
		assertThat(provider.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
			.containsExactly("up_echo");
		assertThat(requests).hasValue(1);

		// The following calls leave the server alone and are not held up.
		assertThat(provider.getToolCallbacks()).hasSize(1);
		assertThat(provider.getToolCallbacks()).hasSize(1);
		assertThat(requests).hasValue(1);
	}

	@Test
	void eachFailedListingStartsANewCoolDown() {
		AtomicInteger requests = new AtomicInteger();
		McpClientTransport down = request -> {
			requests.incrementAndGet();
			return Flux.error(httpStatus(503));
		};
		AtomicLong nanos = new AtomicLong();
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("down"), McpClient.sync(down).build()))
			.unreachableCoolDown(Duration.ofSeconds(30))
			.nanoClock(nanos::get)
			.build();

		provider.getToolCallbacks();
		nanos.addAndGet(TimeUnit.SECONDS.toNanos(30));
		provider.getToolCallbacks();
		assertThat(requests).hasValue(2);

		nanos.addAndGet(TimeUnit.SECONDS.toNanos(29));
		provider.getToolCallbacks();
		assertThat(requests).hasValue(2);
	}

	@Test
	void withoutCoolDownAnUnreachableServerIsAskedOnEveryCall() {
		InMemoryTransport server = transport(echoTool());
		AtomicReference<RuntimeException> outage = new AtomicReference<>(httpStatus(503));
		McpClientTransport flaky = request -> (outage.get() != null) ? Flux.error(outage.get())
				: server.exchange(request);
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.shared(info("flaky"), McpClient.sync(flaky).build()))
			.unreachableCoolDown(Duration.ZERO)
			.build();

		assertThat(provider.getToolCallbacks()).isEmpty();

		outage.set(null);
		assertThat(provider.getToolCallbacks()).hasSize(1);
	}

	@Test
	void paginatedListingIsReadCompletelyAndKeptAsOneListing() {
		McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test-server", "1").build())
			.feature(ToolsFeature.ofSync(
					InMemorySyncToolRepository.builder()
						.tools(List.of(echoTool(), structuredTool(), failingTool()))
						.pageSize(1)
						.build(),
					McpJsonDefaults.getMapper(), McpJsonDefaults.getSchemaValidator(), 60_000, CacheScope.PUBLIC))
			.build();
		InMemoryTransport transport = new InMemoryTransport(server);
		SyncModernMcpToolCallbackProvider provider = SyncModernMcpToolCallbackProvider.builder()
			.connection(SyncModernMcpConnection.perRequest(info("paged"), () -> McpClient.sync(transport).build()))
			.build();

		ToolCallback[] callbacks = provider.getToolCallbacks();

		assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name())
			.containsExactlyInAnyOrder("paged_echo", "paged_weather", "paged_fail");
		// one request for each page of one tool
		assertThat(transport.methods()).containsExactly(McpSchema.METHOD_TOOLS_LIST, McpSchema.METHOD_TOOLS_LIST,
				McpSchema.METHOD_TOOLS_LIST);

		// The pages are kept as one listing, so the next request reads none of them.
		assertThat(provider.getToolCallbacks()).hasSize(3);
		assertThat(transport.methods()).hasSize(3);
	}

	private static McpClientTransport failingWith(RuntimeException failure) {
		return request -> Flux.error(failure);
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

		private InMemoryTransport(McpServer server) {
			this.server = server;
		}

		@Override
		public Flux<JSONRPCMessage> exchange(McpTransportRequest request) {
			return request.withRoundTimeout(Flux.deferContextual(ctx -> {
				this.methods.add(request.method());
				McpTransportContext context = ctx.getOrDefault(McpTransportContext.KEY, McpTransportContext.EMPTY);
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
