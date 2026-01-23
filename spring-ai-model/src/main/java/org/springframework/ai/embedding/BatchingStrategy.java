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

package org.springframework.ai.embedding;

import java.util.List;

import org.springframework.ai.document.Document;

/**
 * Contract for batching {@link Document} objects so that the call to embed them could be
 * optimized.
 *
 * @author Soby Chacko
 * @since 1.0.0
 */
public interface BatchingStrategy {

	/**
	 * EmbeddingModel implementations can call this method to optimize embedding tokens.
	 * The incoming collection of {@link Document}s are split into sub-batches. It is
	 * important to preserve the order of the list of {@link Document}s when batching as
	 * they are mapped to their corresponding embeddings by their order.
	 * @param documents to batch
	 * @return a list of sub-batches that contain {@link Document}s.
	 */
	List<List<Document>> batch(List<Document> documents);

}
