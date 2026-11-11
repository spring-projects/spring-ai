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

package org.springframework.ai.azure.speech.transcription;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A service cancellation, flattened to plain values so that no SDK event type crosses the
 * seam.
 *
 * @param cause how the model must treat this cancellation
 * @param reason the SDK {@code CancellationReason} enumeration name
 * @param errorCode the SDK {@code CancellationErrorCode} enumeration name, when one was
 * reported
 * @param errorDetails the service's own error text, when one was reported. It is captured
 * as the SDK reported it and is never placed in an exception message, a log line, or
 * response metadata: it is free text that can repeat a key, a token, a credential-bearing
 * URL, or a path.
 * @author Spring AI Contributors
 */
record CancellationEvent(CancellationCause cause, @Nullable String reason, @Nullable String errorCode,
		@Nullable String errorDetails) {

	CancellationEvent {
		Assert.notNull(cause, "cause must not be null");
	}

	static CancellationEvent endOfStream(@Nullable String reason) {
		return new CancellationEvent(CancellationCause.END_OF_STREAM, reason, null, null);
	}

	static CancellationEvent error(@Nullable String reason, @Nullable String errorCode, @Nullable String errorDetails) {
		return new CancellationEvent(CancellationCause.ERROR, reason, errorCode, errorDetails);
	}

	static CancellationEvent cancelledByUser(@Nullable String reason) {
		return new CancellationEvent(CancellationCause.CANCELLED_BY_USER, reason, null, null);
	}

	/**
	 * Renders the event without its free text, so that neither a log line nor a
	 * diagnostic built from an event can repeat what the service reported. Whether
	 * details were reported at all is retained, because that much is useful and carries
	 * nothing from the service.
	 */
	@Override
	public String toString() {
		return "CancellationEvent{cause=" + this.cause + ", reason=" + this.reason + ", errorCode=" + this.errorCode
				+ ", errorDetails=" + ((this.errorDetails != null) ? "(omitted)" : "none") + '}';
	}

}
