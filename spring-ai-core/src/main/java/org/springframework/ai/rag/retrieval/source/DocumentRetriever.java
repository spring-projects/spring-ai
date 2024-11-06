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

package org.springframework.ai.rag.retrieval.source;

import java.util.List;
import java.util.function.Function;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * API for retrieving {@link Document}s from an underlying data source.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface DocumentRetriever extends Function<Query, List<Document>> {

	/**
	 * Retrieves {@link Document}s from an underlying data source using the given
	 * {@link Query}.
	 */
	List<Document> retrieve(Query query);

	/**
	 * Retrieves {@link Document}s from an underlying data source using the given query
	 * string.
	 */
	default List<Document> retrieve(String query) {
		return retrieve(new Query(query));
	}

	/**
	 * Retrieves {@link Document}s from an underlying data source using the given
	 * {@link Query}.
	 */
	default List<Document> apply(Query query) {
		return retrieve(query);
	}

}
