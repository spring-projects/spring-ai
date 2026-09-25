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

import java.util.List;
import java.util.Objects;

/**
 * Immutable data object describing the result of one GPULlama3 generation call.
 *
 * <p>
 * This record is returned by {@link GpuLlama3Runtime} after the underlying GPULlama3
 * engine has generated tokens. It keeps the generated facts separate from Spring AI
 * response construction: the runtime records what happened, while
 * {@link GpuLlama3ChatModel} later converts this result into a Spring AI
 * {@code ChatResponse}.
 * </p>
 *
 * <p>
 * The stored values are used as follows:
 * </p>
 *
 * <ul>
 * <li>{@code promptTokens}: used to calculate prompt token usage.</li>
 * <li>{@code completionTokens}: used to calculate completion token usage.</li>
 * <li>{@code rawText}: parsed into visible content and optional thinking text.</li>
 * <li>{@code finishReason}: copied into Spring AI generation metadata.</li>
 * <li>{@code durationNanos}: copied into provider metadata for timing information.</li>
 * </ul>
 *
 * @since 2.0.1
 */
public record GpuLlama3GenerationResult(List<Integer> promptTokens, List<Integer> completionTokens, String rawText,
		String finishReason, long durationNanos) {

	public static final String FINISH_REASON_STOP = "stop";

	public static final String FINISH_REASON_LENGTH = "length";

	/**
	 * Creates an immutable generation result and validates required fields.
	 *
	 * <p>
	 * Prompt and completion tokens are defensively copied to prevent later modification.
	 * The raw text, finish reason, and duration are used by {@link GpuLlama3ChatModel} to
	 * build the final Spring AI response metadata.
	 * </p>
	 */
	public GpuLlama3GenerationResult {
		promptTokens = List.copyOf(Objects.requireNonNull(promptTokens, "promptTokens must not be null"));
		completionTokens = List.copyOf(Objects.requireNonNull(completionTokens, "completionTokens must not be null"));
		Objects.requireNonNull(rawText, "rawText must not be null");
		Objects.requireNonNull(finishReason, "finishReason must not be null");
		if (durationNanos < 0) {
			throw new IllegalArgumentException("durationNanos must not be negative");
		}
	}

}
