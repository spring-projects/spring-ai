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

package org.springframework.ai.openai.metadata.support;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.metadata.OpenAiRateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders.REQUESTS_LIMIT_HEADER;
import static org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders.REQUESTS_REMAINING_HEADER;
import static org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders.REQUESTS_RESET_HEADER;
import static org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders.TOKENS_LIMIT_HEADER;
import static org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders.TOKENS_REMAINING_HEADER;
import static org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders.TOKENS_RESET_HEADER;

/**
 * Utility used to extract known HTTP response headers for the {@literal OpenAI} API.
 *
 * @author John Blum
 * @author Christian Tzolov
 * @since 0.7.0
 */
public class OpenAiResponseHeaderExtractor {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiResponseHeaderExtractor.class);

	public static RateLimit extractAiResponseHeaders(ResponseEntity<?> response) {

		Long requestsLimit = getHeaderAsLong(response, REQUESTS_LIMIT_HEADER.getName());
		Long requestsRemaining = getHeaderAsLong(response, REQUESTS_REMAINING_HEADER.getName());
		Long tokensLimit = getHeaderAsLong(response, TOKENS_LIMIT_HEADER.getName());
		Long tokensRemaining = getHeaderAsLong(response, TOKENS_REMAINING_HEADER.getName());

		Duration requestsReset = getHeaderAsDuration(response, REQUESTS_RESET_HEADER.getName());
		Duration tokensReset = getHeaderAsDuration(response, TOKENS_RESET_HEADER.getName());

		return new OpenAiRateLimit(requestsLimit, requestsRemaining, requestsReset, tokensLimit, tokensRemaining,
				tokensReset);
	}

	private static Duration getHeaderAsDuration(ResponseEntity<?> response, String headerName) {
		var headers = response.getHeaders();
		if (headers.containsKey(headerName)) {
			var values = headers.get(headerName);
			if (!CollectionUtils.isEmpty(values)) {
				return DurationFormatter.TIME_UNIT.parse(values.get(0));
			}
		}
		return null;
	}

	private static Long getHeaderAsLong(ResponseEntity<?> response, String headerName) {
		var headers = response.getHeaders();
		if (headers.containsKey(headerName)) {
			var values = headers.get(headerName);
			if (!CollectionUtils.isEmpty(values)) {
				return parseLong(headerName, values.get(0));
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
				logger.warn("Value [{}] for HTTP header [{}] is not valid: {}", headerName, headerValue,
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

			NANOSECONDS("ns", "nanoseconds", ChronoUnit.NANOS), MICROSECONDS("us", "microseconds", ChronoUnit.MICROS),
			MILLISECONDS("ms", "milliseconds", ChronoUnit.MILLIS), SECONDS("s", "seconds", ChronoUnit.SECONDS),
			MINUTES("m", "minutes", ChronoUnit.MINUTES), HOURS("h", "hours", ChronoUnit.HOURS),
			DAYS("d", "days", ChronoUnit.DAYS);

			private final String name;

			private final String symbol;

			private final ChronoUnit unit;

			Unit(String symbol, String name, ChronoUnit unit) {
				this.symbol = symbol;
				this.name = name;
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

			public String getName() {
				return this.name;
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
