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

package org.springframework.ai.bedrock.converse.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

import org.springframework.ai.chat.metadata.FinishReasonCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the finish reason category that the Bedrock Converse model gives a response.
 *
 * @author Christian Tzolov
 */
class ConverseFinishReasonCategoryTests {

	@ParameterizedTest
	@CsvSource({ "end_turn, COMPLETED", "stop_sequence, COMPLETED", "max_tokens, TRUNCATED",
			"model_context_window_exceeded, TRUNCATED", "tool_use, TOOL_CALL", "guardrail_intervened, FILTERED",
			"content_filtered, FILTERED", "malformed_model_output, OTHER", "malformed_tool_use, OTHER",
			"something_new, OTHER" })
	void classifiesTheStopReasonsOfTheApi(String stopReason, FinishReasonCategory expected) {
		assertThat(ConverseApiUtils.finishReasonCategory(StopReason.fromValue(stopReason))).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullSource
	void aResponseWithoutAStopReasonIsUnknown(StopReason stopReason) {
		assertThat(ConverseApiUtils.finishReasonCategory(stopReason)).isEqualTo(FinishReasonCategory.UNSPECIFIED);
	}

}
