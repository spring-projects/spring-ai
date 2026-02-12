/*
 * Copyright 2023-2026 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.model.Model;
import org.springframework.util.Assert;

/**
 * EmbeddingModel is a generic interface for embedding models.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Soby Chacko
 * @author Jihoon Kim
 * @since 1.0.0
 *
 */
public interface EmbeddingModel extends Model<EmbeddingRequest, EmbeddingResponse> {

	@Override
	EmbeddingResponse call(EmbeddingRequest request);

	/**
	 * Embeds the given text into a vector.
	 * @param text the text to embed.
	 * @return the embedded vector.
	 */
	default float[] embed(String text) {
		Assert.notNull(text, "Text must not be null");
		List<float[]> response = this.embed(List.of(text));
		return response.iterator().next();
	}

	/**
	 * Embeds the given document's content into a vector.
	 * @param document the document to embed.
	 * @return the embedded vector.
	 */
	float[] embed(Document document);

	/**
	 * Extracts the text content from a {@link Document} to be used for embedding. By
	 * default, returns {@link Document#getText()}. Implementations that support
	 * {@link org.springframework.ai.document.MetadataMode} should override this method to
	 * return
	 * {@link Document#getFormattedContent(org.springframework.ai.document.MetadataMode)}
	 * with the appropriate metadata mode, so that metadata is included in the text sent
	 * to the embedding API.
	 * @param document the document to extract embedding content from.
	 * @return the text content to embed.
	 */
	default String getEmbeddingContent(Document document) {
		Assert.notNull(document, "Document must not be null");
		return document.getText();
	}

	/**
	 * Embeds a batch of texts into vectors.
	 * @param texts list of texts to embed.
	 * @return list of embedded vectors.
	 */
	default List<float[]> embed(List<String> texts) {
		Assert.notNull(texts, "Texts must not be null");
		return this.call(new EmbeddingRequest(texts, EmbeddingOptions.builder().build()))
			.getResults()
			.stream()
			.map(Embedding::getOutput)
			.toList();
	}

	/**
	 * Embeds a batch of {@link Document}s into vectors based on a
	 * {@link BatchingStrategy}.
	 * @param documents list of {@link Document}s.
	 * @param options {@link EmbeddingOptions}.
	 * @param batchingStrategy {@link BatchingStrategy}.
	 * @return a list of float[] that represents the vectors for the incoming
	 * {@link Document}s. The returned list is expected to be in the same order of the
	 * {@link Document} list.
	 */
	default List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
		Assert.notNull(documents, "Documents must not be null");
		List<float[]> embeddings = new ArrayList<>(documents.size());
		List<List<Document>> batch = batchingStrategy.batch(documents);
		for (List<Document> subBatch : batch) {
			List<String> texts = subBatch.stream().map(this::getEmbeddingContent).toList();
			EmbeddingRequest request = new EmbeddingRequest(texts, options);
			EmbeddingResponse response = this.call(request);
			for (int i = 0; i < subBatch.size(); i++) {
				embeddings.add(response.getResults().get(i).getOutput());
			}
		}
		Assert.isTrue(embeddings.size() == documents.size(),
				"Embeddings must have the same number as that of the documents");
		return embeddings;
	}

	/**
	 * Embeds a batch of texts into vectors and returns the {@link EmbeddingResponse}.
	 * @param texts list of texts to embed.
	 * @return the embedding response.
	 */
	default EmbeddingResponse embedForResponse(List<String> texts) {
		Assert.notNull(texts, "Texts must not be null");
		return this.call(new EmbeddingRequest(texts, EmbeddingOptions.builder().build()));
	}

	/**
	 * Get the number of dimensions of the embedded vectors. Note that by default, this
	 * method will call the remote Embedding endpoint to get the dimensions of the
	 * embedded vectors. If the dimensions are known ahead of time, it is recommended to
	 * override this method.
	 * @return the number of dimensions of the embedded vectors.
	 */
	default int dimensions() {
		return embed("Test String").length;
	}

}
