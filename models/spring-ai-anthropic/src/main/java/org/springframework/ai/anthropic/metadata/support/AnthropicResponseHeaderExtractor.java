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

package org.springframework.ai.anthropic.metadata.support;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.metadata.AnthropicRateLimit;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Utility used to extract known HTTP response headers for the {@literal Anthropic} API.
 *
 * @author Jake Son
 */
public final class AnthropicResponseHeaderExtractor {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicResponseHeaderExtractor.class);

	private static final String REQUESTS_LIMIT_HEADER = "anthropic-ratelimit-requests-limit";

	private static final String REQUESTS_REMAINING_HEADER = "anthropic-ratelimit-requests-remaining";

	private static final String REQUESTS_RESET_HEADER = "anthropic-ratelimit-requests-reset";

	private static final String TOKENS_LIMIT_HEADER = "anthropic-ratelimit-tokens-limit";

	private static final String TOKENS_REMAINING_HEADER = "anthropic-ratelimit-tokens-remaining";

	private static final String TOKENS_RESET_HEADER = "anthropic-ratelimit-tokens-reset";

	private static final String INPUT_TOKENS_LIMIT_HEADER = "anthropic-ratelimit-input-tokens-limit";

	private static final String INPUT_TOKENS_REMAINING_HEADER = "anthropic-ratelimit-input-tokens-remaining";

	private static final String INPUT_TOKENS_RESET_HEADER = "anthropic-ratelimit-input-tokens-reset";

	private static final String OUTPUT_TOKENS_LIMIT_HEADER = "anthropic-ratelimit-output-tokens-limit";

	private static final String OUTPUT_TOKENS_REMAINING_HEADER = "anthropic-ratelimit-output-tokens-remaining";

	private static final String OUTPUT_TOKENS_RESET_HEADER = "anthropic-ratelimit-output-tokens-reset";

	private AnthropicResponseHeaderExtractor() {
	}

	public static RateLimit extractAiResponseHeaders(ResponseEntity<?> response) {

		Instant now = Instant.now();

		Long requestsLimit = getHeaderAsLong(response, REQUESTS_LIMIT_HEADER);
		Long requestsRemaining = getHeaderAsLong(response, REQUESTS_REMAINING_HEADER);
		Duration requestsReset = getHeaderAsDuration(response, REQUESTS_RESET_HEADER, now);

		Long tokensLimit = getHeaderAsLong(response, TOKENS_LIMIT_HEADER);
		Long tokensRemaining = getHeaderAsLong(response, TOKENS_REMAINING_HEADER);
		Duration tokensReset = getHeaderAsDuration(response, TOKENS_RESET_HEADER, now);

		Long inputTokensLimit = getHeaderAsLong(response, INPUT_TOKENS_LIMIT_HEADER);
		Long inputTokensRemaining = getHeaderAsLong(response, INPUT_TOKENS_REMAINING_HEADER);
		Duration inputTokensReset = getHeaderAsDuration(response, INPUT_TOKENS_RESET_HEADER, now);

		Long outputTokensLimit = getHeaderAsLong(response, OUTPUT_TOKENS_LIMIT_HEADER);
		Long outputTokensRemaining = getHeaderAsLong(response, OUTPUT_TOKENS_REMAINING_HEADER);
		Duration outputTokensReset = getHeaderAsDuration(response, OUTPUT_TOKENS_RESET_HEADER, now);

		boolean allAbsent = requestsLimit == null && requestsRemaining == null && requestsReset == null
				&& tokensLimit == null && tokensRemaining == null && tokensReset == null && inputTokensLimit == null
				&& inputTokensRemaining == null && inputTokensReset == null && outputTokensLimit == null
				&& outputTokensRemaining == null && outputTokensReset == null;

		if (allAbsent) {
			return new EmptyRateLimit();
		}

		return new AnthropicRateLimit(requestsLimit, requestsRemaining, requestsReset, tokensLimit, tokensRemaining,
				tokensReset, inputTokensLimit, inputTokensRemaining, inputTokensReset, outputTokensLimit,
				outputTokensRemaining, outputTokensReset);
	}

	private static Duration getHeaderAsDuration(ResponseEntity<?> response, String headerName, Instant now) {
		var headers = response.getHeaders();
		var values = headers.get(headerName);
		if (!CollectionUtils.isEmpty(values)) {
			return parseRfc3339ToDuration(headerName, values.get(0), now);
		}
		return null;
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
