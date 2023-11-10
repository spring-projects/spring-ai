/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.openai.client.metadata.support;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.restassured.path.json.JsonPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.metadata.RateLimit;
import org.springframework.ai.openai.client.metadata.OpenAiMetadata;
import org.springframework.ai.openai.client.metadata.OpenAiRateLimit;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp {@link Interceptor} implementation used capture the AI HTTP response headers
 * from {@literal OpenAI} API.
 *
 * @author John Blum
 * @since 0.7.0
 */
public class OpenAiHttpResponseHeadersInterceptor implements Interceptor {

	protected static final String REQUESTS_LIMIT_FIELD = "x-ratelimit-limit-requests";

	protected static final String REQUESTS_REMAINING_FIELD = "x-ratelimit-remaining-requests";

	protected static final String REQUESTS_RESET_FIELD = "x-ratelimit-reset-requests";

	protected static final String TOKENS_RESET_FIELD = "x-ratelimit-reset-tokens";

	protected static final String TOKENS_LIMIT_FIELD = "x-ratelimit-limit-tokens";

	protected static final String TOKENS_REMAINING_FIELD = "x-ratelimit-remaining-tokens";

	private static final Map<String, OpenAiRateLimit> cache = Collections.synchronizedMap(new WeakHashMap<>());

	public static void applyTo(OpenAiMetadata metadata) {

		String id = metadata.getId();

		synchronized (cache) {
			metadata.withRateLimit(cache.get(id));
			cache.remove(id);
		}
	}

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public Response intercept(Chain chain) throws IOException {

		Request request = chain.request();
		Response response = chain.proceed(request);

		cacheAiResponseHeaders(response);

		return response;
	}

	protected Logger getLogger() {
		return this.logger;
	}

	private RateLimit cacheAiResponseHeaders(Response response) {

		String id = parseAiResponseId(response);

		OpenAiRateLimit rateLimit = StringUtils.hasText(id) ? cache.computeIfAbsent(id, key -> {

			Long requestsLimit = getHeaderAsLong(response, REQUESTS_LIMIT_FIELD);
			Long requestsRemaining = getHeaderAsLong(response, REQUESTS_REMAINING_FIELD);
			Long tokensLimit = getHeaderAsLong(response, TOKENS_LIMIT_FIELD);
			Long tokensRemaining = getHeaderAsLong(response, TOKENS_REMAINING_FIELD);

			Duration requestsReset = getHeaderAsDuration(response, REQUESTS_RESET_FIELD);
			Duration tokensReset = getHeaderAsDuration(response, TOKENS_RESET_FIELD);

			return new OpenAiRateLimit(requestsLimit, requestsRemaining, requestsReset, tokensLimit, tokensRemaining,
					tokensReset);
		}) : null;

		return rateLimit;
	}

	private Duration getHeaderAsDuration(Response response, String headerName) {
		String headerValue = response.header(headerName);
		return DurationFormatter.TIME_UNIT.parse(headerValue);
	}

	private Long getHeaderAsLong(Response response, String headerName) {
		String headerValue = response.header(headerName);
		return parseLong(headerName, headerValue);
	}

	private String parseAiResponseId(Response response) {

		try {
			ResponseBody responseBody = response.body();
			return responseBody != null ? JsonPath.with(responseBody.string()).getString("id") : null;
		}
		catch (Exception e) {
			getLogger().warn("Unable to get AI response body as a String: {}", e.getMessage());
			return null;
		}
	}

	private Long parseLong(String headerName, String headerValue) {

		if (StringUtils.hasText(headerValue)) {
			try {
				return Long.parseLong(headerValue.trim());
			}
			catch (NumberFormatException e) {
				getLogger().warn("Value [{}] for HTTP header [{}] is not valid: {}", headerName, headerValue,
						e.getMessage());
			}
		}

		return null;
	}

	enum DurationFormatter {

		TIME_UNIT("\\d+[a-zA-Z]{1,2}");

		private final Pattern pattern;

		DurationFormatter(String durationPattern) {
			this.pattern = Pattern.compile(durationPattern);
		}

		public Duration parse(String text) {

			Assert.hasText(text, "Text [%s] to parse as a Duration must not be null or empty".formatted(text));

			Matcher matcher = this.pattern.matcher(text);
			Duration total = Duration.ZERO;

			while (matcher.find()) {
				String value = matcher.group();
				total = total.plus(Unit.parseUnit(value).toDuration(value));
			}

			return total;
		}

		enum Unit {

			NANOSECONDS("ns", ChronoUnit.NANOS), MICROSECONDS("us", ChronoUnit.MICROS),
			MILLISECONDS("ms", ChronoUnit.MILLIS), SECONDS("s", ChronoUnit.SECONDS), MINUTES("m", ChronoUnit.MINUTES),
			HOURS("h", ChronoUnit.HOURS), DAYS("d", ChronoUnit.DAYS);

			private final String symbol;

			private final ChronoUnit unit;

			Unit(String symbol, ChronoUnit unit) {
				this.symbol = symbol;
				this.unit = unit;
			}

			static Unit parseUnit(String value) {
				String symbol = parseSymbol(value);
				return Arrays.stream(values())
					.filter(unit -> unit.getSymbol().equalsIgnoreCase(symbol))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"Value [%s] does not contain a valid time unit".formatted(value)));
			}

			private static String parse(String value, Predicate<Character> predicate) {
				Assert.hasText(value, "Value [%s] must not be null or empty".formatted(value));
				StringBuilder builder = new StringBuilder();
				for (char character : value.toCharArray()) {
					if (predicate.test(character)) {
						builder.append(character);
					}
				}
				return builder.toString();
			}

			private static String parseSymbol(String value) {
				return parse(value, Character::isLetter);
			}

			private static Long parseTime(String value) {
				return Long.parseLong(parse(value, Character::isDigit));
			}

			public String getSymbol() {
				return this.symbol;
			}

			public ChronoUnit getUnit() {
				return this.unit;
			}

			public Duration toDuration(String value) {
				return Duration.of(parseTime(value), getUnit());
			}

		}

	}

}
