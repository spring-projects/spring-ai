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

import org.springframework.util.Assert;

/**
 * Thrown by a {@link ToolCallingManager} when a tool call of the batch needs a person's
 * confirmation before it runs (see {@link ToolCallConfirmationPolicy}) and no
 * {@link ToolCallDecisions} were given. None of the batch's tool calls ran.
 * <p>
 * A tool-calling loop pauses on it: it returns the reply to the caller as the model gave
 * it, listing the keys of the calls that wait for a decision under
 * {@link ToolCallDecisions#AWAITING_DECISION_METADATA_KEY}. The caller collects the
 * decisions and runs the batch with
 * {@link ToolCallingManager#executeToolCalls(org.springframework.ai.chat.prompt.Prompt, org.springframework.ai.chat.model.ChatResponse, ToolCallDecisions)}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class ToolCallConfirmationRequiredException extends RuntimeException {

	private final List<String> toolCallIds;

	/**
	 * Create the exception for the calls that wait for a decision.
	 * @param toolCallIds the keys of the tool calls that need a confirmation (see
	 * {@link ToolCallDecisions#toolCallKeys}), in the order the model asked for them
	 */
	public ToolCallConfirmationRequiredException(List<String> toolCallIds) {
		super("Tool calls " + toolCallIds + " need a person's confirmation before they run");
		Assert.notEmpty(toolCallIds, "toolCallIds must not be empty");
		Assert.noNullElements(toolCallIds, "toolCallIds must not contain null elements");
		this.toolCallIds = List.copyOf(toolCallIds);
	}

	/**
	 * The keys of the tool calls that need a confirmation (see
	 * {@link ToolCallDecisions#toolCallKeys}), in the order the model asked for them.
	 */
	public List<String> getToolCallIds() {
		return this.toolCallIds;
	}

}
