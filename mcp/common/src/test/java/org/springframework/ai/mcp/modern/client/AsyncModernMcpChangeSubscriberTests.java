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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.JsonRpc;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCMessage;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.CallToolResult;
import io.modelcontextprotocol.modern.McpSchema.Implementation;
import io.modelcontextprotocol.modern.McpSchema.TextContent;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpClient;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import io.modelcontextprotocol.modern.client.transport.McpClientTransport;
import io.modelcontextprotocol.modern.client.transport.McpTransportRequest;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.McpTransportResponse;
import io.modelcontextprotocol.modern.server.feature.McpChangeFeed;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.ai.mcp.modern.InMemorySyncToolRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link AsyncModernMcpChangeSubscriber}, against an in-memory modern server
 * that serves {@code subscriptions/listen}.
 *
 * @author Christian Tzolov
 */
class AsyncModernMcpChangeSubscriberTests {

	private static final Map<String, Object> EMPTY_SCHEMA = Map.of("type", "object", "properties", Map.of());

	private static final Duration REOPEN_DELAY = Duration.ofMillis(100);

	private final ListeningServer server = new ListeningServer();

	private final RecordingListener listener = new RecordingListener();

	private final AsyncModernMcpConnection connection = AsyncModernMcpConnection.perRequest(
			new McpModernConnectionInfo("demo", Implementation.builder("test-client", "1").build()),
			() -> McpClient.async(this.server).build());

	private final AsyncModernMcpToolCallbackProvider provider = AsyncModernMcpToolCallbackProvider.builder()
		.connection(this.connection)
		.build();

	private final AsyncModernMcpChangeSubscriber subscriber = AsyncModernMcpChangeSubscriber.builder()
		.connection(this.connection)
		.listener(this.provider)
		.listener(this.listener)
		.maxReopenDelay(REOPEN_DELAY)
		.build();

	@AfterEach
	void stopSubscriber() {
		this.subscriber.stop();
	}

	@Test
	void keptListingIsDroppedWhenTheServerReportsThatItsToolsChanged() {
		this.subscriber.start();
		await(() -> this.listener.missed.size() == 1);

		assertThat(this.provider.getToolCallbacks()).hasSize(1);
		assertThat(this.provider.getToolCallbacks()).hasSize(1);
		// The listing is public and may be kept for five minutes.
		assertThat(this.server.requests("tools/list")).isEqualTo(1);

		this.server.tools.add(tool("added"));

		await(() -> this.listener.changes.size() == 1);
		assertThat(this.listener.changes.get(0)).isInstanceOf(SubscriptionEvent.ToolsListChanged.class);
		assertThat(this.provider.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
			.containsExactlyInAnyOrder("demo_echo", "demo_added");
		assertThat(this.server.requests("tools/list")).isEqualTo(2);
	}

	@Test
	void streamThatEndsIsOpenedAgainAndTheKeptListingIsDropped() {
		this.subscriber.start();
		await(() -> this.listener.missed.size() == 1);
		assertThat(this.provider.getToolCallbacks()).hasSize(1);

		this.server.cutListenStreams();

		// Once for the stream that ended and once for the stream that replaced it.
		await(() -> this.listener.missed.size() == 3);
		assertThat(this.server.requests("subscriptions/listen")).isEqualTo(2);
		// Changes may have been missed in between, so the tools are listed again.
		assertThat(this.provider.getToolCallbacks()).hasSize(1);
		assertThat(this.server.requests("tools/list")).isEqualTo(2);

		// The new stream reports changes.
		this.server.tools.add(tool("added"));
		await(() -> this.listener.changes.size() == 1);
	}

	@Test
	void serverThatCannotBeReachedDoesNotFailTheStartAndIsAskedAgain() {
		this.server.down = true;

		assertThatCode(this.subscriber::start).doesNotThrowAnyException();
		await(() -> this.server.requests("subscriptions/listen") >= 2);
		assertThat(this.listener.changes).isEmpty();
		// A stream that cannot be established must not make the provider drop a kept
		// listing again and again.
		assertThat(this.listener.missed).isEmpty();

		this.server.down = false;
		await(() -> {
			this.server.tools.add(tool("added-" + System.nanoTime()));
			return !this.listener.changes.isEmpty();
		});
	}

	@Test
	void pausesBeforeTheStreamIsOpenedAgainDoubleUpToTheMaximum() throws InterruptedException {
		List<Long> pauses = new CopyOnWriteArrayList<>();
		AsyncModernMcpChangeSubscriber backingOff = AsyncModernMcpChangeSubscriber.builder()
			.connection(this.connection)
			.listener(this.listener)
			.initialReopenDelay(Duration.ofMillis(10))
			.maxReopenDelay(Duration.ofMillis(400))
			.schedulerFactory(() -> new ScheduledThreadPoolExecutor(1) {
				@Override
				public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
					pauses.add(unit.toMillis(delay));
					return super.schedule(command, delay, unit);
				}
			})
			.build();
		this.server.down = true;
		try {
			backingOff.start();

			await(() -> pauses.size() >= 7);
			assertThat(pauses.subList(0, 7)).containsExactly(10L, 20L, 40L, 80L, 160L, 320L, 400L);
			// A stream that cannot be established reports nothing, and it changes
			// nothing about what is kept.
			assertThat(this.listener.missed).isEmpty();

			// A stream that the server accepts only to end it does not start the pauses
			// over: such a server is not asked more often than one that stays away.
			this.server.down = false;
			await(() -> this.listener.established.size() == 1);
			int pausesBeforeEarlyEnd = pauses.size();
			this.server.cutListenStreams();
			await(() -> pauses.size() > pausesBeforeEarlyEnd);
			assertThat(pauses.get(pausesBeforeEarlyEnd)).isEqualTo(400L);

			// Once a stream has stayed up for the maximum delay, the pauses start over.
			await(() -> this.listener.established.size() == 2);
			Thread.sleep(500);
			int pausesBeforeLateEnd = pauses.size();
			this.server.cutListenStreams();
			await(() -> pauses.size() > pausesBeforeLateEnd);
			assertThat(pauses.get(pausesBeforeLateEnd)).isEqualTo(10L);
		}
		finally {
			backingOff.stop();
		}
	}

	@Test
	void clientThatCannotBeBuiltDoesNotFailTheStartAndIsTriedAgain() {
		AtomicInteger attempts = new AtomicInteger();
		AsyncModernMcpConnection failingAtFirst = AsyncModernMcpConnection.perRequest(this.connection.info(), () -> {
			if (attempts.incrementAndGet() < 3) {
				throw new IllegalStateException("no caller outside of a request");
			}
			return McpClient.async(this.server).build();
		});
		AsyncModernMcpChangeSubscriber retrying = AsyncModernMcpChangeSubscriber.builder()
			.connection(failingAtFirst)
			.listener(this.listener)
			.maxReopenDelay(REOPEN_DELAY)
			.build();
		try {
			assertThatCode(retrying::start).doesNotThrowAnyException();

			await(() -> this.listener.established.size() == 1);
			assertThat(attempts).hasValue(3);
			// Only the stream that was established is reported to the listeners.
			assertThat(this.listener.missed).hasSize(1);
		}
		finally {
			retrying.stop();
		}
	}

	@Test
	void restartedSubscriberKeepsOneStream() throws InterruptedException {
		this.subscriber.start();
		await(() -> this.listener.established.size() == 1);

		this.subscriber.stop();
		this.subscriber.start();
		await(() -> this.listener.established.size() == 2);

		this.server.tools.add(tool("added"));
		await(() -> !this.listener.changes.isEmpty());
		Thread.sleep(3 * REOPEN_DELAY.toMillis());

		// One stream after the restart: the change is reported once.
		assertThat(this.listener.changes).hasSize(1);
		assertThat(this.server.requests("subscriptions/listen")).isEqualTo(2);
	}

	@Test
	void subscriberWithoutListenersOpensNoStreamUntilAListenerIsAdded() throws InterruptedException {
		AsyncModernMcpChangeSubscriber unheard = AsyncModernMcpChangeSubscriber.builder()
			.connection(this.connection)
			.build();
		try {
			unheard.start();
			Thread.sleep(3 * REOPEN_DELAY.toMillis());

			assertThat(unheard.isRunning()).isTrue();
			assertThat(this.server.requests("subscriptions/listen")).isZero();

			// A listener that is created late, such as a lazy bean, is not left unheard.
			unheard.addListener(this.listener);
			await(() -> this.listener.established.size() == 1);
			assertThat(this.server.requests("subscriptions/listen")).isEqualTo(1);

			// The streams are open: another listener opens no second one.
			unheard.addListener(this.provider);
			Thread.sleep(3 * REOPEN_DELAY.toMillis());
			assertThat(this.server.requests("subscriptions/listen")).isEqualTo(1);
		}
		finally {
			unheard.stop();
		}
	}

	@Test
	void listenStreamOfAScopedConnectionIsOpenedWithoutAskingTheScope() {
		AsyncModernMcpConnection scoped = AsyncModernMcpConnection.scoped(this.connection.info(),
				() -> McpClient.async(this.server).build(), (connectionInfo, clientFactory) -> {
					throw new IllegalStateException("There is no user to select a client for");
				});
		AsyncModernMcpChangeSubscriber ofScoped = AsyncModernMcpChangeSubscriber.builder()
			.connection(scoped)
			.listener(this.listener)
			.maxReopenDelay(REOPEN_DELAY)
			.build();
		try {
			ofScoped.start();
			await(() -> this.listener.established.size() == 1);
			assertThat(this.server.requests("subscriptions/listen")).isEqualTo(1);
		}
		finally {
			ofScoped.stop();
		}
	}

	@Test
	void listenerAddedToAStoppedSubscriberOpensNoStream() throws InterruptedException {
		AsyncModernMcpChangeSubscriber stopped = AsyncModernMcpChangeSubscriber.builder()
			.connection(this.connection)
			.build();

		stopped.addListener(this.listener);
		Thread.sleep(3 * REOPEN_DELAY.toMillis());

		assertThat(stopped.isRunning()).isFalse();
		assertThat(this.server.requests("subscriptions/listen")).isZero();
	}

	@Test
	void toolCallbackProviderIsToldBeforeTheOtherListenersWheneverItIsAdded() {
		AsyncModernMcpChangeListener other = (connectionInfo, event) -> Mono.empty();
		AsyncModernMcpChangeSubscriber providerLast = AsyncModernMcpChangeSubscriber.builder()
			.connection(this.connection)
			.listener(this.listener)
			.listener(other)
			.listener(this.provider)
			.build();
		// The others keep the order they were added in.
		assertThat(providerLast).extracting("streams.listeners")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsExactly(this.provider, this.listener, other);

		AsyncModernMcpChangeSubscriber providerLate = AsyncModernMcpChangeSubscriber.builder()
			.connection(this.connection)
			.listener(this.listener)
			.build();
		providerLate.addListener(other);
		providerLate.addListener(this.provider);
		assertThat(providerLate).extracting("streams.listeners")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsExactly(this.provider, this.listener, other);
	}

	@Test
	void stoppedSubscriberClosesItsStreamAndDoesNotOpenItAgain() throws InterruptedException {
		this.subscriber.start();
		await(() -> this.listener.missed.size() == 1);

		this.subscriber.stop();
		assertThat(this.subscriber.isRunning()).isFalse();
		this.server.tools.add(tool("added"));
		this.server.cutListenStreams();
		Thread.sleep(3 * REOPEN_DELAY.toMillis());

		assertThat(this.listener.changes).isEmpty();
		assertThat(this.server.requests("subscriptions/listen")).isEqualTo(1);
	}

	@Test
	void failingListenerDoesNotEndTheStream() {
		List<String> seen = new CopyOnWriteArrayList<>();
		this.subscriber.addListener((connectionInfo, event) -> {
			seen.add(connectionInfo.name());
			throw new IllegalStateException("listener failure");
		});
		this.subscriber.start();
		await(() -> this.listener.missed.size() == 1);

		this.server.tools.add(tool("first"));
		await(() -> seen.size() == 1);
		this.server.tools.add(tool("second"));
		await(() -> seen.size() == 2);

		assertThat(this.server.requests("subscriptions/listen")).isEqualTo(1);
	}

	@Test
	void listenerWhoseMonoFailsDoesNotKeepTheOthersFromBeingTold() {
		AsyncModernMcpChangeSubscriber failingFirst = AsyncModernMcpChangeSubscriber.builder()
			.connection(this.connection)
			.listener((connectionInfo, event) -> Mono.error(new IllegalStateException("listener failure")))
			.listener(this.listener)
			.maxReopenDelay(REOPEN_DELAY)
			.build();
		try {
			failingFirst.start();
			await(() -> this.listener.missed.size() == 1);

			this.server.tools.add(tool("first"));
			await(() -> this.listener.changes.size() == 1);
			this.server.tools.add(tool("second"));
			await(() -> this.listener.changes.size() == 2);

			assertThat(this.server.requests("subscriptions/listen")).isEqualTo(1);
		}
		finally {
			failingFirst.stop();
		}
	}

	@Test
	void reportsOfAConnectionAreToldOneAtATime() throws InterruptedException {
		Sinks.Empty<Void> firstDone = Sinks.empty();
		List<String> seen = new CopyOnWriteArrayList<>();
		// Told after the recording listener, and slow to deal with the first change.
		this.subscriber.addListener((connectionInfo, event) -> {
			seen.add("told");
			return seen.size() == 1 ? firstDone.asMono() : Mono.empty();
		});
		this.subscriber.start();
		await(() -> this.listener.missed.size() == 1);

		this.server.tools.add(tool("first"));
		await(() -> seen.size() == 1);
		this.server.tools.add(tool("second"));

		// The second change waits for the listeners to be done with the first one.
		Thread.sleep(3 * REOPEN_DELAY.toMillis());
		assertThat(this.listener.changes).hasSize(1);

		firstDone.tryEmitEmpty();
		await(() -> this.listener.changes.size() == 2 && seen.size() == 2);
		// The stream itself was never held up.
		assertThat(this.server.requests("subscriptions/listen")).isEqualTo(1);
	}

	@Test
	void streamListensForToolsAndForPromptsAndResourcesIfAskedFor() {
		this.subscriber.start();
		await(() -> this.server.listenRequests.size() == 1);
		assertThat(this.server.listenRequests.get(0)).contains("toolsListChanged")
			.doesNotContain("promptsListChanged")
			.doesNotContain("resourcesListChanged");
		this.subscriber.stop();

		AsyncModernMcpChangeSubscriber everything = AsyncModernMcpChangeSubscriber.builder()
			.connection(this.connection, true, true)
			.listener(this.listener)
			.maxReopenDelay(REOPEN_DELAY)
			.build();
		try {
			everything.start();
			await(() -> this.server.listenRequests.size() == 2);
			assertThat(this.server.listenRequests.get(1)).contains("toolsListChanged")
				.contains("promptsListChanged")
				.contains("resourcesListChanged");
		}
		finally {
			everything.stop();
		}
	}

	@Test
	void subscriberWithoutConnectionsStartsNothing() {
		AsyncModernMcpChangeSubscriber empty = AsyncModernMcpChangeSubscriber.builder().build();

		empty.start();

		assertThat(empty.isRunning()).isTrue();
		assertThat(empty.connectionNames()).isEmpty();
		empty.stop();
	}

	private static McpSyncTool tool(String name) {
		return McpSyncTool.of(Tool.builder(name, EMPTY_SCHEMA).description("A tool").build(),
				(ctx, request) -> McpSyncResponse
					.result(CallToolResult.builder().addContent(TextContent.builder(name).build()).build()));
	}

	private static void await(BooleanSupplier condition) {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("the condition was not met within 10 seconds");
			}
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError(ex);
			}
		}
	}

	private static final class RecordingListener implements AsyncModernMcpChangeListener {

		private final List<SubscriptionEvent> changes = new CopyOnWriteArrayList<>();

		private final List<String> missed = new CopyOnWriteArrayList<>();

		private final List<String> established = new CopyOnWriteArrayList<>();

		@Override
		public Mono<Void> changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event) {
			return Mono.fromRunnable(() -> this.changes.add(event));
		}

		@Override
		public Mono<Void> changesMayBeMissed(McpModernConnectionInfo connectionInfo) {
			return Mono.fromRunnable(() -> this.missed.add(connectionInfo.name()));
		}

		@Override
		public Mono<Void> streamEstablished(McpModernConnectionInfo connectionInfo) {
			return Mono.fromRunnable(() -> this.established.add(connectionInfo.name()));
		}

	}

	/**
	 * An in-memory modern server with one tool, a public tool listing that may be kept
	 * for five minutes, and listen streams that can be cut.
	 */
	private static final class ListeningServer implements McpClientTransport {

		private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

		private final McpChangeFeed changeFeed = new McpChangeFeed();

		private final InMemorySyncToolRepository tools = InMemorySyncToolRepository.builder()
			.tool(tool("echo"))
			.changeBroadcaster(this.changeFeed)
			.build();

		private final McpServer server = McpServer.builder()
			.serverInfo(Implementation.builder("test-server", "1").build())
			.feature(ToolsFeature.ofSync(this.tools, McpJsonDefaults.getMapper(), McpJsonDefaults.getSchemaValidator(),
					Duration.ofMinutes(5).toMillis(), CacheScope.PUBLIC))
			.subscriptions(this.changeFeed)
			.build();

		private final List<String> methods = new CopyOnWriteArrayList<>();

		/** The listen requests received so far, as JSON. */
		private final List<String> listenRequests = new CopyOnWriteArrayList<>();

		private volatile Sinks.One<Boolean> cut = Sinks.one();

		private volatile boolean down;

		@Override
		public Flux<JSONRPCMessage> exchange(McpTransportRequest request) {
			return request.withRoundTimeout(Flux.deferContextual(ctx -> {
				this.methods.add(request.method());
				if (this.down) {
					return Flux.error(new UncheckedIOException(new ConnectException("Connection refused")));
				}
				McpTransportContext context = ctx.getOrDefault(McpTransportContext.KEY, McpTransportContext.EMPTY);
				JsonRpc.JSONRPCRequest received = (JsonRpc.JSONRPCRequest) overTheWire(request.message());
				if ("subscriptions/listen".equals(request.method())) {
					this.listenRequests.add(json(request.message()));
				}
				Flux<JSONRPCMessage> messages = this.server.handle(context, received).flatMapMany(response -> {
					if (response instanceof McpTransportResponse.Result result) {
						return Flux.just(result.response());
					}
					if (response instanceof McpTransportResponse.Error error) {
						return Flux.just(error.response());
					}
					return ((McpTransportResponse.Streaming) response).messages();
				}).map(this::overTheWire);
				return "subscriptions/listen".equals(request.method()) ? messages.takeUntilOther(this.cut.asMono())
						: messages;
			}));
		}

		/** Ends the open listen streams without a result, as a broken connection does. */
		private void cutListenStreams() {
			Sinks.One<Boolean> current = this.cut;
			this.cut = Sinks.one();
			current.tryEmitValue(true);
		}

		private long requests(String method) {
			return this.methods.stream().filter(method::equals).count();
		}

		private JSONRPCMessage overTheWire(JSONRPCMessage message) {
			try {
				return JsonRpc.deserializeMessage(this.jsonMapper, json(message));
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		private String json(JSONRPCMessage message) {
			try {
				return this.jsonMapper.writeValueAsString(message);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

}
