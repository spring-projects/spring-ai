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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A {@link ResponseTextCleaner} that removes thinking tags from LLM responses. This
 * cleaner supports multiple tag patterns to handle different AI models:
 * <ul>
 * <li>Amazon Nova: {@code <thinking>...</thinking>}</li>
 * <li>Qwen models: {@code <think>...</think>}</li>
 * <li>DeepSeek models: various thinking patterns</li>
 * <li>Claude models: thinking blocks in different formats</li>
 * </ul>
 * <p>
 * <b>Performance:</b> This cleaner includes fast-path optimization. For responses without
 * thinking tags (most models), it performs a quick character check and returns
 * immediately, making it safe to use as a default cleaner even for non-thinking models.
 *
 * @author liugddx
 * @since 1.1.0
 */
public class ThinkingTagCleaner implements ResponseTextCleaner {

	/**
	 * Default thinking tag patterns used by common AI models.
	 */
	private static final List<Pattern> DEFAULT_PATTERNS = Arrays.asList(
			// Amazon Nova: <thinking>...</thinking>
			Pattern.compile("(?s)<thinking>.*?</thinking>\\s*", Pattern.CASE_INSENSITIVE),
			// Qwen models: <think>...</think>
			Pattern.compile("(?s)<think>.*?</think>\\s*", Pattern.CASE_INSENSITIVE),
			// Alternative XML-style tags
			Pattern.compile("(?s)<reasoning>.*?</reasoning>\\s*", Pattern.CASE_INSENSITIVE),
			// Markdown style thinking blocks
			Pattern.compile("(?s)```thinking.*?```\\s*", Pattern.CASE_INSENSITIVE),
			// Some models use comment-style
			Pattern.compile("(?s)<!--\\s*thinking:.*?-->\\s*", Pattern.CASE_INSENSITIVE));

	private final List<Pattern> patterns;

	/**
	 * Creates a cleaner with default thinking tag patterns.
	 */
	public ThinkingTagCleaner() {
		this(DEFAULT_PATTERNS);
	}

	/**
	 * Creates a cleaner with custom patterns.
	 * @param patterns the list of regex patterns to match thinking tags
	 */
	public ThinkingTagCleaner(List<Pattern> patterns) {
		Assert.notNull(patterns, "patterns cannot be null");
		Assert.notEmpty(patterns, "patterns cannot be empty");
		this.patterns = new ArrayList<>(patterns);
	}

	/**
	 * Creates a cleaner with custom pattern strings.
	 * @param patternStrings the list of regex pattern strings to match thinking tags
	 */
	public ThinkingTagCleaner(String... patternStrings) {
		Assert.notNull(patternStrings, "patternStrings cannot be null");
		Assert.notEmpty(patternStrings, "patternStrings cannot be empty");
		this.patterns = new ArrayList<>();
		for (String patternString : patternStrings) {
			this.patterns.add(Pattern.compile(patternString, Pattern.CASE_INSENSITIVE));
		}
	}

	@Override
	public @Nullable String clean(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		// Fast path: if text doesn't contain '<' character, no tags to remove
		if (!text.contains("<") && !text.contains("`")) {
			return text;
		}

		String result = text;
		for (Pattern pattern : this.patterns) {
			String afterReplacement = pattern.matcher(result).replaceAll("");
			// If replacement occurred, update result and continue checking other patterns
			// (since multiple tag types might coexist)
			if (!afterReplacement.equals(result)) {
				result = afterReplacement;
			}
		}
		return result;
	}

	/**
	 * Creates a builder for constructing a thinking tag cleaner.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link ThinkingTagCleaner}.
	 */
	public static final class Builder {

		private final List<Pattern> patterns = new ArrayList<>(DEFAULT_PATTERNS);

		private boolean useDefaultPatterns = true;

		private Builder() {
		}

		/**
		 * Disable default patterns. Only custom patterns added via
		 * {@link #addPattern(String)} or {@link #addPattern(Pattern)} will be used.
		 * @return this builder
		 */
		public Builder withoutDefaultPatterns() {
			this.useDefaultPatterns = false;
			return this;
		}

		/**
		 * Add a custom pattern string.
		 * @param patternString the regex pattern string
		 * @return this builder
		 */
		public Builder addPattern(String patternString) {
			Assert.hasText(patternString, "patternString cannot be empty");
			if (!this.useDefaultPatterns) {
				this.patterns.clear();
				this.useDefaultPatterns = true; // Reset flag after first custom pattern
			}
			this.patterns.add(Pattern.compile(patternString, Pattern.CASE_INSENSITIVE));
			return this;
		}

		/**
		 * Add a custom pattern.
		 * @param pattern the regex pattern
		 * @return this builder
		 */
		public Builder addPattern(Pattern pattern) {
			Assert.notNull(pattern, "pattern cannot be null");
			if (!this.useDefaultPatterns) {
				this.patterns.clear();
				this.useDefaultPatterns = true; // Reset flag after first custom pattern
			}
			this.patterns.add(pattern);
			return this;
		}

		/**
		 * Build the thinking tag cleaner.
		 * @return a new thinking tag cleaner instance
		 */
		public ThinkingTagCleaner build() {
			return new ThinkingTagCleaner(this.patterns);
		}

	}

}
