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

package org.springframework.ai.ollama;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Yokior
 */
@SpringBootTest(classes = OllamaEmbeddingOptionsTestsIT.TestConfiguration.class)
public class OllamaEmbeddingOptionsTestsIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.QWEN3_EMBED_8B.getName();

	@Autowired
	private OllamaEmbeddingModel embeddingModel;

	@Test
	void testDimensionsOption() {
		// Test setting and getting dimensions parameter
		Integer expectedDimensions = 1024;

		OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
			.model(MODEL)
			.dimensions(expectedDimensions)
			.build();

		assertThat(options.getDimensions()).isEqualTo(expectedDimensions);
		assertThat(options.getModel()).isEqualTo(MODEL);
	}

	@Test
	void testDimensionsOptionWithSetter() {
		// Test setting dimensions parameter using setter method
		Integer expectedDimensions = 768;

		OllamaEmbeddingOptions options = new OllamaEmbeddingOptions();
		options.setDimensions(expectedDimensions);
		options.setModel(MODEL);

		assertThat(options.getDimensions()).isEqualTo(expectedDimensions);
		assertThat(options.getModel()).isEqualTo(MODEL);
	}

	@Test
	void testDimensionsOptionInFromOptions() {
		// Test if fromOptions method correctly copies dimensions parameter
		Integer expectedDimensions = 512;

		OllamaEmbeddingOptions originalOptions = OllamaEmbeddingOptions.builder()
			.model(MODEL)
			.dimensions(expectedDimensions)
			.build();

		OllamaEmbeddingOptions copiedOptions = OllamaEmbeddingOptions.fromOptions(originalOptions);

		assertThat(copiedOptions.getDimensions()).isEqualTo(expectedDimensions);
		assertThat(copiedOptions.getModel()).isEqualTo(MODEL);
	}

	@Test
	void testDimensionsOptionInEqualsAndHashCode() {
		// Test the impact of dimensions parameter in equals and hashCode methods
		Integer dimensions1 = 1024;
		Integer dimensions2 = 768;

		OllamaEmbeddingOptions options1 = OllamaEmbeddingOptions.builder().model(MODEL).dimensions(dimensions1).build();

		OllamaEmbeddingOptions options2 = OllamaEmbeddingOptions.builder().model(MODEL).dimensions(dimensions1).build();

		OllamaEmbeddingOptions options3 = OllamaEmbeddingOptions.builder().model(MODEL).dimensions(dimensions2).build();

		// Same dimensions should be equal
		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());

		// Different dimensions should not be equal
		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
	}

	@Test
	void testDimensionsOptionNull() {
		// Test dimensions parameter when it's null
		OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder().model(MODEL).build();

		assertThat(options.getDimensions()).isNull();
	}

	@Test
	void testDimensionsOptionWithToMap() {
		// Test dimensions parameter in toMap method, which validates parameter
		// serialization to API call
		Integer expectedDimensions = 1536;

		OllamaEmbeddingOptions options = OllamaEmbeddingOptions.builder()
			.model(MODEL)
			.dimensions(expectedDimensions)
			.build();

		var optionsMap = options.toMap();

		// Verify dimensions parameter is included in serialized map
		assertThat(optionsMap).containsKey("dimensions");
		assertThat(optionsMap.get("dimensions")).isEqualTo(expectedDimensions);

		// Verify map is not empty, indicating parameters will be passed to API
		assertThat(optionsMap).isNotEmpty();
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "OLLAMA_WITH_REUSE", matches = "true")
	void testDimensionsParameterWithRealEmbedding() {
		// Test actual vector model call to verify dimensions parameter is effectively
		// passed
		String testText = "Yokior";
		Integer customDimensions = 512;

		// Create options with dimensions parameter
		OllamaEmbeddingOptions optionsWithDimensions = OllamaEmbeddingOptions.builder()
			.model(MODEL)
			.dimensions(customDimensions)
			.build();

		// Call embedding model
		EmbeddingRequest request = new EmbeddingRequest(List.of(testText), optionsWithDimensions);
		EmbeddingResponse response = this.embeddingModel.call(request);

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).isNotEmpty();

		// Get actual vector dimensions
		float[] embeddingVector = response.getResults().get(0).getOutput();
		Integer actualDimensions = embeddingVector.length;

		// Verify response basic information
		assertThat(response.getMetadata().getModel()).isEqualTo(MODEL);

		// Verify vector dimensions
		assertThat(actualDimensions).isEqualTo(customDimensions);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "OLLAMA_WITH_REUSE", matches = "true")
	void testDimensionsParameterComparison() {
		// Compare scenarios with and without dimensions parameter
		String testText = "Spring AI is awesome - 2026.01.02";

		// Without dimensions parameter
		OllamaEmbeddingOptions optionsWithoutDimensions = OllamaEmbeddingOptions.builder().model(MODEL).build();

		EmbeddingRequest requestWithoutDimensions = new EmbeddingRequest(List.of(testText), optionsWithoutDimensions);
		EmbeddingResponse responseWithoutDimensions = this.embeddingModel.call(requestWithoutDimensions);

		// With dimensions parameter
		OllamaEmbeddingOptions optionsWithDimensions = OllamaEmbeddingOptions.builder()
			.model(MODEL)
			.dimensions(1024)
			.build();

		EmbeddingRequest requestWithDimensions = new EmbeddingRequest(List.of(testText), optionsWithDimensions);
		EmbeddingResponse responseWithDimensions = this.embeddingModel.call(requestWithDimensions);

		// Verify both responses are valid
		assertThat(responseWithoutDimensions.getResults()).hasSize(1);
		assertThat(responseWithDimensions.getResults()).hasSize(1);

		float[] vectorWithoutDimensions = responseWithoutDimensions.getResults().get(0).getOutput();
		float[] vectorWithDimensions = responseWithDimensions.getResults().get(0).getOutput();

		// Verify vector dimension information
		assertThat(vectorWithoutDimensions.length).isPositive();
		assertThat(vectorWithDimensions.length).isPositive();

		// Vector dimensions should be different
		assertThat(vectorWithoutDimensions.length).isNotEqualTo(vectorWithDimensions.length);

		// qwen3-embedding:8b default dimension is 4096
		assertThat(vectorWithoutDimensions.length).isEqualTo(4096);
		assertThat(vectorWithDimensions.length).isEqualTo(1024);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		public OllamaEmbeddingModel ollamaEmbedding(OllamaApi ollamaApi) {
			return OllamaEmbeddingModel.builder()
				.ollamaApi(ollamaApi)
				.defaultOptions(OllamaEmbeddingOptions.builder().model(MODEL).build())
				.build();
		}

	}

}
