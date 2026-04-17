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

package org.springframework.ai.transformer.splitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link TextSplitter} that uses semantic similarity to determine chunk boundaries.
 *
 * <p>
 * Unlike {@link TokenTextSplitter}, which splits at fixed token counts,
 * {@code SemanticTextSplitter} embeds each sentence using a configured
 * {@link EmbeddingModel} and splits where the cosine similarity between consecutive
 * sentence embeddings drops below a configurable threshold. This preserves semantic
 * coherence within each chunk, improving RAG retrieval quality.
 * </p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 * <li>Split the input text into sentences (on {@code .}, {@code !}, or {@code ?}).</li>
 * <li>Embed each sentence via the provided {@link EmbeddingModel}.</li>
 * <li>Compute cosine similarity between consecutive sentence embeddings.</li>
 * <li>Start a new chunk whenever similarity drops below {@code similarityThreshold}
 * <em>or</em> the accumulated chunk length would exceed {@code maxChunkSize}
 * characters.</li>
 * </ol>
 *
 * <h3>Edge cases</h3>
 * <ul>
 * <li>Empty or blank input → returns an empty list.</li>
 * <li>Single sentence → returned as a single chunk.</li>
 * <li>All similarities above threshold → entire text becomes one chunk (capped by
 * {@code maxChunkSize}).</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * EmbeddingModel embeddingModel = ...; // inject any Spring AI EmbeddingModel
 * SemanticTextSplitter splitter = SemanticTextSplitter.builder()
 *         .embeddingModel(embeddingModel)
 *         .similarityThreshold(0.6)
 *         .maxChunkSize(800)
 *         .build();
 *
 * List<Document> chunks = splitter.split(document);
 * }</pre>
 *
 * @author Anurag Saxena
 * @since 1.0.0
 * @see TextSplitter
 * @see TokenTextSplitter
 */
public class SemanticTextSplitter extends TextSplitter {

	private static final Logger logger = LoggerFactory.getLogger(SemanticTextSplitter.class);

	/**
	 * Default cosine-similarity threshold below which a new chunk is started.
	 */
	public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

	/**
	 * Default maximum chunk size in characters.
	 */
	public static final int DEFAULT_MAX_CHUNK_SIZE = 1000;

	/**
	 * Sentence boundary regex: split on {@code .}, {@code !}, or {@code ?} followed by
	 * whitespace or end-of-string.
	 */
	private static final String SENTENCE_SPLIT_REGEX = "(?<=[.!?])\\s+";

	private final EmbeddingModel embeddingModel;

	private final double similarityThreshold;

	private final int maxChunkSize;

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Create a {@code SemanticTextSplitter} with default parameters.
	 * @param embeddingModel the embedding model used to compute sentence embeddings
	 */
	public SemanticTextSplitter(EmbeddingModel embeddingModel) {
		this(embeddingModel, DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_MAX_CHUNK_SIZE);
	}

	/**
	 * Create a {@code SemanticTextSplitter} with explicit parameters.
	 * @param embeddingModel the embedding model used to compute sentence embeddings
	 * @param similarityThreshold cosine-similarity threshold in {@code [0, 1]}; a new
	 * chunk is started when similarity falls below this value
	 * @param maxChunkSize maximum character length of a single chunk; sentences exceeding
	 * this on their own are kept as individual chunks regardless of similarity
	 */
	public SemanticTextSplitter(EmbeddingModel embeddingModel, double similarityThreshold, int maxChunkSize) {
		Assert.notNull(embeddingModel, "embeddingModel must not be null");
		Assert.isTrue(similarityThreshold >= 0 && similarityThreshold <= 1,
				"similarityThreshold must be in [0, 1], got: " + similarityThreshold);
		Assert.isTrue(maxChunkSize > 0, "maxChunkSize must be positive, got: " + maxChunkSize);
		this.embeddingModel = embeddingModel;
		this.similarityThreshold = similarityThreshold;
		this.maxChunkSize = maxChunkSize;
	}

	// -------------------------------------------------------------------------
	// TextSplitter contract
	// -------------------------------------------------------------------------

	@Override
	protected List<String> splitText(String text) {
		if (!StringUtils.hasText(text)) {
			return Collections.emptyList();
		}

		// 1. Tokenize into sentences
		List<String> sentences = tokenizeSentences(text);
		if (sentences.isEmpty()) {
			return Collections.emptyList();
		}
		if (sentences.size() == 1) {
			return Collections.singletonList(sentences.get(0));
		}

		// 2. Embed every sentence
		List<float[]> embeddings = embedSentences(sentences);

		// 3. Build chunks based on cosine similarity and maxChunkSize
		return buildChunks(sentences, embeddings);
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	/**
	 * Split the input text into individual sentences. Sentences are trimmed and blank
	 * fragments are discarded.
	 */
	private List<String> tokenizeSentences(String text) {
		String[] parts = text.split(SENTENCE_SPLIT_REGEX);
		List<String> sentences = new ArrayList<>(parts.length);
		for (String part : parts) {
			String trimmed = part.strip();
			if (!trimmed.isEmpty()) {
				sentences.add(trimmed);
			}
		}
		return sentences;
	}

	/**
	 * Embed each sentence using the configured {@link EmbeddingModel}. Returns a
	 * parallel list of raw embedding vectors.
	 */
	private List<float[]> embedSentences(List<String> sentences) {
		List<float[]> embeddings = new ArrayList<>(sentences.size());
		for (String sentence : sentences) {
			float[] vector = embeddingModel.embed(sentence);
			embeddings.add(vector);
		}
		return embeddings;
	}

	/**
	 * Assemble chunks by walking consecutive sentence pairs and measuring cosine
	 * similarity. A new chunk starts when:
	 * <ul>
	 * <li>cosine similarity between sentence {@code i} and {@code i+1} is below
	 * {@link #similarityThreshold}, or</li>
	 * <li>adding sentence {@code i+1} to the current buffer would exceed
	 * {@link #maxChunkSize}.</li>
	 * </ul>
	 */
	private List<String> buildChunks(List<String> sentences, List<float[]> embeddings) {
		List<String> chunks = new ArrayList<>();
		StringBuilder buffer = new StringBuilder();

		for (int i = 0; i < sentences.size(); i++) {
			String sentence = sentences.get(i);

			if (buffer.isEmpty()) {
				buffer.append(sentence);
			}
			else {
				// Decide whether to extend the current chunk or flush
				boolean similarToNext = false;
				if (i < embeddings.size()) {
					double sim = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));
					similarToNext = sim >= similarityThreshold;
					logger.trace("Sentence {}: cosine similarity to previous = {:.4f}", i, sim);
				}

				boolean wouldExceedMaxSize = buffer.length() + 1 + sentence.length() > maxChunkSize;

				if (similarToNext && !wouldExceedMaxSize) {
					buffer.append(' ').append(sentence);
				}
				else {
					chunks.add(buffer.toString());
					buffer = new StringBuilder(sentence);
				}
			}
		}

		// Flush the last buffer
		if (!buffer.isEmpty()) {
			chunks.add(buffer.toString());
		}

		return chunks;
	}

	/**
	 * Compute the cosine similarity between two float vectors.
	 *
	 * @param a first vector
	 * @param b second vector (must have the same length as {@code a})
	 * @return cosine similarity in {@code [-1, 1]}, or {@code 0} if either vector has
	 * zero magnitude
	 */
	static double cosineSimilarity(float[] a, float[] b) {
		Assert.isTrue(a.length == b.length,
				() -> "Embedding vectors must have the same dimension: %d vs %d".formatted(a.length, b.length));

		double dot = 0.0;
		double normA = 0.0;
		double normB = 0.0;
		for (int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
			normA += (double) a[i] * a[i];
			normB += (double) b[i] * b[i];
		}

		double denom = Math.sqrt(normA) * Math.sqrt(normB);
		if (denom == 0.0) {
			return 0.0;
		}
		return dot / denom;
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	public EmbeddingModel getEmbeddingModel() {
		return embeddingModel;
	}

	public double getSimilarityThreshold() {
		return similarityThreshold;
	}

	public int getMaxChunkSize() {
		return maxChunkSize;
	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Fluent builder for {@link SemanticTextSplitter}.
	 */
	public static final class Builder {

		private EmbeddingModel embeddingModel;

		private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

		private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;

		private Builder() {
		}

		/**
		 * Set the embedding model (required).
		 */
		public Builder embeddingModel(EmbeddingModel embeddingModel) {
			this.embeddingModel = embeddingModel;
			return this;
		}

		/**
		 * Set the cosine-similarity threshold ({@code [0, 1]}, default {@code 0.5}).
		 */
		public Builder similarityThreshold(double threshold) {
			this.similarityThreshold = threshold;
			return this;
		}

		/**
		 * Set the maximum chunk size in characters (default {@code 1000}).
		 */
		public Builder maxChunkSize(int maxChunkSize) {
			this.maxChunkSize = maxChunkSize;
			return this;
		}

		public SemanticTextSplitter build() {
			Assert.notNull(embeddingModel, "embeddingModel must be set");
			return new SemanticTextSplitter(embeddingModel, similarityThreshold, maxChunkSize);
		}

	}

}
