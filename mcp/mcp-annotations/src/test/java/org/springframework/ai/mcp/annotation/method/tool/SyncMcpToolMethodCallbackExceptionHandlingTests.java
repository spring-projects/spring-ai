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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for exception handling in {@link SyncMcpToolMethodCallback}.
 *
 * <p>
 * The contract mirrors {@code @Tool}: RuntimeExceptions are conveyed to the LLM as error
 * results; declared checked exceptions and Errors bubble up without reaching the LLM.
 *
 * @author Christian Tzolov
 */
public class SyncMcpToolMethodCallbackExceptionHandlingTests {

	@Test
	public void runtimeExceptionIsConvertedToErrorResult() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("runtimeExceptionTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("runtime-exception-tool")
			.arguments(Map.of("input", "test"))
			.build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result.isError()).isTrue();
		assertThat(((TextContent) result.content().get(0)).text()).contains("Runtime error: test");
	}

	@Test
	public void runtimeExceptionSubclassIsConvertedToErrorResult() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("customRuntimeExceptionTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("custom-runtime-exception-tool")
			.arguments(Map.of("input", "test"))
			.build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result.isError()).isTrue();
		assertThat(((TextContent) result.content().get(0)).text()).contains("Custom runtime error: test");
	}

	@Test
	public void nullPointerExceptionIsConvertedToErrorResult() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("nullPointerTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("null-pointer-tool")
			.arguments(Map.of("input", "test"))
			.build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result.isError()).isTrue();
		assertThat(((TextContent) result.content().get(0)).text()).contains("Null pointer: test");
	}

	@Test
	public void illegalArgumentExceptionIsConvertedToErrorResult() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("illegalArgumentTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("illegal-argument-tool")
			.arguments(Map.of("input", "test"))
			.build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result.isError()).isTrue();
		assertThat(((TextContent) result.content().get(0)).text()).contains("Illegal argument: test");
	}

	@Test
	public void declaredCheckedExceptionBubblesUp() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("checkedExceptionTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("checked-exception-tool")
			.arguments(Map.of("input", "test"))
			.build();

		assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(UndeclaredThrowableException.class)
			.cause()
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("Business error: test");
	}

	@Test
	public void errorBubblesUp() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("assertionErrorTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("assertion-error-tool")
			.arguments(Map.of("input", "test"))
			.build();

		assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("Assertion error: test");
	}

	@Test
	public void mcpErrorBubblesUp() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("mcpErrorTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("mcp-error-tool").arguments(Map.of("input", "test")).build();

		// A protocol-level McpError must bubble up rather than be converted to an error
		// CallToolResult conveyed to the model.
		assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(McpError.class);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void deprecatedConstructorIgnoresToolCallExceptionClass() throws Exception {
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("checkedExceptionTool", String.class);
		// The deprecated toolCallExceptionClass argument is retained for binary
		// compatibility but ignored: a declared checked exception still bubbles up rather
		// than being converted to an error result, regardless of the class passed.
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				RuntimeException.class);
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("checked-exception-tool")
			.arguments(Map.of("input", "test"))
			.build();

		assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(UndeclaredThrowableException.class)
			.cause()
			.isInstanceOf(BusinessException.class);
	}

	@Test
	public void successfulExecutionReturnsResult() throws Exception {
		SyncMcpToolMethodCallback callback = callbackFor("successTool");
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = CallToolRequest.builder("success-tool").arguments(Map.of("input", "test")).build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result.isError()).isFalse();
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Success: test");
	}

	// --- helpers ---

	private SyncMcpToolMethodCallback callbackFor(String methodName) throws Exception {
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod(methodName, String.class);
		return new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);
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
		public String runtimeExceptionTool(String input) {
			throw new RuntimeException("Runtime error: " + input);
		}

		@McpTool(name = "custom-runtime-exception-tool", description = "Throws CustomRuntimeException")
		public String customRuntimeExceptionTool(String input) {
			throw new CustomRuntimeException("Custom runtime error: " + input);
		}

		@McpTool(name = "null-pointer-tool", description = "Throws NullPointerException")
		public String nullPointerTool(String input) {
			throw new NullPointerException("Null pointer: " + input);
		}

		@McpTool(name = "illegal-argument-tool", description = "Throws IllegalArgumentException")
		public String illegalArgumentTool(String input) {
			throw new IllegalArgumentException("Illegal argument: " + input);
		}

		@McpTool(name = "checked-exception-tool", description = "Throws declared checked exception")
		public String checkedExceptionTool(String input) throws BusinessException {
			throw new BusinessException("Business error: " + input);
		}

		@McpTool(name = "assertion-error-tool", description = "Throws AssertionError")
		public String assertionErrorTool(String input) {
			throw new AssertionError("Assertion error: " + input);
		}

		@McpTool(name = "mcp-error-tool", description = "Throws McpError")
		public String mcpErrorTool(String input) {
			throw McpError.builder(-32000).message("Protocol error: " + input).build();
		}

		@McpTool(name = "success-tool", description = "Returns a result")
		public String successTool(String input) {
			return "Success: " + input;
		}

	}

}
