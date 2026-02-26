/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.chat.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FinishReasonCategory}.
 *
 * @author Spring AI Team
 */
class FinishReasonCategoryTests {

	@ParameterizedTest
	@CsvSource({
			// OpenAI
			"STOP, COMPLETED", "stop, COMPLETED",
			// Anthropic
			"end_turn, COMPLETED", "END_TURN, COMPLETED",
			// Stop sequence
			"stop_sequence, COMPLETED", "STOP_SEQUENCE, COMPLETED" })
	void shouldCategorizeCompletedReasons(String rawReason, FinishReasonCategory expected) {
		assertThat(FinishReasonCategory.categorize(rawReason)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
			// OpenAI
			"LENGTH, TRUNCATED", "length, TRUNCATED",
			// Anthropic/Gemini/Bedrock
			"max_tokens, TRUNCATED", "MAX_TOKENS, TRUNCATED",
			// Context window
			"context_window_exceeded, TRUNCATED" })
	void shouldCategorizeTruncatedReasons(String rawReason, FinishReasonCategory expected) {
		assertThat(FinishReasonCategory.categorize(rawReason)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
			// OpenAI
			"tool_calls, TOOL_CALL", "TOOL_CALLS, TOOL_CALL",
			// Mistral compatibility
			"tool_call, TOOL_CALL", "TOOL_CALL, TOOL_CALL",
			// Anthropic/Bedrock
			"tool_use, TOOL_CALL", "TOOL_USE, TOOL_CALL" })
	void shouldCategorizeToolCallReasons(String rawReason, FinishReasonCategory expected) {
		assertThat(FinishReasonCategory.categorize(rawReason)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
			// OpenAI/Azure
			"content_filter, FILTERED", "CONTENT_FILTER, FILTERED",
			// Gemini
			"safety, FILTERED", "SAFETY, FILTERED", "recitation, FILTERED", "RECITATION, FILTERED",
			// Other safety-related
			"prohibited_content, FILTERED", "spii, FILTERED", "blocklist, FILTERED", "refusal, FILTERED" })
	void shouldCategorizeFilteredReasons(String rawReason, FinishReasonCategory expected) {
		assertThat(FinishReasonCategory.categorize(rawReason)).isEqualTo(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "\t", "\n" })
	void shouldCategorizeNullOrBlankAsUnknown(String rawReason) {
		assertThat(FinishReasonCategory.categorize(rawReason)).isEqualTo(FinishReasonCategory.UNKNOWN);
	}

	@ParameterizedTest
	@ValueSource(strings = { "unknown_reason", "custom_stop", "model_specific", "foo_bar" })
	void shouldCategorizeUnrecognizedAsOther(String rawReason) {
		assertThat(FinishReasonCategory.categorize(rawReason)).isEqualTo(FinishReasonCategory.OTHER);
	}

	@Test
	void shouldHandleMixedCaseAndWhitespace() {
		assertThat(FinishReasonCategory.categorize("  STOP  ")).isEqualTo(FinishReasonCategory.COMPLETED);
		assertThat(FinishReasonCategory.categorize("  End_Turn  ")).isEqualTo(FinishReasonCategory.COMPLETED);
		assertThat(FinishReasonCategory.categorize("MAX_TOKENS")).isEqualTo(FinishReasonCategory.TRUNCATED);
	}

	@Test
	void shouldReturnNonNullForAllEnumValues() {
		for (FinishReasonCategory category : FinishReasonCategory.values()) {
			assertThat(category).isNotNull();
		}
	}

}
