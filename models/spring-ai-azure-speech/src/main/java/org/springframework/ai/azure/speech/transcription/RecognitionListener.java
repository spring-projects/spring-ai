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

/**
 * Receives recognition events from the SDK adapter, as plain values.
 * <p>
 * Every method may be invoked on an SDK callback thread, so implementations must not
 * block and must not call back into the SDK.
 *
 * @author Spring AI Contributors
 */
interface RecognitionListener {

	/**
	 * A final recognition result arrived.
	 */
	void recognized(AzureSpeechSegment segment);

	/**
	 * A final result arrived that matched no speech.
	 */
	void noMatch();

	/**
	 * The service reported a cancellation.
	 */
	void canceled(CancellationEvent event);

	/**
	 * The recognition session ended.
	 */
	void sessionStopped();

}
