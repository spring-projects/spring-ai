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

package org.springframework.ai.ollama;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonghoon Park
 */
public class OllamaEmbeddingRequestTests {

	private OllamaEmbeddingModel embeddingModel;

	@BeforeEach
	public void setUp() {
		this.embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(OllamaApi.builder().build())
			.defaultOptions(OllamaOptions.builder().model("DEFAULT_MODEL").mainGPU(11).useMMap(true).numGPU(1).build())
			.build();
	}

	@Test
	public void ollamaEmbeddingRequestDefaultOptions() {
		var embeddingRequest = this.embeddingModel.buildEmbeddingRequest(new EmbeddingRequest(List.of("Hello"), null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(ollamaRequest.options().get("num_gpu")).isEqualTo(1);
		assertThat(ollamaRequest.options().get("main_gpu")).isEqualTo(11);
		assertThat(ollamaRequest.options().get("use_mmap")).isEqualTo(true);
		assertThat(ollamaRequest.input()).isEqualTo(List.of("Hello"));
	}

	@Test
	public void ollamaEmbeddingRequestRequestOptions() {
		var promptOptions = OllamaOptions.builder()//
			.model("PROMPT_MODEL")//
			.mainGPU(22)//
			.useMMap(true)//
			.numGPU(2)
			.build();

		var embeddingRequest = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(List.of("Hello"), promptOptions));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.model()).isEqualTo("PROMPT_MODEL");
		assertThat(ollamaRequest.options().get("num_gpu")).isEqualTo(2);
		assertThat(ollamaRequest.options().get("main_gpu")).isEqualTo(22);
		assertThat(ollamaRequest.options().get("use_mmap")).isEqualTo(true);
		assertThat(ollamaRequest.input()).isEqualTo(List.of("Hello"));
	}

	@Test
	public void ollamaEmbeddingRequestWithNegativeKeepAlive() {
		var promptOptions = OllamaOptions.builder().model("PROMPT_MODEL").keepAlive("-1m").build();

		var embeddingRequest = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(List.of("Hello"), promptOptions));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.keepAlive()).isEqualTo(Duration.ofMinutes(-1));
	}

	@Test
	public void ollamaEmbeddingRequestWithEmptyInput() {
		var embeddingRequest = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(Collections.emptyList(), null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.input()).isEmpty();
		assertThat(ollamaRequest.model()).isEqualTo("DEFAULT_MODEL");
	}

	@Test
	public void ollamaEmbeddingRequestWithMultipleInputs() {
		List<String> inputs = Arrays.asList("Hello", "World", "How are you?");
		var embeddingRequest = this.embeddingModel.buildEmbeddingRequest(new EmbeddingRequest(inputs, null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.input()).hasSize(3);
		assertThat(ollamaRequest.input()).containsExactly("Hello", "World", "How are you?");
	}

	@Test
	public void ollamaEmbeddingRequestOptionsOverrideDefaults() {
		var requestOptions = OllamaOptions.builder()
			.model("OVERRIDE_MODEL")
			.mainGPU(99)
			.useMMap(false)
			.numGPU(8)
			.build();

		var embeddingRequest = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(List.of("Override test"), requestOptions));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		// Request options should override defaults
		assertThat(ollamaRequest.model()).isEqualTo("OVERRIDE_MODEL");
		assertThat(ollamaRequest.options().get("num_gpu")).isEqualTo(8);
		assertThat(ollamaRequest.options().get("main_gpu")).isEqualTo(99);
		assertThat(ollamaRequest.options().get("use_mmap")).isEqualTo(false);
	}

	@Test
	public void ollamaEmbeddingRequestWithDifferentKeepAliveFormats() {
		// Test seconds format
		var optionsSeconds = OllamaOptions.builder().keepAlive("30s").build();
		var requestSeconds = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(List.of("Test"), optionsSeconds));
		var ollamaRequestSeconds = this.embeddingModel.ollamaEmbeddingRequest(requestSeconds);
		assertThat(ollamaRequestSeconds.keepAlive()).isEqualTo(Duration.ofSeconds(30));

		// Test hours format
		var optionsHours = OllamaOptions.builder().keepAlive("2h").build();
		var requestHours = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(List.of("Test"), optionsHours));
		var ollamaRequestHours = this.embeddingModel.ollamaEmbeddingRequest(requestHours);
		assertThat(ollamaRequestHours.keepAlive()).isEqualTo(Duration.ofHours(2));
	}

	@Test
	public void ollamaEmbeddingRequestWithMinimalDefaults() {
		// Create model with minimal defaults
		var minimalModel = OllamaEmbeddingModel.builder()
			.ollamaApi(OllamaApi.builder().build())
			.defaultOptions(OllamaOptions.builder().model("MINIMAL_MODEL").build())
			.build();

		var embeddingRequest = minimalModel.buildEmbeddingRequest(new EmbeddingRequest(List.of("Minimal test"), null));
		var ollamaRequest = minimalModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.model()).isEqualTo("MINIMAL_MODEL");
		assertThat(ollamaRequest.input()).isEqualTo(List.of("Minimal test"));
		// Should not have GPU-related options when not set
		assertThat(ollamaRequest.options().get("num_gpu")).isNull();
		assertThat(ollamaRequest.options().get("main_gpu")).isNull();
		assertThat(ollamaRequest.options().get("use_mmap")).isNull();
	}

	@Test
	public void ollamaEmbeddingRequestPreservesInputOrder() {
		List<String> orderedInputs = Arrays.asList("First", "Second", "Third", "Fourth");
		var embeddingRequest = this.embeddingModel.buildEmbeddingRequest(new EmbeddingRequest(orderedInputs, null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.input()).containsExactly("First", "Second", "Third", "Fourth");
	}

	@Test
	public void ollamaEmbeddingRequestWithWhitespaceInputs() {
		List<String> inputs = Arrays.asList("", "   ", "\t\n", "normal text", "  spaced  ");
		var embeddingRequest = this.embeddingModel.buildEmbeddingRequest(new EmbeddingRequest(inputs, null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		// Verify that whitespace inputs are preserved as-is
		assertThat(ollamaRequest.input()).containsExactly("", "   ", "\t\n", "normal text", "  spaced  ");
	}

	@Test
	public void ollamaEmbeddingRequestWithNullInput() {
		// Test behavior when input list contains null values
		List<String> inputsWithNull = Arrays.asList("Hello", null, "World");
		var embeddingRequest = this.embeddingModel.buildEmbeddingRequest(new EmbeddingRequest(inputsWithNull, null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.input()).containsExactly("Hello", null, "World");
		assertThat(ollamaRequest.input()).hasSize(3);
	}

	@Test
	public void ollamaEmbeddingRequestPartialOptionsOverride() {
		// Test that only specified options are overridden, others remain default
		var requestOptions = OllamaOptions.builder()
			.model("PARTIAL_OVERRIDE_MODEL")
			.numGPU(5) // Override only numGPU, leave others as default
			.build();

		var embeddingRequest = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(List.of("Partial override"), requestOptions));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.model()).isEqualTo("PARTIAL_OVERRIDE_MODEL");
		assertThat(ollamaRequest.options().get("num_gpu")).isEqualTo(5);
		assertThat(ollamaRequest.options().get("main_gpu")).isEqualTo(11);
		assertThat(ollamaRequest.options().get("use_mmap")).isEqualTo(true);
	}

	@Test
	public void ollamaEmbeddingRequestWithEmptyStringInput() {
		// Test with list containing only empty string
		var embeddingRequest = this.embeddingModel.buildEmbeddingRequest(new EmbeddingRequest(List.of(""), null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.input()).hasSize(1);
		assertThat(ollamaRequest.input().get(0)).isEmpty();
		assertThat(ollamaRequest.model()).isEqualTo("DEFAULT_MODEL");
	}

}
