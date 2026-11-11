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
 * Internal, sanitized channel for a recognizer start or stop call that the SDK rejected
 * synchronously.
 * <p>
 * Nothing was handed to the SDK when this is thrown, so the rejected action's close-gate
 * reservation is already settled and the recognizer is safe to close. The start call site
 * propagates it as the operation's startup failure; every stop call site logs it at WARN,
 * because an outcome is already decided by then and must not be overwritten.
 *
 * @author Spring AI Contributors
 */
class AzureSpeechActionSubmissionException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	AzureSpeechActionSubmissionException(String message) {
		super(message);
	}

}
