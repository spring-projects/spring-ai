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
 * Raised when the {@code SpeechConfig} a factory supplied cannot serve the requested
 * options, such as continuous language identification over a configuration that was not
 * created from an endpoint.
 * <p>
 * It is an {@link IllegalStateException} because the configuration and the options are
 * the application's own and no service was contacted to reach this conclusion. Its
 * message is always built from literals and the model's own option names, never from the
 * supplied configuration's properties, which is what lets the model hand it to the caller
 * unchanged while every failure a factory itself throws is reduced to a type name.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class AzureSpeechConfigurationException extends IllegalStateException {

	@Serial
	private static final long serialVersionUID = 1L;

	public AzureSpeechConfigurationException(String message) {
		super(message);
	}

}
