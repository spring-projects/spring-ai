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

package org.springframework.ai.chat.client.advisor;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.support.UsageCalculator;

/**
 * Accumulates token {@link Usage} across the iterations of a recursive advisor (for
 * example a tool-calling loop or a structured-output validation retry loop), so that the
 * final response reports the cumulative usage of every model call rather than only the
 * last one.
 * <p>
 * An instance is stateful and not thread-safe: create one per non-streaming
 * {@code adviseCall} invocation, or one per stream subscription (inside a
 * {@link Flux#defer}) to keep the accumulated usage subscription-local.
 * <p>
 * The token arithmetic and {@link ChatResponse} metadata copying are delegated to
 * {@link UsageCalculator}; this class owns the recursive advisor loop state and the final
 * response stamping.
 * <p>
 * Typical non-streaming use:
 *
 * <pre>{@code
 * UsageAccumulator usage = new UsageAccumulator();
 * ChatClientResponse response;
 * do {
 *     response = chain.nextCall(request);
 *     usage.addRoundResponse(response.chatResponse());
 *     // ... decide whether to loop again ...
 * }
 * while (loopAgain);
 * return usage.applyAccumulatedUsage(response);
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Jewoo Shin
 * @since 2.0.0
 */
public final class UsageAccumulator {

	private @Nullable ChatResponse accumulated;

	/**
	 * Returns the cumulative usage folded in so far, or {@code null} if none. In a
	 * streaming loop this is the total of the <em>previous</em> rounds, since it is read
	 * before the in-flight round is {@link #addRoundResponse(ChatResponse) added}.
	 * @return the accumulated chat response carrying the cumulative usage, or
	 * {@code null}
	 */
	public @Nullable ChatResponse accumulatedResponse() {
		return this.accumulated;
	}

	/**
	 * Folds the usage of the given round response into the running total.
	 * @param roundChatResponse the response produced by the current round, or
	 * {@code null}
	 * @return the updated cumulative total, or {@code null} if still empty
	 */
	public @Nullable ChatResponse addRoundResponse(@Nullable ChatResponse roundChatResponse) {
		this.accumulated = UsageCalculator.accumulateResponseUsage(roundChatResponse, this.accumulated);
		return this.accumulated;
	}

	/**
	 * Stamps the accumulated total usage onto the given response. Used to finalize a
	 * non-streaming loop or a streaming return-direct result. The response is returned
	 * unchanged when there is nothing accumulated, or when its usage already equals the
	 * accumulated total.
	 * @param chatClientResponse the response to stamp the total onto
	 * @return the response carrying the accumulated total usage
	 */
	public ChatClientResponse applyAccumulatedUsage(ChatClientResponse chatClientResponse) {
		return stampTotalUsage(chatClientResponse, this.accumulated);
	}

	private static ChatClientResponse stampTotalUsage(ChatClientResponse chatClientResponse,
			@Nullable ChatResponse accumulatedChatResponse) {
		ChatResponse currentChatResponse = chatClientResponse.chatResponse();
		if (currentChatResponse == null || accumulatedChatResponse == null) {
			return chatClientResponse;
		}
		Usage accumulatedUsage = accumulatedChatResponse.getMetadata().getUsage();
		if (UsageCalculator.isEmpty(accumulatedUsage)
				|| Objects.equals(currentChatResponse.getMetadata().getUsage(), accumulatedUsage)) {
			return chatClientResponse;
		}
		return chatClientResponse.mutate()
			.chatResponse(UsageCalculator.withUsage(currentChatResponse, accumulatedUsage))
			.build();
	}

	/**
	 * Adds the previous rounds' accumulated usage onto a single streamed chunk that
	 * already carries its own usage. Chunks without usage (or when there is no previous
	 * accumulated response) are returned unchanged, so the previous rounds' usage is
	 * reflected only on usage-bearing chunks.
	 * @param chunk the streamed chunk
	 * @param previousAccumulatedResponse the response carrying the accumulated usage of
	 * previous rounds, or {@code null}
	 * @return the chunk with the previous rounds' usage added to its usage, or unchanged
	 */
	static ChatClientResponse applyPreviousAccumulatedUsageToChunk(ChatClientResponse chunk,
			@Nullable ChatResponse previousAccumulatedResponse) {
		ChatResponse currentChatResponse = chunk.chatResponse();
		if (currentChatResponse == null || previousAccumulatedResponse == null) {
			return chunk;
		}
		Usage currentUsage = currentChatResponse.getMetadata().getUsage();
		if (UsageCalculator.isEmpty(currentUsage)
				|| UsageCalculator.isEmpty(previousAccumulatedResponse.getMetadata().getUsage())) {
			return chunk;
		}
		Usage cumulativeUsage = UsageCalculator.getCumulativeUsage(currentUsage, previousAccumulatedResponse);
		if (Objects.equals(currentUsage, cumulativeUsage)) {
			return chunk;
		}
		return chunk.mutate().chatResponse(UsageCalculator.withUsage(currentChatResponse, cumulativeUsage)).build();
	}

	/**
	 * Builds the trailing usage-only emission for a streaming loop whose final round
	 * reported no usage of its own. In that case the accumulated total from previous
	 * rounds was never stamped onto an emitted chunk, so a usage-only
	 * {@link ChatClientResponse} (with empty generations, so no content is duplicated)
	 * carrying the accumulated total is emitted. Returns an empty flux when no correction
	 * is needed (the round reported its own usage, or there is nothing accumulated to
	 * preserve).
	 * @param aggregatedResponse the aggregated response of the final round
	 * @param roundChatResponse the final round's own response (its own usage)
	 * @param accumulatedChatResponse the response carrying the cumulative usage
	 * @return a single usage-only response, or an empty flux when no correction is needed
	 */
	static Flux<ChatClientResponse> emitFinalUsageCorrectionIfNecessary(ChatClientResponse aggregatedResponse,
			@Nullable ChatResponse roundChatResponse, @Nullable ChatResponse accumulatedChatResponse) {
		if (accumulatedChatResponse == null) {
			return Flux.empty();
		}
		Usage accumulatedUsage = accumulatedChatResponse.getMetadata().getUsage();
		if (!UsageCalculator.isEmpty(getUsage(roundChatResponse)) || UsageCalculator.isEmpty(accumulatedUsage)) {
			return Flux.empty();
		}
		ChatResponse usageOnlyChatResponse = ChatResponse.builder()
			.from(accumulatedChatResponse)
			.generations(List.of())
			.build();
		return Flux.just(aggregatedResponse.mutate().chatResponse(usageOnlyChatResponse).build());
	}

	private static @Nullable Usage getUsage(@Nullable ChatResponse chatResponse) {
		return chatResponse != null ? chatResponse.getMetadata().getUsage() : null;
	}

}
