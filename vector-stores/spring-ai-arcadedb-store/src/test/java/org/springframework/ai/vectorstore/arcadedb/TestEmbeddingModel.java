/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.arcadedb;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Simple deterministic embedding model for testing. Generates fixed-dimension
 * embeddings based on text hash.
 *
 * @author Luca Garulli
 */
class TestEmbeddingModel implements EmbeddingModel {

	private final int dimensions;

	TestEmbeddingModel(int dimensions) {
		this.dimensions = dimensions;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<Embedding> embeddings = request.getInstructions().stream()
				.map(text -> new Embedding(generateEmbedding(text),
						request.getInstructions().indexOf(text)))
				.toList();
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public float[] embed(Document document) {
		return generateEmbedding(document.getText());
	}

	@Override
	public float[] embed(String text) {
		return generateEmbedding(text);
	}

	private float[] generateEmbedding(String text) {
		float[] embedding = new float[dimensions];
		if (text == null || text.isEmpty()) {
			return embedding;
		}
		int hash = text.hashCode();
		for (int i = 0; i < dimensions; i++) {
			embedding[i] = (float) Math.sin(hash * (i + 1) * 0.1);
		}
		float norm = 0;
		for (float v : embedding) {
			norm += v * v;
		}
		norm = (float) Math.sqrt(norm);
		if (norm > 0) {
			for (int i = 0; i < dimensions; i++) {
				embedding[i] /= norm;
			}
		}
		return embedding;
	}

}
