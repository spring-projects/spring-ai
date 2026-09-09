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

package org.springframework.ai.gpullama3;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Parses GPULlama3 response text into visible content and optional thinking content.
 * <p>
 * This parser is stateless and thread-safe. If a {@code <think>} block is not closed, the
 * text before the opening tag is treated as visible content and the text after it is
 * treated as thinking content. This preserves partially generated thinking text when
 * generation stops because of a length limit.
 *
 * @since 2.0.1
 */
public final class GpuLlama3ResponseParser {

	private static final String THINK_OPEN = "<think>";

	private static final String THINK_CLOSE = "</think>";

	public ParsedResponse parse(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return new ParsedResponse("", null);
		}

		// Parse the text into visible content and thinking blocks
		StringBuilder content = new StringBuilder();
		List<String> thinkingBlocks = new ArrayList<>();

		int cursor = 0;
		boolean done = false;
		while (!done && cursor < text.length()) {
			int openIndex = text.indexOf(THINK_OPEN, cursor);

			if (openIndex < 0) {
				content.append(text, cursor, text.length());
				done = true;
			}
			else {
				int thinkingStart = openIndex + THINK_OPEN.length();
				int closeIndex = text.indexOf(THINK_CLOSE, thinkingStart);

				content.append(text, cursor, openIndex);

				if (closeIndex < 0) {
					addThinkingBlock(thinkingBlocks, text.substring(thinkingStart));
					done = true;
				}
				else {
					addThinkingBlock(thinkingBlocks, text.substring(thinkingStart, closeIndex));
					cursor = closeIndex + THINK_CLOSE.length();
				}
			}
		}

		String parsedContent = content.toString().trim();
		String parsedThinking = thinkingBlocks.isEmpty() ? null : String.join("\n\n", thinkingBlocks);

		return new ParsedResponse(parsedContent, parsedThinking);
	}

	private static void addThinkingBlock(List<String> thinkingBlocks, String thinking) {
		String trimmedThinking = thinking.trim();
		if (!trimmedThinking.isEmpty()) {
			thinkingBlocks.add(trimmedThinking);
		}
	}

	/**
	 * Parsed GPULlama3 response content and optional thinking text.
	 *
	 * @since 2.0.1
	 */
	public record ParsedResponse(String content, @Nullable String thinking) {
	}

}
