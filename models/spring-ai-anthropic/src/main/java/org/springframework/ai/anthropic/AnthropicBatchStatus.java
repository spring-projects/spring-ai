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

import com.anthropic.models.messages.batches.MessageBatch;

/**
 * Processing status of an Anthropic message batch.
 *
 * @author Ricken Bazolo
 * @since 2.0.0
 * @see <a href="https://platform.claude.com/docs/en/api/messages/batches">Anthropic
 * Message Batches API</a>
 */
public enum AnthropicBatchStatus {

	/**
	 * The batch has been accepted and its requests are being processed.
	 */
	IN_PROGRESS("in_progress"),

	/**
	 * Cancellation has been requested; requests already completed keep their result while
	 * the remaining ones are canceled.
	 */
	CANCELING("canceling"),

	/**
	 * Processing has finished. Results are available for reading, and every request has a
	 * terminal outcome (succeeded, errored, canceled or expired).
	 */
	ENDED("ended"),

	/**
	 * A status returned by the API that this version of Spring AI does not know about.
	 */
	UNKNOWN("unknown");

	private final String value;

	AnthropicBatchStatus(String value) {
		this.value = value;
	}

	/**
	 * Returns the wire value used by the Anthropic API.
	 * @return the wire value
	 */
	public String getValue() {
		return this.value;
	}

	/**
	 * Maps the SDK processing status onto this enum, returning {@link #UNKNOWN} for
	 * values added by the API after this release.
	 * @param processingStatus the SDK processing status
	 * @return the corresponding status
	 */
	static AnthropicBatchStatus from(MessageBatch.ProcessingStatus processingStatus) {
		String value = processingStatus.asString();
		for (AnthropicBatchStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		return UNKNOWN;
	}

}
