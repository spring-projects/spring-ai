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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HuggingfaceChatOptions}.
 *
 * @author Myeongdeok Kang
 */
class HuggingfaceChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.7)
			.maxTokens(100)
			.topP(0.9)
			.frequencyPenalty(0.5)
			.presencePenalty(0.8)
			.build();

		assertThat(options)
			.extracting("model", "temperature", "maxTokens", "topP", "frequencyPenalty", "presencePenalty")
			.containsExactly("meta-llama/Llama-3.2-3B-Instruct", 0.7, 100, 0.9, 0.5, 0.8);
	}

	@Test
	void testCopy() {
		HuggingfaceChatOptions originalOptions = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		HuggingfaceChatOptions copiedOptions = originalOptions.copy();

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testSetters() {
		HuggingfaceChatOptions options = new HuggingfaceChatOptions();

		options.setModel("test-model");
		options.setTemperature(0.5);
		options.setMaxTokens(50);
		options.setTopP(0.8);
		options.setFrequencyPenalty(0.3);
		options.setPresencePenalty(0.6);

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.5);
		assertThat(options.getMaxTokens()).isEqualTo(50);
		assertThat(options.getTopP()).isEqualTo(0.8);
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.3);
		assertThat(options.getPresencePenalty()).isEqualTo(0.6);
	}

	@Test
	void testDefaultValues() {
		HuggingfaceChatOptions options = new HuggingfaceChatOptions();

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
	}

	@Test
	void testFromOptions() {
		HuggingfaceChatOptions originalOptions = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.7)
			.build();

		HuggingfaceChatOptions copiedOptions = HuggingfaceChatOptions.fromOptions(originalOptions);

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
	}

	@Test
	void testToMap() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.topP(0.9)
			.build();

		Map<String, Object> map = options.toMap();

		assertThat(map).containsEntry("model", "test-model")
			.containsEntry("temperature", 0.7)
			.containsEntry("max_tokens", 100)
			.containsEntry("top_p", 0.9);
	}

	@Test
	void testEqualsAndHashCode() {
		HuggingfaceChatOptions options1 = HuggingfaceChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		HuggingfaceChatOptions options2 = HuggingfaceChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testBuilderWithNullValues() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model(null)
			.temperature(null)
			.maxTokens(null)
			.build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getMaxTokens()).isNull();
	}

	@Test
	void testBuilderChaining() {
		HuggingfaceChatOptions.Builder builder = HuggingfaceChatOptions.builder();

		HuggingfaceChatOptions.Builder result = builder.model("test-model").temperature(0.7).maxTokens(100);

		assertThat(result).isSameAs(builder);
	}

	@Test
	void testCopyChangeIndependence() {
		HuggingfaceChatOptions originalOptions = HuggingfaceChatOptions.builder()
			.model("original-model")
			.temperature(0.5)
			.build();

		HuggingfaceChatOptions copiedOptions = originalOptions.copy();

		// Modify original
		originalOptions.setTemperature(0.9);

		// Copy should retain original values
		assertThat(copiedOptions.getTemperature()).isEqualTo(0.5);
		assertThat(originalOptions.getTemperature()).isEqualTo(0.9);
	}

	@Test
	void testStopSequences() {
		List<String> stopSequences = Arrays.asList("STOP", "END");
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("test-model")
			.stopSequences(stopSequences)
			.build();

		assertThat(options.getStopSequences()).isEqualTo(stopSequences);
	}

	@Test
	void testSeed() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder().model("test-model").seed(12345).build();

		assertThat(options.getSeed()).isEqualTo(12345);
	}

	@Test
	void testResponseFormat() {
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("test-model")
			.responseFormat(responseFormat)
			.build();

		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
	}

	@Test
	void testToolPrompt() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("test-model")
			.toolPrompt("You have access to the following tools:")
			.build();

		assertThat(options.getToolPrompt()).isEqualTo("You have access to the following tools:");
	}

	@Test
	void testLogprobs() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder().model("test-model").logprobs(true).build();

		assertThat(options.getLogprobs()).isTrue();
	}

	@Test
	void testTopLogprobs() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("test-model")
			.logprobs(true)
			.topLogprobs(5)
			.build();

		assertThat(options.getTopLogprobs()).isEqualTo(5);
	}

	@Test
	void testBuilderWithAllNewParameters() {
		List<String> stopSequences = Arrays.asList("STOP", "END");
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");

		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.maxTokens(100)
			.topP(0.9)
			.frequencyPenalty(0.5)
			.presencePenalty(0.8)
			.stopSequences(stopSequences)
			.seed(12345)
			.responseFormat(responseFormat)
			.toolPrompt("You have access to tools:")
			.logprobs(true)
			.topLogprobs(3)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getStopSequences()).isEqualTo(stopSequences);
		assertThat(options.getSeed()).isEqualTo(12345);
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
		assertThat(options.getToolPrompt()).isEqualTo("You have access to tools:");
		assertThat(options.getLogprobs()).isTrue();
		assertThat(options.getTopLogprobs()).isEqualTo(3);
	}

	@Test
	void testFromOptionsWithNewParameters() {
		List<String> stopSequences = Arrays.asList("STOP");
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");

		HuggingfaceChatOptions originalOptions = HuggingfaceChatOptions.builder()
			.model("test-model")
			.stopSequences(stopSequences)
			.seed(999)
			.responseFormat(responseFormat)
			.toolPrompt("Tools:")
			.logprobs(true)
			.topLogprobs(2)
			.build();

		HuggingfaceChatOptions copiedOptions = HuggingfaceChatOptions.fromOptions(originalOptions);

		assertThat(copiedOptions.getStopSequences()).isEqualTo(stopSequences);
		assertThat(copiedOptions.getSeed()).isEqualTo(999);
		assertThat(copiedOptions.getResponseFormat()).isEqualTo(responseFormat);
		assertThat(copiedOptions.getToolPrompt()).isEqualTo("Tools:");
		assertThat(copiedOptions.getLogprobs()).isTrue();
		assertThat(copiedOptions.getTopLogprobs()).isEqualTo(2);
	}

	@Test
	void testToMapWithNewParameters() {
		List<String> stopSequences = Arrays.asList("STOP", "END");
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");

		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("test-model")
			.temperature(0.7)
			.stopSequences(stopSequences)
			.seed(12345)
			.responseFormat(responseFormat)
			.toolPrompt("Tools:")
			.logprobs(true)
			.topLogprobs(3)
			.build();

		Map<String, Object> map = options.toMap();

		assertThat(map).containsEntry("model", "test-model")
			.containsEntry("temperature", 0.7)
			.containsEntry("stop", stopSequences)
			.containsEntry("seed", 12345)
			.containsEntry("response_format", responseFormat)
			.containsEntry("tool_prompt", "Tools:")
			.containsEntry("logprobs", true)
			.containsEntry("top_logprobs", 3);
	}

	@Test
	void testEqualsAndHashCodeWithNewParameters() {
		List<String> stopSequences = Arrays.asList("STOP");
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");

		HuggingfaceChatOptions options1 = HuggingfaceChatOptions.builder()
			.model("test-model")
			.stopSequences(stopSequences)
			.seed(999)
			.responseFormat(responseFormat)
			.logprobs(true)
			.build();

		HuggingfaceChatOptions options2 = HuggingfaceChatOptions.builder()
			.model("test-model")
			.stopSequences(stopSequences)
			.seed(999)
			.responseFormat(responseFormat)
			.logprobs(true)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testSettersForNewParameters() {
		HuggingfaceChatOptions options = new HuggingfaceChatOptions();
		List<String> stopSequences = Arrays.asList("STOP");
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");

		options.setStopSequences(stopSequences);
		options.setSeed(777);
		options.setResponseFormat(responseFormat);
		options.setToolPrompt("Tools available:");
		options.setLogprobs(true);
		options.setTopLogprobs(4);

		assertThat(options.getStopSequences()).isEqualTo(stopSequences);
		assertThat(options.getSeed()).isEqualTo(777);
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
		assertThat(options.getToolPrompt()).isEqualTo("Tools available:");
		assertThat(options.getLogprobs()).isTrue();
		assertThat(options.getTopLogprobs()).isEqualTo(4);
	}

}
