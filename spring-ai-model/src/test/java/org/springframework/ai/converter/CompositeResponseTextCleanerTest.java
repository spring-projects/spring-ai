/*
 * Copyright 2025-2026 the original author or authors.
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
 * Unit tests for {@link CompositeResponseTextCleaner}.
 *
 * @author liugddx
 */
class CompositeResponseTextCleanerTest {

	@Test
	void shouldApplyCleanersInOrder() {
		var cleaner = CompositeResponseTextCleaner.builder()
			.addCleaner(text -> text.replace("A", "B"))
			.addCleaner(text -> text.replace("B", "C"))
			.build();

		String result = cleaner.clean("AAA");
		assertThat(result).isEqualTo("CCC");
	}

	@Test
	void shouldWorkWithSingleCleaner() {
		var cleaner = new CompositeResponseTextCleaner(String::trim);
		String result = cleaner.clean("  content  ");
		assertThat(result).isEqualTo("content");
	}

	@Test
	void shouldWorkWithMultipleCleaners() {
		var cleaner = new CompositeResponseTextCleaner(new WhitespaceCleaner(), new ThinkingTagCleaner(),
				new MarkdownCodeBlockCleaner());

		String input = """
				<thinking>Reasoning</thinking>
				```json
				{"key": "value"}
				```
				""";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	void shouldHandleComplexPipeline() {
		var cleaner = CompositeResponseTextCleaner.builder()
			.addCleaner(new WhitespaceCleaner())
			.addCleaner(new ThinkingTagCleaner())
			.addCleaner(new MarkdownCodeBlockCleaner())
			.addCleaner(new WhitespaceCleaner())
			.build();

		String input = """

				<thinking>Let me analyze this</thinking>
				<think>Qwen style thinking</think>

				```json
				{
					"result": "test"
				}
				```

				""";

		String result = cleaner.clean(input);
		assertThat(result).isEqualTo("{\n\t\"result\": \"test\"\n}");
	}

	@Test
	void shouldThrowExceptionWhenCleanersIsNull() {
		assertThatThrownBy(() -> CompositeResponseTextCleaner.builder().addCleaner(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("cleaner cannot be null");
	}

	@Test
	void shouldHandleEmptyCleanersList() {
		var cleaner = new CompositeResponseTextCleaner();
		String input = "test content";
		String result = cleaner.clean(input);
		assertThat(result).isEqualTo(input);
	}

}
