/*
 * Copyright 2023-2024 the original author or authors.
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

import org.springframework.ai.chat.metadata.RateLimit;

/**
 * {@link RateLimit} implementation for {@literal Anthropic}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class AnthropicRateLimit implements RateLimit {

	private static final String RATE_LIMIT_STRING = "{ @type: %1$s, requestsLimit: %2$s, requestsRemaining: %3$s, requestsReset: %4$s, tokensLimit: %5$s, tokensRemaining: %6$s, tokensReset: %7$s }";

	private final Long requestsLimit;

	private final Long requestsRemaining;

	private final Long tokensLimit;

	private final Long tokensRemaining;

	private final Duration requestsReset;

	private final Duration tokensReset;

	public AnthropicRateLimit(Long requestsLimit, Long requestsRemaining, Duration requestsReset, Long tokensLimit,
			Long tokensRemaining, Duration tokensReset) {

		this.requestsLimit = requestsLimit;
		this.requestsRemaining = requestsRemaining;
		this.requestsReset = requestsReset;
		this.tokensLimit = tokensLimit;
		this.tokensRemaining = tokensRemaining;
		this.tokensReset = tokensReset;
	}

	@Override
	public Long getRequestsLimit() {
		return this.requestsLimit;
	}

	@Override
	public Long getTokensLimit() {
		return this.tokensLimit;
	}

	@Override
	public Long getRequestsRemaining() {
		return this.requestsRemaining;
	}

	@Override
	public Long getTokensRemaining() {
		return this.tokensRemaining;
	}

	@Override
	public Duration getRequestsReset() {
		return this.requestsReset;
	}

	@Override
	public Duration getTokensReset() {
		return this.tokensReset;
	}

	@Override
	public String toString() {
		return RATE_LIMIT_STRING.formatted(getClass().getName(), getRequestsLimit(), getRequestsRemaining(),
				getRequestsReset(), getTokensLimit(), getTokensRemaining(), getTokensReset());
	}

}
