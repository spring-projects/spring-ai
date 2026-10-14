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

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.Assert;

/**
 * The decisions a person made on the tool calls of a reply, for a
 * {@link ToolCallingManager} to carry them out.
 * <p>
 * A manager pauses a batch when one of its calls needs confirmation (see
 * {@link ToolCallConfirmationPolicy}), by throwing
 * {@link ToolCallConfirmationRequiredException} before any call runs. A tool-calling loop
 * then returns the reply to the caller, listing the ids of the calls that wait for a
 * decision under {@link #AWAITING_DECISION_METADATA_KEY} in the response metadata
 * ({@link #awaitingDecision(ChatResponse)}). The caller shows them to a person and runs
 * the reply's calls with the decisions through
 * {@link ToolCallingManager#executeToolCalls(org.springframework.ai.chat.prompt.Prompt, ChatResponse, ToolCallDecisions)}:
 * the allowed calls run, a denied call answers the model with its message. The tool
 * results then go to the model as part of the next request.
 * <p>
 * Every tool call of the reply needs a decision, and an allowed call runs only with a
 * tool the prompt carries. The reply the calls come from must be the one the model gave,
 * read from storage the end user cannot change: whoever can edit it can run any tool with
 * any arguments.
 *
 * @param decisions one decision per tool call of the reply, with distinct ids
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record ToolCallDecisions(List<ToolCallDecision> decisions) {

	/**
	 * The response metadata key under which a paused loop lists the ids of the tool calls
	 * that wait for a decision, as a {@code List<String>}. When streaming, the key is on
	 * the last chunk only; the aggregated response does not carry it.
	 */
	public static final String AWAITING_DECISION_METADATA_KEY = "spring.ai.tool-calls-awaiting-decision";

	public ToolCallDecisions {
		Assert.notNull(decisions, "decisions must not be null");
		Assert.noNullElements(decisions, "decisions must not contain null elements");
		Assert.isTrue(decisions.stream().map(ToolCallDecision::toolCallId).distinct().count() == decisions.size(),
				"decisions must be about distinct tool calls");
		decisions = List.copyOf(decisions);
	}

	/** The decisions, one per tool call. */
	public static ToolCallDecisions of(ToolCallDecision... decisions) {
		return new ToolCallDecisions(List.of(decisions));
	}

	/** The decision on the tool call with the id, or {@code null} when there is none. */
	public @Nullable ToolCallDecision decisionOn(String toolCallId) {
		for (ToolCallDecision decision : this.decisions) {
			if (decision.toolCallId().equals(toolCallId)) {
				return decision;
			}
		}
		return null;
	}

	/**
	 * The ids of the tool calls a paused loop waits on, or an empty list when the
	 * response is not a paused one. When streaming, ask the last chunk; the aggregated
	 * response does not carry the ids.
	 * <p>
	 * Only a response tells a paused reply apart: read it with {@code chatResponse()} or
	 * {@code chatClientResponse()}. The text that {@code content()} returns is the paused
	 * reply's text, often empty, and a streamed {@code content()} simply ends.
	 */
	@SuppressWarnings("unchecked")
	public static List<String> awaitingDecision(@Nullable ChatResponse chatResponse) {
		if (chatResponse == null) {
			return List.of();
		}
		Object ids = chatResponse.getMetadata().get(AWAITING_DECISION_METADATA_KEY);
		return (ids instanceof List<?> list) ? (List<String>) list : List.of();
	}

	/** Whether the response is a paused one, with tool calls waiting for a decision. */
	public static boolean isAwaitingDecision(@Nullable ChatResponse chatResponse) {
		return !awaitingDecision(chatResponse).isEmpty();
	}

}
