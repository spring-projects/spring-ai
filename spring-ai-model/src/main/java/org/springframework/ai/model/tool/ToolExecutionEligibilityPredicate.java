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

import java.util.function.BiPredicate;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.Assert;

/**
 * Interface for determining when tool execution should be performed based on model
 * responses.
 *
 * @author Christian Tzolov
 */
public interface ToolExecutionEligibilityPredicate extends BiPredicate<ChatOptions, ChatResponse> {

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
		return test(promptOptions, chatResponse);
	}

	/**
	 * Determines if tool execution should be performed based on the prompt options and chat response and the number of attempts.
	 * @param promptOptions The options from the prompt
	 * @param chatResponse The response from the chat model
	 * @param attempts The number of attempts
	 * @return true if tool execution should be performed, false otherwise
	 * @see ToolCallingChatOptions#getInternalToolExecutionMaxAttempts()
	 * @see #isToolExecutionRequired(ChatOptions, ChatResponse)
	 */
	default boolean isToolExecutionRequired(ChatOptions promptOptions, ChatResponse chatResponse, int attempts) {
		boolean isToolExecutionRequired = isToolExecutionRequired(promptOptions, chatResponse);
		if (!isToolExecutionRequired) {
			return true;
		}

		if (promptOptions instanceof ToolCallingChatOptions toolCallingChatOptions) {
			return toolCallingChatOptions.getInternalToolExecutionMaxAttempts() == null
					|| attempts <= toolCallingChatOptions.getInternalToolExecutionMaxAttempts();
		}
		else {
			isToolExecutionRequired = true;
		}
		return isToolExecutionRequired;
	}

}
