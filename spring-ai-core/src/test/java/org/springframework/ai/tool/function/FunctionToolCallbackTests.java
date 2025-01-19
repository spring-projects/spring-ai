/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.tool.function;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.util.json.JsonSchemaGenerator;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FunctionToolCallback}.
 *
 * @author Thomas Vitale
 */
class FunctionToolCallbackTests {

	@Test
	void constructorShouldValidateRequiredParameters() {
		ToolDefinition toolDefinition = mock(ToolDefinition.class);
		ToolMetadata toolMetadata = mock(ToolMetadata.class);
		BiFunction<String, ToolContext, String> toolFunction = (input, context) -> input;

		assertThatThrownBy(() -> new FunctionToolCallback<>(null, toolMetadata, String.class, toolFunction, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolDefinition cannot be null");

		assertThatThrownBy(() -> new FunctionToolCallback<>(toolDefinition, toolMetadata, null, toolFunction, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolInputType cannot be null");

		assertThatThrownBy(() -> new FunctionToolCallback<>(toolDefinition, toolMetadata, String.class, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolFunction cannot be null");
	}

	@Test
	void callShouldExecuteToolFunctionAndConvertResult() {
		ToolDefinition toolDefinition = mock(ToolDefinition.class);
		when(toolDefinition.name()).thenReturn("test-tool");
		BiFunction<TestRequest, ToolContext, TestResponse> toolFunction = (input,
				context) -> new TestResponse(input.input());

		ToolCallback callback = FunctionToolCallback.builder("test-tool", toolFunction)
			.inputType(TestRequest.class)
			.build();

		String result = callback.call("""
				{
				    "input": "test input"
				}
				""", mock(ToolContext.class));

		assertThat(result).isEqualToIgnoringWhitespace("""
				{
				    "output": "test input"
				}
				""");
	}

	@Test
	void callShouldValidateInput() {
		ToolCallback callback = FunctionToolCallback.builder("test-tool", (input, context) -> input)
			.inputType(String.class)
			.build();

		assertThatThrownBy(() -> callback.call("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolInput cannot be null or empty");

		assertThatThrownBy(() -> callback.call(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolInput cannot be null or empty");
	}

	@Test
	void callWithoutContextShouldWorkCorrectly() {
		BiFunction<TestRequest, ToolContext, TestResponse> toolFunction = (input,
				context) -> new TestResponse(input.input());

		ToolCallback callback = FunctionToolCallback.builder("test-tool", toolFunction)
			.inputType(TestRequest.class)
			.build();

		String result = callback.call("""
				{
				    "input": "test input"
				}
				""");

		assertThat(result).isEqualToIgnoringWhitespace("""
				{
				    "output": "test input"
				}
				""");
	}

	// Builder

	@Test
	void builderShouldCreateInstanceWithAllProperties() {
		ToolMetadata toolMetadata = mock(ToolMetadata.class);
		BiFunction<String, ToolContext, String> toolFunction = (input, context) -> input;
		ToolCallResultConverter resultConverter = mock(ToolCallResultConverter.class);

		ToolCallback callback = FunctionToolCallback.builder("testTool", toolFunction)
			.description("A test tool")
			.inputSchema(JsonSchemaGenerator.generateForType(String.class))
			.inputType(String.class)
			.toolMetadata(toolMetadata)
			.toolCallResultConverter(resultConverter)
			.build();

		assertThat(callback.getToolDefinition().name()).isEqualTo("testTool");
		assertThat(callback.getToolDefinition().description()).isEqualTo("A test tool");
		assertThat(callback.getToolMetadata()).isEqualTo(toolMetadata);
	}

	@Test
	void builderShouldCreateInstanceWithCustomSchema() {
		ToolMetadata toolMetadata = mock(ToolMetadata.class);
		BiFunction<String, ToolContext, String> toolFunction = (input, context) -> input;
		ToolCallResultConverter resultConverter = mock(ToolCallResultConverter.class);

		ToolCallback callback = FunctionToolCallback.builder("testTool", toolFunction)
			.description("A test tool")
			// Special schema generation required by Vertex AI.
			.inputSchema(JsonSchemaGenerator.generateForType(String.class,
					JsonSchemaGenerator.SchemaOption.UPPER_CASE_TYPE_VALUES))
			.inputType(String.class)
			.toolMetadata(toolMetadata)
			.toolCallResultConverter(resultConverter)
			.build();

		assertThat(callback.getToolDefinition().name()).isEqualTo("testTool");
		assertThat(callback.getToolDefinition().description()).isEqualTo("A test tool");
		assertThat(callback.getToolMetadata()).isEqualTo(toolMetadata);
	}

	@Test
	void whenBuilderWithRequiredPropertiesThenReturn() {
		var builder = FunctionToolCallback.builder("test-tool", (input, context) -> input);
		assertThat(builder).isNotNull();
	}

	@Test
	void whenToolNameIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionToolCallback.builder(null, (input, context) -> input))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("name cannot be null or empty");
	}

	@Test
	void whenToolNameIsEmptyThenThrow() {
		assertThatThrownBy(() -> FunctionToolCallback.builder("", (input, context) -> input))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("name cannot be null or empty");
	}

	@Test
	void whenBuildingFromBiFunctionThenReturn() {
		var builder = FunctionToolCallback.builder("test-tool", (input, context) -> input);
		assertThat(builder).isNotNull();
	}

	@Test
	void whenBuildingFromNullBiFunctionThenReturn() {
		assertThatThrownBy(() -> FunctionToolCallback.builder("test-tool", (BiFunction<?, ToolContext, ?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolFunction cannot be null");
	}

	@Test
	void whenBuildingFromFunctionThenReturn() {
		var builder = FunctionToolCallback.builder("test-tool", (input) -> input);
		assertThat(builder).isNotNull();
	}

	@Test
	void whenBuildingFromNullFunctionThenReturn() {
		assertThatThrownBy(() -> FunctionToolCallback.builder("test-tool", (Function<?, ?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("function cannot be null");
	}

	@Test
	void whenBuildingFromSupplierThenReturn() {
		var builder = FunctionToolCallback.builder("test-tool", () -> "Hello");
		assertThat(builder).isNotNull();
	}

	@Test
	void whenBuildingFromNullSupplierThenReturn() {
		assertThatThrownBy(() -> FunctionToolCallback.builder("test-tool", (Supplier<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("supplier cannot be null");
	}

	@Test
	void whenBuildingFromConsumerThenReturn() {
		var builder = FunctionToolCallback.builder("test-tool", (input) -> null);
		assertThat(builder).isNotNull();
	}

	@Test
	void whenBuildingFromNullConsumerThenReturn() {
		assertThatThrownBy(() -> FunctionToolCallback.builder("test-tool", (Consumer<?>) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("consumer cannot be null");
	}

	@Test
	void whenInputTypeIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionToolCallback.builder("test-tool", (input, context) -> input).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputType cannot be null");
	}

	@Test
	void whenToolDescriptionIsNullThenComputeFromName() {
		ToolCallback callback = FunctionToolCallback.builder("mySuperTestTool", (input, context) -> input)
			.inputType(String.class)
			.build();
		assertThat(callback.getToolDefinition().description()).isEqualTo("my super test tool");
	}

	@Test
	void whenInputTypeIsGenericThenReturn() {
		ToolCallback callback = FunctionToolCallback.builder("mySuperTestTool", (input, context) -> input)
			.inputType(new ParameterizedTypeReference<List<String>>() {
			})
			.build();
		assertThat(callback).isNotNull();
	}

	public record TestRequest(String input) {
	}

	public record TestResponse(String output) {
	}

}
