/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.qianfan;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import org.springframework.ai.qianfan.api.QianFanApi;

/**
 * Tests for {@link QianFanChatOptions}.
 */
class QianFanChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		QianFanChatOptions options = QianFanChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(10)
			.presencePenalty(0.5)
			.responseFormat(new QianFanApi.ChatCompletionRequest.ResponseFormat("text"))
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.6)
			.build();

		assertThat(options)
			.extracting("model", "frequencyPenalty", "maxTokens", "presencePenalty", "responseFormat", "stop",
					"temperature", "topP")
			.containsExactly("test-model", 0.5, 10, 0.5, new QianFanApi.ChatCompletionRequest.ResponseFormat("text"),
					List.of("test"), 0.6, 0.6);
	}

	@Test
	void testCopy() {
		QianFanChatOptions original = QianFanChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(10)
			.presencePenalty(0.5)
			.responseFormat(new QianFanApi.ChatCompletionRequest.ResponseFormat("text"))
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.6)
			.build();

		QianFanChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStop()).isNotSameAs(original.getStop());
	}

	@Test
	void testSetters() {
		QianFanChatOptions options = new QianFanChatOptions();
		options.setModel("test-model");
		options.setFrequencyPenalty(0.5);
		options.setMaxTokens(10);
		options.setPresencePenalty(0.5);
		options.setResponseFormat(new QianFanApi.ChatCompletionRequest.ResponseFormat("text"));
		options.setStop(List.of("test"));
		options.setTemperature(0.6);
		options.setTopP(0.6);

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getMaxTokens()).isEqualTo(10);
		assertThat(options.getPresencePenalty()).isEqualTo(0.5);
		assertThat(options.getResponseFormat()).isEqualTo(new QianFanApi.ChatCompletionRequest.ResponseFormat("text"));
		assertThat(options.getStop()).isEqualTo(List.of("test"));
		assertThat(options.getTemperature()).isEqualTo(0.6);
		assertThat(options.getTopP()).isEqualTo(0.6);
	}

	@Test
	void testDefaultValues() {
		QianFanChatOptions options = new QianFanChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
	}

	@Test
	void testSerialization() throws Exception {
		QianFanChatOptions options = QianFanChatOptions.builder().maxTokens(10).build();

		ObjectMapper objectMapper = new ObjectMapper();
		String json = objectMapper.writeValueAsString(options);

		assertThat(json).contains("\"max_output_tokens\":10");
	}

	@Test
	void testDeserialization() throws Exception {
		String json = "{\"max_output_tokens\":10}";

		ObjectMapper objectMapper = new ObjectMapper();
		QianFanChatOptions options = objectMapper.readValue(json, QianFanChatOptions.class);

		assertThat(options.getMaxTokens()).isEqualTo(10);
	}

}
