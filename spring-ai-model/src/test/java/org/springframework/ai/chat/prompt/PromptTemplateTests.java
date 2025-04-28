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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.HashMap;
import java.util.Map;

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
		assertThatThrownBy(() -> new PromptTemplate(template, variables)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("variables cannot be null");
	}

	@Test
	void createWithNullVariableKeys() {
		String template = "Hello!";
		Map<String, Object> variables = new HashMap<>();
		variables.put(null, "value");
		assertThatThrownBy(() -> new PromptTemplate(template, variables)).isInstanceOf(IllegalArgumentException.class)
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
		PromptTemplate promptTemplate = new PromptTemplate("Hello {name}!", variables);
		assertThat(promptTemplate.render()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void renderWithAdditionalVariables() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("greeting", "Hello");
		PromptTemplate promptTemplate = new PromptTemplate("{greeting} {name}!", variables);

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
		PromptTemplate promptTemplate = new PromptTemplate("Hello {name}!");
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

}
