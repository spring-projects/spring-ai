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

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link FactCheckingEvaluator}.
 *
 * @author guan xu
 * @author Yanming Zhou
 */
class FactCheckingEvaluatorTests {

	@SuppressWarnings("deprecation")
	@Test
	void whenChatClientBuilderIsNullThenThrow() {
		assertThatThrownBy(() -> new FactCheckingEvaluator(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");

		assertThatThrownBy(() -> FactCheckingEvaluator.builder(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatClientBuilder cannot be null");
	}

	@SuppressWarnings("deprecation")
	@Test
	void whenEvaluationPromptIsNullThenUseDefaultEvaluationPromptText() {
		FactCheckingEvaluator evaluator = new FactCheckingEvaluator(ChatClient.builder(mock(ChatModel.class)));
		assertThat(evaluator).isNotNull();

		evaluator = FactCheckingEvaluator.builder(ChatClient.builder(mock(ChatModel.class))).build();
		assertThat(evaluator).isNotNull();
	}

	@Test
	void whenForBespokeMinicheckThenUseBespokeEvaluationPromptText() {
		FactCheckingEvaluator evaluator = FactCheckingEvaluator
			.forBespokeMinicheck(ChatClient.builder(mock(ChatModel.class)));
		assertThat(evaluator).isNotNull();
	}

}
