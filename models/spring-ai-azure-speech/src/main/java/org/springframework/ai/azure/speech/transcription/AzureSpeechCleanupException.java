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

/**
 * Records that releasing one resource owned by a transcription operation failed.
 * <p>
 * Carries the resource kind and the failing exception's type name only; the original
 * throwable is never attached, so no SDK or I/O text can reach a caller through it. When
 * the failure is known before the operation publishes its outcome, it is attached as a
 * suppressed exception on that outcome's primary failure, or logged at WARN if the
 * outcome has no failure to attach it to. A failure that arrives after publication —
 * which a deadline, an interrupt or a cancellation can leave behind — is only logged at
 * WARN, because the outcome the caller sees has already been handed over. It is never
 * thrown to a caller on its own.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechCleanupException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public AzureSpeechCleanupException(String message) {
		super(message);
	}

}
