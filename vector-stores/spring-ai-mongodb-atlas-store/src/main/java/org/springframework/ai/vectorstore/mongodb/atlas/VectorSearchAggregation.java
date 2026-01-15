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

package org.springframework.ai.vectorstore.mongodb.atlas;

import java.util.List;

import org.bson.Document;

import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext;

record VectorSearchAggregation(List<Float> embeddings, String path, int numCandidates, String index, int count,
		String filter) implements AggregationOperation {

	@Override
	public org.bson.Document toDocument(AggregationOperationContext context) {
		var vectorSearch = new Document("queryVector", this.embeddings).append("path", this.path)
			.append("numCandidates", this.numCandidates)
			.append("index", this.index)
			.append("limit", this.count);
		if (!this.filter.isEmpty()) {
			vectorSearch.append("filter", Document.parse(this.filter));
		}
		var doc = new Document("$vectorSearch", vectorSearch);

		return context.getMappedObject(doc);
	}

}
