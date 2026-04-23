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

package org.springframework.ai.openai.batch;

import java.util.List;

import com.openai.models.batches.Batch;

/**
 * Listener interface for OpenAI Batch API lifecycle events.
 * <p>
 * Implementations can react to batch creation, completion, failure, expiration, and
 * individual request results. A no-op default is provided for all methods so
 * implementations only need to override the events they care about.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public interface OpenAiBatchListener {

	/**
	 * Called after a batch has been successfully created on the OpenAI API.
	 * @param batch the created batch
	 * @param requestCount the number of requests in the batch
	 */
	default void onBatchCreated(Batch batch, int requestCount) {
	}

	/**
	 * Called when a batch has completed successfully.
	 * @param batch the completed batch
	 */
	default void onBatchCompleted(Batch batch) {
	}

	/**
	 * Called when a batch has failed.
	 * @param batch the failed batch
	 */
	default void onBatchFailed(Batch batch) {
	}

	/**
	 * Called when a batch has expired before completion.
	 * @param batch the expired batch
	 */
	default void onBatchExpired(Batch batch) {
	}

	/**
	 * Called when a batch has been cancelled.
	 * @param batch the cancelled batch
	 */
	default void onBatchCancelled(Batch batch) {
	}

	/**
	 * Called after individual response lines from a completed batch have been processed.
	 * @param batch the batch that produced these results
	 * @param successLines response lines that completed successfully
	 * @param errorLines response lines that failed
	 */
	default void onBatchResultsProcessed(Batch batch, List<BatchResponseLine> successLines,
			List<BatchResponseLine> errorLines) {
	}

}
