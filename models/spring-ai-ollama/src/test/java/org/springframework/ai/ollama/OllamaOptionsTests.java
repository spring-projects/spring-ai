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

package org.springframework.ai.ollama;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying {@link OllamaOptions} builder, conversions, and model binding.
 *
 * @author Raphael Zanarelli
 */
@SuppressWarnings("deprecation")
class OllamaOptionsTests {

	@Test
	void testOllamaOptionsBuilderAndGetters() {
		OllamaOptions options = OllamaOptions.builder()
			.model("mistral")
			.temperature(0.7)
			.topK(40)
			.topP(0.9)
			.numPredict(100)
			.dimensions(768)
			.keepAlive("5m")
			.truncate(true)
			.useNUMA(true)
			.numCtx(4096)
			.build();

		assertThat(options.getModel()).isEqualTo("mistral");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopK()).isEqualTo(40);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getDimensions()).isEqualTo(768);
		assertThat(options.getKeepAlive()).isEqualTo("5m");
		assertThat(options.getTruncate()).isTrue();
		assertThat(options.getUseNUMA()).isTrue();
		assertThat(options.getNumCtx()).isEqualTo(4096);
	}

	@Test
	void testToEmbeddingOptions() {
		OllamaOptions options = OllamaOptions.builder()
			.model("nomic-embed-text")
			.dimensions(768)
			.keepAlive("10m")
			.truncate(true)
			.useNUMA(true)
			.numCtx(2048)
			.numBatch(512)
			.numGPU(1)
			.mainGPU(0)
			.lowVRAM(false)
			.f16KV(true)
			.logitsAll(false)
			.vocabOnly(false)
			.useMMap(true)
			.useMLock(false)
			.numThread(8)
			.build();

		OllamaEmbeddingOptions embeddingOptions = options.toEmbeddingOptions();
		assertThat(embeddingOptions.getModel()).isEqualTo("nomic-embed-text");
		assertThat(embeddingOptions.getDimensions()).isEqualTo(768);
		assertThat(embeddingOptions.getKeepAlive()).isEqualTo("10m");
		assertThat(embeddingOptions.getTruncate()).isTrue();
		assertThat(embeddingOptions.getUseNUMA()).isTrue();
		assertThat(embeddingOptions.getNumCtx()).isEqualTo(2048);
		assertThat(embeddingOptions.getNumBatch()).isEqualTo(512);
		assertThat(embeddingOptions.getNumGPU()).isEqualTo(1);
		assertThat(embeddingOptions.getMainGPU()).isEqualTo(0);
		assertThat(embeddingOptions.getLowVRAM()).isFalse();
		assertThat(embeddingOptions.getF16KV()).isTrue();
		assertThat(embeddingOptions.getLogitsAll()).isFalse();
		assertThat(embeddingOptions.getVocabOnly()).isFalse();
		assertThat(embeddingOptions.getUseMMap()).isTrue();
		assertThat(embeddingOptions.getUseMLock()).isFalse();
		assertThat(embeddingOptions.getNumThread()).isEqualTo(8);
	}

	@Test
	void testToMapIncludesDimensions() {
		OllamaOptions options = OllamaOptions.builder().model("mistral").dimensions(512).temperature(0.5).build();

		Map<String, Object> map = options.toMap();
		assertThat(map).containsEntry("model", "mistral");
		assertThat(map).containsEntry("dimensions", 512);
		assertThat(map).containsEntry("temperature", 0.5);
	}

	@Test
	void testMutatePreservesDimensions() {
		OllamaOptions original = OllamaOptions.builder().model("llama3").dimensions(1024).temperature(0.8).build();

		OllamaOptions mutated = original.mutate().temperature(0.2).build();
		assertThat(mutated.getModel()).isEqualTo("llama3");
		assertThat(mutated.getDimensions()).isEqualTo(1024);
		assertThat(mutated.getTemperature()).isEqualTo(0.2);
	}

	@Test
	void testOllamaChatModelAcceptsLegacyOllamaOptions() {
		OllamaOptions options = OllamaOptions.builder().model("mistral").temperature(0.6).topK(50).build();

		OllamaChatModel chatModel = OllamaChatModel.builder()
			.ollamaApi(OllamaApi.builder().build())
			.options(options)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		Prompt prompt = new Prompt("test", options);
		OllamaApi.ChatRequest request = chatModel.ollamaChatRequest(prompt, false);
		assertThat(request.model()).isEqualTo("mistral");
		assertThat(request.options()).containsEntry("temperature", 0.6);
		assertThat(request.options()).containsEntry("top_k", 50);
	}

	@Test
	void testOllamaEmbeddingModelAcceptsLegacyOllamaOptions() {
		OllamaOptions options = OllamaOptions.builder()
			.model("nomic-embed-text")
			.dimensions(512)
			.keepAlive("2m")
			.build();

		OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
			.ollamaApi(OllamaApi.builder().build())
			.options(options)
			.build();

		EmbeddingRequest embeddingRequest = new EmbeddingRequest(List.of("test message"), options);
		OllamaApi.EmbeddingsRequest request = embeddingModel
			.ollamaEmbeddingRequest(embeddingModel.buildEmbeddingRequest(embeddingRequest));

		assertThat(request.model()).isEqualTo("nomic-embed-text");
		assertThat(request.dimensions()).isEqualTo(512);
		assertThat(request.keepAlive()).isEqualTo("2m");
	}

}
