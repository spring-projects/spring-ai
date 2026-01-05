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
 * Unit tests for {@link CompressionQueryTransformer}.
 *
 * @author Thomas Vitale
 */
class CompressionQueryTransformerTests {

	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> CompressionQueryTransformer.builder().chatClientBuilder(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenQueryIsNullThenThrow() {
		QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.build();
		assertThatThrownBy(() -> queryTransformer.transform(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null");
	}

	@Test
	void whenPromptHasMissingHistoryPlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Compress {query}");
		assertThatThrownBy(() -> CompressionQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("history");
	}

	@Test
	void whenPromptHasMissingQueryPlaceholderThenThrow() {
		PromptTemplate customPromptTemplate = new PromptTemplate("Compress {history}");
		assertThatThrownBy(() -> CompressionQueryTransformer.builder()
			.chatClientBuilder(mock(ChatClient.Builder.class))
			.promptTemplate(customPromptTemplate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The following placeholders must be present in the prompt template")
			.hasMessageContaining("query");
	}

}
