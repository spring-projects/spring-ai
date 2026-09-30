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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompressionQueryTransformer}.
 *
 * @author Thomas Vitale
 * @author Xuhan Zhuang
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
	void whenHistoryIsEmptyThenReturnQueryUnchanged() {
		ChatModel chatModel = mock(ChatModel.class);
		QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
			.chatClientBuilder(ChatClient.builder(chatModel))
			.build();

		Query query = new Query("What is a Moonstone?");

		assertThat(queryTransformer.transform(query)).isSameAs(query);
		verifyNoInteractions(chatModel);
	}

	@Test
	void whenHistoryHasNoUserOrAssistantMessagesThenReturnQueryUnchanged() {
		ChatModel chatModel = mock(ChatModel.class);
		QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
			.chatClientBuilder(ChatClient.builder(chatModel))
			.build();

		Query query = Query.builder()
			.text("What is a Moonstone?")
			.history(new SystemMessage("You are a wizard!"),
					ToolResponseMessage.builder()
						.responses(List.of(new ToolResponseMessage.ToolResponse("1", "brew", "Felix Felicis")))
						.build())
			.build();

		assertThat(queryTransformer.transform(query)).isSameAs(query);
		verifyNoInteractions(chatModel);
	}

	@Test
	void whenHistoryIsPresentThenCompressIntoStandaloneQuery() {
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture())).willReturn(ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("What is a powdered Gold?"))))
			.build());

		QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
			.chatClientBuilder(ChatClient.builder(chatModel))
			.build();

		Query query = Query.builder()
			.text("And a powdered Gold?")
			.history(new UserMessage("What is a Moonstone?"), new AssistantMessage("A silvery-white gemstone."))
			.build();

		assertThat(queryTransformer.transform(query).text()).isEqualTo("What is a powdered Gold?");
		assertThat(promptCaptor.getValue().getContents()).containsIgnoringNewLines("""
				Conversation history:
				USER: What is a Moonstone?
				ASSISTANT: A silvery-white gemstone.

				Follow-up query:
				And a powdered Gold?
				""");
	}

}
