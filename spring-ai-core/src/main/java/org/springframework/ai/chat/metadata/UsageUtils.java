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

package org.springframework.ai.chat.metadata;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * An utility class to provide support methods handling {@link Usage}.
 *
 * @author Ilayaperumal Gopinathan
 */
public class UsageUtils {

	public static Usage getCumulativeUsage(final Usage currentUsage, final ChatResponse previousChatResponse) {
		Long promptTokens = currentUsage.getPromptTokens().longValue();
		Long generationTokens = currentUsage.getGenerationTokens().longValue();
		Long totalTokens = currentUsage.getTotalTokens().longValue();
		// Make sure to accumulate the usage from the previous chat response.
		if (previousChatResponse != null && previousChatResponse.getMetadata() != null
				&& previousChatResponse.getMetadata().getUsage() != null) {
			Usage usageFromPreviousChatResponse = previousChatResponse.getMetadata().getUsage();
			promptTokens += usageFromPreviousChatResponse.getPromptTokens();
			generationTokens += usageFromPreviousChatResponse.getGenerationTokens();
			totalTokens += usageFromPreviousChatResponse.getTotalTokens();
		}
		return new DefaultUsage(promptTokens, generationTokens, totalTokens);
	}

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
