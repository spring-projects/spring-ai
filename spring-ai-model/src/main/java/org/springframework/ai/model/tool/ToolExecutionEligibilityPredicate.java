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

import java.util.function.BiPredicate;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.util.Assert;

/**
 * Interface for determining when tool execution should be performed based on model
 * responses.
 *
 * @deprecated since 2.0.0 for removal in 3.0.0 — replaced by
 * {@link ToolExecutionEligibilityChecker}, which separates the response check from the
 * options-based policy check. For the recommended long-term approach, internal tool
 * execution in {@link org.springframework.ai.chat.model.ChatModel} implementations is
 * superseded by {@code ToolCallAdvisor} used via {@code ChatClient}.
 * @author Christian Tzolov
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public interface ToolExecutionEligibilityPredicate extends BiPredicate<ChatOptions, ChatResponse> {

	/**
	 * Determines if tool execution should be performed based on the prompt options and
	 * chat response.
	 * @param promptOptions The options from the prompt
	 * @param chatResponse The response from the chat model
	 * @return true if tool execution should be performed, false otherwise
	 * @deprecated since 2.0.0 for removal in 3.0.0
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	default boolean isToolExecutionRequired(ChatOptions promptOptions, ChatResponse chatResponse) {
		Assert.notNull(promptOptions, "promptOptions cannot be null");
		Assert.notNull(chatResponse, "chatResponse cannot be null");
		return test(promptOptions, chatResponse);
	}

}
