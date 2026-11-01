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

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.Generation;

/**
 * Thrown by {@link DefaultToolCallingManager} when a configured tool call limit is
 * exceeded and {@link ToolCallLimitBehavior#THROW} is in effect.
 * <p>
 * Carries the {@link ToolExecutionResult} assembled from whichever tool calls in the
 * current batch already executed successfully before the limit was hit, so a caller can
 * still produce a coherent response instead of discarding completed work.
 *
 * @author Christian Tzolov
 * @since 2.0.1
 */
public class ToolCallLimitExceededException extends RuntimeException {

	/**
	 * {@link Generation#getMetadata()} finish reason set on the {@link Generation} built
	 * by {@link #buildGeneration()}, distinguishing a forcibly aborted turn from a
	 * regular {@link ToolExecutionResult#FINISH_REASON returnDirect} completion.
	 */
	public static final String FINISH_REASON = "toolCallLimitExceeded";

	/**
	 * {@link ChatGenerationMetadata} key under which the
	 * {@link ToolResponseMessage.ToolResponse}s that completed successfully in the
	 * current batch before the limit was hit are attached to the {@link Generation} built
	 * by {@link #buildGeneration()}. Keeping them in metadata - rather than as separate,
	 * sibling {@link Generation}s - avoids mixing successful tool output with the error
	 * message as if they were equally valid alternative answers.
	 */
	public static final String METADATA_PARTIAL_TOOL_RESPONSES = "partialToolResponses";

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

	/**
	 * Builds a single {@link Generation} representing this exception, so that a limit
	 * breach is never mistaken for one of several equally valid answers.
	 * <p>
	 * The {@link Generation}'s output is the breach message synthesized by
	 * {@link DefaultToolCallingManager} for the call that exceeded the limit. Any tool
	 * responses that completed successfully earlier in the same batch are attached under
	 * {@value #METADATA_PARTIAL_TOOL_RESPONSES} instead of being emitted as separate,
	 * sibling generations, so completed work isn't discarded but also isn't confused with
	 * the error itself.
	 */
	public Generation buildGeneration() {
		String breachMessage = getMessage();
		List<ToolResponseMessage.ToolResponse> partialResponses = List.of();

		List<Message> history = this.partialToolExecutionResult.conversationHistory();
		if (!history.isEmpty() && history.get(history.size() - 1) instanceof ToolResponseMessage toolResponseMessage) {
			List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
			if (!responses.isEmpty()) {
				breachMessage = responses.get(responses.size() - 1).responseData();
				partialResponses = responses.subList(0, responses.size() - 1);
			}
		}

		ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
			.finishReason(FINISH_REASON)
			.metadata(METADATA_PARTIAL_TOOL_RESPONSES, partialResponses)
			.build();
		return new Generation(new AssistantMessage(breachMessage), metadata);
	}

}
