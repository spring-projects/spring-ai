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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor.DurationFormatter;

/**
 * Unit Tests for {@link OpenAiHttpResponseHeadersInterceptor}.
 *
 * @author John Blum
 * @author Christian Tzolov
 * @since 0.7.0
 */
public class OpenAiResponseHeaderExtractorTests {

	@Test
	void parseTimeAsDurationWithDaysHoursMinutesSeconds() {

		Duration actual = DurationFormatter.TIME_UNIT.parse("6d18h22m45s");
		Duration expected = Duration.ofDays(6L)
			.plus(Duration.ofHours(18L))
			.plus(Duration.ofMinutes(22))
			.plus(Duration.ofSeconds(45L));

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void parseTimeAsDurationWithMinutesSecondsMicrosecondsAndMicroseconds() {

		Duration actual = DurationFormatter.TIME_UNIT.parse("42m18s451ms21541ns");
		Duration expected = Duration.ofMinutes(42L)
			.plus(Duration.ofSeconds(18L))
			.plus(Duration.ofMillis(451))
			.plus(Duration.ofNanos(21541L));

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void parseTimeAsDurationWithDaysMinutesAndMilliseconds() {

		Duration actual = DurationFormatter.TIME_UNIT.parse("2d15m981ms");
		Duration expected = Duration.ofDays(2L).plus(Duration.ofMinutes(15L)).plus(Duration.ofMillis(981L));

		assertThat(actual).isEqualTo(expected);
	}

}
