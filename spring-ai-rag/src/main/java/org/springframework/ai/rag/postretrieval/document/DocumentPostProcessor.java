/*
 * Copyright 2023-2025 the original author or authors.
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
 * A component for post-processing retrieved documents based on a query, addressing
 * challenges such as "lost-in-the-middle", context length restrictions from the model,
 * and the need to reduce noise and redundancy in the retrieved information.
 * <p>
 * For example, it could rank documents based on their relevance to the query, remove
 * irrelevant or redundant documents, or compress the content of each document to reduce
 * noise and redundancy.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface DocumentPostProcessor extends BiFunction<Query, List<Document>, List<Document>> {

	List<Document> process(Query query, List<Document> documents);

	default List<Document> apply(Query query, List<Document> documents) {
		return process(query, documents);
	}

}
