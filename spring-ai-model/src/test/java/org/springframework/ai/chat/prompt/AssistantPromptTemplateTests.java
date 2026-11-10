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

package org.springframework.ai.chat.prompt;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AssistantPromptTemplate}.
 */
class AssistantPromptTemplateTests {

	@Test
	void createPromptWithVariables() {
		AssistantPromptTemplate assistantPromptTemplate = new AssistantPromptTemplate("Hello {name}!");
		Prompt prompt = assistantPromptTemplate.create(Map.of("name", "Spring AI"));
		assertThat(prompt.getInstructions()).singleElement().isInstanceOf(AssistantMessage.class);
		assertThat(prompt.getContents()).isEqualTo("Hello Spring AI!");
	}

	@Test
	void createPromptWithChatOptions() {
		AssistantPromptTemplate assistantPromptTemplate = new AssistantPromptTemplate("Hello!");
		ChatOptions chatOptions = ChatOptions.builder().model("test-model").build();
		Prompt prompt = assistantPromptTemplate.create(chatOptions);
		assertThat(prompt.getInstructions()).singleElement().isInstanceOf(AssistantMessage.class);
		assertThat(prompt.getContents()).isEqualTo("Hello!");
		assertThat(prompt.getOptions()).isEqualTo(chatOptions);
	}

	@Test
	void createPromptWithVariablesAndChatOptions() {
		AssistantPromptTemplate assistantPromptTemplate = new AssistantPromptTemplate("Hello {name}!");
		ChatOptions chatOptions = ChatOptions.builder().model("test-model").build();
		Prompt prompt = assistantPromptTemplate.create(Map.of("name", "Spring AI"), chatOptions);
		assertThat(prompt.getInstructions()).singleElement().isInstanceOf(AssistantMessage.class);
		assertThat(prompt.getContents()).isEqualTo("Hello Spring AI!");
		assertThat(prompt.getOptions()).isEqualTo(chatOptions);
	}

}
