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

package org.springframework.ai.template.jinja;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.template.ValidationMode;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JinjaTemplateRenderer}.
 *
 * @author Sun YuHan
 */
class JinjaTemplateRendererTests {

	@Test
	void shouldNotAcceptNullValidationMode() {
		assertThatThrownBy(() -> JinjaTemplateRenderer.builder().validationMode(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("validationMode cannot be null");
	}

	@Test
	void shouldUseDefaultValuesWhenUsingBuilder() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();

		assertThat(ReflectionTestUtils.getField(renderer, "validationMode")).isEqualTo(ValidationMode.THROW);
	}

	@Test
	void shouldRenderTemplateWithSingleVariable() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");

		String result = renderer.apply("Hello {{name}}!", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	@Test
	void shouldRenderTemplateWithMultipleVariables() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		variables.put("name", "Spring AI");
		variables.put("punctuation", "!");

		String result = renderer.apply("{{greeting}} {{name}}{{punctuation}}", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	@Test
	void shouldNotRenderEmptyTemplate() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();

		assertThatThrownBy(() -> renderer.apply("", variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void shouldNotAcceptNullVariables() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		assertThatThrownBy(() -> renderer.apply("Hello!", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables cannot be null");
	}

	@Test
	void shouldNotAcceptVariablesWithNullKeySet() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		String template = "Hello!";
		Map<String, Object> variables = new HashMap<>();
		variables.put(null, "Spring AI");

		assertThatThrownBy(() -> renderer.apply(template, variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables keys cannot be null");
	}

	@Test
	void shouldThrowExceptionForMissingVariablesInThrowMode() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		assertThatThrownBy(() -> renderer.apply("{{greeting}} {{name}}!", variables))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
					"Not all variables were replaced in the template. Missing variable names are: [name]");
	}

	@Test
	void shouldContinueRenderingWithMissingVariablesInWarnMode() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().validationMode(ValidationMode.WARN).build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		String result = renderer.apply("{{greeting}} {{name}}!", variables);

		assertThat(result).isEqualTo("Hello !");
	}

	@Test
	void shouldRenderWithoutValidationInNoneMode() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().validationMode(ValidationMode.NONE).build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		String result = renderer.apply("{{greeting}} {{name}}!", variables);

		assertThat(result).isEqualTo("Hello !");
	}

	/**
	 * Tests that complex multi-line template structures with multiple variables are
	 * rendered correctly with proper whitespace and newline handling.
	 */
	@Test
	void shouldHandleComplexTemplateStructures() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("header", "Welcome");
		variables.put("user", "Spring AI");
		variables.put("items", "one, two, three");
		variables.put("footer", "Goodbye");

		String result = renderer.apply("""
				{{header}}
				User: {{user}}
				Items: {{items}}
				{{footer}}
				""", variables);

		assertThat(result).isEqualToNormalizingNewlines("""
				Welcome
				User: Spring AI
				Items: one, two, three
				Goodbye
				""");
	}

	/**
	 * Tests that numeric variables (both integer and floating-point) are correctly
	 * converted to strings during template rendering.
	 */
	@Test
	void shouldHandleNumericVariables() {
		JinjaTemplateRenderer renderer = JinjaTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("integer", 42);
		variables.put("float", 3.14);

		String result = renderer.apply("Integer: {{integer}}, Float: {{float}}", variables);

		assertThat(result).isEqualTo("Integer: 42, Float: 3.14");
	}

}
