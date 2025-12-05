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

package org.springframework.ai.huggingface;

import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HuggingfaceEmbeddingModel}. These tests require a valid
 * HuggingFace API key set in the HUGGINGFACE_API_KEY environment variable.
 *
 * @author Myeongdeok Kang
 */
@EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
class HuggingfaceEmbeddingModelIT extends BaseHuggingfaceIT {

	@Autowired
	private HuggingfaceEmbeddingModel embeddingModel;

	@Test
	void defaultEmbedding() {
		assertThat(this.embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(List.of("Hello World"), HuggingfaceEmbeddingOptions.builder().build()));

		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getMetadata()).isNotNull();
	}

	@Test
	void embeddingBatchDocuments() {
		assertThat(this.embeddingModel).isNotNull();
		List<String> texts = List.of("Hello World", "Spring AI is awesome", "Huggingface provides great models");

		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(texts, HuggingfaceEmbeddingOptions.builder().build()));

		assertThat(embeddingResponse.getResults()).hasSize(3);
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).isNotEmpty();

		assertThat(embeddingResponse.getMetadata()).isNotNull();
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo(DEFAULT_EMBEDDING_MODEL);
	}

	@Test
	void embeddingWithDocuments() {
		List<Document> documents = List.of(new Document("Spring Framework is great"),
				new Document("AI is transforming technology"), new Document("Integration tests are important"));

		List<String> texts = documents.stream().map(Document::getText).toList();

		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(texts, HuggingfaceEmbeddingOptions.builder().build()));

		assertThat(embeddingResponse.getResults()).hasSize(3);
		for (int i = 0; i < 3; i++) {
			assertThat(embeddingResponse.getResults().get(i).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(i).getIndex()).isEqualTo(i);
		}
	}

	@Test
	void embeddingDimensions() {
		assertThat(this.embeddingModel).isNotNull();

		// For sentence-transformers/all-MiniLM-L6-v2, the dimension should be 384
		// Note: The dimensions() method returns the model's native dimensions,
		// not a configurable parameter
		Integer dimensions = this.embeddingModel.dimensions();
		assertThat(dimensions).isNotNull();
		assertThat(dimensions).isEqualTo(384);
	}

	@Test
	void embeddingWithCustomModel() {
		HuggingfaceEmbeddingOptions customOptions = HuggingfaceEmbeddingOptions.builder()
			.model("sentence-transformers/all-MiniLM-L6-v2")
			.build();

		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(List.of("Custom model test"), customOptions));

		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("sentence-transformers/all-MiniLM-L6-v2");
	}

	@Test
	void embeddingWithEmptyString() {
		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(List.of(""), HuggingfaceEmbeddingOptions.builder().build()));

		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
	}

	@Test
	void embeddingWithLongText() {
		String longText = "This is a longer text that contains multiple sentences. "
				+ "It is used to test how the embedding model handles longer inputs. "
				+ "The model should be able to process this text and return meaningful embeddings. "
				+ "These embeddings can then be used for various NLP tasks such as similarity search or classification.";

		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(List.of(longText), HuggingfaceEmbeddingOptions.builder().build()));

		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSizeGreaterThan(100);
	}

	@Test
	void embeddingVectorSimilarity() {
		// Test that similar texts produce similar embeddings
		List<String> similarTexts = List.of("The cat sat on the mat", "A cat is sitting on a mat");

		EmbeddingResponse embeddingResponse = this.embeddingModel
			.call(new EmbeddingRequest(similarTexts, HuggingfaceEmbeddingOptions.builder().build()));

		assertThat(embeddingResponse.getResults()).hasSize(2);
		float[] embedding1 = embeddingResponse.getResults().get(0).getOutput();
		float[] embedding2 = embeddingResponse.getResults().get(1).getOutput();

		// Both embeddings should have the same dimensions
		assertThat(embedding1).hasSameSizeAs(embedding2);

		// Calculate cosine similarity (should be high for similar texts)
		double similarity = cosineSimilarity(embedding1, embedding2);
		assertThat(similarity).isGreaterThan(0.7); // Similar texts should have high
													// similarity
	}

	@Test
	void embeddingWithNormalizeOption() {
		// Note: The normalize, prompt_name, truncate, and truncation_direction parameters
		// are part of the HuggingFace Inference API Feature Extraction specification:
		// https://huggingface.co/docs/inference-providers/tasks/feature-extraction
		//
		// This test verifies that:
		// 1. The normalize option can be set and sent to the API (via toMap())
		// 2. The API accepts the parameter without throwing errors
		// 3. The resulting embeddings are normalized (magnitude ≈ 1.0)

		HuggingfaceEmbeddingOptions optionsWithNormalize = HuggingfaceEmbeddingOptions.builder()
			.model("sentence-transformers/all-MiniLM-L6-v2")
			.normalize(true)
			.build();

		// Verify the option is included in the request
		assertThat(optionsWithNormalize.getNormalize()).isTrue();
		assertThat(optionsWithNormalize.toMap()).containsEntry("normalize", true);

		EmbeddingResponse response = this.embeddingModel
			.call(new EmbeddingRequest(List.of("Test normalize option"), optionsWithNormalize));

		assertThat(response.getResults()).hasSize(1);
		float[] embedding = response.getResults().get(0).getOutput();
		assertThat(embedding).isNotEmpty();

		// The standard HuggingFace Inference API normalizes embeddings by default,
		// so the magnitude should be close to 1.0 regardless of the normalize parameter
		double magnitude = calculateMagnitude(embedding);
		assertThat(magnitude).isCloseTo(1.0, Offset.offset(0.01));
	}

	@Test
	void embeddingWithWrongBaseUrl() {
		HuggingfaceApi wrongApi = HuggingfaceApi.builder()
			.baseUrl("https://router.huggingface.co/v1")
			.apiKey(getApiKey())
			.build();

		HuggingfaceEmbeddingModel wrongEmbeddingModel = HuggingfaceEmbeddingModel.builder()
			.huggingfaceApi(wrongApi)
			.defaultOptions(HuggingfaceEmbeddingOptions.builder().model(DEFAULT_EMBEDDING_MODEL).build())
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP)
			.build();

		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.client.HttpClientErrorException.class,
				() -> wrongEmbeddingModel.call(new EmbeddingRequest(List.of("Test with wrong URL"),
						HuggingfaceEmbeddingOptions.builder().model(DEFAULT_EMBEDDING_MODEL).build())));
	}

	@Test
	void embeddingWithCorrectBaseUrl() {
		EmbeddingResponse embeddingResponse = this.embeddingModel.call(new EmbeddingRequest(
				List.of("Verify correct baseURL usage"), HuggingfaceEmbeddingOptions.builder().build()));

		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
	}

	private double calculateMagnitude(float[] vector) {
		double sum = 0.0;
		for (float v : vector) {
			sum += v * v;
		}
		return Math.sqrt(sum);
	}

	private double cosineSimilarity(float[] vectorA, float[] vectorB) {
		double dotProduct = 0.0;
		double normA = 0.0;
		double normB = 0.0;

		for (int i = 0; i < vectorA.length; i++) {
			dotProduct += vectorA[i] * vectorB[i];
			normA += Math.pow(vectorA[i], 2);
			normB += Math.pow(vectorB[i], 2);
		}

		return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
	}

}
