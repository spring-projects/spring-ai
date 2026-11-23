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

package org.springframework.ai.openai.responses;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.chat.metadata.FinishReasonCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the finish reason category that {@link ResponsesItemMapper} gives a response.
 *
 * @author Christian Tzolov
 */
class ResponsesFinishReasonCategoryTests {

	@ParameterizedTest
	@CsvSource({ "STOP, COMPLETED", "LENGTH, TRUNCATED", "TOOL_CALLS, TOOL_CALL", "CONTENT_FILTER, FILTERED",
			"INCOMPLETE, OTHER", "CANCELLED, OTHER" })
	void classifiesTheDerivedFinishReasons(String finishReason, FinishReasonCategory expected) {
		assertThat(ResponsesItemMapper.finishReasonCategory(finishReason, "")).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "STOP", "TOOL_CALLS" })
	void aRefusalIsFilteredWhateverTheFinishReason(String finishReason) {
		assertThat(ResponsesItemMapper.finishReasonCategory(finishReason, "I can't help with that."))
			.isEqualTo(FinishReasonCategory.FILTERED);
	}

}
