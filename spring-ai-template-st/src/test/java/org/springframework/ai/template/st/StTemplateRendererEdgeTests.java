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

package org.springframework.ai.template.st;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.template.ValidationMode;

/**
 * Additional edge and robustness tests for {@link StTemplateRenderer}.
 */
class StTemplateRendererEdgeTests {

	/**
	 * Built-in functions (first, last) are rendered correctly with variables.
	 */
	@Test
	void shouldHandleMultipleBuiltInFunctionsAndVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("list", java.util.Arrays.asList("a", "b", "c"));
		variables.put("name", "Mark");
		String template = "{name}: {first(list)}, {last(list)}";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("Mark: a, c");
	}

	/**
	 * Nested and chained built-in functions are handled when validation is enabled.
	 * Confirms that ST4 supports valid nested function expressions.
	 */
	@Test
	void shouldSupportValidNestedFunctionExpressionInST4() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("words", java.util.Arrays.asList("hello", "WORLD"));
		String template = "{first(words)} {last(words)} {length(words)}";
		StTemplateRenderer defaultRenderer = StTemplateRenderer.builder().build();
		String defaultResult = defaultRenderer.apply(template, variables);
		assertThat(defaultResult).isEqualTo("hello WORLD 2");
	}

	/**
	 * Nested and chained built-in functions are handled when validation is enabled.
	 */
	@Test
	void shouldHandleNestedBuiltInFunctions() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("words", java.util.Arrays.asList("hello", "WORLD"));
		String template = "{first(words)} {last(words)} {length(words)}";
		StTemplateRenderer renderer = StTemplateRenderer.builder().validateStFunctions().build();
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("hello WORLD 2");
	}

	/**
	 * Built-in functions as properties are rendered correctly if supported.
	 */
	@Test
	@Disabled("It is very hard to validate the template expression when using property style access of built-in functions ")
	void shouldSupportBuiltInFunctionsAsProperties() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("words", java.util.Arrays.asList("hello", "WORLD"));
		String template = "{words.first} {words.last} {words.length}";
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("hello WORLD 2");
	}

	/**
	 * Built-in functions are not reported as missing variables in THROW mode.
	 */
	@Test
	void shouldNotReportBuiltInFunctionsAsMissingVariablesInThrowMode() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().validationMode(ValidationMode.THROW).build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("memory", "abc");
		String template = "{if(strlen(memory))}ok{endif}";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("ok");
	}

	/**
	 * Built-in functions are not reported as missing variables in WARN mode.
	 */
	@Test
	void shouldNotReportBuiltInFunctionsAsMissingVariablesInWarnMode() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().validationMode(ValidationMode.WARN).build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("memory", "abc");
		String template = "{if(strlen(memory))}ok{endif}";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("ok");
	}

	/**
	 * Variables with names similar to built-in functions are treated as normal variables.
	 */
	@Test
	void shouldHandleVariableNamesSimilarToBuiltInFunctions() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("lengthy", "foo");
		variables.put("firstName", "bar");
		String template = "{lengthy} {firstName}";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("foo bar");
	}

	// --- Built-in Function Handling Tests END ---

	@Test
	void shouldRenderEscapedDelimiters() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("x", "y");
		String template = "{x} \\{foo\\}";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("y {foo}");
	}

	@Test
	void shouldRenderStaticTextTemplate() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		String template = "Just static text.";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("Just static text.");
	}

	// Duplicate removed: shouldHandleVariableNamesSimilarToBuiltInFunctions
	// (now grouped at the top of the class)

	@Test
	void shouldHandleLargeNumberOfVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		StringBuilder template = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			String key = "var" + i;
			variables.put(key, i);
			template.append("{" + key + "} ");
		}
		String result = renderer.apply(template.toString().trim(), variables);
		StringBuilder expected = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			expected.append(i).append(" ");
		}
		assertThat(result).isEqualTo(expected.toString().trim());
	}

	@Test
	void shouldRenderUnicodeAndSpecialCharacters() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("emoji", "😀");
		variables.put("accented", "Café");
		String template = "{emoji} {accented}";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("😀 Café");
	}

	@Test
	void shouldRenderNullVariableValuesAsBlank() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("foo", null);
		String template = "Value: {foo}";
		String result = renderer.apply(template, variables);
		assertThat(result).isEqualTo("Value: ");
	}

}
