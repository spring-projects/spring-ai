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

package org.springframework.ai.gpullama3;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuLlama3ChatOptionsTests {

	@Test
	void builderSetsAllSupportedFields() {
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.model("llama")
			.onGpu(true)
			.contextLength(2048)
			.maxTokens(512)
			.temperature(0.2)
			.topP(0.9)
			.seed(42L)
			.stopSequences(List.of("stop"))
			.topK(40)
			.frequencyPenalty(0.1)
			.presencePenalty(0.2)
			.build();

		assertThat(options.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
		assertThat(options.getModel()).isEqualTo("llama");
		assertThat(options.getOnGpu()).isTrue();
		assertThat(options.getContextLength()).isEqualTo(2048);
		assertThat(options.getMaxTokens()).isEqualTo(512);
		assertThat(options.getTemperature()).isEqualTo(0.2);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getSeed()).isEqualTo(42L);
		assertThat(options.getStopSequences()).containsExactly("stop");
		assertThat(options.getTopK()).isEqualTo(40);
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.1);
		assertThat(options.getPresencePenalty()).isEqualTo(0.2);
	}

	@Test
	void copyReturnsIndependentOptionsInstance() {
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.model("llama")
			.stopSequences(List.of("stop"))
			.build();

		GpuLlama3ChatOptions copy = options.copy();

		assertThat(copy).isNotSameAs(options);
		assertThat(copy.getModelPath()).isEqualTo(options.getModelPath());
		assertThat(copy.getModel()).isEqualTo(options.getModel());
		List<String> stopSequences = Objects.requireNonNull(copy.getStopSequences());
		assertThat(stopSequences).containsExactly("stop");
		assertThatThrownBy(() -> stopSequences.add("other")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void mutateKeepsExistingValuesAndAllowsOverrides() {
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.model("llama")
			.contextLength(2048)
			.maxTokens(512)
			.build();

		GpuLlama3ChatOptions mutated = options.mutate().maxTokens(256).build();

		assertThat(mutated.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
		assertThat(mutated.getModel()).isEqualTo("llama");
		assertThat(mutated.getContextLength()).isEqualTo(2048);
		assertThat(mutated.getMaxTokens()).isEqualTo(256);
		assertThat(options.getMaxTokens()).isEqualTo(512);
	}

	@Test
	void mergeRuntimeOptionsOverridesPortableRequestOptions() {
		GpuLlama3ChatOptions defaults = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.model("llama")
			.onGpu(false)
			.contextLength(2048)
			.maxTokens(512)
			.temperature(0.1)
			.topP(1.0)
			.seed(12345L)
			.build();

		ChatOptions runtime = ChatOptions.builder()
			.model("runtime-model")
			.maxTokens(128)
			.temperature(0.8)
			.topP(0.7)
			.build();

		GpuLlama3ChatOptions merged = defaults.mergeWithRuntimeOptions(runtime);

		assertThat(merged.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
		assertThat(merged.getModel()).isEqualTo("llama");
		assertThat(merged.getOnGpu()).isFalse();
		assertThat(merged.getContextLength()).isEqualTo(2048);
		assertThat(merged.getMaxTokens()).isEqualTo(128);
		assertThat(merged.getTemperature()).isEqualTo(0.8);
		assertThat(merged.getTopP()).isEqualTo(0.7);
		assertThat(merged.getSeed()).isEqualTo(12345L);
	}

	@Test
	void mergeRuntimeOptionsRejectsLoadTimeOptionChanges() {
		GpuLlama3ChatOptions defaults = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(false)
			.contextLength(2048)
			.build();

		GpuLlama3ChatOptions runtime = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/other.gguf"))
			.onGpu(false)
			.contextLength(2048)
			.build();

		assertThatThrownBy(() -> defaults.mergeWithRuntimeOptions(runtime)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("modelPath");
	}

	@Test
	void mergeRuntimeOptionsRejectsOnGpuChanges() {
		GpuLlama3ChatOptions defaults = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(false)
			.contextLength(2048)
			.build();

		GpuLlama3ChatOptions runtime = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(true)
			.contextLength(2048)
			.build();

		assertThatThrownBy(() -> defaults.mergeWithRuntimeOptions(runtime)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("onGpu");
	}

	@Test
	void mergeRuntimeOptionsRejectsContextLengthChanges() {
		GpuLlama3ChatOptions defaults = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(false)
			.contextLength(2048)
			.build();

		GpuLlama3ChatOptions runtime = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(false)
			.contextLength(4096)
			.build();

		assertThatThrownBy(() -> defaults.mergeWithRuntimeOptions(runtime)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("contextLength");
	}

	@Test
	void mergeRuntimeOptionsAllowsEqualLoadTimeOptionsAndOverridesSeed() {
		GpuLlama3ChatOptions defaults = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(false)
			.contextLength(2048)
			.seed(1L)
			.build();

		GpuLlama3ChatOptions runtime = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.onGpu(false)
			.contextLength(2048)
			.seed(9_000_000_000L)
			.build();

		GpuLlama3ChatOptions merged = defaults.mergeWithRuntimeOptions(runtime);

		assertThat(merged.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
		assertThat(merged.getOnGpu()).isFalse();
		assertThat(merged.getContextLength()).isEqualTo(2048);
		assertThat(merged.getSeed()).isEqualTo(9_000_000_000L);
	}

	@Test
	void mergeNullRuntimeOptionsReturnsSameInstance() {
		GpuLlama3ChatOptions defaults = GpuLlama3ChatOptions.builder().modelPath(Path.of("/models/llama.gguf")).build();

		assertThat(defaults.mergeWithRuntimeOptions(null)).isSameAs(defaults);
	}

	@Test
	void supportsValueEqualityAndDebugString() {
		GpuLlama3ChatOptions options = GpuLlama3ChatOptions.builder()
			.modelPath(Path.of("/models/llama.gguf"))
			.model("llama")
			.onGpu(true)
			.contextLength(2048)
			.maxTokens(512)
			.seed(9_000_000_000L)
			.build();
		GpuLlama3ChatOptions same = options.copy();

		assertThat(same).isEqualTo(options).hasSameHashCodeAs(options);
		assertThat(options.toString()).contains("GpuLlama3ChatOptions")
			.contains("modelPath=/models/llama.gguf")
			.contains("seed=9000000000");
	}

}
