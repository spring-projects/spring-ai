/*
 * Copyright 2025-2025 the original author or authors.
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

import io.modelcontextprotocol.server.McpSyncServerExchange;
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
 * These tests verify the exception handling behavior in the apply() method, specifically
 * the catch block that checks if an exception is an instance of the configured
 * toolCallExceptionClass.
 *
 * @author Christian Tzolov
 */
public class SyncMcpToolMethodCallbackExceptionHandlingTests {

	@Test
	public void testDefaultConstructor_CatchesAllExceptions() throws Exception {
		// Test with default constructor (uses Exception.class)
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("runtimeExceptionTool", String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("runtime-exception-tool", Map.of("input", "test"));

		// The RuntimeException thrown by callMethod should be caught and converted to
		// error result
		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains("Runtime error: test");
	}

	@Test
	public void testExceptionClassConstructor_CatchesSpecifiedExceptions() throws Exception {
		// Configure to catch only RuntimeException and its subclasses
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("customRuntimeExceptionTool", String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				RuntimeException.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("custom-runtime-exception-tool", Map.of("input", "test"));

		// The RuntimeException wrapper from callMethod should be caught
		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains("Custom runtime error: test");
	}

	@Test
	public void testNonMatchingExceptionClass_ThrowsException() throws Exception {
		// Configure to catch only IllegalArgumentException
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("runtimeExceptionTool", String.class);

		// Create callback that only catches IllegalArgumentException
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				IllegalArgumentException.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("runtime-exception-tool", Map.of("input", "test"));

		// The RuntimeException from callMethod should NOT be caught (not an
		// IllegalArgumentException)
		assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Error invoking method");
	}

	@Test
	public void testCheckedExceptionHandling_WithExceptionClass() throws Exception {
		// Test handling of checked exceptions wrapped in RuntimeException
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("checkedExceptionTool", String.class);

		// Configure to catch Exception (which includes RuntimeException)
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				Exception.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("checked-exception-tool", Map.of("input", "test"));

		// The RuntimeException wrapper should be caught
		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains("Business error: test");
	}

	@Test
	public void testCheckedExceptionHandling_WithSpecificClass() throws Exception {
		// Configure to catch only IllegalArgumentException (not RuntimeException)
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("checkedExceptionTool", String.class);

		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				IllegalArgumentException.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("checked-exception-tool", Map.of("input", "test"));

		// The RuntimeException wrapper should NOT be caught
		assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Error invoking method")
			.hasCauseInstanceOf(BusinessException.class);
	}

	@Test
	public void testSuccessfulExecution_NoExceptionThrown() throws Exception {
		// Test that successful execution works normally regardless of exception class
		// config
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("successTool", String.class);

		// Configure with a specific exception class
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				IllegalArgumentException.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("success-tool", Map.of("input", "test"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Success: test");
	}

	@Test
	public void testNullPointerException_WithRuntimeExceptionClass() throws Exception {
		// Configure to catch RuntimeException (which includes NullPointerException)
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("nullPointerTool", String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				RuntimeException.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("null-pointer-tool", Map.of("input", "test"));

		// Should catch the RuntimeException wrapper
		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains("Null pointer: test");
	}

	@Test
	public void testIllegalArgumentException_WithSpecificHandling() throws Exception {
		// Configure to catch only RuntimeException
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("illegalArgumentTool", String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				RuntimeException.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("illegal-argument-tool", Map.of("input", "test"));

		// Should catch the RuntimeException wrapper (which wraps
		// IllegalArgumentException)
		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains("Illegal argument: test");
	}

	@Test
	public void testMultipleCallsWithDifferentResults() throws Exception {
		// Test that the same callback instance handles different scenarios correctly
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method successMethod = ExceptionTestToolProvider.class.getMethod("successTool", String.class);
		Method exceptionMethod = ExceptionTestToolProvider.class.getMethod("runtimeExceptionTool", String.class);

		// Create callbacks with Exception handling (catches all)
		SyncMcpToolMethodCallback successCallback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, successMethod,
				provider, Exception.class);
		SyncMcpToolMethodCallback exceptionCallback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, exceptionMethod,
				provider, Exception.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		// Test success case
		CallToolRequest successRequest = new CallToolRequest("success-tool", Map.of("input", "success"));
		CallToolResult successResult = successCallback.apply(exchange, successRequest);
		assertThat(successResult.isError()).isFalse();
		assertThat(((TextContent) successResult.content().get(0)).text()).isEqualTo("Success: success");

		// Test exception case
		CallToolRequest exceptionRequest = new CallToolRequest("runtime-exception-tool", Map.of("input", "error"));
		CallToolResult exceptionResult = exceptionCallback.apply(exchange, exceptionRequest);
		assertThat(exceptionResult.isError()).isTrue();
		assertThat(((TextContent) exceptionResult.content().get(0)).text()).contains("Runtime error: error");
	}

	@Test
	public void testExceptionHierarchy_ParentClassCatchesSubclasses() throws Exception {
		// Configure to catch Exception (parent of RuntimeException)
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("customRuntimeExceptionTool", String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider,
				Exception.class);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("custom-runtime-exception-tool", Map.of("input", "test"));

		// Should catch the RuntimeException (subclass of Exception)
		CallToolResult result = callback.apply(exchange, request);
		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
	}

	@Test
	public void testConstructorWithNullExceptionClass_UsesDefault() throws Exception {
		// The constructor with 3 parameters uses Exception.class as default
		ExceptionTestToolProvider provider = new ExceptionTestToolProvider();
		Method method = ExceptionTestToolProvider.class.getMethod("runtimeExceptionTool", String.class);

		// This constructor uses Exception.class internally
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("runtime-exception-tool", Map.of("input", "test"));

		// Should catch all exceptions (default is Exception.class)
		CallToolResult result = callback.apply(exchange, request);
		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
	}

	// Custom exception classes for testing
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

	// Test tool provider with various exception-throwing methods
	private static class ExceptionTestToolProvider {

		@McpTool(name = "runtime-exception-tool", description = "Tool that throws RuntimeException")
		public String runtimeExceptionTool(String input) {
			throw new RuntimeException("Runtime error: " + input);
		}

		@McpTool(name = "custom-runtime-exception-tool", description = "Tool that throws CustomRuntimeException")
		public String customRuntimeExceptionTool(String input) {
			throw new CustomRuntimeException("Custom runtime error: " + input);
		}

		@McpTool(name = "checked-exception-tool", description = "Tool that throws checked exception")
		public String checkedExceptionTool(String input) throws BusinessException {
			throw new BusinessException("Business error: " + input);
		}

		@McpTool(name = "success-tool", description = "Tool that succeeds")
		public String successTool(String input) {
			return "Success: " + input;
		}

		@McpTool(name = "null-pointer-tool", description = "Tool that throws NullPointerException")
		public String nullPointerTool(String input) {
			throw new NullPointerException("Null pointer: " + input);
		}

		@McpTool(name = "illegal-argument-tool", description = "Tool that throws IllegalArgumentException")
		public String illegalArgumentTool(String input) {
			throw new IllegalArgumentException("Illegal argument: " + input);
		}

	}

}
