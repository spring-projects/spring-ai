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
 * Records that a resource owned by a transcription operation was deliberately not closed,
 * because doing so would not have been safe.
 * <p>
 * This is the disclosed residual of the recognizer close gate: when the completion of an
 * SDK action that was already handed to the recognizer cannot be observed by any thread,
 * the model reports the recognizer as unreleased rather than calling {@code close()},
 * which the SDK rejects while an asynchronous recognition is still counted as active.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechResourceException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public AzureSpeechResourceException(String message) {
		super(message);
	}

}
