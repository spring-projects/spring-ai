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

package org.springframework.ai.rag.orchestration.routing;

import java.util.List;
import java.util.function.Function;

import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

/**
 * A component for routing a query to one or more document retrievers. It provides a
 * decision-making mechanism to support various scenarios and making the Retrieval
 * Augmented Generation flow more flexible and extensible. It can be used to implement
 * routing strategies using metadata, large language models, tools (the foundation of
 * Agentic RAG), and other techniques.
 * <p>
 * When retrieving documents from multiple sources, you'll need to join the results before
 * concluding the retrieval stage. For this purpose, you can use the
 * {@link DocumentJoiner}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface QueryRouter extends Function<Query, List<DocumentRetriever>> {

	/**
	 * Routes a query to one or more document retrievers.
	 * @param query the query to route
	 * @return a list of document retrievers
	 */
	List<DocumentRetriever> route(Query query);

	default List<DocumentRetriever> apply(Query query) {
		return route(query);
	}

}
