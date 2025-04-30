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

package org.springframework.ai.rag.generation.augmentation;

import java.util.List;
import java.util.function.BiFunction;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * A component for augmenting an input query with additional data, useful to provide a
 * large language model with the necessary context to answer the user query.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface QueryAugmenter extends BiFunction<Query, List<Document>, Query> {

	/**
	 * Augments the user query with contextual data.
	 * @param query The user query to augment
	 * @param documents The contextual data to use for augmentation
	 * @return The augmented query
	 */
	Query augment(Query query, List<Document> documents);

	default Query apply(Query query, List<Document> documents) {
		return augment(query, documents);
	}

}
