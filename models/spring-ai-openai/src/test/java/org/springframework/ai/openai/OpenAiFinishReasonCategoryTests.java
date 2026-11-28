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

package org.springframework.ai.openai;

import java.util.List;
import java.util.Optional;

import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.FinishReasonCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the finish reason category that {@link OpenAiChatModel} gives a choice.
 *
 * @author Christian Tzolov
 */
class OpenAiFinishReasonCategoryTests {

	@Test
	void classifiesTheFinishReasonsOfTheApi() {
		assertThat(category(ChatCompletion.Choice.FinishReason.STOP, null)).isEqualTo(FinishReasonCategory.COMPLETED);
		assertThat(category(ChatCompletion.Choice.FinishReason.LENGTH, null)).isEqualTo(FinishReasonCategory.TRUNCATED);
		assertThat(category(ChatCompletion.Choice.FinishReason.TOOL_CALLS, null))
			.isEqualTo(FinishReasonCategory.TOOL_CALL);
		assertThat(category(ChatCompletion.Choice.FinishReason.FUNCTION_CALL, null))
			.isEqualTo(FinishReasonCategory.TOOL_CALL);
		assertThat(category(ChatCompletion.Choice.FinishReason.CONTENT_FILTER, null))
			.isEqualTo(FinishReasonCategory.FILTERED);
	}

	@Test
	void aRefusalIsFilteredAlthoughTheFinishReasonIsAnOrdinaryStop() {
		assertThat(category(ChatCompletion.Choice.FinishReason.STOP, "I'm sorry, I can't help with that."))
			.isEqualTo(FinishReasonCategory.FILTERED);
	}

	@Test
	void anEmptyRefusalIsNotARefusal() {
		assertThat(category(ChatCompletion.Choice.FinishReason.STOP, "")).isEqualTo(FinishReasonCategory.COMPLETED);
	}

	@Test
	void aFinishReasonTheApiDoesNotDefineIsKeptAndClassifiedByItsCommonName() {
		// As an OpenAI-compatible provider may send.
		ChatCompletion.Choice other = choice(ChatCompletion.Choice.FinishReason.of("eos"), null);
		assertThat(OpenAiChatModel.finishReason(other)).isEqualTo("eos");
		assertThat(OpenAiChatModel.finishReasonCategory(other)).isEqualTo(FinishReasonCategory.OTHER);

		ChatCompletion.Choice truncated = choice(ChatCompletion.Choice.FinishReason.of("max_tokens"), null);
		assertThat(OpenAiChatModel.finishReason(truncated)).isEqualTo("max_tokens");
		assertThat(OpenAiChatModel.finishReasonCategory(truncated)).isEqualTo(FinishReasonCategory.TRUNCATED);
	}

	@Test
	void aChoiceWithoutAFinishReasonIsUnknown() {
		// As a streamed chunk before the last one.
		ChatCompletion.Choice choice = choice(ChatCompletion.Choice.FinishReason.of(""), null);
		assertThat(OpenAiChatModel.finishReason(choice)).isNull();
		assertThat(OpenAiChatModel.finishReasonCategory(choice)).isEqualTo(FinishReasonCategory.UNSPECIFIED);
	}

	@Test
	void aFinishReasonTheApiDefinesIsReportedByItsName() {
		assertThat(OpenAiChatModel.finishReason(choice(ChatCompletion.Choice.FinishReason.STOP, null)))
			.isEqualTo("STOP");
	}

	private static FinishReasonCategory category(ChatCompletion.Choice.FinishReason finishReason,
			@Nullable String refusal) {
		return OpenAiChatModel.finishReasonCategory(choice(finishReason, refusal));
	}

	private static ChatCompletion.Choice choice(ChatCompletion.Choice.FinishReason finishReason,
			@Nullable String refusal) {
		return ChatCompletion.Choice.builder()
			.finishReason(finishReason)
			.index(0)
			.logprobs(Optional.empty())
			.message(ChatCompletionMessage.builder()
				.content("")
				.refusal(Optional.ofNullable(refusal))
				.role(JsonValue.from("assistant"))
				.annotations(List.of())
				.toolCalls(List.of())
				.build())
			.build();
	}

}
