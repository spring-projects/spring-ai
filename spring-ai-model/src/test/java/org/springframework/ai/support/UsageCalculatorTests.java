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

package org.springframework.ai.support;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UsageCalculator}.
 *
 * @author Jewoo Shin
 */
class UsageCalculatorTests {

	@Test
	void getCumulativeUsageReturnsCurrentWhenPreviousChatResponseIsNull() {
		Usage current = new DefaultUsage(10, 20, 30);

		Usage result = UsageCalculator.getCumulativeUsage(current, null);

		// The current usage is returned unchanged (same instance) when there is nothing
		// to accumulate.
		assertThat(result).isSameAs(current);
	}

	@Test
	void getCumulativeUsageReturnsCurrentWhenPreviousUsageIsEmpty() {
		Usage current = new DefaultUsage(10, 20, 30);
		ChatResponse previous = responseWith(new DefaultUsage(0, 0, 0));

		Usage result = UsageCalculator.getCumulativeUsage(current, previous);

		// Previous contributes nothing, so the current token counts are preserved.
		assertUsage(result, 10, 20, 30);
	}

	@Test
	void getCumulativeUsageReturnsPreviousWhenCurrentIsEmpty() {
		Usage current = new DefaultUsage(0, 0, 0);
		Usage previousUsage = new DefaultUsage(1, 2, 3);
		ChatResponse previous = responseWith(previousUsage);

		Usage result = UsageCalculator.getCumulativeUsage(current, previous);

		// When current is empty the previous usage is returned as-is.
		assertThat(result).isSameAs(previousUsage);
	}

	@Test
	void getCumulativeUsageSumsTokenCounts() {
		Usage current = new DefaultUsage(10, 20, 30);
		ChatResponse previous = responseWith(new DefaultUsage(1, 2, 3));

		Usage result = UsageCalculator.getCumulativeUsage(current, previous);

		assertUsage(result, 11, 22, 33);
	}

	@Test
	void getCumulativeUsageSumsCacheMetrics() {
		Usage current = new DefaultUsage(10, 20, 30, null, 100L, 5L);
		ChatResponse previous = responseWith(new DefaultUsage(1, 2, 3, null, 200L, 7L));

		Usage result = UsageCalculator.getCumulativeUsage(current, previous);

		assertUsage(result, 11, 22, 33);
		assertThat(result.getCacheReadInputTokens()).isEqualTo(300L);
		assertThat(result.getCacheWriteInputTokens()).isEqualTo(12L);
	}

	@Test
	void getCumulativeUsageTreatsOneSidedCacheMetricAsZero() {
		Usage current = new DefaultUsage(10, 20, 30, null, 100L, null);
		ChatResponse previous = responseWith(new DefaultUsage(1, 2, 3, null, null, null));

		Usage result = UsageCalculator.getCumulativeUsage(current, previous);

		// Only one side reports cacheRead, so it carries over; cacheWrite stays null
		// because neither side reports it.
		assertThat(result.getCacheReadInputTokens()).isEqualTo(100L);
		assertThat(result.getCacheWriteInputTokens()).isNull();
	}

	@Test
	void getCumulativeUsagePreservesNullCacheMetricsWhenNeitherSideReports() {
		Usage current = new DefaultUsage(10, 20, 30);
		ChatResponse previous = responseWith(new DefaultUsage(1, 2, 3));

		Usage result = UsageCalculator.getCumulativeUsage(current, previous);

		assertThat(result.getCacheReadInputTokens()).isNull();
		assertThat(result.getCacheWriteInputTokens()).isNull();
	}

	@Test
	void getCumulativeUsageDropsNativeUsageWhenAccumulating() {
		Object currentNative = new Object();
		Object previousNative = new Object();
		Usage current = new DefaultUsage(10, 20, 30, currentNative);
		ChatResponse previous = responseWith(new DefaultUsage(1, 2, 3, previousNative));

		Usage result = UsageCalculator.getCumulativeUsage(current, previous);

		// Provider-specific native usage objects cannot be merged across responses,
		// so the accumulated result drops them.
		assertThat(result.getNativeUsage()).isNull();
	}

	@Test
	void getCumulativeUsageKeepsNativeUsageWhenNothingToAccumulate() {
		Object currentNative = new Object();
		Usage current = new DefaultUsage(10, 20, 30, currentNative);

		Usage result = UsageCalculator.getCumulativeUsage(current, null);

		// A single, non-accumulated usage keeps its native usage intact.
		assertThat(result.getNativeUsage()).isSameAs(currentNative);
	}

	@Test
	void accumulateResponseUsageReturnsAccumulatedResponseWhenCurrentIsNull() {
		ChatResponse accumulated = responseWith(new DefaultUsage(1, 2, 3));

		ChatResponse result = UsageCalculator.accumulateResponseUsage(null, accumulated);

		assertThat(result).isSameAs(accumulated);
	}

	@Test
	void accumulateResponseUsageReturnsCumulativeUsageOnTheCurrentResponse() {
		ChatResponse current = responseWith(new DefaultUsage(10, 20, 30));
		ChatResponse accumulated = responseWith(new DefaultUsage(1, 2, 3));

		ChatResponse result = UsageCalculator.accumulateResponseUsage(current, accumulated);

		assertThat(result).isNotNull();
		assertUsage(result.getMetadata().getUsage(), 11, 22, 33);
	}

	@Test
	void accumulateResponseUsageReturnsNullWhenNothingReported() {
		ChatResponse current = responseWith(new DefaultUsage(0, 0, 0));

		ChatResponse result = UsageCalculator.accumulateResponseUsage(current, null);

		assertThat(result).isNull();
	}

	@Test
	void accumulateResponseUsageKeepsNativeUsageOnFirstNonEmptyResponse() {
		Object nativeUsage = new Object();
		ChatResponse current = responseWith(new DefaultUsage(10, 20, 30, nativeUsage));

		ChatResponse result = UsageCalculator.accumulateResponseUsage(current, null);

		assertThat(result).isNotNull();
		assertThat(result.getMetadata().getUsage().getNativeUsage()).isSameAs(nativeUsage);
	}

	@Test
	void withUsageReplacesUsageAndPreservesOtherMetadata() {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder()
			.id("id-1")
			.model("model-1")
			.usage(new DefaultUsage(1, 2, 3))
			.keyValue("custom", "value")
			.build();
		ChatResponse chatResponse = ChatResponse.builder().generations(List.of()).metadata(metadata).build();

		ChatResponse result = UsageCalculator.withUsage(chatResponse, new DefaultUsage(10, 20, 30));

		assertUsage(result.getMetadata().getUsage(), 10, 20, 30);
		assertThat(result.getMetadata().getId()).isEqualTo("id-1");
		assertThat(result.getMetadata().getModel()).isEqualTo("model-1");
		assertThat(result.getMetadata().<String>get("custom")).isEqualTo("value");
	}

	@Test
	void isEmptyReturnsTrueForNullUsage() {
		assertThat(UsageCalculator.isEmpty(null)).isTrue();
	}

	@Test
	void isEmptyReturnsTrueForZeroTotalTokens() {
		assertThat(UsageCalculator.isEmpty(new DefaultUsage(0, 0, 0))).isTrue();
	}

	@Test
	void isEmptyReturnsFalseForNonZeroTotalTokens() {
		assertThat(UsageCalculator.isEmpty(new DefaultUsage(1, 2, 3))).isFalse();
	}

	private ChatResponse responseWith(Usage usage) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
		return ChatResponse.builder().generations(List.of()).metadata(metadata).build();
	}

	private void assertUsage(Usage usage, int promptTokens, int completionTokens, int totalTokens) {
		assertThat(usage.getPromptTokens()).isEqualTo(promptTokens);
		assertThat(usage.getCompletionTokens()).isEqualTo(completionTokens);
		assertThat(usage.getTotalTokens()).isEqualTo(totalTokens);
	}

}
