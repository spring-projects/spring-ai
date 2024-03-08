package org.springframework.ai.vectorstore;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;

import java.util.List;

record VectorSearchAggregation(List<Double> embeddings, String path, int numCandidates, String index,
		int count, String filter) implements AggregationOperation {
	@Override
	public org.bson.Document toDocument(AggregationOperationContext context) {
		var doc = new Document("$vectorSearch",
				new Document("queryVector", embeddings).append("path", path)
					.append("numCandidates", numCandidates)
					.append("index", index)
					.append("filter",filter)
					.append("limit", count));

		return context.getMappedObject(doc);
	}
}