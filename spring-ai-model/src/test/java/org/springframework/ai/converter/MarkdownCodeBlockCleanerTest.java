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
 * @author gaoxiaolei-s59
 */
class MarkdownCodeBlockCleanerTest {

	private final MarkdownCodeBlockCleaner cleaner = new MarkdownCodeBlockCleaner();

	@Test
	void shouldRemoveFencedBlockWithLanguageIdentifier() {
		String input = """
				```json
				{"key": "value"}
				```""";
		assertThat(this.cleaner.clean(input)).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldRemoveFencedBlockWithoutLanguageIdentifier() {
		String input = """
				```
				{"key": "value"}
				```""";
		assertThat(this.cleaner.clean(input)).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldPreserveMultiLineJsonStructure() {
		String input = """
				```json
				{
					"key": "value"
				}
				```""";
		assertThat(this.cleaner.clean(input)).isEqualTo("{\n\t\"key\": \"value\"\n}");
	}

	@Test
	void shouldTrimSurroundingWhitespaceAroundFencedBlock() {
		String input = "  ```json\n{\"key\": \"value\"}\n```  ";
		assertThat(this.cleaner.clean(input)).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldPreserveContentForSingleLineBlockWithoutLanguageIdentifier() {
		// A fenced block that fits on a single line (e.g. ```{"key":"value"}```) has no
		// separate language-identifier line: its content must not be discarded.
		String input = "```{\"key\": \"value\"}```";
		assertThat(this.cleaner.clean(input)).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldReturnTextUnchangedWhenNoCodeBlockPresent() {
		String input = "{\"key\": \"value\"}";
		assertThat(this.cleaner.clean(input)).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldReturnNullWhenInputIsNull() {
		assertThat(this.cleaner.clean(null)).isNull();
	}

	@Test
	void shouldReturnEmptyStringWhenInputIsEmpty() {
		assertThat(this.cleaner.clean("")).isEmpty();
	}

}
