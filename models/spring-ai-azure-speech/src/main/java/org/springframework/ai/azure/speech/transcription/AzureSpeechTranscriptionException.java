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

import java.io.Serial;
import java.time.Duration;

import org.jspecify.annotations.Nullable;

/**
 * Raised when an Azure Speech transcription cannot produce a result.
 * <p>
 * Instances carry only model-built text plus, for a service cancellation, the SDK's own
 * reason and error-code enumeration names. They never carry the service's free-text error
 * details, the configured API key, an authorization token, a credential-bearing URL, or
 * the audio resource path, and they never wrap a raw SDK or I/O throwable as their cause:
 * the underlying failure is reduced to its type name. The only causes ever attached are
 * model-created {@link java.util.concurrent.TimeoutException} and
 * {@link InterruptedException} instances, which carry no external text.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechTranscriptionException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final @Nullable String cancellationReason;

	private final @Nullable String errorCode;

	public AzureSpeechTranscriptionException(String message) {
		this(message, null, null, null);
	}

	public AzureSpeechTranscriptionException(String message, @Nullable Throwable cause) {
		this(message, null, null, cause);
	}

	public AzureSpeechTranscriptionException(String message, @Nullable String cancellationReason,
			@Nullable String errorCode, @Nullable Throwable cause) {
		super(message, cause);
		this.cancellationReason = cancellationReason;
		this.errorCode = errorCode;
	}

	/**
	 * Returns the SDK {@code CancellationReason} enumeration name, or {@code null} when
	 * the failure did not originate in a service cancellation.
	 */
	public @Nullable String getCancellationReason() {
		return this.cancellationReason;
	}

	/**
	 * Returns the SDK {@code CancellationErrorCode} enumeration name, or {@code null}
	 * when none was reported. Callers can use it to decide whether to retry; the model
	 * itself never retries.
	 */
	public @Nullable String getErrorCode() {
		return this.errorCode;
	}

	static AzureSpeechTranscriptionException timedOut(Duration timeout, @Nullable Throwable cause) {
		return new AzureSpeechTranscriptionException("Azure Speech transcription timed out after " + timeout, cause);
	}

	static AzureSpeechTranscriptionException interrupted(@Nullable Throwable cause) {
		return new AzureSpeechTranscriptionException("Azure Speech transcription was interrupted", cause);
	}

	/**
	 * The outcome recorded when a streaming subscriber cancels before anything else
	 * decided the operation. Reactor already considers such a subscription finished, so
	 * this never reaches a subscriber; it exists so the operation still has one recorded
	 * outcome to publish, log and account for.
	 */
	static AzureSpeechTranscriptionException canceledBySubscriber() {
		return new AzureSpeechTranscriptionException("Azure Speech transcription was canceled by the subscriber");
	}

	static AzureSpeechTranscriptionException audioReadFailure(String what, String underlyingType) {
		return new AzureSpeechTranscriptionException(what + " (" + underlyingType + ")");
	}

	static AzureSpeechTranscriptionException audioTooLarge(long limitBytes) {
		return new AzureSpeechTranscriptionException(
				"Azure Speech transcription stopped: the audio exceeds the configured limit of " + limitBytes
						+ " PCM payload bytes");
	}

	/**
	 * Builds a cancellation failure from the SDK's enumeration names only. The service's
	 * own {@code errorDetails} text is deliberately not accepted here: it is free text
	 * that can repeat a key, a token, a credential-bearing URL, or a path, and nothing
	 * guarantees otherwise. Diagnostic guidance comes from {@link #hintFor} instead,
	 * which is fixed text chosen by the error-code name.
	 */
	static AzureSpeechTranscriptionException canceled(@Nullable String reason, @Nullable String errorCode) {
		StringBuilder message = new StringBuilder("Azure Speech recognition was canceled");
		if (reason != null) {
			message.append(": reason=").append(reason);
		}
		if (errorCode != null) {
			message.append(", errorCode=").append(errorCode);
		}
		message.append("; ").append(hintFor(errorCode));
		return new AzureSpeechTranscriptionException(message.toString(), reason, errorCode, null);
	}

	/**
	 * Returns fixed guidance for an SDK {@code CancellationErrorCode} enumeration name.
	 * Every branch returns a literal, so no service-supplied text can reach a message.
	 */
	private static String hintFor(@Nullable String errorCode) {
		if (errorCode == null) {
			return "the service did not report an error code";
		}
		return switch (errorCode) {
			case "AuthenticationFailure" -> "check the API key, token and region or endpoint";
			case "Forbidden" -> "the free subscription used by the request has run out of quota";
			case "BadRequest" -> "check the configured recognition options and audio format";
			case "TooManyRequests" -> "the resource is being throttled; retry later";
			case "ConnectionFailure", "ServiceTimeout", "ServiceUnavailable" ->
				"the service could not be reached or did not answer in time";
			case "ServiceError", "RuntimeError" -> "the service reported an internal failure";
			default -> "see the Azure Speech CancellationErrorCode documentation for this code";
		};
	}

}
