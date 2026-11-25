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

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.Nullable;

/**
 * A {@link ResponseTextCleaner} that removes text surrounding a JSON object or array in
 * LLM responses, such as a preamble like {@code Here are the results:} or a closing
 * remark like {@code Hope this helps!}.
 * <p>
 * The cleaner keeps the first balanced JSON object or array found in the text. Braces,
 * brackets and escaped quotes inside string values are ignored while looking for the end
 * of the JSON. Text that does not contain such a JSON value, or that starts with a JSON
 * string or number, is returned unchanged so that the JSON parser reports the original
 * error.
 *
 * @author welikeWatermelon
 * @since 2.1.1
 */
public class JsonSurroundingTextCleaner implements ResponseTextCleaner {

	@Override
	public @Nullable String clean(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		String trimmed = text.trim();
		if (trimmed.isEmpty() || startsWithJsonScalar(trimmed)) {
			return text;
		}

		// Fast path: the text is already a single JSON object or array
		char first = trimmed.charAt(0);
		if ((first == '{' || first == '[') && findClosingIndex(trimmed, 0) == trimmed.length() - 1) {
			return text;
		}

		for (int start = 0; start < trimmed.length(); start++) {
			char c = trimmed.charAt(start);
			if ((c == '{' || c == '[') && looksLikeJsonStart(trimmed, start)) {
				int end = findClosingIndex(trimmed, start);
				// Keep the text if the outermost JSON is truncated or malformed, rather
				// than returning an object nested in it
				return (end != -1) ? trimmed.substring(start, end + 1) : text;
			}
		}

		return text;
	}

	private static boolean startsWithJsonScalar(String text) {
		char first = text.charAt(0);
		return first == '"' || first == '-' || Character.isDigit(first);
	}

	/**
	 * Checks that the character following the opening brace or bracket can start the
	 * content of a JSON object or array, to skip text such as {@code {the}}.
	 */
	private static boolean looksLikeJsonStart(String text, int start) {
		int next = start + 1;
		while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
			next++;
		}
		if (next >= text.length()) {
			return false;
		}
		char c = text.charAt(next);
		if (text.charAt(start) == '{') {
			return c == '"' || c == '\'' || c == '}';
		}
		return c == '{' || c == '[' || c == '"' || c == '\'' || c == ']' || c == '-' || Character.isDigit(c)
				|| text.startsWith("true", next) || text.startsWith("false", next) || text.startsWith("null", next);
	}

	/**
	 * Returns the index of the brace or bracket closing the one at {@code start}, or
	 * {@code -1} if it is not closed or a closing character does not match.
	 */
	private static int findClosingIndex(String text, int start) {
		Deque<Character> expectedClosings = new ArrayDeque<>();
		char quote = 0;
		boolean escaped = false;
		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (escaped) {
					escaped = false;
				}
				else if (c == '\\') {
					escaped = true;
				}
				else if (c == quote) {
					quote = 0;
				}
				continue;
			}
			switch (c) {
				case '"', '\'' -> {
					quote = c;
				}
				case '{' -> expectedClosings.push('}');
				case '[' -> expectedClosings.push(']');
				case '}', ']' -> {
					if (expectedClosings.isEmpty() || expectedClosings.pop() != c) {
						return -1;
					}
					if (expectedClosings.isEmpty()) {
						return i;
					}
				}
				default -> {
				}
			}
		}
		return -1;
	}

}
