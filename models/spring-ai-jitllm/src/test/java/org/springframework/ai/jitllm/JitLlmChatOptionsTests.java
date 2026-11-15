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

package org.springframework.ai.jitllm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JitLlmChatOptions}.
 *
 * @author Michalis Papadimitriou
 */
class JitLlmChatOptionsTests {

	@Test
	void mutateCopiesEveryOption() {
		JitLlmChatOptions options = JitLlmChatOptions.builder()
			.model("m")
			.maxTokens(10)
			.temperature(0.2)
			.topP(0.8)
			.topK(5)
			.seed(3L)
			.stopSequences(List.of("END"))
			.frequencyPenalty(0.1)
			.presencePenalty(0.3)
			.toolContext(Map.of("k", "v"))
			.build();

		assertThat(options.mutate().build()).isEqualTo(options).hasSameHashCodeAs(options);
	}

	@Test
	void combineWithOverridesOnlyWhatTheOtherSets() {
		JitLlmChatOptions defaults = JitLlmChatOptions.builder().temperature(0.1).seed(1L).maxTokens(64).build();

		JitLlmChatOptions merged = defaults.mutate()
			.combineWith(JitLlmChatOptions.builder().seed(2L).temperature(0.7))
			.build();

		assertThat(merged.getTemperature()).isEqualTo(0.7);
		assertThat(merged.getSeed()).isEqualTo(2L);
		assertThat(merged.getMaxTokens()).isEqualTo(64);
	}

	@Test
	void combineWithGenericChatOptionsKeepsTheSeed() {
		JitLlmChatOptions merged = JitLlmChatOptions.builder()
			.seed(9L)
			.combineWith(ChatOptions.builder().temperature(0.4))
			.build();

		assertThat(merged.getSeed()).isEqualTo(9L);
		assertThat(merged.getTemperature()).isEqualTo(0.4);
	}

}
