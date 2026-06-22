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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AsyncStatelessMcpToolMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class AsyncStatelessMcpToolMethodCallbackTests {

	@Test
	public void testSimpleMonoToolCallback() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("simpleMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("simple-mono-tool", Map.of("input", "test message"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: test message");
		}).verifyComplete();
	}

	@Test
	public void testSimpleFluxToolCallback() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("simpleFluxTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("simple-flux-tool", Map.of("input", "test message"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: test message");
		}).verifyComplete();
	}

	@Test
	public void testSimplePublisherToolCallback() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("simplePublisherTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("simple-publisher-tool", Map.of("input", "test message"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: test message");
		}).verifyComplete();
	}

	@Test
	public void testMathMonoToolCallback() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("addNumbersMono", int.class, int.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("math-mono-tool", Map.of("a", 5, "b", 3));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("8");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolThatThrowsException() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("exceptionMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("exception-mono-tool", Map.of("input", "test"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isTrue();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.contains("Error invoking tool 'exception-mono-tool'");
		}).verifyComplete();
	}

	@Test
	public void testComplexFluxToolCallback() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("complexFluxTool", String.class, int.class,
				boolean.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("complex-flux-tool",
				Map.of("name", "Alice", "age", 25, "active", false));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Name: Alice, Age: 25, Active: false");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithContextParameter() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("monoToolWithContext", McpTransportContext.class,
				String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("context-mono-tool", Map.of("message", "hello"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Context tool: hello");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithListParameter() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("processListMono", List.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("list-mono-tool",
				Map.of("items", List.of("item1", "item2", "item3")));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Items: item1, item2, item3");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithObjectParameter() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("processObjectMono", TestObject.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("object-mono-tool",
				Map.of("obj", Map.of("name", "test", "value", 42)));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Object: test - 42");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithNoParameters() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("noParamsMonoTool");
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("no-params-mono-tool", Map.of());

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("No parameters needed");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithEnumParameter() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("enumMonoTool", TestEnum.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("enum-mono-tool", Map.of("enumValue", "OPTION_B"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Enum: OPTION_B");
		}).verifyComplete();
	}

	@Test
	public void testComplexMonoToolCallback() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("complexMonoTool", String.class, int.class,
				boolean.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("complex-mono-tool",
				Map.of("name", "John", "age", 30, "active", true));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Name: John, Age: 30, Active: true");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithMissingParameters() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("simpleMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("simple-mono-tool", Map.of());

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: null");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithPrimitiveTypes() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("primitiveTypesMonoTool", boolean.class,
				byte.class, short.class, int.class, long.class, float.class, double.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("primitive-types-mono-tool",
				Map.of("flag", true, "b", 1, "s", 2, "i", 3, "l", 4L, "f", 5.5f, "d", 6.6));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Primitives: true, 1, 2, 3, 4, 5.5, 6.6");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithNullParameters() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("simpleMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		Map<String, Object> args = new java.util.HashMap<>();
		args.put("input", null);
		CallToolRequest request = new CallToolRequest("simple-mono-tool", args);

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Processed: null");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolThatReturnsNull() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("nullReturnMonoTool");
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("null-return-mono-tool", Map.of());

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isTrue();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).contains("value");
		}).verifyComplete();
	}

	@Test
	public void testVoidMonoTool() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("voidMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.VOID, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("void-mono-tool", Map.of("input", "test"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("\"Done\"");
		}).verifyComplete();
	}

	@Test
	public void testVoidFluxTool() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("voidFluxTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.VOID, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("void-flux-tool", Map.of("input", "test"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("\"Done\"");
		}).verifyComplete();
	}

	@Test
	public void testPrivateMonoToolMethod() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getDeclaredMethod("privateMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("private-mono-tool", Map.of("input", "test"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Private: test");
		}).verifyComplete();
	}

	@Test
	public void testNullRequest() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("simpleMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);

		StepVerifier.create(callback.apply(context, null)).expectError(IllegalArgumentException.class).verify();
	}

	@Test
	public void testMonoToolReturningComplexObject() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("returnObjectMonoTool", String.class, int.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.STRUCTURED,
				method, provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("return-object-mono-tool", Map.of("name", "test", "value", 42));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).isEmpty();
			assertThat(result.structuredContent()).isNotNull();
			assertThat((Map<String, Object>) result.structuredContent()).containsEntry("name", "test");
			assertThat((Map<String, Object>) result.structuredContent()).containsEntry("value", 42);
		}).verifyComplete();
	}

	@Test
	public void testEmptyMonoTool() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("emptyMonoTool");
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("empty-mono-tool", Map.of());

		StepVerifier.create(callback.apply(context, request)).verifyComplete();
	}

	@Test
	public void testMultipleFluxTool() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("multipleFluxTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("multiple-flux-tool", Map.of("prefix", "item"));

		// Flux tools should take the first element
		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("item1");
		}).verifyComplete();
	}

	@Test
	public void testNonReactiveToolShouldFail() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("nonReactiveTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("non-reactive-tool", Map.of("input", "test"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isTrue();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.contains("Expected reactive return type but got: java.lang.String");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithInvalidJsonConversion() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("processObjectMono", TestObject.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		// Pass invalid object structure that can't be converted to TestObject
		CallToolRequest request = new CallToolRequest("object-mono-tool", Map.of("obj", "invalid-object-string"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isTrue();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).contains(
					"Conversion from JSON to org.springframework.ai.mcp.annotation.method.tool.AsyncStatelessMcpToolMethodCallbackTests$TestObject failed");
		}).verifyComplete();
	}

	@Test
	public void testConstructorParameters() {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethods()[0]; // Any
																				// method

		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		// Verify that the callback was created successfully
		assertThat(callback).isNotNull();
	}

	@Test
	public void testIsExchangeOrContextType() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("simpleMonoTool", String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		// Test that McpTransportContext is recognized as context type
		assertThat(callback.isExchangeOrContextType(McpTransportContext.class)).isTrue();

		// Test that other types are not recognized as context type
		assertThat(callback.isExchangeOrContextType(String.class)).isFalse();
		assertThat(callback.isExchangeOrContextType(Integer.class)).isFalse();
		assertThat(callback.isExchangeOrContextType(Object.class)).isFalse();
	}

	@Test
	public void testMonoToolWithOptionalParameters() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("monoToolWithOptionalParams", String.class,
				String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("optional-params-mono-tool",
				Map.of("required", "test", "optional", "optional-value"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Required: test, Optional: optional-value");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithOptionalParametersMissing() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("monoToolWithOptionalParams", String.class,
				String.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("optional-params-mono-tool", Map.of("required", "test"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Required: test, Optional: null");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithStructuredOutput() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("processObjectMono", TestObject.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("object-mono-tool",
				Map.of("obj", Map.of("name", "test", "value", 42)));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Object: test - 42");
		}).verifyComplete();
	}

	@Test
	public void testCallbackReturnsCallToolResult() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("complexMonoTool", String.class, int.class,
				boolean.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("complex-mono-tool",
				Map.of("name", "Alice", "age", 25, "active", false));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Name: Alice, Age: 25, Active: false");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithCallToolRequest() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("monoToolWithCallToolRequest",
				CallToolRequest.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("call-tool-request-mono-tool",
				Map.of("param1", "value1", "param2", "value2"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Received tool: call-tool-request-mono-tool with 2 arguments");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithMixedParams() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("monoToolWithMixedParams", String.class,
				CallToolRequest.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("mixed-params-mono-tool", Map.of("action", "process"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Action: process, Tool: mixed-params-mono-tool");
		}).verifyComplete();
	}

	@Test
	public void testMonoToolWithContextAndRequest() throws Exception {
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("monoToolWithContextAndRequest",
				McpTransportContext.class, CallToolRequest.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);
		CallToolRequest request = new CallToolRequest("context-and-request-mono-tool", Map.of());

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text())
				.isEqualTo("Context present, Tool: context-and-request-mono-tool");
		}).verifyComplete();
	}

	@Test
	public void testAsyncStatelessMetaParameterInjection() throws Exception {
		// Test that McpMeta parameter receives the meta from request in async stateless
		// context
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("metaMonoTool", String.class, McpMeta.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);

		// Create request with meta data
		CallToolRequest request = CallToolRequest.builder()
			.name("meta-mono-tool")
			.arguments(Map.of("input", "test-input"))
			.meta(Map.of("userId", "user123", "sessionId", "session456"))
			.build();

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).contains("Input: test-input")
				.contains("Meta: {userId=user123, sessionId=session456}");
		}).verifyComplete();
	}

	@Test
	public void testAsyncStatelessMetaParameterWithNullMeta() throws Exception {
		// Test that McpMeta parameter handles null meta in async stateless context
		TestAsyncStatelessToolProvider provider = new TestAsyncStatelessToolProvider();
		Method method = TestAsyncStatelessToolProvider.class.getMethod("metaMonoTool", String.class, McpMeta.class);
		AsyncStatelessMcpToolMethodCallback callback = new AsyncStatelessMcpToolMethodCallback(ReturnMode.TEXT, method,
				provider);

		McpTransportContext context = mock(McpTransportContext.class);

		// Create request without meta
		CallToolRequest request = new CallToolRequest("meta-mono-tool", Map.of("input", "test-input"));

		StepVerifier.create(callback.apply(context, request)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.isError()).isFalse();
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
			assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Input: test-input, Meta: {}");
		}).verifyComplete();
	}

	private static class TestAsyncStatelessToolProvider {

		@McpTool(name = "simple-mono-tool", description = "A simple mono tool")
		public Mono<String> simpleMonoTool(String input) {
			return Mono.just("Processed: " + input);
		}

		@McpTool(name = "simple-flux-tool", description = "A simple flux tool")
		public Flux<String> simpleFluxTool(String input) {
			return Flux.just("Processed: " + input);
		}

		@McpTool(name = "simple-publisher-tool", description = "A simple publisher tool")
		public Publisher<String> simplePublisherTool(String input) {
			return Mono.just("Processed: " + input);
		}

		@McpTool(name = "math-mono-tool", description = "A math mono tool")
		public Mono<Integer> addNumbersMono(int a, int b) {
			return Mono.just(a + b);
		}

		@McpTool(name = "complex-mono-tool", description = "A complex mono tool")
		public Mono<CallToolResult> complexMonoTool(String name, int age, boolean active) {
			return Mono.just(CallToolResult.builder()
				.addTextContent("Name: " + name + ", Age: " + age + ", Active: " + active)
				.build());
		}

		@McpTool(name = "complex-flux-tool", description = "A complex flux tool")
		public Flux<CallToolResult> complexFluxTool(String name, int age, boolean active) {
			return Flux.just(CallToolResult.builder()
				.addTextContent("Name: " + name + ", Age: " + age + ", Active: " + active)
				.build());
		}

		@McpTool(name = "context-mono-tool", description = "Mono tool with context parameter")
		public Mono<String> monoToolWithContext(McpTransportContext context, String message) {
			return Mono.just("Context tool: " + message);
		}

		@McpTool(name = "list-mono-tool", description = "Mono tool with list parameter")
		public Mono<String> processListMono(List<String> items) {
			return Mono.just("Items: " + String.join(", ", items));
		}

		@McpTool(name = "object-mono-tool", description = "Mono tool with object parameter")
		public Mono<String> processObjectMono(TestObject obj) {
			return Mono.just("Object: " + obj.name + " - " + obj.value);
		}

		@McpTool(name = "optional-params-mono-tool", description = "Mono tool with optional parameters")
		public Mono<String> monoToolWithOptionalParams(@McpToolParam(required = true) String required,
				@McpToolParam(required = false) String optional) {
			return Mono.just("Required: " + required + ", Optional: " + (optional != null ? optional : "null"));
		}

		@McpTool(name = "no-params-mono-tool", description = "Mono tool with no parameters")
		public Mono<String> noParamsMonoTool() {
			return Mono.just("No parameters needed");
		}

		@McpTool(name = "exception-mono-tool", description = "Mono tool that throws exception")
		public Mono<String> exceptionMonoTool(String input) {
			return Mono.error(new RuntimeException("Tool execution failed: " + input));
		}

		@McpTool(name = "null-return-mono-tool", description = "Mono tool that returns null")
		public Mono<String> nullReturnMonoTool() {
			return Mono.just((String) null);
		}

		@McpTool(name = "void-mono-tool", description = "Mono<Void> tool")
		public Mono<Void> voidMonoTool(String input) {
			return Mono.empty();
		}

		@McpTool(name = "void-flux-tool", description = "Flux<Void> tool")
		public Flux<Void> voidFluxTool(String input) {
			return Flux.empty();
		}

		@McpTool(name = "enum-mono-tool", description = "Mono tool with enum parameter")
		public Mono<String> enumMonoTool(TestEnum enumValue) {
			return Mono.just("Enum: " + enumValue.name());
		}

		@McpTool(name = "primitive-types-mono-tool", description = "Mono tool with primitive types")
		public Mono<String> primitiveTypesMonoTool(boolean flag, byte b, short s, int i, long l, float f, double d) {
			return Mono
				.just(String.format(Locale.US, "Primitives: %b, %d, %d, %d, %d, %.1f, %.1f", flag, b, s, i, l, f, d));
		}

		@McpTool(name = "return-object-mono-tool", description = "Mono tool that returns a complex object")
		public Mono<TestObject> returnObjectMonoTool(String name, int value) {
			return Mono.just(new TestObject(name, value));
		}

		@McpTool(name = "delayed-mono-tool", description = "Mono tool with delay")
		public Mono<String> delayedMonoTool(String input) {
			return Mono.just("Delayed: " + input);
		}

		@McpTool(name = "empty-mono-tool", description = "Mono tool that returns empty")
		public Mono<String> emptyMonoTool() {
			return Mono.empty();
		}

		@McpTool(name = "multiple-flux-tool", description = "Flux tool that emits multiple values")
		public Flux<String> multipleFluxTool(String prefix) {
			return Flux.just(prefix + "1", prefix + "2", prefix + "3");
		}

		@McpTool(name = "private-mono-tool", description = "Private mono tool")
		private Mono<String> privateMonoTool(String input) {
			return Mono.just("Private: " + input);
		}

		// Non-reactive method that should cause error in async context
		@McpTool(name = "non-reactive-tool", description = "Non-reactive tool")
		public String nonReactiveTool(String input) {
			return "Non-reactive: " + input;
		}

		@McpTool(name = "call-tool-request-mono-tool", description = "Mono tool with CallToolRequest parameter")
		public Mono<String> monoToolWithCallToolRequest(CallToolRequest request) {
			return Mono.just("Received tool: " + request.name() + " with " + request.arguments().size() + " arguments");
		}

		@McpTool(name = "mixed-params-mono-tool", description = "Mono tool with mixed parameters")
		public Mono<String> monoToolWithMixedParams(String action, CallToolRequest request) {
			return Mono.just("Action: " + action + ", Tool: " + request.name());
		}

		@McpTool(name = "context-and-request-mono-tool", description = "Mono tool with context and request")
		public Mono<String> monoToolWithContextAndRequest(McpTransportContext context, CallToolRequest request) {
			return Mono.just("Context present, Tool: " + request.name());
		}

		/**
		 * Mono tool with McpMeta parameter
		 */
		@McpTool(name = "meta-mono-tool", description = "Mono tool with meta parameter")
		public Mono<String> metaMonoTool(@McpToolParam(description = "Input parameter", required = true) String input,
				McpMeta meta) {
			String metaInfo = meta != null && meta.meta() != null ? meta.meta().toString() : "null";
			return Mono.just("Input: " + input + ", Meta: " + metaInfo);
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
