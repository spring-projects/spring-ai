/*
 * Copyright 2026 the original author or authors.
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

package org.springframework.ai.transformer.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.util.Assert;

/**
 * A {@link TextSplitter} that splits text into chunks based on semantic similarity
 * between consecutive sentences, as measured by cosine similarity of their embedding
 * vectors.
 *
 * <p>
 * Unlike {@link TokenTextSplitter} which splits at fixed token boundaries, this splitter
 * respects natural semantic boundaries — splitting only where embedding similarity
 * between adjacent sentences drops below a configurable threshold.
 *
 * <p>
 * This produces variable-sized chunks that preserve semantic coherence, improving RAG
 * retrieval quality.
 *
 * @author ENG (RedTeam)
 * @since 1.0.0
 */
public class SemanticTextSplitter extends TextSplitter {

	/**
	 * Default similarity threshold below which a semantic split occurs.
	 */
	public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

	/**
	 * Default maximum chunk size in characters.
	 */
	public static final int DEFAULT_MAX_CHUNK_SIZE = 1000;

	private final EmbeddingModel embeddingModel;

	private final double similarityThreshold;

	private final int maxChunkSize;

	/**
	 * Creates a new semantic text splitter.
	 * @param embeddingModel the embedding model used to compute sentence similarity
	 * @param similarityThreshold threshold [0,1] below which a semantic split occurs
	 * @param maxChunkSize maximum character size before forcing a new chunk
	 */
	public SemanticTextSplitter(EmbeddingModel embeddingModel, double similarityThreshold, int maxChunkSize) {
		Assert.notNull(embeddingModel, "embeddingModel must not be null");
		Assert.isTrue(similarityThreshold >= 0 && similarityThreshold <= 1,
				"similarityThreshold must be between 0 and 1");
		Assert.isTrue(maxChunkSize > 0, "maxChunkSize must be positive");
		this.embeddingModel = embeddingModel;
		this.similarityThreshold = similarityThreshold;
		this.maxChunkSize = maxChunkSize;
	}

	/**
	 * Creates a new semantic text splitter with default max chunk size (1000 chars).
	 */
	public SemanticTextSplitter(EmbeddingModel embeddingModel, double similarityThreshold) {
		this(embeddingModel, similarityThreshold, DEFAULT_MAX_CHUNK_SIZE);
	}

	/**
	 * Creates a new semantic text splitter with defaults (threshold=0.5, max=1000).
	 */
	public SemanticTextSplitter(EmbeddingModel embeddingModel) {
		this(embeddingModel, DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_MAX_CHUNK_SIZE);
	}

	/**
	 * Creates a new semantic text splitter from a builder.
	 */
	SemanticTextSplitter(Builder builder) {
		this(builder.embeddingModel, builder.similarityThreshold, builder.maxChunkSize);
	}

	/**
	 * Returns a new builder for {@link SemanticTextSplitter}.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/** Builder for {@link SemanticTextSplitter}. */
	public static class Builder {

		private EmbeddingModel embeddingModel;

		private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

		private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;

		public Builder embeddingModel(EmbeddingModel embeddingModel) {
			this.embeddingModel = embeddingModel;
			return this;
		}

		public Builder similarityThreshold(double similarityThreshold) {
			this.similarityThreshold = similarityThreshold;
			return this;
		}

		public Builder maxChunkSize(int maxChunkSize) {
			this.maxChunkSize = maxChunkSize;
			return this;
		}

		public SemanticTextSplitter build() {
			return new SemanticTextSplitter(this);
		}

	}

	@Override
	protected List<String> splitText(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}

		List<String> sentences = splitIntoSentences(text);
		if (sentences.isEmpty()) {
			return List.of();
		}
		if (sentences.size() == 1) {
			return sentences;
		}

		// Embed all sentences in one batch call
		List<float[]> embeddings = this.embeddingModel.embed(sentences);

		List<String> chunks = new ArrayList<>();
		StringBuilder currentChunk = new StringBuilder();
		List<Integer> currentSentenceIndices = new ArrayList<>();

		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);

			// Force new chunk if maxChunkSize exceeded
			if (currentChunk.length() + sentence.length() > maxChunkSize && !currentChunk.isEmpty()) {
				chunks.add(currentChunk.toString().trim());
				currentChunk.setLength(0);
				currentSentenceIndices.clear();
			}

			currentChunk.append(sentence).append(" ");
			currentSentenceIndices.add(i);

			// Check semantic similarity with next sentence
			if (i < sentences.size() - 1 && currentChunk.length() > 0) {
				double similarity = cosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
				if (similarity < this.similarityThreshold) {
					chunks.add(currentChunk.toString().trim());
					currentChunk.setLength(0);
					currentSentenceIndices.clear();
				}
			}
		}

		// Flush remaining content
		if (!currentChunk.isEmpty()) {
			chunks.add(currentChunk.toString().trim());
		}

		return chunks;
	}

	/**
	 * Splits text into sentences using punctuation marks.
	 */
	List<String> splitIntoSentences(String text) {
		List<String> sentences = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			current.append(c);

			if (c == '.' || c == '!' || c == '?') {
				// Check this isn't an ellipsis or decimal
				if (i + 2 < text.length() && text.charAt(i + 1) == '.' && text.charAt(i + 2) == '.') {
					// Ellipsis — already added single dot, that's fine
				}
				String sentence = current.toString().trim();
				if (!sentence.isEmpty()) {
					sentences.add(sentence);
				}
				current.setLength(0);
			}
		}

		String remaining = current.toString().trim();
		if (!remaining.isEmpty()) {
			sentences.add(remaining);
		}

		return sentences;
	}

	/**
	 * Computes cosine similarity between two embedding vectors.
	 */
	double cosineSimilarity(float[] vecA, float[] vecB) {
		if (vecA.length != vecB.length) {
			return 0.0;
		}
		double dotProduct = 0.0;
		double normA = 0.0;
		double normB = 0.0;
		for (int i = 0; i < vecA.length; i++) {
			dotProduct += vecA[i] * vecB[i];
			normA += vecA[i] * vecA[i];
			normB += vecB[i] * vecB[i];
		}
		if (normA == 0 || normB == 0) {
			return 0.0;
		}
		return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
	}

	public EmbeddingModel getEmbeddingModel() {
		return this.embeddingModel;
	}

	public double getSimilarityThreshold() {
		return this.similarityThreshold;
	}

	public int getMaxChunkSize() {
		return this.maxChunkSize;
	}

}
