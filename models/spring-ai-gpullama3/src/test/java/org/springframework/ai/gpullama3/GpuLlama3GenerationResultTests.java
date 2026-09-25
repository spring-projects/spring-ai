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

package org.springframework.ai.gpullama3;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuLlama3GenerationResultTests {

	@Test
	void storesGenerationFacts() {
		GpuLlama3GenerationResult result = new GpuLlama3GenerationResult(List.of(1, 2), List.of(3, 4), "hello",
				GpuLlama3GenerationResult.FINISH_REASON_STOP, 100L);

		assertThat(result.promptTokens()).containsExactly(1, 2);
		assertThat(result.completionTokens()).containsExactly(3, 4);
		assertThat(result.rawText()).isEqualTo("hello");
		assertThat(result.finishReason()).isEqualTo("stop");
		assertThat(result.durationNanos()).isEqualTo(100L);
	}

	@Test
	void makesTokenListsImmutableAndIndependent() {
		List<Integer> promptTokens = new ArrayList<>(List.of(1, 2));
		List<Integer> completionTokens = new ArrayList<>(List.of(3, 4));

		GpuLlama3GenerationResult result = new GpuLlama3GenerationResult(promptTokens, completionTokens, "hello",
				GpuLlama3GenerationResult.FINISH_REASON_LENGTH, 100L);
		promptTokens.add(99);
		completionTokens.add(100);

		assertThat(result.promptTokens()).containsExactly(1, 2);
		assertThat(result.completionTokens()).containsExactly(3, 4);
		List<Integer> copiedPromptTokens = result.promptTokens();
		List<Integer> copiedCompletionTokens = result.completionTokens();
		assertThatThrownBy(() -> copiedPromptTokens.add(99)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> copiedCompletionTokens.add(100)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsNullPromptTokens() {
		assertThatNullPointerException()
			.isThrownBy(() -> new GpuLlama3GenerationResult(null, List.of(1), "hello",
					GpuLlama3GenerationResult.FINISH_REASON_STOP, 100L))
			.withMessage("promptTokens must not be null");
	}

	@Test
	void rejectsNullCompletionTokens() {
		assertThatNullPointerException()
			.isThrownBy(() -> new GpuLlama3GenerationResult(List.of(1), null, "hello",
					GpuLlama3GenerationResult.FINISH_REASON_STOP, 100L))
			.withMessage("completionTokens must not be null");
	}

	@Test
	void rejectsNullRawText() {
		assertThatNullPointerException()
			.isThrownBy(() -> new GpuLlama3GenerationResult(List.of(1), List.of(2), null,
					GpuLlama3GenerationResult.FINISH_REASON_STOP, 100L))
			.withMessage("rawText must not be null");
	}

	@Test
	void rejectsNullFinishReason() {
		assertThatNullPointerException()
			.isThrownBy(() -> new GpuLlama3GenerationResult(List.of(1), List.of(2), "hello", null, 100L))
			.withMessage("finishReason must not be null");
	}

	@Test
	void rejectsNegativeDuration() {
		List<Integer> promptTokens = List.of(1);
		List<Integer> completionTokens = List.of(2);

		assertThatThrownBy(() -> new GpuLlama3GenerationResult(promptTokens, completionTokens, "hello",
				GpuLlama3GenerationResult.FINISH_REASON_STOP, -1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("durationNanos must not be negative");
	}

	@Test
	void acceptsZeroDuration() {
		GpuLlama3GenerationResult result = new GpuLlama3GenerationResult(List.of(1), List.of(2), "hello",
				GpuLlama3GenerationResult.FINISH_REASON_STOP, 0L);

		assertThat(result.durationNanos()).isZero();
	}

	@Test
	void acceptsEmptyTokenListsAndRawText() {
		GpuLlama3GenerationResult result = new GpuLlama3GenerationResult(List.of(), List.of(), "",
				GpuLlama3GenerationResult.FINISH_REASON_STOP, 100L);

		assertThat(result.promptTokens()).isEmpty();
		assertThat(result.completionTokens()).isEmpty();
		assertThat(result.rawText()).isEmpty();
	}

	@Test
	void exposesSpringAiFinishReasonLiterals() {
		assertThat(GpuLlama3GenerationResult.FINISH_REASON_STOP).isEqualTo("stop");
		assertThat(GpuLlama3GenerationResult.FINISH_REASON_LENGTH).isEqualTo("length");
	}

	@Test
	void preservesRecordValueEqualityAfterCopyingLists() {
		GpuLlama3GenerationResult first = new GpuLlama3GenerationResult(new ArrayList<>(List.of(1, 2)),
				new ArrayList<>(List.of(3, 4)), "hello", GpuLlama3GenerationResult.FINISH_REASON_STOP, 100L);
		GpuLlama3GenerationResult second = new GpuLlama3GenerationResult(List.of(1, 2), List.of(3, 4), "hello",
				GpuLlama3GenerationResult.FINISH_REASON_STOP, 100L);

		assertThat(first).isEqualTo(second);
	}

}
