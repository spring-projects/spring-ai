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

package org.springframework.ai.template;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link NoOpTemplateRenderer}.
 *
 * @author Thomas Vitale
 */
class NoOpTemplateRendererTests {

	@Test
	void shouldReturnUnchangedTemplate() {
		NoOpTemplateRenderer renderer = new NoOpTemplateRenderer();
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");

		String result = renderer.apply("Hello {name}!", variables);

		assertThat(result).isEqualTo("Hello {name}!");
	}

	@Test
	void shouldReturnUnchangedTemplateWithMultipleVariables() {
		NoOpTemplateRenderer renderer = new NoOpTemplateRenderer();
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		variables.put("name", "Spring AI");
		variables.put("punctuation", "!");

		String result = renderer.apply("{greeting} {name}{punctuation}", variables);

		assertThat(result).isEqualTo("{greeting} {name}{punctuation}");
	}

	@Test
	void shouldNotAcceptEmptyTemplate() {
		NoOpTemplateRenderer renderer = new NoOpTemplateRenderer();
		Map<String, Object> variables = new HashMap<>();

		assertThatThrownBy(() -> renderer.apply("", variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void shouldNotAcceptNullTemplate() {
		NoOpTemplateRenderer renderer = new NoOpTemplateRenderer();
		Map<String, Object> variables = new HashMap<>();

		assertThatThrownBy(() -> renderer.apply(null, variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void shouldNotAcceptNullVariables() {
		NoOpTemplateRenderer renderer = new NoOpTemplateRenderer();
		String template = "Hello!";

		assertThatThrownBy(() -> renderer.apply(template, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables cannot be null");
	}

	@Test
	void shouldNotAcceptVariablesWithNullKeySet() {
		NoOpTemplateRenderer renderer = new NoOpTemplateRenderer();
		String template = "Hello!";
		Map<String, Object> variables = new HashMap<>();
		variables.put(null, "Spring AI");

		assertThatThrownBy(() -> renderer.apply(template, variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables keys cannot be null");
	}

	@Test
	void shouldReturnUnchangedComplexTemplate() {
		NoOpTemplateRenderer renderer = new NoOpTemplateRenderer();
		Map<String, Object> variables = new HashMap<>();
		variables.put("header", "Welcome");
		variables.put("user", "Spring AI");
		variables.put("items", "one, two, three");
		variables.put("footer", "Goodbye");

		String template = """
				{header}
				User: {user}
				Items: {items}
				{footer}
				""";

		String result = renderer.apply(template, variables);

		assertThat(result).isEqualToNormalizingNewlines(template);
	}

}
