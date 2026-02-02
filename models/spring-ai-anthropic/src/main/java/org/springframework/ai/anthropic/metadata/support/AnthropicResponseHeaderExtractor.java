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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.metadata.AnthropicRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Utility used to extract known HTTP response headers for the {@literal Anthropic} API.
 *
 * @author Jake Son
 * @since 2.0.0
 */
public final class AnthropicResponseHeaderExtractor {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicResponseHeaderExtractor.class);

	private AnthropicResponseHeaderExtractor() {
	}

	public static RateLimit extractAiResponseHeaders(ResponseEntity<?> response) {

		Instant now = Instant.now();

		Long requestsLimit = getHeaderAsLongOrDefault(response,
				AnthropicApiResponseHeaders.REQUESTS_LIMIT_HEADER.getName());
		Long requestsRemaining = getHeaderAsLongOrDefault(response,
				AnthropicApiResponseHeaders.REQUESTS_REMAINING_HEADER.getName());
		Duration requestsReset = getHeaderAsDurationOrDefault(response,
				AnthropicApiResponseHeaders.REQUESTS_RESET_HEADER.getName(), now);

		Long tokensLimit = getHeaderAsLongOrDefault(response,
				AnthropicApiResponseHeaders.TOKENS_LIMIT_HEADER.getName());
		Long tokensRemaining = getHeaderAsLongOrDefault(response,
				AnthropicApiResponseHeaders.TOKENS_REMAINING_HEADER.getName());
		Duration tokensReset = getHeaderAsDurationOrDefault(response,
				AnthropicApiResponseHeaders.TOKENS_RESET_HEADER.getName(), now);

		Long inputTokensLimit = getHeaderAsLong(response,
				AnthropicApiResponseHeaders.INPUT_TOKENS_LIMIT_HEADER.getName());
		Long inputTokensRemaining = getHeaderAsLong(response,
				AnthropicApiResponseHeaders.INPUT_TOKENS_REMAINING_HEADER.getName());
		Duration inputTokensReset = getHeaderAsDuration(response,
				AnthropicApiResponseHeaders.INPUT_TOKENS_RESET_HEADER.getName(), now);

		Long outputTokensLimit = getHeaderAsLong(response,
				AnthropicApiResponseHeaders.OUTPUT_TOKENS_LIMIT_HEADER.getName());
		Long outputTokensRemaining = getHeaderAsLong(response,
				AnthropicApiResponseHeaders.OUTPUT_TOKENS_REMAINING_HEADER.getName());
		Duration outputTokensReset = getHeaderAsDuration(response,
				AnthropicApiResponseHeaders.OUTPUT_TOKENS_RESET_HEADER.getName(), now);

		return new AnthropicRateLimit(requestsLimit, requestsRemaining, requestsReset, tokensLimit, tokensRemaining,
				tokensReset, inputTokensLimit, inputTokensRemaining, inputTokensReset, outputTokensLimit,
				outputTokensRemaining, outputTokensReset);
	}

	private static Duration getHeaderAsDurationOrDefault(ResponseEntity<?> response, String headerName, Instant now) {
		Duration duration = getHeaderAsDuration(response, headerName, now);
		return duration != null ? duration : Duration.ZERO;
	}

	private static Duration getHeaderAsDuration(ResponseEntity<?> response, String headerName, Instant now) {
		var headers = response.getHeaders();
		var values = headers.get(headerName);
		if (!CollectionUtils.isEmpty(values)) {
			return parseRfc3339ToDuration(headerName, values.get(0), now);
		}
		return null;
	}

	private static Long getHeaderAsLongOrDefault(ResponseEntity<?> response, String headerName) {
		Long value = getHeaderAsLong(response, headerName);
		return value != null ? value : 0L;
	}

	private static Long getHeaderAsLong(ResponseEntity<?> response, String headerName) {
		var headers = response.getHeaders();
		var values = headers.get(headerName);
		if (!CollectionUtils.isEmpty(values)) {
			return parseLong(headerName, values.get(0));
		}
		return null;
	}

	private static Duration parseRfc3339ToDuration(String headerName, String headerValue, Instant now) {

		if (StringUtils.hasText(headerValue)) {
			try {
				Instant resetTime = Instant.parse(headerValue.trim());
				Duration duration = Duration.between(now, resetTime);
				return duration.isNegative() ? Duration.ZERO : duration;
			}
			catch (Exception e) {
				logger.warn("Value [{}] for HTTP header [{}] is not a valid RFC 3339 timestamp: {}", headerValue,
						headerName, e.getMessage());
			}
		}

		return null;
	}

	private static Long parseLong(String headerName, String headerValue) {

		if (StringUtils.hasText(headerValue)) {
			try {
				return Long.parseLong(headerValue.trim());
			}
			catch (NumberFormatException e) {
				logger.warn("Value [{}] for HTTP header [{}] is not valid: {}", headerValue, headerName,
						e.getMessage());
			}
		}

		return null;
	}

}
