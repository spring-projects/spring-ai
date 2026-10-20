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

import com.openai.core.http.Headers;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiRateLimit}.
 *
 * @author Raphael Vullriede
 */
class OpenAiRateLimitTests {

	@Test
	void fromReturnsEmptyRateLimitWhenNoHeadersPresent() {
		RateLimit rateLimit = OpenAiRateLimit.from(Headers.builder().build());

		assertThat(rateLimit).isInstanceOf(EmptyRateLimit.class);
	}

	@Test
	void fromReadsAllRateLimitHeaders() {
		Headers headers = Headers.builder()
			.put("x-ratelimit-limit-requests", "4000")
			.put("x-ratelimit-remaining-requests", "999")
			.put("x-ratelimit-reset-requests", "2d16h15m29s")
			.put("x-ratelimit-limit-tokens", "725000")
			.put("x-ratelimit-remaining-tokens", "112358")
			.put("x-ratelimit-reset-tokens", "27h55s451ms")
			.build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(OpenAiRateLimit.class);
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(4000L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(999L);
		assertThat(rateLimit.getRequestsReset())
			.isEqualTo(Duration.ofDays(2).plusHours(16).plusMinutes(15).plusSeconds(29));
		assertThat(rateLimit.getTokensLimit()).isEqualTo(725000L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(112358L);
		assertThat(rateLimit.getTokensReset()).isEqualTo(Duration.ofHours(27).plusSeconds(55).plusMillis(451));
	}

	@Test
	void fromLeavesAbsentHeadersNull() {
		Headers headers = Headers.builder().put("x-ratelimit-limit-requests", "1000").build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(OpenAiRateLimit.class);
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(1000L);
		assertThat(rateLimit.getRequestsRemaining()).isNull();
		assertThat(rateLimit.getRequestsReset()).isNull();
		assertThat(rateLimit.getTokensLimit()).isNull();
		assertThat(rateLimit.getTokensRemaining()).isNull();
		assertThat(rateLimit.getTokensReset()).isNull();
	}

	@Test
	void fromReturnsEmptyRateLimitWhenEveryValueIsUnparseable() {
		Headers headers = Headers.builder()
			.put("x-ratelimit-limit-requests", "invalid")
			.put("x-ratelimit-remaining-requests", "not-a-number")
			.put("x-ratelimit-reset-requests", "bad-duration")
			.build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(EmptyRateLimit.class);
	}

	@Test
	void fromReadsSingleUnitResetHeaders() {
		Headers headers = Headers.builder()
			.put("x-ratelimit-reset-requests", "1s")
			.put("x-ratelimit-reset-tokens", "88ms")
			.build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ofSeconds(1));
		assertThat(rateLimit.getTokensReset()).isEqualTo(Duration.ofMillis(88));
	}

	@Test
	void fromIgnoresResetHeadersThatOverflowADuration() {
		Headers headers = Headers.builder().put("x-ratelimit-reset-requests", "99999999999999999999d").build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(EmptyRateLimit.class);
	}

	@Test
	void fromReadsResetHeadersReportedAsPlainSeconds() {
		Headers headers = Headers.builder().put("x-ratelimit-reset-requests", "60").build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ofSeconds(60));
	}

	@Test
	void fromReadsFractionalResetHeaders() {
		Headers headers = Headers.builder()
			.put("x-ratelimit-reset-requests", "1.5s")
			.put("x-ratelimit-reset-tokens", "19m42.213s")
			.build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ofMillis(1500));
		assertThat(rateLimit.getTokensReset()).isEqualTo(Duration.ofMinutes(19).plusSeconds(42).plusMillis(213));
	}

	@Test
	void fromReadsMicrosecondResetHeaders() {
		Headers headers = Headers.builder()
			.put("x-ratelimit-reset-requests", "800µs")
			.put("x-ratelimit-reset-tokens", "800us")
			.build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit.getRequestsReset()).isEqualTo(Duration.ofNanos(800_000));
		assertThat(rateLimit.getTokensReset()).isEqualTo(Duration.ofNanos(800_000));
	}

	@Test
	void fromIgnoresResetHeadersCarryingUnexpectedText() {
		Headers headers = Headers.builder().put("x-ratelimit-reset-requests", "about 3s").build();

		RateLimit rateLimit = OpenAiRateLimit.from(headers);

		assertThat(rateLimit).isInstanceOf(EmptyRateLimit.class);
	}

}
