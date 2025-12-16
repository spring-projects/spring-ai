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

import org.jspecify.annotations.Nullable;

/**
 * A {@link ResponseTextCleaner} that removes markdown code block formatting from LLM
 * responses. This cleaner handles:
 * <ul>
 * <li>{@code ```json ... ```}</li>
 * <li>{@code ``` ... ```}</li>
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
		if (text.startsWith("```") && text.endsWith("```")) {
			// Remove the first line if it contains "```json" or similar
			String[] lines = text.split("\n", 2);
			if (lines[0].trim().toLowerCase().startsWith("```")) {
				// Extract language identifier if present
				String firstLine = lines[0].trim();
				if (firstLine.length() > 3) {
					// Has language identifier like ```json
					text = lines.length > 1 ? lines[1] : "";
				}
				else {
					// Just ``` without language
					text = text.substring(3);
				}
			}
			else {
				text = text.substring(3);
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
