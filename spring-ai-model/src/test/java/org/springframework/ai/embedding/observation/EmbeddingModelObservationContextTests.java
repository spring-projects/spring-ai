/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.embedding.observation;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EmbeddingModelObservationContext}.
 *
 * @author Thomas Vitale
 */
class EmbeddingModelObservationContextTests {

	@Test
	void whenMandatoryRequestOptionsThenReturn() {
		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(generateEmbeddingRequest(EmbeddingOptions.builder().model("supermodel").build()))
			.provider("superprovider")
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenBuilderWithNullRequestThenThrowsException() {
		assertThatThrownBy(() -> EmbeddingModelObservationContext.builder()
			.embeddingRequest(null)
			.provider("test-provider")
			.build()).isInstanceOf(IllegalStateException.class).hasMessage("request cannot be null");
	}

	@Test
	void whenBuilderWithNullProviderThenThrowsException() {
		var embeddingRequest = generateEmbeddingRequest(EmbeddingOptions.builder().model("test-model").build());

		assertThatThrownBy(() -> EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(null)
			.build()).isInstanceOf(IllegalStateException.class).hasMessage("provider cannot be null or empty");
	}

	@Test
	void whenBuilderWithEmptyProviderThenThrowsException() {
		var embeddingRequest = generateEmbeddingRequest(EmbeddingOptions.builder().model("test-model").build());

		assertThatThrownBy(() -> EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider("")
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("provider cannot be null or empty");
	}

	@Test
	void whenValidRequestAndProviderThenBuildsSuccessfully() {
		var embeddingRequest = generateEmbeddingRequest(EmbeddingOptions.builder().model("test-model").build());

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider("valid-provider")
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenBuilderWithBlankProviderThenThrowsException() {
		var embeddingRequest = generateEmbeddingRequest(EmbeddingOptions.builder().model("test-model").build());

		assertThatThrownBy(() -> EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider("   ")
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("provider cannot be null or empty");
	}

	@Test
	void whenEmbeddingRequestWithNullOptionsThenBuildsSuccessfully() {
		var embeddingRequest = generateEmbeddingRequest(null);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider("test-provider")
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenEmbeddingRequestWithEmptyInputListThenBuildsSuccessfully() {
		var embeddingRequest = new EmbeddingRequest(List.of(), EmbeddingOptions.builder().model("test-model").build());

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider("test-provider")
			.build();

		assertThat(observationContext).isNotNull();
	}

	private EmbeddingRequest generateEmbeddingRequest(EmbeddingOptions embeddingOptions) {
		return new EmbeddingRequest(List.of("test input"), embeddingOptions);
	}

}
