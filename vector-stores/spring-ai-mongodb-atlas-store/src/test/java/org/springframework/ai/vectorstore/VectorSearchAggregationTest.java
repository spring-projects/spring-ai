package org.springframework.ai.vectorstore;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.aggregation.Aggregation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorSearchAggregationTest {

	@Test
	void toDocument() {
		var vectorSearchAggregation = new VectorSearchAggregation(List.of(1.0, 2.0, 3.0), "embedding", 10,
				"vector_store", 10, "{}");
		var aggregation = Aggregation.newAggregation(vectorSearchAggregation);
		var document = aggregation.toDocument("vector_store", Aggregation.DEFAULT_CONTEXT);

		var vectorSearchDocument = new Document("$vectorSearch",
				new Document("queryVector", List.of(1.0, 2.0, 3.0)).append("path", "embedding")
					.append("numCandidates", 10)
					.append("index", "vector_store")
					.append("filter", "{}")
					.append("limit", 10));
		var expected = new Document().append("aggregate", "vector_store")
			.append("pipeline", List.of(vectorSearchDocument));
		assertEquals(expected, document);
	}

}