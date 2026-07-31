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

import java.time.OffsetDateTime;

import com.anthropic.models.messages.batches.MessageBatch;
import org.jspecify.annotations.Nullable;

/**
 * State of an Anthropic message batch, as returned by
 * {@link AnthropicBatchModel#submit(java.util.List)},
 * {@link AnthropicBatchModel#retrieve(String)} and
 * {@link AnthropicBatchModel#cancel(String)}.
 *
 * <p>
 * Batch processing is asynchronous and can take up to 24 hours. Applications are expected
 * to persist {@link #id()} and poll {@link AnthropicBatchModel#retrieve(String)} on their
 * own schedule — Spring AI performs no polling.
 *
 * @param id the batch identifier, to be persisted by the application for later polling
 * and result retrieval
 * @param status the current processing status
 * @param requestCounts per-outcome request counters
 * @param createdAt when the batch was created
 * @param expiresAt when the batch expires; requests not completed by then are reported as
 * {@link AnthropicBatchResultStatus#EXPIRED}
 * @param endedAt when processing finished, or {@code null} while still in progress
 * @param cancelInitiatedAt when cancellation was requested, or {@code null}
 * @param archivedAt when the batch was archived, or {@code null}
 * @param resultsUrl the URL of the JSONL results, or {@code null} until processing ends;
 * prefer {@link AnthropicBatchModel#results(String)} over fetching it directly
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public record AnthropicBatch(String id, AnthropicBatchStatus status, AnthropicBatchRequestCounts requestCounts,
		OffsetDateTime createdAt, OffsetDateTime expiresAt, @Nullable OffsetDateTime endedAt,
		@Nullable OffsetDateTime cancelInitiatedAt, @Nullable OffsetDateTime archivedAt, @Nullable String resultsUrl) {

	/**
	 * Whether processing has finished and results can be read.
	 * @return {@code true} when the status is {@link AnthropicBatchStatus#ENDED}
	 */
	public boolean isEnded() {
		return this.status == AnthropicBatchStatus.ENDED;
	}

	/**
	 * Whether cancellation has been requested for this batch.
	 * @return {@code true} when the status is {@link AnthropicBatchStatus#CANCELING}
	 */
	public boolean isCanceling() {
		return this.status == AnthropicBatchStatus.CANCELING;
	}

	static AnthropicBatch from(MessageBatch messageBatch) {
		return new AnthropicBatch(messageBatch.id(), AnthropicBatchStatus.from(messageBatch.processingStatus()),
				AnthropicBatchRequestCounts.from(messageBatch.requestCounts()), messageBatch.createdAt(),
				messageBatch.expiresAt(), messageBatch.endedAt().orElse(null),
				messageBatch.cancelInitiatedAt().orElse(null), messageBatch.archivedAt().orElse(null),
				messageBatch.resultsUrl().orElse(null));
	}

}
