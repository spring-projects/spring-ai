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
 * Raised when the supplied audio is not the uncompressed PCM WAV this model accepts.
 * <p>
 * It is an {@link IllegalArgumentException} because the audio is the caller's argument
 * and no service was contacted to reach this conclusion. Its message is always built from
 * literals and numbers decoded out of the WAV header, never from the resource description
 * or path, which is what lets the model hand it to the caller unchanged while every other
 * failure is reduced to a type name.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechAudioFormatException extends IllegalArgumentException {

	@Serial
	private static final long serialVersionUID = 1L;

	public AzureSpeechAudioFormatException(String message) {
		super(message);
	}

}
