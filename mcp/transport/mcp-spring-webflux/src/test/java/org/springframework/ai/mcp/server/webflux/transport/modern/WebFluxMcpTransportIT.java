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

package org.springframework.ai.mcp.server.webflux.transport.modern;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CallToolOutcome;
import io.modelcontextprotocol.modern.McpSchema.CallToolRequest;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunctions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WebFluxMcpTransport} over a Reactor Netty server.
 *
 * @author Dariusz Jędrzejczyk
 */
@Timeout(30)
class WebFluxMcpTransportIT {

	private final CountDownLatch slowToolStarted = new CountDownLatch(1);

	private final CountDownLatch slowToolReleased = new CountDownLatch(1);

	private final CountDownLatch slowToolInterrupted = new CountDownLatch(1);

	private final CountDownLatch listenCancelled = new CountDownLatch(1);

	private DisposableServer httpServer;

	private WebClient client;

	@BeforeEach
	void setUp() {
		Flux<ServerChange> changes = Flux.<ServerChange>never().doOnCancel(this.listenCancelled::countDown);
		McpServer server = McpServer.builder()
			.serverInfo(ModernRequests.SERVER_INFO)
			.feature(ToolsFeature.ofSync(new TestTools()))
			.subscriptions(() -> changes)
			.build();
		WebFluxMcpTransport transport = WebFluxMcpTransport.builder(server).build();

		ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(
				RouterFunctions.toHttpHandler(transport.getRouterFunction()));
		this.httpServer = HttpServer.create().port(0).handle(adapter).bindNow();
		this.client = WebClient.create("http://127.0.0.1:" + this.httpServer.port());
	}

	@AfterEach
	void tearDown() {
		this.slowToolReleased.countDown();
		this.httpServer.disposeNow();
	}

	@Test
	void syncHandlerRunsOffTheEventLoop() {
		String body = callTool("thread").retrieve().bodyToMono(String.class).block();

		JsonAssertions.assertThatJson(body)
			.inPath("$.result.content[0].text")
			.asString()
			.startsWith("boundedElastic")
			.endsWith("|false");
	}

	@Test
	void eventStreamStartsBeforeTheFirstMessage() throws InterruptedException {
		ResponseEntity<Flux<ServerSentEvent<String>>> response = callTool("slow").retrieve()
			.toEntityFlux(ModernRequests.SSE_TYPE)
			.block(Duration.ofSeconds(5));

		// The tool is still running: status and headers went out ahead of any message.
		assertThat(this.slowToolStarted.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();

		this.slowToolReleased.countDown();
		List<ServerSentEvent<String>> events = response.getBody().collectList().block(Duration.ofSeconds(5));
		assertThat(events).hasSize(2);
		assertThat(events.get(0).data()).isNull();
		JsonAssertions.assertThatJson(events.get(1).data()).inPath("$.result.content[0].text").isEqualTo("released");
	}

	@Test
	void disconnectInterruptsBlockingHandler() throws InterruptedException {
		List<ServerSentEvent<String>> events = new CopyOnWriteArrayList<>();
		Disposable subscription = callTool("slow").retrieve()
			.bodyToFlux(ModernRequests.SSE_TYPE)
			.subscribe(events::add);
		assertThat(this.slowToolStarted.await(5, TimeUnit.SECONDS)).isTrue();

		subscription.dispose();

		assertThat(this.slowToolInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(events).allMatch(event -> event.data() == null);
	}

	@Test
	void disconnectCancelsListenStream() throws InterruptedException {
		CountDownLatch acknowledged = new CountDownLatch(1);
		Disposable subscription = post(McpSchema.METHOD_SUBSCRIPTIONS_LISTEN, ModernRequests.listen(1)).retrieve()
			.bodyToFlux(ModernRequests.SSE_TYPE)
			.filter(event -> event.data() != null && event.data().contains("acknowledged"))
			.subscribe(event -> acknowledged.countDown());
		assertThat(acknowledged.await(5, TimeUnit.SECONDS)).isTrue();

		subscription.dispose();

		assertThat(this.listenCancelled.await(5, TimeUnit.SECONDS)).isTrue();
	}

	private WebClient.RequestHeadersSpec<?> callTool(String tool) {
		return post(McpSchema.METHOD_TOOLS_CALL, ModernRequests.toolsCall(1, tool, Map.of())).header("Mcp-Name", tool);
	}

	private WebClient.RequestHeadersSpec<?> post(String method, String body) {
		return this.client.post()
			.uri("/mcp")
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
			.header("Mcp-Method", method)
			.header("MCP-Protocol-Version", McpSchema.LATEST_PROTOCOL_VERSION)
			.bodyValue(body);
	}

	private final class TestTools implements McpSyncToolRepository {

		@Override
		public ToolsPage list(McpRequestContext ctx, String cursor) {
			return ToolsPage.of(ModernRequests.tools("thread", "slow"));
		}

		@Override
		public Tool find(McpRequestContext ctx, String name) {
			return ModernRequests.tool(name);
		}

		@Override
		public McpSyncResponse<CallToolOutcome> call(McpRequestContext ctx, CallToolRequest request) {
			if ("thread".equals(request.name())) {
				return McpSyncResponse
					.result(ModernRequests.text(Thread.currentThread().getName() + "|" + ctx.isBlocking()));
			}
			return McpSyncResponse.streaming(notifier -> {
				WebFluxMcpTransportIT.this.slowToolStarted.countDown();
				try {
					WebFluxMcpTransportIT.this.slowToolReleased.await();
				}
				catch (InterruptedException ex) {
					WebFluxMcpTransportIT.this.slowToolInterrupted.countDown();
					Thread.currentThread().interrupt();
				}
				return ModernRequests.text("released");
			});
		}

	}

}
