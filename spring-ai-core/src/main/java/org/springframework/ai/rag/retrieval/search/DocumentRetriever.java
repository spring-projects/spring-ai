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

package org.springframework.ai.rag.retrieval.search;

import java.util.List;
import java.util.function.Function;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * Component responsible for retrieving {@link Document}s from an underlying data source,
 * such as a search engine, a vector store, a database, or a knowledge graph.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface DocumentRetriever extends Function<Query, List<Document>> {

	/**
	 * Retrieves relevant documents from an underlying data source based on the given
	 * query.
	 * @param query The query to use for retrieving documents
	 * @return The list of relevant documents
	 */
	List<Document> retrieve(Query query);

	default List<Document> apply(Query query) {
		return retrieve(query);
	}

}
