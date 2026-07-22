/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.rag.postretrieval.document;

import java.util.List;
import java.util.function.BiFunction;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * Reranks retrieved documents according to their relevance to a query.
 *
 * @author KoreaNirsa
 */
public interface DocumentReranker extends BiFunction<Query, List<Document>, List<Document>> {

	/**
	 * Rerank the given documents for the query.
	 * @param query the user query
	 * @param documents the retrieved documents to rerank
	 * @return the reranked documents
	 */
	List<Document> rerank(Query query, List<Document> documents);

	@Override
	default List<Document> apply(Query query, List<Document> documents) {
		return rerank(query, documents);
	}

}
