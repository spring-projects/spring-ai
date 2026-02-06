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

package org.springframework.ai.rag.retrieval.join;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * A component for combining documents retrieved based on multiple queries and from
 * multiple data sources into a single collection of documents. As part of the joining
 * process, it can also handle duplicate documents and reciprocal ranking strategies.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface DocumentJoiner extends Function<Map<Query, List<List<Document>>>, List<Document>> {

	/**
	 * Joins documents retrieved across multiple queries and data sources.
	 * @param documentsForQuery a map of queries and the corresponding list of documents
	 * retrieved
	 * @return a single collection of documents
	 */
	List<Document> join(Map<Query, List<List<Document>>> documentsForQuery);

	default List<Document> apply(Map<Query, List<List<Document>>> documentsForQuery) {
		return join(documentsForQuery);
	}

}
