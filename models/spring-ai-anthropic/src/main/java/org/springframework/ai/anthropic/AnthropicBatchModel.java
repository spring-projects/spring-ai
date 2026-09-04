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

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Anthropic <a href="https://platform.claude.com/docs/en/api/messages/batches">Message
 * Batches</a> abstraction: submit many prompts at once, poll the batch, then read the
 * per-request results.
 *
 * <p>
 * Batch processing is asynchronous and can take <b>up to 24 hours</b>, so this is
 * deliberately <b>not</b> a {@link ChatModel}: {@link #submit(List)} has no immediate
 * {@link ChatResponse} to return. The control operations are synchronous; only
 * {@link #results(String)} is reactive, so a batch with a very large number of entries
 * never has to be held in memory.
 *
 * <p>
 * <b>Spring AI provides the provider access, not the orchestration.</b> There is no
 * automatic polling, no persistence, no scheduling and no business retry: how often to
 * call {@link #retrieve(String)}, where to store the batch id and the {@code customId}
 * correlations, when to notify and how to account for cost all stay with the application.
 *
 * <p>
 * Requests are mapped exactly as they would be for {@link ChatModel#call(Prompt)}, and a
 * succeeded result is converted back into a {@link ChatResponse} with the same
 * generations, metadata and usage — see {@link DefaultAnthropicBatchModel} for the
 * details and for the tool-calling limitation.
 *
 * <p>
 * <b>Results are unordered.</b> Correlate them through
 * {@link AnthropicBatchResult#customId()}, never by position.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 * @see AnthropicBatchRequest
 * @see AnthropicBatchResult
 * @see DefaultAnthropicBatchModel
 */
public interface AnthropicBatchModel {

	/**
	 * Creates a builder for the default {@link AnthropicBatchModel} implementation.
	 * @return a new builder instance
	 */
	static DefaultAnthropicBatchModel.Builder builder() {
		return DefaultAnthropicBatchModel.builder();
	}

	/**
	 * Submits a batch of prompts for asynchronous processing.
	 *
	 * <p>
	 * Returns as soon as Anthropic accepts the batch; no request has been processed yet.
	 * Persist {@link AnthropicBatch#id()} together with the {@code customId} of every
	 * entry so that polling and correlation survive an application restart.
	 * @param requests the batch entries; must be non-empty and carry distinct
	 * {@code customId} values
	 * @return the accepted batch, in state {@link AnthropicBatchStatus#IN_PROGRESS}
	 * @throws IllegalArgumentException if the list is empty or a {@code customId} is
	 * duplicated
	 */
	AnthropicBatch submit(List<AnthropicBatchRequest> requests);

	/**
	 * Retrieves the current state of a batch.
	 *
	 * <p>
	 * Call this on the application's own schedule; Spring AI performs no polling. Results
	 * become readable once {@link AnthropicBatch#isEnded()} is {@code true}.
	 * @param batchId the batch identifier returned by {@link #submit(List)}
	 * @return the current batch state
	 */
	AnthropicBatch retrieve(String batchId);

	/**
	 * Streams the results of an ended batch.
	 *
	 * <p>
	 * Results arrive in an unspecified order: key them by
	 * {@link AnthropicBatchResult#customId()}. Individual failures are emitted as
	 * {@link AnthropicBatchResultStatus#ERRORED} items rather than thrown, so one bad
	 * entry never hides the rest.
	 * @param batchId the batch identifier
	 * @return a lazily-populated flux of per-request results
	 */
	Flux<AnthropicBatchResult> results(String batchId);

	/**
	 * Requests cancellation of a batch.
	 *
	 * <p>
	 * Cancellation is not immediate: the batch moves to
	 * {@link AnthropicBatchStatus#CANCELING} and requests that already completed keep
	 * their result, while the remaining ones end up as
	 * {@link AnthropicBatchResultStatus#CANCELED}.
	 * @param batchId the batch identifier
	 * @return the batch state after the cancellation request
	 */
	AnthropicBatch cancel(String batchId);

	/**
	 * Deletes a batch. Only batches whose processing has ended can be deleted.
	 * @param batchId the batch identifier
	 */
	void delete(String batchId);

}
