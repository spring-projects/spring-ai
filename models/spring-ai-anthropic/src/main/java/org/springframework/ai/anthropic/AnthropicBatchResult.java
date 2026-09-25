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

package org.springframework.ai.anthropic;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Result of a single request inside an Anthropic message batch, correlated to its request
 * through {@link #customId()}.
 *
 * <p>
 * Results are <b>not</b> returned in submission order. Key them by {@code customId}
 * rather than by position in the stream.
 *
 * <p>
 * Exactly one of {@link #chatResponse()} / {@link #error()} is populated: a succeeded
 * result carries the {@link ChatResponse}, an errored one carries the
 * {@link AnthropicBatchError}, and canceled or expired results carry neither.
 *
 * @param customId the correlation identifier supplied on the matching
 * {@link AnthropicBatchRequest}
 * @param status the terminal outcome of this request
 * @param chatResponse the response, converted exactly as
 * {@link AnthropicChatModel#call(org.springframework.ai.chat.prompt.Prompt)} would; only
 * present when the status is {@link AnthropicBatchResultStatus#SUCCEEDED}
 * @param error the failure detail; only present when the status is
 * {@link AnthropicBatchResultStatus#ERRORED}
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public record AnthropicBatchResult(String customId, AnthropicBatchResultStatus status,
		@Nullable ChatResponse chatResponse, @Nullable AnthropicBatchError error) {

	/**
	 * Creates a succeeded result.
	 * @param customId the correlation identifier
	 * @param chatResponse the converted response
	 * @return the result
	 */
	static AnthropicBatchResult succeeded(String customId, ChatResponse chatResponse) {
		return new AnthropicBatchResult(customId, AnthropicBatchResultStatus.SUCCEEDED, chatResponse, null);
	}

	/**
	 * Creates an errored result.
	 * @param customId the correlation identifier
	 * @param error the failure detail
	 * @return the result
	 */
	static AnthropicBatchResult errored(String customId, AnthropicBatchError error) {
		return new AnthropicBatchResult(customId, AnthropicBatchResultStatus.ERRORED, null, error);
	}

	/**
	 * Creates a result with no payload, for canceled, expired or unrecognised outcomes.
	 * @param customId the correlation identifier
	 * @param status the terminal outcome
	 * @return the result
	 */
	static AnthropicBatchResult of(String customId, AnthropicBatchResultStatus status) {
		return new AnthropicBatchResult(customId, status, null, null);
	}

	/**
	 * Whether this request completed successfully.
	 * @return {@code true} when the status is
	 * {@link AnthropicBatchResultStatus#SUCCEEDED}
	 */
	public boolean isSucceeded() {
		return this.status == AnthropicBatchResultStatus.SUCCEEDED;
	}

	/**
	 * Returns the token usage reported for this request, for cost accounting.
	 * @return the usage, or {@code null} when this request produced no response
	 */
	public @Nullable Usage usage() {
		if (this.chatResponse == null || this.chatResponse.getMetadata() == null) {
			return null;
		}
		return this.chatResponse.getMetadata().getUsage();
	}

	/**
	 * Returns the aggregated text of the response.
	 * @return the response text, or {@code null} when this request produced no response
	 */
	public @Nullable String getText() {
		if (this.chatResponse == null || this.chatResponse.getResult() == null) {
			return null;
		}
		return this.chatResponse.getResult().getOutput().getText();
	}

}
