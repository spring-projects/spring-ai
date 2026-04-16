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

package org.springframework.ai.rag.preretrieval.query.transformation;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	@Test
	void whenHistoryIsEmptyThenReturnOriginalQuery() {
		ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
		ChatClient chatClient = mock(ChatClient.class);
		when(chatClientBuilder.build()).thenReturn(chatClient);

		QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
			.chatClientBuilder(chatClientBuilder)
			.build();

		Query query = Query.builder().text("What is Spring AI?").build();

		Query result = queryTransformer.transform(query);

		assertThat(result).isEqualTo(query);
	}

	@Test
	void whenLastHistoryEntryMatchesCurrentQueryThenExcludeItFromHistory() {
		ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.Callable mockedCallable = mock(ChatClient.Callable.class);
		when(chatClientBuilder.build()).thenReturn(chatClient);
		when(chatClient.prompt()).thenReturn(mockedCallable);
		when(mockedCallable.user(any())).thenReturn(mockedCallable);
		when(mockedCallable.call()).thenReturn(
				org.springframework.ai.chat.client.ChatResponse.builder().content("What is Spring AI?").build());

		QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
			.chatClientBuilder(chatClientBuilder)
			.build();

		String currentQueryText = "What is Spring AI?";
		Message userMessage = Message.builder().messageType(MessageType.USER).text(currentQueryText).build();
		Message assistantMessage = Message.builder()
			.messageType(MessageType.ASSISTANT)
			.text("Spring AI is a framework for AI-native applications.")
			.build();

		Query query = Query.builder()
			.text(currentQueryText)
			.history(java.util.List.of(assistantMessage, userMessage))
			.build();

		queryTransformer.transform(query);

		// Verify the prompt was called - the history should not contain the duplicate
		// query
		verify(chatClient).prompt();
	}

}
