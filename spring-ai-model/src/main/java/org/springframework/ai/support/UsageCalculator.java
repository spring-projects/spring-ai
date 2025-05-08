/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.support;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * An utility class to provide support methods handling {@link Usage}.
 *
 * @author Ilayaperumal Gopinathan
 */
public final class UsageCalculator {

	private UsageCalculator() {
		throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
	}

	/**
	 * Accumulate usage tokens from the previous chat response to the current usage
	 * tokens.
	 * @param currentUsage the current usage.
	 * @param previousChatResponse the previous chat response.
	 * @return accumulated usage.
	 */
	public static Usage getCumulativeUsage(final Usage currentUsage, final ChatResponse previousChatResponse) {
		Usage usageFromPreviousChatResponse = null;
		if (previousChatResponse != null && previousChatResponse.getMetadata() != null
				&& previousChatResponse.getMetadata().getUsage() != null) {
			usageFromPreviousChatResponse = previousChatResponse.getMetadata().getUsage();
		}
		else {
			// Return the current usage when the previous chat response usage is empty or
			// null.
			return currentUsage;
		}
		// For a valid usage from previous chat response, accumulate it to the current
		// usage.
		if (!isEmpty(currentUsage)) {
			Integer promptTokens = currentUsage.getPromptTokens();
			Integer generationTokens = currentUsage.getCompletionTokens();
			Integer totalTokens = currentUsage.getTotalTokens();
			// Make sure to accumulate the usage from the previous chat response.
			promptTokens += usageFromPreviousChatResponse.getPromptTokens();
			generationTokens += usageFromPreviousChatResponse.getCompletionTokens();
			totalTokens += usageFromPreviousChatResponse.getTotalTokens();
			return new DefaultUsage(promptTokens, generationTokens, totalTokens);
		}
		// When current usage is empty, return the usage from the previous chat response.
		return usageFromPreviousChatResponse;
	}

	/**
	 * Check if the {@link Usage} is empty. Returns true when the {@link Usage} is null.
	 * Returns true when the {@link Usage} has zero tokens.
	 * @param usage the usage to check against.
	 * @return the boolean value to represent if it is empty.
	 */
	public static boolean isEmpty(Usage usage) {
		if (usage == null) {
			return true;
		}
		else if (usage != null && usage.getTotalTokens() == 0L) {
			return true;
		}
		return false;
	}

}
