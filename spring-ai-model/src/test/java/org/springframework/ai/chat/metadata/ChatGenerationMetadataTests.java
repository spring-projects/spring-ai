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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ChatGenerationMetadata}.
 *
 * @author Spring AI Team
 */
class ChatGenerationMetadataTests {

	@Test
	void shouldBuildMetadataWithFinishReason() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("stop").build();

		assertThat(metadata.getFinishReason()).isEqualTo("stop");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.COMPLETED);
	}

	@Test
	void shouldReturnCorrectCategoryForDifferentReasons() {
		ChatGenerationMetadata stopMetadata = ChatGenerationMetadata.builder().finishReason("STOP").build();
		assertThat(stopMetadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.COMPLETED);

		ChatGenerationMetadata lengthMetadata = ChatGenerationMetadata.builder().finishReason("length").build();
		assertThat(lengthMetadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.TRUNCATED);

		ChatGenerationMetadata toolMetadata = ChatGenerationMetadata.builder().finishReason("tool_calls").build();
		assertThat(toolMetadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.TOOL_CALL);

		ChatGenerationMetadata filterMetadata = ChatGenerationMetadata.builder().finishReason("content_filter").build();
		assertThat(filterMetadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.FILTERED);
	}

	@Test
	void shouldReturnUnknownForNullFinishReason() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().build();

		assertThat(metadata.getFinishReason()).isNull();
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.UNKNOWN);
	}

	@Test
	void nullMetadataShouldReturnUnknownCategory() {
		assertThat(ChatGenerationMetadata.NULL.getFinishReason()).isNull();
		assertThat(ChatGenerationMetadata.NULL.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.UNKNOWN);
	}

	@Test
	void shouldReturnOtherForUnrecognizedReason() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("custom_reason").build();

		assertThat(metadata.getFinishReason()).isEqualTo("custom_reason");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.OTHER);
	}

	@Test
	void shouldPreserveOriginalFinishReasonString() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("END_TURN").build();

		// Raw reason preserved with original case
		assertThat(metadata.getFinishReason()).isEqualTo("END_TURN");
		// Category normalized correctly
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.COMPLETED);
	}

}
