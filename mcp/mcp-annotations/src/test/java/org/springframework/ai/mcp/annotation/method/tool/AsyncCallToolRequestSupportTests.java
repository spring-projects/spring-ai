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

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for CallToolRequest parameter support in async MCP tools.
 *
 * @author Christian Tzolov
 */
public class AsyncCallToolRequestSupportTests {

	@Test
	public void testAsyncDynamicToolWithCallToolRequest() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncDynamicTool", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-dynamic-tool",
				Map.of("action", "analyze", "data", "test-data"));

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Async processed action: analyze for tool: async-dynamic-tool");
		}).verifyComplete();
	}

	@Test
	public void testAsyncDynamicToolMissingRequiredParameter() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncDynamicTool", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-dynamic-tool", Map.of("data", "test-data")); // Missing
																											// 'action'
																											// parameter

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isTrue();
			assertThat(result.content()).hasSize(1);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Missing required 'action' parameter");
		}).verifyComplete();
	}

	@Test
	public void testAsyncErrorToolWithCallToolRequest() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncErrorTool", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-error-tool", Map.of("data", "test"));

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		// When a method returns Mono.error(), it propagates as an error
		StepVerifier.create(resultMono)
			.expectErrorMatches(throwable -> throwable instanceof RuntimeException
					&& throwable.getMessage().contains("Async tool execution failed"))
			.verify();
	}

	@Test
	public void testAsyncMixedParametersTool() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncMixedParamsTool", CallToolRequest.class,
				String.class, Integer.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-mixed-params-tool",
				Map.of("requiredParam", "test-value", "optionalParam", 42, "extraParam", "extra"));

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Async Required: test-value, Optional: 42, Total args: 3, Tool: async-mixed-params-tool");
		}).verifyComplete();
	}

	@Test
	public void testAsyncMixedParametersToolWithNullOptional() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncMixedParamsTool", CallToolRequest.class,
				String.class, Integer.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-mixed-params-tool", Map.of("requiredParam", "test-value"));

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Async Required: test-value, Optional: 0, Total args: 1, Tool: async-mixed-params-tool");
		}).verifyComplete();
	}

	@Test
	public void testAsyncSchemaValidatorTool() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncValidateSchema", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);

		// Test with valid schema
		CallToolRequest validRequest = new CallToolRequest("async-schema-validator",
				Map.of("data", "test-data", "format", "json"));

		Mono<CallToolResult> validResultMono = callback.apply(exchange, validRequest);

		StepVerifier.create(validResultMono).assertNext(result -> {
			assertThat(result.isError()).isFalse();
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Async schema validation successful for: async-schema-validator");
		}).verifyComplete();

		// Test with invalid schema
		CallToolRequest invalidRequest = new CallToolRequest("async-schema-validator", Map.of("data", "test-data")); // Missing
																														// 'format'

		Mono<CallToolResult> invalidResultMono = callback.apply(exchange, invalidRequest);

		StepVerifier.create(invalidResultMono).assertNext(result -> {
			assertThat(result.isError()).isTrue();
			assertThat(((TextContent) result.content().get(0)).text()).contains("Async schema validation failed");
		}).verifyComplete();
	}

	@Test
	public void testAsyncStructuredOutputWithCallToolRequest() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncStructuredOutputTool",
				CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.STRUCTURED, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-structured-output-tool", Map.of("input", "test-message"));

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.structuredContent()).isNotNull();
			assertThat((Map<String, Object>) result.structuredContent()).containsEntry("message", "test-message");
			assertThat((Map<String, Object>) result.structuredContent()).containsEntry("value", 42);
		}).verifyComplete();
	}

	@Test
	public void testAsyncVoidToolWithCallToolRequest() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncVoidTool", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.VOID, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-void-tool", Map.of("action", "process"));

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			// Void methods should return "Done"
			assertThat(((TextContent) result.content().get(0)).text()).contains("Done");
		}).verifyComplete();
	}

	@Test
	public void testAsyncCallToolRequestParameterInjection() throws Exception {
		// Test that CallToolRequest is properly injected as a parameter
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncDynamicTool", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("async-dynamic-tool", Map.of("action", "test", "data", "sample"));

		Mono<CallToolResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			// The tool should have access to the full request including the tool name
			assertThat(((TextContent) result.content().get(0)).text()).contains("tool: async-dynamic-tool");
		}).verifyComplete();
	}

	@Test
	public void testAsyncNullRequest() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncDynamicTool", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);

		Mono<CallToolResult> resultMono = callback.apply(exchange, null);

		StepVerifier.create(resultMono).expectError(IllegalArgumentException.class).verify();
	}

	@Test
	public void testAsyncIsExchangeType() throws Exception {
		AsyncCallToolRequestTestProvider provider = new AsyncCallToolRequestTestProvider();
		Method method = AsyncCallToolRequestTestProvider.class.getMethod("asyncDynamicTool", CallToolRequest.class);
		AsyncMcpToolMethodCallback callback = new AsyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		// Test that McpAsyncServerExchange is recognized as exchange type
		assertThat(callback.isExchangeOrContextType(McpAsyncServerExchange.class)).isTrue();

		// Test that other types are not recognized as exchange type
		assertThat(callback.isExchangeOrContextType(String.class)).isFalse();
		assertThat(callback.isExchangeOrContextType(Integer.class)).isFalse();
		assertThat(callback.isExchangeOrContextType(Object.class)).isFalse();
	}

	private static class AsyncCallToolRequestTestProvider {

		/**
		 * Async tool that only takes CallToolRequest - for fully dynamic handling
		 */
		@McpTool(name = "async-dynamic-tool", description = "Async fully dynamic tool")
		public Mono<CallToolResult> asyncDynamicTool(CallToolRequest request) {
			// Access full request details
			String toolName = request.name();
			Map<String, Object> arguments = request.arguments();

			// Custom validation
			if (!arguments.containsKey("action")) {
				return Mono.just(CallToolResult.builder()
					.isError(true)
					.addTextContent("Missing required 'action' parameter")
					.build());
			}

			String action = (String) arguments.get("action");
			return Mono.just(CallToolResult.builder()
				.addTextContent("Async processed action: " + action + " for tool: " + toolName)
				.build());
		}

		/**
		 * Async tool with CallToolRequest and Exchange parameters
		 */
		@McpTool(name = "async-context-aware-tool", description = "Async tool with context and request")
		public Mono<CallToolResult> asyncContextAwareTool(McpAsyncServerExchange exchange, CallToolRequest request) {
			// Exchange is available for context
			Map<String, Object> arguments = request.arguments();

			return Mono.just(CallToolResult.builder()
				.addTextContent("Async Exchange available: " + (exchange != null) + ", Args: " + arguments.size())
				.build());
		}

		/**
		 * Async tool with mixed parameters - CallToolRequest plus regular parameters
		 */
		@McpTool(name = "async-mixed-params-tool", description = "Async tool with mixed parameters")
		public Mono<CallToolResult> asyncMixedParamsTool(CallToolRequest request,
				@McpToolParam(description = "Required string parameter", required = true) String requiredParam,
				@McpToolParam(description = "Optional integer parameter", required = false) Integer optionalParam) {

			Map<String, Object> allArguments = request.arguments();

			return Mono.just(CallToolResult.builder()
				.addTextContent(String.format("Async Required: %s, Optional: %d, Total args: %d, Tool: %s",
						requiredParam, optionalParam != null ? optionalParam : 0, allArguments.size(), request.name()))
				.build());
		}

		/**
		 * Async tool that validates custom schema from CallToolRequest
		 */
		@McpTool(name = "async-schema-validator", description = "Async validates against custom schema")
		public Mono<CallToolResult> asyncValidateSchema(CallToolRequest request) {
			Map<String, Object> arguments = request.arguments();

			// Custom schema validation logic
			boolean hasRequiredFields = arguments.containsKey("data") && arguments.containsKey("format");

			if (!hasRequiredFields) {
				return Mono.just(CallToolResult.builder()
					.isError(true)
					.addTextContent("Async schema validation failed: missing required fields 'data' and 'format'")
					.build());
			}

			return Mono.just(CallToolResult.builder()
				.addTextContent("Async schema validation successful for: " + request.name())
				.build());
		}

		/**
		 * Regular async tool without CallToolRequest for comparison
		 */
		@McpTool(name = "async-regular-tool", description = "Regular async tool without CallToolRequest")
		public Mono<String> asyncRegularTool(String input, int number) {
			return Mono.just("Async Regular: " + input + " - " + number);
		}

		/**
		 * Async tool that returns structured output
		 */
		@McpTool(name = "async-structured-output-tool", description = "Async tool with structured output")
		public Mono<TestResult> asyncStructuredOutputTool(CallToolRequest request) {
			Map<String, Object> arguments = request.arguments();
			String input = (String) arguments.get("input");

			return Mono.just(new TestResult(input != null ? input : "default", 42));
		}

		/**
		 * Async tool that returns Mono<Void>
		 */
		@McpTool(name = "async-void-tool", description = "Async tool that returns void")
		public Mono<Void> asyncVoidTool(CallToolRequest request) {
			// Perform some side effect
			Map<String, Object> arguments = request.arguments();
			System.out.println("Processing: " + arguments);
			return Mono.empty();
		}

		/**
		 * Async tool that throws an error
		 */
		@McpTool(name = "async-error-tool", description = "Async tool that throws error")
		public Mono<CallToolResult> asyncErrorTool(CallToolRequest request) {
			return Mono.error(new RuntimeException("Async tool execution failed"));
		}

	}

	public static class TestResult {

		public String message;

		public int value;

		public TestResult(String message, int value) {
			this.message = message;
			this.value = value;
		}

	}

}
