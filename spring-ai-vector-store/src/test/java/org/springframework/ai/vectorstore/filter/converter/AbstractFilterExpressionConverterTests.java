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

package org.springframework.ai.vectorstore.filter.converter;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link AbstractFilterExpressionConverter}.
 */
class AbstractFilterExpressionConverterTests {

	@Test
	void normalizeDateStringWithoutFraction() {
		assertThat(AbstractFilterExpressionConverter.normalizeDateString("2024-01-15T10:30:00Z"))
			.isEqualTo(Date.from(Instant.parse("2024-01-15T10:30:00Z")));
	}

	@Test
	void normalizeDateStringWithMilliseconds() {
		assertThat(AbstractFilterExpressionConverter.normalizeDateString("2024-01-15T10:30:00.123Z"))
			.isEqualTo(Date.from(Instant.parse("2024-01-15T10:30:00.123Z")));
	}

	@Test
	void normalizeDateStringWithShortFraction() {
		assertThat(AbstractFilterExpressionConverter.normalizeDateString("2024-01-15T10:30:00.5Z"))
			.isEqualTo(Date.from(Instant.parse("2024-01-15T10:30:00.500Z")));
	}

	@Test
	void normalizeDateStringWithMicroseconds() {
		assertThat(AbstractFilterExpressionConverter.normalizeDateString("2024-01-15T10:30:00.123456Z"))
			.isEqualTo(Date.from(Instant.parse("2024-01-15T10:30:00.123Z")));
	}

	@Test
	void normalizeDateStringWithNanoseconds() {
		assertThat(AbstractFilterExpressionConverter.normalizeDateString("2024-01-15T10:30:00.123456789Z"))
			.isEqualTo(Date.from(Instant.parse("2024-01-15T10:30:00.123Z")));
	}

	@Test
	void normalizeDateStringRejectsInvalidDate() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AbstractFilterExpressionConverter.normalizeDateString("2024-13-45T10:30:00Z"))
			.withMessage("Invalid date type: 2024-13-45T10:30:00Z");
	}

	@Test
	void normalizeDateStringLeavesOtherValuesUnchanged() {
		assertThat(AbstractFilterExpressionConverter.normalizeDateString("2024-01-15")).isEqualTo("2024-01-15");
		assertThat(AbstractFilterExpressionConverter.normalizeDateString(42)).isEqualTo(42);
	}

}
