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
 * The PCM payload the SDK pulls samples from, as the model sees it.
 * <p>
 * The SDK adapter wraps one of these in the SDK's own pull-stream callback, so that the
 * byte counting, the size guard, and the failure handling all stay on the model's side of
 * the seam and can be tested without the SDK.
 *
 * @author Spring AI Contributors
 */
interface PcmAudioSource {

	/**
	 * Fills {@code buffer} with the next PCM bytes.
	 * @param buffer the SDK-owned buffer to write into
	 * @return the number of bytes written, or {@code 0} at the end of the payload
	 */
	int read(byte[] buffer);

	/**
	 * Releases the underlying audio stream. Called by the SDK when it disposes the pull
	 * stream, and by the model's own cleanup; both route to the same idempotent close.
	 */
	void close();

}
