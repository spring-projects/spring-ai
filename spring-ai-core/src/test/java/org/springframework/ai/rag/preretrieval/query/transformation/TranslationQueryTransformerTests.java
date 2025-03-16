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

package org.springframework.ai.rag.preretrieval.query.transformation;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link TranslationQueryTransformer}.
 *
 * @author Thomas Vitale
 */
class TranslationQueryTransformerTests {

	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> TranslationQueryTransformer.builder().chatClientBuilder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenQueryIsNullThenThrow() {
		QueryTransformer queryTransformer = TranslationQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetLanguage("italian")
			.build();
		assertThatThrownBy(() -> queryTransformer.transform(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null");
	}

	@Test
	void whenPromptHasMissingTargetLanguagePlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Translate {query}");
		assertThatThrownBy(() -> TranslationQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetLanguage("italian")
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("targetLanguage");
	}

	@Test
	void whenPromptHasMissingQueryPlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Translate to {targetLanguage}");
		assertThatThrownBy(() -> TranslationQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.targetLanguage("italian")
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("query");
	}

}
