/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.ollama.autoconfigure;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.ollama.api.ThinkOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * @author Nicolas Krier
 * @since 1.1.3
 */
class ThinkOptionConverterTests {

	private final ThinkOptionConverter converter = new ThinkOptionConverter();

	@Test
	void verifyUnsupportedConversion() {
		assertThatIllegalStateException().isThrownBy(() -> this.converter.convert("ABC"))
			.withMessage("Unexpected think option value: ABC");
	}

	@ParameterizedTest
	@MethodSource("thinkBooleanConversionParameters")
	void verifyThinkBooleanConversion(String source, ThinkOption.ThinkBoolean expectedTarget) {
		assertThat(this.converter.convert(source)).isEqualTo(expectedTarget);
	}

	@ParameterizedTest
	@MethodSource("thinkLevelConversionParameters")
	void verifyThinkLevelConversion(String source, ThinkOption.ThinkLevel expectedTarget) {
		assertThat(this.converter.convert(source)).isEqualTo(expectedTarget);
	}

	private static Stream<Arguments> thinkBooleanConversionParameters() {
		return Stream.of(Arguments.of("enabled", ThinkOption.ThinkBoolean.ENABLED),
				Arguments.of("disabled", ThinkOption.ThinkBoolean.DISABLED));
	}

	private static Stream<Arguments> thinkLevelConversionParameters() {
		return Stream.of(Arguments.of("low", ThinkOption.ThinkLevel.LOW),
				Arguments.of("medium", ThinkOption.ThinkLevel.MEDIUM),
				Arguments.of("high", ThinkOption.ThinkLevel.HIGH));
	}

}
