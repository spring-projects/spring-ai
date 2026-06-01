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

import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.util.Assert;

/**
 * {@link RateLimit} implementation for the Anthropic SDK.
 *
 * <p>
 * Parses Anthropic rate-limit response headers as documented at
 * <a href="https://docs.anthropic.com/en/api/rate-limits">Anthropic rate limits</a>.
 * Beyond the request and token families exposed through the {@link RateLimit} contract,
 * this class also surfaces the Anthropic-specific input-token and output-token buckets
 * via dedicated getters.
 *
 * @author Gorre Surya
 * @author Soby Chacko
 * @since 2.0.0-M8
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

	private static final String INPUT_TOKENS_LIMIT_HEADER = "anthropic-ratelimit-input-tokens-limit";

	private static final String INPUT_TOKENS_REMAINING_HEADER = "anthropic-ratelimit-input-tokens-remaining";

	private static final String INPUT_TOKENS_RESET_HEADER = "anthropic-ratelimit-input-tokens-reset";

	private static final String OUTPUT_TOKENS_LIMIT_HEADER = "anthropic-ratelimit-output-tokens-limit";

	private static final String OUTPUT_TOKENS_REMAINING_HEADER = "anthropic-ratelimit-output-tokens-remaining";

	private static final String OUTPUT_TOKENS_RESET_HEADER = "anthropic-ratelimit-output-tokens-reset";

	private final @Nullable Long requestsLimit;

	private final @Nullable Long requestsRemaining;

	private final @Nullable Duration requestsReset;

	private final @Nullable Long tokensLimit;

	private final @Nullable Long tokensRemaining;

	private final @Nullable Duration tokensReset;

	private final @Nullable Long inputTokensLimit;

	private final @Nullable Long inputTokensRemaining;

	private final @Nullable Duration inputTokensReset;

	private final @Nullable Long outputTokensLimit;

	private final @Nullable Long outputTokensRemaining;

	private final @Nullable Duration outputTokensReset;

	public AnthropicRateLimit(@Nullable Long requestsLimit, @Nullable Long requestsRemaining,
			@Nullable Duration requestsReset, @Nullable Long tokensLimit, @Nullable Long tokensRemaining,
			@Nullable Duration tokensReset, @Nullable Long inputTokensLimit, @Nullable Long inputTokensRemaining,
			@Nullable Duration inputTokensReset, @Nullable Long outputTokensLimit, @Nullable Long outputTokensRemaining,
			@Nullable Duration outputTokensReset) {
		this.requestsLimit = requestsLimit;
		this.requestsRemaining = requestsRemaining;
		this.requestsReset = requestsReset;
		this.tokensLimit = tokensLimit;
		this.tokensRemaining = tokensRemaining;
		this.tokensReset = tokensReset;
		this.inputTokensLimit = inputTokensLimit;
		this.inputTokensRemaining = inputTokensRemaining;
		this.inputTokensReset = inputTokensReset;
		this.outputTokensLimit = outputTokensLimit;
		this.outputTokensRemaining = outputTokensRemaining;
		this.outputTokensReset = outputTokensReset;
	}

	/**
	 * Parses Anthropic rate-limit headers from the given {@link Headers}.
	 * @param headers the HTTP response headers
	 * @return an {@link AnthropicRateLimit} populated from the headers, or an
	 * {@link EmptyRateLimit} if no rate-limit headers are present
	 */
	public static RateLimit from(Headers headers) {
		Assert.notNull(headers, "Headers must not be null");

		Long requestsLimit = parseLong(headers, REQUESTS_LIMIT_HEADER);
		Long requestsRemaining = parseLong(headers, REQUESTS_REMAINING_HEADER);
		Duration requestsReset = parseResetDuration(headers, REQUESTS_RESET_HEADER);
		Long tokensLimit = parseLong(headers, TOKENS_LIMIT_HEADER);
		Long tokensRemaining = parseLong(headers, TOKENS_REMAINING_HEADER);
		Duration tokensReset = parseResetDuration(headers, TOKENS_RESET_HEADER);
		Long inputTokensLimit = parseLong(headers, INPUT_TOKENS_LIMIT_HEADER);
		Long inputTokensRemaining = parseLong(headers, INPUT_TOKENS_REMAINING_HEADER);
		Duration inputTokensReset = parseResetDuration(headers, INPUT_TOKENS_RESET_HEADER);
		Long outputTokensLimit = parseLong(headers, OUTPUT_TOKENS_LIMIT_HEADER);
		Long outputTokensRemaining = parseLong(headers, OUTPUT_TOKENS_REMAINING_HEADER);
		Duration outputTokensReset = parseResetDuration(headers, OUTPUT_TOKENS_RESET_HEADER);

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

	/**
	 * Returns the maximum number of input tokens allowed within the rate-limit window.
	 * @return the input tokens limit, or {@code null} if Anthropic did not return the
	 * header
	 */
	public @Nullable Long getInputTokensLimit() {
		return this.inputTokensLimit;
	}

	/**
	 * Returns the number of input tokens remaining within the current rate-limit window.
	 * @return the remaining input tokens, or {@code null} if Anthropic did not return the
	 * header
	 */
	public @Nullable Long getInputTokensRemaining() {
		return this.inputTokensRemaining;
	}

	/**
	 * Returns the duration until the input-token rate-limit window resets.
	 * @return the duration until reset, or {@code null} if Anthropic did not return the
	 * header
	 */
	public @Nullable Duration getInputTokensReset() {
		return this.inputTokensReset;
	}

	/**
	 * Returns the maximum number of output tokens allowed within the rate-limit window.
	 * @return the output tokens limit, or {@code null} if Anthropic did not return the
	 * header
	 */
	public @Nullable Long getOutputTokensLimit() {
		return this.outputTokensLimit;
	}

	/**
	 * Returns the number of output tokens remaining within the current rate-limit window.
	 * @return the remaining output tokens, or {@code null} if Anthropic did not return
	 * the header
	 */
	public @Nullable Long getOutputTokensRemaining() {
		return this.outputTokensRemaining;
	}

	/**
	 * Returns the duration until the output-token rate-limit window resets.
	 * @return the duration until reset, or {@code null} if Anthropic did not return the
	 * header
	 */
	public @Nullable Duration getOutputTokensReset() {
		return this.outputTokensReset;
	}

	@Override
	public String toString() {
		return ("{ @type: %1$s, requestsLimit: %2$s, requestsRemaining: %3$s, requestsReset: %4$s, "
				+ "tokensLimit: %5$s, tokensRemaining: %6$s, tokensReset: %7$s, "
				+ "inputTokensLimit: %8$s, inputTokensRemaining: %9$s, inputTokensReset: %10$s, "
				+ "outputTokensLimit: %11$s, outputTokensRemaining: %12$s, outputTokensReset: %13$s }")
			.formatted(getClass().getName(), this.requestsLimit, this.requestsRemaining, this.requestsReset,
					this.tokensLimit, this.tokensRemaining, this.tokensReset, this.inputTokensLimit,
					this.inputTokensRemaining, this.inputTokensReset, this.outputTokensLimit,
					this.outputTokensRemaining, this.outputTokensReset);
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
