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
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link FactCheckingEvaluator}.
 *
 * @author guan xu
 * @author Yanming Zhou
 */
@ExtendWith(MockitoExtension.class)
class FactCheckingEvaluatorTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@SuppressWarnings("deprecation")
	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> FactCheckingEvaluator.builder(null).build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ChatClientBuilder cannot be null");
	}

	@SuppressWarnings("deprecation")
	@Test
	void whenEvaluationPromptIsNullThenUseDefaultEvaluationPromptText() {
		FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(ChatClient.builder(mock(ChatModel.class)))
			.build();
		assertThat(evaluator).isNotNull();
	}

	@Test
	void whenForBespokeMinicheckThenUseBespokeEvaluationPromptText() {
		FactCheckingEvaluator evaluator = FactCheckingEvaluator
			.forBespokeMinicheck(ChatClient.builder(mock(ChatModel.class)));
		assertThat(evaluator).isNotNull();
	}

	@SuppressWarnings("deprecation")
	@ParameterizedTest
	@ValueSource(strings = { "yes", " yes", "yes ", " yes ", "YES", " YES\n", "\tYes\t" })
	void whenEvaluationResponseIsYesWithWhitespaceThenPass(String response) {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(ChatClient.builder(this.chatModel)).build();
		EvaluationResponse result = evaluator.evaluate(new EvaluationRequest("query", List.of(), "response content"));

		assertThat(result.isPass()).isTrue();
	}

	@SuppressWarnings("deprecation")
	@ParameterizedTest
	@ValueSource(strings = { "no", " no ", "yes and more", "maybe" })
	void whenEvaluationResponseIsNotYesThenFail(String response) {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(ChatClient.builder(this.chatModel)).build();
		EvaluationResponse result = evaluator.evaluate(new EvaluationRequest("query", List.of(), "response content"));

		assertThat(result.isPass()).isFalse();
	}

	@Test
	void whenCreatedWithChatClientBuilderThenEvaluatorIsCreated() {
		FactCheckingEvaluator evaluator = new FactCheckingEvaluator(ChatClient.builder(mock(ChatModel.class)));

		assertThat(evaluator).isNotNull();
	}

	@Test
	void whenConstructorChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> new FactCheckingEvaluator(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenCreatedWithNewBuilderThenEvaluatorIsCreated() {
		FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(mock(ChatModel.class)))
			.build();

		assertThat(evaluator).isNotNull();
	}

	@Test
	void whenNewBuilderChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> FactCheckingEvaluator.builder().build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ChatClientBuilder cannot be null");
	}

	@Test
	void whenPromptTemplateIsConfiguredThenUseIt() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("yes")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		PromptTemplate promptTemplate = new PromptTemplate("""
				Custom fact check
				Document: {document}
				Claim: {claim}
				""");
		FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(this.chatModel))
			.promptTemplate(promptTemplate)
			.build();

		evaluator
			.evaluate(new EvaluationRequest("query", List.of(new Document("supporting context")), "response content"));

		assertThat(this.promptCaptor.getValue().getContents()).contains("Custom fact check", "supporting context",
				"response content");
	}

	@SuppressWarnings("deprecation")
	@Test
	void whenLegacyEvaluationPromptIsConfiguredThenUseIt() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("yes")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(ChatClient.builder(this.chatModel))
			.evaluationPrompt("""
					Legacy fact check
					Document: {document}
					Claim: {claim}
					""")
			.build();

		evaluator
			.evaluate(new EvaluationRequest("query", List.of(new Document("supporting context")), "response content"));

		assertThat(this.promptCaptor.getValue().getContents()).contains("Legacy fact check", "supporting context",
				"response content");
	}

	@Test
	void whenProtectedStringConstructorIsUsedThenUsePrompt() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("yes")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		FactCheckingEvaluator evaluator = new FactCheckingEvaluator(ChatClient.builder(this.chatModel), """
				Protected constructor fact check
				Document: {document}
				Claim: {claim}
				""");

		evaluator
			.evaluate(new EvaluationRequest("query", List.of(new Document("supporting context")), "response content"));

		assertThat(this.promptCaptor.getValue().getContents()).contains("Protected constructor fact check",
				"supporting context", "response content");
	}

	@SuppressWarnings("deprecation")
	@Test
	void whenLegacyEvaluationPromptIsNullThenThrow() {
		assertThatThrownBy(() -> FactCheckingEvaluator.builder(ChatClient.builder(mock(ChatModel.class)))
			.evaluationPrompt(null)
			.build()).isInstanceOf(IllegalStateException.class).hasMessageContaining("EvaluationPrompt cannot be null");
	}

}
