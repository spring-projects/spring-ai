package org.springframework.ai.anthropic.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

class AnthropicUsageAccessorTests {

	@Test
	@DisplayName("Should throw exception when usage map is null")
	void constructorShouldThrowExceptionWhenUsageMapIsNull() {
		assertThrows(IllegalArgumentException.class, () -> new AnthropicUsageAccessor(null));
	}

	@Test
	@DisplayName("Should return correct token counts for all fields")
	void shouldReturnCorrectTokenCounts() {
		Map<String, Object> usageMap = new HashMap<>();
		usageMap.put("input_tokens", 100L);
		usageMap.put("output_tokens", 50L);
		usageMap.put("cache_creation_input_tokens", 25L);
		usageMap.put("cache_read_input_tokens", 75L);

		AnthropicUsageAccessor accessor = new AnthropicUsageAccessor(usageMap);

		assertThat(accessor.getPromptTokens()).isEqualTo(100L);
		assertThat(accessor.getGenerationTokens()).isEqualTo(50L);
		assertThat(accessor.getCacheCreationInputTokens()).isEqualTo(25L);
		assertThat(accessor.getCacheReadInputTokens()).isEqualTo(75L);
	}

	@Test
	@DisplayName("Should handle missing values in usage map")
	void shouldHandleMissingValues() {
		Map<String, Object> usageMap = new HashMap<>();
		usageMap.put("input_tokens", 100L);

		AnthropicUsageAccessor accessor = new AnthropicUsageAccessor(usageMap);

		assertThat(accessor.getPromptTokens()).isEqualTo(100L);
		assertThat(accessor.getGenerationTokens()).isNull();
		assertThat(accessor.getCacheCreationInputTokens()).isNull();
		assertThat(accessor.getCacheReadInputTokens()).isNull();
	}

	@Test
	@DisplayName("Should handle empty usage map")
	void shouldHandleEmptyUsageMap() {
		Map<String, Object> usageMap = new HashMap<>();

		AnthropicUsageAccessor accessor = new AnthropicUsageAccessor(usageMap);

		assertThat(accessor.getPromptTokens()).isNull();
		assertThat(accessor.getGenerationTokens()).isNull();
		assertThat(accessor.getCacheCreationInputTokens()).isNull();
		assertThat(accessor.getCacheReadInputTokens()).isNull();
	}

	@Test
	@DisplayName("Should handle maximum token values")
	void shouldHandleMaximumTokenValues() {
		Map<String, Object> usageMap = new HashMap<>();
		usageMap.put("input_tokens", Long.MAX_VALUE);
		usageMap.put("output_tokens", Long.MAX_VALUE);
		usageMap.put("cache_creation_input_tokens", Long.MAX_VALUE);
		usageMap.put("cache_read_input_tokens", Long.MAX_VALUE);

		AnthropicUsageAccessor accessor = new AnthropicUsageAccessor(usageMap);

		assertThat(accessor.getPromptTokens()).isEqualTo(Long.MAX_VALUE);
		assertThat(accessor.getGenerationTokens()).isEqualTo(Long.MAX_VALUE);
		assertThat(accessor.getCacheCreationInputTokens()).isEqualTo(Long.MAX_VALUE);
		assertThat(accessor.getCacheReadInputTokens()).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	@DisplayName("Should return usage metadata")
	void shouldReturnUsageMetadata() {
		Map<String, Object> usageMap = new HashMap<>();
		usageMap.put("input_tokens", 100L);
		usageMap.put("output_tokens", 50L);
		usageMap.put("cache_creation_input_tokens", 25L);
		usageMap.put("cache_read_input_tokens", 75L);

		AnthropicUsageAccessor accessor = new AnthropicUsageAccessor(usageMap);

		assertThat(accessor.getUsage()).isEqualTo(usageMap);
	}

}
