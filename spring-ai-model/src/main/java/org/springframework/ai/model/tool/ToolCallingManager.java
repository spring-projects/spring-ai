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

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Service responsible for managing the tool calling process for a chat model.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolCallingManager {

	/**
	 * Resolve the tool definitions from the model's tool calling options.
	 */
	List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions);

	/**
	 * Execute the tool calls requested by the model.
	 * @throws ToolCallConfirmationRequiredException if a tool call needs a person's
	 * confirmation before it runs; none of the calls ran. Resume with
	 * {@link #executeToolCalls(Prompt, ChatResponse, ToolCallDecisions)}.
	 */
	ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse);

	/**
	 * Execute the tool calls requested by the model as a person decided: an allowed call
	 * runs, a denied call does not and the model receives the decision's message as its
	 * result. Every tool call of the response needs a decision, and an allowed call runs
	 * only with a tool the prompt carries. This is how a batch paused with a
	 * {@link ToolCallConfirmationRequiredException} resumes; the response is the reply
	 * the model gave, read from storage the end user cannot change.
	 * <p>
	 * The default throws {@link UnsupportedOperationException}.
	 * @param prompt the conversation the model answered with the tool calls, carrying the
	 * tools; it may end with the reply itself
	 * @param chatResponse the reply with the tool calls
	 * @param decisions one decision per tool call of the reply
	 * @return the result, with the conversation history ending in the tool results
	 * @throws IllegalArgumentException if a decision names no tool call of the reply, a
	 * tool call has no decision, or an allowed call names a tool the prompt does not
	 * carry
	 * @throws ToolCallLimitExceededException if an allowed call breaks a limit; the
	 * caller handles it, there is no loop to turn it into a reply
	 * @since 2.1.0
	 */
	default ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse,
			ToolCallDecisions decisions) {
		throw new UnsupportedOperationException(getClass().getName() + " does not support tool call decisions");
	}

	/**
	 * Create a default {@link ToolCallingManager} builder.
	 */
	static DefaultToolCallingManager.Builder builder() {
		return DefaultToolCallingManager.builder();
	}

}
