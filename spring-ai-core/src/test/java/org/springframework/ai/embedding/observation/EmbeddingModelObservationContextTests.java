/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.embedding.observation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.util.List;

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
			.embeddingRequest(generateEmbeddingRequest())
			.provider("superprovider")
			.requestOptions(EmbeddingOptionsBuilder.builder().withModel("supermodel").build())
			.build();

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenRequestOptionsIsNullThenThrow() {
		assertThatThrownBy(() -> EmbeddingModelObservationContext.builder()
			.embeddingRequest(generateEmbeddingRequest())
			.provider("superprovider")
			.requestOptions(null)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("requestOptions cannot be null");
	}

	private EmbeddingRequest generateEmbeddingRequest() {
		return new EmbeddingRequest(List.of(), EmbeddingOptionsBuilder.builder().build());
	}

}
