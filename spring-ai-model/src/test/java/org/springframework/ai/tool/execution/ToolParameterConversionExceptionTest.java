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

package org.springframework.ai.tool.execution;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToolParameterConversionException}.
 *
 * @author Christian Tzolov
 */
class ToolParameterConversionExceptionTest {

	private static final ToolDefinition TEST_TOOL = DefaultToolDefinition.builder()
		.name("testTool")
		.description("Test tool for conversion testing")
		.inputSchema("{\"type\":\"object\",\"properties\":{}}")
		.build();

	@Test
	void shouldCreateExceptionWithParameterName() {
		var originalException = new NumberFormatException("For input string: \"\"");
		var exception = new ToolParameterConversionException(TEST_TOOL, "userId", Long.class, "", originalException);

		assertThat(exception.getParameterName()).isEqualTo("userId");
		assertThat(exception.getExpectedType()).isEqualTo(Long.class);
		assertThat(exception.getActualValue()).isEqualTo("");
		assertThat(exception.getToolDefinition()).isEqualTo(TEST_TOOL);
		assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
		assertThat(exception.getMessage())
			.contains("Tool parameter conversion failed for parameter 'userId' in tool 'testTool'")
			.contains("Expected type: Long")
			.contains("but received: \"\" (empty string)")
			.contains(
					"Suggestion: Ensure your prompt clearly specifies that numeric parameters should contain valid numbers");
	}

	@Test
	void shouldCreateExceptionWithoutParameterName() {
		var originalException = new NumberFormatException("Invalid number");
		var exception = new ToolParameterConversionException(TEST_TOOL, Integer.class, "invalid", originalException);

		assertThat(exception.getParameterName()).isNull();
		assertThat(exception.getExpectedType()).isEqualTo(Integer.class);
		assertThat(exception.getActualValue()).isEqualTo("invalid");
		assertThat(exception.getMessage()).contains("Tool parameter conversion failed in tool 'testTool'")
			.contains("Expected type: Integer")
			.contains("but received: \"invalid\" (String)")
			.contains("Suggestion: Verify that the model is providing numeric values");
	}

	@Test
	void shouldCreateExceptionWithNullValue() {
		var originalException = new NullPointerException("Null value");
		var exception = new ToolParameterConversionException(TEST_TOOL, "param", String.class, null, originalException);

		assertThat(exception.getActualValue()).isNull();
		assertThat(exception.getMessage()).contains("but received: null");
	}

	@Test
	void shouldTruncateLongValues() {
		String longValue = "This is a very long string that should be truncated because it exceeds the maximum length";
		var originalException = new IllegalArgumentException("Invalid value");
		var exception = new ToolParameterConversionException(TEST_TOOL, "param", String.class, longValue,
				originalException);

		assertThat(exception.getMessage()).contains("This is a very long string that should be trunc...")
			.doesNotContain("because it exceeds the maximum length");
	}

	@Test
	void shouldProvideHelpfulSuggestionsForNumericTypes() {
		var originalException = new NumberFormatException("Empty string");
		var exception = new ToolParameterConversionException(TEST_TOOL, "count", Long.class, "", originalException);

		assertThat(exception.getMessage()).contains(
				"Suggestion: Ensure your prompt clearly specifies that numeric parameters should contain valid numbers")
			.contains("Consider making the parameter optional or providing a default value");
	}

	@Test
	void shouldProvideGenericSuggestionsForNonNumericTypes() {
		var originalException = new IllegalArgumentException("Invalid format");
		var exception = new ToolParameterConversionException(TEST_TOOL, "data", String.class, new Object(),
				originalException);

		assertThat(exception.getMessage()).contains(
				"Suggestion: Review your tool description and prompt to ensure the model provides values compatible");
	}

}