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

package org.springframework.ai.rag.analysis.query.transformation;

import java.util.function.Function;

import org.springframework.ai.rag.Query;

/**
 * Component responsible for transforming the input query based on a specified strategy.
 * These transformations can be used to enhance the clarity, semantic meaning, or language
 * of the query, thereby improving the effectiveness of the retrieval process.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface QueryTransformer extends Function<Query, Query> {

	/**
	 * Transforms the given query according to the implemented strategy.
	 * @param query The original query to transform
	 * @return The transformed query
	 */
	Query transform(Query query);

	/**
	 * Transforms the given query according to the implemented strategy.
	 * @param query The original query to transform
	 * @return The transformed query
	 */
	default Query apply(Query query) {
		return transform(query);
	}

}
