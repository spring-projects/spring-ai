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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SyncStatelessMcpToolMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class SyncStatelessMcpToolMethodCallbackTests {

	@Test
	public void testSimpleToolCallback() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("simpleTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("simple-tool", Map.of("input", "test message"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: test message");
	}

	@Test
	public void testMathToolCallback() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("addNumbers", int.class, int.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("math-tool", Map.of("a", 5, "b", 3));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("8");
	}

	@Test
	public void testComplexToolCallback() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("complexTool", String.class, int.class, boolean.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("complex-tool",
				Map.of("name", "John", "age", 30, "active", true));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Name: John, Age: 30, Active: true");
	}

	@Test
	public void testToolWithContextParameter() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("toolWithContext", McpTransportContext.class, String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("context-tool", Map.of("message", "hello"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Context tool: hello");
	}

	@Test
	public void testToolWithListParameter() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("processList", List.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("list-tool", Map.of("items", List.of("item1", "item2", "item3")));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Items: item1, item2, item3");
	}

	@Test
	public void testToolWithObjectParameter() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("processObject", TestObject.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("object-tool",
				Map.of("obj", Map.of("name", "test", "value", 42)));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Object: test - 42");
	}

	@Test
	public void testToolWithNoParameters() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("noParamsTool");
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("no-params-tool", Map.of());

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("No parameters needed");
	}

	@Test
	public void testToolWithEnumParameter() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("enumTool", TestEnum.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("enum-tool", Map.of("enumValue", "OPTION_B"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Enum: OPTION_B");
	}

	@Test
	public void testToolWithPrimitiveTypes() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("primitiveTypesTool", boolean.class, byte.class, short.class,
				int.class, long.class, float.class, double.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("primitive-types-tool",
				Map.of("flag", true, "b", 1, "s", 2, "i", 3, "l", 4L, "f", 5.5f, "d", 6.6));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Primitives: true, 1, 2, 3, 4, 5.5, 6.6");
	}

	@Test
	public void testToolWithNullParameters() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("simpleTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		Map<String, Object> args = new java.util.HashMap<>();
		args.put("input", null);
		CallToolRequest request = new CallToolRequest("simple-tool", args);

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: null");
	}

	@Test
	public void testToolWithMissingParameters() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("simpleTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("simple-tool", Map.of());

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: null");
	}

	@Test
	public void testToolThatThrowsException() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("exceptionTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("exception-tool", Map.of("input", "test"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains("Tool execution failed: test");
	}

	@Test
	public void testToolThatReturnsNull() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("nullReturnTool");
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("null-return-tool", Map.of());

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("null");
	}

	@Test
	public void testPrivateToolMethod() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getDeclaredMethod("privateTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("private-tool", Map.of("input", "test"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Private: test");
	}

	@Test
	public void testNullRequest() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("simpleTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);

		assertThatThrownBy(() -> callback.apply(context, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Request must not be null");
	}

	@Test
	public void testCallbackReturnsCallToolResult() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("complexTool", String.class, int.class, boolean.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("complex-tool",
				Map.of("name", "Alice", "age", 25, "active", false));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Name: Alice, Age: 25, Active: false");
	}

	@Test
	public void testIsExchangeOrContextType() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("simpleTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		// Test that McpTransportContext is recognized as context type
		// Note: We need to use reflection to access the protected method for testing
		java.lang.reflect.Method isContextTypeMethod = SyncStatelessMcpToolMethodCallback.class
			.getDeclaredMethod("isExchangeOrContextType", Class.class);
		isContextTypeMethod.setAccessible(true);

		assertThat((Boolean) isContextTypeMethod.invoke(callback, McpTransportContext.class)).isTrue();

		// Test that other types are not recognized as context type
		assertThat((Boolean) isContextTypeMethod.invoke(callback, String.class)).isFalse();
		assertThat((Boolean) isContextTypeMethod.invoke(callback, Integer.class)).isFalse();
		assertThat((Boolean) isContextTypeMethod.invoke(callback, Object.class)).isFalse();
	}

	@Test
	public void testToolWithInvalidJsonConversion() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("processObject", TestObject.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		// Pass invalid object structure that can't be converted to TestObject
		CallToolRequest request = new CallToolRequest("object-tool", Map.of("obj", "invalid-object-string"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains(
				"Conversion from JSON to org.springframework.ai.mcp.annotation.method.tool.SyncStatelessMcpToolMethodCallbackTests$TestObject failed");
	}

	@Test
	public void testConstructorParameters() {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethods()[0]; // Any method

		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		// Verify that the callback was created successfully
		assertThat(callback).isNotNull();
	}

	@Test
	public void testToolReturningComplexObject() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("returnObjectTool", String.class, int.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.STRUCTURED,
				method, provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("return-object-tool", Map.of("name", "test", "value", 42));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		// For complex return types (non-primitive, non-wrapper, non-CallToolResult),
		// the new implementation should return structured content
		assertThat(result.content()).isEmpty();
		assertThat(result.structuredContent()).isNotNull();
		assertThat((Map<String, Object>) result.structuredContent()).containsEntry("name", "test");
		assertThat((Map<String, Object>) result.structuredContent()).containsEntry("value", 42);
	}

	@Test
	public void testToolReturningStructuredComplexListObject() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("returnListObjectTool", String.class, int.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.STRUCTURED, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("return-list-object-tool", Map.of("name", "test", "value", 42));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();

		assertThat(result.structuredContent()).isNotNull();
		assertThat(result.structuredContent()).isInstanceOf(List.class);
		assertThat((List<?>) result.structuredContent()).hasSize(1);
		Map<String, Object> firstEntry = ((List<Map<String, Object>>) result.structuredContent()).get(0);
		assertThat(firstEntry).containsEntry("name", "test");
		assertThat(firstEntry).containsEntry("value", 42);
	}

	@Test
	public void testVoidReturnMode() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("voidTool", String.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.VOID, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("void-tool", Map.of("input", "test"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("\"Done\"");
	}

	@Test
	public void testToolWithCallToolRequest() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("toolWithCallToolRequest", CallToolRequest.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("call-tool-request-tool",
				Map.of("param1", "value1", "param2", "value2"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Received tool: call-tool-request-tool with 2 arguments");
	}

	@Test
	public void testToolWithMixedParams() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("toolWithMixedParams", String.class, CallToolRequest.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("mixed-params-tool", Map.of("action", "process"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Action: process, Tool: mixed-params-tool");
	}

	@Test
	public void testToolWithContextAndRequest() throws Exception {
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("toolWithContextAndRequest", McpTransportContext.class,
				CallToolRequest.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("context-and-request-tool", Map.of());

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Context present, Tool: context-and-request-tool");
	}

	@Test
	public void testStatelessMetaParameterInjection() throws Exception {
		// Test that McpMeta parameter receives the meta from request in stateless context
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("metaTool", String.class, McpMeta.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);

		// Create request with meta data
		CallToolRequest request = CallToolRequest.builder()
			.name("meta-tool")
			.arguments(Map.of("input", "test-input"))
			.meta(Map.of("userId", "user123", "sessionId", "session456"))
			.build();

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).contains("Input: test-input")
			.contains("Meta: {userId=user123, sessionId=session456}");
	}

	@Test
	public void testStatelessMetaParameterWithNullMeta() throws Exception {
		// Test that McpMeta parameter handles null meta in stateless context
		TestToolProvider provider = new TestToolProvider();
		Method method = TestToolProvider.class.getMethod("metaTool", String.class, McpMeta.class);
		SyncStatelessMcpToolMethodCallback callback = new SyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);

		// Create request without meta
		CallToolRequest request = new CallToolRequest("meta-tool", Map.of("input", "test-input"));

		CallToolResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Input: test-input, Meta: {}");
	}

	private static class TestToolProvider {

		@McpTool(name = "simple-tool", description = "A simple tool")
		public String simpleTool(String input) {
			return "Processed: " + input;
		}

		@McpTool(name = "math-tool", description = "A math tool")
		public int addNumbers(int a, int b) {
			return a + b;
		}

		@McpTool(name = "complex-tool", description = "A complex tool")
		public CallToolResult complexTool(String name, int age, boolean active) {
			return CallToolResult.builder()
				.addTextContent("Name: " + name + ", Age: " + age + ", Active: " + active)
				.build();
		}

		@McpTool(name = "context-tool", description = "Tool with context parameter")
		public String toolWithContext(McpTransportContext context, String message) {
			return "Context tool: " + message;
		}

		@McpTool(name = "list-tool", description = "Tool with list parameter")
		public String processList(List<String> items) {
			return "Items: " + String.join(", ", items);
		}

		@McpTool(name = "object-tool", description = "Tool with object parameter")
		public String processObject(TestObject obj) {
			return "Object: " + obj.name + " - " + obj.value;
		}

		@McpTool(name = "optional-params-tool", description = "Tool with optional parameters")
		public String toolWithOptionalParams(@McpToolParam(required = true) String required,
				@McpToolParam(required = false) String optional) {
			return "Required: " + required + ", Optional: " + (optional != null ? optional : "null");
		}

		@McpTool(name = "no-params-tool", description = "Tool with no parameters")
		public String noParamsTool() {
			return "No parameters needed";
		}

		@McpTool(name = "exception-tool", description = "Tool that throws exception")
		public String exceptionTool(String input) {
			throw new RuntimeException("Tool execution failed: " + input);
		}

		@McpTool(name = "null-return-tool", description = "Tool that returns null")
		public String nullReturnTool() {
			return null;
		}

		public String nonAnnotatedTool(String input) {
			return "Non-annotated: " + input;
		}

		@McpTool(name = "private-tool", description = "Private tool")
		private String privateTool(String input) {
			return "Private: " + input;
		}

		@McpTool(name = "enum-tool", description = "Tool with enum parameter")
		public String enumTool(TestEnum enumValue) {
			return "Enum: " + enumValue.name();
		}

		@McpTool(name = "primitive-types-tool", description = "Tool with primitive types")
		public String primitiveTypesTool(boolean flag, byte b, short s, int i, long l, float f, double d) {
			return String.format(Locale.US, "Primitives: %b, %d, %d, %d, %d, %.1f, %.1f", flag, b, s, i, l, f, d);
		}

		@McpTool(name = "return-object-tool", description = "Tool that returns a complex object")
		public TestObject returnObjectTool(String name, int value) {
			return new TestObject(name, value);
		}

		@McpTool(name = "void-tool", description = "Tool with void return")
		public void voidTool(String input) {
			// Do nothing
		}

		@McpTool(name = "call-tool-request-tool", description = "Tool with CallToolRequest parameter")
		public String toolWithCallToolRequest(CallToolRequest request) {
			return "Received tool: " + request.name() + " with " + request.arguments().size() + " arguments";
		}

		@McpTool(name = "mixed-params-tool", description = "Tool with mixed parameters")
		public String toolWithMixedParams(String action, CallToolRequest request) {
			return "Action: " + action + ", Tool: " + request.name();
		}

		@McpTool(name = "context-and-request-tool", description = "Tool with context and request")
		public String toolWithContextAndRequest(McpTransportContext context, CallToolRequest request) {
			return "Context present, Tool: " + request.name();
		}

		@McpTool(name = "return-list-object-tool", description = "Tool that returns a list of complex objects")
		public List<TestObject> returnListObjectTool(String name, int value) {
			return List.of(new TestObject(name, value));
		}

		/**
		 * Tool with McpMeta parameter
		 */
		@McpTool(name = "meta-tool", description = "Tool with meta parameter")
		public String metaTool(@McpToolParam(description = "Input parameter", required = true) String input,
				McpMeta meta) {
			String metaInfo = meta != null && meta.meta() != null ? meta.meta().toString() : "null";
			return "Input: " + input + ", Meta: " + metaInfo;
		}

	}

	public static class TestObject {

		public String name;

		public int value;

		public TestObject() {
		}

		public TestObject(String name, int value) {
			this.name = name;
			this.value = value;
		}

	}

	public enum TestEnum {

		OPTION_A, OPTION_B, OPTION_C

	}

}
