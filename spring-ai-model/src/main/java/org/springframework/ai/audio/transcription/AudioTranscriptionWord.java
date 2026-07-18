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

package org.springframework.ai.audio.transcription;

import java.time.Duration;

import org.springframework.util.Assert;

/**
 * A timestamped word in an audio transcription.
 *
 * @param text the transcribed word
 * @param start the start offset of the word
 * @param end the end offset of the word
 * @since 2.0.1
 */
public record AudioTranscriptionWord(String text, Duration start, Duration end) {

	public AudioTranscriptionWord {
		Assert.notNull(text, "Text must not be null");
		Assert.notNull(start, "Start must not be null");
		Assert.notNull(end, "End must not be null");
	}

}
