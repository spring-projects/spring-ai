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
package org.springframework.ai.chat.prompt;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests focused on the {@link PromptTemplate.Builder} input validation and edge
 * cases.
 */
class PromptTemplateBuilderTests {

	@Test
	void builderNullTemplateShouldThrow() {
		assertThatThrownBy(() -> PromptTemplate.builder().template(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void builderEmptyTemplateShouldThrow() {
		assertThatThrownBy(() -> PromptTemplate.builder().template("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void builderNullResourceShouldThrow() {
		assertThatThrownBy(() -> PromptTemplate.builder().resource(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resource cannot be null");
	}

	@Test
	void builderNullVariablesShouldThrow() {
		assertThatThrownBy(() -> PromptTemplate.builder().variables(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables cannot be null");
	}

	@Test
	void builderNullVariableKeyShouldThrow() {
		Map<String, Object> variables = new HashMap<>();
		variables.put(null, "value");
		assertThatThrownBy(() -> PromptTemplate.builder().variables(variables))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables keys cannot be null");
	}

	@Test
	void builderNullRendererShouldThrow() {
		assertThatThrownBy(() -> PromptTemplate.builder().renderer(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("renderer cannot be null");
	}

	@Test
	void renderWithMissingVariableShouldThrow() {
		// Using the default ST4 template renderer
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("Hello {name}!")
			// No variables provided
			.build();

		// Expecting an exception because 'name' is required by the template but not
		// supplied
		try {
			promptTemplate.render();
			// If render() doesn't throw, fail the test
			Assertions.fail("Expected IllegalStateException was not thrown.");
		}
		catch (IllegalStateException e) {
			// Assert that the message is exactly the expected string
			assertThat(e.getMessage())
				.isEqualTo("Not all variables were replaced in the template. Missing variable names are: [name].");
		}
		catch (Exception e) {
			// Fail if any other unexpected exception is caught
			Assertions.fail("Caught unexpected exception: " + e.getClass().getName());
		}
	}

}
