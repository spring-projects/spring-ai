/*
 * Copyright 2023-2026 the original author or authors.
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
 * Normalized categories for chat completion finish reasons.
 *
 * <p>
 * This enum provides a provider-agnostic categorization of finish reasons, making it
 * easier to build consistent audits, metrics, and alerts across multiple AI providers.
 * </p>
 *
 * <p>
 * Provider-specific finish reason strings vary widely:
 * </p>
 * <ul>
 * <li>OpenAI: {@code STOP}, {@code LENGTH}, {@code TOOL_CALLS},
 * {@code CONTENT_FILTER}</li>
 * <li>Anthropic: {@code end_turn}, {@code max_tokens}, {@code tool_use}</li>
 * <li>Gemini: {@code STOP}, {@code MAX_TOKENS}, {@code SAFETY}, {@code RECITATION}</li>
 * <li>Azure OpenAI: {@code stop}, {@code length}, {@code tool_calls},
 * {@code content_filter}</li>
 * <li>Bedrock: {@code end_turn}, {@code max_tokens}, {@code tool_use}</li>
 * </ul>
 *
 * @author Spring AI Team
 * @since 1.0.0
 * @see ChatGenerationMetadata#getFinishReasonCategory()
 */
public enum FinishReasonCategory {

	/**
	 * Normal completion - the model finished generating naturally. Corresponds to:
	 * {@code stop}, {@code end_turn}, {@code STOP}, {@code stop_sequence}.
	 */
	COMPLETED,

	/**
	 * Output was truncated due to length or token limits. Corresponds to: {@code length},
	 * {@code max_tokens}, {@code MAX_TOKENS}, {@code context_window_exceeded}.
	 */
	TRUNCATED,

	/**
	 * The model invoked a tool/function. Corresponds to: {@code tool_calls},
	 * {@code tool_call}, {@code tool_use}, {@code TOOL_CALLS}.
	 */
	TOOL_CALL,

	/**
	 * Content was filtered due to safety or policy constraints. Corresponds to:
	 * {@code content_filter}, {@code SAFETY}, {@code RECITATION}, {@code refusal},
	 * {@code PROHIBITED_CONTENT}, {@code SPII}, {@code BLOCKLIST}.
	 */
	FILTERED,

	/**
	 * A known finish reason that doesn't fit other categories.
	 */
	OTHER,

	/**
	 * The finish reason is null, empty, or not recognized.
	 */
	UNKNOWN;

	/**
	 * Categorize a raw finish reason string into a normalized category.
	 *
	 * <p>
	 * This method performs case-insensitive matching against known provider finish reason
	 * values.
	 * </p>
	 * @param rawReason the raw finish reason string from the provider (may be null)
	 * @return the categorized finish reason, never null
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

			// Tool call - model wants to invoke a tool
			case "tool_calls", "tool_call", "tool_use" -> TOOL_CALL;

			// Filtered - content policy/safety
			case "content_filter", "safety", "recitation", "prohibited_content", "spii", "blocklist", "refusal" ->
				FILTERED;

			// Unknown or unrecognized
			default -> OTHER;
		};
	}

}
