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

import com.openai.models.batches.Batch;

/**
 * Status values for a tracked batch execution, mapping to OpenAI Batch API states with an
 * additional {@link #RESULTS_PROCESSED} terminal state indicating that the application
 * has fully processed the results.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public enum BatchExecutionStatus {

	/** Batch has been submitted to OpenAI and is awaiting validation. */
	SUBMITTED,

	/** OpenAI is validating the batch input file. */
	VALIDATING,

	/** Batch is actively being processed by OpenAI. */
	IN_PROGRESS,

	/** OpenAI is finalizing the batch results. */
	FINALIZING,

	/** Batch completed on OpenAI but results have not yet been processed. */
	COMPLETED,

	/** Results have been downloaded and dispatched to handlers. */
	RESULTS_PROCESSED,

	/** Batch failed on the OpenAI side. */
	FAILED,

	/** Batch expired before completion (OpenAI's 24h window). */
	EXPIRED,

	/** Batch is being cancelled. */
	CANCELLING,

	/** Batch was cancelled. */
	CANCELLED;

	/**
	 * Converts an OpenAI SDK {@link Batch.Status} to a {@link BatchExecutionStatus}.
	 * @param openAiStatus the OpenAI batch status
	 * @return the corresponding execution status
	 */
	public static BatchExecutionStatus fromOpenAiStatus(Batch.Status openAiStatus) {
		if (Batch.Status.VALIDATING.equals(openAiStatus)) {
			return VALIDATING;
		}
		if (Batch.Status.IN_PROGRESS.equals(openAiStatus)) {
			return IN_PROGRESS;
		}
		if (Batch.Status.FINALIZING.equals(openAiStatus)) {
			return FINALIZING;
		}
		if (Batch.Status.COMPLETED.equals(openAiStatus)) {
			return COMPLETED;
		}
		if (Batch.Status.FAILED.equals(openAiStatus)) {
			return FAILED;
		}
		if (Batch.Status.EXPIRED.equals(openAiStatus)) {
			return EXPIRED;
		}
		if (Batch.Status.CANCELLING.equals(openAiStatus)) {
			return CANCELLING;
		}
		if (Batch.Status.CANCELLED.equals(openAiStatus)) {
			return CANCELLED;
		}
		return SUBMITTED;
	}

	/**
	 * Returns whether this status represents a terminal state (no further transitions).
	 */
	public boolean isTerminal() {
		return this == RESULTS_PROCESSED || this == FAILED || this == EXPIRED || this == CANCELLED;
	}

}
