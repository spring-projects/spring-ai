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
package org.springframework.ai.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.Model;
import org.springframework.util.Assert;

import java.util.List;

/**
 * EmbeddingModel is a generic interface for embedding models.
 */
public interface EmbeddingModel extends Model<EmbeddingRequest, EmbeddingResponse> {

	@Override
	EmbeddingResponse call(EmbeddingRequest request);

	/**
	 * Embeds the given text into a vector.
	 * @param text the text to embed.
	 * @return the embedded vector.
	 */
	default List<Double> embed(String text) {
		Assert.notNull(text, "Text must not be null");
		return this.embed(List.of(text)).iterator().next();
	}

	/**
	 * Embeds the given document's content into a vector.
	 * @param document the document to embed.
	 * @return the embedded vector.
	 */
	List<Double> embed(Document document);

	/**
	 * Embeds a batch of texts into vectors.
	 * @param texts list of texts to embed.
	 * @return list of list of embedded vectors.
	 */
	default List<List<Double>> embed(List<String> texts) {
		Assert.notNull(texts, "Texts must not be null");
		return this.call(new EmbeddingRequest(texts, EmbeddingOptionsBuilder.builder().build()))
			.getResults()
			.stream()
			.map(Embedding::getOutput)
			.toList();
	}

	/**
	 * Embeds a batch of texts into vectors and returns the {@link EmbeddingResponse}.
	 * @param texts list of texts to embed.
	 * @return the embedding response.
	 */
	default EmbeddingResponse embedForResponse(List<String> texts) {
		Assert.notNull(texts, "Texts must not be null");
		return this.call(new EmbeddingRequest(texts, EmbeddingOptionsBuilder.builder().build()));
	}

	/**
	 * @return the number of dimensions of the embedded vectors. It is generative
	 * specific.
	 */
	default int dimensions() {
		return embed("Test String").size();
	}

}
