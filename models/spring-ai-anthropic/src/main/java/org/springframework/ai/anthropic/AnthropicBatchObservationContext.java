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

import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Context used to store metadata for Anthropic Message Batches operations.
 *
 * <p>
 * Deliberately carries no batch identifier and no prompt or generated content: batch ids
 * are unbounded in cardinality and prompts must not leak into metric tags.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public class AnthropicBatchObservationContext extends Observation.Context {

	private final Operation operation;

	private final String provider;

	private final @Nullable String requestModel;

	private final @Nullable Integer requestCount;

	private @Nullable AnthropicBatch batch;

	private @Nullable AnthropicBatchRequestCounts requestCounts;

	AnthropicBatchObservationContext(Operation operation, String provider, @Nullable String requestModel,
			@Nullable Integer requestCount) {
		this.operation = operation;
		this.provider = provider;
		this.requestModel = requestModel;
		this.requestCount = requestCount;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * The batch operation being observed.
	 * @return the operation
	 */
	public Operation getOperation() {
		return this.operation;
	}

	/**
	 * The model provider as identified by the client instrumentation.
	 * @return the provider
	 */
	public String getProvider() {
		return this.provider;
	}

	/**
	 * The model requested for the batch entries, when a single model applies to all of
	 * them.
	 * @return the model, or {@code null}
	 */
	public @Nullable String getRequestModel() {
		return this.requestModel;
	}

	/**
	 * The number of requests submitted, for {@link Operation#CREATE}.
	 * @return the request count, or {@code null}
	 */
	public @Nullable Integer getRequestCount() {
		return this.requestCount;
	}

	/**
	 * The batch returned by the operation, when it returns one.
	 * @return the batch, or {@code null}
	 */
	public @Nullable AnthropicBatch getBatch() {
		return this.batch;
	}

	public void setBatch(@Nullable AnthropicBatch batch) {
		this.batch = batch;
		if (batch != null) {
			this.requestCounts = batch.requestCounts();
		}
	}

	/**
	 * The per-outcome counters observed for this operation. Set from the batch for
	 * control-plane operations, and accumulated while streaming for
	 * {@link Operation#RESULTS}.
	 * @return the counters, or {@code null}
	 */
	public @Nullable AnthropicBatchRequestCounts getRequestCounts() {
		return this.requestCounts;
	}

	public void setRequestCounts(@Nullable AnthropicBatchRequestCounts requestCounts) {
		this.requestCounts = requestCounts;
	}

	/**
	 * The Anthropic Message Batches operations that Spring AI observes.
	 */
	public enum Operation {

		/**
		 * Batch submission, {@code POST /v1/messages/batches}.
		 */
		CREATE("batch_create"),

		/**
		 * Status lookup, {@code GET /v1/messages/batches/{id}}.
		 */
		RETRIEVE("batch_retrieve"),

		/**
		 * Result streaming, {@code GET /v1/messages/batches/{id}/results}.
		 */
		RESULTS("batch_results"),

		/**
		 * Cancellation, {@code POST /v1/messages/batches/{id}/cancel}.
		 */
		CANCEL("batch_cancel"),

		/**
		 * Deletion, {@code DELETE /v1/messages/batches/{id}}.
		 */
		DELETE("batch_delete");

		private final String value;

		Operation(String value) {
			this.value = value;
		}

		public String value() {
			return this.value;
		}

	}

	public static final class Builder {

		private @Nullable Operation operation;

		private @Nullable String provider;

		private @Nullable String requestModel;

		private @Nullable Integer requestCount;

		private Builder() {
		}

		public Builder operation(Operation operation) {
			this.operation = operation;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public Builder requestModel(@Nullable String requestModel) {
			this.requestModel = requestModel;
			return this;
		}

		public Builder requestCount(@Nullable Integer requestCount) {
			this.requestCount = requestCount;
			return this;
		}

		public AnthropicBatchObservationContext build() {
			Assert.state(this.operation != null, "Operation must not be null");
			Assert.state(this.provider != null, "Provider must not be null");
			return new AnthropicBatchObservationContext(this.operation, this.provider, this.requestModel,
					this.requestCount);
		}

	}

}
