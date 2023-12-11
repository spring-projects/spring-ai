/*
 * Copyright 2023-2023 the original author or authors.
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

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Converts a {@link Document} text into its vector representation (e.g. embedding).
 */
public interface EmbeddingClient {

	/**
	 * Computes embedding for provided, raw, text.
	 * @param text Input text to commute the embedding for.
	 * @return Returns a raw, double, list of the embedding representation of the input
	 * text.
	 */
	List<Double> embed(String text);

	/**
	 * Computes embedding for provided document.
	 * @param document Document to commute the embedding for.
	 * @return Returns a raw, double, list of the embedding representation of the input
	 * text.
	 */
	List<Double> embed(Document document);

	/**
	 * Computes embeddings for provided list of text.
	 * @param texts list of input text to compute embeddings for.
	 * @return Returns a list of embeddings. The order corresponds to the input text list.
	 */
	List<List<Double>> embed(List<String> texts);

	/**
	 * Computes embeddings for provided list of text.
	 * @param texts list of input text to compute embeddings for.
	 * @return Returns
	 */
	EmbeddingResponse embedForResponse(List<String> texts);

	/**
	 * Retrieves embedding model's dimensions.
	 * @return Returns the vector dimensions for the configured embedding model.
	 */
	default int dimensions() {
		return embed("Test String").size();
	}

}
