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

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.util.Assert;

/**
 * Thrown by a {@link ToolCallingManager} when a tool call of the batch needs a person's
 * confirmation before it runs (see {@link ToolCallConfirmationPolicy}) and no
 * {@link ToolCallDecisions} were given. None of the batch's tool calls ran.
 * <p>
 * A tool-calling loop pauses on it: it returns the reply to the caller, listing the ids
 * under {@link ToolCallDecisions#AWAITING_DECISION_METADATA_KEY}. The caller collects the
 * decisions and runs the batch with
 * {@link ToolCallingManager#executeToolCalls(org.springframework.ai.chat.prompt.Prompt, org.springframework.ai.chat.model.ChatResponse, ToolCallDecisions)}.
 * <p>
 * The reply the loop returns must carry {@link #getToolCallsMessage() this message}: when
 * the model gave its tool calls no ids, the manager made some up, so that a decision can
 * name the call it is about.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class ToolCallConfirmationRequiredException extends RuntimeException {

	private final List<String> toolCallIds;

	private final AssistantMessage toolCallsMessage;

	/**
	 * Create the exception for the calls that wait for a decision.
	 * @param toolCallIds the ids of the tool calls that need a confirmation, in the order
	 * the model asked for them
	 * @param toolCallsMessage the reply's message with the tool calls, every one of them
	 * with an id
	 */
	public ToolCallConfirmationRequiredException(List<String> toolCallIds, AssistantMessage toolCallsMessage) {
		super("Tool calls " + toolCallIds + " need a person's confirmation before they run");
		Assert.notEmpty(toolCallIds, "toolCallIds must not be empty");
		Assert.noNullElements(toolCallIds, "toolCallIds must not contain null elements");
		Assert.notNull(toolCallsMessage, "toolCallsMessage must not be null");
		this.toolCallIds = List.copyOf(toolCallIds);
		this.toolCallsMessage = toolCallsMessage;
	}

	/**
	 * The ids of the tool calls that need a confirmation, in the order the model asked
	 * for them.
	 */
	public List<String> getToolCallIds() {
		return this.toolCallIds;
	}

	/**
	 * The reply's message with the tool calls, every one of them with an id. The loop
	 * returns it in place of the model's, so that the decisions can name the calls.
	 */
	public AssistantMessage getToolCallsMessage() {
		return this.toolCallsMessage;
	}

}
