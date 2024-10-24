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

package org.springframework.ai.vectorstore;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import org.springframework.data.mongodb.core.aggregation.Aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorSearchAggregationTest {

	@Test
	void toDocumentNoFilter() {
		var vectorSearchAggregation = new VectorSearchAggregation(List.of(1.0f, 2.0f, 3.0f), "embedding", 10,
				"vector_store", 10, "");
		var aggregation = Aggregation.newAggregation(vectorSearchAggregation);
		var document = aggregation.toDocument("vector_store", Aggregation.DEFAULT_CONTEXT);

		var vectorSearchDocument = new Document("$vectorSearch",
				new Document("queryVector", List.of(1.0f, 2.0f, 3.0f)).append("path", "embedding")
					.append("numCandidates", 10)
					.append("index", "vector_store")
					.append("limit", 10));
		var expected = new Document().append("aggregate", "vector_store")
			.append("pipeline", List.of(vectorSearchDocument));
		assertEquals(expected, document);
	}

	@Test
	void toDocumentWithFilter() {
		var vectorSearchAggregation = new VectorSearchAggregation(List.of(1.0f, 2.0f, 3.0f), "embedding", 10,
				"vector_store", 10, "{\"metadata.country\":{$eq:\"BG\"}}");
		var aggregation = Aggregation.newAggregation(vectorSearchAggregation);
		var document = aggregation.toDocument("vector_store", Aggregation.DEFAULT_CONTEXT);

		var vectorSearchDocument = new Document("$vectorSearch",
				new Document("queryVector", List.of(1.0f, 2.0f, 3.0f)).append("path", "embedding")
					.append("numCandidates", 10)
					.append("index", "vector_store")
					.append("filter", new Document("metadata.country", new Document().append("$eq", "BG")))
					.append("limit", 10));
		var expected = new Document().append("aggregate", "vector_store")
			.append("pipeline", List.of(vectorSearchDocument));
		assertEquals(expected, document);
	}

}
