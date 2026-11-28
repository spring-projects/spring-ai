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

package org.springframework.ai.chat.observation;

import java.util.List;
import java.util.Objects;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatObservationContentObservationHandler}.
 */
class ChatObservationContentObservationHandlerTests {

	private static final String PROMPT_KEY = "gen_ai.prompt";

	private static final String COMPLETION_KEY = "gen_ai.completion";

	private static final int MAX_CONTENT_LENGTH = 2000;

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		var context = new Observation.Context();

		assertThat(handler(true, true, MAX_CONTENT_LENGTH).supportsContext(context)).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		var context = context("What is Spring AI?");

		assertThat(handler(true, true, MAX_CONTENT_LENGTH).supportsContext(context)).isTrue();
	}

	@Test
	void whenPromptIncludedThenAddPromptAsHighCardinalityKeyValue() {
		var context = context("What is Spring AI?");
		context.setResponse(response("Spring AI is an application framework for AI engineering."));

		handler(true, false, MAX_CONTENT_LENGTH).onStop(context);

		assertHighCardinalityValue(context, PROMPT_KEY, "What is Spring AI?");
		assertThat(context.getHighCardinalityKeyValue(COMPLETION_KEY)).isNull();
	}

	@Test
	void whenCompletionIncludedThenAddCompletionAsHighCardinalityKeyValue() {
		var context = context("What is Spring AI?");
		context.setResponse(response("Spring AI is an application framework for AI engineering."));

		handler(false, true, MAX_CONTENT_LENGTH).onStop(context);

		assertThat(context.getHighCardinalityKeyValue(PROMPT_KEY)).isNull();
		assertHighCardinalityValue(context, COMPLETION_KEY,
				"Spring AI is an application framework for AI engineering.");
	}

	@Test
	void whenPromptAndCompletionIncludedThenAddBothAsHighCardinalityKeyValues() {
		var context = context("What is Spring AI?");
		context.setResponse(response("Spring AI is an application framework for AI engineering."));

		handler(true, true, MAX_CONTENT_LENGTH).onStop(context);

		assertHighCardinalityValue(context, PROMPT_KEY, "What is Spring AI?");
		assertHighCardinalityValue(context, COMPLETION_KEY,
				"Spring AI is an application framework for AI engineering.");
	}

	@Test
	void whenPromptAndCompletionAreDisabledThenDoNotAddKeyValues() {
		var context = context("What is Spring AI?");
		context.setResponse(response("Spring AI is an application framework for AI engineering."));

		handler(false, false, MAX_CONTENT_LENGTH).onStop(context);

		assertThat(context.getHighCardinalityKeyValue(PROMPT_KEY)).isNull();
		assertThat(context.getHighCardinalityKeyValue(COMPLETION_KEY)).isNull();
	}

	@Test
	void whenPromptIsEmptyThenDoNotAddPromptKeyValue() {
		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt(List.of(), ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();

		handler(true, false, MAX_CONTENT_LENGTH).onStop(context);

		assertThat(context.getHighCardinalityKeyValue(PROMPT_KEY)).isNull();
	}

	@Test
	void whenResponseIsNullThenDoNotAddCompletionKeyValue() {
		var context = context("What is Spring AI?");

		handler(false, true, MAX_CONTENT_LENGTH).onStop(context);

		assertThat(context.getHighCardinalityKeyValue(COMPLETION_KEY)).isNull();
	}

	@Test
	void whenResponseHasNoGenerationsThenDoNotAddCompletionKeyValue() {
		var context = context("What is Spring AI?");
		context.setResponse(new ChatResponse(List.of()));

		handler(false, true, MAX_CONTENT_LENGTH).onStop(context);

		assertThat(context.getHighCardinalityKeyValue(COMPLETION_KEY)).isNull();
	}

	@Test
	void whenCompletionIsEmptyThenDoNotAddCompletionKeyValue() {
		var context = context("What is Spring AI?");
		context.setResponse(response(""));

		handler(false, true, MAX_CONTENT_LENGTH).onStop(context);

		assertThat(context.getHighCardinalityKeyValue(COMPLETION_KEY)).isNull();
	}

	@Test
	void whenCustomKeysAreConfiguredThenUseThem() {
		var context = context("What is Spring AI?");
		context.setResponse(response("Spring AI is an application framework."));

		var handler = new ChatObservationContentObservationHandler(true, true, MAX_CONTENT_LENGTH, "custom.prompt",
				"custom.completion");

		handler.onStop(context);

		assertHighCardinalityValue(context, "custom.prompt", "What is Spring AI?");
		assertHighCardinalityValue(context, "custom.completion", "Spring AI is an application framework.");
		assertThat(context.getHighCardinalityKeyValue(PROMPT_KEY)).isNull();
		assertThat(context.getHighCardinalityKeyValue(COMPLETION_KEY)).isNull();
	}

	@Test
	void whenContentExceedsMaxLengthThenTruncatePromptAndCompletion() {
		var content = "abcdefghijklmnopqrstuvwxyz";
		var context = context(content);
		context.setResponse(response(content));

		handler(true, true, 20).onStop(context);

		assertHighCardinalityValue(context, PROMPT_KEY, "abcde... [truncated]");
		assertHighCardinalityValue(context, COMPLETION_KEY, "abcde... [truncated]");
		assertThat(Objects.requireNonNull(context.getHighCardinalityKeyValue(PROMPT_KEY)).getValue()).hasSize(20);
		assertThat(Objects.requireNonNull(context.getHighCardinalityKeyValue(COMPLETION_KEY)).getValue()).hasSize(20);
	}

	private ChatObservationContentObservationHandler handler(boolean includePrompt, boolean includeCompletion,
			int maxContentLength) {
		return new ChatObservationContentObservationHandler(includePrompt, includeCompletion, maxContentLength,
				PROMPT_KEY, COMPLETION_KEY);
	}

	private ChatModelObservationContext context(String promptText) {
		return ChatModelObservationContext.builder()
			.prompt(new Prompt(promptText, ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
	}

	private ChatResponse response(String completionText) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(completionText))));
	}

	private void assertHighCardinalityValue(ChatModelObservationContext context, String key, String expectedValue) {
		var keyValue = context.getHighCardinalityKeyValue(key);
		assertThat(keyValue).isNotNull();
		assertThat(keyValue.getValue()).isEqualTo(expectedValue);
	}

}
