/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ThinkingTagCleaner}.
 *
 * @author liugddx
 */
class ThinkingTagCleanerTest {

	@Test
	void shouldRemoveAmazonNovaThinkingTags() {
		var cleaner = new ThinkingTagCleaner();
		String input = "<thinking>My reasoning process</thinking>Actual content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldRemoveQwenThinkTags() {
		var cleaner = new ThinkingTagCleaner();
		String input = "<think>Let me think about this</think>Actual content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldRemoveReasoningTags() {
		var cleaner = new ThinkingTagCleaner();
		String input = "<reasoning>Step by step reasoning</reasoning>Actual content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldRemoveMultilineThinkingTags() {
		var cleaner = new ThinkingTagCleaner();
		String input = """
				<thinking>
				Line 1 of thinking
				Line 2 of thinking
				</thinking>
				Actual content""";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldRemoveMultipleThinkingTags() {
		var cleaner = new ThinkingTagCleaner();
		String input = "<thinking>First</thinking><think>Second</think><reasoning>Third</reasoning>Actual content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldBeCaseInsensitive() {
		var cleaner = new ThinkingTagCleaner();
		String input = "<THINKING>UPPER CASE</THINKING>Actual content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldRemoveMarkdownThinkingBlocks() {
		var cleaner = new ThinkingTagCleaner();
		String input = """
				```thinking
				This is markdown thinking
				```
				Actual content""";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldHandleEmptyInput() {
		var cleaner = new ThinkingTagCleaner();
		assertThat(cleaner.clean("")).isEmpty();
		assertThat(cleaner.clean(null)).isNull();
	}

	@Test
	void shouldHandleContentWithoutTags() {
		var cleaner = new ThinkingTagCleaner();
		String input = "Just regular content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo(input);
	}

	@Test
	void shouldSupportCustomPatterns() {
		var cleaner = new ThinkingTagCleaner("(?s)<custom>.*?</custom>\\s*");
		String input = "<custom>Custom tag content</custom>Actual content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Actual content");
	}

	@Test
	void shouldSupportBuilderWithoutDefaultPatterns() {
		var cleaner = ThinkingTagCleaner.builder()
			.withoutDefaultPatterns()
			.addPattern("(?s)<mytag>.*?</mytag>\\s*")
			.build();

		String input = "<thinking>Should remain</thinking><mytag>Should be removed</mytag>Content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("<thinking>Should remain</thinking>Content");
	}

	@Test
	void shouldSupportBuilderWithAdditionalPatterns() {
		var cleaner = ThinkingTagCleaner.builder().addPattern("(?s)<custom>.*?</custom>\\s*").build();

		String input = "<thinking>Removed</thinking><custom>Also removed</custom>Content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("Content");
	}

	@Test
	void shouldThrowExceptionWhenPatternsAreNull() {
		assertThatThrownBy(() -> new ThinkingTagCleaner((String[]) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("patternStrings cannot be null");
	}

	@Test
	void shouldThrowExceptionWhenPatternsAreEmpty() {
		assertThatThrownBy(() -> new ThinkingTagCleaner(new String[0])).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("patternStrings cannot be empty");
	}

}
