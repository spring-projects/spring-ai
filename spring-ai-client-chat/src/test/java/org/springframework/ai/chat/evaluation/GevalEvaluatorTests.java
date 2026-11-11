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
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link GevalEvaluator}.
 */
@ExtendWith(MockitoExtension.class)
class GevalEvaluatorTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	void whenRequiredBuilderValuesAreMissingThenThrow() {
		assertThatThrownBy(() -> GevalEvaluator.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");

		assertThatThrownBy(() -> GevalEvaluator.builder().chatClientBuilder(ChatClient.builder(this.chatModel)).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("criteria cannot be null or empty");
	}

	@Test
	void whenStepsAreGeneratedThenUseThemToScoreResponse() {
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(
				new ChatResponse(List
					.of(new Generation(new AssistantMessage("{\"steps\":[\"Check relevance\",\"Check clarity\"]}")))),
				new ChatResponse(List.of(new Generation(new AssistantMessage(
						"{\"score\":8,\"reason\":\"The reply directly addresses the requested topic.\"}")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		GevalEvaluator evaluator = GevalEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(this.chatModel))
			.criteria("The reply is relevant and clear")
			.build();
		EvaluationResponse result = evaluator
			.evaluate(new EvaluationRequest("Explain the topic", List.of(), "A clear answer"));

		assertThat(result.isPass()).isFalse();
		assertThat(result.getScore()).isEqualTo(8.0f);
		assertThat(result.getFeedback()).isEqualTo("The reply directly addresses the requested topic.");
		assertThat(this.promptCaptor.getAllValues()).hasSize(2);
		assertThat(this.promptCaptor.getAllValues().get(0).getContents()).contains("The reply is relevant and clear");
		assertThat(this.promptCaptor.getAllValues().get(1).getContents()).contains("- Check relevance",
				"- Check clarity", "0 to 10", "Explain the topic", "A clear answer");
	}

	@Test
	void whenEvaluatingMultipleResponsesThenReuseGeneratedSteps() {
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("{\"steps\":[\"Check relevance\"]}")))),
				new ChatResponse(
						List.of(new Generation(new AssistantMessage("{\"score\":8,\"reason\":\"First response\"}")))),
				new ChatResponse(
						List.of(new Generation(new AssistantMessage("{\"score\":9,\"reason\":\"Second response\"}")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		GevalEvaluator evaluator = GevalEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(this.chatModel))
			.criteria("The reply is relevant")
			.build();
		evaluator.evaluate(new EvaluationRequest("Question one", List.of(), "Answer one"));
		evaluator.evaluate(new EvaluationRequest("Question two", List.of(), "Answer two"));

		assertThat(this.promptCaptor.getAllValues()).hasSize(3);
		assertThat(this.promptCaptor.getAllValues().get(0).getContents()).contains("generate 3-4 concise evaluation",
				"steps applicable to any text");
		assertThat(this.promptCaptor.getAllValues().get(1).getContents()).contains("Question one");
		assertThat(this.promptCaptor.getAllValues().get(2).getContents()).contains("Question two");
	}

	@Test
	void whenScoreIsOutsideZeroToTenThenThrow() {
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("{\"steps\":[\"Check criterion\"]}")))),
				new ChatResponse(
						List.of(new Generation(new AssistantMessage("{\"score\":11,\"reason\":\"reason\"}")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		GevalEvaluator evaluator = GevalEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(this.chatModel))
			.criteria("criterion")
			.build();
		assertThatThrownBy(() -> evaluator.evaluate(new EvaluationRequest("input", List.of(), "output")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("score must be between 0 and 10");
	}

	@Test
	void whenMaxValueIsOneThenEvaluateAsBinary() {
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("{\"steps\":[\"Check criterion\"]}")))),
				new ChatResponse(List.of(new Generation(
						new AssistantMessage("{\"score\":1,\"reason\":\"The criterion is satisfied.\"}")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		GevalEvaluator evaluator = GevalEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(this.chatModel))
			.criteria("binary criterion")
			.maxValue(1)
			.build();
		EvaluationResponse result = evaluator.evaluate(new EvaluationRequest("input", List.of(), "output"));

		assertThat(result.isPass()).isTrue();
		assertThat(result.getScore()).isEqualTo(1.0f);
		assertThat(this.promptCaptor.getAllValues().get(1).getContents()).contains("0 to 1");
	}

	@Test
	void whenMaxValueIsNotPositiveThenThrow() {
		assertThatThrownBy(() -> GevalEvaluator.builder()
			.chatClientBuilder(ChatClient.builder(this.chatModel))
			.criteria("criterion")
			.maxValue(0)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("maxValue must be greater than zero");
	}

}
