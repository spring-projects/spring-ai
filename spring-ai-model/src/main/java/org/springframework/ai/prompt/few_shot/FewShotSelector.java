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

package org.springframework.ai.prompt.few_shot;

import java.util.List;

/**
 * Strategy interface for selecting relevant few-shot examples from a pool.
 *
 * Different selection strategies can be implemented for different use cases:
 * <ul>
 * <li>Random selection for general cases
 * <li>Semantic similarity for production RAG systems
 * <li>Metadata-based filtering for domain-specific selection
 * <li>Custom implementations for specialized requirements
 * </ul>
 *
 * <p>
 * This follows the Strategy design pattern, allowing runtime selection of different
 * example selection algorithms without modifying the orchestrator code.
 *
 * <p>
 * Example usage: <pre>{@code
 * FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);
 * List<FewShotExample> selected = selector.select(
 *     "What is Spring?",
 *     availableExamples,
 *     3  // max 3 examples
 * );
 * }</pre>
 *
 * @author galt-k
 * @since 1.0
 */
@FunctionalInterface
public interface FewShotSelector {

	/**
	 * Select relevant few-shot examples from the available pool based on the user query.
	 * @param userQuery The user's current query or input
	 * @param availableExamples All examples available for selection (must not be null)
	 * @param maxExamples Maximum number of examples to return
	 * @return A list of selected examples in order of relevance (highest to lowest). The
	 * list size will be at most maxExamples. Never null, may be empty if no examples
	 * match the criteria.
	 * @throws IllegalArgumentException if userQuery is null or empty, availableExamples
	 * is null, or maxExamples is less than 1
	 */
	List<FewShotExample> select(String userQuery, List<FewShotExample> availableExamples, int maxExamples);

}
