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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for SemanticSimilarityFewShotSelector.
 */
public class SemanticSimilaritySelectorTests {

	@Test
	public void testSemanticSelectorSelectsRequestedCount() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> examples = Arrays.asList(
				FewShotExample.builder()
					.id("ex1")
					.input("Spring Framework details")
					.output("Spring is a framework.")
					.build(),
				FewShotExample.builder().id("ex2").input("Java programming").output("Java is a language.").build(),
				FewShotExample.builder().id("ex3").input("REST API design").output("REST is architectural.").build());

		List<FewShotExample> selected = selector.select("What is Spring Framework?", examples, 2);

		assertThat(selected).hasSize(2);
	}

	@Test
	public void testSemanticSelectorRanksBySimilarity() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> examples = Arrays.asList(
				FewShotExample.builder().id("ex1").input("Spring Framework details").output("Similar").build(),
				FewShotExample.builder().id("ex2").input("REST API design").output("Different").build());

		List<FewShotExample> selected = selector.select("What is Spring Framework?", examples, 2);

		// First should be most similar (ex1)
		assertThat(selected.get(0).getId()).isEqualTo("ex1");
		// Second should be less similar (ex2)
		assertThat(selected.get(1).getId()).isEqualTo("ex2");
		// First should have higher relevance score
		assertThat(selected.get(0).getRelevanceScore()).isGreaterThan(selected.get(1).getRelevanceScore());
	}

	@Test
	public void testSemanticSelectorUpdatesRelevanceScores() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> examples = Arrays.asList(FewShotExample.builder()
			.id("ex1")
			.input("Spring Framework details")
			.output("Answer1")
			.relevanceScore(0.0) // Original score ignored
			.build());

		List<FewShotExample> selected = selector.select("What is Spring Framework?", examples, 1);

		// Relevance score should be updated to similarity score (not 0.0)
		assertThat(selected.get(0).getRelevanceScore()).isGreaterThan(0.0);
	}

	@Test
	public void testSemanticSelectorHandlesEmptyExamples() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> selected = selector.select("query", new ArrayList<>(), 3);

		assertThat(selected).isEmpty();
	}

	@Test
	public void testSemanticSelectorValidatesEmbeddingModel() {
		assertThatIllegalArgumentException().isThrownBy(() -> new SemanticSimilarityFewShotSelector(null));
	}

	@Test
	public void testSemanticSelectorValidatesUserQuery() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> examples = Arrays
			.asList(FewShotExample.builder().id("ex1").input("Example").output("Output").build());

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select(null, examples, 1));

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("", examples, 1));
	}

	@Test
	public void testSemanticSelectorValidatesAvailableExamples() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("query", null, 1));
	}

	@Test
	public void testSemanticSelectorValidatesMaxExamples() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> examples = Arrays
			.asList(FewShotExample.builder().id("ex1").input("Example").output("Output").build());

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("query", examples, 0));

		assertThatIllegalArgumentException().isThrownBy(() -> selector.select("query", examples, -1));
	}

	@Test
	public void testSemanticSelectorGetEmbeddingModel() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		SemanticSimilarityFewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		assertThat(selector.getEmbeddingModel()).isSameAs(embeddingModel);
	}

	@Test
	public void testSemanticSelectorLimitsToMaxExamples() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> examples = Arrays.asList(
				FewShotExample.builder().id("ex1").input("Spring Framework details").output("Answer1").build(),
				FewShotExample.builder().id("ex2").input("Java programming").output("Answer2").build(),
				FewShotExample.builder().id("ex3").input("REST API design").output("Answer3").build());

		List<FewShotExample> selected = selector.select("What is Spring Framework?", examples, 1);

		assertThat(selected).hasSize(1);
		assertThat(selected.get(0).getId()).isEqualTo("ex1");
	}

	@Test
	public void testSemanticSelectorPreservesMetadata() {
		EmbeddingModel embeddingModel = new MockEmbeddingModel();
		FewShotSelector selector = new SemanticSimilarityFewShotSelector(embeddingModel);

		List<FewShotExample> examples = Arrays.asList(FewShotExample.builder()
			.id("ex1")
			.input("Spring Framework details")
			.output("Answer")
			.metadata("domain", "technical")
			.metadata("level", "beginner")
			.build());

		List<FewShotExample> selected = selector.select("What is Spring Framework?", examples, 1);

		assertThat(selected.get(0).getMetadata()).containsEntry("domain", "technical")
			.containsEntry("level", "beginner");
	}

	private static class MockEmbeddingModel implements EmbeddingModel {

		private final Map<String, float[]> embeddings;

		MockEmbeddingModel() {
			this.embeddings = new HashMap<>();
			// Predefined embeddings for testing
			this.embeddings.put("What is Spring Framework?", new float[] { 1.0f, 0.0f, 0.0f });
			this.embeddings.put("Spring Framework details", new float[] { 0.95f, 0.1f, 0.0f });
			this.embeddings.put("Java programming", new float[] { 0.5f, 0.5f, 0.0f });
			this.embeddings.put("REST API design", new float[] { 0.0f, 1.0f, 0.0f });
		}

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			// Not implemented for testing
			throw new UnsupportedOperationException("Not implemented for testing");
		}

		@Override
		public float[] embed(String text) {
			return this.embeddings.getOrDefault(text, new float[] { 0.0f, 0.0f, 1.0f });
		}

		@Override
		public float[] embed(Document document) {
			return embed(document.getText());
		}

	}

}
