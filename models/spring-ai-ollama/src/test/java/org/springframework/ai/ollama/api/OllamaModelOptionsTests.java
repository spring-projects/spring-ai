/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.ollama.api;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class OllamaModelOptionsTests {

	@Test
	public void testBasicOptions() {
		var options = OllamaOptions.builder().temperature(3.14).topK(30).stop(List.of("a", "b", "c")).build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("temperature", 3.14);
		assertThat(optionsMap).containsEntry("top_k", 30);
		assertThat(optionsMap).containsEntry("stop", List.of("a", "b", "c"));
	}

	@Test
	public void testAllNumericOptions() {
		var options = OllamaOptions.builder()
			.numCtx(2048)
			.numBatch(512)
			.numGPU(1)
			.mainGPU(0)
			.numThread(8)
			.numKeep(5)
			.seed(42)
			.numPredict(100)
			.topK(40)
			.topP(0.9)
			.tfsZ(1.0f)
			.typicalP(1.0f)
			.repeatLastN(64)
			.temperature(0.7)
			.repeatPenalty(1.1)
			.presencePenalty(0.0)
			.frequencyPenalty(0.0)
			.mirostat(2)
			.mirostatTau(5.0f)
			.mirostatEta(0.1f)
			.build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("num_ctx", 2048);
		assertThat(optionsMap).containsEntry("num_batch", 512);
		assertThat(optionsMap).containsEntry("num_gpu", 1);
		assertThat(optionsMap).containsEntry("main_gpu", 0);
		assertThat(optionsMap).containsEntry("num_thread", 8);
		assertThat(optionsMap).containsEntry("num_keep", 5);
		assertThat(optionsMap).containsEntry("seed", 42);
		assertThat(optionsMap).containsEntry("num_predict", 100);
		assertThat(optionsMap).containsEntry("top_k", 40);
		assertThat(optionsMap).containsEntry("top_p", 0.9);
		assertThat(optionsMap).containsEntry("tfs_z", 1.0);
		assertThat(optionsMap).containsEntry("typical_p", 1.0);
		assertThat(optionsMap).containsEntry("repeat_last_n", 64);
		assertThat(optionsMap).containsEntry("temperature", 0.7);
		assertThat(optionsMap).containsEntry("repeat_penalty", 1.1);
		assertThat(optionsMap).containsEntry("presence_penalty", 0.0);
		assertThat(optionsMap).containsEntry("frequency_penalty", 0.0);
		assertThat(optionsMap).containsEntry("mirostat", 2);
		assertThat(optionsMap).containsEntry("mirostat_tau", 5.0);
		assertThat(optionsMap).containsEntry("mirostat_eta", 0.1);
	}

	@Test
	public void testBooleanOptions() {
		var options = OllamaOptions.builder()
			.truncate(true)
			.useNUMA(true)
			.lowVRAM(false)
			.f16KV(true)
			.logitsAll(false)
			.vocabOnly(false)
			.useMMap(true)
			.useMLock(false)
			.penalizeNewline(true)
			.proxyToolCalls(true)
			.build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("truncate", true);
		assertThat(optionsMap).containsEntry("numa", true);
		assertThat(optionsMap).containsEntry("low_vram", false);
		assertThat(optionsMap).containsEntry("f16_kv", true);
		assertThat(optionsMap).containsEntry("logits_all", false);
		assertThat(optionsMap).containsEntry("vocab_only", false);
		assertThat(optionsMap).containsEntry("use_mmap", true);
		assertThat(optionsMap).containsEntry("use_mlock", false);
		assertThat(optionsMap).containsEntry("penalize_newline", true);
	}

	@Test
	public void testModelAndFormat() {
		var options = OllamaOptions.builder().model("llama2").format("json").build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("model", "llama2");
		assertThat(optionsMap).containsEntry("format", "json");
	}

	@Test
	public void testFunctionAndToolOptions() {
		var options = OllamaOptions.builder()
			.function("function1")
			.function("function2")
			.function("function3")
			.toolContext(Map.of("key1", "value1", "key2", "value2"))
			.build();

		// Function-related fields are not included in the map due to @JsonIgnore
		var optionsMap = options.toMap();
		assertThat(optionsMap).doesNotContainKey("functions");
		assertThat(optionsMap).doesNotContainKey("tool_context");

		// But they are accessible through getters
		assertThat(options.getFunctions()).containsExactlyInAnyOrder("function1", "function2", "function3");
		assertThat(options.getToolContext())
			.containsExactlyInAnyOrderEntriesOf(Map.of("key1", "value1", "key2", "value2"));
	}

	@Test
	public void testFunctionOptionsWithMutableSet() {
		Set<String> functionSet = new HashSet<>();
		functionSet.add("function1");
		functionSet.add("function2");

		var options = OllamaOptions.builder().functions(functionSet).function("function3").build();

		assertThat(options.getFunctions()).containsExactlyInAnyOrder("function1", "function2", "function3");
	}

	@Test
	public void testFromOptions() {
		var originalOptions = OllamaOptions.builder()
			.model("llama2")
			.temperature(0.7)
			.topK(40)
			.functions(Set.of("function1"))
			.build();

		var copiedOptions = OllamaOptions.fromOptions(originalOptions);

		// Test the copied options directly rather than through toMap()
		assertThat(copiedOptions.getModel()).isEqualTo("llama2");
		assertThat(copiedOptions.getTemperature()).isEqualTo(0.7);
		assertThat(copiedOptions.getTopK()).isEqualTo(40);
		assertThat(copiedOptions.getFunctions()).containsExactly("function1");
	}

	@Test
	public void testFunctionOptionsNotInMap() {
		var options = OllamaOptions.builder().model("llama2").functions(Set.of("function1")).build();

		var optionsMap = options.toMap();

		// Verify function-related fields are not included in the map due to @JsonIgnore
		assertThat(optionsMap).containsEntry("model", "llama2");
		assertThat(optionsMap).doesNotContainKey("functions");
		assertThat(optionsMap).doesNotContainKey("functionCallbacks");
		assertThat(optionsMap).doesNotContainKey("proxyToolCalls");
		assertThat(optionsMap).doesNotContainKey("toolContext");

		// But verify they are still accessible through getters
		assertThat(options.getFunctions()).containsExactly("function1");
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testDeprecatedMethods() {
		var options = OllamaOptions.builder().model("llama2").temperature(0.7).topK(40).function("function1").build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("model", "llama2");
		assertThat(optionsMap).containsEntry("temperature", 0.7);
		assertThat(optionsMap).containsEntry("top_k", 40);

		// Function is not in map but accessible via getter
		assertThat(options.getFunctions()).containsExactly("function1");
	}

}
