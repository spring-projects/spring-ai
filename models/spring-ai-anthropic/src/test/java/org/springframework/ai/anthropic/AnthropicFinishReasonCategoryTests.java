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

package org.springframework.ai.anthropic;

import java.util.Optional;

import com.anthropic.models.messages.StopReason;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.FinishReasonCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the finish reason category that {@link AnthropicChatModel} gives a message.
 *
 * @author Christian Tzolov
 */
class AnthropicFinishReasonCategoryTests {

	@Test
	void classifiesTheStopReasonsOfTheApi() {
		assertThat(category(StopReason.END_TURN)).isEqualTo(FinishReasonCategory.COMPLETED);
		assertThat(category(StopReason.STOP_SEQUENCE)).isEqualTo(FinishReasonCategory.COMPLETED);
		assertThat(category(StopReason.MAX_TOKENS)).isEqualTo(FinishReasonCategory.TRUNCATED);
		assertThat(category(StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED)).isEqualTo(FinishReasonCategory.TRUNCATED);
		assertThat(category(StopReason.TOOL_USE)).isEqualTo(FinishReasonCategory.TOOL_CALL);
		assertThat(category(StopReason.REFUSAL)).isEqualTo(FinishReasonCategory.FILTERED);
		assertThat(category(StopReason.PAUSE_TURN)).isEqualTo(FinishReasonCategory.OTHER);
	}

	@Test
	void aStopReasonTheSdkDoesNotKnowIsOther() {
		assertThat(category(StopReason.of("something_new"))).isEqualTo(FinishReasonCategory.OTHER);
	}

	@Test
	void aMessageWithoutAStopReasonIsUnknown() {
		assertThat(AnthropicChatModel.finishReasonCategory(Optional.empty()))
			.isEqualTo(FinishReasonCategory.UNSPECIFIED);
	}

	private static FinishReasonCategory category(StopReason stopReason) {
		return AnthropicChatModel.finishReasonCategory(Optional.of(stopReason));
	}

}
