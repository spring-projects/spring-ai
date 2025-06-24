/*
 * Copyright 2025-2025 the original author or authors.
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

import java.util.function.Function;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.Assert;

/**
 * Interface for determining when tool execution should be performed based on model
 * responses.
 *
 * @author Christian Tzolov
 * @author lambochen
 */
public interface ToolExecutionEligibilityChecker extends Function<ChatResponse, Boolean> {

	/**
	 * Determines if tool execution should be performed based on the prompt options and
	 * chat response.
	 * @param promptOptions The options from the prompt
	 * @param chatResponse The response from the chat model
	 * @return true if tool execution should be performed, false otherwise
	 */
	default boolean isToolExecutionRequired(ChatOptions promptOptions, ChatResponse chatResponse) {
		Assert.notNull(promptOptions, "promptOptions cannot be null");
		Assert.notNull(chatResponse, "chatResponse cannot be null");
		return this.isInternalToolExecutionEnabled(promptOptions) && this.isToolCallResponse(chatResponse);
	}

	/**
	 * Determines if tool execution should be performed based on the prompt options and
	 * chat response and toolExecutionIterations.
	 * @param promptOptions The options from the prompt
	 * @param chatResponse The response from the chat model
	 * @param toolExecutionIterations The number of toolExecutionIterations to execute the
	 * tool
	 * @return true if tool execution should be performed, false otherwise
	 */
	default boolean isToolExecutionRequired(ChatOptions promptOptions, ChatResponse chatResponse,
			int toolExecutionIterations) {
		Assert.notNull(promptOptions, "promptOptions cannot be null");
		Assert.notNull(chatResponse, "chatResponse cannot be null");
		return this.isInternalToolExecutionEnabled(promptOptions, toolExecutionIterations)
				&& this.isToolCallResponse(chatResponse);
	}

	/**
	 * Determines if the response is a tool call message response.
	 * @param chatResponse The response from the chat model call
	 * @return true if the response is a tool call message response, false otherwise
	 */
	default boolean isToolCallResponse(ChatResponse chatResponse) {
		Assert.notNull(chatResponse, "chatResponse cannot be null");
		return apply(chatResponse);
	}

	/**
	 * Determines if tool execution should be performed by the Spring AI or by the client.
	 * @param chatOptions The options from the chat
	 * @return true if tool execution should be performed by Spring AI, false if it should
	 * be performed by the client
	 */
	default boolean isInternalToolExecutionEnabled(ChatOptions chatOptions) {

		Assert.notNull(chatOptions, "chatOptions cannot be null");
		boolean internalToolExecutionEnabled;
		if (chatOptions instanceof ToolCallingChatOptions toolCallingChatOptions
				&& toolCallingChatOptions.getInternalToolExecutionEnabled() != null) {
			internalToolExecutionEnabled = Boolean.TRUE
				.equals(toolCallingChatOptions.getInternalToolExecutionEnabled());
		}
		else {
			internalToolExecutionEnabled = true;
		}
		return internalToolExecutionEnabled;
	}

	/**
	 * Determines if tool execution should be performed by the Spring AI or by the client.
	 * @param chatOptions The options from the chat
	 * @param toolExecutionIterations The number of toolExecutionIterations to execute the
	 * tool
	 * @return true if tool execution should be performed by Spring AI, false if it should
	 * be performed by the client
	 */
	default boolean isInternalToolExecutionEnabled(ChatOptions chatOptions, int toolExecutionIterations) {
		boolean internalToolExecutionEnabled = isInternalToolExecutionEnabled(chatOptions);
		if (!internalToolExecutionEnabled) {
			return false;
		}

		return !isLimitExceeded(chatOptions, toolExecutionIterations);
	}

	/**
	 * Determines if the tool execution limit has been exceeded.
	 * @param promptOptions The options from the prompt
	 * @param toolExecutionIterations The number of toolExecutionIterations
	 * @return true if the tool execution limit has been exceeded, false otherwise
	 */
	default boolean isLimitExceeded(ChatOptions promptOptions, int toolExecutionIterations) {
		if (promptOptions instanceof ToolCallingChatOptions toolCallingChatOptions) {
			return toolCallingChatOptions.getInternalToolExecutionMaxIterations() == null
					|| toolExecutionIterations <= toolCallingChatOptions.getInternalToolExecutionMaxIterations();
		}

		return false;
	}

}
