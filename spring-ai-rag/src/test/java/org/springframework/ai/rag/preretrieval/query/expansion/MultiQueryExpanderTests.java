/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.rag.preretrieval.query.expansion;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link MultiQueryExpander}.
 *
 * @author Thomas Vitale
 */
class MultiQueryExpanderTests {

	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> MultiQueryExpander.builder().chatClientBuilder(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenQueryIsNullThenThrow() {
		QueryExpander queryExpander = MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.build();
		assertThatThrownBy(() -> queryExpander.expand(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null");
	}

	@Test
	void whenPromptHasMissingNumberPlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("You are the boss. Original query: {query}");
		assertThatThrownBy(() -> MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("number");
	}

	@Test
	void whenPromptHasMissingQueryPlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("You are the boss. Number of queries: {number}");
		assertThatThrownBy(() -> MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("query");
	}

	@Test
	void whenBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> MultiQueryExpander.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenPromptTemplateIsNullThenUseDefault() {
		MultiQueryExpander queryExpander = MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(null)
			.build();
		assertThat(queryExpander).isNotNull();
	}

	@Test
	void whenPromptTemplateHasBothPlaceholdersThenBuild() {
		PromptTemplate validTemplate = new PromptTemplate("Generate {number} variations of: {query}");

		MultiQueryExpander expander = MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(validTemplate)
			.build();

		assertThat(expander).isNotNull();
	}

	@Test
	void whenPromptTemplateHasExtraPlaceholdersThenBuild() {
		PromptTemplate templateWithExtra = new PromptTemplate(
				"Generate {number} variations of: {query}. Context: {context}");

		MultiQueryExpander expander = MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(templateWithExtra)
			.build();

		assertThat(expander).isNotNull();
	}

	@Test
	void whenBuilderSetMultipleTimesThenUseLastValue() {
		ChatClient.Builder firstBuilder = mock(ChatClient.Builder.class);
		ChatClient.Builder secondBuilder = mock(ChatClient.Builder.class);

		MultiQueryExpander expander = MultiQueryExpander.builder()
			.chatClientBuilder(firstBuilder)
			.chatClientBuilder(secondBuilder)
			.build();

		assertThat(expander).isNotNull();
	}

	@Test
	void whenPromptTemplateSetToNullAfterValidTemplateThenUseDefault() {
		PromptTemplate validTemplate = new PromptTemplate("Config: {number} values for {query}");

		MultiQueryExpander expander = MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(validTemplate)
			.promptTemplate(null)
			.build();

		assertThat(expander).isNotNull();
	}

	@Test
	void whenPromptTemplateHasPlaceholdersInDifferentCaseThenThrow() {
		PromptTemplate templateWithWrongCase = new PromptTemplate("Generate {NUMBER} variations of: {QUERY}");

		assertThatThrownBy(() -> MultiQueryExpander.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(templateWithWrongCase)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template");
	}

}
