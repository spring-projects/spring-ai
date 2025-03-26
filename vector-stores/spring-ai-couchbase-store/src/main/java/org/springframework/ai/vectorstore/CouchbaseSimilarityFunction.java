/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

/**
 * Choose the method to calculate the similarity between the vector embedding in a Vector
 * Search index and the vector embedding in a Vector Search query. See
 * https://docs.couchbase.com/server/current/search/child-field-options-reference.html for
 * more details.
 *
 * @author Laurent Doguin
 * @since 1.0.0
 */
public enum CouchbaseSimilarityFunction {

	/**
	 * It’s best to use l2_norm similarity when your embeddings contain information about
	 * the count or measure of specific things, and your embedding model uses the same
	 * similarity metric.
	 */
	l2_norm,
	/**
	 * Dot product similarity is commonly used by Large Language Models (LLMs). Use
	 * dot_product to get the best results with an embedding model that uses dot product
	 * similarity.
	 */
	dot_product

}
