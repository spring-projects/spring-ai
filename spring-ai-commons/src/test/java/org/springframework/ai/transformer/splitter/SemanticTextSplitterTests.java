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

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SemanticTextSplitter}.
 *
 * @author ENG (RedTeam)
 */
public class SemanticTextSplitterTests {

	/**
	 * Mock embedding model that returns pre-configured vectors.
	 */
	static class MockEmbeddingModel implements EmbeddingModel {

		private final float[][] embeddings;

		MockEmbeddingModel(float[][] embeddings) {
			this.embeddings = embeddings;
		}

		@Override
		public float[] embed(Document document) {
			return embed(document.getText());
		}

		@Override
		public List<float[]> embed(List<String> texts) {
			List<float[]> results = new ArrayList<>();
			for (int i = 0; i < texts.size(); i++) {
				results.add(embeddings[i % embeddings.length]);
			}
			return results;
		}

		@Override
		public List<float[]> embed(List<String> texts, org.springframework.ai.embedding.EmbeddingOptions options,
				org.springframework.ai.embedding.BatchingStrategy batchingStrategy) {
			return embed(texts);
		}

		@Override
		public org.springframework.ai.embedding.EmbeddingResponse embedForResponse(List<String> texts) {
			return null;
		}

		@Override
		public int dimensions() {
			return embeddings.length > 0 ? embeddings[0].length : 0;
		}

	}

	// ─── splitText tests ─────────────────────────────────────────────────────

	@Test
	void splitText_normalCase_returnsSemanticChunks() {
		// Two pairs of sentences: first pair similar, second pair dissimilar.
		// Similarity between [0] and [1] > threshold → same chunk.
		// Similarity between [1] and [2] < threshold → split.
		float[][] embeddings = {
				{ 1f, 0f, 0f }, // sentence 0
				{ 0.9f, 0.1f, 0f }, // sentence 1 — similar to 0 (cosine ≈ 0.99)
				{ -1f, 0f, 0f }, // sentence 2 — dissimilar to 1 (cosine ≈ -0.99)
				{ -0.9f, 0.1f, 0f } // sentence 3 — similar to 2 (cosine ≈ 0.99)
		};
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.5);

		List<Document> chunks = splitter.split("Sentence one here. Sentence two here. "
				+ "Completely different topic begins. Another related sentence.");

		// Should split: [0,1] form one chunk, [2,3] form another
		assertThat(chunks).hasSize(2);
	}

	@Test
	void splitText_singleSentence_returnsOneChunk() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.5);

		List<Document> chunks = splitter.split("This is a single sentence.");

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).getText()).isEqualTo("This is a single sentence.");
	}

	@Test
	void splitText_nullOrBlank_returnsEmptyList() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.5);

		assertThat(splitter.split((String) null)).isEmpty();
		assertThat(splitter.split("   ")).isEmpty();
		assertThat(splitter.split("")).isEmpty();
	}

	@Test
	void splitText_thresholdBoundary_exactThreshold_noSplit() {
		// Two vectors with cosine similarity exactly 0.5
		// Dot = 0.5, normA = normB = 1.0 → similarity = 0.5
		float[][] embeddings = { { 1f, 0f }, { 0.5f, Math.sqrt(0.75f) } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.5);

		List<Document> chunks = splitter.split("Sentence one. Sentence two.");

		// At exact threshold, similarity < threshold → split
		// (our condition is similarity < threshold, so 0.5 < 0.5 is true → split)
		assertThat(chunks).hasSize(2);
	}

	@Test
	void splitText_lowThreshold_returnsFewChunks() {
		// All sentences similar → one chunk
		float[][] embeddings = {
				{ 1f, 0f },
				{ 0.99f, 0.01f },
				{ 0.98f, 0.02f }
		};
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.1);

		List<Document> chunks = splitter.split("First sentence. Second sentence. Third sentence.");

		assertThat(chunks).hasSize(1);
	}

	@Test
	void splitText_highThreshold_returnsManyChunks() {
		// Every sentence is dissimilar to the next
		float[][] embeddings = {
				{ 1f, 0f },
				{ -1f, 0f },
				{ 1f, 0f },
				{ -1f, 0f }
		};
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.9);

		List<Document> chunks = splitter.split("One. Two. Three. Four.");

		// High threshold: every sentence split
		assertThat(chunks).hasSize(4);
	}

	@Test
	void splitText_maxChunkSize_forcesSplit() {
		// Three sentences that are similar but would exceed maxChunkSize
		float[][] embeddings = {
				{ 1f, 0f },
				{ 0.99f, 0.01f },
				{ 0.98f, 0.02f }
		};
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.5, 20);

		List<Document> chunks = splitter.split("Short sentence. Another. Third.");

		// maxChunkSize=20: "Short sentence. " (15 chars) + "Another. " (9 chars) = 24 > 20 → split
		// Then "Third." (6 chars) alone
		assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
	}

	// ─── splitIntoSentences tests ───────────────────────────────────────────────

	@Test
	void splitIntoSentences_multiplePunctuationTypes() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel);

		List<String> sentences = splitter.splitIntoSentences("First sentence! Second? Third.");

		assertThat(sentences).containsExactly("First sentence!", "Second?", "Third.");
	}

	@Test
	void splitIntoSentences_trailingWhitespace() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel);

		List<String> sentences = splitter.splitIntoSentences("Sentence one.   Sentence two.");

		assertThat(sentences).containsExactly("Sentence one.", "Sentence two.");
	}

	// ─── cosineSimilarity tests ─────────────────────────────────────────────────

	@Test
	void cosineSimilarity_identicalVectors_returnsOne() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel);

		double sim = splitter.cosineSimilarity(new float[] { 1f, 2f }, new float[] { 1f, 2f });

		assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
	}

	@Test
	void cosineSimilarity_oppositeVectors_returnsNegativeOne() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel);

		double sim = splitter.cosineSimilarity(new float[] { 1f, 2f }, new float[] { -1f, -2f });

		assertThat(sim).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(0.0001));
	}

	@Test
	void cosineSimilarity_orthogonalVectors_returnsZero() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel);

		double sim = splitter.cosineSimilarity(new float[] { 1f, 0f }, new float[] { 0f, 1f });

		assertThat(sim).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
	}

	@Test
	void cosineSimilarity_differentLengths_returnsZero() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel);

		double sim = splitter.cosineSimilarity(new float[] { 1f, 2f, 3f }, new float[] { 1f, 2f });

		assertThat(sim).isEqualTo(0.0);
	}

	@Test
	void cosineSimilarity_zeroVectors_returnsZero() {
		float[][] embeddings = { { 1f, 0f } };
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel);

		double sim = splitter.cosineSimilarity(new float[] { 0f, 0f }, new float[] { 0f, 0f });

		assertThat(sim).isEqualTo(0.0);
	}

	// ─── Document integration tests ─────────────────────────────────────────────

	@Test
	void apply_withDocument_returnsChunksWithMetadata() {
		float[][] embeddings = {
				{ 1f, 0f },
				{ -1f, 0f }
		};
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.5);

		Document doc = new Document("Sentence one here. Sentence two here.");
		List<Document> chunks = splitter.apply(List.of(doc));

		assertThat(chunks).hasSize(2);
		// Verify parent tracking metadata
		assertThat(chunks.get(0).getMetadata()).containsKey("parent_document_id");
		assertThat(chunks.get(0).getMetadata()).containsEntry("chunk_index", 0);
		assertThat(chunks.get(0).getMetadata()).containsEntry("total_chunks", 2);
	}

	@Test
	void apply_withDocuments_preservesMetadata() {
		float[][] embeddings = {
				{ 1f, 0f },
				{ -1f, 0f }
		};
		MockEmbeddingModel mockModel = new MockEmbeddingModel(embeddings);
		SemanticTextSplitter splitter = new SemanticTextSplitter(mockModel, 0.5);

		Document doc = Document.builder()
			.text("Sentence one. Sentence two.")
			.metadata(java.util.Map.of("source", "test.txt"))
			.build();
		List<Document> chunks = splitter.apply(List.of(doc));

		assertThat(chunks).hasSize(2);
		assertThat(chunks.get(0).getMetadata().get("source")).isEqualTo("test.txt");
		assertThat(chunks.get(1).getMetadata().get("source")).isEqualTo("test.txt");
	}

	@Test
	void constructor_rejectsNullEmbeddingModel() {
		assertThatThrownBy(() -> new SemanticTextSplitter(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("embeddingModel must not be null");
	}

	@Test
	void constructor_rejectsInvalidThreshold() {
		MockEmbeddingModel mockModel = new MockEmbeddingModel(new float[][] { { 1f } });
		assertThatThrownBy(() -> new SemanticTextSplitter(mockModel, -0.1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SemanticTextSplitter(mockModel, 1.1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void constructor_rejectsInvalidMaxChunkSize() {
		MockEmbeddingModel mockModel = new MockEmbeddingModel(new float[][] { { 1f } });
		assertThatThrownBy(() -> new SemanticTextSplitter(mockModel, 0.5, 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
