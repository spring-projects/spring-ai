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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.anthropic.core.http.Headers;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.util.Assert;

/**
 * {@link RateLimit} implementation for the Anthropic SDK.
 *
 * <p>
 * Parses Anthropic rate-limit response headers as documented at
 * <a href="https://docs.anthropic.com/en/api/rate-limits">Anthropic rate limits</a>.
 *
 * @author Gorre Surya
 * @see RateLimit
 * @see <a href="https://docs.anthropic.com/en/api/rate-limits">Anthropic rate limits</a>
 */
@SuppressWarnings("NullAway")
public class AnthropicRateLimit implements RateLimit {

	private static final String REQUESTS_LIMIT_HEADER = "anthropic-ratelimit-requests-limit";

	private static final String REQUESTS_REMAINING_HEADER = "anthropic-ratelimit-requests-remaining";

	private static final String REQUESTS_RESET_HEADER = "anthropic-ratelimit-requests-reset";

	private static final String TOKENS_LIMIT_HEADER = "anthropic-ratelimit-tokens-limit";

	private static final String TOKENS_REMAINING_HEADER = "anthropic-ratelimit-tokens-remaining";

	private static final String TOKENS_RESET_HEADER = "anthropic-ratelimit-tokens-reset";

	private final @Nullable Long requestsLimit;

	private final @Nullable Long requestsRemaining;

	private final @Nullable Duration requestsReset;

	private final @Nullable Long tokensLimit;

	private final @Nullable Long tokensRemaining;

	private final @Nullable Duration tokensReset;

	public AnthropicRateLimit(@Nullable Long requestsLimit, @Nullable Long requestsRemaining,
			@Nullable Duration requestsReset, @Nullable Long tokensLimit, @Nullable Long tokensRemaining,
			@Nullable Duration tokensReset) {
		this.requestsLimit = requestsLimit;
		this.requestsRemaining = requestsRemaining;
		this.requestsReset = requestsReset;
		this.tokensLimit = tokensLimit;
		this.tokensRemaining = tokensRemaining;
		this.tokensReset = tokensReset;
	}

	/**
	 * Parses Anthropic rate-limit headers from the given {@link Headers}.
	 * @param headers the HTTP response headers
	 * @return a new {@link AnthropicRateLimit} populated from the headers
	 */
	public static AnthropicRateLimit from(Headers headers) {
		Assert.notNull(headers, "Headers must not be null");
		return new AnthropicRateLimit(parseLong(headers, REQUESTS_LIMIT_HEADER),
				parseLong(headers, REQUESTS_REMAINING_HEADER), parseResetDuration(headers, REQUESTS_RESET_HEADER),
				parseLong(headers, TOKENS_LIMIT_HEADER), parseLong(headers, TOKENS_REMAINING_HEADER),
				parseResetDuration(headers, TOKENS_RESET_HEADER));
	}

	@Override
	public Long getRequestsLimit() {
		return this.requestsLimit;
	}

	@Override
	public Long getRequestsRemaining() {
		return this.requestsRemaining;
	}

	@Override
	public Duration getRequestsReset() {
		return this.requestsReset;
	}

	@Override
	public Long getTokensLimit() {
		return this.tokensLimit;
	}

	@Override
	public Long getTokensRemaining() {
		return this.tokensRemaining;
	}

	@Override
	public Duration getTokensReset() {
		return this.tokensReset;
	}

	@Override
	public String toString() {
		return "{ @type: %1$s, requestsLimit: %2$s, requestsRemaining: %3$s, requestsReset: %4$s, tokensLimit: %5$s, tokensRemaining: %6$s, tokensReset: %7$s }"
			.formatted(getClass().getName(), this.requestsLimit, this.requestsRemaining, this.requestsReset,
					this.tokensLimit, this.tokensRemaining, this.tokensReset);
	}

	private static @Nullable Long parseLong(Headers headers, String name) {
		List<String> values = headers.values(name);
		if (values.isEmpty()) {
			return null;
		}
		try {
			return Long.parseLong(values.get(0).trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static @Nullable Duration parseResetDuration(Headers headers, String name) {
		List<String> values = headers.values(name);
		if (values.isEmpty()) {
			return null;
		}
		try {
			Instant resetAt = Instant.parse(values.get(0).trim());
			Duration remaining = Duration.between(Instant.now(), resetAt);
			return remaining.isNegative() ? Duration.ZERO : remaining;
		}
		catch (Exception e) {
			return null;
		}
	}

}
