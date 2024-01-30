/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.ai.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata.PromptFilterMetadata;

/**
 * Unit Tests for {@link PromptMetadata}.
 *
 * @author John Blum
 * @since 0.7.0
 */
public class PromptMetadataTests {

	private PromptFilterMetadata mockPromptFilterMetadata(int index) {
		PromptFilterMetadata mockPromptFilterMetadata = mock(PromptFilterMetadata.class);
		doReturn(index).when(mockPromptFilterMetadata).getPromptIndex();
		return mockPromptFilterMetadata;
	}

	@Test
	void emptyPromptMetadata() {

		PromptMetadata empty = PromptMetadata.empty();

		assertThat(empty).isNotNull();
		assertThat(empty).isEmpty();
	}

	@Test
	void promptMetadataWithOneFilter() {

		PromptFilterMetadata mockPromptFilterMetadata = mockPromptFilterMetadata(0);
		PromptMetadata promptMetadata = PromptMetadata.of(mockPromptFilterMetadata);

		assertThat(promptMetadata).isNotNull();
		assertThat(promptMetadata).containsExactly(mockPromptFilterMetadata);
	}

	@Test
	void promptMetadataWithTwoFilters() {

		PromptFilterMetadata mockPromptFilterMetadataOne = mockPromptFilterMetadata(0);
		PromptFilterMetadata mockPromptFilterMetadataTwo = mockPromptFilterMetadata(1);
		PromptMetadata promptMetadata = PromptMetadata.of(mockPromptFilterMetadataOne, mockPromptFilterMetadataTwo);

		assertThat(promptMetadata).isNotNull();
		assertThat(promptMetadata).containsExactly(mockPromptFilterMetadataOne, mockPromptFilterMetadataTwo);
	}

	@Test
	void findByPromptIndex() {

		PromptFilterMetadata mockPromptFilterMetadataOne = mockPromptFilterMetadata(0);
		PromptFilterMetadata mockPromptFilterMetadataTwo = mockPromptFilterMetadata(1);
		PromptMetadata promptMetadata = PromptMetadata.of(mockPromptFilterMetadataOne, mockPromptFilterMetadataTwo);

		assertThat(promptMetadata).isNotNull();
		assertThat(promptMetadata).containsExactly(mockPromptFilterMetadataOne, mockPromptFilterMetadataTwo);
		assertThat(promptMetadata.findByPromptIndex(1).orElse(null)).isEqualTo(mockPromptFilterMetadataTwo);
		assertThat(promptMetadata.findByPromptIndex(0).orElse(null)).isEqualTo(mockPromptFilterMetadataOne);
	}

	@Test
	void findByPromptIndexWithNoFilters() {
		assertThat(PromptMetadata.empty().findByPromptIndex(0)).isNotPresent();
	}

	@Test
	void findByInvalidPromptIndex() {

		assertThatIllegalArgumentException().isThrownBy(() -> PromptMetadata.empty().findByPromptIndex(-1))
			.withMessage("Prompt index [-1] must be greater than equal to 0")
			.withNoCause();
	}

	@Test
	void fromPromptIndexAndContentFilterMetadata() {

		PromptFilterMetadata promptFilterMetadata = PromptFilterMetadata.from(1, "{ content-sentiment: 'SAFE' }");

		assertThat(promptFilterMetadata).isNotNull();
		assertThat(promptFilterMetadata.getPromptIndex()).isOne();
		assertThat(promptFilterMetadata.<String>getContentFilterMetadata()).isEqualTo("{ content-sentiment: 'SAFE' }");
	}

}
