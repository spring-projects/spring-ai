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

package org.springframework.ai.mcp.annotation.method.tool;

import java.lang.reflect.Method;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Verifies that {@link McpError} thrown by {@code @McpTool} methods propagates correctly
 * instead of being converted to an error {@link CallToolResult}.
 *
 * <p>
 * Covers all four concrete callback variants (sync/async stateful/stateless), the
 * wrapped-cause scenario where {@code callMethod()} wraps the original exception, and
 * reactive errors emitted after an async method returns.
 *
 * @author Dongliang Xie
 */
public class McpToolMethodCallbackMcpErrorHandlingTests {

	private static final McpError TEST_ERROR = new McpError(new JSONRPCError(-32000, "protocol-error", null));

	/**
	 * Provider with a tool method that throws {@link McpError} directly.
	 */
	private static class McpErrorToolProvider {

		@McpTool(description = "throws McpError")
		public String throwMcpError(String input) {
			throw TEST_ERROR;
		}

		@McpTool(description = "throws ordinary exception")
		public String throwOrdinaryException(String input) {
			throw new IllegalArgumentException("ordinary-error");
		}

		@McpTool(description = "returns Mono error with McpError")
		public Mono<String> monoMcpError(String input) {
			return Mono.error(TEST_ERROR);
		}

		@McpTool(description = "returns Flux error with McpError")
		public Flux<String> fluxMcpError(String input) {
			return Flux.error(TEST_ERROR);
		}

		@McpTool(description = "returns Publisher error with McpError")
		public Publisher<String> publisherMcpError(String input) {
			return Mono.error(TEST_ERROR);
		}

		@McpTool(description = "returns Mono error with wrapped McpError")
		public Mono<CallToolResult> monoWrappedMcpError(String input) {
			return Mono.error(new IllegalStateException("wrapped-error", TEST_ERROR));
		}

		@McpTool(description = "returns Flux error with wrapped McpError")
		public Flux<CallToolResult> fluxWrappedMcpError(String input) {
			return Flux.error(new IllegalStateException("wrapped-error", TEST_ERROR));
		}

		@McpTool(description = "returns Mono<Void> error with wrapped McpError")
		public Mono<Void> monoVoidWrappedMcpError(String input) {
			return Mono.error(new IllegalStateException("wrapped-error", TEST_ERROR));
		}

		@McpTool(description = "returns Mono error with ordinary exception")
		public Mono<String> monoOrdinaryException(String input) {
			return Mono.error(new IllegalArgumentException("ordinary-reactive-error"));
		}

	}

	// ---- Sync Stateful ----

	@Nested
	class SyncStatefulMcpErrorTests {

		@Test
		void mcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwMcpError", String.class);
			SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(McpError.class);
		}

		@Test
		void ordinaryExceptionShouldBecomeCallToolResult() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwOrdinaryException", String.class);
			SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			CallToolResult result = callback.apply(exchange, request);

			assertThat(result.isError()).isTrue();
			assertThat(result.content()).hasSize(1);
			assertThat(((TextContent) result.content().get(0)).text()).contains("ordinary-error");
		}

	}

	// ---- Sync Stateless ----

	@Nested
	class SyncStatelessMcpErrorTests {

		@Test
		void mcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwMcpError", String.class);
			SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			assertThatThrownBy(() -> callback.apply(context, request)).isInstanceOf(McpError.class);
		}

		@Test
		void ordinaryExceptionShouldBecomeCallToolResult() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwOrdinaryException", String.class);
			SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			CallToolResult result = callback.apply(context, request);

			assertThat(result.isError()).isTrue();
			assertThat(((TextContent) result.content().get(0)).text()).contains("ordinary-error");
		}

	}

	// ---- Async Stateful ----

	@Nested
	class AsyncStatefulMcpErrorTests {

		@Test
		void mcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwMcpError", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request)).expectError(McpError.class).verify();
		}

		@Test
		void ordinaryExceptionShouldBecomeCallToolResult() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwOrdinaryException", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request)).assertNext(result -> {
				assertThat(result.isError()).isTrue();
				assertThat(((TextContent) result.content().get(0)).text()).contains("ordinary-error");
			}).verifyComplete();
		}

		@Test
		void reactiveMonoMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("monoMcpError", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveFluxMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("fluxMcpError", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactivePublisherMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("publisherMcpError", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveWrappedMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("monoWrappedMcpError", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveFluxWrappedMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("fluxWrappedMcpError", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveVoidWrappedMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("monoVoidWrappedMcpError", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.VOID, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveOrdinaryExceptionShouldBecomeCallToolResult() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("monoOrdinaryException", String.class);
			AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

			McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(exchange, request)).assertNext(result -> {
				assertThat(result.isError()).isTrue();
				assertThat(((TextContent) result.content().get(0)).text()).contains("ordinary-reactive-error");
			}).verifyComplete();
		}

	}

	// ---- Async Stateless ----

	@Nested
	class AsyncStatelessMcpErrorTests {

		@Test
		void mcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwMcpError", String.class);
			AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(context, request)).expectError(McpError.class).verify();
		}

		@Test
		void ordinaryExceptionShouldBecomeCallToolResult() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("throwOrdinaryException", String.class);
			AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
				assertThat(result.isError()).isTrue();
				assertThat(((TextContent) result.content().get(0)).text()).contains("ordinary-error");
			}).verifyComplete();
		}

		@Test
		void reactiveMonoMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("monoMcpError", String.class);
			AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(context, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveFluxMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("fluxMcpError", String.class);
			AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(context, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveWrappedMcpErrorShouldPropagate() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("monoWrappedMcpError", String.class);
			AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(context, request))
				.expectErrorSatisfies(throwable -> assertThat(throwable).isSameAs(TEST_ERROR))
				.verify();
		}

		@Test
		void reactiveOrdinaryExceptionShouldBecomeCallToolResult() throws Exception {
			McpErrorToolProvider provider = new McpErrorToolProvider();
			Method method = McpErrorToolProvider.class.getMethod("monoOrdinaryException", String.class);
			AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT,
					method, provider);

			McpTransportContext context = mock(McpTransportContext.class);
			CallToolRequest request = new CallToolRequest("tool", Map.of("input", "test"));

			StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
				assertThat(result.isError()).isTrue();
				assertThat(((TextContent) result.content().get(0)).text()).contains("ordinary-reactive-error");
			}).verifyComplete();
		}

	}

}
