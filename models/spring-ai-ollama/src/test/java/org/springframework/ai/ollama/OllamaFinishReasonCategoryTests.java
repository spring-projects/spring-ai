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

package org.springframework.ai.ollama;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import org.springframework.ai.chat.metadata.FinishReasonCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the finish reason category that {@link OllamaChatModel} gives a response.
 *
 * @author Christian Tzolov
 */
class OllamaFinishReasonCategoryTests {

	@ParameterizedTest
	@CsvSource({ "stop, COMPLETED", "length, TRUNCATED", "load, OTHER", "unload, OTHER" })
	void classifiesTheDoneReasonsOfTheApi(String doneReason, FinishReasonCategory expected) {
		assertThat(OllamaChatModel.finishReasonCategory(doneReason, false)).isEqualTo(expected);
	}

	@Test
	void aStopWithToolCallsIsAToolCall() {
		assertThat(OllamaChatModel.finishReasonCategory("stop", true)).isEqualTo(FinishReasonCategory.TOOL_CALL);
		assertThat(OllamaChatModel.finishReasonCategory("length", true)).isEqualTo(FinishReasonCategory.TRUNCATED);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void aResponseWithoutADoneReasonIsUnknown(String doneReason) {
		assertThat(OllamaChatModel.finishReasonCategory(doneReason, false)).isEqualTo(FinishReasonCategory.UNSPECIFIED);
	}

}
