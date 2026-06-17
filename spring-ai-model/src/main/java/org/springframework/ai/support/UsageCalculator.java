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

package org.springframework.ai.support;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * A utility class to provide support methods handling {@link Usage}.
 *
 * @author Ilayaperumal Gopinathan
 * @author Jewoo Shin
 */
public final class UsageCalculator {

	private UsageCalculator() {
		throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
	}

	/**
	 * Accumulate usage tokens from the previous chat response to the current usage
	 * tokens.
	 * <p>
	 * Note: when the two usages are actually summed, the result is a plain
	 * {@link DefaultUsage} and the provider-specific {@link Usage#getNativeUsage() native
	 * usage} object is <em>not</em> preserved (it cannot be merged across responses).
	 * Only the token counts and cache metrics carry over. The original
	 * {@code currentUsage} (native usage included) is returned unchanged when there is
	 * nothing to accumulate.
	 * @param currentUsage the current usage.
	 * @param previousChatResponse the previous chat response.
	 * @return accumulated usage.
	 */
	public static Usage getCumulativeUsage(final Usage currentUsage,
			final @Nullable ChatResponse previousChatResponse) {
		Usage usageFromPreviousChatResponse = null;
		if (previousChatResponse != null) {
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
			// Accumulate cache metrics, preserving null when neither side reports them.
			Long cacheRead = null;
			if (currentUsage.getCacheReadInputTokens() != null
					|| usageFromPreviousChatResponse.getCacheReadInputTokens() != null) {
				cacheRead = (currentUsage.getCacheReadInputTokens() != null ? currentUsage.getCacheReadInputTokens()
						: 0L)
						+ (usageFromPreviousChatResponse.getCacheReadInputTokens() != null
								? usageFromPreviousChatResponse.getCacheReadInputTokens() : 0L);
			}
			Long cacheWrite = null;
			if (currentUsage.getCacheWriteInputTokens() != null
					|| usageFromPreviousChatResponse.getCacheWriteInputTokens() != null) {
				cacheWrite = (currentUsage.getCacheWriteInputTokens() != null ? currentUsage.getCacheWriteInputTokens()
						: 0L)
						+ (usageFromPreviousChatResponse.getCacheWriteInputTokens() != null
								? usageFromPreviousChatResponse.getCacheWriteInputTokens() : 0L);
			}
			// Native usage is passed as null: provider-specific objects cannot be merged
			// across responses, so only token counts and cache metrics are accumulated.
			return new DefaultUsage(promptTokens, generationTokens, totalTokens, null, cacheRead, cacheWrite);
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
	public static boolean isEmpty(@Nullable Usage usage) {
		return usage == null || usage.getTotalTokens() == 0L;
	}

	/**
	 * Folds the usage of the current chat response into the previously accumulated
	 * {@link ChatResponse}. The returned {@link ChatResponse} carries the cumulative
	 * usage (current plus previously accumulated). This is the building block for
	 * accumulating usage across the iterations of a recursive flow (for example a
	 * tool-calling loop or a validation retry loop).
	 * @param currentChatResponse the chat response produced by the current iteration, or
	 * {@code null}
	 * @param accumulatedChatResponse the chat response carrying the cumulative usage from
	 * previous iterations, or {@code null} if none yet
	 * @return a chat response carrying the cumulative usage, the previously accumulated
	 * response unchanged when the current response is {@code null}, or {@code null} when
	 * no usage has been reported
	 * @since 2.0.0
	 */
	public static @Nullable ChatResponse accumulateResponseUsage(@Nullable ChatResponse currentChatResponse,
			@Nullable ChatResponse accumulatedChatResponse) {
		if (currentChatResponse == null) {
			return accumulatedChatResponse;
		}
		Usage currentUsage = currentChatResponse.getMetadata().getUsage();
		ChatResponse previousChatResponse = isEmpty(getUsage(accumulatedChatResponse)) ? null : accumulatedChatResponse;
		Usage cumulativeUsage = getCumulativeUsage(currentUsage, previousChatResponse);
		if (isEmpty(cumulativeUsage)) {
			return null;
		}
		return withUsage(currentChatResponse, cumulativeUsage);
	}

	/**
	 * Returns a copy of the given chat response with its usage replaced by the provided
	 * {@link Usage}, preserving all other response metadata.
	 * @param chatResponse the chat response to copy
	 * @param usage the usage to set on the copy
	 * @return a new chat response carrying the given usage
	 * @since 2.0.0
	 */
	public static ChatResponse withUsage(ChatResponse chatResponse, Usage usage) {
		return ChatResponse.builder()
			.from(chatResponse)
			.metadata(metadataWithUsage(chatResponse.getMetadata(), usage))
			.build();
	}

	private static ChatResponseMetadata metadataWithUsage(ChatResponseMetadata metadata, Usage usage) {
		ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
			.id(metadata.getId())
			.model(metadata.getModel())
			.rateLimit(metadata.getRateLimit())
			.usage(usage)
			.promptMetadata(metadata.getPromptMetadata());
		metadata.entrySet().forEach(entry -> builder.keyValue(entry.getKey(), entry.getValue()));
		return builder.build();
	}

	private static @Nullable Usage getUsage(@Nullable ChatResponse chatResponse) {
		return chatResponse != null ? chatResponse.getMetadata().getUsage() : null;
	}

}
