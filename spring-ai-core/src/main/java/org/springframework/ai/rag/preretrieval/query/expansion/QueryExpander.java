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

package org.springframework.ai.rag.preretrieval.query.expansion;

import java.util.List;
import java.util.function.Function;

import org.springframework.ai.rag.Query;

/**
 * A component for expanding the input query into a list of queries, addressing challenges
 * such as poorly formed queries by providing alternative query formulations, or by
 * breaking down complex problems into simpler sub-queries.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface QueryExpander extends Function<Query, List<Query>> {

	/**
	 * Expands the given query into a list of queries.
	 * @param query The original query to be expanded
	 * @return A list of expanded queries
	 */
	List<Query> expand(Query query);

	default List<Query> apply(Query query) {
		return expand(query);
	}

}
