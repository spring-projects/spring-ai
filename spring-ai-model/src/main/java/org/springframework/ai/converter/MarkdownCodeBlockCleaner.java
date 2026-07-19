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

import org.jspecify.annotations.Nullable;

/**
 * A {@link ResponseTextCleaner} that removes markdown code block formatting from LLM
 * responses. This cleaner handles:
 * <ul>
 * <li>{@code ```json ... ```}</li>
 * <li>{@code ``` ... ```}</li>
 * <li>{@code ```json ...}</li>
 * <li>{@code ``` ...}</li>
 * </ul>
 *
 * @author liugddx
 * @since 1.1.0
 */
public class MarkdownCodeBlockCleaner implements ResponseTextCleaner {

	@Override
	public @Nullable String clean(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		// Trim leading and trailing whitespace first
		text = text.trim();

		// Check for and remove triple backticks
		if (text.startsWith("```")) {
			String[] lines = text.split("\n", 2);
			String firstLine = lines[0].trim();
			if (lines.length > 1) {
				// Has the shape like ```[json] and content on following lines (captured
				// in lines[1])
				text = lines[1];
			}
			else {
				// Single-line fenced block without line break, e.g.
				// ```{"key": "value"}```
				// Not a correct fenced block per-se, but can happen in practice
				// Strip the opening fence only; the trailing fence is removed below
				text = firstLine.substring(3);
			}

			// Remove trailing ```
			if (text.endsWith("```")) {
				text = text.substring(0, text.length() - 3);
			}

			// Trim again to remove any potential whitespace
			text = text.trim();
		}

		return text;
	}

}
