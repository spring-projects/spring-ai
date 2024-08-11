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
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation.LowCardinalityKeyNames;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;

/**
 * Unit tests for {@link DefaultVectorStoreObservationConvention}.
 *
 * @author Christian Tzolov
 */
class DefaultVectorStoreObservationConventionTests {

	private final DefaultVectorStoreObservationConvention observationConvention = new DefaultVectorStoreObservationConvention();

	@Test
	void shouldHaveName() {
		assertThat(this.observationConvention.getName())
			.isEqualTo(DefaultVectorStoreObservationConvention.DEFAULT_NAME);
	}

	@Test
	void shouldHaveContextualName() {
		VectorStoreObservationContext observationContext = VectorStoreObservationContext
			.builder("my-database", VectorStoreObservationContext.Operation.QUERY)
			.build();
		assertThat(this.observationConvention.getContextualName(observationContext))
			.isEqualTo("vector_store my-database query");
	}

	@Test
	void supportsOnlyVectorStoreObservationContext() {
		VectorStoreObservationContext observationContext = VectorStoreObservationContext
			.builder("my-database", VectorStoreObservationContext.Operation.QUERY)
			.build();
		assertThat(this.observationConvention.supportsContext(observationContext)).isTrue();
		assertThat(this.observationConvention.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void shouldHaveRequiredKeyValues() {
		VectorStoreObservationContext observationContext = VectorStoreObservationContext
			.builder("my_database", VectorStoreObservationContext.Operation.QUERY)
			.build();
		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(), "query"),
				KeyValue.of(LowCardinalityKeyNames.DB_SYSTEM.asString(), "my_database"));
	}

	@Test
	void shouldHaveOptionalKeyValues() {

		VectorStoreObservationContext observationContext = VectorStoreObservationContext
			.builder("my-database", VectorStoreObservationContext.Operation.QUERY)
			.withCollectionName("COLLECTION_NAME")
			.withDimensions(696)
			.withFieldName("FIELD_NAME")
			.withIndexName("INDEX_NAME")
			.withNamespace("NAMESPACE")
			.withSimilarityMetric("SIMILARITY_METRIC")
			.withQueryRequest(SearchRequest.query("VDB QUERY").withFilterExpression("country == 'UK' && year >= 2020"))
			.build();

		List<Document> queryResponseDocs = List.of(new Document("doc1"), new Document("doc2"));

		observationContext.setQueryResponse(queryResponseDocs);

		assertThat(this.observationConvention.getLowCardinalityKeyValues(observationContext))
			.contains(KeyValue.of(LowCardinalityKeyNames.DB_OPERATION_NAME.asString(),
					VectorStoreObservationContext.Operation.QUERY.value));

		// Optional, filter only added content
		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext))
			.doesNotContain(KeyValue.of(HighCardinalityKeyNames.QUERY_RESPONSE, "[doc1,doc2]"));

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(HighCardinalityKeyNames.COLLECTION_NAME.asString(), "COLLECTION_NAME"),
				KeyValue.of(HighCardinalityKeyNames.DIMENSIONS.asString(), "696"),
				KeyValue.of(HighCardinalityKeyNames.FIELD_NAME.asString(), "FIELD_NAME"),
				KeyValue.of(HighCardinalityKeyNames.INDEX_NAME.asString(), "INDEX_NAME"),
				KeyValue.of(HighCardinalityKeyNames.NAMESPACE.asString(), "NAMESPACE"),
				KeyValue.of(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), "SIMILARITY_METRIC"),
				KeyValue.of(HighCardinalityKeyNames.QUERY.asString(), "VDB QUERY"),
				KeyValue.of(HighCardinalityKeyNames.QUERY_METADATA_FILTER.asString(),
						"Expression[type=AND, left=Expression[type=EQ, left=Key[key=country], right=Value[value=UK]], right=Expression[type=GTE, left=Key[key=year], right=Value[value=2020]]]"));
	}

	@Test
	void shouldHaveMissingKeyValues() {
		VectorStoreObservationContext observationContext = VectorStoreObservationContext
			.builder("my-database", VectorStoreObservationContext.Operation.QUERY)
			.build();

		assertThat(this.observationConvention.getHighCardinalityKeyValues(observationContext)).contains(
				KeyValue.of(HighCardinalityKeyNames.COLLECTION_NAME.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.DIMENSIONS.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.FIELD_NAME.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.INDEX_NAME.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.NAMESPACE.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.SIMILARITY_METRIC.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.QUERY.asString(), KeyValue.NONE_VALUE),
				KeyValue.of(HighCardinalityKeyNames.QUERY_METADATA_FILTER.asString(), KeyValue.NONE_VALUE));
	}

}
