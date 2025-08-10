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

package org.springframework.ai.rag.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.Assert;

/**
 * Assertion utility class that assists in validating arguments for prompt-related
 * operations.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class PromptAssert {

	private PromptAssert() {
	}

	/**
	 * Assert that the given prompt template contains the required placeholders.
	 * @param promptTemplate the prompt template to check
	 * @param placeholders the placeholders that must be present in the prompt template
	 */
	public static void templateHasRequiredPlaceholders(PromptTemplate promptTemplate, String... placeholders) {
		Assert.notNull(promptTemplate, "promptTemplate cannot be null");
		Assert.notEmpty(placeholders, "placeholders cannot be null or empty");

		List<String> missingPlaceholders = new ArrayList<>();
		for (String placeholder : placeholders) {
			if (!promptTemplate.getTemplate().contains(placeholder)) {
				missingPlaceholders.add(placeholder);
			}
		}

		if (!missingPlaceholders.isEmpty()) {
			throw new IllegalArgumentException("The following placeholders must be present in the prompt template: %s"
				.formatted(String.join(",", missingPlaceholders)));
		}
	}

}
