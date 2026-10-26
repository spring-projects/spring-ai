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
 * Unit tests for {@link RubricEvaluator}.
 */
@ExtendWith(MockitoExtension.class)
class RubricEvaluatorTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> new RubricEvaluator(null, "criteria")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");

		assertThatThrownBy(() -> RubricEvaluator.builder().chatClientBuilder(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@Test
	void whenEvaluationResponseIsValidJsonThenReturnEvaluation() {
		String modelResponse = "{\"reason\":\"meets rubric\",\"pass\":true,\"score\":0.8}";
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(modelResponse)))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		RubricEvaluator evaluator = new RubricEvaluator(ChatClient.builder(this.chatModel), "contains greeting");
		EvaluationResponse result = evaluator.evaluate(new EvaluationRequest("contains greeting", List.of(), "Hello"));

		assertThat(result.isPass()).isTrue();
		assertThat(result.getScore()).isEqualTo(0.8f);
		assertThat(result.getFeedback()).isEqualTo("meets rubric");
	}

	@Test
	void whenEvaluationResponseIsInvalidJsonThenThrow() {
		given(this.chatModel.call(this.promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("not json")))));
		given(this.chatModel.getOptions()).willReturn(ChatOptions.builder().build());

		RubricEvaluator evaluator = new RubricEvaluator(ChatClient.builder(this.chatModel), "rubric");
		assertThatThrownBy(() -> evaluator.evaluate(new EvaluationRequest("rubric", List.of(), "output")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Evaluation response must be valid JSON");
	}

}
