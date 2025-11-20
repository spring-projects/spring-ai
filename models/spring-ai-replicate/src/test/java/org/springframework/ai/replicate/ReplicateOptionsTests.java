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

package org.springframework.ai.replicate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReplicateOptions}.
 *
 * @author Rene Maierhofer
 */
class ReplicateOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		Map<String, Object> inputParams = new HashMap<>();
		inputParams.put("temperature", 0.7);
		inputParams.put("max_tokens", 100);

		List<String> webhookEvents = Arrays.asList("start", "completed");

		ReplicateOptions options = ReplicateOptions.builder()
			.model("meta/llama-3-8b-instruct")
			.version("1234abcd")
			.withParameters(inputParams)
			.webhook("https://example.com/webhook")
			.webhookEventsFilter(webhookEvents)
			.build();

		assertThat(options).extracting("model", "version", "webhook")
			.containsExactly("meta/llama-3-8b-instruct", "1234abcd", "https://example.com/webhook");

		assertThat(options.getInput()).containsEntry("temperature", 0.7).containsEntry("max_tokens", 100);

		assertThat(options.getWebhookEventsFilter()).containsExactly("start", "completed");
	}

	@Test
	void testWithParameter() {
		ReplicateOptions options = ReplicateOptions.builder().model("test-model").build();

		options.withParameter("temperature", 0.8);
		options.withParameter("max_tokens", 200);

		assertThat(options.getInput()).hasSize(2).containsEntry("temperature", 0.8).containsEntry("max_tokens", 200);
	}

	@Test
	void testWithParametersMap() {
		Map<String, Object> params = new HashMap<>();
		params.put("param1", "value1");
		params.put("param2", 42);

		ReplicateOptions options = ReplicateOptions.builder().model("test-model").build();

		options.withParameters(params);

		assertThat(options.getInput()).hasSize(2).containsEntry("param1", "value1").containsEntry("param2", 42);
	}

	@Test
	void testFromOptions() {
		Map<String, Object> inputParams = new HashMap<>();
		inputParams.put("temperature", 0.7);

		List<String> webhookEvents = Arrays.asList("start", "completed");

		ReplicateOptions original = ReplicateOptions.builder()
			.model("meta/llama-3-8b-instruct")
			.version("1234abcd")
			.withParameters(inputParams)
			.webhook("https://example.com/webhook")
			.webhookEventsFilter(webhookEvents)
			.build();

		ReplicateOptions copy = ReplicateOptions.fromOptions(original);

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getVersion()).isEqualTo(original.getVersion());
		assertThat(copy.getWebhook()).isEqualTo(original.getWebhook());
		assertThat(copy.getWebhookEventsFilter()).isEqualTo(original.getWebhookEventsFilter());
		assertThat(copy.getInput()).isNotSameAs(original.getInput()).isEqualTo(original.getInput());
	}

	@Test
	void testSetInputConvertsStringValues() {
		ReplicateOptions options = new ReplicateOptions();

		Map<String, Object> input = new HashMap<>();
		input.put("temperature", "0.7");
		input.put("maxTokens", "100");
		input.put("enabled", "true");

		options.setInput(input);

		assertThat(options.getInput().get("temperature")).isInstanceOf(Double.class).isEqualTo(0.7);
		assertThat(options.getInput().get("maxTokens")).isInstanceOf(Integer.class).isEqualTo(100);
		assertThat(options.getInput().get("enabled")).isInstanceOf(Boolean.class).isEqualTo(true);
	}

}
