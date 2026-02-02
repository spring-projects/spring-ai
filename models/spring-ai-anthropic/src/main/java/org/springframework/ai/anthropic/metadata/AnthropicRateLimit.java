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

package org.springframework.ai.anthropic.metadata;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.RateLimit;

/**
 * {@link RateLimit} implementation for {@literal Anthropic}.
 * <p>
 * Anthropic provides rate limit information for requests, tokens, input tokens, and
 * output tokens. The base {@link RateLimit} interface provides access to request and
 * token limits. This class extends those with additional Anthropic-specific methods for
 * input and output token limits.
 *
 * @author Christian Tzolov
 * @author Jonghoon Park
 * @author Jake Son
 * @since 1.0.0
 */
public class AnthropicRateLimit implements RateLimit {

	private static final String RATE_LIMIT_STRING = "{ @type: %1$s, requestsLimit: %2$s, requestsRemaining: %3$s, "
			+ "requestsReset: %4$s, tokensLimit: %5$s, tokensRemaining: %6$s, tokensReset: %7$s, "
			+ "inputTokensLimit: %8$s, inputTokensRemaining: %9$s, inputTokensReset: %10$s, "
			+ "outputTokensLimit: %11$s, outputTokensRemaining: %12$s, outputTokensReset: %13$s }";

	private final Long requestsLimit;

	private final Long requestsRemaining;

	private final Duration requestsReset;

	private final Long tokensLimit;

	private final Long tokensRemaining;

	private final Duration tokensReset;

	private final Long inputTokensLimit;

	private final Long inputTokensRemaining;

	private final Duration inputTokensReset;

	private final Long outputTokensLimit;

	private final Long outputTokensRemaining;

	private final Duration outputTokensReset;

	public AnthropicRateLimit(Long requestsLimit, Long requestsRemaining, Duration requestsReset, Long tokensLimit,
			Long tokensRemaining, Duration tokensReset, @Nullable Long inputTokensLimit,
			@Nullable Long inputTokensRemaining, @Nullable Duration inputTokensReset, @Nullable Long outputTokensLimit,
			@Nullable Long outputTokensRemaining, @Nullable Duration outputTokensReset) {

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
	 * Returns the maximum number of input tokens allowed within the rate limit window.
	 * @return the input tokens limit, or null if not provided
	 */
	public @Nullable Long getInputTokensLimit() {
		return this.inputTokensLimit;
	}

	/**
	 * Returns the number of input tokens remaining within the current rate limit window.
	 * @return the remaining input tokens, or null if not provided
	 */
	public @Nullable Long getInputTokensRemaining() {
		return this.inputTokensRemaining;
	}

	/**
	 * Returns the duration until the input token rate limit window resets.
	 * @return the duration until reset, or null if not provided
	 */
	public @Nullable Duration getInputTokensReset() {
		return this.inputTokensReset;
	}

	/**
	 * Returns the maximum number of output tokens allowed within the rate limit window.
	 * @return the output tokens limit, or null if not provided
	 */
	public @Nullable Long getOutputTokensLimit() {
		return this.outputTokensLimit;
	}

	/**
	 * Returns the number of output tokens remaining within the current rate limit window.
	 * @return the remaining output tokens, or null if not provided
	 */
	public @Nullable Long getOutputTokensRemaining() {
		return this.outputTokensRemaining;
	}

	/**
	 * Returns the duration until the output token rate limit window resets.
	 * @return the duration until reset, or null if not provided
	 */
	public @Nullable Duration getOutputTokensReset() {
		return this.outputTokensReset;
	}

	@Override
	public String toString() {
		return RATE_LIMIT_STRING.formatted(getClass().getName(), getRequestsLimit(), getRequestsRemaining(),
				getRequestsReset(), getTokensLimit(), getTokensRemaining(), getTokensReset(), getInputTokensLimit(),
				getInputTokensRemaining(), getInputTokensReset(), getOutputTokensLimit(), getOutputTokensRemaining(),
				getOutputTokensReset());
	}

}
