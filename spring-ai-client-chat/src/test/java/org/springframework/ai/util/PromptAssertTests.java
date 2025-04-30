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

package org.springframework.ai.util;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PromptAssert}.
 *
 * @author Thomas Vitale
 */
class PromptAssertTests {

	@Test
	void whenPlaceholderIsPresentThenOk() {
		var promptTemplate = new PromptTemplate("Hello, {name}!");
		PromptAssert.templateHasRequiredPlaceholders(promptTemplate, "{name}");
	}

	@Test
	void whenPlaceholderIsPresentThenThrow() {
		PromptTemplate promptTemplate = new PromptTemplate("Hello, {name}!");
		assertThatThrownBy(() -> PromptAssert.templateHasRequiredPlaceholders(promptTemplate, "{name}", "{age}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("age");
	}

	@Test
	void whenPromptTemplateIsNullThenThrow() {
		assertThatThrownBy(() -> PromptAssert.templateHasRequiredPlaceholders(null, "{name}"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("promptTemplate cannot be null");
	}

	@Test
	void whenPlaceholdersIsNullThenThrow() {
		assertThatThrownBy(
				() -> PromptAssert.templateHasRequiredPlaceholders(new PromptTemplate("{query}"), (String[]) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("placeholders cannot be null or empty");
	}

	@Test
	void whenPlaceholdersIsEmptyThenThrow() {
		assertThatThrownBy(() -> PromptAssert.templateHasRequiredPlaceholders(new PromptTemplate("{query}")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("placeholders cannot be null or empty");
	}

}
