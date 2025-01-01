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

package org.springframework.ai.watsonx.api;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.springframework.ai.watsonx.WatsonxAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 * @author Alexandros Pappas
 */
public class WatsonxAiChatOptionTest {

	@Test
	public void testOptions() {
		WatsonxAiChatOptions options = WatsonxAiChatOptions.builder()
			.decodingMethod("sample")
			.temperature(1.2)
			.topK(20)
			.topP(0.5)
			.maxNewTokens(100)
			.minNewTokens(20)
			.stopSequences(List.of("\n\n\n"))
			.repetitionPenalty(1.1)
			.randomSeed(4)
			.build();

		var optionsMap = options.toMap();

		assertThat(optionsMap).containsEntry("decoding_method", "sample");
		assertThat(optionsMap).containsEntry("temperature", 1.2);
		assertThat(optionsMap).containsEntry("top_k", 20);
		assertThat(optionsMap).containsEntry("top_p", 0.5);
		assertThat(optionsMap).containsEntry("max_new_tokens", 100);
		assertThat(optionsMap).containsEntry("min_new_tokens", 20);
		assertThat(optionsMap).containsEntry("stop_sequences", List.of("\n\n\n"));
		assertThat(optionsMap).containsEntry("repetition_penalty", 1.1);
		assertThat(optionsMap).containsEntry("random_seed", 4);
	}

	@Test
	public void testOptionsWithAdditionalParamsOneByOne() {
		WatsonxAiChatOptions options = WatsonxAiChatOptions.builder()
			.decodingMethod("sample")
			.temperature(1.2)
			.topK(20)
			.topP(0.5)
			.maxNewTokens(100)
			.minNewTokens(20)
			.stopSequences(List.of("\n\n\n"))
			.repetitionPenalty(1.1)
			.randomSeed(4)
			.additionalProperty("HAP", true)
			.additionalProperty("typicalP", 0.5f)
			.build();

		var optionsMap = options.toMap();

		assertThat(optionsMap).containsEntry("decoding_method", "sample");
		assertThat(optionsMap).containsEntry("temperature", 1.2);
		assertThat(optionsMap).containsEntry("top_k", 20);
		assertThat(optionsMap).containsEntry("top_p", 0.5);
		assertThat(optionsMap).containsEntry("max_new_tokens", 100);
		assertThat(optionsMap).containsEntry("min_new_tokens", 20);
		assertThat(optionsMap).containsEntry("stop_sequences", List.of("\n\n\n"));
		assertThat(optionsMap).containsEntry("repetition_penalty", 1.1);
		assertThat(optionsMap).containsEntry("random_seed", 4);
		assertThat(optionsMap).containsEntry("hap", true);
		assertThat(optionsMap).containsEntry("typical_p", 0.5);
	}

	@Test
	public void testOptionsWithAdditionalParamsMap() {
		WatsonxAiChatOptions options = WatsonxAiChatOptions.builder()
			.decodingMethod("sample")
			.temperature(1.2)
			.topK(20)
			.topP(0.5)
			.maxNewTokens(100)
			.minNewTokens(20)
			.stopSequences(List.of("\n\n\n"))
			.repetitionPenalty(1.1)
			.randomSeed(4)
			.additionalProperties(Map.of("HAP", true, "typicalP", 0.5f, "test_value", "test"))
			.build();

		var optionsMap = options.toMap();

		assertThat(optionsMap).containsEntry("decoding_method", "sample");
		assertThat(optionsMap).containsEntry("temperature", 1.2);
		assertThat(optionsMap).containsEntry("top_k", 20);
		assertThat(optionsMap).containsEntry("top_p", 0.5);
		assertThat(optionsMap).containsEntry("max_new_tokens", 100);
		assertThat(optionsMap).containsEntry("min_new_tokens", 20);
		assertThat(optionsMap).containsEntry("stop_sequences", List.of("\n\n\n"));
		assertThat(optionsMap).containsEntry("repetition_penalty", 1.1);
		assertThat(optionsMap).containsEntry("random_seed", 4);
		assertThat(optionsMap).containsEntry("hap", true);
		assertThat(optionsMap).containsEntry("typical_p", 0.5);
		assertThat(optionsMap).containsEntry("test_value", "test");
	}

	@Test
	public void testFilterOut() {
		WatsonxAiChatOptions options = WatsonxAiChatOptions.builder().model("google/flan-ul2").build();
		var mappedOptions = WatsonxAiChatOptions.filterNonSupportedFields(options.toMap());
		assertThat(mappedOptions).doesNotContainEntry("model", "google/flan-ul2");
	}

}
