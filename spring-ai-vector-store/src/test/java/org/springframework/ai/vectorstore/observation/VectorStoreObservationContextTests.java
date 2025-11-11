/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.observation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VectorStoreObservationContext}.
 *
 * @author Christian Tzolov
 */
class VectorStoreObservationContextTests {

	@Test
	void whenMandatoryFieldsThenReturn() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.ADD)
			.build();
		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenDbSystemIsNullThenThrow() {
		assertThatThrownBy(() -> VectorStoreObservationContext.builder(null, "delete").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("databaseSystem cannot be null or empty");
	}

	@Test
	void whenOperationNameIsNullThenThrow() {
		assertThatThrownBy(() -> VectorStoreObservationContext.builder("Db", "").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("operationName cannot be null or empty");
	}

	@Test
	void whenEmptyDbSystemThenThrow() {
		assertThatThrownBy(
				() -> VectorStoreObservationContext.builder("", VectorStoreObservationContext.Operation.ADD).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("databaseSystem cannot be null or empty");
	}

	@Test
	void whenWhitespaceDbSystemThenThrow() {
		assertThatThrownBy(
				() -> VectorStoreObservationContext.builder("   ", VectorStoreObservationContext.Operation.ADD).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("databaseSystem cannot be null or empty");
	}

	@Test
	void whenStringOperationNameUsedThenCorrectValue() {
		var observationContext = VectorStoreObservationContext.builder("testdb", "custom_operation").build();
		assertThat(observationContext.getDatabaseSystem()).isEqualTo("testdb");
		assertThat(observationContext.getOperationName()).isEqualTo("custom_operation");
	}

	@Test
	void whenCollectionNameProvidedThenSet() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.ADD)
			.collectionName("documents")
			.build();

		assertThat(observationContext.getCollectionName()).isEqualTo("documents");
	}

	@Test
	void whenNoCollectionNameProvidedThenNull() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.ADD)
			.build();

		assertThat(observationContext.getCollectionName()).isNull();
	}

	@Test
	void whenNoDimensionsProvidedThenNull() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.QUERY)
			.build();

		assertThat(observationContext.getDimensions()).isNull();
	}

	@Test
	void whenFieldNameProvidedThenSet() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.QUERY)
			.fieldName("embedding_vector")
			.build();

		assertThat(observationContext.getFieldName()).isEqualTo("embedding_vector");
	}

	@Test
	void whenNamespaceProvidedThenSet() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.ADD)
			.namespace("production")
			.build();

		assertThat(observationContext.getNamespace()).isEqualTo("production");
	}

	@Test
	void whenSimilarityMetricProvidedThenSet() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.QUERY)
			.similarityMetric("cosine")
			.build();

		assertThat(observationContext.getSimilarityMetric()).isEqualTo("cosine");
	}

	@Test
	void whenEmptyCollectionNameThenSet() {
		var observationContext = VectorStoreObservationContext
			.builder("db", VectorStoreObservationContext.Operation.ADD)
			.collectionName("")
			.build();

		assertThat(observationContext.getCollectionName()).isEmpty();
	}

}
