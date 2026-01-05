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

package org.springframework.ai.chat.metadata;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Normalized, provider-neutral categories for a chat completion finish reason.
 * <p>
 * {@link #categorize(String)} classifies the raw provider string. {@code null} and blank
 * values are {@link #UNKNOWN} because no reason was supplied. A non-blank value that does
 * not match a known alias is {@link #OTHER} and is never treated as {@link #COMPLETED}.
 * The raw string is left unchanged for callers that still read
 * {@link ChatGenerationMetadata#getFinishReason()}.
 *
 * @author Quark
 * @since 2.1.0
 * @see ChatGenerationMetadata#getFinishReasonCategory()
 */
public enum FinishReasonCategory {

	/**
	 * The model finished generating on its own. Corresponds to {@code stop},
	 * {@code end_turn}, {@code STOP}, and {@code stop_sequence}.
	 */
	COMPLETED,

	/**
	 * Output stopped because a length or token limit was hit. Corresponds to
	 * {@code length}, {@code max_tokens}, {@code MAX_TOKENS}, and
	 * {@code context_window_exceeded}.
	 */
	TRUNCATED,

	/**
	 * The raw finish reason itself reports a tool invocation. Corresponds to
	 * {@code tool_calls}, {@code tool_call}, {@code tool_use}, and {@code TOOL_CALLS}.
	 * Tool calls that a provider exposes separately, while reporting an ordinary stop,
	 * stay {@link #COMPLETED}.
	 */
	TOOL_CALL,

	/**
	 * Generation stopped because of a safety or policy filter. Corresponds to
	 * {@code content_filter}, {@code SAFETY}, {@code RECITATION}, {@code refusal},
	 * {@code PROHIBITED_CONTENT}, {@code SPII}, and {@code BLOCKLIST}.
	 */
	FILTERED,

	/**
	 * A non-blank finish reason that has no cross-provider category. Unrecognized
	 * provider values land here and are never reclassified as {@link #COMPLETED}.
	 */
	OTHER,

	/**
	 * No finish reason was supplied. {@code null} and blank strings are absent, which is
	 * distinct from an unrecognized value ({@link #OTHER}).
	 */
	UNKNOWN;

	/**
	 * Classify a raw finish reason.
	 * <p>
	 * Matching is case-insensitive and ignores surrounding whitespace. {@code null} and
	 * blank input is an absent reason and returns {@link #UNKNOWN}. Any other string that
	 * is not a known alias returns {@link #OTHER}. This method does not modify or replace
	 * the raw value.
	 * @param rawReason the provider finish reason, or {@code null} when absent
	 * @return the category, never {@code null}
	 */
	public static FinishReasonCategory categorize(@Nullable String rawReason) {
		if (rawReason == null || rawReason.isBlank()) {
			return UNKNOWN;
		}

		String normalized = rawReason.toLowerCase(Locale.ROOT).trim();

		return switch (normalized) {
			// Completed - normal end of generation
			case "stop", "end_turn", "stop_sequence" -> COMPLETED;

			// Truncated - hit token/length limits
			case "length", "max_tokens", "context_window_exceeded" -> TRUNCATED;

			// Tool call - the raw reason itself reports a tool invocation
			case "tool_calls", "tool_call", "tool_use" -> TOOL_CALL;

			// Filtered - content policy/safety
			case "content_filter", "safety", "recitation", "prohibited_content", "spii", "blocklist", "refusal" ->
				FILTERED;

			// Unrecognized non-blank value; not an absent reason
			default -> OTHER;
		};
	}

}
