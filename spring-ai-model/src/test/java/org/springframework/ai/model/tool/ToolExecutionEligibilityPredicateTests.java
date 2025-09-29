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

package org.springframework.ai.model.tool;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolExecutionEligibilityPredicate}.
 *
 * @author Christian Tzolov
 */
class ToolExecutionEligibilityPredicateTests {

	@Test
	void whenIsToolExecutionRequiredWithNullPromptOptions() {
		ToolExecutionEligibilityPredicate predicate = new TestToolExecutionEligibilityPredicate();
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("test"))));

		assertThatThrownBy(() -> predicate.isToolExecutionRequired(null, chatResponse))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("promptOptions cannot be null");
	}

	@Test
	void whenIsToolExecutionRequiredWithNullChatResponse() {
		ToolExecutionEligibilityPredicate predicate = new TestToolExecutionEligibilityPredicate();
		ChatOptions promptOptions = ChatOptions.builder().build();

		assertThatThrownBy(() -> predicate.isToolExecutionRequired(promptOptions, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("chatResponse cannot be null");
	}

	@Test
	void whenIsToolExecutionRequiredWithValidInputs() {
		ToolExecutionEligibilityPredicate predicate = new TestToolExecutionEligibilityPredicate();
		ChatOptions promptOptions = ChatOptions.builder().build();
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("test"))));

		boolean result = predicate.isToolExecutionRequired(promptOptions, chatResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenTestMethodCalledDirectly() {
		ToolExecutionEligibilityPredicate predicate = new TestToolExecutionEligibilityPredicate();
		ChatOptions promptOptions = ChatOptions.builder().build();
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("test"))));

		boolean result = predicate.test(promptOptions, chatResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenChatResponseHasEmptyGenerations() {
		ToolExecutionEligibilityPredicate predicate = new TestToolExecutionEligibilityPredicate();
		ChatOptions promptOptions = ChatOptions.builder().build();
		ChatResponse emptyResponse = new ChatResponse(Collections.emptyList());

		boolean result = predicate.isToolExecutionRequired(promptOptions, emptyResponse);
		assertThat(result).isTrue();
	}

	@Test
	void whenChatOptionsHasModel() {
		ModelCheckingPredicate predicate = new ModelCheckingPredicate();

		ChatOptions optionsWithModel = ChatOptions.builder().model("gpt-4").build();

		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("test"))));

		boolean result = predicate.isToolExecutionRequired(optionsWithModel, chatResponse);
		assertThat(result).isTrue();

		ChatOptions optionsWithoutModel = ChatOptions.builder().build();
		result = predicate.isToolExecutionRequired(optionsWithoutModel, chatResponse);
		assertThat(result).isFalse();
	}

	/**
	 * Test implementation of {@link ToolExecutionEligibilityPredicate} that always
	 * returns true.
	 */
	private static class TestToolExecutionEligibilityPredicate implements ToolExecutionEligibilityPredicate {

		@Override
		public boolean test(ChatOptions promptOptions, ChatResponse chatResponse) {
			return true;
		}

	}

	private static class ModelCheckingPredicate implements ToolExecutionEligibilityPredicate {

		@Override
		public boolean test(ChatOptions promptOptions, ChatResponse chatResponse) {
			return promptOptions.getModel() != null && !promptOptions.getModel().isEmpty();
		}

	}

}
