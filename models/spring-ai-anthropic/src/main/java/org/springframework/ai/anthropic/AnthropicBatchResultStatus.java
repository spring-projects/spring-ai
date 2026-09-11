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

/**
 * Terminal outcome of a single request inside an Anthropic message batch.
 *
 * <p>
 * Outcomes are per request, not per batch: one errored entry does not prevent the other
 * entries of the same batch from succeeding.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 */
public enum AnthropicBatchResultStatus {

	/**
	 * The request completed and a message is available.
	 */
	SUCCEEDED,

	/**
	 * The request failed; see {@link AnthropicBatchResult#error()} for the reason.
	 */
	ERRORED,

	/**
	 * The request was canceled before completion, following a
	 * {@link AnthropicBatchModel#cancel(String)} call.
	 */
	CANCELED,

	/**
	 * The request did not complete before the batch expired.
	 */
	EXPIRED,

	/**
	 * An outcome returned by the API that this version of Spring AI does not know about.
	 */
	UNKNOWN

}
