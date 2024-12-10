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

package org.springframework.ai.rag.postretrieval.compression;

import java.util.List;
import java.util.function.BiFunction;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.ranking.DocumentRanker;
import org.springframework.ai.rag.postretrieval.selection.DocumentSelector;

/**
 * A component for compressing the content of each document to reduce noise and redundancy
 * in the retrieved information, addressing challenges such as "lost-in-the-middle" and
 * context length restrictions from the model.
 * <p>
 * Unlike {@link DocumentSelector}, this component does not remove entire documents from
 * the list, but rather alters the content of the documents. Unlike
 * {@link DocumentRanker}, this component does not change the order/score of the documents
 * in the list.
 */
public interface DocumentCompressor extends BiFunction<Query, List<Document>, List<Document>> {

	/**
	 * Compresses the content of each document.
	 * @param query the query to compress documents for
	 * @param documents the list of documents whose content should be compressed
	 * @return a list of documents with compressed content
	 */
	List<Document> compress(Query query, List<Document> documents);

	default List<Document> apply(Query query, List<Document> documents) {
		return compress(query, documents);
	}

}
