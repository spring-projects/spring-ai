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

package org.springframework.ai.reader;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link ExtractedTextFormatter}.
 *
 * @author Dimitar Proynov
 */
class ExtractedTextFormatterTests {

	static Stream<Arguments> blankLineCases() {
		return Stream.of(Arguments.of("a\n\n\nb", "a\n\nb", "a\nb"), Arguments.of("a\n\nb", "a\n\nb", "a\nb"),
				Arguments.of("a\nb", "a\nb", "a\nb"), Arguments.of("a\n \n\nb", "a\n\nb", "a\nb"),
				Arguments.of("\n\na", "\na", "a"), Arguments.of("a\n\n", "a\n\n", "a\n\n"),
				Arguments.of("a\n\n\r\n\nb", "a\n\nb", "a\nb"), Arguments.of("a\r\n\r\n\r\nb", "a\r\n\r\nb", "a\r\nb"),
				Arguments.of("a\n\nb\n\nc", "a\n\nb\n\nc", "a\nb\nc"), Arguments.of("", "", ""),
				Arguments.of("a", "a", "a"), Arguments.of("a\n", "a\n", "a\n"));
	}

	@ParameterizedTest
	@MethodSource("blankLineCases")
	void trimAdjacentBlankLinesMatchesPreviousBehavior(String input, String expectedTrimmed, String expectedAligned) {
		assertThat(ExtractedTextFormatter.trimAdjacentBlankLines(input)).isEqualTo(expectedTrimmed);
	}

	@ParameterizedTest
	@MethodSource("blankLineCases")
	void alignToLeftMatchesPreviousBehavior(String input, String expectedTrimmed, String expectedAligned) {
		assertThat(ExtractedTextFormatter.alignToLeft(input)).isEqualTo(expectedAligned);
	}

	@ParameterizedTest
	@MethodSource("blankLineCases")
	void formatWithLeftAlignmentMatchesPreviousBehavior(String input, String expectedTrimmed, String expectedAligned) {
		// format() unconditionally trims blank lines first, then left-aligns the
		// already-trimmed result - not the raw input - when leftAlignment is set.
		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder().withLeftAlignment(true).build();
		String expected = ExtractedTextFormatter.alignToLeft(ExtractedTextFormatter.trimAdjacentBlankLines(input));
		assertThat(formatter.format(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@MethodSource("blankLineCases")
	void formatWithoutLeftAlignmentMatchesPreviousBehavior(String input, String expectedTrimmed,
			String expectedAligned) {
		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder().withLeftAlignment(false).build();
		assertThat(formatter.format(input)).isEqualTo(expectedTrimmed);
	}

	@ParameterizedTest
	@MethodSource("largeBlankRunSizes")
	void largeRunsOfBlankLinesDoNotFailAndStillCollapse(int blankLineCount) {
		String withContentAfter = "a\n" + "\n".repeat(blankLineCount) + "b";

		assertThatCode(() -> ExtractedTextFormatter.trimAdjacentBlankLines(withContentAfter))
			.doesNotThrowAnyException();
		assertThatCode(() -> ExtractedTextFormatter.alignToLeft(withContentAfter)).doesNotThrowAnyException();

		String trimmed = ExtractedTextFormatter.trimAdjacentBlankLines(withContentAfter);
		String aligned = ExtractedTextFormatter.alignToLeft(withContentAfter);

		assertThat(trimmed).isEqualTo("a\n\nb");
		assertThat(aligned).isEqualTo("a\nb");
	}

	static Stream<Integer> largeBlankRunSizes() {
		return Stream.of(50_000, 200_000);
	}

}
