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

package org.springframework.ai.moonshot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MoonshotChatOptions}.
 *
 * @author Alexandros Pappas
 */
class MoonshotChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		MoonshotChatOptions options = MoonshotChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(10)
			.N(1)
			.presencePenalty(0.5)
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.6)
			.toolChoice("test")
			.proxyToolCalls(true)
			.toolContext(Map.of("key1", "value1"))
			.user("test-user")
			.build();

		assertThat(options)
			.extracting("model", "frequencyPenalty", "maxTokens", "N", "presencePenalty", "stop", "temperature", "topP",
					"toolChoice", "proxyToolCalls", "toolContext", "user")
			.containsExactly("test-model", 0.5, 10, 1, 0.5, List.of("test"), 0.6, 0.6, "test", true,
					Map.of("key1", "value1"), "test-user");
	}

	@Test
	void testCopy() {
		MoonshotChatOptions original = MoonshotChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(10)
			.N(1)
			.presencePenalty(0.5)
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.6)
			.toolChoice("test")
			.proxyToolCalls(true)
			.toolContext(Map.of("key1", "value1"))
			.user("test-user")
			.build();

		MoonshotChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStop()).isNotSameAs(original.getStop());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());
	}

	@Test
	void testSetters() {
		MoonshotChatOptions options = new MoonshotChatOptions();
		options.setModel("test-model");
		options.setFrequencyPenalty(0.5);
		options.setMaxTokens(10);
		options.setN(1);
		options.setPresencePenalty(0.5);
		options.setUser("test-user");
		options.setStop(List.of("test"));
		options.setTemperature(0.6);
		options.setTopP(0.6);
		options.setProxyToolCalls(true);
		options.setToolContext(Map.of("key1", "value1"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getMaxTokens()).isEqualTo(10);
		assertThat(options.getN()).isEqualTo(1);
		assertThat(options.getPresencePenalty()).isEqualTo(0.5);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getStopSequences()).isEqualTo(List.of("test"));
		assertThat(options.getTemperature()).isEqualTo(0.6);
		assertThat(options.getTopP()).isEqualTo(0.6);
		assertThat(options.getProxyToolCalls()).isTrue();
		assertThat(options.getToolContext()).isEqualTo(Map.of("key1", "value1"));
	}

	@Test
	void testDefaultValues() {
		MoonshotChatOptions options = new MoonshotChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getN()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getUser()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getProxyToolCalls()).isNull();
		assertThat(options.getToolContext()).isEqualTo(new java.util.HashMap<>());
	}

}
