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

package org.springframework.ai.rag.postretrieval.ranking;

import java.util.List;
import java.util.function.BiFunction;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.compression.DocumentCompressor;
import org.springframework.ai.rag.postretrieval.selection.DocumentSelector;

/**
 * A component for ordering and ranking documents based on their relevance to a query to
 * bring the most relevant documents to the top of the list, addressing challenges such as
 * "lost-in-the-middle".
 * <p>
 * Unlike {@link DocumentSelector}, this component does not remove entire documents from
 * the list, but rather changes the order/score of the documents in the list. Unlike
 * {@link DocumentCompressor}, this component does not alter the content of the documents.
 */
public interface DocumentRanker extends BiFunction<Query, List<Document>, List<Document>> {

	/**
	 * Ranks documents based on their relevance to the given query.
	 * @param query the query to rank documents for
	 * @param documents the list of documents to rank
	 * @return a list of ordered documents based on a ranking algorithm
	 */
	List<Document> rank(Query query, List<Document> documents);

	default List<Document> apply(Query query, List<Document> documents) {
		return rank(query, documents);
	}

}
