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

package org.springframework.ai.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MarkdownCodeBlockCleaner}.
 *
 * @author MKP999
 */
class MarkdownCodeBlockCleanerTest {

	private final MarkdownCodeBlockCleaner cleaner = new MarkdownCodeBlockCleaner();

	@Test
	void shouldRemoveMultilineCodeBlockWithLanguageIdentifier() {
		String input = "```json\n{\"key\": \"value\"}\n```";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldRemoveMultilineCodeBlockWithoutLanguageIdentifier() {
		String input = "```\n{\"key\": \"value\"}\n```";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldRemoveSingleLineCodeBlockWithLanguageIdentifier() {
		String input = "```json{\"key\": \"value\"}```";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldRemoveSingleLineCodeBlockWithoutLanguageIdentifier() {
		String input = "```{\"key\": \"value\"}```";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldReturnNullForNullInput() {
		assertThat(cleaner.clean(null)).isNull();
	}

	@Test
	void shouldReturnEmptyForEmptyInput() {
		assertThat(cleaner.clean("")).isEmpty();
	}

	@Test
	void shouldNotModifyTextWithoutCodeBlocks() {
		String input = "plain text without code blocks";
		assertThat(cleaner.clean(input)).isEqualTo(input);
	}

}
