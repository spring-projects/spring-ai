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

package org.springframework.ai.template.mustache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.template.ValidationMode;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MustacheTemplateRenderer}.
 *
 * @author Hyunjoon Park
 */
class MustacheTemplateRendererTests {

	// Builder tests

	@Test
	void shouldNotAcceptNullValidationMode() {
		assertThatThrownBy(() -> MustacheTemplateRenderer.builder().validationMode(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("validationMode cannot be null");
	}

	@Test
	void shouldNotAcceptNullMustacheFactory() {
		assertThatThrownBy(() -> MustacheTemplateRenderer.builder().mustacheFactory(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("mustacheFactory cannot be null");
	}

	@Test
	void shouldUseDefaultValuesWhenUsingBuilder() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		assertThat(ReflectionTestUtils.getField(renderer, "validationMode")).isEqualTo(ValidationMode.THROW);
		assertThat(ReflectionTestUtils.getField(renderer, "mustacheFactory")).isNotNull();
	}

	// Basic rendering tests

	@Test
	void shouldRenderTemplateWithSingleVariable() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");

		String result = renderer.apply("Hello {{name}}!", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	@Test
	void shouldRenderTemplateWithMultipleVariables() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		variables.put("name", "Spring AI");
		variables.put("punctuation", "!");

		String result = renderer.apply("{{greeting}} {{name}}{{punctuation}}", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	@Test
	void shouldRenderMultilineTemplate() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("title", "Welcome");
		variables.put("content", "This is the content");

		String template = """
				Title: {{title}}
				Content: {{content}}
				""";
		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("""
				Title: Welcome
				Content: This is the content
				""");
	}

	// Input validation tests

	@Test
	void shouldNotRenderEmptyTemplate() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();

		assertThatThrownBy(() -> renderer.apply("", variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void shouldNotRenderNullTemplate() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();

		assertThatThrownBy(() -> renderer.apply(null, variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void shouldNotAcceptNullVariables() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();

		assertThatThrownBy(() -> renderer.apply("Hello!", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables cannot be null");
	}

	@Test
	void shouldNotAcceptNullVariableKeys() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put(null, "value");

		assertThatThrownBy(() -> renderer.apply("Hello!", variables)).isInstanceOf(IllegalArgumentException.class);
	}

	// ValidationMode tests

	@Test
	void shouldThrowExceptionForMissingVariablesInThrowMode() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		assertThatThrownBy(() -> renderer.apply("{{greeting}} {{name}}!", variables))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Not all variables were replaced in the template")
			.hasMessageContaining("name");
	}

	@Test
	void shouldContinueRenderingWithMissingVariablesInWarnMode() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder()
			.validationMode(ValidationMode.WARN)
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		String result = renderer.apply("{{greeting}} {{name}}!", variables);

		assertThat(result).isEqualTo("Hello !");
	}

	@Test
	void shouldRenderWithoutValidationInNoneMode() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder()
			.validationMode(ValidationMode.NONE)
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");

		String result = renderer.apply("{{greeting}} {{name}}!", variables);

		assertThat(result).isEqualTo("Hello !");
	}

	// Mustache-specific feature tests

	@Test
	void shouldRenderSectionWithIterable() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder()
			.validationMode(ValidationMode.NONE)
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("items", List.of(Map.of("name", "Item 1", "price", 100), Map.of("name", "Item 2", "price", 200)));

		String template = "{{#items}}- {{name}}: {{price}}\n{{/items}}";
		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("- Item 1: 100\n- Item 2: 200\n");
	}

	@Test
	void shouldRenderNestedProperties() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("user", Map.of("profile", Map.of("name", "John")));

		String result = renderer.apply("Hello {{user.profile.name}}!", variables);

		assertThat(result).isEqualTo("Hello John!");
	}

	@Test
	void shouldRenderInvertedSection() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder()
			.validationMode(ValidationMode.NONE)
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("items", List.of());

		String template = "{{#items}}Has items{{/items}}{{^items}}No items{{/items}}";
		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("No items");
	}

	@Test
	void shouldRenderSectionWithNonEmptyList() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder()
			.validationMode(ValidationMode.NONE)
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("items", List.of("a", "b", "c"));

		String template = "{{#items}}Has items{{/items}}{{^items}}No items{{/items}}";
		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("Has itemsHas itemsHas items");
	}

	@Test
	void shouldRenderBooleanSection() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("showGreeting", true);
		variables.put("name", "World");

		String template = "{{#showGreeting}}Hello {{name}}!{{/showGreeting}}";
		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("Hello World!");
	}

	@Test
	void shouldNotRenderFalseBooleanSection() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("showGreeting", false);
		variables.put("name", "World");

		String template = "{{#showGreeting}}Hello {{name}}!{{/showGreeting}}Goodbye!";
		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("Goodbye!");
	}

	// Static text tests

	@Test
	void shouldRenderStaticTextTemplate() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();

		String result = renderer.apply("Just static text.", variables);

		assertThat(result).isEqualTo("Just static text.");
	}

	// Null value tests

	@Test
	void shouldRenderNullVariableValuesAsBlank() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder()
			.validationMode(ValidationMode.NONE)
			.build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("foo", null);

		String result = renderer.apply("Value: {{foo}}", variables);

		assertThat(result).isEqualTo("Value: ");
	}

	// Complex template tests (from issue example)

	@Test
	void shouldRenderComplexTemplateFromIssueExample() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("query", "Find me the best deals");
		variables.put("items", List.of(Map.of("name", "Product A", "pricing", Map.of("salePrice", "$99.99")),
				Map.of("name", "Product B", "pricing", Map.of("salePrice", "$149.99"))));

		String template = """
				Query: {{query}}

				{{#items}}
				- {{name}} ({{pricing.salePrice}})
				{{/items}}
				""";

		String result = renderer.apply(template, variables);

		assertThat(result).isEqualTo("""
				Query: Find me the best deals

				- Product A ($99.99)
				- Product B ($149.99)
				""");
	}

	// Unicode and special character tests

	@Test
	void shouldRenderUnicodeCharacters() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		variables.put("emoji", "\uD83D\uDE00");

		String result = renderer.apply("{{greeting}} {{emoji}}", variables);

		assertThat(result).isEqualTo("Hello \uD83D\uDE00");
	}

	@Test
	void shouldRenderAccentedCharacters() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("place", "Caf\u00e9");

		String result = renderer.apply("Welcome to {{place}}", variables);

		assertThat(result).isEqualTo("Welcome to Caf\u00e9");
	}

	// Number variable tests

	@Test
	void shouldRenderNumericVariables() {
		MustacheTemplateRenderer renderer = MustacheTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("count", 42);
		variables.put("price", 19.99);

		String result = renderer.apply("Count: {{count}}, Price: {{price}}", variables);

		assertThat(result).isEqualTo("Count: 42, Price: 19.99");
	}

}
