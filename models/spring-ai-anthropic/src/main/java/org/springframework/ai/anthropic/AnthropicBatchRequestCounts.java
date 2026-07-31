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

import com.anthropic.models.messages.batches.MessageBatchRequestCounts;

/**
 * Per-outcome request counters of an Anthropic message batch.
 *
 * <p>
 * While a batch is {@link AnthropicBatchStatus#IN_PROGRESS in progress} these counters
 * let an application report progress without reading the (potentially large) result
 * stream.
 *
 * @param processing number of requests still being processed
 * @param succeeded number of requests that completed successfully
 * @param errored number of requests that failed
 * @param canceled number of requests canceled before completion
 * @param expired number of requests that expired before completion
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public record AnthropicBatchRequestCounts(long processing, long succeeded, long errored, long canceled, long expired) {

	/**
	 * Returns the total number of requests tracked by this batch.
	 * @return the sum of every counter
	 */
	public long total() {
		return this.processing + this.succeeded + this.errored + this.canceled + this.expired;
	}

	/**
	 * Returns the number of requests that reached a terminal outcome.
	 * @return the sum of every counter except {@link #processing()}
	 */
	public long completed() {
		return this.succeeded + this.errored + this.canceled + this.expired;
	}

	static AnthropicBatchRequestCounts from(MessageBatchRequestCounts counts) {
		return new AnthropicBatchRequestCounts(counts.processing(), counts.succeeded(), counts.errored(),
				counts.canceled(), counts.expired());
	}

}
