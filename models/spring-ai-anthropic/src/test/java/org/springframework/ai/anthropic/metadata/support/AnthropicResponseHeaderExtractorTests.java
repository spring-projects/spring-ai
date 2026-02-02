/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.anthropic.metadata.support;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.metadata.AnthropicRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnthropicResponseHeaderExtractor}.
 *
 * @author Jake Son
 */
class AnthropicResponseHeaderExtractorTests {

	@Test
	void extractAllHeaders() {
		Instant futureTime = Instant.now().plus(60, ChronoUnit.SECONDS);
		String resetTime = futureTime.toString();

		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-limit", "1000");
		headers.add("anthropic-ratelimit-requests-remaining", "999");
		headers.add("anthropic-ratelimit-requests-reset", resetTime);
		headers.add("anthropic-ratelimit-tokens-limit", "100000");
		headers.add("anthropic-ratelimit-tokens-remaining", "99000");
		headers.add("anthropic-ratelimit-tokens-reset", resetTime);
		headers.add("anthropic-ratelimit-input-tokens-limit", "50000");
		headers.add("anthropic-ratelimit-input-tokens-remaining", "49500");
		headers.add("anthropic-ratelimit-input-tokens-reset", resetTime);
		headers.add("anthropic-ratelimit-output-tokens-limit", "50000");
		headers.add("anthropic-ratelimit-output-tokens-remaining", "49500");
		headers.add("anthropic-ratelimit-output-tokens-reset", resetTime);

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit).isInstanceOf(AnthropicRateLimit.class);
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(1000L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(999L);
		assertThat(rateLimit.getRequestsReset()).isNotNull();
		assertThat(rateLimit.getTokensLimit()).isEqualTo(100000L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(99000L);
		assertThat(rateLimit.getTokensReset()).isNotNull();

		AnthropicRateLimit anthropicRateLimit = (AnthropicRateLimit) rateLimit;
		assertThat(anthropicRateLimit.getInputTokensLimit()).isEqualTo(50000L);
		assertThat(anthropicRateLimit.getInputTokensRemaining()).isEqualTo(49500L);
		assertThat(anthropicRateLimit.getInputTokensReset()).isNotNull();
		assertThat(anthropicRateLimit.getOutputTokensLimit()).isEqualTo(50000L);
		assertThat(anthropicRateLimit.getOutputTokensRemaining()).isEqualTo(49500L);
		assertThat(anthropicRateLimit.getOutputTokensReset()).isNotNull();
	}

	@Test
	void extractPartialHeaders() {
		Instant futureTime = Instant.now().plus(60, ChronoUnit.SECONDS);

		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-limit", "1000");
		headers.add("anthropic-ratelimit-requests-remaining", "999");
		headers.add("anthropic-ratelimit-requests-reset", futureTime.toString());

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsLimit()).isEqualTo(1000L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(999L);
		assertThat(rateLimit.getRequestsReset()).isNotNull();

		assertThat(rateLimit.getTokensLimit()).isEqualTo(0L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(0L);
		assertThat(rateLimit.getTokensReset()).isEqualTo(Duration.ZERO);

		AnthropicRateLimit anthropicRateLimit = (AnthropicRateLimit) rateLimit;
		assertThat(anthropicRateLimit.getInputTokensLimit()).isNull();
		assertThat(anthropicRateLimit.getOutputTokensLimit()).isNull();
	}

	@Test
	void extractNoHeaders() {
		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsLimit()).isEqualTo(0L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(0L);
		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ZERO);
		assertThat(rateLimit.getTokensLimit()).isEqualTo(0L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(0L);
		assertThat(rateLimit.getTokensReset()).isEqualTo(Duration.ZERO);

		AnthropicRateLimit anthropicRateLimit = (AnthropicRateLimit) rateLimit;
		assertThat(anthropicRateLimit.getInputTokensLimit()).isNull();
		assertThat(anthropicRateLimit.getInputTokensRemaining()).isNull();
		assertThat(anthropicRateLimit.getInputTokensReset()).isNull();
		assertThat(anthropicRateLimit.getOutputTokensLimit()).isNull();
		assertThat(anthropicRateLimit.getOutputTokensRemaining()).isNull();
		assertThat(anthropicRateLimit.getOutputTokensReset()).isNull();
	}

	@Test
	void parseRfc3339Timestamp() {
		// Future time: should return positive duration
		Instant futureTime = Instant.now().plus(120, ChronoUnit.SECONDS);

		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-reset", futureTime.toString());

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsReset()).isNotNull();
		assertThat(rateLimit.getRequestsReset().getSeconds()).isGreaterThan(0);
		assertThat(rateLimit.getRequestsReset().getSeconds()).isLessThanOrEqualTo(120);
	}

	@Test
	void parseRfc3339TimestampPastReturnsZero() {
		// Past time: should return zero duration
		Instant pastTime = Instant.now().minus(60, ChronoUnit.SECONDS);

		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-reset", pastTime.toString());

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ZERO);
	}

	@Test
	void handleInvalidTimestamp() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-reset", "not-a-valid-timestamp");

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ZERO);
	}

	@Test
	void handleInvalidLongValue() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-limit", "not-a-number");

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsLimit()).isEqualTo(0L);
	}

	@Test
	void handleEmptyHeaderValue() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-limit", "");
		headers.add("anthropic-ratelimit-requests-reset", "");

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsLimit()).isEqualTo(0L);
		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ZERO);
	}

	@Test
	void handleWhitespaceInValues() {
		Instant futureTime = Instant.now().plus(60, ChronoUnit.SECONDS);

		HttpHeaders headers = new HttpHeaders();
		headers.add("anthropic-ratelimit-requests-limit", "  1000  ");
		headers.add("anthropic-ratelimit-requests-reset", "  " + futureTime.toString() + "  ");

		ResponseEntity<String> response = ResponseEntity.ok().headers(headers).body("test");

		RateLimit rateLimit = AnthropicResponseHeaderExtractor.extractAiResponseHeaders(response);

		assertThat(rateLimit.getRequestsLimit()).isEqualTo(1000L);
		assertThat(rateLimit.getRequestsReset()).isNotNull();
	}

}
