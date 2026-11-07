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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link ExtractedTextFormatter} class.
 *
 * @author LeiXiaoGao
 */
class ExtractedTextFormatterTests {

	@Test
	void trimAdjacentBlankLinesCollapsesConsecutiveBlankLines() {
		assertThat(ExtractedTextFormatter.trimAdjacentBlankLines("line1\n\n\n\nline2")).isEqualTo("line1\n\nline2");
	}

	@Test
	void trimAdjacentBlankLinesTreatsSpaceOnlyLinesAsBlank() {
		assertThat(ExtractedTextFormatter.trimAdjacentBlankLines("line1\n   \n  \nline2")).isEqualTo("line1\n\nline2");
	}

	@Test
	void trimAdjacentBlankLinesKeepsSingleBlankLine() {
		assertThat(ExtractedTextFormatter.trimAdjacentBlankLines("line1\n\nline2")).isEqualTo("line1\n\nline2");
	}

	@Test
	void alignToLeftStripsLeadingSpaces() {
		assertThat(ExtractedTextFormatter.alignToLeft("   line1\n  line2")).isEqualTo("line1\nline2");
	}

	@Test
	void alignToLeftCollapsesInnerSpaceRunsAndStripsTrailingSpaces() {
		assertThat(ExtractedTextFormatter.alignToLeft("hello   world  ")).isEqualTo("hello world");
	}

	@Test
	void deleteBottomTextLinesRemovesRequestedLines() {
		assertThat(ExtractedTextFormatter.deleteBottomTextLines("L1\nL2\nL3", 1, "\n")).isEqualTo("L1\nL2");
		assertThat(ExtractedTextFormatter.deleteBottomTextLines("L1\nL2\nL3", 2, "\n")).isEqualTo("L1");
	}

	@Test
	void deleteBottomTextLinesKeepsFirstLineWhenAskedToRemoveMoreLinesThanPresent() {
		assertThat(ExtractedTextFormatter.deleteBottomTextLines("L1\nL2\nL3", 5, "\n")).isEqualTo("L1");
	}

	@Test
	void deleteBottomTextLinesReturnsBlankInputUnchanged() {
		assertThat(ExtractedTextFormatter.deleteBottomTextLines("", 2, "\n")).isEmpty();
		assertThat(ExtractedTextFormatter.deleteBottomTextLines("   ", 2, "\n")).isEqualTo("   ");
		assertThat(ExtractedTextFormatter.deleteBottomTextLines(null, 2, "\n")).isNull();
	}

	@Test
	void deleteBottomTextLinesHonorsCustomLineSeparator() {
		assertThat(ExtractedTextFormatter.deleteBottomTextLines("L1\r\nL2\r\nL3", 1, "\r\n")).isEqualTo("L1\r\nL2");
	}

	@Test
	void deleteTopTextLinesRemovesRequestedLinesKeepingSeparator() {
		assertThat(ExtractedTextFormatter.deleteTopTextLines("L1\nL2\nL3", 1, "\n")).isEqualTo("\nL2\nL3");
	}

	@Test
	void deleteTopTextLinesStopsAtLastLineWhenAskedToRemoveMoreLinesThanPresent() {
		assertThat(ExtractedTextFormatter.deleteTopTextLines("L1\nL2\nL3", 5, "\n")).isEqualTo("\nL3");
	}

	@Test
	void deleteTopTextLinesReturnsBlankInputUnchanged() {
		assertThat(ExtractedTextFormatter.deleteTopTextLines("", 2, "\n")).isEmpty();
		assertThat(ExtractedTextFormatter.deleteTopTextLines(null, 2, "\n")).isNull();
	}

	@Test
	void defaultFormatterOnlyCollapsesBlankLines() {
		ExtractedTextFormatter formatter = ExtractedTextFormatter.defaults();

		assertThat(formatter.format("  line1\n\n\n\nline2")).isEqualTo("  line1\n\nline2");
	}

	@Test
	void formatWithLeftAlignmentAlignsText() {
		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder().withLeftAlignment(true).build();

		assertThat(formatter.format("   line1\n  line2")).isEqualTo("line1\nline2");
	}

	@Test
	void formatDeletesTopAndBottomLines() {
		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder()
			.overrideLineSeparator("\n")
			.withNumberOfTopTextLinesToDelete(1)
			.withNumberOfBottomTextLinesToDelete(1)
			.build();

		assertThat(formatter.format("header\nbody\nfooter")).isEqualTo("\nbody");
	}

	@Test
	void formatSkipsDeletionForPagesBeforeSkipThreshold() {
		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder()
			.overrideLineSeparator("\n")
			.withNumberOfTopTextLinesToDelete(1)
			.withNumberOfTopPagesToSkipBeforeDelete(2)
			.build();

		assertThat(formatter.format("header\nbody", 0)).isEqualTo("header\nbody");
		assertThat(formatter.format("header\nbody", 1)).isEqualTo("header\nbody");
		assertThat(formatter.format("header\nbody", 2)).isEqualTo("\nbody");
	}

	@Test
	void formatWithoutPageNumberAppliesDeletionAsPageZero() {
		ExtractedTextFormatter deleting = ExtractedTextFormatter.builder()
			.overrideLineSeparator("\n")
			.withNumberOfTopTextLinesToDelete(1)
			.build();
		ExtractedTextFormatter skipping = ExtractedTextFormatter.builder()
			.overrideLineSeparator("\n")
			.withNumberOfTopTextLinesToDelete(1)
			.withNumberOfTopPagesToSkipBeforeDelete(1)
			.build();

		assertThat(deleting.format("header\nbody")).isEqualTo("\nbody");
		assertThat(skipping.format("header\nbody")).isEqualTo("header\nbody");
	}

	@Test
	void formatCombinesBlankLineTrimmingDeletionAndAlignment() {
		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder()
			.overrideLineSeparator("\n")
			.withNumberOfTopTextLinesToDelete(1)
			.withLeftAlignment(true)
			.build();

		assertThat(formatter.format("header\n\n\n\n   indented body")).isEqualTo("indented body");
	}

	@Test
	void builderReturnsFreshInstances() {
		assertThat(ExtractedTextFormatter.builder()).isNotSameAs(ExtractedTextFormatter.builder());
		assertThat(ExtractedTextFormatter.defaults()).isNotSameAs(ExtractedTextFormatter.defaults());
	}

}
