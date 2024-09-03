/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;
import org.springframework.lang.NonNull;

record VectorSearchAggregation(List<Float> embeddings, String path, int numCandidates, String index, int count,
		String filter) implements AggregationOperation {

	@SuppressWarnings("null")
	@Override
	public org.bson.Document toDocument(@NonNull AggregationOperationContext context) {
		var vectorSearch = new Document("queryVector", embeddings).append("path", path)
			.append("numCandidates", numCandidates)
			.append("index", index)
			.append("limit", count);
		if (!filter.isEmpty()) {
			vectorSearch.append("filter", Document.parse(filter));
		}
		var doc = new Document("$vectorSearch", vectorSearch);

		return context.getMappedObject(doc);
	}
}