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

package org.springframework.ai.openai.metadata;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.openai.core.http.Headers;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.util.Assert;

/**
 * {@link RateLimit} implementation for {@literal OpenAI SDK}.
 *
 * @author John Blum
 * @author Ilayaperumal Gopinathan
 * @see <a href=
 * "https://developers.openai.com/api/docs/guides/rate-limits/#rate-limits-in-headers">Rate
 * limits in headers</a>
 */
@SuppressWarnings("NullAway")
public class OpenAiRateLimit implements RateLimit {

	private static final String REQUESTS_LIMIT_HEADER = "x-ratelimit-limit-requests";

	private static final String REQUESTS_REMAINING_HEADER = "x-ratelimit-remaining-requests";

	private static final String REQUESTS_RESET_HEADER = "x-ratelimit-reset-requests";

	private static final String TOKENS_LIMIT_HEADER = "x-ratelimit-limit-tokens";

	private static final String TOKENS_REMAINING_HEADER = "x-ratelimit-remaining-tokens";

	private static final String TOKENS_RESET_HEADER = "x-ratelimit-reset-tokens";

	private static final Pattern DURATION_COMPONENT_PATTERN = Pattern.compile("(\\d+)(ms|d|h|m|s)");

	private final @Nullable Long requestsLimit;

	private final @Nullable Long requestsRemaining;

	private final @Nullable Long tokensLimit;

	private final @Nullable Long tokensRemaining;

	private final @Nullable Duration requestsReset;

	private final @Nullable Duration tokensReset;

	public OpenAiRateLimit(@Nullable Long requestsLimit, @Nullable Long requestsRemaining,
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
	 * Parses OpenAI rate-limit response headers.
	 * @param headers the HTTP response headers
	 * @return an {@link OpenAiRateLimit} populated from the headers, or an
	 * {@link EmptyRateLimit} if no rate-limit headers are present
	 */
	public static RateLimit from(Headers headers) {
		Assert.notNull(headers, "Headers must not be null");

		Long requestsLimit = getHeaderAsLong(headers, REQUESTS_LIMIT_HEADER);
		Long requestsRemaining = getHeaderAsLong(headers, REQUESTS_REMAINING_HEADER);
		Duration requestsReset = getHeaderAsDuration(headers, REQUESTS_RESET_HEADER);
		Long tokensLimit = getHeaderAsLong(headers, TOKENS_LIMIT_HEADER);
		Long tokensRemaining = getHeaderAsLong(headers, TOKENS_REMAINING_HEADER);
		Duration tokensReset = getHeaderAsDuration(headers, TOKENS_RESET_HEADER);

		return (requestsLimit != null || tokensLimit != null) ? new OpenAiRateLimit(requestsLimit, requestsRemaining,
				requestsReset, tokensLimit, tokensRemaining, tokensReset) : new EmptyRateLimit();
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

	private static @Nullable Long getHeaderAsLong(Headers headers, String headerName) {
		var values = headers.values(headerName);
		if (!values.isEmpty()) {
			try {
				return Long.parseLong(values.get(0).trim());
			}
			catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	private static @Nullable Duration getHeaderAsDuration(Headers headers, String headerName) {
		var values = headers.values(headerName);
		if (!values.isEmpty()) {
			return parseDuration(values.get(0).trim());
		}
		return null;
	}

	private static @Nullable Duration parseDuration(String value) {
		if (value.isEmpty()) {
			return null;
		}
		try {
			return Duration.ofSeconds(Long.parseLong(value));
		}
		catch (NumberFormatException ex) {
			Matcher matcher = DURATION_COMPONENT_PATTERN.matcher(value);
			Duration duration = Duration.ZERO;
			int end = 0;
			try {
				while (matcher.find()) {
					if (matcher.start() != end) {
						return null;
					}
					long amount = Long.parseLong(matcher.group(1));
					duration = switch (matcher.group(2)) {
						case "ms" -> duration.plusMillis(amount);
						case "d" -> duration.plusDays(amount);
						case "h" -> duration.plusHours(amount);
						case "m" -> duration.plusMinutes(amount);
						case "s" -> duration.plusSeconds(amount);
						default -> throw new IllegalArgumentException("Unsupported duration unit");
					};
					end = matcher.end();
				}
				return end == value.length() ? duration : null;
			}
			catch (ArithmeticException | NumberFormatException ignored) {
				return null;
			}
		}
	}

	@Override
	public String toString() {
		return "{ @type: %1$s, requestsLimit: %2$s, requestsRemaining: %3$s, requestsReset: %4$s, tokensLimit: %5$s; tokensRemaining: %6$s; tokensReset: %7$s }"
			.formatted(getClass().getName(), getRequestsLimit(), getRequestsRemaining(), getRequestsReset(),
					getTokensLimit(), getTokensRemaining(), getTokensReset());
	}

}
