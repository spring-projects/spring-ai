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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PromptTemplate}.
 *
 * @author Thomas Vitale
 */
class PromptTemplateTests {

	@Test
	void createWithValidTemplate() {
		String template = "Hello {name}!";
		PromptTemplate promptTemplate = new PromptTemplate(template);
		assertThat(promptTemplate.getTemplate()).isEqualTo(template);
	}

	@Test
	void createWithEmptyTemplate() {
		assertThatThrownBy(() -> new PromptTemplate("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void createWithNullTemplate() {
		String template = null;
		assertThatThrownBy(() -> new PromptTemplate(template)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("template cannot be null or empty");
	}

	@Test
	void createWithValidResource() {
		String content = "Hello {name}!";
		Resource resource = new ByteArrayResource(content.getBytes());
		PromptTemplate promptTemplate = new PromptTemplate(resource);
		assertThat(promptTemplate.getTemplate()).isEqualTo(content);
	}

	@Test
	void createWithNullResource() {
		Resource resource = null;
		assertThatThrownBy(() -> new PromptTemplate(resource)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resource cannot be null");
	}

	@Test
	void createWithNullVariables() {
		String template = "Hello!";
		Map<String, Object> variables = null;
		assertThatThrownBy(() -> PromptTemplate.builder().template(template).variables(variables).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables cannot be null");
	}

	@Test
	void createWithNullVariableKeys() {
		String template = "Hello!";
		Map<String, Object> variables = new HashMap<>();
		variables.put(null, "value");
		assertThatThrownBy(() -> PromptTemplate.builder().template(template).variables(variables).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables keys cannot be null");
	}

	@Test
	void addVariable() {
		PromptTemplate promptTemplate = new PromptTemplate("Hello {name}!");
		promptTemplate.add("name", "Spring AI");
		assertThat(promptTemplate.render()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void renderWithoutVariables() {
		PromptTemplate promptTemplate = new PromptTemplate("Hello!");
		assertThat(promptTemplate.render()).isEqualTo("Hello!");
	}

	@Test
	void renderWithVariables() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");
		PromptTemplate promptTemplate = PromptTemplate.builder().template("Hello {name}!").variables(variables).build();
		assertThat(promptTemplate.render()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void renderWithAdditionalVariables() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("{greeting} {name}!")
			.variables(variables)
			.build();

		Map<String, Object> additionalVariables = new HashMap<>();
		additionalVariables.put("name", "Spring AI");
		assertThat(promptTemplate.render(additionalVariables)).isEqualTo("Hello Spring AI!");
	}

	@Test
	void renderWithResourceVariable() {
		String resourceContent = "Spring AI";
		Resource resource = new ByteArrayResource(resourceContent.getBytes());
		Map<String, Object> variables = new HashMap<>();
		variables.put("content", resource);

		PromptTemplate promptTemplate = new PromptTemplate("Hello {content}!");
		assertThat(promptTemplate.render(variables)).isEqualTo("Hello Spring AI!");
	}

	@Test
	void createMessageWithoutVariables() {
		PromptTemplate promptTemplate = new PromptTemplate("Hello!");
		Message message = promptTemplate.createMessage();
		assertThat(message).isInstanceOf(UserMessage.class);
		assertThat(message.getText()).isEqualTo("Hello!");
	}

	@Test
	void createMessageWithVariables() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");
		PromptTemplate promptTemplate = new PromptTemplate("Hello {name}!");
		Message message = promptTemplate.createMessage(variables);
		assertThat(message).isInstanceOf(UserMessage.class);
		assertThat(message.getText()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void createPromptWithoutVariables() {
		PromptTemplate promptTemplate = new PromptTemplate("Hello!");
		Prompt prompt = promptTemplate.create();
		assertThat(prompt.getContents()).isEqualTo("Hello!");
	}

	@Test
	void createPromptWithVariables() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");
		PromptTemplate promptTemplate = PromptTemplate.builder().template("Hello {name}!").variables(variables).build();
		Prompt prompt = promptTemplate.create(variables);
		assertThat(prompt.getContents()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void createWithCustomRenderer() {
		TemplateRenderer customRenderer = new NoOpTemplateRenderer();
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("Hello {name}!")
			.renderer(customRenderer)
			.build();
		assertThat(promptTemplate.render()).isEqualTo("Hello {name}!");
	}

	@Test
	void builderShouldNotAllowBothTemplateAndResource() {
		String template = "Hello!";
		Resource resource = new ByteArrayResource(template.getBytes());

		assertThatThrownBy(() -> PromptTemplate.builder().template(template).resource(resource).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Only one of template or resource can be set");
	}

	// --- Builder Pattern Tests ---

	@Test
	void createWithValidTemplate_Builder() {
		String template = "Hello {name}!";
		PromptTemplate promptTemplate = PromptTemplate.builder().template(template).build();
		// Render with the required variable to check the template string was set
		// correctly
		assertThat(promptTemplate.render(Map.of("name", "Test"))).isEqualTo("Hello Test!");
	}

	@Test
	void renderWithVariables_Builder() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Spring AI");
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("Hello {name}!")
			.variables(variables) // Use builder's variable method
			.build();
		assertThat(promptTemplate.render()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void createWithValidResource_Builder() {
		String content = "Hello {name}!";
		Resource resource = new ByteArrayResource(content.getBytes());
		PromptTemplate promptTemplate = PromptTemplate.builder().resource(resource).build();
		// Render with the required variable to check the resource was read correctly
		assertThat(promptTemplate.render(Map.of("name", "Resource"))).isEqualTo("Hello Resource!");
	}

	@Test
	void addVariable_Builder() {
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("Hello {name}!")
			.variables(Map.of("name", "Spring AI")) // Use variables() method
			.build();
		assertThat(promptTemplate.render()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void renderWithoutVariables_Builder() {
		PromptTemplate promptTemplate = PromptTemplate.builder().template("Hello!").build();
		assertThat(promptTemplate.render()).isEqualTo("Hello!");
	}

	@Test
	void renderWithAdditionalVariables_Builder() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("{greeting} {name}!")
			.variables(variables) // Set default variables via builder
			.build();

		Map<String, Object> additionalVariables = new HashMap<>();
		additionalVariables.put("name", "Spring AI");
		// Pass additional variables during render - should merge with defaults
		assertThat(promptTemplate.render(additionalVariables)).isEqualTo("Hello Spring AI!");
	}

	@Test
	void renderWithResourceVariable_Builder() {
		String resourceContent = "Spring AI";
		Resource resource = new ByteArrayResource(resourceContent.getBytes());
		Map<String, Object> variables = new HashMap<>();
		variables.put("content", resource);

		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("Hello {content}!")
			.variables(variables) // Set resource variable via builder
			.build();
		assertThat(promptTemplate.render()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void variablesOverwriting_Builder() {
		Map<String, Object> initialVars = Map.of("name", "Initial", "adj", "Good");
		Map<String, Object> overwriteVars = Map.of("name", "Overwritten", "noun", "Day");

		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("Hello {name} {noun}!")
			.variables(initialVars) // Set initial variables
			.variables(overwriteVars) // Overwrite with new variables
			.build();

		// Expect only variables from the last call to be present
		assertThat(promptTemplate.render()).isEqualTo("Hello Overwritten Day!");
	}

	@Test
	void customRenderer_Builder() {
		String template = "This is a test.";
		TemplateRenderer customRenderer = new CustomTestRenderer();

		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.renderer(customRenderer) // Set custom renderer
			.build();

		assertThat(promptTemplate.render()).isEqualTo(template + " (Rendered by Custom)");
	}

	@Test
	void resource_Builder() {
		String templateContent = "Hello {name} from Resource!";
		Resource templateResource = new ByteArrayResource(templateContent.getBytes());
		Map<String, Object> vars = Map.of("name", "Builder");

		PromptTemplate promptTemplate = PromptTemplate.builder().resource(templateResource).variables(vars).build();

		assertThat(promptTemplate.render()).isEqualTo("Hello Builder from Resource!");
	}

	@Test
	void renderWithResourceFile() {
		Resource resource = new ClassPathResource("prompt-user.txt");

		// Build PromptTemplate: bind the Resource to "name" in this.variables
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template("How {name}")
			.variables(Map.of("name", resource))
			.build();

		assertThat(promptTemplate.render(Map.of())).isEqualTo("How Hello, world!");
	}

	// Helper Custom Renderer for testing
	private static class CustomTestRenderer implements TemplateRenderer {

		@Override
		public String apply(String template, Map<String, ? extends @Nullable Object> model) {
			// Simple renderer that just appends a marker
			// Note: This simple renderer ignores the model map for test purposes.
			return template + " (Rendered by Custom)";
		}

	}

}
