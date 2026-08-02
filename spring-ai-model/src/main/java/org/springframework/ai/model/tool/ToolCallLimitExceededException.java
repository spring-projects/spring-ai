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

package org.springframework.ai.model.tool;

import org.jspecify.annotations.Nullable;

/**
 * Thrown by {@link DefaultToolCallingManager} when a configured tool call limit is
 * exceeded and {@link ToolCallLimitBehavior#THROW} is in effect.
 * <p>
 * Carries the {@link ToolExecutionResult} assembled from whichever tool calls in the
 * current batch already executed successfully before the limit was hit, so a caller can
 * still produce a coherent response instead of discarding completed work.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public class ToolCallLimitExceededException extends RuntimeException {

	private final @Nullable String toolName;

	private final int limit;

	private final ToolExecutionResult partialToolExecutionResult;

	/**
	 * @param toolName the name of the tool whose per-tool limit was exceeded, or
	 * {@code null} when the total per-turn limit was exceeded instead
	 * @param limit the configured limit that was exceeded
	 * @param partialToolExecutionResult the result assembled from the tool calls already
	 * executed in the current batch, including a synthesized error response for the call
	 * that exceeded the limit
	 */
	public ToolCallLimitExceededException(@Nullable String toolName, int limit,
			ToolExecutionResult partialToolExecutionResult) {
		super(buildMessage(toolName, limit));
		this.toolName = toolName;
		this.limit = limit;
		this.partialToolExecutionResult = partialToolExecutionResult;
	}

	private static String buildMessage(@Nullable String toolName, int limit) {
		return toolName != null ? "Tool call limit (%d) exceeded for tool '%s'".formatted(limit, toolName)
				: "Total tool call limit (%d) exceeded for this turn".formatted(limit);
	}

	/**
	 * The name of the tool whose per-tool limit was exceeded, or {@code null} when the
	 * total per-turn limit was exceeded instead.
	 */
	public @Nullable String getToolName() {
		return this.toolName;
	}

	/**
	 * The configured limit that was exceeded.
	 */
	public int getLimit() {
		return this.limit;
	}

	/**
	 * The result assembled from the tool calls already executed in the current batch,
	 * including a synthesized error response for the call that exceeded the limit.
	 */
	public ToolExecutionResult getPartialToolExecutionResult() {
		return this.partialToolExecutionResult;
	}

}
