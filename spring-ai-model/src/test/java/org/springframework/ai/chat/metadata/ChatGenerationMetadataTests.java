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

package org.springframework.ai.chat.metadata;

import java.util.Map.Entry;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ChatGenerationMetadata}.
 *
 * @author Quark
 */
public class ChatGenerationMetadataTests {

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
	void shouldPreserveBlankFinishReasonAsAbsent() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(" \t ").build();

		assertThat(metadata.getFinishReason()).isEqualTo(" \t ");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.UNKNOWN);
	}

	@Test
	void shouldReturnOtherForUnrecognizedReason() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("custom_reason").build();

		assertThat(metadata.getFinishReason()).isEqualTo("custom_reason");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.OTHER);
	}

	@Test
	void shouldKeepAnthropicContextWindowReasonUnchanged() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
			.finishReason("model_context_window_exceeded")
			.build();

		assertThat(metadata.getFinishReason()).isEqualTo("model_context_window_exceeded");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.TRUNCATED);
	}

	@Test
	void shouldPreserveOriginalFinishReasonString() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("END_TURN").build();

		assertThat(metadata.getFinishReason()).isEqualTo("END_TURN");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.COMPLETED);
	}

	@Test
	void thirdPartyImplementationKeepsRawReasonAndUsesDefaultCategory() {
		ChatGenerationMetadata absent = new FixedFinishReasonMetadata(null);
		assertThat(absent.getFinishReason()).isNull();
		assertThat(absent.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.UNKNOWN);

		ChatGenerationMetadata unrecognized = new FixedFinishReasonMetadata("provider_specific");
		assertThat(unrecognized.getFinishReason()).isEqualTo("provider_specific");
		assertThat(unrecognized.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.OTHER);
	}

	/**
	 * Third-party metadata that implements the interface and does not override
	 * {@link ChatGenerationMetadata#getFinishReasonCategory()}.
	 */
	private static final class FixedFinishReasonMetadata implements ChatGenerationMetadata {

		private final @Nullable String finishReason;

		private FixedFinishReasonMetadata(@Nullable String finishReason) {
			this.finishReason = finishReason;
		}

		@Override
		public @Nullable String getFinishReason() {
			return this.finishReason;
		}

		@Override
		public Set<String> getContentFilters() {
			return Set.of();
		}

		@Override
		public <T> @Nullable T get(String key) {
			return null;
		}

		@Override
		public boolean containsKey(String key) {
			return false;
		}

		@Override
		public <T> T getOrDefault(String key, T defaultObject) {
			return defaultObject;
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			return Set.of();
		}

		@Override
		public Set<String> keySet() {
			return Set.of();
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

	}

}
