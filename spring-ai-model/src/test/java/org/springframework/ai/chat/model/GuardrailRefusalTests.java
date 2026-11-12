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

package org.springframework.ai.chat.model;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.FinishReasonCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link GuardrailRefusal}.
 *
 * @author Christian Tzolov
 */
class GuardrailRefusalTests {

	@Test
	void buildsAGenerationThatCarriesTheTextAndTheFinishReason() {
		Generation generation = GuardrailRefusal.generation("I can't help with that.");

		assertThat(generation.getOutput().getText()).isEqualTo("I can't help with that.");
		assertThat(generation.getMetadata().getFinishReason()).isEqualTo(GuardrailRefusal.FINISH_REASON);
	}

	@Test
	void aRefusalIsAFilteredGeneration() {
		ChatResponse refusal = new ChatResponse(List.of(GuardrailRefusal.generation("I can't help with that.")));

		assertThat(refusal.hasFinishReasonCategory(FinishReasonCategory.FILTERED)).isTrue();
	}

	@Test
	void aRefusalBuiltWithTheFinishReasonAloneIsFilteredToo() {
		// As a guardrail that does not use the factory method builds it.
		Generation generation = new Generation(new AssistantMessage("I can't help with that."),
				ChatGenerationMetadata.builder().finishReason(GuardrailRefusal.FINISH_REASON).build());

		assertThat(generation.getMetadata().getFinishReasonCategory()).isEqualTo(FinishReasonCategory.FILTERED);
	}

	@Test
	void recognisesAResponseThatHoldsARefusal() {
		ChatResponse refusal = new ChatResponse(List.of(GuardrailRefusal.generation("I can't help with that.")));

		assertThat(GuardrailRefusal.isRefusal(refusal)).isTrue();
	}

	@Test
	void doesNotTakeAnAnswerOfTheModelForARefusal() {
		// The same words, said by the model, are an answer like any other.
		ChatResponse answer = new ChatResponse(List.of(new Generation(new AssistantMessage("I can't help with that."),
				ChatGenerationMetadata.builder().finishReason("STOP").build())));

		assertThat(GuardrailRefusal.isRefusal(answer)).isFalse();
		assertThat(GuardrailRefusal.isRefusal(new ChatResponse(List.of(new Generation(new AssistantMessage("Hi"))))))
			.isFalse();
		assertThat(GuardrailRefusal.isRefusal(new ChatResponse(List.of()))).isFalse();
		assertThat(GuardrailRefusal.isRefusal((ChatResponse) null)).isFalse();
	}

	@Test
	void oneRefusedGenerationMakesTheResponseARefusal() {
		Generation answer = new Generation(new AssistantMessage("{}"));
		Generation refusal = GuardrailRefusal.generation("I can't help with that.");

		// The same rule as ChatResponse.hasToolCalls() and hasFinishReasons().
		assertThat(GuardrailRefusal.isRefusal(new ChatResponse(List.of(answer, refusal)))).isTrue();
		assertThat(GuardrailRefusal.isRefusal(new ChatResponse(List.of(refusal, answer)))).isTrue();
		assertThat(GuardrailRefusal.text(new ChatResponse(List.of(answer, refusal))))
			.isEqualTo("I can't help with that.");
		assertThat(GuardrailRefusal.isRefusal(refusal)).isTrue();
		assertThat(GuardrailRefusal.isRefusal(answer)).isFalse();
		assertThat(GuardrailRefusal.isRefusal((Generation) null)).isFalse();
	}

	@Test
	void comparesTheFinishReasonInAnyCaseAsChatResponseDoes() {
		ChatResponse refusal = new ChatResponse(List.of(new Generation(new AssistantMessage("No."),
				ChatGenerationMetadata.builder().finishReason("GUARDRAILREFUSAL").build())));

		assertThat(GuardrailRefusal.isRefusal(refusal)).isTrue();
		assertThat(refusal.hasFinishReasons(Set.of(GuardrailRefusal.FINISH_REASON))).isTrue();
	}

	@Test
	void readsTheTextOfARefusalOnly() {
		ChatResponse refusal = new ChatResponse(List.of(GuardrailRefusal.generation("I can't help with that.")));
		ChatResponse answer = new ChatResponse(List.of(new Generation(new AssistantMessage("Hi"))));

		assertThat(GuardrailRefusal.text(refusal)).isEqualTo("I can't help with that.");
		assertThat(GuardrailRefusal.text(answer)).isNull();
		assertThat(GuardrailRefusal.text(new ChatResponse(List.of()))).isNull();
		assertThat(GuardrailRefusal.text(null)).isNull();
	}

	@Test
	void requiresATextForTheRefusal() {
		assertThatIllegalArgumentException().isThrownBy(() -> GuardrailRefusal.generation(null));
	}

}
