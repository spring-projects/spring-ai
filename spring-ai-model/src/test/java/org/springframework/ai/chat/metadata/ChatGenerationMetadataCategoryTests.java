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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the finish reason category that a chat model sets on
 * {@link ChatGenerationMetadata}.
 *
 * @author Christian Tzolov
 */
class ChatGenerationMetadataCategoryTests {

	@Test
	void theCategoryOfTheChatModelWinsOverTheRawFinishReason() {
		// The provider reported an ordinary stop and the refusal in another field. Only
		// the chat model can tell.
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
			.finishReason("STOP")
			.finishReasonCategory(FinishReasonCategory.FILTERED)
			.build();

		assertThat(metadata.getFinishReason()).isEqualTo("STOP");
		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.FILTERED);
	}

	@Test
	void withoutACategoryTheRawFinishReasonIsClassified() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason("content_filter").build();

		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.FILTERED);
	}

	@Test
	void aNullCategoryFallsBackToTheRawFinishReason() {
		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
			.finishReason("max_tokens")
			.finishReasonCategory(null)
			.build();

		assertThat(metadata.getFinishReasonCategory()).isEqualTo(FinishReasonCategory.TRUNCATED);
	}

	@Test
	void theCategoryIsPartOfEquality() {
		ChatGenerationMetadata derived = ChatGenerationMetadata.builder().finishReason("STOP").build();
		ChatGenerationMetadata classified = ChatGenerationMetadata.builder()
			.finishReason("STOP")
			.finishReasonCategory(FinishReasonCategory.FILTERED)
			.build();
		ChatGenerationMetadata same = ChatGenerationMetadata.builder()
			.finishReason("STOP")
			.finishReasonCategory(FinishReasonCategory.FILTERED)
			.build();

		assertThat(classified).isNotEqualTo(derived);
		assertThat(classified).isEqualTo(same).hasSameHashCodeAs(same);
	}

	@Test
	void aCategoryThatAgreesWithTheRawFinishReasonDoesNotChangeEquality() {
		// What a chat model returns still equals the metadata a test builds by hand.
		ChatGenerationMetadata byHand = ChatGenerationMetadata.builder().finishReason("STOP").build();
		ChatGenerationMetadata byChatModel = ChatGenerationMetadata.builder()
			.finishReason("STOP")
			.finishReasonCategory(FinishReasonCategory.COMPLETED)
			.build();

		assertThat(byChatModel).isEqualTo(byHand).hasSameHashCodeAs(byHand);
	}

}
