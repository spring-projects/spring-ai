/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.test.vectorstore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A minimal deterministic {@link EmbeddingModel} that returns a constant vector of a
 * configured dimension, for tests that must not depend on a hosted embedding provider.
 * <p>
 * It is the query-side model for the {@link AbstractVectorStoreUpsertTests} suite:
 * {@code upsert} supplies its own vectors, and the suite reads back with
 * {@code similarityThresholdAll()} regardless of ranking, so a constant vector is
 * sufficient and no API key is needed. Wire it into a store's upsert integration test and
 * size the store's index to {@link #dimensions()}.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
public final class FixedDimensionEmbeddingModel implements EmbeddingModel {

	private final int dimensions;

	public FixedDimensionEmbeddingModel(int dimensions) {
		this.dimensions = dimensions;
	}

	@Override
	public float[] embed(Document document) {
		return fixedVector();
	}

	@Override
	public float[] embed(String text) {
		return fixedVector();
	}

	private float[] fixedVector() {
		float[] vector = new float[this.dimensions];
		Arrays.fill(vector, 0.1f);
		return vector;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<Embedding> embeddings = new ArrayList<>();
		for (int i = 0; i < request.getInstructions().size(); i++) {
			embeddings.add(new Embedding(embed(request.getInstructions().get(i)), i));
		}
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public int dimensions() {
		return this.dimensions;
	}

}
