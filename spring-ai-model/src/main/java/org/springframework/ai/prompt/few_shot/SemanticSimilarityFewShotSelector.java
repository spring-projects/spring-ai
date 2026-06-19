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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.util.Assert;

/**
 * Implementation of {@link FewShotSelector} that selects examples using semantic
 * similarity.
 *
 * This selector finds the most semantically similar examples to the user's query by:
 * <ol>
 * <li>Embedding the user's query using the provided EmbeddingModel
 * <li>Computing cosine similarity between query and each example's input
 * <li>Ranking examples by similarity score
 * <li>Returning the top maxExamples
 * </ol>
 *
 * <p>
 * This approach is ideal for production RAG systems where semantic relevance matters. It
 * automatically selects the most contextually relevant examples without explicit metadata
 * tagging.
 *
 * <p>
 * Example: <pre>{@code
 * EmbeddingModel model = OpenAiEmbeddingModel.builder()
 *     .openAiApi(openAiApi)
 *     .build();
 * FewShotSelector selector = new SemanticSimilarityFewShotSelector(model);
 * List<FewShotExample> selected = selector.select(
 *     "What is Spring Framework?",
 *     examples,
 *     3
 * );
 * }</pre>
 *
 * @author galt-k
 * @since 1.0
 */
public class SemanticSimilarityFewShotSelector implements FewShotSelector {

	private final EmbeddingModel embeddingModel;

	/**
	 * Creates a new SemanticSimilarityFewShotSelector.
	 * @param embeddingModel the embedding model to use for computing embeddings, must not
	 * be null
	 */
	public SemanticSimilarityFewShotSelector(EmbeddingModel embeddingModel) {
		Assert.notNull(embeddingModel, "embeddingModel cannot be null");
		this.embeddingModel = embeddingModel;
	}

	@Override
	public List<FewShotExample> select(String userQuery, List<FewShotExample> availableExamples, int maxExamples) {
		Assert.hasText(userQuery, "userQuery cannot be null or empty");
		Assert.notNull(availableExamples, "availableExamples cannot be null");
		Assert.isTrue(maxExamples > 0, "maxExamples must be greater than 0");

		// Return empty list if no examples available
		if (availableExamples.isEmpty()) {
			return new ArrayList<>();
		}

		// Embed the user query
		float[] queryEmbedding = this.embeddingModel.embed(userQuery);

		// Score each example by similarity and sort
		return availableExamples.stream().map(example -> {
			// Embed the example's input
			float[] exampleEmbedding = this.embeddingModel.embed(example.getInput());
			// Calculate cosine similarity
			double similarity = cosineSimilarity(queryEmbedding, exampleEmbedding);
			// Create new example with updated relevance score
			return FewShotExample.builder()
				.id(example.getId())
				.input(example.getInput())
				.output(example.getOutput())
				.metadata(example.getMetadata())
				.relevanceScore(similarity)
				.build();
		})
			// Sort by relevance score descending (highest similarity first)
			.sorted(Comparator.comparingDouble(FewShotExample::getRelevanceScore).reversed())
			.limit(maxExamples)
			.collect(Collectors.toList());
	}

	/**
	 * Computes cosine similarity between two vectors.
	 *
	 * Cosine similarity = (a · b) / (||a|| * ||b||)
	 * @param vectorA first vector
	 * @param vectorB second vector
	 * @return cosine similarity, range [-1, 1], where 1 means identical direction
	 * @throws IllegalArgumentException if vectors have different lengths
	 */
	private double cosineSimilarity(float[] vectorA, float[] vectorB) {
		Assert.isTrue(vectorA.length == vectorB.length,
				"Vectors must have the same length: " + vectorA.length + " vs " + vectorB.length);

		// Compute dot product
		double dotProduct = 0.0;
		for (int i = 0; i < vectorA.length; i++) {
			dotProduct += vectorA[i] * vectorB[i];
		}

		// Compute magnitudes
		double magnitudeA = 0.0;
		double magnitudeB = 0.0;
		for (int i = 0; i < vectorA.length; i++) {
			magnitudeA += vectorA[i] * vectorA[i];
			magnitudeB += vectorB[i] * vectorB[i];
		}
		magnitudeA = Math.sqrt(magnitudeA);
		magnitudeB = Math.sqrt(magnitudeB);

		// Avoid division by zero
		if (magnitudeA == 0.0 || magnitudeB == 0.0) {
			return 0.0;
		}

		return dotProduct / (magnitudeA * magnitudeB);
	}

	/**
	 * Returns the embedding model used by this selector.
	 * @return the embedding model
	 */
	public EmbeddingModel getEmbeddingModel() {
		return this.embeddingModel;
	}

}
