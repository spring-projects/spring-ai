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

package org.springframework.ai.template.st;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.template.ValidationMode;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StTemplateRenderer}.
 *
 * @author Thomas Vitale
 * @author Jewoo Shin
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
		Map<String, Object> variables = new HashMap<>();
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
	 */
	@Test
	void shouldHandleListVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();

		Map<String, Object> variables = new HashMap<>();
		variables.put("items", new String[] { "apple", "banana", "cherry" });

		String result = renderer.apply("Items: {items; separator=\", \"}", variables);

		assertThat(result).isEqualTo("Items: apple, banana, cherry");
	}

	/**
	 * Tests rendering with StringTemplate options.
	 */
	@Test
	void shouldRenderTemplateWithOptions() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();

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

	@Test
	void shouldNotRequireAnonymousSubtemplateFormalArgumentAsExternalVariable() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> myMap = new LinkedHashMap<>();
		myMap.put("a", 1);
		myMap.put("b", 2);
		myMap.put("c", 3);

		String result = renderer.apply("{myMap:{k | key={k}, value={myMap.(k)}\n}}", Map.of("myMap", myMap));

		assertThat(result).isEqualTo("key=a, value=1\nkey=b, value=2\nkey=c, value=3\n");
	}

	@Test
	void shouldNotRequireAnonymousSubtemplateIndexArgumentsAsExternalVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("items", List.of("a", "b"));

		String result = renderer.apply("{items:{item | {i0}:{i}:{item}\n}}", variables);

		assertThat(result).isEqualTo("0:1:a\n1:2:b\n");
	}

	@Test
	void shouldRequireItInsideAnonymousSubtemplateWithFormalArgument() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("items", List.of("a"));

		assertThatThrownBy(() -> renderer.apply("{items:{item | rootIt={it}, item={item}}}", variables))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Not all variables were replaced in the template. Missing variable names are: [it]");
	}

	@Test
	void shouldResolveItFromOuterScopeInsideAnonymousSubtemplateWithFormalArgument() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("items", List.of("a", "b"));
		variables.put("it", "outer");

		String result = renderer.apply("{items:{item | rootIt={it}, item={item}\n}}", variables);

		assertThat(result).isEqualTo("rootIt=outer, item=a\nrootIt=outer, item=b\n");
	}

	@Test
	void shouldNotRequireMultipleAnonymousSubtemplateFormalArgumentsAsExternalVariables() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("names", List.of("name1", "name2"));
		variables.put("phones", List.of("phone1", "phone2"));

		String result = renderer.apply("{names,phones:{n,p | {n}={p}\n}}", variables);

		assertThat(result).isEqualTo("name1=phone1\nname2=phone2\n");
	}

	@Test
	void shouldKeepNestedAnonymousSubtemplateScopesSeparate() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> item = new HashMap<>();
		item.put("name", "item1");
		item.put("values", List.of("a", "b"));

		String result = renderer.apply("{items:{item | {item.values:{x | {item.name}:{x}\n}}}}",
				Map.of("items", List.of(item)));

		assertThat(result).isEqualTo("item1:a\nitem1:b\n");
	}

	@Test
	void shouldRequireSameVariableNameOutsideAnonymousSubtemplateScope() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("myMap", Map.of("a", 1));

		assertThatThrownBy(() -> renderer.apply("{myMap:{k | key={k}}}{k}", variables))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Not all variables were replaced in the template. Missing variable names are: [k]");
	}

	@Test
	void shouldRequireMissingVariableInsideAnonymousSubtemplateBody() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("items", List.of("a"));

		assertThatThrownBy(() -> renderer.apply("{items:{item | {missing}}}", variables))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
					"Not all variables were replaced in the template. Missing variable names are: [missing]");
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

	/**
	 * Tests that property access syntax like {test.name} is correctly handled. The
	 * top-level variable 'test' should be identified as required, but 'name' should not.
	 */
	@Test
	void shouldHandlePropertyAccessSyntax() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("test", Map.of("name", "Spring AI"));

		String result = renderer.apply("Hello {test.name}!", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	/**
	 * Tests that deep property access syntax like {test.tom.name} is correctly handled.
	 * Only the top-level variable 'test' should be identified as required.
	 */
	@Test
	void shouldHandleDeepPropertyAccessSyntax() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		variables.put("test", Map.of("tom", Map.of("name", "Spring AI")));

		String result = renderer.apply("Hello {test.tom.name}!", variables);

		assertThat(result).isEqualTo("Hello Spring AI!");
	}

	/**
	 * Tests validation behavior with property access syntax. Should only require the
	 * top-level variable, not the property names.
	 */
	@Test
	void shouldValidatePropertyAccessCorrectly() {
		StTemplateRenderer renderer = StTemplateRenderer.builder().build();
		Map<String, Object> variables = new HashMap<>();
		// Only provide the top-level variable, not the properties
		variables.put("user", Map.of("profile", Map.of("name", "John")));

		// This should work fine since we provide the required top-level variable
		String result = renderer.apply("Hello {user.profile.name}!", variables);
		assertThat(result).isEqualTo("Hello John!");

		// Test with missing top-level variable - should throw exception
		Map<String, Object> missingVariables = new HashMap<>();
		// Wrong: providing nested variable instead of top-level
		missingVariables.put("profile", Map.of("name", "John"));

		assertThatThrownBy(() -> renderer.apply("Hello {user.profile.name}!", missingVariables))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
					"Not all variables were replaced in the template. Missing variable names are: [user]");
	}

}
