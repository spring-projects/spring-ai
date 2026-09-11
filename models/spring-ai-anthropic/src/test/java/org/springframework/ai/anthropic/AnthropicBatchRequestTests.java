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

package org.springframework.ai.anthropic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link AnthropicBatchRequest} correlation-identifier validation. A malformed
 * {@code customId} is rejected client-side rather than costing a round trip, because it
 * is the only handle the caller has to match a result back to its request.
 *
 * @author Ricken Bazolo
 */
class AnthropicBatchRequestTests {

	@Test
	void acceptsValidCustomIds() {
		assertThat(AnthropicBatchRequest.of("invoice_42-A", "hello").customId()).isEqualTo("invoice_42-A");
		assertThat(AnthropicBatchRequest.of("a".repeat(64), "hello").customId()).hasSize(64);
	}

	@Test
	void carriesThePromptAndItsOptions() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model("claude-haiku-4-5").maxTokens(32).build();

		AnthropicBatchRequest request = AnthropicBatchRequest.of("req-1", "hello", options);

		assertThat(request.prompt().getOptions()).isSameAs(options);
		assertThat(request.prompt().getContents()).contains("hello");
	}

	@Test
	void acceptsAnExplicitPrompt() {
		Prompt prompt = new Prompt("hello");

		assertThat(AnthropicBatchRequest.of("req-1", prompt).prompt()).isSameAs(prompt);
	}

	@ParameterizedTest
	@ValueSource(strings = { " ", "with space", "with/slash", "with:colon", "accentué" })
	void rejectsMalformedCustomIds(String customId) {
		assertThatIllegalArgumentException().isThrownBy(() -> AnthropicBatchRequest.of(customId, "hello"));
	}

	@Test
	void rejectsAnEmptyCustomId() {
		assertThatIllegalArgumentException().isThrownBy(() -> AnthropicBatchRequest.of("", "hello"))
			.withMessageContaining("customId must not be empty");
	}

	@Test
	void rejectsACustomIdLongerThanTheApiLimit() {
		assertThatIllegalArgumentException().isThrownBy(() -> AnthropicBatchRequest.of("a".repeat(65), "hello"))
			.withMessageContaining("customId must match");
	}

}
