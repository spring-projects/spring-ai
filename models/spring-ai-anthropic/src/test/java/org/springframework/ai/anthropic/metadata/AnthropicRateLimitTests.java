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

package org.springframework.ai.anthropic.metadata;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.anthropic.core.http.Headers;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicRateLimit}.
 *
 * @author Soby Chacko
 */
class AnthropicRateLimitTests {

	@Test
	void fromReturnsEmptyRateLimitWhenNoHeadersPresent() {
		Headers headers = Headers.builder().build();

		RateLimit rateLimit = AnthropicRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(EmptyRateLimit.class);
	}

	@Test
	void fromReturnsAnthropicRateLimitWhenStandardHeadersPresent() {
		Instant resetAt = Instant.now().plus(60, ChronoUnit.SECONDS);
		Headers headers = Headers.builder()
			.put("anthropic-ratelimit-requests-limit", "1000")
			.put("anthropic-ratelimit-requests-remaining", "999")
			.put("anthropic-ratelimit-requests-reset", resetAt.toString())
			.put("anthropic-ratelimit-tokens-limit", "100000")
			.put("anthropic-ratelimit-tokens-remaining", "99000")
			.put("anthropic-ratelimit-tokens-reset", resetAt.toString())
			.build();

		RateLimit rateLimit = AnthropicRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(AnthropicRateLimit.class);
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(1000L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(999L);
		assertThat(rateLimit.getRequestsReset()).isNotNull().isPositive();
		assertThat(rateLimit.getTokensLimit()).isEqualTo(100000L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(99000L);
		assertThat(rateLimit.getTokensReset()).isNotNull().isPositive();
	}

	@Test
	void fromReturnsAnthropicRateLimitWithNullsWhenOnlySomeHeadersPresent() {
		Headers headers = Headers.builder().put("anthropic-ratelimit-requests-limit", "1000").build();

		RateLimit rateLimit = AnthropicRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(AnthropicRateLimit.class);
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(1000L);
		assertThat(rateLimit.getRequestsRemaining()).isNull();
		assertThat(rateLimit.getTokensLimit()).isNull();

		AnthropicRateLimit anthropic = (AnthropicRateLimit) rateLimit;
		assertThat(anthropic.getInputTokensLimit()).isNull();
		assertThat(anthropic.getOutputTokensLimit()).isNull();
	}

	@Test
	void fromExposesInputAndOutputTokenFamilies() {
		Instant resetAt = Instant.now().plus(60, ChronoUnit.SECONDS);
		Headers headers = Headers.builder()
			.put("anthropic-ratelimit-input-tokens-limit", "50000")
			.put("anthropic-ratelimit-input-tokens-remaining", "49500")
			.put("anthropic-ratelimit-input-tokens-reset", resetAt.toString())
			.put("anthropic-ratelimit-output-tokens-limit", "50000")
			.put("anthropic-ratelimit-output-tokens-remaining", "49500")
			.put("anthropic-ratelimit-output-tokens-reset", resetAt.toString())
			.build();

		RateLimit rateLimit = AnthropicRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(AnthropicRateLimit.class);
		AnthropicRateLimit anthropic = (AnthropicRateLimit) rateLimit;
		assertThat(anthropic.getInputTokensLimit()).isEqualTo(50000L);
		assertThat(anthropic.getInputTokensRemaining()).isEqualTo(49500L);
		assertThat(anthropic.getInputTokensReset()).isNotNull().isPositive();
		assertThat(anthropic.getOutputTokensLimit()).isEqualTo(50000L);
		assertThat(anthropic.getOutputTokensRemaining()).isEqualTo(49500L);
		assertThat(anthropic.getOutputTokensReset()).isNotNull().isPositive();
	}

}
