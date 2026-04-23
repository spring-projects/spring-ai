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

import java.time.Instant;

import org.springframework.util.Assert;

/**
 * Represents a tracked OpenAI Batch API execution. Stores metadata about a batch
 * submission including its current status, request count, and associated file
 * identifiers.
 * <p>
 * This entity is managed by a {@link BatchExecutionRepository} and is updated as the
 * batch progresses through its lifecycle.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public class BatchExecution {

	private final String batchId;

	private final String endpoint;

	private final int requestCount;

	private final String inputFileId;

	private final Instant createdAt;

	private BatchExecutionStatus status;

	private Instant updatedAt;

	/**
	 * Creates a new batch execution record.
	 * @param batchId the OpenAI batch ID
	 * @param endpoint the API endpoint this batch targets
	 * @param status the initial status
	 * @param requestCount the number of requests in the batch
	 * @param inputFileId the OpenAI file ID of the uploaded JSONL input
	 */
	public BatchExecution(String batchId, String endpoint, BatchExecutionStatus status, int requestCount,
			String inputFileId) {
		Assert.hasText(batchId, "batchId must not be blank");
		Assert.hasText(endpoint, "endpoint must not be blank");
		Assert.notNull(status, "status must not be null");
		Assert.isTrue(requestCount > 0, "requestCount must be positive");
		Assert.hasText(inputFileId, "inputFileId must not be blank");
		this.batchId = batchId;
		this.endpoint = endpoint;
		this.status = status;
		this.requestCount = requestCount;
		this.inputFileId = inputFileId;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public String getBatchId() {
		return this.batchId;
	}

	public String getEndpoint() {
		return this.endpoint;
	}

	public BatchExecutionStatus getStatus() {
		return this.status;
	}

	public void setStatus(BatchExecutionStatus status) {
		Assert.notNull(status, "status must not be null");
		this.status = status;
		this.updatedAt = Instant.now();
	}

	public int getRequestCount() {
		return this.requestCount;
	}

	public String getInputFileId() {
		return this.inputFileId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	@Override
	public String toString() {
		return "BatchExecution{batchId='" + this.batchId + "', endpoint='" + this.endpoint + "', status=" + this.status
				+ ", requestCount=" + this.requestCount + '}';
	}

}
