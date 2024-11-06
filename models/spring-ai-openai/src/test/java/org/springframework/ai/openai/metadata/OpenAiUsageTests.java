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

package org.springframework.ai.openai.metadata;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.api.OpenAiApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiUsage}.
 *
 * @author Thomas Vitale
 */
class OpenAiUsageTests {

	@Test
	void whenPromptTokensIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getPromptTokens()).isEqualTo(200);
	}

	@Test
	void whenPromptTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, null, 100);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getPromptTokens()).isEqualTo(0);
	}

	@Test
	void whenGenerationTokensIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getGenerationTokens()).isEqualTo(100);
	}

	@Test
	void whenGenerationTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(null, 200, 200);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getGenerationTokens()).isEqualTo(0);
	}

	@Test
	void whenTotalTokensIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getTotalTokens()).isEqualTo(300);
	}

	@Test
	void whenTotalTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, null);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getTotalTokens()).isEqualTo(300);
	}

	@Test
	void whenPromptAndCompletionTokensDetailsIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null, null);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getTotalTokens()).isEqualTo(300);
		assertThat(usage.getCachedTokens()).isEqualTo(0);
		assertThat(usage.getReasoningTokens()).isEqualTo(0);
	}

	@Test
	void whenReasoningTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(null, null, null, null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getReasoningTokens()).isEqualTo(0);
	}

	@Test
	void whenCompletionTokenDetailsIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(50, null, null, null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getReasoningTokens()).isEqualTo(50);
	}

	@Test
	void whenAcceptedPredictionTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(null, null, null, null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getAcceptedPredictionTokens()).isEqualTo(0);
	}

	@Test
	void whenAcceptedPredictionTokensIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(null, 75, null, null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getAcceptedPredictionTokens()).isEqualTo(75);
	}

	@Test
	void whenAudioTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(null, null, null, null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getAudioTokens()).isEqualTo(0);
	}

	@Test
	void whenAudioTokensIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(null, null, 125, null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getAudioTokens()).isEqualTo(125);
	}

	@Test
	void whenRejectedPredictionTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(null, null, null, null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getRejectedPredictionTokens()).isEqualTo(0);
	}

	@Test
	void whenRejectedPredictionTokensIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null,
				new OpenAiApi.Usage.CompletionTokenDetails(null, null, null, 25));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getRejectedPredictionTokens()).isEqualTo(25);
	}

	@Test
	void whenCacheTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, new OpenAiApi.Usage.PromptTokensDetails(null),
				null);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getCachedTokens()).isEqualTo(0);
	}

	@Test
	void whenCacheTokensIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, new OpenAiApi.Usage.PromptTokensDetails(15),
				null);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getCachedTokens()).isEqualTo(15);
	}

}
