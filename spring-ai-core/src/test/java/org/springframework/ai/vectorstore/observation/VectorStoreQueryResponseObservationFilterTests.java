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
package org.springframework.ai.vectorstore.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;

/**
 * Unit tests for {@link VectorStoreQueryResponseObservationFilter}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
class VectorStoreQueryResponseObservationFilterTests {

	private final VectorStoreQueryResponseObservationFilter observationFilter = new VectorStoreQueryResponseObservationFilter();

	@Test
	void whenNotSupportedObservationContextThenReturnOriginalContext() {
		var expectedContext = new Observation.Context();
		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenEmptyQueryResponseThenReturnOriginalContext() {
		var expectedContext = VectorStoreObservationContext.builder("db", VectorStoreObservationContext.Operation.ADD)
			.build();

		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenNonEmptyQueryResponseThenAugmentContext() {
		var expectedContext = VectorStoreObservationContext.builder("db", VectorStoreObservationContext.Operation.ADD)
			.build();

		List<Document> queryResponseDocs = List.of(new Document("doc1"), new Document("doc2"));

		expectedContext.setQueryResponse(queryResponseDocs);

		var augmentedContext = observationFilter.map(expectedContext);

		assertThat(augmentedContext.getHighCardinalityKeyValues()).contains(KeyValue
			.of(HighCardinalityKeyNames.DB_VECTOR_QUERY_RESPONSE_DOCUMENTS.asString(), "[\"doc1\", \"doc2\"]"));
	}

}
