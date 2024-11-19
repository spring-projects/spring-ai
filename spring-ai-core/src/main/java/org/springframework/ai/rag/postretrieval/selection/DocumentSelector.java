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

package org.springframework.ai.rag.postretrieval.selection;

import java.util.List;
import java.util.function.BiFunction;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.compression.DocumentCompressor;
import org.springframework.ai.rag.postretrieval.ranking.DocumentRanker;

/**
 * A component for removing irrelevant or redundant documents from a list of retrieved
 * documents, addressing challenges such as "lost-in-the-middle" and context length
 * restrictions from the model.
 * <p>
 * Unlike {@link DocumentRanker}, this component does not change the order/score of the
 * documents in the list, but rather removes irrelevant or redundant documents. Unlike
 * {@link DocumentCompressor}, this component does not alter the content of the documents,
 * but rather removes entire documents.
 */
public interface DocumentSelector extends BiFunction<Query, List<Document>, List<Document>> {

	/**
	 * Removes irrelevant or redundant documents from a list of retrieved documents.
	 * @param query the query to select documents for
	 * @param documents the list of documents to select from
	 * @return a list of selected documents
	 */
	List<Document> select(Query query, List<Document> documents);

	default List<Document> apply(Query query, List<Document> documents) {
		return select(query, documents);
	}

}
