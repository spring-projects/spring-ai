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
	void whenCompletionTokenDetailsIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300, null);
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getTotalTokens()).isEqualTo(300);
		assertThat(usage.getReasoningTokens()).isEqualTo(0);
	}

	@Test
	void whenReasoningTokensIsNull() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300,
				new OpenAiApi.Usage.CompletionTokenDetails(null));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getReasoningTokens()).isEqualTo(0);
	}

	@Test
	void whenCompletionTokenDetailsIsPresent() {
		OpenAiApi.Usage openAiUsage = new OpenAiApi.Usage(100, 200, 300,
				new OpenAiApi.Usage.CompletionTokenDetails(50));
		OpenAiUsage usage = OpenAiUsage.from(openAiUsage);
		assertThat(usage.getReasoningTokens()).isEqualTo(50);
	}

}
