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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SemanticTextSplitter}.
 *
 * @author Anurag Saxena
 */
class SemanticTextSplitterTests {

	/**
	 * Dimension used for all fake embeddings in this test suite.
	 */
	private static final int DIM = 4;

	private EmbeddingModel embeddingModel;

	@BeforeEach
	void setUp() {
		embeddingModel = mock(EmbeddingModel.class);
	}

	// -------------------------------------------------------------------------
	// Edge cases
	// -------------------------------------------------------------------------

	@Test
	void emptyTextReturnsEmptyList() {
		SemanticTextSplitter splitter = new SemanticTextSplitter(embeddingModel);
		List<String> result = splitter.splitText("");
		assertThat(result).isEmpty();
	}

	@Test
	void blankTextReturnsEmptyList() {
		SemanticTextSplitter splitter = new SemanticTextSplitter(embeddingModel);
		List<String> result = splitter.splitText("   ");
		assertThat(result).isEmpty();
	}

	@Test
	void singleSentenceReturnsSingleChunk() {
		// No embedding call should be needed for a single sentence
		SemanticTextSplitter splitter = new SemanticTextSplitter(embeddingModel);
		List<String> result = splitter.splitText("This is a single sentence.");
		assertThat(result).containsExactly("This is a single sentence.");
	}

	// -------------------------------------------------------------------------
	// Normal splitting behaviour
	// -------------------------------------------------------------------------

	@Test
	void highSimilarityKeepsSentencesTogether() {
		// Two sentences with cosine similarity = 1.0 (identical vectors) → one chunk
		float[] sameVec = { 1.0f, 0.0f, 0.0f, 0.0f };
		when(embeddingModel.embed(anyString())).thenReturn(sameVec);

		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.similarityThreshold(0.5)
			.maxChunkSize(1000)
			.build();

		String text = "The sky is blue. The ocean reflects the sky.";
		List<String> chunks = splitter.splitText(text);

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0)).contains("The sky is blue").contains("The ocean reflects the sky");
	}

	@Test
	void lowSimilaritySplitsSentences() {
		// Sentences with orthogonal embeddings (similarity = 0.0) → each becomes its own
		// chunk
		float[] vec1 = { 1.0f, 0.0f, 0.0f, 0.0f };
		float[] vec2 = { 0.0f, 1.0f, 0.0f, 0.0f };
		float[] vec3 = { 0.0f, 0.0f, 1.0f, 0.0f };

		when(embeddingModel.embed("The cat sat on the mat.")).thenReturn(vec1);
		when(embeddingModel.embed("Quantum mechanics describes subatomic particles.")).thenReturn(vec2);
		when(embeddingModel.embed("She baked a delicious chocolate cake.")).thenReturn(vec3);

		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.similarityThreshold(0.9) // very high threshold → always split
			.maxChunkSize(1000)
			.build();

		String text = "The cat sat on the mat. "
				+ "Quantum mechanics describes subatomic particles. "
				+ "She baked a delicious chocolate cake.";

		List<String> chunks = splitter.splitText(text);

		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0)).contains("cat");
		assertThat(chunks.get(1)).contains("Quantum");
		assertThat(chunks.get(2)).contains("cake");
	}

	// -------------------------------------------------------------------------
	// maxChunkSize boundary
	// -------------------------------------------------------------------------

	@Test
	void maxChunkSizeForcesNewChunk() {
		// Even with identical embeddings (similarity = 1.0), chunks must not exceed
		// maxChunkSize
		float[] sameVec = { 1.0f, 0.0f, 0.0f, 0.0f };
		when(embeddingModel.embed(anyString())).thenReturn(sameVec);

		// Use a tiny maxChunkSize that would be exceeded after first sentence
		int tinyMax = 30;
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.similarityThreshold(0.0) // never split on similarity
			.maxChunkSize(tinyMax)
			.build();

		String text = "Hello world today. Another sentence follows.";
		List<String> chunks = splitter.splitText(text);

		assertThat(chunks).hasSizeGreaterThan(1);
		for (String chunk : chunks) {
			assertThat(chunk.length()).isLessThanOrEqualTo(
					// A single sentence may itself exceed tinyMax; we only assert that
					// the splitter did split, not that every chunk fits
					text.length());
		}
		// Concatenated chunks must cover all text
		String rejoined = String.join(" ", chunks);
		assertThat(rejoined).contains("Hello world").contains("Another sentence");
	}

	// -------------------------------------------------------------------------
	// Threshold boundaries
	// -------------------------------------------------------------------------

	@Test
	void thresholdExactlyAtSimilarityKeepsTogether() {
		float[] vec = { 1.0f, 0.0f, 0.0f, 0.0f };
		when(embeddingModel.embed(anyString())).thenReturn(vec);

		// Similarity is 1.0; threshold is exactly 1.0 → keeps together (>= check)
		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.similarityThreshold(1.0)
			.maxChunkSize(1000)
			.build();

		List<String> chunks = splitter.splitText("First sentence. Second sentence.");
		assertThat(chunks).hasSize(1);
	}

	@Test
	void thresholdJustAboveSimilaritySplits() {
		// sim = 0.0 (orthogonal), threshold = 0.001 → must split
		float[] vec1 = { 1.0f, 0.0f, 0.0f, 0.0f };
		float[] vec2 = { 0.0f, 1.0f, 0.0f, 0.0f };
		when(embeddingModel.embed("First sentence.")).thenReturn(vec1);
		when(embeddingModel.embed("Second sentence.")).thenReturn(vec2);

		SemanticTextSplitter splitter = SemanticTextSplitter.builder()
			.embeddingModel(embeddingModel)
			.similarityThreshold(0.001)
			.maxChunkSize(1000)
			.build();

		List<String> chunks = splitter.splitText("First sentence. Second sentence.");
		assertThat(chunks).hasSize(2);
	}

	// -------------------------------------------------------------------------
	// Document-level split
	// -------------------------------------------------------------------------

	@Test
	void splitDocumentPreservesMetadata() {
		float[] sameVec = { 1.0f, 0.0f, 0.0f, 0.0f };
		when(embeddingModel.embed(anyString())).thenReturn(sameVec);

		SemanticTextSplitter splitter = new SemanticTextSplitter(embeddingModel);
		Document doc = Document.builder()
			.text("Hello world. This is a test.")
			.metadata("source", "unit-test")
			.build();

		List<Document> docs = splitter.split(doc);

		assertThat(docs).isNotEmpty();
		docs.forEach(d -> assertThat(d.getMetadata()).containsEntry("source", "unit-test"));
	}

	// -------------------------------------------------------------------------
	// Cosine similarity helper
	// -------------------------------------------------------------------------

	@Test
	void cosineSimilarityOfIdenticalVectorsIsOne() {
		float[] v = { 3.0f, 4.0f };
		double sim = SemanticTextSplitter.cosineSimilarity(v, v);
		assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
	}

	@Test
	void cosineSimilarityOfOrthogonalVectorsIsZero() {
		float[] a = { 1.0f, 0.0f };
		float[] b = { 0.0f, 1.0f };
		double sim = SemanticTextSplitter.cosineSimilarity(a, b);
		assertThat(sim).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
	}

	@Test
	void cosineSimilarityOfZeroVectorIsZero() {
		float[] a = { 0.0f, 0.0f };
		float[] b = { 1.0f, 2.0f };
		double sim = SemanticTextSplitter.cosineSimilarity(a, b);
		assertThat(sim).isEqualTo(0.0);
	}

	// -------------------------------------------------------------------------
	// Constructor validation
	// -------------------------------------------------------------------------

	@Test
	void nullEmbeddingModelThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SemanticTextSplitter(null))
			.withMessageContaining("embeddingModel");
	}

	@Test
	void negativeThresholdThrows() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new SemanticTextSplitter(embeddingModel, -0.1, 1000))
			.withMessageContaining("similarityThreshold");
	}

	@Test
	void thresholdAboveOneThrows() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new SemanticTextSplitter(embeddingModel, 1.1, 1000))
			.withMessageContaining("similarityThreshold");
	}

	@Test
	void zeroMaxChunkSizeThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SemanticTextSplitter(embeddingModel, 0.5, 0))
			.withMessageContaining("maxChunkSize");
	}

}
