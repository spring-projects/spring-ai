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

import com.microsoft.cognitiveservices.speech.SpeechConfig;

/**
 * Supplies the {@link SpeechConfig} for one Azure Speech operation.
 * <p>
 * The contract is deliberately narrow, because the SDK copies a configuration into the
 * recognizer at construction time and requires it to be closed explicitly:
 * <ul>
 * <li>It is invoked exactly once per {@code call} or {@code stream} subscription.</li>
 * <li>It must return a <em>new</em> instance every time. A shared or cached instance is a
 * programming error, because ownership transfers to the model, which closes the returned
 * configuration when that one operation terminates.</li>
 * <li>Request options are applied to the returned configuration by the model, so a
 * factory never needs to read them, and option mapping stays in one tested place.</li>
 * <li>Validating credentials is the factory's own responsibility. The model never
 * inspects how a configuration was authenticated.</li>
 * <li>Return an endpoint-based configuration when a request may ask for continuous
 * language identification: the service supports that mode only over an endpoint, so the
 * model rejects the request before opening a session when the supplied configuration
 * carries no endpoint.</li>
 * </ul>
 * Applications that want to reuse expensive state, such as a token credential, keep that
 * state inside the factory rather than inside the {@link SpeechConfig}.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
@FunctionalInterface
public interface AzureSpeechConfigFactory {

	/**
	 * Creates a new {@link SpeechConfig} whose ownership transfers to the caller.
	 * @return a freshly created configuration, never a shared or cached one
	 */
	SpeechConfig create();

}
