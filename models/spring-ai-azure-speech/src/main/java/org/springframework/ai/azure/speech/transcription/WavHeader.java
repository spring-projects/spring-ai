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
 * The parts of a WAV header the model needs, as read by {@link WavHeaderReader}.
 *
 * @param sampleRate samples per second, always 8000 or 16000
 * @param declaredDataBytes the {@code data} chunk's declared length in PCM payload bytes,
 * or {@link #UNKNOWN_DATA_LENGTH} when the header does not state one
 * @author Spring AI Contributors
 */
record WavHeader(int sampleRate, long declaredDataBytes) {

	/**
	 * Sentinel for a {@code data} chunk whose length the header does not state, either
	 * because it declares {@code 0} or because it uses the {@code 0xFFFFFFFF} streaming
	 * placeholder. The size decision then falls to the streaming guard.
	 */
	static final long UNKNOWN_DATA_LENGTH = -1L;

	boolean hasDeclaredDataLength() {
		return this.declaredDataBytes != UNKNOWN_DATA_LENGTH;
	}

}
