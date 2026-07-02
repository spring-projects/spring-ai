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
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Map;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for exception handling in {@link AsyncMcpToolMethodCallback}.
 *
 * <p>
 * The contract mirrors {@code @Tool}: RuntimeExceptions are conveyed to the LLM as error
 * results; declared checked exceptions and Errors bubble up without reaching the LLM.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpToolMethodCallbackExceptionHandlingTests {

	@Test
	public void runtimeExceptionIsConvertedToErrorResult() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("runtimeExceptionTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("runtime-exception-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).assertNext(r -> {
			assertThat(r.isError()).isTrue();
			assertThat(((TextContent) r.content().get(0)).text()).contains("Runtime error: test");
		}).verifyComplete();
	}

	@Test
	public void runtimeExceptionSubclassIsConvertedToErrorResult() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("customRuntimeExceptionTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("custom-runtime-exception-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).assertNext(r -> {
			assertThat(r.isError()).isTrue();
			assertThat(((TextContent) r.content().get(0)).text()).contains("Custom runtime error: test");
		}).verifyComplete();
	}

	@Test
	public void nullPointerExceptionIsConvertedToErrorResult() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("nullPointerTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("null-pointer-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).assertNext(r -> {
			assertThat(r.isError()).isTrue();
			assertThat(((TextContent) r.content().get(0)).text()).contains("Null pointer: test");
		}).verifyComplete();
	}

	@Test
	public void illegalArgumentExceptionIsConvertedToErrorResult() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("illegalArgumentTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("illegal-argument-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).assertNext(r -> {
			assertThat(r.isError()).isTrue();
			assertThat(((TextContent) r.content().get(0)).text()).contains("Illegal argument: test");
		}).verifyComplete();
	}

	@Test
	public void declaredCheckedExceptionBubblesUp() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("checkedExceptionTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("checked-exception-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result)
			.expectErrorSatisfies(e -> assertThat(e).isInstanceOf(UndeclaredThrowableException.class)
				.cause()
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Business error: test"))
			.verify();
	}

	@Test
	public void errorBubblesUp() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("assertionErrorTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("assertion-error-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result)
			.expectErrorSatisfies(
					e -> assertThat(e).isInstanceOf(AssertionError.class).hasMessageContaining("Assertion error: test"))
			.verify();
	}

	@Test
	public void mcpErrorThrownDirectlyBubblesUp() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("mcpErrorTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mcp-error-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).expectError(McpError.class).verify();
	}

	@Test
	public void errorEmittedReactivelyBubblesUp() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("monoAssertionErrorTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mono-assertion-error-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result)
			.expectErrorSatisfies(
					e -> assertThat(e).isInstanceOf(AssertionError.class).hasMessageContaining("Reactive error: test"))
			.verify();
	}

	@Test
	public void mcpErrorEmittedReactivelyBubblesUp() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("monoMcpErrorTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mono-mcp-error-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).expectError(McpError.class).verify();
	}

	@Test
	public void runtimeExceptionFromMonoVoidIsConvertedToErrorResult() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("monoVoidRuntimeExceptionTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mono-void-runtime-exception-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).assertNext(r -> {
			assertThat(r.isError()).isTrue();
			assertThat(((TextContent) r.content().get(0)).text()).contains("Void error: test");
		}).verifyComplete();
	}

	@Test
	public void mcpErrorFromMonoVoidBubblesUp() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("monoVoidMcpErrorTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mono-void-mcp-error-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).expectError(McpError.class).verify();
	}

	@Test
	public void runtimeExceptionFromMonoCallToolResultIsConvertedToErrorResult() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("monoCallToolResultRuntimeExceptionTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mono-call-tool-result-runtime-exception-tool",
				Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).assertNext(r -> {
			assertThat(r.isError()).isTrue();
			assertThat(((TextContent) r.content().get(0)).text()).contains("CallToolResult error: test");
		}).verifyComplete();
	}

	@Test
	public void mcpErrorFromMonoCallToolResultBubblesUp() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("monoCallToolResultMcpErrorTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mono-call-tool-result-mcp-error-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).expectError(McpError.class).verify();
	}

	@Test
	public void successfulExecutionReturnsResult() throws Exception {
		AsyncMcpToolMethodCallback callback = callbackFor("successTool");
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("success-tool", Map.of("input", "test"));

		Mono<CallToolResult> result = callback.apply(exchange, request);

		StepVerifier.create(result).assertNext(r -> {
			assertThat(r.isError()).isFalse();
			assertThat(((TextContent) r.content().get(0)).text()).isEqualTo("Success: test");
		}).verifyComplete();
	}

	// --- helpers ---

	private AsyncMcpToolMethodCallback callbackFor(String methodName) throws Exception {
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod(methodName, String.class);
		return new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);
	}

	// --- exception types ---

	public static class BusinessException extends Exception {

		public BusinessException(String message) {
			super(message);
		}

	}

	public static class CustomRuntimeException extends RuntimeException {

		public CustomRuntimeException(String message) {
			super(message);
		}

	}

	// --- tool provider ---

	public static class ExceptionTestToolProvider {

		@McpTool(name = "runtime-exception-tool", description = "Throws RuntimeException")
		public Mono<String> runtimeExceptionTool(String input) {
			throw new RuntimeException("Runtime error: " + input);
		}

		@McpTool(name = "custom-runtime-exception-tool", description = "Throws CustomRuntimeException")
		public Mono<String> customRuntimeExceptionTool(String input) {
			throw new CustomRuntimeException("Custom runtime error: " + input);
		}

		@McpTool(name = "null-pointer-tool", description = "Throws NullPointerException")
		public Mono<String> nullPointerTool(String input) {
			throw new NullPointerException("Null pointer: " + input);
		}

		@McpTool(name = "illegal-argument-tool", description = "Throws IllegalArgumentException")
		public Mono<String> illegalArgumentTool(String input) {
			throw new IllegalArgumentException("Illegal argument: " + input);
		}

		@McpTool(name = "checked-exception-tool", description = "Throws declared checked exception")
		public Mono<String> checkedExceptionTool(String input) throws BusinessException {
			throw new BusinessException("Business error: " + input);
		}

		@McpTool(name = "assertion-error-tool", description = "Throws AssertionError")
		public Mono<String> assertionErrorTool(String input) {
			throw new AssertionError("Assertion error: " + input);
		}

		@McpTool(name = "mono-assertion-error-tool", description = "Emits AssertionError via Mono")
		public Mono<String> monoAssertionErrorTool(String input) {
			return Mono.error(new AssertionError("Reactive error: " + input));
		}

		@McpTool(name = "mcp-error-tool", description = "Throws McpError directly")
		public Mono<String> mcpErrorTool(String input) {
			throw McpError.builder(-32000).message("Protocol error: " + input).build();
		}

		@McpTool(name = "mono-mcp-error-tool", description = "Emits McpError via Mono")
		public Mono<String> monoMcpErrorTool(String input) {
			return Mono.error(McpError.builder(-32000).message("Protocol error: " + input).build());
		}

		@McpTool(name = "success-tool", description = "Returns a result")
		public Mono<String> successTool(String input) {
			return Mono.just("Success: " + input);
		}

		@McpTool(name = "mono-void-runtime-exception-tool", description = "Emits RuntimeException via Mono<Void>")
		public Mono<Void> monoVoidRuntimeExceptionTool(String input) {
			return Mono.error(new RuntimeException("Void error: " + input));
		}

		@McpTool(name = "mono-void-mcp-error-tool", description = "Emits McpError via Mono<Void>")
		public Mono<Void> monoVoidMcpErrorTool(String input) {
			return Mono.error(McpError.builder(-32000).message("Protocol error: " + input).build());
		}

		@McpTool(name = "mono-call-tool-result-runtime-exception-tool",
				description = "Emits RuntimeException via Mono<CallToolResult>")
		public Mono<CallToolResult> monoCallToolResultRuntimeExceptionTool(String input) {
			return Mono.error(new RuntimeException("CallToolResult error: " + input));
		}

		@McpTool(name = "mono-call-tool-result-mcp-error-tool", description = "Emits McpError via Mono<CallToolResult>")
		public Mono<CallToolResult> monoCallToolResultMcpErrorTool(String input) {
			return Mono.error(McpError.builder(-32000).message("Protocol error: " + input).build());
		}

	}

}
