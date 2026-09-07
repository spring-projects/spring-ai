/*
 * Copyright 2023-2025 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.chat.evaluation.ResponseRelevancyEvaluator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResponseRelevancyEvaluator}.
 *
 * @author Alessandro Russo
 */
class ResponseRelevancyEvaluatorTests {

	private ChatClient.Builder mockChatClientBuilder;

	private ChatClient mockChatClient;

	private ChatClient.ChatClientRequestSpec mockRequestSpec;

	private ChatClient.CallResponseSpec mockResponseSpec;

	@BeforeEach
	void setUp() {
		mockChatClientBuilder = Mockito.mock(ChatClient.Builder.class);
		mockChatClient = Mockito.mock(ChatClient.class);
		mockRequestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
		mockResponseSpec = Mockito.mock(ChatClient.CallResponseSpec.class);

		when(mockChatClientBuilder.build()).thenReturn(mockChatClient);
		when(mockChatClient.prompt()).thenReturn(mockRequestSpec);
		when(mockRequestSpec.user(any(String.class))).thenReturn(mockRequestSpec);
		when(mockRequestSpec.call()).thenReturn(mockResponseSpec);
	}

	@Test
	void whenEvaluatorReturnPositiveAnswer() {
		var evaluator = new ResponseRelevancyEvaluator(mockChatClientBuilder);
		var request = new EvaluationRequest("What is the capital of France?", "Paris");
		when(mockResponseSpec.content()).thenReturn("1.0"); // Score is above the default
															// 0.8 threshold
		EvaluationResponse response = evaluator.evaluate(request);
		assertThat(response.isPass()).isTrue();
		assertThat(response.getScore()).isEqualTo(1.0f);
	}

	@Test
	void whenEvaluatorReturnNegativeAnswer() {
		var evaluator = new ResponseRelevancyEvaluator(mockChatClientBuilder);
		var request = new EvaluationRequest("What is the capital of France?", "Par");
		when(mockResponseSpec.content()).thenReturn("0.5"); // Score is below the default
															// 0.8 threshold
		EvaluationResponse response = evaluator.evaluate(request);
		assertThat(response.isPass()).isFalse();
		assertThat(response.getScore()).isEqualTo(0.5f);
	}

	@Test
	void whenEvaluatorReturnNoCompliantAnswer() {
		var evaluator = new ResponseRelevancyEvaluator(mockChatClientBuilder);
		var request = new EvaluationRequest("What is the capital of France?", "Paris");
		when(mockResponseSpec.content()).thenReturn("This is not a valid float.");
		assertThatThrownBy(() -> evaluator.evaluate(request)).isInstanceOf(NumberFormatException.class);
	}

}