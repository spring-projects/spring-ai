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

package org.springframework.ai.tool.method;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MethodToolCallback}.
 *
 * @author Thomas Vitale
 */
class MethodToolCallbackTests {

	@ParameterizedTest
	@ValueSource(strings = { "publicStaticMethod", "privateStaticMethod", "packageStaticMethod", "publicMethod",
			"privateMethod", "packageMethod" })
	void shouldCallToolFromPublicClass(String methodName) {
		validateAssertions(methodName, new PublicTools());
	}

	@ParameterizedTest
	@ValueSource(strings = { "publicStaticMethod", "privateStaticMethod", "packageStaticMethod", "publicMethod",
			"privateMethod", "packageMethod" })
	void shouldCallToolFromPrivateClass(String methodName) {
		validateAssertions(methodName, new PrivateTools());
	}

	@ParameterizedTest
	@ValueSource(strings = { "publicStaticMethod", "privateStaticMethod", "packageStaticMethod", "publicMethod",
			"privateMethod", "packageMethod" })
	void shouldCallToolFromPackageClass(String methodName) {
		validateAssertions(methodName, new PackageTools());
	}

	@Test
	void shouldHandleToolContextWhenSupported() {
		Method toolMethod = getMethod("methodWithToolContext", ToolContextTools.class);
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinition.from(toolMethod))
			.toolMetadata(ToolMetadata.from(toolMethod))
			.toolMethod(toolMethod)
			.toolObject(new ToolContextTools())
			.build();

		ToolContext toolContext = new ToolContext(Map.of("key", "value"));
		String result = callback.call("""
				{
				    "input": "test"
				}
				""", toolContext);

		assertThat(result).contains("value");
	}

	@Test
	void shouldThrowExceptionWhenToolContextArgumentIsMissing() {
		Method toolMethod = getMethod("methodWithToolContext", ToolContextTools.class);
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinition.from(toolMethod))
			.toolMetadata(ToolMetadata.from(toolMethod))
			.toolMethod(toolMethod)
			.toolObject(new PublicTools())
			.build();

		assertThatThrownBy(() -> callback.call("""
				{
				    "input": "test"
				}
				""")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("ToolContext is required by the method as an argument");
	}

	@Test
	void shouldHandleComplexArguments() {
		Method toolMethod = getMethod("complexArgumentMethod", ComplexTools.class);
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinition.from(toolMethod))
			.toolMetadata(ToolMetadata.from(toolMethod))
			.toolMethod(toolMethod)
			.toolObject(new ComplexTools())
			.build();

		String result = callback.call("""
				{
				    "stringArg": "test",
				    "intArg": 42,
				    "listArg": ["a", "b", "c"],
				    "optionalArg": null
				}
				""");

		assertThat(JsonParser.fromJson(result, new TypeReference<Map<String, Object>>() {
		})).containsEntry("stringValue", "test").containsEntry("intValue", 42).containsEntry("listSize", 3);
	}

	@Test
	void shouldHandleCustomResultConverter() {
		Method toolMethod = getMethod("publicMethod", PublicTools.class);
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinition.from(toolMethod))
			.toolMetadata(ToolMetadata.from(toolMethod))
			.toolMethod(toolMethod)
			.toolObject(new PublicTools())
			.toolCallResultConverter((result, type) -> "Converted: " + result)
			.build();

		String result = callback.call("""
				{
				    "input": "test"
				}
				""");

		assertThat(result).startsWith("Converted:");
	}

	@Test
	void shouldThrowExceptionWhenToolExecutionFails() {
		Method toolMethod = getMethod("errorMethod", ErrorTools.class);
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinition.from(toolMethod))
			.toolMetadata(ToolMetadata.from(toolMethod))
			.toolMethod(toolMethod)
			.toolObject(new ErrorTools())
			.build();

		assertThatThrownBy(() -> callback.call("""
				{
				    "input": "test"
				}
				""")).isInstanceOf(ToolExecutionException.class).hasMessageContaining("Test error");
	}

	private static void validateAssertions(String methodName, Object toolObject) {
		Method toolMethod = getMethod(methodName, toolObject.getClass());
		assertThat(toolMethod).isNotNull();
		MethodToolCallback callback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinition.from(toolMethod))
			.toolMetadata(ToolMetadata.from(toolMethod))
			.toolMethod(toolMethod)
			.toolObject(toolObject)
			.build();

		String result = callback.call("""
				{
				    "input": "Wingardium Leviosa"
				}
				""");

		assertThat(JsonParser.fromJson(result, new TypeReference<List<String>>() {
		})).contains("Wingardium Leviosa");
	}

	private static Method getMethod(String name, Class<?> toolsClass) {
		return Arrays.stream(ReflectionUtils.getDeclaredMethods(toolsClass))
			.filter(m -> m.getName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	static public class PublicTools {

		@Tool(description = "Test description")
		public static List<String> publicStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		private static List<String> privateStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		static List<String> packageStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		public List<String> publicMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		private List<String> privateMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		List<String> packageMethod(String input) {
			return List.of(input);
		}

	}

	static private class PrivateTools {

		@Tool(description = "Test description")
		public static List<String> publicStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		private static List<String> privateStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		static List<String> packageStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		public List<String> publicMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		private List<String> privateMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		List<String> packageMethod(String input) {
			return List.of(input);
		}

	}

	static class PackageTools {

		@Tool(description = "Test description")
		public static List<String> publicStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		private static List<String> privateStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		static List<String> packageStaticMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		public List<String> publicMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		private List<String> privateMethod(String input) {
			return List.of(input);
		}

		@Tool(description = "Test description")
		List<String> packageMethod(String input) {
			return List.of(input);
		}

	}

	static class ToolContextTools {

		@Tool(description = "Test description")
		public String methodWithToolContext(String input, ToolContext toolContext) {
			return input + ": " + toolContext.getContext().get("key");
		}

	}

	static class ComplexTools {

		@Tool(description = "Test description")
		public Map<String, Object> complexArgumentMethod(String stringArg, int intArg, List<String> listArg,
				String optionalArg) {
			return Map.of("stringValue", stringArg, "intValue", intArg, "listSize", listArg.size(), "optionalProvided",
					optionalArg != null);
		}

	}

	static class ErrorTools {

		@Tool(description = "Test description")
		public String errorMethod(String input) {
			throw new IllegalArgumentException("Test error");
		}

	}

}
