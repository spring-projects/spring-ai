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

package org.springframework.ai.chat.evaluation;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RelevancyEvaluator}.
 *
 * @author Thomas Vitale
 */
@ExtendWith(MockitoExtension.class)
class RelevancyEvaluatorTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> new RelevancyEvaluator(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");

		assertThatThrownBy(() -> RelevancyEvaluator.builder().chatClientBuilder(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenPromptTemplateIsNullThenUseDefault() {
		RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(mock(ChatModel.class)));
		assertThat(evaluator).isNotNull();

		evaluator = RelevancyEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(mock(ChatModel.class)))
			.promptTemplate(null)
			.build();
		assertThat(evaluator).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "yes", " yes", "yes ", " yes ", "YES", " YES\n", "\tYes\t" })
	void whenEvaluationResponseIsYesWithWhitespaceThenPass(String response) {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(this.chatModel));
		EvaluationResponse result = evaluator.evaluate(new EvaluationRequest("query", List.of(), "response content"));

		assertThat(result.isPass()).isTrue();
		assertThat(result.getScore()).isEqualTo(1.0f);
		assertThat(result.getFeedback()).isEqualTo(response);
		assertThat(result.getMetadata()).containsEntry(RelevancyEvaluator.LLM_RAW_RESPONSE_METADATA_KEY, response);
	}

	@ParameterizedTest
	@ValueSource(strings = { "no", " no ", "yes and more", "maybe" })
	void whenEvaluationResponseIsNotYesThenFail(String response) {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(this.chatModel));
		EvaluationResponse result = evaluator.evaluate(new EvaluationRequest("query", List.of(), "response content"));

		assertThat(result.isPass()).isFalse();
		assertThat(result.getScore()).isEqualTo(0.0f);
		assertThat(result.getFeedback()).isEqualTo(response);
		assertThat(result.getMetadata()).containsEntry(RelevancyEvaluator.LLM_RAW_RESPONSE_METADATA_KEY, response);
	}

	@Test
	void whenLlmReturnsNullThenPassIsFalseAndFeedbackIsEmpty() {
		ChatClient.Builder builder = mock(ChatClient.Builder.class);
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

		when(builder.build()).thenReturn(chatClient);
		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn(null);

		EvaluationResponse result = RelevancyEvaluator.builder()
			.chatClientBuilder(builder)
			.build()
			.evaluate(new EvaluationRequest("What is Spring?", List.of(), "Spring is a Java framework."));

		assertThat(result.isPass()).isFalse();
		assertThat(result.getScore()).isEqualTo(0.0f);
		assertThat(result.getFeedback()).isEmpty();
	}

}
