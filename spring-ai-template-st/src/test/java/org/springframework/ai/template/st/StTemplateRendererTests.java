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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ai.template.ValidationMode;

/**
 * Unit tests for {@link StTemplateRenderer}.
 *
 * @author Thomas Vitale
 */
class StTemplateRendererTests {

	@Test
	void shouldNotAcceptNullValidationMode() {
		assertThatThrownBy(() -> StTemplateRenderer.builder().validationMode(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("validationMode cannot be null");
	}

	@Test
	void shouldUseDefaultValuesWhenUsingBuilder() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();

		assertThat(ReflectionTestUtils.getField(renderer, "startDelimiterToken")).isEqualTo('{');
		assertThat(ReflectionTestUtils.getField(renderer, "endDelimiterToken")).isEqualTo('}');
		assertThat(ReflectionTestUtils.getField(renderer, "validationMode")).isEqualTo(ValidationMode.THROW);
	}

	@Test
	void shouldRenderTemplateWithSingleVariable() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");

		String result = renderer.apply("Hello {name}!", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	@Test
	void shouldRenderTemplateWithMultipleVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		variables.put("name", "Spring AI");
		variables.put("punctuation", "!");

		String result = renderer.apply("{greeting} {name}{punctuation}", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	@Test
	void shouldNotRenderEmptyTemplate() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();

		assertThatThrownBy(() -> renderer.apply("", variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void shouldNotAcceptNullVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		assertThatThrownBy(() -> renderer.apply("Hello!", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables cannot be null");
	}

	@Test
	void shouldNotAcceptVariablesWithNullKeySet() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		String template = "Hello!";
		Map<String, Object> variables = new HashMap<String, Object>();
		variables.put(null, "Spring AI");

		assertThatThrownBy(() -> renderer.apply(template, variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables keys cannot be null");
	}

	@Test
	void shouldThrowExceptionForInvalidTemplateSyntax() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");

		assertThatThrownBy(() -> renderer.apply("Hello {name!", variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The template string is not valid.");
	}

	@Test
	void shouldThrowExceptionForMissingVariablesInThrowMode() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		assertThatThrownBy(() -> renderer.apply("{greeting} {name}!", variables))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
					"Not all variables were replaced in the template. Missing variable names are: [name]");
	}

	@Test
	void shouldContinueRenderingWithMissingVariablesInWarnMode() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().validationMode(ValidationMode.WARN).build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		String result = renderer.apply("{greeting} {name}!", variables);

		assertThat(result).isEqualTo("Hello !");
	}

	@Test
	void shouldRenderWithoutValidationInNoneMode() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().validationMode(ValidationMode.NONE).build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		String result = renderer.apply("{greeting} {name}!", variables);

		assertThat(result).isEqualTo("Hello !");
	}

	@Test
	void shouldRenderWithCustomDelimiters() {
		StTemplateRenderer renderer = StTemplateRenderer.builder()
			.startDelimiterToken('<')
			.endDelimiterToken('>')
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");

		String result = renderer.apply("Hello <name>!", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	@Test
	void shouldHandleSpecialCharactersAsDelimiters() {
		StTemplateRenderer renderer = StTemplateRenderer.builder()
			.startDelimiterToken('$')
			.endDelimiterToken('$')
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");

		String result = renderer.apply("Hello $name$!", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	/**
	 * Tests that complex multi-line template structures with multiple variables are
	 * rendered correctly with proper whitespace and newline handling.
	 */
	@Test
	void shouldHandleComplexTemplateStructures() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("header", "Welcome");
		variables.put("user", "Spring AI");
		variables.put("items", "one, two, three");
		variables.put("footer", "Goodbye");

		String result = renderer.apply("""
				{header}
				User: {user}
				Items: {items}
				{footer}
				""", variables);

		assertThat(result).isEqualToNormalizingNewlines("""
				Welcome
				User: Spring AI
				Items: one, two, three
				Goodbye
				""");
	}

	/**
	 * Tests that StringTemplate list variables with separators are correctly handled.
	 * Note: Uses NONE validation mode because the current implementation of
	 * getInputVariables incorrectly treats template options like 'separator' as variables
	 * to be resolved.
	 */
	@Test
	void shouldHandleListVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().validationMode(ValidationMode.NONE).build();

		Map<String, Object> variables = new HashMap<>();
		variables.put("items", new String[] { "apple", "banana", "cherry" });

		String result = renderer.apply("Items: {items; separator=\", \"}", variables);

		assertThat(result).isEqualTo("Items: apple, banana, cherry");
	}

	/**
	 * Tests rendering with StringTemplate options. Note: This uses NONE validation mode
	 * because the current implementation of getInputVariables incorrectly treats template
	 * options like 'separator' as variables to be resolved.
	 */
	@Test
	void shouldRenderTemplateWithOptions() {
		// Use NONE validation mode to bypass the issue with option detection
		StTemplateRenderer renderer = StTemplateRenderer.builder().validationMode(ValidationMode.NONE).build();

		Map<String, Object> variables = new HashMap<>();
		variables.put("fruits", new String[] { "apple", "banana", "cherry" });
		variables.put("count", 3);

		// Template with separator option for list formatting
		String result = renderer.apply("Fruits: {fruits; separator=\", \"}, Count: {count}", variables);

		// Verify the template was rendered correctly
		assertThat(result).isEqualTo("Fruits: apple, banana, cherry, Count: 3");

		// Verify specific elements to ensure the list was processed
		assertThat(result).contains("apple");
		assertThat(result).contains("banana");
		assertThat(result).contains("cherry");
	}

	/**
	 * Tests that numeric variables (both integer and floating-point) are correctly
	 * converted to strings during template rendering.
	 */
	@Test
	void shouldHandleNumericVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("integer", 42);
		variables.put("float", 3.14);

		String result = renderer.apply("Integer: {integer}, Float: {float}", variables);

		assertThat(result).isEqualTo("Integer: 42, Float: 3.14");
	}

	/**
	 * Tests handling of object variables using StringTemplate's map access syntax. Since
	 * ST4 doesn't support direct property access like "person.name", we test both flat
	 * properties and alternative methods of accessing nested properties.
	 */
	@Test
	void shouldHandleObjectVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		// Add flattened properties directly
		variables.put("name", "John");
		variables.put("age", 30);

		// StringTemplate doesn't support person.name direct access
		// so we use flat properties instead
		String result = renderer.apply("Person: {name}, Age: {age}", variables);

		assertThat(result).isEqualTo("Person: John, Age: 30");
	}

	/**
	 * Test whether StringTemplate can correctly render a template containing built-in
	 * functions. It should render properly.
	 */
	@Test
	void shouldRenderTemplateWithBuiltInFunctions() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("memory", "you are a helpful assistant");
		String template = "{if(strlen(memory))}Hello!{endif}";

		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("Hello!");
	}

}
