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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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
 * @author Raphael Vullriede
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

	/**
	 * One component of a reset header such as {@code 6m0s}, {@code 27h55s451ms} or
	 * {@code 19m42.213s}. The two-letter symbols come first so that {@code ms} is not
	 * read as {@code m}.
	 */
	private static final String RESET_COMPONENT_REGEX = "(\\d+(?:\\.\\d+)?)(ns|\u00b5s|us|ms|s|m|h|d)";

	private static final Pattern RESET_COMPONENT = Pattern.compile(RESET_COMPONENT_REGEX);

	/**
	 * A reset header is a sequence of components and nothing else. Anchoring the whole
	 * value keeps text such as {@code about 3s} from reading as a duration.
	 */
	private static final Pattern RESET_VALUE = Pattern.compile("(?:" + RESET_COMPONENT_REGEX + ")+");

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
	 * Parses the OpenAI rate-limit headers from the given {@link Headers}.
	 * @param headers the HTTP response headers
	 * @return an {@link OpenAiRateLimit} populated from the headers, or an
	 * {@link EmptyRateLimit} if no rate-limit headers are present
	 * @since 2.1.0
	 */
	public static RateLimit from(Headers headers) {
		Assert.notNull(headers, "Headers must not be null");

		Long requestsLimit = parseLong(headers, REQUESTS_LIMIT_HEADER);
		Long requestsRemaining = parseLong(headers, REQUESTS_REMAINING_HEADER);
		Duration requestsReset = parseReset(headers, REQUESTS_RESET_HEADER);
		Long tokensLimit = parseLong(headers, TOKENS_LIMIT_HEADER);
		Long tokensRemaining = parseLong(headers, TOKENS_REMAINING_HEADER);
		Duration tokensReset = parseReset(headers, TOKENS_RESET_HEADER);

		if (requestsLimit == null && requestsRemaining == null && requestsReset == null && tokensLimit == null
				&& tokensRemaining == null && tokensReset == null) {
			return new EmptyRateLimit();
		}

		return new OpenAiRateLimit(requestsLimit, requestsRemaining, requestsReset, tokensLimit, tokensRemaining,
				tokensReset);
	}

	private static @Nullable String firstValue(Headers headers, String headerName) {
		List<String> values = headers.values(headerName);
		return values.isEmpty() ? null : values.get(0).trim();
	}

	private static @Nullable Long parseLong(Headers headers, String headerName) {
		String value = firstValue(headers, headerName);
		if (value == null) {
			return null;
		}
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static @Nullable Duration parseReset(Headers headers, String headerName) {
		String value = firstValue(headers, headerName);
		if (value == null) {
			return null;
		}

		try {
			if (RESET_VALUE.matcher(value).matches()) {
				Duration reset = Duration.ZERO;
				Matcher matcher = RESET_COMPONENT.matcher(value);
				while (matcher.find()) {
					reset = reset.plusNanos(nanosOf(matcher.group(1), matcher.group(2)));
				}
				return reset;
			}
			// The speech path read the reset as plain seconds, so keep accepting that
			// form.
			return Duration.ofSeconds(Long.parseLong(value));
		}
		catch (ArithmeticException | NumberFormatException e) {
			return null;
		}
	}

	private static long nanosOf(String amount, String symbol) {
		return new BigDecimal(amount).multiply(BigDecimal.valueOf(nanosPerUnit(symbol))).longValueExact();
	}

	private static long nanosPerUnit(String symbol) {
		return switch (symbol) {
			case "ns" -> 1L;
			case "\u00b5s", "us" -> 1_000L;
			case "ms" -> 1_000_000L;
			case "s" -> 1_000_000_000L;
			case "m" -> 60_000_000_000L;
			case "h" -> 3_600_000_000_000L;
			case "d" -> 86_400_000_000_000L;
			default -> throw new IllegalArgumentException("Unsupported time unit: " + symbol);
		};
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
		return "{ @type: %1$s, requestsLimit: %2$s, requestsRemaining: %3$s, requestsReset: %4$s, tokensLimit: %5$s; tokensRemaining: %6$s; tokensReset: %7$s }"
			.formatted(getClass().getName(), getRequestsLimit(), getRequestsRemaining(), getRequestsReset(),
					getTokensLimit(), getTokensRemaining(), getTokensReset());
	}

}
